package com.hungergames.platform.border;

import com.hungergames.game.border.BorderPhaseConfig;
import com.hungergames.game.border.BorderTrigger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parst die kompakte Borderphasen-Syntax aus der Konfiguration:
 *
 * <pre>{@code <trigger> -> <zielgröße> @ <modus>}</pre>
 *
 * Trigger: {@code 20m} (Spielzeit), {@code 50%} (Anteil der Spielzeit),
 * {@code alive<4} oder ODER-Kombination {@code 20m|alive<4}.
 * Modus: {@code duration:10m}, {@code speed:1.5} (fix) oder {@code max:2.5}
 * (Geschwindigkeit als Obergrenze).
 */
public final class BorderPhaseParser {

    private static final Pattern PHASE = Pattern.compile(
            "^(?<trigger>[^-]+?)\\s*->\\s*(?<target>\\d+(?:\\.\\d+)?)\\s*@\\s*(?<mode>duration|speed|max)\\s*:\\s*(?<value>[\\w.]+)"
                    + "(?:\\s*,\\s*prefer\\s*:\\s*(?<prefer>[\\w.]+))?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIVE = Pattern.compile("^alive\\s*<\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERCENT = Pattern.compile("^(\\d+(?:\\.\\d+)?)%$");

    private BorderPhaseParser() {
    }

    /**
     * Parst eine Phasenzeile.
     *
     * @param gameDuration Spielzeit zur Auflösung von Prozent-Triggern
     * @throws IllegalArgumentException bei Syntaxfehlern
     */
    public static BorderPhaseConfig parse(String line, Duration gameDuration) {
        Matcher matcher = PHASE.matcher(line.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Ungültige Phasensyntax: \"" + line
                    + "\" (erwartet: '<trigger> -> <größe> @ <modus>:<wert>')");
        }

        BorderTrigger trigger = parseTrigger(matcher.group("trigger").trim(), gameDuration);
        double target = Double.parseDouble(matcher.group("target"));
        String mode = matcher.group("mode").toLowerCase(Locale.ROOT);
        String value = matcher.group("value");
        String prefer = matcher.group("prefer");

        if (prefer != null && !mode.equals("max")) {
            throw new IllegalArgumentException(
                    "'prefer:' ist nur im max-Modus erlaubt (Zeile: \"" + line + "\")");
        }

        return switch (mode) {
            case "duration" -> BorderPhaseConfig.ofDuration(trigger, target, parseDuration(value));
            case "speed" -> BorderPhaseConfig.ofFixedSpeed(trigger, target, Double.parseDouble(value));
            case "max" -> prefer != null
                    ? BorderPhaseConfig.ofMaxSpeed(trigger, target, Double.parseDouble(value),
                            parseDuration(prefer))
                    : BorderPhaseConfig.ofMaxSpeed(trigger, target, Double.parseDouble(value));
            default -> throw new IllegalArgumentException("Unbekannter Modus: " + mode);
        };
    }

    /** Parst alle Zeilen. */
    public static List<BorderPhaseConfig> parseAll(List<String> lines, Duration gameDuration) {
        List<BorderPhaseConfig> phases = new ArrayList<>();
        for (String line : lines) {
            phases.add(parse(line, gameDuration));
        }
        return phases;
    }

    /** Validator für das Setting: prüft die reine Syntax jeder Zeile. */
    public static Optional<String> validateList(List<String> lines) {
        for (String line : lines) {
            try {
                parse(line, Duration.ofMinutes(180));
            } catch (IllegalArgumentException e) {
                return Optional.of(e.getMessage());
            }
        }
        return Optional.empty();
    }

    private static BorderTrigger parseTrigger(String input, Duration gameDuration) {
        Optional<Duration> time = Optional.empty();
        Optional<Integer> aliveBelow = Optional.empty();

        for (String part : input.split("\\|")) {
            String trimmed = part.trim();
            Matcher alive = ALIVE.matcher(trimmed);
            Matcher percent = PERCENT.matcher(trimmed);
            if (alive.matches()) {
                aliveBelow = Optional.of(Integer.parseInt(alive.group(1)));
            } else if (percent.matches()) {
                double fraction = Double.parseDouble(percent.group(1)) / 100.0;
                time = Optional.of(Duration.ofMillis((long) (gameDuration.toMillis() * fraction)));
            } else {
                time = Optional.of(parseDuration(trimmed));
            }
        }
        return new BorderTrigger(time, aliveBelow);
    }

    private static Duration parseDuration(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile("(\\d+)([hms])").matcher(s);
        Duration result = Duration.ZERO;
        int matched = 0;
        while (m.find()) {
            long amount = Long.parseLong(m.group(1));
            result = switch (m.group(2)) {
                case "h" -> result.plusHours(amount);
                case "m" -> result.plusMinutes(amount);
                default -> result.plusSeconds(amount);
            };
            matched += m.group().length();
        }
        if (matched != s.length() || matched == 0) {
            throw new IllegalArgumentException("Ungültige Dauer: \"" + input + "\" (z. B. 90s, 15m, 1h30m)");
        }
        return result;
    }
}
