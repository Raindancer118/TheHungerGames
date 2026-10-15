package com.hungergames.api.event;

import com.hungergames.api.model.Team;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired on the main thread when a team is deleted. Members become teamless. */
public class TeamDeletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Team team;

    /** @param team snapshot of the team as it was before deletion */
    public TeamDeletedEvent(Team team) {
        this.team = team;
    }

    public Team getTeam() {
        return team;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
