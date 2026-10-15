package com.hungergames.game.support;

import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.model.Winner;
import com.hungergames.game.port.GameEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Zeichnet alle Events als Strings auf — für Assertions in Logik-Tests.
 */
public final class RecordingEventPublisher implements GameEventPublisher {

    public final List<String> events = new ArrayList<>();
    public final List<Winner> winners = new ArrayList<>();

    @Override
    public void phaseChanged(GamePhase oldPhase, GamePhase newPhase) {
        events.add("phase:" + oldPhase + "->" + newPhase);
    }

    @Override
    public void participantEliminated(UUID participant, UUID killer, int remainingAlive) {
        events.add("eliminated:" + participant + ":remaining=" + remainingAlive);
    }

    @Override
    public void participantRevived(UUID participant) {
        events.add("revived:" + participant);
    }

    @Override
    public void whitelistChanged(UUID player, boolean added) {
        events.add("whitelist:" + player + ":" + (added ? "added" : "removed"));
    }

    @Override
    public void teamCreated(Team team) {
        events.add("teamCreated:" + team.id());
    }

    @Override
    public void teamDeleted(Team team) {
        events.add("teamDeleted:" + team.id());
    }

    @Override
    public void teamColorChanged(Team team, TeamColor oldColor, TeamColor newColor) {
        events.add("teamColor:" + team.id() + ":" + oldColor + "->" + newColor);
    }

    @Override
    public void teamMembershipChanged(UUID player, TeamId oldTeam, TeamId newTeam, MembershipCause cause) {
        events.add("membership:" + player + ":" + oldTeam + "->" + newTeam + ":" + cause);
    }

    @Override
    public void kill(UUID killer, UUID victim, int killerTotalKills) {
        events.add("kill:" + killer + "->" + victim + ":total=" + killerTotalKills);
    }

    @Override
    public void winnerDeclared(Winner winner) {
        events.add("winner:" + winner);
        winners.add(winner);
    }
}
