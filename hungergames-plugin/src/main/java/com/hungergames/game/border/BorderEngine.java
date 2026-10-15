package com.hungergames.game.border;

import java.time.Duration;
import java.util.Optional;

/**
 * Laufzeit-Engine der Worldborder: entscheidet pro Tick, ob die nächste
 * konfigurierte Phase auslöst, und berechnet den auszuführenden
 * Schrumpfbefehl. Das Anwenden auf die Bukkit-{@code WorldBorder} übernimmt
 * der Plattform-Adapter.
 *
 * <p>Bukkit-frei und deterministisch testbar; die Zeit kommt als virtuelle
 * Spielzeit herein (respektiert {@code /speedup}).</p>
 */
public final class BorderEngine {

    /**
     * Ein auszuführender Schrumpfvorgang.
     *
     * @param targetSize      Zieldurchmesser in Blöcken
     * @param duration        Dauer des Schrumpfens
     * @param effectiveSpeed  resultierende Kantengeschwindigkeit (Blöcke/s)
     */
    public record ShrinkCommand(double targetSize, Duration duration, double effectiveSpeed) {
    }

    private final BorderSettings settings;
    private int nextPhaseIndex;

    public BorderEngine(BorderSettings settings) {
        this(settings, 0);
    }

    /** @param nextPhaseIndex Wiederaufsetzpunkt nach Session-Restore */
    public BorderEngine(BorderSettings settings, int nextPhaseIndex) {
        this.settings = settings;
        this.nextPhaseIndex = nextPhaseIndex;
    }

    /**
     * Prüft, ob die nächste Phase auslöst.
     *
     * @param elapsed     virtuelle Spielzeit seit RUNNING-Beginn
     * @param aliveCount  Anzahl lebender Teilnehmer
     * @param currentSize aktueller Border-Durchmesser
     * @return der Schrumpfbefehl, falls eine Phase startet
     */
    public Optional<ShrinkCommand> tick(Duration elapsed, int aliveCount, double currentSize) {
        if (isFinished()) {
            return Optional.empty();
        }

        BorderPhaseConfig phase = settings.phases().get(nextPhaseIndex);
        if (!phase.trigger().isTriggered(elapsed, aliveCount)) {
            return Optional.empty();
        }
        nextPhaseIndex++;

        // Mindestgröße hart durchsetzen; Ausgangsgröße ist der Ist-Zustand.
        double target = Math.max(phase.targetSize(), settings.minimumSize());
        if (target >= currentSize) {
            return Optional.empty(); // nichts zu schrumpfen
        }

        double speed;
        Duration duration;
        switch (phase.mode()) {
            case DURATION -> {
                duration = phase.duration().orElseThrow();
                speed = BorderMath.edgeSpeed(currentSize, target, duration.toMillis() / 1000.0);
            }
            case FIXED_SPEED -> {
                speed = phase.edgeSpeed().orElseThrow();
                duration = BorderMath.durationFor(currentSize, target, speed);
            }
            case MAX_SPEED -> {
                double cap = Math.min(phase.edgeSpeed().orElseThrow(), settings.maxEdgeSpeed());
                // Zeitanker: Geschwindigkeit aus der angestrebten Dauer,
                // geklemmt auf die Obergrenze; ohne Anker die Obergrenze selbst.
                speed = phase.duration()
                        .map(preferred -> Math.min(cap, BorderMath.edgeSpeed(
                                currentSize, target, preferred.toMillis() / 1000.0)))
                        .orElse(cap);
                duration = BorderMath.durationFor(currentSize, target, speed);
            }
            default -> throw new IllegalStateException("Unbekannter Modus: " + phase.mode());
        }

        return Optional.of(new ShrinkCommand(target, duration, speed));
    }

    /** @return Index der nächsten (noch nicht ausgelösten) Phase — für die Persistenz. */
    public int nextPhaseIndex() {
        return nextPhaseIndex;
    }

    public boolean isFinished() {
        return nextPhaseIndex >= settings.phases().size();
    }

    public BorderSettings settings() {
        return settings;
    }
}
