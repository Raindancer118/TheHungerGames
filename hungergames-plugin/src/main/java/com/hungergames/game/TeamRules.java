package com.hungergames.game;

import com.hungergames.api.model.GamePhase;

/**
 * Konfigurierbare Regeln für Teams. Wird pro Zugriff aus dem
 * ConfigurationService gelesen, damit Laufzeitänderungen sofort greifen.
 *
 * @param enabled                sind Teams überhaupt aktiv? {@code false} = Solo-Modus, jeder für sich
 * @param maxTeamSize            maximale Mitgliederzahl pro Team; {@code 0} = unbegrenzt
 * @param maxTeams               maximale Teamanzahl; {@code 0} = unbegrenzt
 * @param allowSwitching         dürfen Spieler ihr Team wechseln (bis zum Lock)?
 * @param captainEnabled         Captain-System aktiv?
 * @param playersCanCreateTeams  dürfen Spieler über die GUI eigene Teams anlegen?
 * @param playersCanChooseColor  dürfen Spieler/Captains die Teamfarbe wählen?
 * @param lockFromPhase          ab dieser Phase sind Teams eingefroren
 */
public record TeamRules(
        boolean enabled,
        int maxTeamSize,
        int maxTeams,
        boolean allowSwitching,
        boolean captainEnabled,
        boolean playersCanCreateTeams,
        boolean playersCanChooseColor,
        GamePhase lockFromPhase) {

    /** Standardregeln: Teams aktiv, 2er-Teams, Wechsel erlaubt, Lock ab STARTUP. */
    public static TeamRules defaults() {
        return new TeamRules(true, 2, 0, true, false, true, true, GamePhase.STARTUP);
    }

    /** @return {@code true}, wenn Teams in der gegebenen Phase gesperrt sind. */
    public boolean isLocked(GamePhase phase) {
        return phase.ordinal() >= lockFromPhase.ordinal();
    }
}
