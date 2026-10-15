package com.hungergames.api.model;

/**
 * The lifecycle phases of a Hunger Games round.
 *
 * <p>Phases progress strictly forward within one round; a new round starts
 * again at {@link #NOT_INITIALIZED} after a reset.</p>
 */
public enum GamePhase {

    /** No arena has been initialized yet ({@code /init} not executed). */
    NOT_INITIALIZED,

    /** Arena generation and world preparation are in progress. */
    PREFLIGHT,

    /** Players gather in the lobby; teams may be chosen in this phase. */
    LOBBY,

    /** Players are teleported underground and levitated to their platforms. */
    STARTUP,

    /** Players stand on their platforms awaiting the countdown. */
    READY,

    /** The game is live. */
    RUNNING,

    /** A winner has been declared or the time limit was reached. */
    FINISHED;

    /**
     * @return {@code true} once the arena has been initialized
     *         (any phase after {@link #NOT_INITIALIZED}).
     */
    public boolean isInitialized() {
        return this != NOT_INITIALIZED;
    }

    /**
     * @return {@code true} while participants can still join or switch teams
     *         (lobby and pre-game phases, unless locked earlier by configuration).
     */
    public boolean isPreGame() {
        return this == NOT_INITIALIZED || this == PREFLIGHT || this == LOBBY;
    }
}
