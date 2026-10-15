package com.hungergames.game.config;

import com.hungergames.game.config.SettingDefinition.SettingCategory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Zentrale Registrierung aller {@link SettingDefinition}s. Reihenfolge der
 * Registrierung bestimmt die Anzeige-Reihenfolge in den GUIs.
 */
public final class SettingsRegistry {

    private final Map<String, SettingDefinition<?>> definitions = new LinkedHashMap<>();

    public <T> SettingDefinition<T> register(SettingDefinition<T> definition) {
        if (definitions.putIfAbsent(definition.key(), definition) != null) {
            throw new IllegalArgumentException("Setting doppelt registriert: " + definition.key());
        }
        return definition;
    }

    public Optional<SettingDefinition<?>> get(String key) {
        return Optional.ofNullable(definitions.get(key));
    }

    public Collection<SettingDefinition<?>> all() {
        return definitions.values();
    }

    public List<SettingDefinition<?>> byCategory(SettingCategory category) {
        return definitions.values().stream()
                .filter(def -> def.category() == category)
                .toList();
    }

    /** @return Keys aus einer Datei, die keiner Definition entsprechen (für Warnungen). */
    public Set<String> unknownKeys(Set<String> fileKeys) {
        return fileKeys.stream()
                .filter(key -> !definitions.containsKey(key))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
