package com.hungergames.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired on the main thread when a player is added to or removed from the
 * tournament whitelist.
 */
public class WhitelistChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Action {
        ADDED,
        REMOVED
    }

    private final UUID player;
    private final Action action;

    public WhitelistChangedEvent(UUID player, Action action) {
        this.player = player;
        this.action = action;
    }

    public UUID getPlayer() {
        return player;
    }

    public Action getAction() {
        return action;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
