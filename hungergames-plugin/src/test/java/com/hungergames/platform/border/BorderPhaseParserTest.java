package com.hungergames.platform.border;

import com.hungergames.game.border.BorderPhaseConfig;
import com.hungergames.game.border.BorderSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kompakte Phasensyntax: Parsen, Validieren und Roundtrip über den Serializer. */
class BorderPhaseParserTest {

    private static final Duration GAME = Duration.ofMinutes(180);

    @Test
    @DisplayName("Zeit-Trigger mit Dauer-Modus")
    void parsesDurationMode() {
        BorderPhaseConfig phase = BorderPhaseParser.parse("20m -> 1000 @ duration:10m", GAME);

        assertEquals(Duration.ofMinutes(20), phase.trigger().time().orElseThrow());
        assertEquals(1000, phase.targetSize(), 1e-9);
        assertEquals(BorderPhaseConfig.Mode.DURATION, phase.mode());
        assertEquals(Duration.ofMinutes(10), phase.duration().orElseThrow());
    }

    @Test
    @DisplayName("Spielerzahl-Trigger mit fester Geschwindigkeit")
    void parsesAliveTriggerAndSpeed() {
        BorderPhaseConfig phase = BorderPhaseParser.parse("alive<4 -> 200 @ speed:1.0", GAME);

        assertEquals(4, phase.trigger().aliveBelow().orElseThrow());
        assertTrue(phase.trigger().time().isEmpty());
        assertEquals(BorderPhaseConfig.Mode.FIXED_SPEED, phase.mode());
        assertEquals(1.0, phase.edgeSpeed().orElseThrow(), 1e-9);
    }

    @Test
    @DisplayName("Prozent-Trigger wird relativ zur Spielzeit aufgelöst")
    void parsesPercentTrigger() {
        BorderPhaseConfig phase = BorderPhaseParser.parse("50% -> 1000 @ max:2.5", GAME);

        assertEquals(Duration.ofMinutes(90), phase.trigger().time().orElseThrow());
        assertEquals(BorderPhaseConfig.Mode.MAX_SPEED, phase.mode());
    }

    @Test
    @DisplayName("ODER-Kombination aus Zeit und Spielerzahl")
    void parsesCombinedTrigger() {
        BorderPhaseConfig phase = BorderPhaseParser.parse("1h|alive<6 -> 500 @ max:2.5", GAME);

        assertEquals(Duration.ofHours(1), phase.trigger().time().orElseThrow());
        assertEquals(6, phase.trigger().aliveBelow().orElseThrow());
    }

    @Test
    @DisplayName("Syntaxfehler werden mit verständlicher Meldung abgelehnt")
    void rejectsInvalidSyntax() {
        assertThrows(IllegalArgumentException.class,
                () -> BorderPhaseParser.parse("kaputt", GAME));
        assertThrows(IllegalArgumentException.class,
                () -> BorderPhaseParser.parse("20m -> 1000 @ warp:9", GAME));
        assertTrue(BorderPhaseParser.validateList(List.of("20m -> 1000 @ duration:zehn")).isPresent());
        assertTrue(BorderPhaseParser.validateList(
                List.of("20m -> 1000 @ duration:10m", "alive<4 -> 200 @ speed:1.0")).isEmpty());
    }

    @Test
    @DisplayName("prefer: setzt den Zeitanker im max-Modus, sonst abgelehnt")
    void parsesPreferAnchor() {
        BorderPhaseConfig phase = BorderPhaseParser.parse("50% -> 1000 @ max:2.5,prefer:25m", GAME);

        assertEquals(BorderPhaseConfig.Mode.MAX_SPEED, phase.mode());
        assertEquals(2.5, phase.edgeSpeed().orElseThrow(), 1e-9);
        assertEquals(Duration.ofMinutes(25), phase.duration().orElseThrow());

        assertThrows(IllegalArgumentException.class,
                () -> BorderPhaseParser.parse("50% -> 1000 @ speed:1.0,prefer:25m", GAME));
    }

    @Test
    @DisplayName("Serializer-Roundtrip erhält alle Phasen")
    void serializerRoundtrip() {
        List<String> original = List.of(
                "20m -> 1000 @ duration:10m",
                "1h|alive<4 -> 200 @ speed:1.0",
                "90m -> 150 @ max:2.5",
                "95m -> 120 @ max:2.5,prefer:20m");
        List<BorderPhaseConfig> phases = BorderPhaseParser.parseAll(original, GAME);
        BorderSettings settings = new BorderSettings(2500, 100, 2.5, phases);

        List<String> serialized = BorderPhaseSerializer.serialize(settings);
        List<BorderPhaseConfig> reparsed = BorderPhaseParser.parseAll(serialized, GAME);

        assertEquals(phases, reparsed);
    }
}
