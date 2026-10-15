package com.hungergames.platform.supplydrop;

import com.hungergames.GameState;
import com.hungergames.api.event.GamePhaseChangeEvent;
import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.SettingType;
import com.hungergames.game.events.ScheduleTracker;
import com.hungergames.loot.LootConfig;
import com.hungergames.loot.LootManager;
import com.hungergames.platform.VirtualTime;
import com.hungergames.platform.announce.AnnouncementService;
import com.hungergames.platform.announce.AnnouncementService.Style;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.log.RoundLogService;
import com.hungergames.platform.persistence.RuntimeStateStore;
import com.hungergames.HungerGamesPlugin;
import com.hungergames.platform.Perms;
import com.hungergames.platform.sound.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Zeitgesteuerte Kapitol-Lieferungen (Supply Drops): An konfigurierten
 * Spielzeitpunkten wird eine Warnung gesendet und nach der Warnzeit eine
 * Loot-Kiste an einer sicheren Zufallsposition platziert — mit optionalem
 * Beacon, Feuerwerk und Partikeln.
 *
 * <p>Kopplung an die virtuelle Spielzeit: {@code /speedup} beschleunigt auch
 * die Drops. Ausgelöste Zeitpunkte und noch nicht gelandete Lieferungen
 * werden persistiert — ein Neustart löst nichts doppelt aus und holt eine
 * ausstehende Landung sofort nach.</p>
 */
public final class SupplyDropService implements Listener {

    private static final int MAX_POSITION_ATTEMPTS = 30;
    private static final int BORDER_MARGIN = 8;

    /** PDC-Marker auf Kiste und Beacon einer Lieferung (Schutz-Erkennung). */
    private static final NamespacedKey DROP_KEY =
            new NamespacedKey(JavaPlugin.getPlugin(HungerGamesPlugin.class), "supply_drop");

    private final Plugin plugin;
    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final VirtualTime virtualTime;
    private final SoundService sounds;
    private com.hungergames.platform.effect.EffectService effects;

    public void setEffects(com.hungergames.platform.effect.EffectService effects) {
        this.effects = effects;
    }
    private final AnnouncementService announcements;
    private final RoundLogService roundLog;
    private final RuntimeStateStore runtimeStore;
    private final Random random = new Random();

    private final Set<Integer> triggered = new HashSet<>();
    private final List<Location> pendingLandings = new ArrayList<>();
    private BukkitTask timerTask;

    public SupplyDropService(Plugin plugin, GameSession session, ConfigurationService config,
                             HgSettings settings, VirtualTime virtualTime, SoundService sounds,
                             AnnouncementService announcements, RoundLogService roundLog,
                             RuntimeStateStore runtimeStore) {
        this.plugin = plugin;
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.virtualTime = virtualTime;
        this.sounds = sounds;
        this.announcements = announcements;
        this.roundLog = roundLog;
        this.runtimeStore = runtimeStore;
    }

    /** Startet den Sekunden-Tick (läuft dauerhaft, tut außerhalb RUNNING nichts). */
    public void start() {
        triggered.addAll(runtimeStore.loadSupplyDropTriggered());
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    /** Holt nach einem Neustart ausstehende Landungen sofort nach. */
    public void restoreFromStore() {
        if (session.phase() != GamePhase.RUNNING) {
            return;
        }
        for (String raw : runtimeStore.loadSupplyDropPending()) {
            Location location = parseLocation(raw);
            if (location != null) {
                roundLog.log("SUPPLY", "Ausstehende Lieferung nach Neustart nachgeholt", location);
                land(location);
            }
        }
        pendingLandings.clear();
        persist();
    }

    // ==================== Status ====================

    /** Geplante Zeitpunkte als Dauer-Liste (aus der Konfiguration geparst). */
    public List<Duration> schedule() {
        return config.get(settings.supplyDropSchedule).stream()
                .map(SettingType.DURATION::parse)
                .toList();
    }

    public Set<Integer> triggeredIndices() {
        return Set.copyOf(triggered);
    }

    public String statusLine() {
        if (!config.get(settings.supplyDropsEnabled)) {
            return "deaktiviert";
        }
        List<Duration> schedule = schedule();
        long done = triggered.size();
        return done + "/" + schedule.size() + " ausgelöst";
    }

    // ==================== Tick & Auslösung ====================

    private void tick() {
        if (session.phase() != GamePhase.RUNNING
                || !config.get(settings.supplyDropsEnabled)) {
            return;
        }
        List<Integer> due = ScheduleTracker.dueIndices(schedule(), triggered, virtualTime.elapsed());
        for (int index : due) {
            triggered.add(index);
            persist();
            roundLog.log("SUPPLY", "Geplanter Drop #" + (index + 1) + " wird ausgelöst");
            beginDrop();
        }
    }

    /**
     * Löst manuell eine Lieferung aus (Warnphase inklusive).
     *
     * @return Fehlermeldung oder leer bei Erfolg
     */
    public java.util.Optional<String> triggerNow(String actor) {
        if (session.phase() != GamePhase.RUNNING) {
            return java.util.Optional.of("Supply Drops sind nur während RUNNING möglich.");
        }
        if (!config.get(settings.supplyDropsEnabled)) {
            return java.util.Optional.of("Supply Drops sind deaktiviert (events.supply-drops.enabled).");
        }
        roundLog.log("SUPPLY", actor + " löst manuell eine Lieferung aus");
        beginDrop();
        return java.util.Optional.empty();
    }

    private void beginDrop() {
        int count = config.get(settings.supplyDropCount);
        for (int i = 0; i < count; i++) {
            Location target = pickDropLocation();
            if (target == null) {
                roundLog.log("SUPPLY", "Keine geeignete Landeposition gefunden — Drop übersprungen");
                continue;
            }
            announceWarning(target);
            pendingLandings.add(target);
            persist();

            int warningSeconds = config.get(settings.supplyDropWarningSeconds);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pendingLandings.remove(target);
                persist();
                if (session.phase() == GamePhase.RUNNING) {
                    land(target);
                }
            }, Math.max(1, warningSeconds) * 20L);
        }
    }

    // ==================== Position ====================

    private Location pickDropLocation() {
        Location center = GameState.getInstance().getArenaCenter();
        if (center == null || center.getWorld() == null) {
            return null;
        }
        World world = center.getWorld();
        if (config.get(settings.supplyDropOnlyOverworld)
                && world.getEnvironment() != World.Environment.NORMAL) {
            return null;
        }
        int radiusMin = config.get(settings.supplyDropRadiusMin);
        int radiusMax = Math.max(radiusMin + 1, config.get(settings.supplyDropRadiusMax));
        double borderLimit = world.getWorldBorder().getSize() / 2.0 - BORDER_MARGIN;

        for (int attempt = 0; attempt < MAX_POSITION_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = radiusMin + random.nextDouble() * (radiusMax - radiusMin);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

            // Innerhalb der (aktuellen) Worldborder bleiben
            if (Math.abs(x - world.getWorldBorder().getCenter().getBlockX()) > borderLimit
                    || Math.abs(z - world.getWorldBorder().getCenter().getBlockZ()) > borderLimit) {
                continue;
            }
            Block ground = world.getHighestBlockAt(x, z);
            if (ground.isLiquid() || ground.getType() == Material.AIR) {
                continue;
            }
            return ground.getLocation().add(0, 1, 0);
        }
        return null;
    }

    // ==================== Landung ====================

    private void land(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        // Position kann sich seit der Warnung verändert haben (Bau/Explosion):
        // auf die aktuelle Oberfläche setzen.
        Block ground = world.getHighestBlockAt(location.getBlockX(), location.getBlockZ());
        Block chestBlock = ground.getRelative(0, 1, 0);
        chestBlock.setType(Material.CHEST);

        if (chestBlock.getState() instanceof Container container) {
            // Reihenfolge ist entscheidend: PDC sitzt auf dem Snapshot und braucht
            // update(), das Inventar ist live. Erst update() aufrufen und danach
            // aus einem FRISCHEN State befüllen — andersherum überschreibt update()
            // das gerade gefüllte Inventar mit dem (leeren) Snapshot.
            // Schutz-Markierung (Öffnen bleibt möglich, nur Abbau wird verhindert)
            container.getPersistentDataContainer().set(DROP_KEY, PersistentDataType.BOOLEAN, true);
            container.update();

            if (chestBlock.getState() instanceof Container live) {
                String tableKey = config.get(settings.supplyDropLootTable);
                LootConfig lootConfig = LootConfig.getInstance();
                var pool = lootConfig.getLootPoolByKey(tableKey);
                int slots = Math.max(1, live.getInventory().getSize()
                        * lootConfig.getFillPercentageByKey(tableKey) / 100);
                int filled = new LootManager().fillWithLoot(live.getInventory(), pool, slots);
                if (filled <= 0) {
                    roundLog.log("SUPPLY", "LootTable \"" + tableKey + "\" leer oder ungültig — "
                            + "Kiste bleibt leer", chestBlock.getLocation());
                }
            }
        }

        if (config.get(settings.supplyDropBeacon)) {
            placeBeacon(chestBlock);
        }
        if (effects != null) {
            effects.playAt(chestBlock.getLocation().add(0.5, 1, 0.5), settings.effectSupplyDrop);
        }
        if (config.get(settings.supplyDropFirework)) {
            spawnFirework(chestBlock.getLocation().add(0.5, 1, 0.5));
        }
        if (config.get(settings.supplyDropParticles)) {
            spawnParticleColumn(chestBlock.getLocation().add(0.5, 1, 0.5));
        }

        announcements.broadcast(settings.textSupplyDropLanded,
                Map.of("coords", coordsText(chestBlock.getLocation())), Style.CHAT, Style.TITLE);
        sounds.playToAll(settings.soundSupplyDropLanded);
        roundLog.log("SUPPLY", "Lieferung gelandet", chestBlock.getLocation());
    }

    /** Beacon neben der Kiste mit 3×3-Basis — weithin sichtbare Lichtsäule. */
    private void placeBeacon(Block chestBlock) {
        Block beaconBlock = chestBlock.getRelative(1, 0, 0);
        Material base = Material.matchMaterial(config.get(settings.supplyDropBaseMaterial));
        if (base == null) {
            base = Material.IRON_BLOCK;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                beaconBlock.getRelative(dx, -1, dz).setType(base);
            }
        }
        beaconBlock.setType(Material.BEACON);
        if (beaconBlock.getState() instanceof TileState tileState) {
            tileState.getPersistentDataContainer().set(DROP_KEY, PersistentDataType.BOOLEAN, true);
            tileState.update();
        }
    }

    // ==================== Schutz ====================

    /** Kiste/Beacon einer Lieferung (PDC), oder deren 3×3-Basis unter dem Beacon. */
    private boolean isProtectedDropBlock(Block block) {
        if (block.getState() instanceof TileState tileState
                && tileState.getPersistentDataContainer().has(DROP_KEY, PersistentDataType.BOOLEAN)) {
            return true;
        }
        // Basis: ein Block direkt unter einem markierten Beacon (3×3)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block above = block.getRelative(dx, 1, dz);
                if (above.getType() == Material.BEACON
                        && above.getState() instanceof TileState ts
                        && ts.getPersistentDataContainer().has(DROP_KEY, PersistentDataType.BOOLEAN)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Supply-Drop-Struktur ist (wenn geschützt) nicht abbaubar — außer von Spielmachern. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.get(settings.supplyDropProtected)
                || event.getPlayer().hasPermission(Perms.GAMEMASTER)) {
            return;
        }
        if (isProtectedDropBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (config.get(settings.supplyDropProtected)) {
            event.blockList().removeIf(this::isProtectedDropBlock);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (config.get(settings.supplyDropProtected)) {
            event.blockList().removeIf(this::isProtectedDropBlock);
        }
    }

    private void spawnFirework(Location location) {
        World world = location.getWorld();
        Firework firework = world.spawn(location, Firework.class);
        var meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.ORANGE, Color.YELLOW)
                .withTrail()
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    private void spawnParticleColumn(Location location) {
        World world = location.getWorld();
        for (int y = 0; y < 30; y += 2) {
            world.spawnParticle(Particle.END_ROD, location.clone().add(0, y, 0), 8, 0.2, 0.5, 0.2, 0);
        }
    }

    // ==================== Ansagen & Persistenz ====================

    private void announceWarning(Location target) {
        announcements.broadcast(settings.textSupplyDropWarning, Map.of(
                        "seconds", String.valueOf(config.get(settings.supplyDropWarningSeconds)),
                        "coords", coordsText(target)),
                Style.CHAT, Style.ACTIONBAR);
        sounds.playToAll(settings.soundSupplyDropWarning);
    }

    private String coordsText(Location location) {
        if (!config.get(settings.supplyDropAnnounceCoords)) {
            return "";
        }
        int fuzz = config.get(settings.supplyDropCoordinateFuzz);
        int x = location.getBlockX() + (fuzz > 0 ? random.nextInt(fuzz * 2 + 1) - fuzz : 0);
        int z = location.getBlockZ() + (fuzz > 0 ? random.nextInt(fuzz * 2 + 1) - fuzz : 0);
        return " bei §e" + x + " / " + z + "§r" + (fuzz > 0 ? " (ungefähr)" : "");
    }

    private void persist() {
        runtimeStore.saveSupplyDropState(triggered, pendingLandings.stream()
                .map(loc -> loc.getWorld().getName() + "," + loc.getBlockX() + ","
                        + loc.getBlockY() + "," + loc.getBlockZ())
                .toList());
    }

    private Location parseLocation(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @EventHandler
    public void onPhaseChange(GamePhaseChangeEvent event) {
        if (event.getNewPhase() == GamePhase.NOT_INITIALIZED) {
            // Neue Runde: Zähler zurücksetzen (runtime.yml wird ohnehin geleert)
            triggered.clear();
            pendingLandings.clear();
        }
    }
}
