package com.hungergames.platform.sim;

import com.hungergames.GameState;
import com.hungergames.HungerGamesPlugin;
import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.game.GameSession;
import com.hungergames.game.TeamRules;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.port.GameEventPublisher.MembershipCause;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.log.RoundLogService;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Test-Simulation mit Mannequins: spawnt Attrappen-Tribute, die echte
 * {@link GameSession}-Teilnehmer auf automatisch angelegten Test-Teams sind.
 * Dadurch durchlaufen sie exakt dieselben Eliminierungs-, Sieger-, Deathmatch-
 * und Sound-Flows wie echte Spieler — ein einzelner Admin kann so ein ganzes
 * Spiel simulieren.
 *
 * <p>Eliminierung: die Mannequins im laufenden Spiel schlagen/beschießen ODER
 * per Menü-Button. Beide Wege rufen {@link GameSession#eliminate(UUID, UUID)}.</p>
 *
 * <p>Erkennung: Entity-PDC {@code sim_mannequin} = Teilnehmer-UUID; die
 * synthetischen Teilnehmer hängen an Teams mit Präfix {@code Test-Team}. Ein
 * Serverneustart räumt eine laufende Simulation auf ({@link #purgeStaleSimulation()}).</p>
 */
public final class MannequinSimService implements Listener {

    private static final String TEST_TEAM_PREFIX = "Test-Team ";
    private static final NamespacedKey MARKER =
            new NamespacedKey(JavaPlugin.getPlugin(HungerGamesPlugin.class), "sim_mannequin");

    private final HungerGamesPlugin plugin;
    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final RoundLogService roundLog;

    /** Teilnehmer-UUID -> Entity-UUID der zugehörigen Mannequin-Attrappe. */
    private final Map<UUID, UUID> entityByParticipant = new LinkedHashMap<>();
    private final List<TeamId> simTeams = new ArrayList<>();

    private int tributeCounter;
    private int teamCounter;
    private TeamId fillTeam;
    private int fillCount;
    private com.hungergames.platform.effect.EffectService effects;

    public void setEffects(com.hungergames.platform.effect.EffectService effects) {
        this.effects = effects;
    }

    public MannequinSimService(HungerGamesPlugin plugin, GameSession session,
                               ConfigurationService config, HgSettings settings,
                               RoundLogService roundLog) {
        this.plugin = plugin;
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.roundLog = roundLog;
    }

    // ==================== Status ====================

    public int mannequinCount() {
        return entityByParticipant.size();
    }

    public int aliveCount() {
        int alive = 0;
        for (UUID uuid : entityByParticipant.keySet()) {
            if (session.participants().isAlive(uuid)) {
                alive++;
            }
        }
        return alive;
    }

    public int teamCount() {
        return simTeams.size();
    }

    /** Spawnen ist nur möglich, solange Teams noch nicht gesperrt sind (< STARTUP). */
    public boolean canSpawn() {
        return !teamsLocked();
    }

    private boolean teamsLocked() {
        return TeamRules.defaults().isLocked(session.phase());
    }

    // ==================== Spawn ====================

    /**
     * Spawnt {@code count} Mannequin-Tribute, verteilt auf Test-Teams, die bis
     * zur konfigurierten Teamgröße gefüllt werden.
     *
     * @return kurze Ergebnismeldung
     */
    public String spawn(Player admin, int count) {
        if (teamsLocked()) {
            return "Mannequins können nur vor der Startsequenz gespawnt werden (Phase "
                    + session.phase() + ").";
        }
        if (count <= 0) {
            return "Anzahl muss positiv sein.";
        }
        int teamSize = Math.max(1, config.get(settings.teamMaxSize));

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            TeamId team = ensureFillTeam(teamSize);
            if (team == null) {
                return spawned + "/" + count + " Mannequins gespawnt — keine freien Team-Farben/-Slots mehr.";
            }
            UUID uuid = UUID.randomUUID();
            String name = "Tribut-" + String.format("%02d", ++tributeCounter);
            if (!session.whitelistAdd(uuid, name)) {
                continue;
            }
            session.teamAssign(uuid, team, MembershipCause.API);
            fillCount++;

            TeamColor color = session.teamRegistry().team(team)
                    .map(Team::color).orElse(TeamColor.WHITE);
            entityByParticipant.put(uuid, spawnEntity(admin, uuid, name, team, color, spawned));
            spawned++;
        }
        roundLog.log("SIM", admin.getName() + " spawnte " + spawned + " Mannequins", admin.getLocation());
        return spawned + " Mannequin-Tribute gespawnt (" + simTeams.size() + " Test-Teams).";
    }

    /** Liefert ein Team mit freiem Slot; legt bei Bedarf ein neues Test-Team an. */
    private TeamId ensureFillTeam(int teamSize) {
        if (fillTeam != null && fillCount < teamSize && session.teamRegistry().team(fillTeam).isPresent()) {
            return fillTeam;
        }
        String name = TEST_TEAM_PREFIX + (++teamCounter);
        var result = session.teamCreate(name, null);
        if (result.team().isEmpty()) {
            teamCounter--;
            return null;
        }
        fillTeam = result.team().get().id();
        fillCount = 0;
        simTeams.add(fillTeam);
        return fillTeam;
    }

    private UUID spawnEntity(Player admin, UUID participant, String name, TeamId teamId,
                             TeamColor color, int index) {
        Location base = spawnBase(admin);
        // Attrappen leicht im Ring um den Admin verteilen
        double angle = index * 0.9;
        Location loc = base.clone().add(Math.cos(angle) * (1.5 + index * 0.15), 0,
                Math.sin(angle) * (1.5 + index * 0.15));
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        String teamName = session.teamRegistry().team(teamId).map(Team::name).orElse("Test");
        Mannequin mannequin = world.spawn(loc, Mannequin.class, m -> {
            m.setImmovable(true);
            m.setPersistent(true);
            m.setRemoveWhenFarAway(false);
            m.customName(Component.text(name, color.textColor()));
            m.setCustomNameVisible(true);
            m.setDescription(Component.text(teamName));
            m.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING,
                    participant.toString());
            equip(m, color);
        });
        return mannequin.getUniqueId();
    }

    private void equip(Mannequin mannequin, TeamColor color) {
        if (mannequin.getEquipment() == null) {
            return;
        }
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        if (helmet.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(color.dyeColor().getColor().asRGB()));
            helmet.setItemMeta(meta);
        }
        mannequin.getEquipment().setHelmet(helmet);
    }

    private Location spawnBase(Player admin) {
        Location lobby = GameState.getInstance().getLobbyCenter();
        if (lobby != null && lobby.getWorld() != null) {
            return lobby;
        }
        return admin.getLocation();
    }

    // ==================== Eliminierung ====================

    /** Menü-Weg: eliminiert eine noch lebende Attrappe (durch den Admin). */
    public Optional<String> eliminateOne(Player actor) {
        if (session.phase() != GamePhase.RUNNING) {
            return Optional.of("Eliminieren geht nur im laufenden Spiel (Phase " + session.phase() + ").");
        }
        for (UUID uuid : new ArrayList<>(entityByParticipant.keySet())) {
            if (session.participants().isAlive(uuid)) {
                eliminate(uuid, actor.getUniqueId());
                return Optional.empty();
            }
        }
        return Optional.of("Keine lebende Attrappe mehr übrig.");
    }

    private void eliminate(UUID participant, UUID killer) {
        UUID entityId = entityByParticipant.get(participant);
        Location deathLoc = null;
        if (entityId != null) {
            Entity entity = plugin.getServer().getEntity(entityId);
            if (entity != null) {
                deathLoc = entity.getLocation();
            }
        }
        // Fährt die vollen Flows hoch: Kanonenschuss, Sounds, Winner-Check.
        boolean eliminated = session.eliminate(participant, killer);
        if (!eliminated) {
            return;
        }
        if (entityId != null) {
            Entity entity = plugin.getServer().getEntity(entityId);
            if (entity != null) {
                if (deathLoc != null && deathLoc.getWorld() != null) {
                    deathLoc.getWorld().strikeLightningEffect(deathLoc);
                    if (effects != null) {
                        effects.playAt(deathLoc.clone().add(0, 1, 0),
                                killer != null ? settings.effectKill : settings.effectElimination);
                    }
                }
                entity.remove();
            }
        }
    }

    // ==================== Aufräumen ====================

    /** Entfernt alle Attrappen, ihre Teilnehmer und die Test-Teams. */
    public int clear() {
        int removed = entityByParticipant.size();
        for (Map.Entry<UUID, UUID> entry : entityByParticipant.entrySet()) {
            Entity entity = entry.getValue() == null ? null : plugin.getServer().getEntity(entry.getValue());
            if (entity != null) {
                entity.remove();
            }
            session.whitelistRemove(entry.getKey());
        }
        for (TeamId team : new ArrayList<>(simTeams)) {
            session.teamDelete(team);
        }
        entityByParticipant.clear();
        simTeams.clear();
        fillTeam = null;
        fillCount = 0;
        tributeCounter = 0;
        teamCounter = 0;
        return removed;
    }

    /**
     * Räumt bei Serverstart eine ggf. hängengebliebene Simulation auf: entfernt
     * verwaiste Attrappen-Entities und löst Test-Team-Teilnehmer aus der Session.
     */
    public void purgeStaleSimulation() {
        int entities = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Mannequin mannequin : world.getEntitiesByClass(Mannequin.class)) {
                if (mannequin.getPersistentDataContainer().has(MARKER, PersistentDataType.STRING)) {
                    mannequin.remove();
                    entities++;
                }
            }
        }
        List<Team> testTeams = session.teamRegistry().all().stream()
                .filter(t -> t.name().startsWith(TEST_TEAM_PREFIX))
                .toList();
        for (Team team : testTeams) {
            for (UUID member : new ArrayList<>(team.members())) {
                session.whitelistRemove(member);
            }
            session.teamDelete(team.id());
        }
        if (entities > 0 || !testTeams.isEmpty()) {
            plugin.getLogger().info("Test-Simulation aufgeräumt: " + entities
                    + " Attrappen, " + testTeams.size() + " Test-Teams entfernt.");
        }
    }

    // ==================== Events ====================

    /**
     * Sobald die Startsequenz READY erreicht, werden die Attrappen auf die
     * Plattformen gesetzt — so sind sie wie echte Tribute „in Position" und
     * werden von Startup/Ablauf berücksichtigt. Von hinten befüllt, damit sie
     * mit echten Spielern (die von vorn platziert werden) möglichst nicht
     * kollidieren.
     */
    @EventHandler
    public void onPhaseChange(com.hungergames.api.event.GamePhaseChangeEvent event) {
        if (event.getNewPhase() != GamePhase.READY || entityByParticipant.isEmpty()) {
            return;
        }
        List<Location> platforms = GameState.getInstance().getPlatformPositions();
        if (platforms.isEmpty()) {
            return;
        }
        int j = 0;
        for (UUID participant : entityByParticipant.keySet()) {
            UUID entityId = entityByParticipant.get(participant);
            Entity entity = entityId == null ? null : plugin.getServer().getEntity(entityId);
            if (entity != null) {
                Location platform = platforms.get(Math.floorMod(platforms.size() - 1 - j, platforms.size()));
                entity.teleport(platform.clone().add(0.5, 1, 0.5));
            }
            j++;
        }
    }

    /**
     * Attrappen nehmen nie regulären Schaden (sie stehen stabil als Tribute) —
     * ein Spielertreffer im laufenden Spiel eliminiert sie stattdessen. Ein
     * einzelner Handler auf {@link EntityDamageEvent} deckt auch die Untertypen
     * {@link EntityDamageByEntityEvent} ab (gemeinsame HandlerList), sodass es
     * keine Reihenfolgen-Konflikte zwischen zwei Handlern gibt.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!isSimMannequin(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        if (session.phase() != GamePhase.RUNNING
                || !(event instanceof EntityDamageByEntityEvent byEntity)) {
            return; // Vor dem Spiel bzw. bei Umgebungsschaden nur unverwundbar
        }
        UUID killer = null;
        if (byEntity.getDamager() instanceof Player p) {
            killer = p.getUniqueId();
        } else if (byEntity.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) {
            killer = p.getUniqueId();
        }
        String tag = event.getEntity().getPersistentDataContainer()
                .get(MARKER, PersistentDataType.STRING);
        if (tag != null) {
            eliminate(UUID.fromString(tag), killer);
        }
    }

    private boolean isSimMannequin(Entity entity) {
        return entity instanceof Mannequin
                && entity.getPersistentDataContainer().has(MARKER, PersistentDataType.STRING);
    }
}
