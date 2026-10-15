package com.hungergames.platform.config;

import com.hungergames.game.config.SettingDefinition;
import com.hungergames.game.config.SettingsRegistry;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Schreibt eine vollständige Referenz aller Einstellungen nach
 * {@code config-reference.yml}: jeder Key mit Defaultwert, Beschreibung und
 * Anwendungszeitpunkt als Kommentar. Die Datei ist reine Doku und wird bei
 * jedem Start neu generiert — zum Anpassen den Key in die {@code config.yml}
 * übernehmen und {@code /config-reload} ausführen (oder in-game /hg config).
 */
public final class ConfigReferenceWriter {

    private ConfigReferenceWriter() {
    }

    public static void write(File file, SettingsRegistry registry, Logger logger) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Referenz aller HungerGames-Einstellungen mit ihren Defaultwerten.",
                "Diese Datei wird bei jedem Start neu generiert — Änderungen HIER wirken nicht.",
                "Zum Anpassen: Zeile in die config.yml übernehmen, Wert ändern und",
                "/config-reload ausführen (oder in-game /hg config nutzen)."));
        for (SettingDefinition<?> def : registry.all()) {
            setWithComment(yaml, def);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.warning("config-reference.yml konnte nicht geschrieben werden: " + e.getMessage());
        }
    }

    private static <T> void setWithComment(YamlConfiguration yaml, SettingDefinition<T> def) {
        yaml.set(def.key(), def.type().serialize(def.defaultValue()));
        yaml.setComments(def.key(), List.of(
                def.description() + " (wirksam: " + def.applyTiming() + ")"));
    }
}
