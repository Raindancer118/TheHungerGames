package com.hungergames.api.service;

/**
 * Outcome of a team mutation. Operations never fail silently; every rejected
 * change reports its reason.
 */
public enum TeamOperationResult {

    /** The operation was applied. */
    SUCCESS,

    /** No team with the given id exists. */
    TEAM_NOT_FOUND,

    /** A team with the same name (or id) already exists. */
    NAME_TAKEN,

    /** The requested color is already claimed by another team. */
    COLOR_TAKEN,

    /** The team already has the configured maximum number of members. */
    TEAM_FULL,

    /** Teams are locked in the current game phase (see {@code teams.lock-phase}). */
    PHASE_LOCKED,

    /** The player is not on the tournament whitelist. */
    PLAYER_NOT_WHITELISTED,

    /** The player is already a member of the target team. */
    ALREADY_MEMBER,

    /** The player is not a member of any team (for remove/captain operations). */
    NOT_A_MEMBER,

    /** Team switching is disabled by configuration. */
    SWITCHING_DISABLED,

    /** The maximum number of teams has been reached. */
    TEAM_LIMIT_REACHED,

    /** Teams are disabled by configuration ({@code teams.enabled=false}, solo mode). */
    TEAMS_DISABLED;

    /** @return {@code true} if the operation was applied. */
    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
