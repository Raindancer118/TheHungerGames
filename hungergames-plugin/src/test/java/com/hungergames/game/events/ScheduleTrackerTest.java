package com.hungergames.game.events;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleTrackerTest {

    private static final List<Duration> SCHEDULE = List.of(
            Duration.ofMinutes(20), Duration.ofMinutes(45), Duration.ofMinutes(75));

    @Test
    void nichtsFaelligVorDemErstenZeitpunkt() {
        assertEquals(List.of(),
                ScheduleTracker.dueIndices(SCHEDULE, Set.of(), Duration.ofMinutes(19)));
    }

    @Test
    void ersterZeitpunktFaellig() {
        assertEquals(List.of(0),
                ScheduleTracker.dueIndices(SCHEDULE, Set.of(), Duration.ofMinutes(20)));
    }

    @Test
    void ausgeloesteWerdenUebersprungen() {
        assertEquals(List.of(1),
                ScheduleTracker.dueIndices(SCHEDULE, Set.of(0), Duration.ofMinutes(50)));
    }

    @Test
    void nachDowntimeWerdenVerpassteNachgeholt() {
        // Server war offline, Spielzeit lief weiter (COUNT-Policy): beide fällig
        assertEquals(List.of(1, 2),
                ScheduleTracker.dueIndices(SCHEDULE, Set.of(0), Duration.ofMinutes(80)));
    }

    @Test
    void keineDoppeltenNachRestart() {
        assertEquals(List.of(),
                ScheduleTracker.dueIndices(SCHEDULE, Set.of(0, 1, 2), Duration.ofHours(3)));
    }
}
