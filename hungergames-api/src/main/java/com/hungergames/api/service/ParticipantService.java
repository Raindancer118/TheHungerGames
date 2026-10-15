package com.hungergames.api.service;

import com.hungergames.api.model.Participant;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Read access to the participants of the current round.
 *
 * <p>Participant state is independent of connection status: a disconnected
 * player remains alive until eliminated. All returned collections are
 * immutable snapshots.</p>
 */
public interface ParticipantService {

    /** @return all participants of the current round. */
    Collection<Participant> participants();

    /** @return the participant with the given UUID, if part of the round. */
    Optional<Participant> participant(UUID uuid);

    /** @return the UUIDs of all participants that are still alive (online or not). */
    Set<UUID> alive();

    /** @return the UUIDs of all eliminated participants. */
    Set<UUID> eliminated();

    /** @return whether the given participant is still alive. */
    boolean isAlive(UUID uuid);
}
