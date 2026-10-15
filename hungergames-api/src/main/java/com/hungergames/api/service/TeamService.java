package com.hungergames.api.service;

import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages teams: creation, membership, colors and captains.
 *
 * <p>Teams can be modified through this API, by admins, by players via the
 * in-game selection GUI, or randomly — all paths go through this service and
 * are subject to the same rules (team size, switching rules, lock phase),
 * except where a caller explicitly bypasses them via {@code force}-semantics
 * documented on the individual method.</p>
 *
 * <p>All mutating methods fire the corresponding Bukkit events
 * ({@code TeamCreatedEvent}, {@code TeamDeletedEvent},
 * {@code TeamColorChangedEvent}, {@code TeamMembershipChangedEvent})
 * on success.</p>
 */
public interface TeamService {

    /**
     * Creates a new team with an automatically assigned free color.
     *
     * @param name the display name; the {@link TeamId} is derived from it
     * @return the created team, or the failure reason
     *         ({@link TeamOperationResult#NAME_TAKEN},
     *         {@link TeamOperationResult#TEAM_LIMIT_REACHED},
     *         {@link TeamOperationResult#PHASE_LOCKED},
     *         {@link TeamOperationResult#COLOR_TAKEN} if no color is left)
     */
    TeamCreation create(String name);

    /**
     * Creates a new team with the given color.
     *
     * @see #create(String)
     */
    TeamCreation create(String name, TeamColor color);

    /** Result of {@link TeamService#create}. {@code team} is present iff {@code status.isSuccess()}. */
    record TeamCreation(TeamOperationResult status, Optional<Team> team) {

        public static TeamCreation failure(TeamOperationResult status) {
            return new TeamCreation(status, Optional.empty());
        }

        public static TeamCreation success(Team team) {
            return new TeamCreation(TeamOperationResult.SUCCESS, Optional.of(team));
        }
    }

    /**
     * Deletes a team. Its members become teamless participants.
     */
    TeamOperationResult delete(TeamId id);

    /** Renames a team. The {@link TeamId} stays stable. */
    TeamOperationResult rename(TeamId id, String newName);

    /**
     * Claims a color for a team. Fails with
     * {@link TeamOperationResult#COLOR_TAKEN} if another team already claimed
     * it — colors are never reassigned silently.
     */
    TeamOperationResult setColor(TeamId id, TeamColor color);

    /** @return all colors not currently claimed by any team. */
    Set<TeamColor> availableColors();

    /**
     * Assigns a player to a team, moving them from their previous team if
     * necessary. Subject to team size, switching rules and lock phase.
     */
    TeamOperationResult assign(UUID player, TeamId team);

    /** Removes a player from their team. */
    TeamOperationResult removeFromTeam(UUID player);

    /** Sets the team captain (requires the captain system to be enabled). */
    TeamOperationResult setCaptain(TeamId id, UUID player);

    /**
     * Randomly distributes all whitelisted players without a team onto
     * existing teams (respecting the size limit), creating teams if allowed
     * and necessary.
     *
     * @return the number of players that were assigned
     */
    int assignRandomly();

    /** @return all teams (immutable snapshot). */
    List<Team> teams();

    /** @return the team with the given id. */
    Optional<Team> team(TeamId id);

    /** @return the team the given player belongs to, if any. */
    Optional<Team> teamOf(UUID player);
}
