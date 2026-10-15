package com.hungergames.api.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to live and final statistics of the current round.
 *
 * <p>All values refer to the running (or most recently finished) session and
 * are reset when a new round starts.</p>
 */
public interface StatsService {

    /** @return the number of kills the given participant scored this round. */
    int kills(UUID participant);

    /** @return kill counts of all participants (immutable snapshot). */
    Map<UUID, Integer> killCounts();

    /**
     * @param limit maximum number of entries
     * @return participants ordered by kill count, best first
     */
    List<KillEntry> topKillers(int limit);

    /** One leaderboard entry. */
    record KillEntry(UUID participant, String lastKnownName, int kills) {
    }

    /** @return total kills of all members of a team combined. */
    int teamKills(com.hungergames.api.model.TeamId teamId);

    /**
     * @return elapsed game time since the RUNNING phase began. Uses the
     *         virtual clock, i.e. it respects {@code /speedup}. Empty before
     *         the game has started.
     */
    Optional<Duration> elapsedGameTime();

    /** @return configured total game duration. */
    Duration configuredGameDuration();

    /** @return number of participants still alive. */
    int aliveCount();

    /** @return number of eliminated participants. */
    int eliminatedCount();
}
