package com.hungergames.game;

import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.service.TeamOperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teamregeln: exklusive Farben, Teamgröße, Phasen-Lock, Wechselregeln,
 * Zufallszuweisung.
 */
class TeamRegistryTest {

    private TeamRules rules = TeamRules.defaults();
    private GamePhase phase = GamePhase.LOBBY;
    private TeamRegistry registry;

    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID p3 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registry = new TeamRegistry(() -> rules, () -> phase, uuid -> true);
    }

    @Test
    @DisplayName("Doppelte Farbwahl wird abgelehnt — keine stillschweigende Umverteilung")
    void duplicateColorRejected() {
        registry.create("Rot", TeamColor.RED);
        TeamRegistry.CreationResult second = registry.create("Blau", TeamColor.RED);

        assertEquals(TeamOperationResult.COLOR_TAKEN, second.status());
        assertTrue(second.team().isEmpty());

        TeamRegistry.CreationResult third = registry.create("Blau", TeamColor.BLUE);
        assertTrue(third.status().isSuccess());
        assertEquals(TeamOperationResult.COLOR_TAKEN,
                registry.setColor(third.team().orElseThrow().id(), TeamColor.RED));
    }

    @Test
    @DisplayName("availableColors spiegelt vergebene Farben wider")
    void availableColorsShrink() {
        assertEquals(TeamColor.values().length, registry.availableColors().size());
        registry.create("Rot", TeamColor.RED);

        Set<TeamColor> available = registry.availableColors();
        assertFalse(available.contains(TeamColor.RED));
        assertEquals(TeamColor.values().length - 1, available.size());
    }

    @Test
    @DisplayName("setColor auf die eigene Farbe ist ein No-Op-Erfolg")
    void settingOwnColorSucceeds() {
        TeamId id = registry.create("Rot", TeamColor.RED).team().orElseThrow().id();
        assertEquals(TeamOperationResult.SUCCESS, registry.setColor(id, TeamColor.RED));
    }

    @Test
    @DisplayName("Teamgröße wird durchgesetzt")
    void teamSizeEnforced() {
        rules = new TeamRules(true, 2, 0, true, false, true, true, GamePhase.STARTUP);
        TeamId id = registry.create("Rot", TeamColor.RED).team().orElseThrow().id();

        assertTrue(registry.assign(p1, id).status().isSuccess());
        assertTrue(registry.assign(p2, id).status().isSuccess());
        assertEquals(TeamOperationResult.TEAM_FULL, registry.assign(p3, id).status());
    }

    @Test
    @DisplayName("Ab der Lock-Phase sind alle Mutationen gesperrt")
    void lockedFromConfiguredPhase() {
        TeamId id = registry.create("Rot", TeamColor.RED).team().orElseThrow().id();
        registry.assign(p1, id);

        phase = GamePhase.STARTUP; // Default-Lock ab STARTUP

        assertEquals(TeamOperationResult.PHASE_LOCKED, registry.create("Blau", TeamColor.BLUE).status());
        assertEquals(TeamOperationResult.PHASE_LOCKED, registry.assign(p2, id).status());
        assertEquals(TeamOperationResult.PHASE_LOCKED, registry.removeFromTeam(p1).status());
        assertEquals(TeamOperationResult.PHASE_LOCKED, registry.setColor(id, TeamColor.BLUE));
    }

    @Test
    @DisplayName("Teamwechsel folgt der Switching-Regel")
    void switchingRule() {
        TeamId red = registry.create("Rot", TeamColor.RED).team().orElseThrow().id();
        TeamId blue = registry.create("Blau", TeamColor.BLUE).team().orElseThrow().id();
        registry.assign(p1, red);

        // Wechsel erlaubt (Default): Spieler wandert inkl. oldTeam-Info
        TeamRegistry.MembershipChange change = registry.assign(p1, blue);
        assertTrue(change.status().isSuccess());
        assertEquals(red, change.oldTeam().orElseThrow());
        assertEquals(blue, registry.teamIdOf(p1).orElseThrow());

        // Wechsel verboten
        rules = new TeamRules(true, 2, 0, false, false, true, true, GamePhase.STARTUP);
        assertEquals(TeamOperationResult.SWITCHING_DISABLED, registry.assign(p1, red).status());
    }

    @Test
    @DisplayName("Whitelist-Pflicht für Teamzuweisung")
    void whitelistRequired() {
        registry = new TeamRegistry(() -> rules, () -> phase, uuid -> false);
        TeamId id = registry.create("Rot", TeamColor.RED).team().orElseThrow().id();

        assertEquals(TeamOperationResult.PLAYER_NOT_WHITELISTED, registry.assign(p1, id).status());
    }

    @Test
    @DisplayName("Zufallszuweisung füllt kleinste Teams zuerst und legt bei Bedarf neue an")
    void randomAssignment() {
        rules = new TeamRules(true, 2, 0, true, false, true, true, GamePhase.STARTUP);
        TeamId red = registry.create("Rot", TeamColor.RED).team().orElseThrow().id();
        registry.assign(p1, red); // Rot hat 1/2

        List<UUID> unassigned = List.of(p2, p3, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Map<UUID, TeamId> assigned = registry.assignRandomly(unassigned, new Random(42));

        assertEquals(5, assigned.size());
        // Kein Team überschreitet die Maximalgröße
        registry.all().forEach(team -> assertTrue(team.size() <= 2,
                "Team " + team.id() + " überschreitet maxTeamSize: " + team.size()));
        // Alle Kandidaten haben jetzt ein Team
        assigned.keySet().forEach(uuid -> assertTrue(registry.teamIdOf(uuid).isPresent()));
    }

    @Test
    @DisplayName("Zufallszuweisung überspringt bereits zugewiesene Spieler")
    void randomAssignmentSkipsAssigned() {
        TeamId red = registry.create("Rot", TeamColor.RED).team().orElseThrow().id();
        registry.assign(p1, red);

        Map<UUID, TeamId> assigned = registry.assignRandomly(List.of(p1, p2), new Random(1));

        assertFalse(assigned.containsKey(p1));
        assertTrue(assigned.containsKey(p2));
    }

    @Test
    @DisplayName("Teamnamen sind eindeutig (case-insensitive)")
    void teamNamesUnique() {
        registry.create("Rot", TeamColor.RED);
        assertEquals(TeamOperationResult.NAME_TAKEN, registry.create("rot", TeamColor.BLUE).status());
        assertEquals(TeamOperationResult.NAME_TAKEN, registry.create("ROT", null).status());
    }

    @Test
    @DisplayName("Löschen gibt Snapshot zurück; Mitglieder werden teamlos")
    void deleteReturnsSnapshot() {
        TeamId id = registry.create("Rot", TeamColor.RED).team().orElseThrow().id();
        registry.assign(p1, id);

        var deleted = registry.delete(id);
        assertTrue(deleted.isPresent());
        assertTrue(deleted.get().isMember(p1));
        assertTrue(registry.teamIdOf(p1).isEmpty());
        assertTrue(registry.availableColors().contains(TeamColor.RED));
    }

    @Test
    @DisplayName("teams.enabled=false: Erstellen, Zuweisen und Zufallsverteilung sind No-Ops")
    void teamsDisabledIsSoloMode() {
        rules = new TeamRules(false, 2, 0, true, false, true, true, GamePhase.LOBBY);

        assertEquals(TeamOperationResult.TEAMS_DISABLED, registry.create("Rot", TeamColor.RED).status());
        assertTrue(registry.all().isEmpty());

        Map<UUID, TeamId> assigned = registry.assignRandomly(List.of(p1, p2, p3), new Random(7));
        assertTrue(assigned.isEmpty());
        assertTrue(registry.teamIdOf(p1).isEmpty());

        assertFalse(registry.enabled());
    }

    @Test
    @DisplayName("Captain muss Mitglied sein und Captain-System aktiviert")
    void captainRules() {
        TeamId id = registry.create("Rot", TeamColor.RED).team().orElseThrow().id();
        registry.assign(p1, id);

        // Captain-System deaktiviert (Default)
        assertEquals(TeamOperationResult.SWITCHING_DISABLED, registry.setCaptain(id, p1));

        rules = new TeamRules(true, 2, 0, true, true, true, true, GamePhase.STARTUP);
        assertEquals(TeamOperationResult.NOT_A_MEMBER, registry.setCaptain(id, p2));
        assertEquals(TeamOperationResult.SUCCESS, registry.setCaptain(id, p1));
        assertEquals(p1, registry.team(id).orElseThrow().captain().orElseThrow());
    }
}
