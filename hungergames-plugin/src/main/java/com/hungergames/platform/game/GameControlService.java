package com.hungergames.platform.game;

import com.hungergames.GameState;
import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import com.hungergames.platform.Messages;
import com.hungergames.platform.log.RoundLogService;
import com.hungergames.stagerunner.PreflightRunner;
import com.hungergames.stagerunner.StartRunner;
import com.hungergames.stagerunner.StartupRunner;
import org.bukkit.entity.Player;

/**
 * Spielablauf-Steuerung für GUI und Commands: kapselt die Stage-Runner mit
 * Phasen-Vorbedingungen, damit GUI-Buttons echte Services rufen statt
 * Command-Strings zu faken.
 */
public final class GameControlService {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 100;

    private final GameSession session;
    private final RoundLogService roundLog;

    public GameControlService(GameSession session, RoundLogService roundLog) {
        this.session = session;
        this.roundLog = roundLog;
    }

    // ==================== Vorbedingungen ====================

    public boolean canInit() {
        GamePhase phase = session.phase();
        return phase == GamePhase.NOT_INITIALIZED || phase == GamePhase.FINISHED;
    }

    public boolean canStartup() {
        return session.phase() == GamePhase.LOBBY;
    }

    /** Start ist nur in READY möglich — und nicht, während schon ein Countdown läuft. */
    public boolean canStart() {
        return session.phase() == GamePhase.READY && !StartRunner.isCountdownActive();
    }

    public boolean canEndRound() {
        return session.phase() == GamePhase.RUNNING;
    }

    // ==================== Aktionen ====================

    /**
     * Initialisiert die Arena an der Position des Admins (wie {@code /init}).
     *
     * @return {@code false}, wenn die Phase das nicht zulässt
     */
    public boolean init(Player admin, int playerCount) {
        if (!canInit()) {
            admin.sendMessage(Messages.error("Arena ist bereits initialisiert — erst Runde beenden."));
            return false;
        }
        if (playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) {
            admin.sendMessage(Messages.error("Spielerzahl muss zwischen " + MIN_PLAYERS
                    + " und " + MAX_PLAYERS + " liegen."));
            return false;
        }
        if (session.phase() == GamePhase.FINISHED) {
            GameState.getInstance().reset();
        }
        GameState.getInstance().setArenaCenter(admin.getLocation());
        // Wie /init: Vorbereitung sofort friedlich, bis /start released.
        admin.getWorld().setDifficulty(
                com.hungergames.utils.ConfigManager.getInstance().getPreflightDifficulty());
        roundLog.log("ADMIN", admin.getName() + " initialisiert Arena für "
                + playerCount + " Tribute", admin.getLocation());
        new PreflightRunner().setup(admin.getUniqueId(), playerCount);
        return true;
    }

    /** Startsequenz (Röhren/Levitation), wie {@code /startup}. */
    public boolean startup(Player admin) {
        if (!canStartup()) {
            admin.sendMessage(Messages.error("Startsequenz nur in der Phase LOBBY möglich (aktuell: "
                    + session.phase() + ")."));
            return false;
        }
        roundLog.log("ADMIN", admin.getName() + " löst die Startsequenz aus");
        new StartupRunner().execute(admin.getUniqueId());
        return true;
    }

    /** Countdown und Spielstart, wie {@code /start}. */
    public boolean start(Player admin) {
        if (StartRunner.isCountdownActive()) {
            admin.sendMessage(Messages.error("Der Countdown läuft bereits — die Spiele starten gleich."));
            return false;
        }
        if (!canStart()) {
            admin.sendMessage(Messages.error("Start nur in der Phase READY möglich (aktuell: "
                    + session.phase() + ")."));
            return false;
        }
        if (!new StartRunner().execute(admin.getUniqueId())) {
            return false;
        }
        roundLog.log("ADMIN", admin.getName() + " startet die Spiele");
        return true;
    }

    /** Beendet die laufende Runde per Zeitablauf-Wertung (liefert immer einen Sieger-Entscheid). */
    public boolean endRound(String actor) {
        if (!canEndRound()) {
            return false;
        }
        roundLog.log("ADMIN", actor + " beendet die Runde manuell");
        session.declareTimeout();
        return true;
    }

    /** Bereitet die nächste Runde vor: Geometrie + Rundenzustand zurücksetzen, Teilnehmer bleiben. */
    public void prepareNextRound(String actor) {
        roundLog.log("ADMIN", actor + " setzt die Runde zurück (Teilnehmer bleiben registriert)");
        GameState.getInstance().reset();
    }

    public GameSession session() {
        return session;
    }
}
