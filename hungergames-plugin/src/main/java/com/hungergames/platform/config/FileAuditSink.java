package com.hungergames.platform.config;

import com.hungergames.game.config.ConfigurationService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Audit-Log als JSON-Lines-Datei ({@code plugins/HungerGames/audit.log}):
 * eine Zeile pro bestätigter Konfigurationsänderung.
 */
public final class FileAuditSink implements ConfigurationService.AuditSink {

    private final Path file;
    private final Logger logger;

    public FileAuditSink(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public void record(ConfigurationService.AuditEntry entry) {
        String line = "{\"time\":\"" + Instant.ofEpochMilli(entry.timestampMillis())
                + "\",\"actor\":\"" + escape(entry.actor())
                + "\",\"key\":\"" + escape(entry.key())
                + "\",\"old\":\"" + escape(entry.oldValue())
                + "\",\"new\":\"" + escape(entry.newValue())
                + "\",\"scope\":\"" + entry.scope() + "\"}\n";
        try {
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.warning("Audit-Log konnte nicht geschrieben werden: " + e.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
