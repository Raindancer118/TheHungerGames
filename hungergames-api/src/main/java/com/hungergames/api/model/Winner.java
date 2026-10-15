package com.hungergames.api.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The outcome of a finished round.
 *
 * <p>Query via {@code HungerGamesApi#winner()} once the game reached
 * {@link GamePhase#FINISHED}, or listen for
 * {@code WinnerDeclaredEvent}.</p>
 */
public sealed interface Winner {

    /** A single player won the round. */
    record Solo(UUID uuid) implements Winner {
        public Solo {
            Objects.requireNonNull(uuid, "uuid");
        }
    }

    /**
     * A whole team won the round.
     *
     * @param teamId  the winning team
     * @param members all members of the winning team at the time of victory
     */
    record Team(TeamId teamId, Set<UUID> members) implements Winner {
        public Team {
            Objects.requireNonNull(teamId, "teamId");
            members = Set.copyOf(members);
        }
    }

    /** The round ended without a victor (e.g. time limit with no survivors). */
    record None() implements Winner {
    }
}
