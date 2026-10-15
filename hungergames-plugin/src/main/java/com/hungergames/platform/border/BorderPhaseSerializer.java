package com.hungergames.platform.border;

import com.hungergames.game.border.BorderPhaseConfig;
import com.hungergames.game.border.BorderSettings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Serialisiert {@link BorderSettings}-Phasen zurück in die kompakte
 * Konfigurations-Syntax des {@link BorderPhaseParser}.
 */
public final class BorderPhaseSerializer {

    private BorderPhaseSerializer() {
    }

    public static List<String> serialize(BorderSettings settings) {
        List<String> lines = new ArrayList<>();
        for (BorderPhaseConfig phase : settings.phases()) {
            lines.add(serializePhase(phase));
        }
        return lines;
    }

    private static String serializePhase(BorderPhaseConfig phase) {
        StringBuilder trigger = new StringBuilder();
        phase.trigger().time().ifPresent(time -> trigger.append(formatDuration(time)));
        phase.trigger().aliveBelow().ifPresent(alive -> {
            if (!trigger.isEmpty()) {
                trigger.append("|");
            }
            trigger.append("alive<").append(alive);
        });

        String mode = switch (phase.mode()) {
            case DURATION -> "duration:" + formatDuration(phase.duration().orElseThrow());
            case FIXED_SPEED -> "speed:" + formatDouble(phase.edgeSpeed().orElseThrow());
            case MAX_SPEED -> "max:" + formatDouble(phase.edgeSpeed().orElseThrow())
                    + phase.duration().map(prefer -> ",prefer:" + formatDuration(prefer)).orElse("");
        };

        return trigger + " -> " + formatDouble(phase.targetSize()) + " @ " + mode;
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds % 3600 == 0 && seconds > 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds % 60 == 0 && seconds > 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    private static String formatDouble(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
