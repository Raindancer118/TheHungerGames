package com.hungergames.game.border;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Laufzeitverhalten der Border-Engine: Zeit- und Spielerzahl-Trigger,
 * Mindestgröße, sequentielle Phasen, Restore.
 */
class BorderEngineTest {

    private static BorderSettings twoPhases() {
        return new BorderSettings(2500, 100, 2.5, List.of(
                BorderPhaseConfig.ofDuration(BorderTrigger.atTime(Duration.ofMinutes(20)),
                        1500, Duration.ofSeconds(400)),
                BorderPhaseConfig.ofFixedSpeed(BorderTrigger.either(Duration.ofMinutes(60), 4),
                        100, 1.0)));
    }

    @Test
    @DisplayName("Zeit-Trigger löst genau einmal aus, nicht davor")
    void timeTriggerFiresOnce() {
        BorderEngine engine = new BorderEngine(twoPhases());

        assertTrue(engine.tick(Duration.ofMinutes(19), 10, 2500).isEmpty());

        BorderEngine.ShrinkCommand cmd = engine.tick(Duration.ofMinutes(20), 10, 2500).orElseThrow();
        assertEquals(1500, cmd.targetSize(), 1e-9);
        assertEquals(Duration.ofSeconds(400), cmd.duration());
        assertEquals(1.25, cmd.effectiveSpeed(), 1e-9); // (2500-1500)/2/400s

        // Gleiche Bedingungen → Phase feuert nicht erneut
        assertTrue(engine.tick(Duration.ofMinutes(21), 10, 2100).isEmpty());
    }

    @Test
    @DisplayName("Spielerzahl-Trigger: aliveBelow löst vor der Zeitbedingung aus")
    void aliveTriggerFires() {
        BorderEngine engine = new BorderEngine(twoPhases());
        engine.tick(Duration.ofMinutes(20), 10, 2500); // Phase 1 abgefeuert

        // Phase 2: aliveBelow 4 ODER 60min — bei 3 Lebenden feuert sie sofort
        BorderEngine.ShrinkCommand cmd = engine.tick(Duration.ofMinutes(25), 3, 1500).orElseThrow();
        assertEquals(100, cmd.targetSize(), 1e-9);
        // FIXED_SPEED 1.0: (1500-100)/2/1.0 = 700s
        assertEquals(Duration.ofSeconds(700), cmd.duration());
        assertTrue(engine.isFinished());
    }

    @Test
    @DisplayName("Mindestgröße wird zur Laufzeit hart durchgesetzt")
    void minimumSizeEnforced() {
        BorderSettings s = new BorderSettings(2500, 400, 2.5, List.of(
                BorderPhaseConfig.ofFixedSpeed(BorderTrigger.atTime(Duration.ZERO), 100, 1.0)));
        BorderEngine engine = new BorderEngine(s);

        BorderEngine.ShrinkCommand cmd = engine.tick(Duration.ZERO, 10, 2500).orElseThrow();
        assertEquals(400, cmd.targetSize(), 1e-9, "Ziel wird auf minimumSize angehoben");
    }

    @Test
    @DisplayName("Phase ohne Schrumpfbedarf wird übersprungen")
    void noShrinkWhenAlreadySmaller() {
        BorderSettings s = new BorderSettings(2500, 100, 2.5, List.of(
                BorderPhaseConfig.ofFixedSpeed(BorderTrigger.atTime(Duration.ZERO), 2000, 1.0)));
        BorderEngine engine = new BorderEngine(s);

        // Border ist bereits kleiner als das Ziel (z. B. durch Admin-Eingriff)
        assertTrue(engine.tick(Duration.ZERO, 10, 1800).isEmpty());
        assertTrue(engine.isFinished(), "Phase gilt trotzdem als verbraucht");
    }

    @Test
    @DisplayName("Restore setzt an der persistierten Phase wieder auf")
    void restoreContinuesAtPersistedIndex() {
        BorderEngine engine = new BorderEngine(twoPhases());
        engine.tick(Duration.ofMinutes(20), 10, 2500);
        int persisted = engine.nextPhaseIndex();
        assertEquals(1, persisted);

        BorderEngine restored = new BorderEngine(twoPhases(), persisted);
        assertFalse(restored.isFinished());
        // Phase 1 feuert nicht erneut, Phase 2 wartet auf ihre Bedingung
        assertTrue(restored.tick(Duration.ofMinutes(30), 10, 1500).isEmpty());
        assertTrue(restored.tick(Duration.ofMinutes(60), 10, 1500).isPresent());
    }

    @Test
    @DisplayName("MAX_SPEED-Phase ohne Anker schrumpft mit der Fairness-Grenze")
    void maxSpeedClampedAtRuntime() {
        BorderSettings s = new BorderSettings(2500, 100, 2.5, List.of(
                BorderPhaseConfig.ofMaxSpeed(BorderTrigger.atTime(Duration.ZERO), 500, 50.0)));
        BorderEngine engine = new BorderEngine(s);

        BorderEngine.ShrinkCommand cmd = engine.tick(Duration.ZERO, 10, 2500).orElseThrow();
        assertEquals(2.5, cmd.effectiveSpeed(), 1e-9);
        assertEquals(Duration.ofSeconds(400), cmd.duration()); // (2500-500)/2/2.5
    }

    @Test
    @DisplayName("MAX_SPEED mit Zeitanker hält den Zeitrahmen, solange er erreichbar ist")
    void maxSpeedWithPreferredDurationAtRuntime() {
        BorderSettings s = new BorderSettings(2500, 100, 2.5, List.of(
                BorderPhaseConfig.ofMaxSpeed(BorderTrigger.atTime(Duration.ZERO),
                        1500, 2.5, Duration.ofSeconds(1000))));
        BorderEngine engine = new BorderEngine(s);

        BorderEngine.ShrinkCommand cmd = engine.tick(Duration.ZERO, 10, 2500).orElseThrow();
        assertEquals(0.5, cmd.effectiveSpeed(), 1e-9); // Wunschtempo, nicht das Maximum
        assertEquals(Duration.ofSeconds(1000), cmd.duration());
    }
}
