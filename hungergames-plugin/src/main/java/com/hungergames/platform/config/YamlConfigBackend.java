package com.hungergames.platform.config;

import com.hungergames.game.config.ConfigurationService;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Persistiert Konfigurationswerte in {@code config.yml}. Schlüssel sind
 * dotted Paths und werden 1:1 als YAML-Pfade abgelegt.
 *
 * <p>Beim Laden werden Alt-Schlüssel aus v0.16 (z. B. {@code gameduration})
 * auf die neuen Keys gemappt, damit bestehende Dateien weiter funktionieren.</p>
 */
public final class YamlConfigBackend implements ConfigurationService.Backend {

    /** Alt-Key (v0.16) → neuer Key. Werte werden als String übernommen. */
    private static final Map<String, String> LEGACY_ALIASES = Map.ofEntries(
            Map.entry("countdown", "game.countdown"),
            Map.entry("gameduration", "game.duration"),
            Map.entry("graceperiod", "game.grace-period"),
            Map.entry("preptime", "game.prep-time-percent"),
            Map.entry("pre-init-admins", "game.pre-init-admins"),
            Map.entry("preflight-difficulty", "game.preflight-difficulty"),
            Map.entry("arena.worldborder-size", "border.initial-size"),
            Map.entry("worldborder.final-size", "border.minimum-size"),
            Map.entry("worldborder.shrink-warning", "border.shrink-warning"),
            Map.entry("running.death-action", "game.death-action"),
            Map.entry("portals.block-nether-portals", "arena.block-nether-portals"),
            Map.entry("portals.nether-allow-radius", "arena.nether-allow-radius"),
            Map.entry("portals.block-end-portals", "arena.block-end-portals"),
            Map.entry("protection.schematic-radius", "protection.cornucopia-radius"));

    private final File file;
    private final Logger logger;

    public YamlConfigBackend(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public Map<String, String> load() {
        Map<String, String> result = new LinkedHashMap<>();
        if (!file.exists()) {
            return result;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        for (Map.Entry<String, String> alias : LEGACY_ALIASES.entrySet()) {
            if (yaml.contains(alias.getKey()) && !yaml.contains(alias.getValue())) {
                result.put(alias.getValue(), String.valueOf(yaml.get(alias.getKey())));
                logger.info("config.yml: Alt-Schlüssel '" + alias.getKey()
                        + "' als '" + alias.getValue() + "' übernommen");
            }
        }

        for (String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key) || LEGACY_ALIASES.containsKey(key)) {
                continue;
            }
            Object value = yaml.get(key);
            if (value instanceof java.util.List<?> list) {
                StringBuilder joined = new StringBuilder();
                for (Object item : list) {
                    if (!joined.isEmpty()) {
                        joined.append(", ");
                    }
                    joined.append(item);
                }
                result.put(key, joined.toString());
            } else if (value != null) {
                result.put(key, String.valueOf(value));
            }
        }
        return result;
    }

    @Override
    public void save(Map<String, String> serializedValues) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(java.util.List.of(
                "HungerGames-Konfiguration — verwaltbar in-game über /hg config",
                "Nur vom Default abweichende Werte werden gespeichert."));
        serializedValues.forEach(yaml::set);
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.severe("config.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
