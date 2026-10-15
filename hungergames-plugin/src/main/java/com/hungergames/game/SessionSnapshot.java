package com.hungergames.game;

import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.ParticipantState;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.model.Winner;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serialisierbare Momentaufnahme der kompletten Game-Session.
 * Wird vom {@link com.hungergames.game.port.SessionStore} persistiert, damit
 * ein Serverneustart eine laufende Runde wiederherstellen kann.
 *
 * @param phase              aktuelle Spielphase
 * @param participants       alle Teilnehmer mit Zustand
 * @param teams              alle Teams mit Mitgliedern und Farbe
 * @param winner             das Rundenergebnis, oder {@code null} solange offen
 * @param kills              Kill-Zähler pro Teilnehmer
 * @param runningSinceMillis Epochen-Zeitpunkt des RUNNING-Beginns, oder {@code null}
 */
public record SessionSnapshot(
        GamePhase phase,
        List<ParticipantData> participants,
        List<TeamData> teams,
        Winner winner,
        Map<UUID, Integer> kills,
        Long runningSinceMillis) {

    public SessionSnapshot {
        participants = List.copyOf(participants);
        teams = List.copyOf(teams);
        kills = Map.copyOf(kills);
    }

    /** Persistierte Teilnehmerdaten. */
    public record ParticipantData(UUID uuid, String name, ParticipantState state) {
    }

    /**
     * Persistierte Teamdaten.
     *
     * @param captain {@code null}, wenn kein Captain gesetzt ist
     */
    public record TeamData(TeamId id, String name, TeamColor color, List<UUID> members, UUID captain) {

        public TeamData {
            members = List.copyOf(members);
        }
    }
}
