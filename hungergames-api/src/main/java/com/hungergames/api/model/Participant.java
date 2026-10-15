package com.hungergames.api.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An immutable snapshot of a tournament participant.
 *
 * <p>The player is identified exclusively by {@link #uuid()}. The
 * {@link #lastKnownName()} is a display cache only and may be outdated if the
 * player renamed their account.</p>
 *
 * @param uuid          the player's Mojang UUID (primary identity)
 * @param lastKnownName the last username observed for this player, never null
 * @param state         alive or eliminated
 * @param teamId        the team this participant belongs to, if any
 */
public record Participant(
        UUID uuid,
        String lastKnownName,
        ParticipantState state,
        Optional<TeamId> teamId) {

    public Participant {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(teamId, "teamId");
    }

    /** @return {@code true} if this participant has not been eliminated. */
    public boolean isAlive() {
        return state == ParticipantState.ALIVE;
    }

    /** @return a copy of this participant with the given state. */
    public Participant withState(ParticipantState newState) {
        return new Participant(uuid, lastKnownName, newState, teamId);
    }

    /** @return a copy of this participant with the given team (or empty to clear). */
    public Participant withTeam(Optional<TeamId> newTeam) {
        return new Participant(uuid, lastKnownName, state, newTeam);
    }

    /** @return a copy of this participant with an updated display name. */
    public Participant withName(String name) {
        return new Participant(uuid, name, state, teamId);
    }
}
