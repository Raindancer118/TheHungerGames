package com.hungergames.api.event;

import com.hungergames.api.model.TeamId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Fired on the main thread when a player joins, leaves or switches teams.
 */
public class TeamMembershipChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    /** How the membership change was initiated. */
    public enum Cause {
        /** Through the public API by another plugin. */
        API,
        /** By an admin command or admin GUI. */
        ADMIN,
        /** By the player through the team selection GUI. */
        PLAYER,
        /** By automatic/random assignment. */
        RANDOM
    }

    private final UUID player;
    private final TeamId oldTeam;
    private final TeamId newTeam;
    private final Cause cause;

    /**
     * @param oldTeam the previous team, or {@code null} if the player had none
     * @param newTeam the new team, or {@code null} if the player left their team
     */
    public TeamMembershipChangedEvent(UUID player, TeamId oldTeam, TeamId newTeam, Cause cause) {
        this.player = player;
        this.oldTeam = oldTeam;
        this.newTeam = newTeam;
        this.cause = cause;
    }

    public UUID getPlayer() {
        return player;
    }

    public Optional<TeamId> getOldTeam() {
        return Optional.ofNullable(oldTeam);
    }

    public Optional<TeamId> getNewTeam() {
        return Optional.ofNullable(newTeam);
    }

    public Cause getCause() {
        return cause;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
