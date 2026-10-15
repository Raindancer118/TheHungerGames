package com.hungergames.game.border;

import java.time.Duration;

/**
 * Eine konkrete Lösungsoption für einen {@link BorderConflict}, inklusive der
 * berechneten Auswirkungen (für die Anzeige in der GUI-Lore).
 *
 * <p>Die GUI zeigt pro Option ein Item; erst nach Bestätigung wird die Option
 * über {@link BorderMath#apply} angewendet.</p>
 */
public sealed interface BorderResolution {

    /** Dauer der Phase so verlängern, dass die Geschwindigkeit passt. */
    record AdjustDuration(Duration newDuration, double resultingSpeed) implements BorderResolution {
    }

    /** Zielgröße so anheben, dass Geschwindigkeit/Minimum eingehalten werden. */
    record AdjustTarget(double newTarget, double resultingSpeed) implements BorderResolution {
    }

    /** Startzeitpunkt der Phase vorziehen, damit sie in die Spielzeit passt. */
    record ShiftStart(Duration newStart) implements BorderResolution {
    }

    /** Die Gesamtspielzeit verlängern, damit die Phase hineinpasst. */
    record AdjustGameTime(Duration newGameDuration) implements BorderResolution {
    }

    /**
     * Geschwindigkeit nur als Maximum interpretieren
     * (Modus {@link BorderPhaseConfig.Mode#MAX_SPEED}): geschrumpft wird mit
     * der Fairness-Grenze, die Dauer verlängert sich entsprechend.
     */
    record UseSpeedAsMax(Duration effectiveDuration, double effectiveSpeed) implements BorderResolution {
    }

    /** Änderung verwerfen — die vorherige Konfiguration bleibt bestehen. */
    record Discard() implements BorderResolution {
    }
}
