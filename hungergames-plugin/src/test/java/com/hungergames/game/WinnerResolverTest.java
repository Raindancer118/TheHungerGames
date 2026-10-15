package com.hungergames.game;

import com.hungergames.api.model.Participant;
import com.hungergames.api.model.ParticipantState;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.model.Winner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gewinnerermittlung — rein, ohne Bukkit. Deckt die geforderten Szenarien ab:
 * 4 Lebende, 3 Lebende aus 2 Teams, mehrere Verbleibende desselben Teams,
 * sowie Disconnect-Verhalten (offline == lebendig).
 */
class WinnerResolverTest {

    private static final TeamId RED = new TeamId("red");
    private static final TeamId BLUE = new TeamId("blue");

    private static Participant alive(String name, TeamId team) {
        return new Participant(UUID.randomUUID(), name, ParticipantState.ALIVE, Optional.ofNullable(team));
    }

    private static Participant dead(String name, TeamId team) {
        return new Participant(UUID.randomUUID(), name, ParticipantState.ELIMINATED, Optional.ofNullable(team));
    }

    @Test
    @DisplayName("4 lebende Spieler (2 Teams) → noch kein Sieger")
    void fourAliveNoWinner() {
        List<Participant> participants = List.of(
                alive("a", RED), alive("b", RED),
                alive("c", BLUE), alive("d", BLUE));

        assertTrue(WinnerResolver.resolve(participants).isEmpty());
    }

    @Test
    @DisplayName("3 Lebende aus 2 Teams → noch kein Sieger")
    void threeAliveTwoTeamsNoWinner() {
        List<Participant> participants = List.of(
                alive("a", RED), alive("b", RED),
                alive("c", BLUE), dead("d", BLUE));

        assertTrue(WinnerResolver.resolve(participants).isEmpty());
    }

    @Test
    @DisplayName("Mehrere Verbleibende desselben Teams → Teamsieg")
    void remainingPlayersOfSameTeamWin() {
        List<Participant> participants = List.of(
                alive("a", RED), alive("b", RED),
                dead("c", BLUE), dead("d", BLUE));

        Winner winner = WinnerResolver.resolve(participants).orElseThrow();
        Winner.Team teamWin = assertInstanceOf(Winner.Team.class, winner);
        assertEquals(RED, teamWin.teamId());
        assertEquals(2, teamWin.members().size());
    }

    @Test
    @DisplayName("Teamsieg umfasst auch bereits eliminierte Teammitglieder")
    void teamWinIncludesEliminatedMembers() {
        Participant deadRed = dead("a", RED);
        List<Participant> participants = List.of(
                deadRed, alive("b", RED),
                dead("c", BLUE), dead("d", BLUE));

        Winner.Team teamWin = assertInstanceOf(Winner.Team.class,
                WinnerResolver.resolve(participants).orElseThrow());
        assertTrue(teamWin.members().contains(deadRed.uuid()));
    }

    @Test
    @DisplayName("Letzter Lebender ohne Team → Solosieg")
    void lastTeamlessPlayerWinsSolo() {
        Participant last = alive("a", null);
        List<Participant> participants = List.of(last, dead("b", null), dead("c", null));

        Winner winner = WinnerResolver.resolve(participants).orElseThrow();
        assertEquals(new Winner.Solo(last.uuid()), winner);
    }

    @Test
    @DisplayName("Letzter Lebender mit Team → Teamsieg seines Teams")
    void lastPlayerWithTeamWinsForTeam() {
        List<Participant> participants = List.of(
                alive("a", RED), dead("b", RED),
                dead("c", BLUE), dead("d", BLUE));

        Winner.Team teamWin = assertInstanceOf(Winner.Team.class,
                WinnerResolver.resolve(participants).orElseThrow());
        assertEquals(RED, teamWin.teamId());
    }

    @Test
    @DisplayName("Teamloser Lebender neben Team-Lebenden → Spiel läuft weiter")
    void teamlessAliveBlocksTeamWin() {
        List<Participant> participants = List.of(
                alive("a", RED), alive("b", RED),
                alive("lone", null));

        assertTrue(WinnerResolver.resolve(participants).isEmpty());
    }

    @Test
    @DisplayName("Keine Lebenden → Winner.None")
    void nobodyAliveIsNone() {
        List<Participant> participants = List.of(dead("a", RED), dead("b", BLUE));

        assertInstanceOf(Winner.None.class, WinnerResolver.resolve(participants).orElseThrow());
    }

    @Test
    @DisplayName("Disconnect ändert nichts: ALIVE zählt unabhängig vom Online-Status")
    void disconnectedAlivePlayerStillCounts() {
        // Ein "disconnecteter" Spieler ist im Modell schlicht ALIVE — es gibt
        // bewusst keinen Online-Zustand. Zwei lebende Spieler aus zwei Teams
        // (einer davon offline) → kein Sieger.
        List<Participant> participants = List.of(
                alive("online", RED),
                alive("offline-but-alive", BLUE));

        assertTrue(WinnerResolver.resolve(participants).isEmpty());
    }

    @Test
    @DisplayName("Zeitablauf ohne Entscheidung → Winner.None")
    void timeoutWithoutDecisionIsNone() {
        List<Participant> participants = List.of(alive("a", RED), alive("b", BLUE));

        assertInstanceOf(Winner.None.class, WinnerResolver.resolveOnTimeout(participants));
    }

    @Test
    @DisplayName("Zeitablauf mit letztem Team → Teamsieg")
    void timeoutWithLastTeamStillResolves() {
        List<Participant> participants = List.of(alive("a", RED), alive("b", RED));

        assertInstanceOf(Winner.Team.class, WinnerResolver.resolveOnTimeout(participants));
    }
}
