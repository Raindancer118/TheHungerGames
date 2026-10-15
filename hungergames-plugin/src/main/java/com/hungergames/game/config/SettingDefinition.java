package com.hungergames.game.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Vollständige Beschreibung einer Einstellung: Schlüssel, Typ, Default,
 * Validierung, Beschreibung, Kategorie und Anwendungszeitpunkt.
 *
 * @param key          eindeutiger Schlüssel, z. B. {@code "border.max-edge-speed"}
 * @param type         Datentyp mit Parse-/Serialisierungslogik
 * @param defaultValue Standardwert
 * @param validator    liefert eine Fehlermeldung bei ungültigem Wert
 * @param description  Beschreibung für GUI-Lore und Doku
 * @param category     GUI-Kategorie
 * @param applyTiming  wann eine Änderung wirksam wird
 */
public record SettingDefinition<T>(
        String key,
        SettingType<T> type,
        T defaultValue,
        Validator<T> validator,
        String description,
        SettingCategory category,
        ApplyTiming applyTiming) {

    public SettingDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(applyTiming, "applyTiming");
        if (validator.validate(defaultValue).isPresent()) {
            throw new IllegalArgumentException(
                    "Defaultwert von '" + key + "' besteht die eigene Validierung nicht");
        }
    }

    /** Wann eine Konfigurationsänderung wirksam wird. */
    public enum ApplyTiming {
        /** Sofort. */
        IMMEDIATE,
        /** Beim nächsten Phasenwechsel. */
        NEXT_PHASE,
        /** Beim Start der nächsten Runde. */
        NEXT_ROUND,
        /** Erst nach Server-/Plugin-Neustart. */
        RESTART
    }

    /** GUI-/Doku-Kategorie einer Einstellung. */
    public enum SettingCategory {
        GAME,
        ARENA,
        LOBBY,
        STARTUP,
        TEAMS,
        BORDER,
        PROTECTION,
        ITEMS,
        LOOT,
        EVENTS,
        SOUNDS,
        EFFECTS,
        SPONSORS,
        DEATHMATCH,
        GAMEMASTER,
        ANNOUNCEMENTS,
        API,
        RESOURCEPACK
    }

    /** Validiert Werte; leeres Optional = gültig. */
    @FunctionalInterface
    public interface Validator<T> {

        Optional<String> validate(T value);

        static <T> Validator<T> any() {
            return value -> Optional.empty();
        }

        static <T extends Comparable<T>> Validator<T> range(T min, T max) {
            return value -> {
                if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
                    return Optional.of("Wert muss zwischen " + min + " und " + max + " liegen");
                }
                return Optional.empty();
            };
        }

        static Validator<Integer> positiveInt() {
            return value -> value > 0 ? Optional.empty() : Optional.of("Wert muss > 0 sein");
        }

        static Validator<Integer> nonNegativeInt() {
            return value -> value >= 0 ? Optional.empty() : Optional.of("Wert darf nicht negativ sein");
        }

        static Validator<Double> positiveDouble() {
            return value -> value > 0 ? Optional.empty() : Optional.of("Wert muss > 0 sein");
        }

        static <T> Validator<T> custom(Function<T, Optional<String>> check) {
            return check::apply;
        }
    }
}
