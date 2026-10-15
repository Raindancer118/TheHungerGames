package com.hungergames.api.model;

/**
 * The tournament state of a participant.
 *
 * <p>Note that being online is deliberately <em>not</em> a state: a
 * disconnected participant remains {@link #ALIVE} until they are actually
 * eliminated.</p>
 */
public enum ParticipantState {

    /** Still in the game (regardless of connection status). */
    ALIVE,

    /** Eliminated from the game (death or admin action). */
    ELIMINATED
}
