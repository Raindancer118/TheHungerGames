package com.hungergames.api.event;

import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired on the main thread when a team successfully claims a new color. */
public class TeamColorChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Team team;
    private final TeamColor oldColor;
    private final TeamColor newColor;

    public TeamColorChangedEvent(Team team, TeamColor oldColor, TeamColor newColor) {
        this.team = team;
        this.oldColor = oldColor;
        this.newColor = newColor;
    }

    /** @return snapshot of the team after the change */
    public Team getTeam() {
        return team;
    }

    public TeamColor getOldColor() {
        return oldColor;
    }

    public TeamColor getNewColor() {
        return newColor;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
