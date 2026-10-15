package com.hungergames.game.config;

import com.hungergames.game.config.SettingDefinition.ApplyTiming;
import com.hungergames.game.config.SettingDefinition.SettingCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Typisiertes, transaktionales Konfigurationssystem.
 *
 * <p>Wert-Auflösung pro Schlüssel: Runden-Override → persistenter Wert →
 * Default. Änderungen laufen ausschließlich über {@link Transaction}en:
 * erst validieren (alles oder nichts), dann committen. Werte mit
 * {@code applyTiming != IMMEDIATE} werden als „pending" vorgemerkt und beim
 * passenden Zeitpunkt aktiv ({@link #activatePending}).</p>
 *
 * <p>Bukkit-frei; Persistenz und Audit-Log sind Ports.</p>
 */
public final class ConfigurationService {

    /** Persistenz-Port (YAML-Adapter im Plugin, In-Memory in Tests). */
    public interface Backend {
        Map<String, String> load();

        void save(Map<String, String> serializedValues);
    }

    /** Audit-Port: nimmt bestätigte Änderungen entgegen. */
    @FunctionalInterface
    public interface AuditSink {
        void record(AuditEntry entry);
    }

    /** Eine bestätigte Konfigurationsänderung. */
    public record AuditEntry(long timestampMillis, String actor, String key,
                             String oldValue, String newValue, Scope scope) {
    }

    /** Geltungsbereich einer Änderung. */
    public enum Scope {
        /** Dauerhaft (in der Konfigurationsdatei). */
        PERSISTENT,
        /** Nur für die aktuelle Runde; fällt danach auf den persistenten Wert zurück. */
        ROUND
    }

    private final SettingsRegistry registry;
    private final Backend backend;
    private final AuditSink audit;
    private final java.util.function.LongSupplier clock;

    private final Map<String, Object> persistentValues = new HashMap<>();
    private final Map<String, Object> roundOverrides = new HashMap<>();
    /** Vorgemerkte Werte, die erst zu ihrem ApplyTiming aktiv werden. */
    private final Map<ApplyTiming, Map<String, StagedValue>> pending = new EnumMap<>(ApplyTiming.class);

    private record StagedValue(Object value, Scope scope) {
    }

    public ConfigurationService(SettingsRegistry registry, Backend backend, AuditSink audit,
                                java.util.function.LongSupplier clock) {
        this.registry = registry;
        this.backend = backend;
        this.audit = audit;
        this.clock = clock;
        loadFromBackend();
    }

    // ==================== Lesen ====================

    /** @return effektiver Wert: Runden-Override → persistent → Default. */
    @SuppressWarnings("unchecked")
    public <T> T get(SettingDefinition<T> definition) {
        Object override = roundOverrides.get(definition.key());
        if (override != null) {
            return (T) override;
        }
        Object persistent = persistentValues.get(definition.key());
        if (persistent != null) {
            return (T) persistent;
        }
        return definition.defaultValue();
    }

    public boolean hasRoundOverride(String key) {
        return roundOverrides.containsKey(key);
    }

    /** @return vorgemerkter (noch nicht aktiver) Wert für einen Schlüssel. */
    public Optional<String> pendingValue(SettingDefinition<?> definition) {
        Map<String, StagedValue> staged = pending.get(definition.applyTiming());
        if (staged == null || !staged.containsKey(definition.key())) {
            return Optional.empty();
        }
        return Optional.of(serialize(definition, staged.get(definition.key()).value()));
    }

    public SettingsRegistry registry() {
        return registry;
    }

    // ==================== Transaktionen ====================

    /** Beginnt eine neue Änderungs-Transaktion. */
    public Transaction begin(String actor) {
        return new Transaction(actor);
    }

    /**
     * Sammelt Änderungen, validiert sie gesamthaft und wendet sie atomar an.
     * Ungültige Werte lassen die gesamte Transaktion scheitern — kein
     * teilweises Übernehmen.
     */
    public final class Transaction {

        private final String actor;
        private final Map<String, StagedChange> changes = new LinkedHashMap<>();
        private final List<String> errors = new ArrayList<>();
        private boolean closed;

        private record StagedChange(SettingDefinition<?> definition, Object value, Scope scope) {
        }

        private Transaction(String actor) {
            this.actor = actor;
        }

        /** Setzt einen bereits typisierten Wert. */
        public <T> Transaction set(SettingDefinition<T> definition, T value, Scope scope) {
            definition.validator().validate(value).ifPresentOrElse(
                    error -> errors.add(definition.key() + ": " + error),
                    () -> changes.put(definition.key(), new StagedChange(definition, value, scope)));
            return this;
        }

        /** Parst und setzt eine Text-Eingabe (GUI/Anvil). */
        public <T> Transaction setFromInput(SettingDefinition<T> definition, String input, Scope scope) {
            try {
                return set(definition, definition.type().parse(input.trim()), scope);
            } catch (IllegalArgumentException e) {
                errors.add(definition.key() + ": Eingabe \"" + input + "\" ist kein gültiger Wert ("
                        + e.getMessage() + ")");
                return this;
            }
        }

        public List<String> errors() {
            return Collections.unmodifiableList(errors);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Wendet alle Änderungen an — oder keine.
         *
         * @return Fehlerliste; leer bei Erfolg
         */
        public List<String> commit() {
            if (closed) {
                throw new IllegalStateException("Transaktion wurde bereits abgeschlossen");
            }
            if (!errors.isEmpty()) {
                return errors();
            }
            closed = true;

            boolean persistentDirty = false;
            for (StagedChange change : changes.values()) {
                SettingDefinition<?> def = change.definition();
                String oldValue = serialize(def, get(def));

                if (def.applyTiming() == ApplyTiming.IMMEDIATE) {
                    applyNow(def.key(), change.value(), change.scope());
                    persistentDirty |= change.scope() == Scope.PERSISTENT;
                } else {
                    pending.computeIfAbsent(def.applyTiming(), t -> new LinkedHashMap<>())
                            .put(def.key(), new StagedValue(change.value(), change.scope()));
                }

                audit.record(new AuditEntry(clock.getAsLong(), actor, def.key(),
                        oldValue, serialize(def, change.value()), change.scope()));
            }
            if (persistentDirty) {
                persistToBackend();
            }
            return List.of();
        }
    }

    // ==================== Lebenszyklus-Hooks ====================

    /**
     * Aktiviert alle für das gegebene Timing vorgemerkten Werte.
     * Aufzurufen bei Phasenwechsel ({@link ApplyTiming#NEXT_PHASE}) bzw.
     * Rundenstart ({@link ApplyTiming#NEXT_ROUND}).
     */
    public void activatePending(ApplyTiming timing) {
        Map<String, StagedValue> staged = pending.remove(timing);
        if (staged == null) {
            return;
        }
        boolean persistentDirty = false;
        for (Map.Entry<String, StagedValue> entry : staged.entrySet()) {
            applyNow(entry.getKey(), entry.getValue().value(), entry.getValue().scope());
            persistentDirty |= entry.getValue().scope() == Scope.PERSISTENT;
        }
        if (persistentDirty) {
            persistToBackend();
        }
    }

    /** Verwirft alle Runden-Overrides (Rundenende). */
    public void clearRoundOverrides() {
        roundOverrides.clear();
    }

    /**
     * Lädt die persistenten Werte neu aus dem Backend — für einen Reload nach
     * manuellen Edits an der Konfigurationsdatei. Runden-Overrides und
     * vorgemerkte Änderungen bleiben erhalten; ungültige Dateiwerte fallen
     * wie beim Start auf den Default zurück.
     */
    public void reloadFromBackend() {
        persistentValues.clear();
        loadFromBackend();
    }

    // ==================== intern ====================

    private void applyNow(String key, Object value, Scope scope) {
        if (scope == Scope.ROUND) {
            roundOverrides.put(key, value);
        } else {
            persistentValues.put(key, value);
        }
    }

    private void loadFromBackend() {
        Map<String, String> raw = backend.load();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            SettingDefinition<?> def = registry.get(entry.getKey()).orElse(null);
            if (def == null) {
                continue; // unbekannte Keys meldet der Aufrufer über registry.unknownKeys()
            }
            try {
                Object value = def.type().parse(entry.getValue());
                if (validateRaw(def, value).isEmpty()) {
                    persistentValues.put(def.key(), value);
                }
            } catch (IllegalArgumentException ignored) {
                // ungültiger Dateiwert → Default bleibt wirksam
            }
        }
    }

    private void persistToBackend() {
        Map<String, String> serialized = new LinkedHashMap<>();
        for (SettingDefinition<?> def : registry.all()) {
            Object value = persistentValues.get(def.key());
            if (value != null && !Objects.equals(value, def.defaultValue())) {
                serialized.put(def.key(), serialize(def, value));
            }
        }
        backend.save(serialized);
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<String> validateRaw(SettingDefinition<T> definition, Object value) {
        return definition.validator().validate((T) value);
    }

    @SuppressWarnings("unchecked")
    private static <T> String serialize(SettingDefinition<T> definition, Object value) {
        return definition.type().serialize((T) value);
    }
}
