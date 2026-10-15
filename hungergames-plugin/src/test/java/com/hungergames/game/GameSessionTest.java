package com.hungergames.game;

import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.model.Winner;
import com.hungergames.game.port.GameEventPublisher.MembershipCause;
import com.hungergames.game.support.InMemorySessionStore;
import com.hungergames.game.support.RecordingEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GameSession als Aggregat: Phasenübergänge, Eliminierung inkl.
 * Gewinnerermittlung, Disconnect-Semantik, Persistenz-Roundtrip.
 */
class GameSessionTest {

    private RecordingEventPublisher events;
    private InMemorySessionStore store;
    private GameSession session;

    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID p3 = UUID.randomUUID();
    private final UUID p4 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        events = new RecordingEventPublisher();
        store = new InMemorySessionStore();
        session = new GameSession(TeamRules::defaults, events, store,
                () -> 1_000_000L, new Random(7));
    }

    private void whitelistFour() {
        session.whitelistAdd(p1, "Anna");
        session.whitelistAdd(p2, "Bela");
        session.whitelistAdd(p3, "Cleo");
        session.whitelistAdd(p4, "Divo");
    }

    private void advanceToRunning() {
        session.transitionTo(GamePhase.PREFLIGHT);
        session.transitionTo(GamePhase.LOBBY);
        session.transitionTo(GamePhase.STARTUP);
        session.transitionTo(GamePhase.READY);
        session.transitionTo(GamePhase.RUNNING);
    }

    @Test
    @DisplayName("Nur zulässige Phasenübergänge werden akzeptiert")
    void phaseTransitions() {
        assertFalse(session.transitionTo(GamePhase.RUNNING));
        assertEquals(GamePhase.NOT_INITIALIZED, session.phase());

        assertTrue(session.transitionTo(GamePhase.PREFLIGHT));
        assertFalse(session.transitionTo(GamePhase.STARTUP)); // PREFLIGHT → STARTUP übersprungen
        assertTrue(session.transitionTo(GamePhase.LOBBY));
        assertFalse(session.transitionTo(GamePhase.RUNNING)); // LOBBY → RUNNING übersprungen
        assertTrue(session.transitionTo(GamePhase.STARTUP));
        assertTrue(session.transitionTo(GamePhase.READY));
        assertTrue(session.transitionTo(GamePhase.RUNNING));
        assertTrue(events.events.contains("phase:READY->RUNNING"));
    }

    @Test
    @DisplayName("Regulärer Ablauf /init → LOBBY → STARTUP → READY → RUNNING kommt durch")
    void fullHappyPathReachesRunning() {
        whitelistFour();
        assertTrue(session.transitionTo(GamePhase.PREFLIGHT), "init: PREFLIGHT");
        assertTrue(session.transitionTo(GamePhase.LOBBY), "Preflight-Ende: LOBBY");
        assertTrue(session.transitionTo(GamePhase.STARTUP), "/startup: STARTUP");
        assertTrue(session.transitionTo(GamePhase.READY), "Ankunft: READY");
        assertTrue(session.transitionTo(GamePhase.RUNNING), "/start: RUNNING");
        assertEquals(GamePhase.RUNNING, session.phase());
    }

    @Test
    @DisplayName("STARTUP → LOBBY ist der erlaubte Recovery-Pfad bei Abbruch")
    void startupCanFallBackToLobby() {
        session.transitionTo(GamePhase.PREFLIGHT);
        session.transitionTo(GamePhase.LOBBY);
        session.transitionTo(GamePhase.STARTUP);

        assertTrue(session.transitionTo(GamePhase.LOBBY), "Recovery zurück in die Lobby");
        assertTrue(session.transitionTo(GamePhase.STARTUP), "und erneuter Anlauf");
    }

    @Test
    @DisplayName("Eliminierung zählt Kills, feuert Events und kürt am Ende den Sieger")
    void eliminationFlowWithSoloWinner() {
        whitelistFour();
        advanceToRunning();

        assertTrue(session.eliminate(p2, p1));
        assertEquals(1, session.kills().kills(p1));
        assertTrue(session.eliminate(p3, p1));
        assertEquals(2, session.kills().kills(p1));
        assertTrue(session.winner().isEmpty(), "4-2=2 Lebende → noch kein Sieger");

        assertTrue(session.eliminate(p4, null));

        Winner winner = session.winner().orElseThrow();
        assertEquals(new Winner.Solo(p1), winner);
        assertEquals(GamePhase.FINISHED, session.phase());
        assertEquals(1, events.winners.size());
    }

    @Test
    @DisplayName("Disconnect existiert im Modell nicht: Offline-Spieler bleibt lebendig")
    void disconnectedPlayerStaysAlive() {
        whitelistFour();
        advanceToRunning();

        // p4 "disconnected" — es gibt keine API, die das als Elimination wertet.
        session.eliminate(p2, p1);
        session.eliminate(p3, p1);

        // p1 und der offline p4 leben noch → kein Sieger
        assertTrue(session.winner().isEmpty());
        assertEquals(2, session.participants().aliveCount());
        assertTrue(session.participants().isAlive(p4));
    }

    @Test
    @DisplayName("Doppelte Eliminierung ist idempotent und verfälscht den Zähler nicht")
    void doubleEliminationIgnored() {
        whitelistFour();
        advanceToRunning();

        assertTrue(session.eliminate(p2, p1));
        assertFalse(session.eliminate(p2, p1));

        assertEquals(1, session.kills().kills(p1));
        assertEquals(3, session.participants().aliveCount());
    }

    @Test
    @DisplayName("Eliminierung außerhalb von RUNNING wird ignoriert")
    void eliminationOnlyDuringRunning() {
        whitelistFour();
        assertFalse(session.eliminate(p1, null));
        assertTrue(session.participants().isAlive(p1));
    }

    @Test
    @DisplayName("Teamsieg: verbleibende Spieler desselben Teams gewinnen gemeinsam")
    void teamWinScenario() {
        whitelistFour();
        TeamId red = session.teamCreate("Rot", TeamColor.RED).team().orElseThrow().id();
        TeamId blue = session.teamCreate("Blau", TeamColor.BLUE).team().orElseThrow().id();
        session.transitionTo(GamePhase.PREFLIGHT);
        session.transitionTo(GamePhase.LOBBY);
        session.teamAssign(p1, red, MembershipCause.API);
        session.teamAssign(p2, red, MembershipCause.API);
        session.teamAssign(p3, blue, MembershipCause.API);
        session.teamAssign(p4, blue, MembershipCause.API);
        session.transitionTo(GamePhase.STARTUP);
        session.transitionTo(GamePhase.READY);
        session.transitionTo(GamePhase.RUNNING);

        session.eliminate(p3, p1);
        assertTrue(session.winner().isEmpty(), "3 Lebende aus 2 Teams → kein Sieger");

        session.eliminate(p4, p2);
        Winner.Team teamWin = assertInstanceOf(Winner.Team.class, session.winner().orElseThrow());
        assertEquals(red, teamWin.teamId());
        assertTrue(teamWin.members().containsAll(java.util.Set.of(p1, p2)));
    }

    @Test
    @DisplayName("Zeitablauf beendet die Runde immer mit einem Ergebnis")
    void timeoutDeclaresResult() {
        whitelistFour();
        advanceToRunning();

        session.declareTimeout();

        assertEquals(GamePhase.FINISHED, session.phase());
        assertInstanceOf(Winner.None.class, session.winner().orElseThrow());
    }

    @Test
    @DisplayName("Revive macht Eliminierung rückgängig")
    void reviveRestoresAlive() {
        whitelistFour();
        advanceToRunning();
        session.eliminate(p2, null);

        assertTrue(session.revive(p2));
        assertTrue(session.participants().isAlive(p2));
        assertTrue(events.events.contains("revived:" + p2));
    }

    @Test
    @DisplayName("Whitelist-Austrag entfernt auch die Teammitgliedschaft")
    void whitelistRemoveClearsTeam() {
        whitelistFour();
        TeamId red = session.teamCreate("Rot", TeamColor.RED).team().orElseThrow().id();
        session.transitionTo(GamePhase.PREFLIGHT);
        session.transitionTo(GamePhase.LOBBY);
        session.teamAssign(p1, red, MembershipCause.API);

        assertTrue(session.whitelistRemove(p1));
        assertFalse(session.isWhitelisted(p1));
        assertTrue(session.teamRegistry().teamIdOf(p1).isEmpty());
    }

    @Test
    @DisplayName("Session-Snapshot übersteht einen Neustart (Restore-Roundtrip)")
    void snapshotRestoreRoundtrip() {
        whitelistFour();
        TeamId red = session.teamCreate("Rot", TeamColor.RED).team().orElseThrow().id();
        session.transitionTo(GamePhase.PREFLIGHT);
        session.transitionTo(GamePhase.LOBBY);
        session.teamAssign(p1, red, MembershipCause.API);
        session.transitionTo(GamePhase.STARTUP);
        session.transitionTo(GamePhase.READY);
        session.transitionTo(GamePhase.RUNNING);
        session.eliminate(p2, p1);

        SessionSnapshot saved = store.load().orElseThrow();

        GameSession restored = new GameSession(TeamRules::defaults, new RecordingEventPublisher(),
                new InMemorySessionStore(), () -> 2_000_000L, new Random(1));
        restored.restore(saved);

        assertEquals(GamePhase.RUNNING, restored.phase());
        assertEquals(3, restored.participants().aliveCount());
        assertFalse(restored.participants().isAlive(p2));
        assertEquals(1, restored.kills().kills(p1));
        assertEquals(red, restored.teamRegistry().teamIdOf(p1).orElseThrow());
        assertEquals("Anna", restored.participants().nameOf(p1).orElseThrow());
        assertEquals(1_000_000L, restored.runningSinceMillis().orElseThrow());
    }

    @Test
    @DisplayName("Nächste Runde behält Whitelist und Teams, setzt Zustand zurück")
    void nextRoundKeepsRoster() {
        whitelistFour();
        advanceToRunning();
        session.eliminate(p2, p1);
        session.declareTimeout();

        session.resetForNextRound();

        assertEquals(GamePhase.NOT_INITIALIZED, session.phase());
        assertTrue(session.winner().isEmpty());
        assertEquals(4, session.participants().aliveCount());
        assertEquals(0, session.kills().kills(p1));
        assertTrue(session.isWhitelisted(p2));
    }
}
