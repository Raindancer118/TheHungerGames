package com.hungergames.game.border;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Border-Mathematik: Kantengeschwindigkeit, Konflikterkennung und die
 * berechneten Auswirkungen jeder Lösungsoption.
 */
class BorderMathTest {

    private static final double MAX_EDGE_SPEED = 2.5;

    private static BorderSettings settings(BorderPhaseConfig... phases) {
        return new BorderSettings(2500, 100, MAX_EDGE_SPEED, List.of(phases));
    }

    @Test
    @DisplayName("Kantengeschwindigkeit = halbe Durchmesser-Differenz pro Sekunde")
    void edgeSpeedFormula() {
        // 2500 → 1500 in 200s: 1000 Blöcke Durchmesser = 500 Blöcke pro Kante → 2.5 b/s
        assertEquals(2.5, BorderMath.edgeSpeed(2500, 1500, 200), 1e-9);
        assertEquals(Duration.ofSeconds(200), BorderMath.durationFor(2500, 1500, 2.5));
    }

    @Test
    @DisplayName("Konfiguration innerhalb der 2,5-b/s-Grenze ist konfliktfrei")
    void validConfigHasNoConflicts() {
        BorderSettings s = settings(
                BorderPhaseConfig.ofDuration(BorderTrigger.atTime(Duration.ofMinutes(20)),
                        1500, Duration.ofSeconds(200)), // exakt 2.5 b/s
                BorderPhaseConfig.ofFixedSpeed(BorderTrigger.aliveBelow(4), 100, 1.0));

        assertTrue(BorderMath.validate(s, Optional.of(Duration.ofMinutes(180))).isEmpty());
    }

    @Test
    @DisplayName("Zu schnelle Phase (Dauer-Modus) → SPEED_EXCEEDS_MAX mit korrekter implizierter Geschwindigkeit")
    void tooFastDurationPhaseConflicts() {
        BorderSettings s = settings(
                BorderPhaseConfig.ofDuration(BorderTrigger.atTime(Duration.ofMinutes(10)),
                        500, Duration.ofSeconds(100))); // (2500-500)/2/100 = 10 b/s

        List<BorderConflict> conflicts = BorderMath.validate(s, Optional.empty());

        assertEquals(1, conflicts.size());
        BorderConflict conflict = conflicts.get(0);
        assertEquals(BorderConflict.Type.SPEED_EXCEEDS_MAX, conflict.type());
        assertEquals(10.0, conflict.impliedSpeed(), 1e-9);
        assertEquals(MAX_EDGE_SPEED, conflict.limit(), 1e-9);
    }

    @Test
    @DisplayName("Lösungsoptionen für Geschwindigkeitskonflikt sind korrekt berechnet")
    void speedConflictResolutions() {
        BorderSettings s = settings(
                BorderPhaseConfig.ofDuration(BorderTrigger.atTime(Duration.ofMinutes(10)),
                        500, Duration.ofSeconds(100)));
        BorderConflict conflict = BorderMath.validate(s, Optional.empty()).get(0);

        List<BorderResolution> options = BorderMath.resolutions(s, conflict, Optional.empty());

        // Option 1: Dauer anpassen → (2500-500)/2/2.5 = 400s
        BorderResolution.AdjustDuration adjustDuration = options.stream()
                .filter(BorderResolution.AdjustDuration.class::isInstance)
                .map(BorderResolution.AdjustDuration.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Duration.ofSeconds(400), adjustDuration.newDuration());
        assertEquals(MAX_EDGE_SPEED, adjustDuration.resultingSpeed(), 1e-9);

        // Option 2: Zielgröße anpassen → 2500 - 2*2.5*100 = 2000
        BorderResolution.AdjustTarget adjustTarget = options.stream()
                .filter(BorderResolution.AdjustTarget.class::isInstance)
                .map(BorderResolution.AdjustTarget.class::cast)
                .findFirst().orElseThrow();
        assertEquals(2000, adjustTarget.newTarget(), 1e-9);

        // Option 3: Geschwindigkeit als Maximum → 400s bei 2.5 b/s
        BorderResolution.UseSpeedAsMax asMax = options.stream()
                .filter(BorderResolution.UseSpeedAsMax.class::isInstance)
                .map(BorderResolution.UseSpeedAsMax.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Duration.ofSeconds(400), asMax.effectiveDuration());

        // Verwerfen ist immer die letzte Option
        assertInstanceOf(BorderResolution.Discard.class, options.get(options.size() - 1));
    }

    @Test
    @DisplayName("Anwenden von AdjustDuration behebt den Konflikt — nichts passiert stillschweigend vorher")
    void applyAdjustDurationResolvesConflict() {
        BorderSettings s = settings(
                BorderPhaseConfig.ofDuration(BorderTrigger.atTime(Duration.ofMinutes(10)),
                        500, Duration.ofSeconds(100)));
        BorderConflict conflict = BorderMath.validate(s, Optional.empty()).get(0);
        BorderResolution.AdjustDuration fix = BorderMath.resolutions(s, conflict, Optional.empty()).stream()
                .filter(BorderResolution.AdjustDuration.class::isInstance)
                .map(BorderResolution.AdjustDuration.class::cast)
                .findFirst().orElseThrow();

        // Vor der Bestätigung: Original unverändert
        assertEquals(Duration.ofSeconds(100), s.phases().get(0).duration().orElseThrow());

        BorderMath.ApplyResult applied = BorderMath.apply(s, 0, fix);

        assertTrue(BorderMath.validate(applied.settings(), Optional.empty()).isEmpty());
        assertEquals(Duration.ofSeconds(400), applied.settings().phases().get(0).duration().orElseThrow());
        assertTrue(applied.newGameDuration().isEmpty());
    }

    @Test
    @DisplayName("Zielgröße unter Mindestgröße → TARGET_BELOW_MINIMUM mit AdjustTarget-Option")
    void targetBelowMinimum() {
        BorderSettings s = settings(
                BorderPhaseConfig.ofFixedSpeed(BorderTrigger.atTime(Duration.ofMinutes(10)), 50, 1.0));

        List<BorderConflict> conflicts = BorderMath.validate(s, Optional.empty());
        assertEquals(1, conflicts.size());
        assertEquals(BorderConflict.Type.TARGET_BELOW_MINIMUM, conflicts.get(0).type());

        BorderResolution.AdjustTarget fix = BorderMath.resolutions(s, conflicts.get(0), Optional.empty()).stream()
                .filter(BorderResolution.AdjustTarget.class::isInstance)
                .map(BorderResolution.AdjustTarget.class::cast)
                .findFirst().orElseThrow();
        assertEquals(100, fix.newTarget(), 1e-9);
    }

    @Test
    @DisplayName("Phase passt nicht in die Spielzeit → ShiftStart/AdjustDuration/AdjustGameTime")
    void exceedsGameTime() {
        // Trigger bei 170min, Dauer 400s → Ende bei ~176.7min > 175min Spielzeit
        BorderSettings s = settings(
                BorderPhaseConfig.ofDuration(BorderTrigger.atTime(Duration.ofMinutes(170)),
                        500, Duration.ofSeconds(400)));
        Optional<Duration> game = Optional.of(Duration.ofMinutes(175));

        List<BorderConflict> conflicts = BorderMath.validate(s, game);
        assertEquals(1, conflicts.size());
        assertEquals(BorderConflict.Type.EXCEEDS_GAME_TIME, conflicts.get(0).type());

        List<BorderResolution> options = BorderMath.resolutions(s, conflicts.get(0), game);

        BorderResolution.ShiftStart shift = options.stream()
                .filter(BorderResolution.ShiftStart.class::isInstance)
                .map(BorderResolution.ShiftStart.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Duration.ofMinutes(175).minus(Duration.ofSeconds(400)), shift.newStart());

        BorderResolution.AdjustGameTime extend = options.stream()
                .filter(BorderResolution.AdjustGameTime.class::isInstance)
                .map(BorderResolution.AdjustGameTime.class::cast)
                .findFirst().orElseThrow();
        assertEquals(Duration.ofMinutes(170).plus(Duration.ofSeconds(400)), extend.newGameDuration());

        // AdjustGameTime ändert nur die Spielzeit, nicht die Border-Settings
        BorderMath.ApplyResult applied = BorderMath.apply(s, 0, extend);
        assertEquals(s, applied.settings());
        assertEquals(extend.newGameDuration(), applied.newGameDuration().orElseThrow());
    }

    @Test
    @DisplayName("Unsortierte Zeit-Trigger → PHASES_OUT_OF_ORDER")
    void phasesOutOfOrder() {
        BorderSettings s = settings(
                BorderPhaseConfig.ofFixedSpeed(BorderTrigger.atTime(Duration.ofMinutes(30)), 1500, 1.0),
                BorderPhaseConfig.ofFixedSpeed(BorderTrigger.atTime(Duration.ofMinutes(10)), 800, 1.0));

        List<BorderConflict> conflicts = BorderMath.validate(s, Optional.empty());
        assertTrue(conflicts.stream().anyMatch(c -> c.type() == BorderConflict.Type.PHASES_OUT_OF_ORDER));
    }

    @Test
    @DisplayName("Wachsende Zielgröße → TARGET_NOT_SHRINKING")
    void growingTargetDetected() {
        BorderSettings s = settings(
                BorderPhaseConfig.ofFixedSpeed(BorderTrigger.atTime(Duration.ofMinutes(10)), 3000, 1.0));

        List<BorderConflict> conflicts = BorderMath.validate(s, Optional.empty());
        assertEquals(BorderConflict.Type.TARGET_NOT_SHRINKING, conflicts.get(0).type());
    }

    @Test
    @DisplayName("MAX_SPEED ohne Zeitanker wird geklemmt und erzeugt keinen Geschwindigkeitskonflikt")
    void maxSpeedModeNeverConflicts() {
        BorderSettings s = settings(
                BorderPhaseConfig.ofMaxSpeed(BorderTrigger.atTime(Duration.ofMinutes(10)), 500, 99.0));

        assertTrue(BorderMath.validate(s, Optional.empty()).isEmpty());
        assertEquals(MAX_EDGE_SPEED, BorderMath.impliedSpeed(s, 0), 1e-9);
    }

    @Test
    @DisplayName("MAX_SPEED mit erreichbarem Zeitanker fährt langsamer als das Maximum")
    void maxSpeedWithAchievablePreferredDuration() {
        // 2500 → 1500 in 1000s: benötigt 0,5 b/s — weit unter dem Cap 2,5
        BorderSettings s = settings(
                BorderPhaseConfig.ofMaxSpeed(BorderTrigger.atTime(Duration.ofMinutes(10)),
                        1500, 2.5, Duration.ofSeconds(1000)));

        assertTrue(BorderMath.validate(s, Optional.empty()).isEmpty());
        assertEquals(0.5, BorderMath.impliedSpeed(s, 0), 1e-9);
        assertEquals(Duration.ofSeconds(1000), BorderMath.effectiveDuration(s, 0));
    }

    @Test
    @DisplayName("MAX_SPEED mit unerreichbarem Zeitanker → SPEED_EXCEEDS_MAX-Konflikt")
    void maxSpeedWithUnachievablePreferredDurationConflicts() {
        // 2500 → 500 in 100s bräuchte 10 b/s, Cap ist 2,5 → Konflikt
        BorderSettings s = settings(
                BorderPhaseConfig.ofMaxSpeed(BorderTrigger.atTime(Duration.ofMinutes(10)),
                        500, 2.5, Duration.ofSeconds(100)));

        List<BorderConflict> conflicts = BorderMath.validate(s, Optional.empty());
        assertEquals(1, conflicts.size());
        assertEquals(BorderConflict.Type.SPEED_EXCEEDS_MAX, conflicts.get(0).type());
        assertEquals(10.0, conflicts.get(0).impliedSpeed(), 1e-9);
        assertEquals(2.5, conflicts.get(0).limit(), 1e-9);

        // Lösungsoptionen: Dauer anpassen und Geschwindigkeit-als-Maximum vorhanden
        List<BorderResolution> options = BorderMath.resolutions(s, conflicts.get(0), Optional.empty());
        assertTrue(options.stream().anyMatch(BorderResolution.AdjustDuration.class::isInstance));
        assertTrue(options.stream().anyMatch(BorderResolution.UseSpeedAsMax.class::isInstance));
    }

    @Test
    @DisplayName("Lösungen nutzen das Phasen-Cap, wenn es unter der globalen Grenze liegt")
    void resolutionsUsePhaseCapWhenLower() {
        // Phase erlaubt max 1.0 b/s (global 2.5), Anker 100s bräuchte 10 b/s
        BorderSettings s = settings(
                BorderPhaseConfig.ofMaxSpeed(BorderTrigger.atTime(Duration.ofMinutes(10)),
                        500, 1.0, Duration.ofSeconds(100)));
        BorderConflict conflict = BorderMath.validate(s, Optional.empty()).get(0);
        assertEquals(1.0, conflict.limit(), 1e-9, "Validierung meldet das Phasen-Cap");

        BorderResolution.AdjustDuration fix = BorderMath.resolutions(s, conflict, Optional.empty()).stream()
                .filter(BorderResolution.AdjustDuration.class::isInstance)
                .map(BorderResolution.AdjustDuration.class::cast)
                .findFirst().orElseThrow();
        // (2500-500)/2/1.0 = 1000s — mit dem Phasen-Cap, nicht mit global 2.5
        assertEquals(Duration.ofSeconds(1000), fix.newDuration());
        assertEquals(1.0, fix.resultingSpeed(), 1e-9);

        // Angewendet ist die Konfiguration konfliktfrei
        BorderMath.ApplyResult applied = BorderMath.apply(s, 0, fix);
        assertTrue(BorderMath.validate(applied.settings(), Optional.empty()).isEmpty(),
                "Nach der Lösung darf der Konflikt nicht erneut auftreten");
    }
}
