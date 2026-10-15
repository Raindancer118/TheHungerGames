package com.hungergames.platform.deathmatch;

import com.hungergames.GameState;
import com.hungergames.api.event.GamePhaseChangeEvent;
import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.announce.AnnouncementService;
import com.hungergames.platform.announce.AnnouncementService.Style;
import com.hungergames.platform.border.BorderService;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.log.RoundLogService;
import com.hungergames.platform.persistence.RuntimeStateStore;
import com.hungergames.platform.sound.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manuell ausgelöstes Deathmatch: Warnphase mit Countdown (abbrechbar),
 * danach übernimmt es die Border und schrumpft sie auf die Zielgröße;
 * optional werden lebende Tribute zur Mitte teleportiert und erhalten eine
 * kurze Schutzzeit.
 *
 * <p>Ein aktives Deathmatch wird in {@code runtime.yml} vermerkt: Nach einem
 * Server-Neustart feuern die regulären Border-Phasen nicht erneut (Vanilla
 * setzt die laufende Schrumpfung selbst fort). Eine Warnphase überlebt den
 * Neustart bewusst nicht.</p>
 */
public final class DeathmatchService implements Listener {

    /** Zustand des Deathmatch-Ablaufs. */
    public enum State {
        IDLE,
        WARNING,
        ACTIVE
    }

    private static final Set<Long> COUNTDOWN_MARKS = Set.of(30L, 10L, 5L, 4L, 3L, 2L, 1L);

    private final Plugin plugin;
    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final BorderService border;
    private final SoundService sounds;
    private final AnnouncementService announcements;
    private final RoundLogService roundLog;
    private final RuntimeStateStore runtimeStore;
    private com.hungergames.platform.effect.EffectService effects;

    public void setEffects(com.hungergames.platform.effect.EffectService effects) {
        this.effects = effects;
    }

    private State state = State.IDLE;
    private BukkitTask warningTask;
    private long graceUntilMillis;

    public DeathmatchService(Plugin plugin, GameSession session, ConfigurationService config,
                             HgSettings settings, BorderService border, SoundService sounds,
                             AnnouncementService announcements, RoundLogService roundLog,
                             RuntimeStateStore runtimeStore) {
        this.plugin = plugin;
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.border = border;
        this.sounds = sounds;
        this.announcements = announcements;
        this.roundLog = roundLog;
        this.runtimeStore = runtimeStore;
    }

    /** Stellt ein aktives Deathmatch nach einem Neustart wieder her. */
    public void restoreFromStore() {
        if (runtimeStore.loadDeathmatchActive() && session.phase() == GamePhase.RUNNING) {
            state = State.ACTIVE;
            // Reguläre Border-Phasen dürfen nach dem Restore nicht erneut feuern.
            border.overrideShrinkTo(config.get(settings.deathmatchTargetBorderSize));
            roundLog.log("DEATHMATCH", "Aktives Deathmatch nach Neustart wiederhergestellt");
        }
    }

    public State state() {
        return state;
    }

    /** Menschenlesbarer Status für {@code /hg status} und GUIs. */
    public String statusLine() {
        return switch (state) {
            case IDLE -> config.get(settings.deathmatchEnabled)
                    ? "bereit (manuell auslösbar)" : "deaktiviert";
            case WARNING -> "Warnphase läuft — abbrechbar";
            case ACTIVE -> "AKTIV — Border-Ziel "
                    + config.get(settings.deathmatchTargetBorderSize) + " Blöcke";
        };
    }

    // ==================== Start / Abbruch ====================

    /**
     * Startet die Deathmatch-Warnphase.
     *
     * @return Fehlermeldung oder leer bei Erfolg
     */
    public Optional<String> start(String actor) {
        if (!config.get(settings.deathmatchEnabled)) {
            return Optional.of("Deathmatch ist deaktiviert (deathmatch.enabled).");
        }
        if (state != State.IDLE) {
            return Optional.of("Deathmatch läuft bereits (" + state + ").");
        }
        boolean phaseAllowed = config.get(settings.deathmatchAllowedPhases).stream()
                .anyMatch(phase -> phase.equalsIgnoreCase(session.phase().name()));
        if (!phaseAllowed) {
            return Optional.of("Deathmatch ist in Phase " + session.phase() + " nicht erlaubt.");
        }

        state = State.WARNING;
        roundLog.log("DEATHMATCH", actor + " löst das Deathmatch aus — Warnphase beginnt");
        int warningSeconds = config.get(settings.deathmatchWarningSeconds);
        if (warningSeconds <= 0) {
            execute();
            return Optional.empty();
        }

        announceWarning(warningSeconds);
        warningTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private long remaining = warningSeconds;

            @Override
            public void run() {
                remaining--;
                if (remaining <= 0) {
                    execute();
                    return;
                }
                if (COUNTDOWN_MARKS.contains(remaining)) {
                    announceWarning(remaining);
                }
            }
        }, 20L, 20L);
        return Optional.empty();
    }

    /**
     * Bricht die Warnphase ab (nur solange das Deathmatch nicht ausgeführt wurde).
     *
     * @return Fehlermeldung oder leer bei Erfolg
     */
    public Optional<String> cancel(String actor) {
        if (state != State.WARNING) {
            return Optional.of(state == State.ACTIVE
                    ? "Das Deathmatch läuft bereits — Abbruch nicht mehr möglich."
                    : "Es läuft keine Deathmatch-Warnphase.");
        }
        stopWarningTask();
        state = State.IDLE;
        roundLog.log("DEATHMATCH", actor + " bricht das Deathmatch ab");
        if (config.get(settings.deathmatchBroadcast)) {
            Bukkit.broadcast(com.hungergames.platform.Messages.info(
                    "Die Spielmacher haben das Deathmatch abgeblasen — vorerst."));
        }
        return Optional.empty();
    }

    // ==================== Ausführung ====================

    private void execute() {
        stopWarningTask();
        if (session.phase() != GamePhase.RUNNING) {
            state = State.IDLE;
            return;
        }
        state = State.ACTIVE;
        runtimeStore.saveDeathmatchActive(true);

        int target = config.get(settings.deathmatchTargetBorderSize);
        long shrinkSeconds = border.overrideShrinkTo(target);
        roundLog.log("DEATHMATCH", "Deathmatch beginnt — Border → " + target
                + " Blöcke in " + shrinkSeconds + "s");

        if (config.get(settings.deathmatchTeleportToCenter)) {
            teleportTributesToCenter();
        }

        if (config.get(settings.deathmatchBroadcast)) {
            announcements.broadcast(settings.textDeathmatchStart, Map.of(),
                    Style.CHAT, Style.TITLE);
        }
        if (config.get(settings.deathmatchSound)) {
            sounds.playToAll(settings.soundDeathmatchStart);
        }
        if (effects != null) {
            Location center = GameState.getInstance().getArenaCenter();
            if (center != null) {
                effects.playAt(center.clone().add(0, 1, 0), settings.effectDeathmatchStart);
            }
        }
    }

    private void teleportTributesToCenter() {
        Location center = GameState.getInstance().getArenaCenter();
        if (center == null) {
            roundLog.log("DEATHMATCH", "Teleport übersprungen — kein Arena-Zentrum bekannt");
            return;
        }
        Location target = center.clone().add(0, config.get(settings.deathmatchTeleportYOffset), 0);
        Duration grace = config.get(settings.deathmatchGraceAfterTeleport);
        graceUntilMillis = grace.isZero() ? 0
                : System.currentTimeMillis() + grace.toMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (session.participants().isAlive(player.getUniqueId())) {
                player.teleport(target);
            }
        }
        roundLog.log("DEATHMATCH", "Lebende Tribute zur Mitte teleportiert"
                + (grace.isZero() ? "" : " (Schutzzeit " + grace.toSeconds() + "s)"), target);
    }

    private void announceWarning(long remainingSeconds) {
        if (config.get(settings.deathmatchBroadcast)) {
            announcements.broadcast(settings.textDeathmatchWarning, Map.of(
                            "seconds", String.valueOf(remainingSeconds),
                            "size", String.valueOf(config.get(settings.deathmatchTargetBorderSize))),
                    Style.CHAT, Style.ACTIONBAR);
        }
        if (config.get(settings.deathmatchSound)) {
            sounds.playToAll(settings.soundDeathmatchWarning);
        }
    }

    private void stopWarningTask() {
        if (warningTask != null) {
            warningTask.cancel();
            warningTask = null;
        }
    }

    // ==================== Schutzzeit & Rundenende ====================

    /** Kurze Unverwundbarkeit lebender Tribute nach dem Deathmatch-Teleport. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (graceUntilMillis == 0 || System.currentTimeMillis() >= graceUntilMillis) {
            return;
        }
        if (event.getEntity() instanceof Player player
                && session.participants().isAlive(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Automatik-Modus ({@code deathmatch.manual-only: false}): Sobald nur noch
     * zwei Tribute leben, starten die Spielmacher das Finale von selbst.
     */
    @EventHandler
    public void onEliminated(com.hungergames.api.event.ParticipantEliminatedEvent event) {
        if (state != State.IDLE
                || config.get(settings.deathmatchManualOnly)
                || !config.get(settings.deathmatchEnabled)) {
            return;
        }
        if (event.getRemainingAlive() == 2) {
            start("Automatik (2 Tribute)").ifPresent(error ->
                    roundLog.log("DEATHMATCH", "Automatik-Start nicht möglich: " + error));
        }
    }

    @EventHandler
    public void onPhaseChange(GamePhaseChangeEvent event) {
        if (event.getNewPhase() == GamePhase.FINISHED
                || event.getNewPhase() == GamePhase.NOT_INITIALIZED) {
            stopWarningTask();
            if (state != State.IDLE) {
                state = State.IDLE;
                graceUntilMillis = 0;
                runtimeStore.saveDeathmatchActive(false);
                roundLog.log("DEATHMATCH", "Deathmatch-Zustand zurückgesetzt (Rundenende)");
            }
        }
    }
}
