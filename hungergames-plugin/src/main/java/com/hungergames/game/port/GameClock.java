package com.hungergames.game.port;

/**
 * Zeitquelle der Spiellogik — abstrahiert für Tests und für die virtuelle
 * Zeit des {@code /speedup}-Systems.
 */
@FunctionalInterface
public interface GameClock {

    /** @return aktueller Zeitpunkt in Millisekunden (Epoche). */
    long nowMillis();

    /** Systemuhr für den Produktionsbetrieb. */
    static GameClock system() {
        return System::currentTimeMillis;
    }
}
