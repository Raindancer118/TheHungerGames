package com.hungergames.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired on the main thread when a participant scores a kill.
 *
 * <p>Fired in addition to {@link ParticipantEliminatedEvent}; use this event
 * for kill streaks, stats plugins or reward systems.</p>
 */
public class ParticipantKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID killer;
    private final UUID victim;
    private final int killerTotalKills;

    /**
     * @param killerTotalKills the killer's total kill count including this kill
     */
    public ParticipantKillEvent(UUID killer, UUID victim, int killerTotalKills) {
        this.killer = killer;
        this.victim = victim;
        this.killerTotalKills = killerTotalKills;
    }

    public UUID getKiller() {
        return killer;
    }

    public UUID getVictim() {
        return victim;
    }

    public int getKillerTotalKills() {
        return killerTotalKills;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
