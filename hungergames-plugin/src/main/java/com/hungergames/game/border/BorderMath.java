package com.hungergames.game.border;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reine Border-Mathematik: Geschwindigkeiten, Dauern, Konflikterkennung und
 * Lösungsoptionen.
 *
 * <p>Grundformel: Beim Schrumpfen von {@code start} auf {@code target} bewegt
 * sich jede Kante um die halbe Durchmesser-Differenz. Die
 * Kantengeschwindigkeit ist also {@code (start - target) / 2 / dauer}.</p>
 */
public final class BorderMath {

    private BorderMath() {
    }

    // ==================== Grundrechnung ====================

    /** @return Kantengeschwindigkeit in Blöcken/Sekunde. */
    public static double edgeSpeed(double startSize, double targetSize, double durationSeconds) {
        if (durationSeconds <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return (startSize - targetSize) / 2.0 / durationSeconds;
    }

    /** @return nötige Dauer, um mit gegebener Kantengeschwindigkeit zu schrumpfen. */
    public static Duration durationFor(double startSize, double targetSize, double edgeSpeed) {
        double seconds = (startSize - targetSize) / 2.0 / edgeSpeed;
        return Duration.ofMillis(Math.round(seconds * 1000));
    }

    /**
     * @return die Obergrenze einer MAX_SPEED-Phase: Minimum aus Phasenwert
     *         und globaler Fairness-Grenze
     */
    public static double speedCap(BorderSettings settings, BorderPhaseConfig phase) {
        return Math.min(phase.edgeSpeed().orElseThrow(), settings.maxEdgeSpeed());
    }

    /** @return die implizierte (tatsächlich gefahrene) Kantengeschwindigkeit einer Phase. */
    public static double impliedSpeed(BorderSettings settings, int phaseIndex) {
        BorderPhaseConfig phase = settings.phases().get(phaseIndex);
        double start = settings.startSizeOf(phaseIndex);
        return switch (phase.mode()) {
            case DURATION -> edgeSpeed(start, phase.targetSize(),
                    phase.duration().orElseThrow().toMillis() / 1000.0);
            case FIXED_SPEED -> phase.edgeSpeed().orElseThrow();
            // Mit Zeitanker: Wunschgeschwindigkeit aus der angestrebten Dauer,
            // geklemmt auf die Obergrenze. Ohne Anker: die Obergrenze selbst.
            case MAX_SPEED -> phase.duration()
                    .map(preferred -> Math.min(
                            edgeSpeed(start, phase.targetSize(), preferred.toMillis() / 1000.0),
                            speedCap(settings, phase)))
                    .orElseGet(() -> speedCap(settings, phase));
        };
    }

    /** @return die effektive Schrumpfdauer einer Phase. */
    public static Duration effectiveDuration(BorderSettings settings, int phaseIndex) {
        BorderPhaseConfig phase = settings.phases().get(phaseIndex);
        double start = settings.startSizeOf(phaseIndex);
        return switch (phase.mode()) {
            case DURATION -> phase.duration().orElseThrow();
            case FIXED_SPEED, MAX_SPEED -> durationFor(start, phase.targetSize(),
                    impliedSpeed(settings, phaseIndex));
        };
    }

    // ==================== Validierung ====================

    /**
     * Prüft die komplette Border-Konfiguration auf Widersprüche.
     *
     * @param gameDuration Gesamtspielzeit für Zeitbudget-Prüfungen, oder leer
     */
    public static List<BorderConflict> validate(BorderSettings settings, Optional<Duration> gameDuration) {
        List<BorderConflict> conflicts = new ArrayList<>();
        Duration lastTriggerTime = null;

        for (int i = 0; i < settings.phases().size(); i++) {
            BorderPhaseConfig phase = settings.phases().get(i);
            double start = settings.startSizeOf(i);
            double target = phase.targetSize();

            if (target >= start) {
                conflicts.add(new BorderConflict(i, BorderConflict.Type.TARGET_NOT_SHRINKING, 0, start));
                continue; // Folgeprüfungen dieser Phase wären sinnlos
            }

            if (target < settings.minimumSize()) {
                conflicts.add(new BorderConflict(i, BorderConflict.Type.TARGET_BELOW_MINIMUM,
                        impliedSpeed(settings, i), settings.minimumSize()));
            }

            if (phase.mode() == BorderPhaseConfig.Mode.MAX_SPEED) {
                // Mit Zeitanker prüfbar: Ist der Anker mit der Obergrenze
                // erreichbar? Ohne Anker gibt es keinen Zeitrahmen → kein Konflikt.
                if (phase.duration().isPresent()) {
                    double needed = edgeSpeed(start, target,
                            phase.duration().get().toMillis() / 1000.0);
                    double cap = speedCap(settings, phase);
                    if (needed > cap) {
                        conflicts.add(new BorderConflict(i,
                                BorderConflict.Type.SPEED_EXCEEDS_MAX, needed, cap));
                    }
                }
            } else {
                double speed = impliedSpeed(settings, i);
                if (speed > settings.maxEdgeSpeed()) {
                    conflicts.add(new BorderConflict(i, BorderConflict.Type.SPEED_EXCEEDS_MAX,
                            speed, settings.maxEdgeSpeed()));
                }
            }

            if (phase.trigger().time().isPresent()) {
                Duration triggerTime = phase.trigger().time().get();

                if (lastTriggerTime != null && triggerTime.compareTo(lastTriggerTime) < 0) {
                    conflicts.add(new BorderConflict(i, BorderConflict.Type.PHASES_OUT_OF_ORDER,
                            0, lastTriggerTime.toSeconds()));
                }
                lastTriggerTime = triggerTime;

                if (gameDuration.isPresent()) {
                    Duration end = triggerTime.plus(effectiveDuration(settings, i));
                    if (end.compareTo(gameDuration.get()) > 0) {
                        conflicts.add(new BorderConflict(i, BorderConflict.Type.EXCEEDS_GAME_TIME,
                                impliedSpeed(settings, i), gameDuration.get().toSeconds()));
                    }
                }
            }
        }
        return conflicts;
    }

    // ==================== Lösungsoptionen ====================

    /**
     * Liefert die konkreten Lösungsoptionen für einen Konflikt, jeweils mit
     * vorberechneten Auswirkungen für die GUI-Anzeige. {@code Discard} ist
     * immer die letzte Option.
     */
    public static List<BorderResolution> resolutions(BorderSettings settings, BorderConflict conflict,
                                                     Optional<Duration> gameDuration) {
        BorderPhaseConfig phase = settings.phases().get(conflict.phaseIndex());
        double start = settings.startSizeOf(conflict.phaseIndex());
        double target = phase.targetSize();
        // Maßgebliches Limit: bei MAX_SPEED das kleinere aus Phasen-Cap und
        // globaler Grenze — sonst lösen die Optionen den Konflikt nicht auf.
        double maxSpeed = phase.mode() == BorderPhaseConfig.Mode.MAX_SPEED
                ? speedCap(settings, phase)
                : settings.maxEdgeSpeed();
        List<BorderResolution> options = new ArrayList<>();

        switch (conflict.type()) {
            case SPEED_EXCEEDS_MAX -> {
                Duration slowEnough = durationFor(start, target, maxSpeed);
                options.add(new BorderResolution.AdjustDuration(slowEnough, maxSpeed));

                phase.duration().ifPresent(d -> {
                    double reachable = start - 2 * maxSpeed * (d.toMillis() / 1000.0);
                    if (reachable > 0 && reachable >= settings.minimumSize()) {
                        options.add(new BorderResolution.AdjustTarget(reachable, maxSpeed));
                    }
                });

                options.add(new BorderResolution.UseSpeedAsMax(slowEnough, maxSpeed));
            }
            case TARGET_BELOW_MINIMUM -> {
                double speedAtMinimum = switch (phase.mode()) {
                    case DURATION -> edgeSpeed(start, settings.minimumSize(),
                            phase.duration().orElseThrow().toMillis() / 1000.0);
                    case FIXED_SPEED, MAX_SPEED -> impliedSpeed(settings, conflict.phaseIndex());
                };
                options.add(new BorderResolution.AdjustTarget(settings.minimumSize(), speedAtMinimum));
            }
            case TARGET_NOT_SHRINKING -> {
                double proposal = Math.max(settings.minimumSize(), start / 2.0);
                if (proposal < start) {
                    options.add(new BorderResolution.AdjustTarget(proposal, maxSpeed));
                }
            }
            case EXCEEDS_GAME_TIME -> {
                Duration effDuration = effectiveDuration(settings, conflict.phaseIndex());
                Duration triggerTime = phase.trigger().time().orElse(Duration.ZERO);

                gameDuration.ifPresent(game -> {
                    Duration newStart = game.minus(effDuration);
                    if (!newStart.isNegative()) {
                        options.add(new BorderResolution.ShiftStart(newStart));
                    }
                    Duration remaining = game.minus(triggerTime);
                    if (remaining.isPositive()) {
                        options.add(new BorderResolution.AdjustDuration(remaining,
                                edgeSpeed(start, target, remaining.toMillis() / 1000.0)));
                    }
                });
                options.add(new BorderResolution.AdjustGameTime(triggerTime.plus(effDuration)));
            }
            case PHASES_OUT_OF_ORDER -> {
                if (conflict.phaseIndex() > 0) {
                    int prev = conflict.phaseIndex() - 1;
                    settings.phases().get(prev).trigger().time().ifPresent(prevTime ->
                            options.add(new BorderResolution.ShiftStart(
                                    prevTime.plus(effectiveDuration(settings, prev)))));
                }
            }
        }

        options.add(new BorderResolution.Discard());
        return options;
    }

    // ==================== Anwendung ====================

    /**
     * Ergebnis von {@link #apply}: die neue Border-Konfiguration und — nur bei
     * {@link BorderResolution.AdjustGameTime} — die neue Gesamtspielzeit.
     */
    public record ApplyResult(BorderSettings settings, Optional<Duration> newGameDuration) {
    }

    /** Wendet eine bestätigte Lösungsoption an (erzeugt neue, unveränderte Objekte). */
    public static ApplyResult apply(BorderSettings settings, int phaseIndex, BorderResolution resolution) {
        BorderPhaseConfig phase = settings.phases().get(phaseIndex);

        return switch (resolution) {
            case BorderResolution.AdjustDuration r -> new ApplyResult(
                    settings.withPhase(phaseIndex, phase.withDuration(r.newDuration())),
                    Optional.empty());
            case BorderResolution.AdjustTarget r -> new ApplyResult(
                    settings.withPhase(phaseIndex, phase.withTargetSize(r.newTarget())),
                    Optional.empty());
            case BorderResolution.ShiftStart r -> new ApplyResult(
                    settings.withPhase(phaseIndex, phase.withTrigger(new BorderTrigger(
                            Optional.of(r.newStart()), phase.trigger().aliveBelow()))),
                    Optional.empty());
            case BorderResolution.AdjustGameTime r -> new ApplyResult(
                    settings, Optional.of(r.newGameDuration()));
            case BorderResolution.UseSpeedAsMax r -> new ApplyResult(
                    settings.withPhase(phaseIndex, BorderPhaseConfig.ofMaxSpeed(
                            phase.trigger(), phase.targetSize(),
                            phase.edgeSpeed().orElse(settings.maxEdgeSpeed()))),
                    Optional.empty());
            case BorderResolution.Discard r -> new ApplyResult(settings, Optional.empty());
        };
    }
}
