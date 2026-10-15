package com.hungergames.api.event;

import com.hungergames.api.model.GamePhase;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the main thread whenever the game transitions into a new phase.
 */
public class GamePhaseChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GamePhase oldPhase;
    private final GamePhase newPhase;

    public GamePhaseChangeEvent(GamePhase oldPhase, GamePhase newPhase) {
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
    }

    public GamePhase getOldPhase() {
        return oldPhase;
    }

    public GamePhase getNewPhase() {
        return newPhase;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
