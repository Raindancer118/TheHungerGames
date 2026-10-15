package com.hungergames.game.border;

/**
 * Ein erkannter Widerspruch in der Border-Konfiguration.
 *
 * <p>Konflikte werden dem Admin über eine GUI mit konkreten
 * {@link BorderResolution}-Optionen präsentiert — es wird niemals
 * stillschweigend angepasst.</p>
 *
 * @param phaseIndex   Index der betroffenen Phase
 * @param type         Art des Konflikts
 * @param impliedSpeed die implizierte Kantengeschwindigkeit (Blöcke/s), falls relevant
 * @param limit        der verletzte Grenzwert (z. B. maxEdgeSpeed oder minimumSize)
 */
public record BorderConflict(int phaseIndex, Type type, double impliedSpeed, double limit) {

    public enum Type {
        /** Die implizierte Kantengeschwindigkeit überschreitet {@code maxEdgeSpeed}. */
        SPEED_EXCEEDS_MAX,

        /** Die Zielgröße liegt unter {@code minimumSize}. */
        TARGET_BELOW_MINIMUM,

        /** Die Zielgröße ist nicht kleiner als die Ausgangsgröße der Phase. */
        TARGET_NOT_SHRINKING,

        /** Startzeit + Dauer überschreiten die konfigurierte Spielzeit. */
        EXCEEDS_GAME_TIME,

        /** Zeit-Trigger der Phasen sind nicht aufsteigend. */
        PHASES_OUT_OF_ORDER
    }
}
