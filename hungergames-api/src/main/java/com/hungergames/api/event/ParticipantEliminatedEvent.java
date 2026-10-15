package com.hungergames.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Fired on the main thread when a participant is eliminated from the round
 * (death or admin action). Not fired for mere disconnects.
 */
public class ParticipantEliminatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID participant;
    private final UUID killer;
    private final int remainingAlive;

    /**
     * @param participant    the eliminated player
     * @param killer         the killer, or {@code null} for environmental/admin eliminations
     * @param remainingAlive number of participants still alive after this elimination
     */
    public ParticipantEliminatedEvent(UUID participant, UUID killer, int remainingAlive) {
        this.participant = participant;
        this.killer = killer;
        this.remainingAlive = remainingAlive;
    }

    public UUID getParticipant() {
        return participant;
    }

    public Optional<UUID> getKiller() {
        return Optional.ofNullable(killer);
    }

    public int getRemainingAlive() {
        return remainingAlive;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
