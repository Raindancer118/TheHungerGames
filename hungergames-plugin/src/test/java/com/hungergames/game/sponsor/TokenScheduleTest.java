package com.hungergames.game.sponsor;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenScheduleTest {

    private static final Duration FIRST = Duration.ofMinutes(10);
    private static final Duration INTERVAL = Duration.ofMinutes(10);

    @Test
    void keineWelleVorDemErstenZeitpunkt() {
        assertEquals(0, TokenSchedule.dueWaves(Duration.ofMinutes(9), FIRST, INTERVAL));
    }

    @Test
    void ersteWelleZumStartzeitpunkt() {
        assertEquals(1, TokenSchedule.dueWaves(Duration.ofMinutes(10), FIRST, INTERVAL));
    }

    @Test
    void weitereWellenImIntervall() {
        assertEquals(1, TokenSchedule.dueWaves(Duration.ofMinutes(19), FIRST, INTERVAL));
        assertEquals(2, TokenSchedule.dueWaves(Duration.ofMinutes(20), FIRST, INTERVAL));
        assertEquals(4, TokenSchedule.dueWaves(Duration.ofMinutes(45), FIRST, INTERVAL));
    }

    @Test
    void rejoinHoltFehlendeWellenNach() {
        int due = TokenSchedule.dueWaves(Duration.ofMinutes(45), FIRST, INTERVAL); // 4
        assertEquals(3, TokenSchedule.pendingTokens(due, 1, 1, 1, 0));
    }

    @Test
    void keineDoppeltenTokensNachRestart() {
        int due = TokenSchedule.dueWaves(Duration.ofMinutes(45), FIRST, INTERVAL);
        assertEquals(0, TokenSchedule.pendingTokens(due, due, 1, due, 0));
    }

    @Test
    void maxProSpielerDeckelt() {
        assertEquals(2, TokenSchedule.pendingTokens(10, 0, 1, 3, 5));
        assertEquals(0, TokenSchedule.pendingTokens(10, 0, 1, 5, 5));
        assertEquals(10, TokenSchedule.pendingTokens(10, 0, 1, 3, 0)); // 0 = unbegrenzt
    }

    @Test
    void mehrereTokensProWelle() {
        assertEquals(6, TokenSchedule.pendingTokens(3, 0, 2, 0, 0));
    }
}
