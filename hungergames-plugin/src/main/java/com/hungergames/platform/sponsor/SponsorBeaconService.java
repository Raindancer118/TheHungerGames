package com.hungergames.platform.sponsor;

import com.hungergames.GameState;
import com.hungergames.HungerGamesPlugin;
import com.hungergames.api.event.GamePhaseChangeEvent;
import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.SettingType;
import com.hungergames.game.events.ScheduleTracker;
import com.hungergames.platform.VirtualTime;
import com.hungergames.platform.announce.AnnouncementService;
import com.hungergames.platform.announce.AnnouncementService.Style;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.config.HgSettings.BeaconSpawnMode;
import com.hungergames.platform.log.RoundLogService;
import com.hungergames.platform.persistence.RuntimeStateStore;
import com.hungergames.platform.sound.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Sponsor-Beacons: physische Shop-Stationen in der Welt. Erkennung läuft über
 * die Location-Registry plus PDC-Markierung am TileState — normale Beacons
 * sind nie Sponsor-Shops. Spawn-Modi: CENTER (bei RUNNING-Beginn nahe der
 * Mitte), RANDOM_TIMED (Zeitplan, sichere Zufallspositionen), MANUAL
 * (Spielmacher setzen sie per GUI/Command), DISABLED.
 *
 * <p>Aktive Positionen und ausgelöste Spawn-Zeitpunkte werden persistiert —
 * Beacons überleben Neustarts, ohne doppelt zu spawnen.</p>
 */
public final class SponsorBeaconService implements Listener {

    private static final NamespacedKey BEACON_KEY =
            new NamespacedKey(JavaPlugin.getPlugin(HungerGamesPlugin.class), "sponsor_beacon");
    private static final int MAX_POSITION_ATTEMPTS = 30;
    private static final int BORDER_MARGIN = 8;

    private final Plugin plugin;
    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final VirtualTime virtualTime;
    private final SoundService sounds;
    private final AnnouncementService announcements;
    private final RoundLogService roundLog;
    private final RuntimeStateStore runtimeStore;
    private final SponsorTokenService tokens;
    private final Random random = new Random();

    private final List<Location> beacons = new ArrayList<>();
    private com.hungergames.platform.effect.EffectService effects;

    public void setEffects(com.hungergames.platform.effect.EffectService effects) {
        this.effects = effects;
    }
    private final Set<Integer> triggeredSpawns = new HashSet<>();
    private BukkitTask timerTask;
    private BukkitTask particleTask;

    public SponsorBeaconService(Plugin plugin, GameSession session, ConfigurationService config,
                                HgSettings settings, VirtualTime virtualTime, SoundService sounds,
                                AnnouncementService announcements, RoundLogService roundLog,
                                RuntimeStateStore runtimeStore, SponsorTokenService tokens) {
        this.plugin = plugin;
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.virtualTime = virtualTime;
        this.sounds = sounds;
        this.announcements = announcements;
        this.roundLog = roundLog;
        this.runtimeStore = runtimeStore;
        this.tokens = tokens;
    }

    /** Lädt persistierte Beacons und startet Zeitplan- und Partikel-Ticks. */
    public void start() {
        for (String raw : runtimeStore.loadSponsorBeaconLocations()) {
            Location location = parseLocation(raw);
            if (location != null) {
                beacons.add(location);
            }
        }
        triggeredSpawns.addAll(runtimeStore.loadSponsorBeaconTriggered());
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 60L, 60L);
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::particles, 40L, 40L);
    }

    public void stop() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
    }

    public boolean beaconsEnabled() {
        return config.get(settings.sponsorsEnabled) && config.get(settings.sponsorBeaconsEnabled);
    }

    public List<Location> activeBeacons() {
        return List.copyOf(beacons);
    }

    public String statusLine() {
        if (!beaconsEnabled()) {
            return "deaktiviert";
        }
        return beacons.size() + " aktiv (Modus: "
                + config.get(settings.sponsorBeaconSpawnMode) + ")";
    }

    // ==================== Spawn-Logik ====================

    private void tick() {
        if (session.phase() != GamePhase.RUNNING || !beaconsEnabled()) {
            return;
        }
        if (config.get(settings.sponsorBeaconSpawnMode) != BeaconSpawnMode.RANDOM_TIMED) {
            return;
        }
        List<Duration> schedule = config.get(settings.sponsorBeaconSchedule).stream()
                .map(SettingType.DURATION::parse)
                .toList();
        for (int index : ScheduleTracker.dueIndices(schedule, triggeredSpawns, virtualTime.elapsed())) {
            triggeredSpawns.add(index);
            persist();
            spawnRandom();
        }
    }

    /** Beacon nahe der Mitte beim Übergang zu RUNNING — gesteuert durch center-on-start. */
    @EventHandler
    public void onPhaseChange(GamePhaseChangeEvent event) {
        // center-on-start ist der maßgebliche Schalter. (Früher war zusätzlich
        // spawn-mode==CENTER verodert — dadurch ließ sich der Center-Beacon nicht
        // abschalten, weil CENTER der Default-Modus ist.)
        boolean centerWanted = config.get(settings.sponsorBeaconCenterOnStart);
        if (event.getNewPhase() == GamePhase.RUNNING
                && beaconsEnabled()
                && centerWanted
                && beacons.isEmpty()) {
            Location center = GameState.getInstance().getArenaCenter();
            if (center != null && center.getWorld() != null) {
                int offset = Math.max(4, config.get(settings.cornucopiaRadius) / 2);
                Block ground = center.getWorld().getHighestBlockAt(
                        center.getBlockX() + offset, center.getBlockZ() + offset);
                createBeacon(ground.getLocation().add(0, 1, 0), "System (CENTER)");
            }
        }
        if (event.getNewPhase() == GamePhase.NOT_INITIALIZED) {
            removeAll("Rundenreset");
            triggeredSpawns.clear();
        }
    }

    private void spawnRandom() {
        Location center = GameState.getInstance().getArenaCenter();
        if (center == null || center.getWorld() == null) {
            return;
        }
        if (beacons.size() >= config.get(settings.sponsorBeaconMaxActive)) {
            roundLog.log("SPONSOR", "Beacon-Spawn übersprungen — max-active erreicht");
            return;
        }
        World world = center.getWorld();
        int radiusMin = config.get(settings.sponsorBeaconRadiusMin);
        int radiusMax = Math.max(radiusMin + 1, config.get(settings.sponsorBeaconRadiusMax));
        double borderLimit = world.getWorldBorder().getSize() / 2.0 - BORDER_MARGIN;

        for (int attempt = 0; attempt < MAX_POSITION_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = radiusMin + random.nextDouble() * (radiusMax - radiusMin);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (Math.abs(x - world.getWorldBorder().getCenter().getBlockX()) > borderLimit
                    || Math.abs(z - world.getWorldBorder().getCenter().getBlockZ()) > borderLimit) {
                continue;
            }
            Block ground = world.getHighestBlockAt(x, z);
            if (ground.isLiquid() || ground.getType() == Material.AIR) {
                continue;
            }
            createBeacon(ground.getLocation().add(0, 1, 0), "System (RANDOM_TIMED)");
            return;
        }
        roundLog.log("SPONSOR", "Beacon-Spawn: keine geeignete Position gefunden");
    }

    // ==================== Erstellen / Entfernen ====================

    /**
     * Erstellt einen Sponsor-Beacon an der Position.
     *
     * @return Fehlermeldung oder leer bei Erfolg
     */
    public Optional<String> createBeacon(Location location, String actor) {
        if (!beaconsEnabled()) {
            return Optional.of("Sponsor-Beacons sind deaktiviert.");
        }
        if (location.getWorld() == null || !location.getWorld()
                .isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return Optional.of("Chunk an der Zielposition ist nicht geladen.");
        }
        if (beacons.size() >= config.get(settings.sponsorBeaconMaxActive)) {
            return Optional.of("Maximal " + config.get(settings.sponsorBeaconMaxActive)
                    + " Beacons gleichzeitig (sponsors.beacons.max-active).");
        }
        Material material = Material.matchMaterial(config.get(settings.sponsorBeaconMaterial));
        if (material == null) {
            material = Material.BEACON;
        }
        Block block = location.getBlock();
        block.setType(material);
        if (material == Material.BEACON) {
            Material base = Material.matchMaterial(config.get(settings.sponsorBeaconBaseMaterial));
            if (base == null) {
                base = Material.IRON_BLOCK;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    block.getRelative(dx, -1, dz).setType(base);
                }
            }
        }
        if (block.getState() instanceof TileState tileState) {
            tileState.getPersistentDataContainer().set(BEACON_KEY, PersistentDataType.BOOLEAN, true);
            tileState.update();
        }
        beacons.add(block.getLocation());
        persist();

        if (config.get(settings.sponsorBeaconAnnounceSpawn)) {
            announcements.broadcast(settings.textSponsorBeaconSpawned,
                    Map.of("coords", coordsText(block.getLocation())), Style.CHAT, Style.TITLE);
        }
        if (config.get(settings.sponsorBeaconSound)) {
            sounds.playToAll(settings.soundSponsorBeaconSpawn);
        }
        if (effects != null) {
            effects.playAt(block.getLocation().add(0.5, 1, 0.5), settings.effectSponsorBeaconSpawn);
        }
        roundLog.log("SPONSOR", "Sponsor-Beacon erstellt von " + actor, block.getLocation());
        return Optional.empty();
    }

    /** Entfernt den nächsten Beacon im Umkreis von 15 Blöcken. */
    public Optional<String> removeNearest(Location from, String actor) {
        Location nearest = null;
        double best = 15 * 15;
        for (Location beacon : beacons) {
            if (!beacon.getWorld().equals(from.getWorld())) {
                continue;
            }
            double distance = beacon.distanceSquared(from);
            if (distance < best) {
                best = distance;
                nearest = beacon;
            }
        }
        if (nearest == null) {
            return Optional.of("Kein Sponsor-Beacon in der Nähe (15 Blöcke).");
        }
        removeBeacon(nearest, actor);
        return Optional.empty();
    }

    public void removeBeacon(Location location, String actor) {
        beacons.remove(location);
        persist();
        Block block = location.getBlock();
        block.setType(Material.AIR);
        roundLog.log("SPONSOR", "Sponsor-Beacon entfernt von " + actor, location);
    }

    public int removeAll(String actor) {
        int count = beacons.size();
        for (Location location : new ArrayList<>(beacons)) {
            removeBeacon(location, actor);
        }
        return count;
    }

    // ==================== Interaktion ====================

    /** Ist dieser Block ein Sponsor-Beacon (Registry oder PDC)? */
    public boolean isSponsorBeacon(Block block) {
        if (beacons.contains(block.getLocation())) {
            return true;
        }
        return block.getState() instanceof TileState tileState
                && tileState.getPersistentDataContainer().has(BEACON_KEY, PersistentDataType.BOOLEAN);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!isSponsorBeacon(block)) {
            return;
        }
        event.setCancelled(true); // kein Vanilla-Beacon-Menü
        Player player = event.getPlayer();

        if (!beaconsEnabled()) {
            announcements.sendTo(player, settings.textSponsorBeaconDisabled, Map.of(), Style.CHAT);
            return;
        }
        if (!config.get(settings.sponsorShopEnabled)) {
            announcements.sendTo(player, settings.textSponsorShopDisabled, Map.of(), Style.CHAT);
            return;
        }
        if (!session.participants().isAlive(player.getUniqueId())) {
            player.sendMessage(com.hungergames.platform.Messages.error(
                    "Nur lebende Tribute können den Sponsor-Shop nutzen."));
            return;
        }
        new com.hungergames.platform.gui.sponsor.SponsorShopMenu(player, config, settings,
                tokens, announcements, sounds, roundLog, false).open();
    }

    /** Sponsor-Beacons + Basis sind (wenn geschützt) nicht abbaubar — außer von Spielmachern. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.get(settings.sponsorBeaconProtected)
                || event.getPlayer().hasPermission(com.hungergames.platform.Perms.GAMEMASTER)) {
            return;
        }
        if (isSponsorBeacon(event.getBlock()) || isBeaconBase(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Explosionen dürfen geschützte Beacons/Basis nicht zerstören. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (config.get(settings.sponsorBeaconProtected)) {
            event.blockList().removeIf(b -> isSponsorBeacon(b) || isBeaconBase(b));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        if (config.get(settings.sponsorBeaconProtected)) {
            event.blockList().removeIf(b -> isSponsorBeacon(b) || isBeaconBase(b));
        }
    }

    /** Ist der Block Teil der 3×3-Basis direkt unter einem Sponsor-Beacon? */
    private boolean isBeaconBase(Block block) {
        if (Material.matchMaterial(config.get(settings.sponsorBeaconMaterial)) != Material.BEACON) {
            return false;
        }
        for (Location beacon : beacons) {
            if (beacon.getWorld() != null && beacon.getWorld().equals(block.getWorld())
                    && beacon.getBlockY() == block.getY() + 1
                    && Math.abs(beacon.getBlockX() - block.getX()) <= 1
                    && Math.abs(beacon.getBlockZ() - block.getZ()) <= 1) {
                return true;
            }
        }
        return false;
    }

    // ==================== Effekte / Persistenz ====================

    private void particles() {
        if (!beaconsEnabled() || !config.get(settings.sponsorBeaconParticles)) {
            return;
        }
        for (Location beacon : beacons) {
            World world = beacon.getWorld();
            if (world != null && world.isChunkLoaded(beacon.getBlockX() >> 4, beacon.getBlockZ() >> 4)) {
                world.spawnParticle(Particle.HAPPY_VILLAGER,
                        beacon.clone().add(0.5, 1.2, 0.5), 6, 0.4, 0.4, 0.4, 0);
            }
        }
    }

    private String coordsText(Location location) {
        if (!config.get(settings.sponsorBeaconAnnounceCoords)) {
            return "";
        }
        int fuzz = config.get(settings.sponsorBeaconCoordinateFuzz);
        int x = location.getBlockX() + (fuzz > 0 ? random.nextInt(fuzz * 2 + 1) - fuzz : 0);
        int z = location.getBlockZ() + (fuzz > 0 ? random.nextInt(fuzz * 2 + 1) - fuzz : 0);
        return " bei §e" + x + " / " + z + "§r" + (fuzz > 0 ? " (ungefähr)" : "");
    }

    private void persist() {
        runtimeStore.saveSponsorBeacons(beacons.stream()
                        .map(loc -> loc.getWorld().getName() + "," + loc.getBlockX() + ","
                                + loc.getBlockY() + "," + loc.getBlockZ())
                        .toList(),
                triggeredSpawns);
    }

    private Location parseLocation(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            plugin.getLogger().warning("Sponsor-Beacon-Welt nicht geladen: " + parts[0]);
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
