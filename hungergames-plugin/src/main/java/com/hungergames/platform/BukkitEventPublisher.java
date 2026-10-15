package com.hungergames.platform;

import com.hungergames.api.event.GamePhaseChangeEvent;
import com.hungergames.api.event.ParticipantEliminatedEvent;
import com.hungergames.api.event.ParticipantKillEvent;
import com.hungergames.api.event.ParticipantRevivedEvent;
import com.hungergames.api.event.TeamColorChangedEvent;
import com.hungergames.api.event.TeamCreatedEvent;
import com.hungergames.api.event.TeamDeletedEvent;
import com.hungergames.api.event.TeamMembershipChangedEvent;
import com.hungergames.api.event.WhitelistChangedEvent;
import com.hungergames.api.event.WinnerDeclaredEvent;
import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.model.Winner;
import com.hungergames.game.port.GameEventPublisher;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Feuert die öffentlichen Bukkit-Events der API. Aufrufe aus fremden Threads
 * werden auf den Main-Thread umgeleitet.
 */
public final class BukkitEventPublisher implements GameEventPublisher {

    private final Plugin plugin;

    public BukkitEventPublisher(Plugin plugin) {
        this.plugin = plugin;
    }

    private void fire(Event event) {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(event));
        }
    }

    @Override
    public void phaseChanged(GamePhase oldPhase, GamePhase newPhase) {
        fire(new GamePhaseChangeEvent(oldPhase, newPhase));
    }

    @Override
    public void participantEliminated(UUID participant, UUID killer, int remainingAlive) {
        fire(new ParticipantEliminatedEvent(participant, killer, remainingAlive));
    }

    @Override
    public void participantRevived(UUID participant) {
        fire(new ParticipantRevivedEvent(participant));
    }

    @Override
    public void whitelistChanged(UUID player, boolean added) {
        fire(new WhitelistChangedEvent(player,
                added ? WhitelistChangedEvent.Action.ADDED : WhitelistChangedEvent.Action.REMOVED));
    }

    @Override
    public void teamCreated(Team team) {
        fire(new TeamCreatedEvent(team));
    }

    @Override
    public void teamDeleted(Team team) {
        fire(new TeamDeletedEvent(team));
    }

    @Override
    public void teamColorChanged(Team team, TeamColor oldColor, TeamColor newColor) {
        fire(new TeamColorChangedEvent(team, oldColor, newColor));
    }

    @Override
    public void teamMembershipChanged(UUID player, TeamId oldTeam, TeamId newTeam, MembershipCause cause) {
        fire(new TeamMembershipChangedEvent(player, oldTeam, newTeam, switch (cause) {
            case API -> TeamMembershipChangedEvent.Cause.API;
            case ADMIN -> TeamMembershipChangedEvent.Cause.ADMIN;
            case PLAYER -> TeamMembershipChangedEvent.Cause.PLAYER;
            case RANDOM -> TeamMembershipChangedEvent.Cause.RANDOM;
        }));
    }

    @Override
    public void kill(UUID killer, UUID victim, int killerTotalKills) {
        fire(new ParticipantKillEvent(killer, victim, killerTotalKills));
    }

    @Override
    public void winnerDeclared(Winner winner) {
        fire(new WinnerDeclaredEvent(winner));
    }
}
