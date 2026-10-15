package com.hungergames.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired on the main thread when an eliminated participant is restored to the
 * alive state (admin correction).
 */
public class ParticipantRevivedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID participant;

    public ParticipantRevivedEvent(UUID participant) {
        this.participant = participant;
    }

    public UUID getParticipant() {
        return participant;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
