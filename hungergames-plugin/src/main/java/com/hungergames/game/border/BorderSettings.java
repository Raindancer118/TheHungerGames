package com.hungergames.game.border;

import java.util.List;

/**
 * Gesamte Border-Konfiguration einer Runde.
 *
 * @param initialSize  Startdurchmesser in Blöcken
 * @param minimumSize  absolute Untergrenze — keine Phase darf darunter zielen
 * @param maxEdgeSpeed Fairness-Obergrenze in Blöcken/Sekunde pro Kante (z. B. 2.5)
 * @param phases       die Borderphasen in Ausführungsreihenfolge
 */
public record BorderSettings(
        double initialSize,
        double minimumSize,
        double maxEdgeSpeed,
        List<BorderPhaseConfig> phases) {

    public BorderSettings {
        if (initialSize <= 0) {
            throw new IllegalArgumentException("initialSize muss > 0 sein");
        }
        if (minimumSize < 0) {
            throw new IllegalArgumentException("minimumSize darf nicht negativ sein");
        }
        if (maxEdgeSpeed <= 0) {
            throw new IllegalArgumentException("maxEdgeSpeed muss > 0 sein");
        }
        phases = List.copyOf(phases);
    }

    /** @return die Ausgangsgröße der Phase mit dem gegebenen Index. */
    public double startSizeOf(int phaseIndex) {
        return phaseIndex == 0 ? initialSize : phases.get(phaseIndex - 1).targetSize();
    }

    /** @return Kopie mit ersetzter Phase. */
    public BorderSettings withPhase(int index, BorderPhaseConfig phase) {
        var copy = new java.util.ArrayList<>(phases);
        copy.set(index, phase);
        return new BorderSettings(initialSize, minimumSize, maxEdgeSpeed, copy);
    }
}
