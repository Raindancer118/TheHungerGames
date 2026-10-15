package com.hungergames.platform.log;

import com.hungergames.api.event.GamePhaseChangeEvent;
import com.hungergames.api.event.ParticipantEliminatedEvent;
import com.hungergames.api.event.ParticipantKillEvent;
import com.hungergames.api.event.ParticipantRevivedEvent;
import com.hungergames.api.event.WinnerDeclaredEvent;
import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Winner;
import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.config.HgSettings;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Leichtgewichtiges Rundenlog: Phasenwechsel, Kills, Revives, Events und
 * Admin-Aktionen landen als Zeitstempel-Zeilen in
 * {@code plugins/HungerGames/logs/round-&lt;zeit&gt;.log} (eine Datei pro Runde,
 * konfigurierbar).
 *
 * <p>Andere Services loggen über {@link #log(String, String)} — dieses
 * Service hört zusätzlich selbst auf die Kern-Events.</p>
 */
public final class RoundLogService implements Listener {

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter LINE_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final File logsDir;
    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final Logger logger;

    private File currentFile;

    public RoundLogService(File logsDir, GameSession session, ConfigurationService config,
                           HgSettings settings, Logger logger) {
        this.logsDir = logsDir;
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.logger = logger;
    }

    // ==================== Schreiben ====================

    /** Schreibt eine Logzeile, sofern das Rundenlog aktiviert ist. */
    public synchronized void log(String category, String message) {
        if (!config.get(settings.roundLogEnabled)) {
            return;
        }
        File file = targetFile();
        String line = LocalDateTime.now().format(LINE_STAMP)
                + " [" + category + "] " + message + System.lineSeparator();
        try {
            if (!logsDir.exists() && !logsDir.mkdirs()) {
                logger.warning("Rundenlog-Verzeichnis konnte nicht angelegt werden: " + logsDir);
                return;
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
            }
        } catch (IOException e) {
            logger.warning("Rundenlog konnte nicht geschrieben werden: " + e.getMessage());
        }
    }

    /** Wie {@link #log(String, String)}, hängt Koordinaten an, wenn konfiguriert. */
    public void log(String category, String message, Location location) {
        if (location != null && config.get(settings.roundLogCoordinates)) {
            message += String.format(" @ %s %d/%d/%d",
                    location.getWorld() != null ? location.getWorld().getName() : "?",
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
        log(category, message);
    }

    private File targetFile() {
        if (!config.get(settings.roundLogFilePerRound)) {
            return new File(logsDir, "rounds.log");
        }
        if (currentFile == null) {
            currentFile = new File(logsDir,
                    "round-" + LocalDateTime.now().format(FILE_STAMP) + ".log");
        }
        return currentFile;
    }

    /** @return Datei, in die aktuell geschrieben wird (für Status-Anzeigen). */
    public File currentFile() {
        return targetFile();
    }

    // ==================== Kern-Events ====================

    @EventHandler
    public void onPhaseChange(GamePhaseChangeEvent event) {
        // Neue Runde beginnt mit PREFLIGHT → neue Logdatei
        if (event.getNewPhase() == GamePhase.PREFLIGHT) {
            synchronized (this) {
                currentFile = null;
            }
        }
        log("PHASE", event.getOldPhase() + " → " + event.getNewPhase());
    }

    @EventHandler
    public void onKill(ParticipantKillEvent event) {
        log("KILL", name(event.getKiller()) + " tötet " + name(event.getVictim())
                + " (Kills gesamt: " + event.getKillerTotalKills() + ")");
    }

    @EventHandler
    public void onEliminated(ParticipantEliminatedEvent event) {
        log("ELIMINATION", name(event.getParticipant())
                + event.getKiller().map(k -> " durch " + name(k)).orElse("")
                + " — " + event.getRemainingAlive() + " Tribute verbleiben");
    }

    @EventHandler
    public void onRevived(ParticipantRevivedEvent event) {
        log("REVIVE", name(event.getParticipant()) + " wiederbelebt");
    }

    @EventHandler
    public void onWinner(WinnerDeclaredEvent event) {
        log("WINNER", describeWinner(event.getWinner()));
    }

    private String name(UUID uuid) {
        return session.participants().nameOf(uuid).orElse(uuid.toString());
    }

    private String describeWinner(Winner winner) {
        return switch (winner) {
            case Winner.Solo solo -> "Solo-Sieger: " + name(solo.uuid());
            case Winner.Team team -> "Team-Sieger: " + session.teamRegistry().team(team.teamId())
                    .map(t -> t.name()).orElse(team.teamId().value());
            case Winner.None none -> "Kein Sieger";
        };
    }
}
