package com.hungergames.platform.game;

import com.hungergames.GameState;
import com.hungergames.api.event.GamePhaseChangeEvent;
import com.hungergames.api.event.ParticipantEliminatedEvent;
import com.hungergames.api.event.ParticipantRevivedEvent;
import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.Messages;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.log.RoundLogService;
import com.hungergames.platform.persistence.RuntimeStateStore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Deop/Re-Op-System für mitspielende Admins: OP-Tribute werden beim Übergang
 * zu RUNNING deoped und bei Eliminierung bzw. Rundenende wieder geoped.
 * Der Snapshot der deoppten Admins liegt in {@code runtime.yml}, damit ein
 * Server-Neustart während der Runde keinen OP-Status verliert.
 *
 * <p>Spielmacher, die nicht als Tribut registriert sind, sind keine
 * Teilnehmer und werden nie angefasst.</p>
 */
public final class OpTrackerService implements Listener {

    private static final long POST_RESPAWN_DELAY_TICKS = 10L;

    private final Plugin plugin;
    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final RuntimeStateStore runtimeStore;
    private final RoundLogService roundLog;

    private final Set<UUID> deoppedAdmins = new HashSet<>();

    public OpTrackerService(Plugin plugin, GameSession session, ConfigurationService config,
                            HgSettings settings, RuntimeStateStore runtimeStore,
                            RoundLogService roundLog) {
        this.plugin = plugin;
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.runtimeStore = runtimeStore;
        this.roundLog = roundLog;
    }

    /** Lädt den Snapshot nach einem Neustart (vor dem Resume der Runde rufen). */
    public void restoreFromStore() {
        deoppedAdmins.clear();
        deoppedAdmins.addAll(runtimeStore.loadOpSnapshot());
        if (!deoppedAdmins.isEmpty()) {
            roundLog.log("OP", "OP-Snapshot wiederhergestellt: "
                    + deoppedAdmins.size() + " deoppte Admin(s)");
        }
    }

    /** UUIDs der aktuell deoppten Admin-Tribute (für Status-Anzeigen/Preflight). */
    public Set<UUID> deoppedAdmins() {
        return Set.copyOf(deoppedAdmins);
    }

    /** OP-Tribute, die beim nächsten Start deoped würden (Deop-Plan für Preflight). */
    public Set<UUID> opParticipants() {
        Set<UUID> result = new HashSet<>();
        for (var participant : session.participants().all()) {
            if (Bukkit.getOfflinePlayer(participant.uuid()).isOp()) {
                result.add(participant.uuid());
            }
        }
        return result;
    }

    // ==================== Phasen ====================

    @EventHandler
    public void onPhaseChange(GamePhaseChangeEvent event) {
        if (event.getNewPhase() == GamePhase.RUNNING) {
            deopParticipatingAdmins();
        } else if (event.getNewPhase() == GamePhase.FINISHED) {
            restoreAllOps();
        }
    }

    private void deopParticipatingAdmins() {
        if (!config.get(settings.adminDeopOnStart)) {
            return;
        }
        for (var participant : session.participants().all()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(participant.uuid());
            if (!offline.isOp()) {
                continue;
            }
            offline.setOp(false);
            deoppedAdmins.add(participant.uuid());
            roundLog.log("OP", participant.lastKnownName() + " (Tribut) deoped für die Runde");
            Player online = offline.getPlayer();
            if (online != null) {
                online.sendMessage(Messages.info(
                        "Du spielst mit — dein OP ruht bis zu deiner Eliminierung bzw. dem Rundenende."));
            }
        }
        runtimeStore.saveOpSnapshot(deoppedAdmins);
    }

    private void restoreAllOps() {
        if (deoppedAdmins.isEmpty()) {
            return;
        }
        if (!config.get(settings.adminReopOnFinish)) {
            return;
        }
        for (UUID uuid : Set.copyOf(deoppedAdmins)) {
            reop(uuid, "Rundenende");
        }
        deoppedAdmins.clear();
        runtimeStore.saveOpSnapshot(deoppedAdmins);
    }

    // ==================== Eliminierung / Revive ====================

    @EventHandler
    public void onEliminated(ParticipantEliminatedEvent event) {
        UUID uuid = event.getParticipant();
        if (!deoppedAdmins.contains(uuid)) {
            return;
        }
        if (config.get(settings.adminReopOnElimination)) {
            reop(uuid, "Eliminierung");
            deoppedAdmins.remove(uuid);
            runtimeStore.saveOpSnapshot(deoppedAdmins);
        }

        // Creative/Teleport erst NACH Respawn und Death-Action anwenden,
        // sonst überschreibt der EliminationListener den Spielmodus wieder.
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        boolean creative = config.get(settings.adminCreativeOnElimination);
        boolean teleport = config.get(settings.adminTeleportCenterOnElimination);
        if (!creative && !teleport) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player current = Bukkit.getPlayer(uuid);
            if (current == null) {
                return;
            }
            if (creative) {
                current.setGameMode(GameMode.CREATIVE);
            }
            if (teleport) {
                Location center = GameState.getInstance().getArenaCenter();
                if (center != null) {
                    current.teleport(center.clone().add(0,
                            config.get(settings.adminCenterYOffset), 0));
                }
            }
        }, POST_RESPAWN_DELAY_TICKS);
    }

    /** Wiederbelebte Admins spielen wieder mit — OP ruht erneut. */
    @EventHandler
    public void onRevived(ParticipantRevivedEvent event) {
        if (session.phase() != GamePhase.RUNNING || !config.get(settings.adminDeopOnStart)) {
            return;
        }
        UUID uuid = event.getParticipant();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.isOp()) {
            offline.setOp(false);
            deoppedAdmins.add(uuid);
            runtimeStore.saveOpSnapshot(deoppedAdmins);
            roundLog.log("OP", session.participants().nameOf(uuid).orElse(uuid.toString())
                    + " wiederbelebt — erneut deoped");
        }
    }

    private void reop(UUID uuid, String reason) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        offline.setOp(true);
        roundLog.log("OP", session.participants().nameOf(uuid).orElse(uuid.toString())
                + " re-oped (" + reason + ")");
        Player online = offline.getPlayer();
        if (online != null) {
            online.sendMessage(Messages.ok("Dein OP-Status wurde wiederhergestellt (" + reason + ")."));
        }
    }
}
