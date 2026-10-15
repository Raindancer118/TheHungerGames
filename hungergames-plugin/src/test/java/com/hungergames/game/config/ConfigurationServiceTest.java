package com.hungergames.game.config;

import com.hungergames.game.config.ConfigurationService.AuditEntry;
import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.game.config.SettingDefinition.ApplyTiming;
import com.hungergames.game.config.SettingDefinition.SettingCategory;
import com.hungergames.game.config.SettingDefinition.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Laufzeitänderungen der Konfiguration: Validierung, transaktionales
 * Alles-oder-Nichts, Runden-Overrides mit Fallback, ApplyTiming, Audit-Log.
 */
class ConfigurationServiceTest {

    private SettingsRegistry registry;
    private SettingDefinition<Integer> gameDuration;
    private SettingDefinition<Double> maxEdgeSpeed;
    private SettingDefinition<Duration> gracePeriod;
    private SettingDefinition<Boolean> captainSystem;

    private Map<String, String> backendData;
    private List<AuditEntry> auditEntries;
    private ConfigurationService config;

    @BeforeEach
    void setUp() {
        registry = new SettingsRegistry();
        gameDuration = registry.register(new SettingDefinition<>(
                "game.duration-minutes", SettingType.INT, 180,
                Validator.range(10, 600), "Spieldauer in Minuten",
                SettingCategory.GAME, ApplyTiming.NEXT_ROUND));
        maxEdgeSpeed = registry.register(new SettingDefinition<>(
                "border.max-edge-speed", SettingType.DOUBLE, 2.5,
                Validator.positiveDouble(), "Max. Kantengeschwindigkeit (Blöcke/s)",
                SettingCategory.BORDER, ApplyTiming.IMMEDIATE));
        gracePeriod = registry.register(new SettingDefinition<>(
                "game.grace-period", SettingType.DURATION, Duration.ofSeconds(60),
                Validator.any(), "Schutzzeit nach Spielstart",
                SettingCategory.GAME, ApplyTiming.NEXT_PHASE));
        captainSystem = registry.register(new SettingDefinition<>(
                "teams.captain-enabled", SettingType.BOOLEAN, false,
                Validator.any(), "Captain-System aktivieren",
                SettingCategory.TEAMS, ApplyTiming.IMMEDIATE));

        backendData = new HashMap<>();
        auditEntries = new ArrayList<>();
        config = newService();
    }

    private ConfigurationService newService() {
        return new ConfigurationService(registry,
                new ConfigurationService.Backend() {
                    @Override
                    public Map<String, String> load() {
                        return new HashMap<>(backendData);
                    }

                    @Override
                    public void save(Map<String, String> serializedValues) {
                        backendData = new HashMap<>(serializedValues);
                    }
                },
                auditEntries::add,
                () -> 42L);
    }

    @Test
    @DisplayName("Defaults gelten, solange nichts gesetzt wurde")
    void defaultsApply() {
        assertEquals(180, config.get(gameDuration));
        assertEquals(2.5, config.get(maxEdgeSpeed));
    }

    @Test
    @DisplayName("IMMEDIATE-Änderung wirkt sofort und wird persistiert")
    void immediateChangeApplies() {
        List<String> errors = config.begin("Tom")
                .set(maxEdgeSpeed, 3.0, Scope.PERSISTENT)
                .commit();

        assertTrue(errors.isEmpty());
        assertEquals(3.0, config.get(maxEdgeSpeed));
        assertEquals("3.0", backendData.get("border.max-edge-speed"));

        // Neustart-Simulation: neuer Service lädt den Wert aus dem Backend
        assertEquals(3.0, newService().get(maxEdgeSpeed));
    }

    @Test
    @DisplayName("Ungültiger Wert lässt die gesamte Transaktion scheitern (Rollback)")
    void invalidValueFailsWholeTransaction() {
        List<String> errors = config.begin("Tom")
                .set(maxEdgeSpeed, 3.0, Scope.PERSISTENT)
                .set(gameDuration, 5, Scope.PERSISTENT) // < 10 → ungültig
                .commit();

        assertFalse(errors.isEmpty());
        assertEquals(2.5, config.get(maxEdgeSpeed), "gültiger Teil darf NICHT angewendet sein");
        assertEquals(180, config.get(gameDuration));
        assertTrue(auditEntries.isEmpty(), "gescheiterte Transaktion erzeugt keine Audit-Einträge");
    }

    @Test
    @DisplayName("Text-Eingaben werden geparst; Parse-Fehler werden gemeldet")
    void inputParsing() {
        var tx = config.begin("Tom")
                .setFromInput(gracePeriod, "90s", Scope.PERSISTENT)
                .setFromInput(captainSystem, "an", Scope.PERSISTENT);
        assertTrue(tx.isValid());
        tx.commit();
        config.activatePending(ApplyTiming.NEXT_PHASE);

        assertEquals(Duration.ofSeconds(90), config.get(gracePeriod));
        assertEquals(true, config.get(captainSystem));

        var bad = config.begin("Tom").setFromInput(maxEdgeSpeed, "schnell", Scope.PERSISTENT);
        assertFalse(bad.isValid());
    }

    @Test
    @DisplayName("Runden-Override überlagert, Fallback nach clearRoundOverrides")
    void roundOverrideFallsBack() {
        config.begin("Tom").set(maxEdgeSpeed, 3.0, Scope.PERSISTENT).commit();
        config.begin("Tom").set(maxEdgeSpeed, 1.0, Scope.ROUND).commit();

        assertEquals(1.0, config.get(maxEdgeSpeed));
        assertTrue(config.hasRoundOverride("border.max-edge-speed"));

        config.clearRoundOverrides();

        assertEquals(3.0, config.get(maxEdgeSpeed), "nach der Runde gilt wieder der persistente Wert");
        // Overrides landen nie im Backend
        assertEquals("3.0", backendData.get("border.max-edge-speed"));
    }

    @Test
    @DisplayName("NEXT_PHASE/NEXT_ROUND-Werte sind pending bis zur Aktivierung")
    void pendingUntilActivated() {
        config.begin("Tom")
                .set(gameDuration, 240, Scope.PERSISTENT)
                .set(gracePeriod, Duration.ofSeconds(30), Scope.PERSISTENT)
                .commit();

        assertEquals(180, config.get(gameDuration), "NEXT_ROUND noch nicht aktiv");
        assertEquals(Duration.ofSeconds(60), config.get(gracePeriod), "NEXT_PHASE noch nicht aktiv");
        assertEquals("240", config.pendingValue(gameDuration).orElseThrow());

        config.activatePending(ApplyTiming.NEXT_PHASE);
        assertEquals(Duration.ofSeconds(30), config.get(gracePeriod));
        assertEquals(180, config.get(gameDuration), "anderes Timing bleibt pending");

        config.activatePending(ApplyTiming.NEXT_ROUND);
        assertEquals(240, config.get(gameDuration));
        assertTrue(config.pendingValue(gameDuration).isEmpty());
    }

    @Test
    @DisplayName("Audit-Log erhält Akteur, alt→neu und Scope")
    void auditLogRecords() {
        config.begin("Tom").set(maxEdgeSpeed, 3.5, Scope.ROUND).commit();

        assertEquals(1, auditEntries.size());
        AuditEntry entry = auditEntries.get(0);
        assertEquals("Tom", entry.actor());
        assertEquals("border.max-edge-speed", entry.key());
        assertEquals("2.5", entry.oldValue());
        assertEquals("3.5", entry.newValue());
        assertEquals(Scope.ROUND, entry.scope());
        assertEquals(42L, entry.timestampMillis());
    }

    @Test
    @DisplayName("Ungültige Dateiwerte fallen still auf den Default zurück")
    void invalidFileValuesIgnored() {
        backendData.put("game.duration-minutes", "kaputt");
        backendData.put("border.max-edge-speed", "-5"); // parsebar, aber ungültig

        ConfigurationService fresh = newService();

        assertEquals(180, fresh.get(gameDuration));
        assertEquals(2.5, fresh.get(maxEdgeSpeed));
    }

    @Test
    @DisplayName("Doppelte Registrierung desselben Keys wird abgelehnt")
    void duplicateRegistrationRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                registry.register(new SettingDefinition<>(
                        "game.duration-minutes", SettingType.INT, 1,
                        Validator.any(), "dup", SettingCategory.GAME, ApplyTiming.IMMEDIATE)));
    }
}
