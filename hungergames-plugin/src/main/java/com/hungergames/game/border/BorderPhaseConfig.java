package com.hungergames.game.border;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Eine konfigurierte Borderphase: wann sie startet, worauf sie schrumpft und
 * wie schnell.
 *
 * @param trigger    Auslöser (Zeit und/oder Spielerzahl)
 * @param targetSize Zielgröße (Durchmesser in Blöcken)
 * @param mode       wie Geschwindigkeit/Dauer bestimmt werden
 * @param duration   Schrumpfdauer ({@link Mode#DURATION}) bzw. angestrebte
 *                   Dauer als Zeitanker ({@link Mode#MAX_SPEED}, optional)
 * @param edgeSpeed  Kantengeschwindigkeit in Blöcken/Sekunde
 *                   (Modi {@link Mode#FIXED_SPEED} und {@link Mode#MAX_SPEED})
 */
public record BorderPhaseConfig(
        BorderTrigger trigger,
        double targetSize,
        Mode mode,
        Optional<Duration> duration,
        Optional<Double> edgeSpeed) {

    /** Wie die Schrumpfgeschwindigkeit einer Phase bestimmt wird. */
    public enum Mode {
        /** Feste Dauer; die Geschwindigkeit ergibt sich aus der Distanz. */
        DURATION,
        /** Feste Kantengeschwindigkeit; die Dauer ergibt sich aus der Distanz. */
        FIXED_SPEED,
        /**
         * Geschwindigkeit ist eine Obergrenze. Mit gesetzter {@code duration}
         * (Zeitanker, Syntax {@code prefer:}) wird die Geschwindigkeit aus der
         * angestrebten Dauer berechnet und nur bei Bedarf auf das Maximum
         * geklemmt; ohne Zeitanker wird mit dem Maximum geschrumpft.
         */
        MAX_SPEED
    }

    public BorderPhaseConfig {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(mode, "mode");
        if (targetSize <= 0) {
            throw new IllegalArgumentException("targetSize muss > 0 sein");
        }
        switch (mode) {
            case DURATION -> {
                if (duration.isEmpty()) {
                    throw new IllegalArgumentException("Modus DURATION braucht eine duration");
                }
            }
            case FIXED_SPEED, MAX_SPEED -> {
                if (edgeSpeed.isEmpty() || edgeSpeed.get() <= 0) {
                    throw new IllegalArgumentException("Modus " + mode + " braucht eine edgeSpeed > 0");
                }
            }
        }
    }

    public static BorderPhaseConfig ofDuration(BorderTrigger trigger, double targetSize, Duration duration) {
        return new BorderPhaseConfig(trigger, targetSize, Mode.DURATION, Optional.of(duration), Optional.empty());
    }

    public static BorderPhaseConfig ofFixedSpeed(BorderTrigger trigger, double targetSize, double edgeSpeed) {
        return new BorderPhaseConfig(trigger, targetSize, Mode.FIXED_SPEED, Optional.empty(), Optional.of(edgeSpeed));
    }

    public static BorderPhaseConfig ofMaxSpeed(BorderTrigger trigger, double targetSize, double edgeSpeed) {
        return new BorderPhaseConfig(trigger, targetSize, Mode.MAX_SPEED, Optional.empty(), Optional.of(edgeSpeed));
    }

    /** MAX_SPEED mit Zeitanker: angestrebte Dauer, Geschwindigkeit höchstens {@code edgeSpeed}. */
    public static BorderPhaseConfig ofMaxSpeed(BorderTrigger trigger, double targetSize,
                                               double edgeSpeed, Duration preferredDuration) {
        return new BorderPhaseConfig(trigger, targetSize, Mode.MAX_SPEED,
                Optional.of(preferredDuration), Optional.of(edgeSpeed));
    }

    public BorderPhaseConfig withTargetSize(double newTarget) {
        return new BorderPhaseConfig(trigger, newTarget, mode, duration, edgeSpeed);
    }

    public BorderPhaseConfig withDuration(Duration newDuration) {
        // Bei MAX_SPEED bleibt der Modus erhalten — die Dauer ist dort der Zeitanker
        Mode newMode = mode == Mode.MAX_SPEED ? Mode.MAX_SPEED : Mode.DURATION;
        return new BorderPhaseConfig(trigger, targetSize, newMode, Optional.of(newDuration), edgeSpeed);
    }

    public BorderPhaseConfig withTrigger(BorderTrigger newTrigger) {
        return new BorderPhaseConfig(newTrigger, targetSize, mode, duration, edgeSpeed);
    }

    public BorderPhaseConfig withMode(Mode newMode) {
        return new BorderPhaseConfig(trigger, targetSize, newMode, duration, edgeSpeed);
    }
}
