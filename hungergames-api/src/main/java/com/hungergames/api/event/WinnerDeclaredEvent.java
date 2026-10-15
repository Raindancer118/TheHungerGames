package com.hungergames.api.event;

import com.hungergames.api.model.Winner;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the main thread when the round ends and a result is declared
 * (solo winner, team winner, or none on timeout).
 */
public class WinnerDeclaredEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Winner winner;

    public WinnerDeclaredEvent(Winner winner) {
        this.winner = winner;
    }

    public Winner getWinner() {
        return winner;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
