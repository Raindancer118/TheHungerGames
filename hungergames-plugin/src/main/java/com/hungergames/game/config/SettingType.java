package com.hungergames.game.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Typisierung einer Einstellung: parsen (GUI-/Datei-Eingabe) und
 * serialisieren (Persistenz, Anzeige).
 */
public interface SettingType<T> {

    /** @throws IllegalArgumentException bei nicht parsebarer Eingabe */
    T parse(String input);

    String serialize(T value);

    Class<T> valueClass();

    SettingType<Integer> INT = simple(Integer.class, Integer::parseInt, String::valueOf);
    SettingType<Double> DOUBLE = simple(Double.class, Double::parseDouble, String::valueOf);
    SettingType<Boolean> BOOLEAN = simple(Boolean.class, input -> switch (input.toLowerCase(Locale.ROOT)) {
        case "true", "on", "yes", "an", "ja" -> true;
        case "false", "off", "no", "aus", "nein" -> false;
        default -> throw new IllegalArgumentException("Kein Wahrheitswert: " + input);
    }, String::valueOf);
    SettingType<String> STRING = simple(String.class, s -> s, s -> s);

    /**
     * Dauer in menschenlesbarer Form: {@code 90s}, {@code 15m}, {@code 2h}
     * oder Kombinationen wie {@code 1h30m}.
     */
    SettingType<Duration> DURATION = simple(Duration.class, SettingType::parseDuration, d -> {
        long seconds = d.toSeconds();
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    });

    @SuppressWarnings("unchecked")
    SettingType<List<String>> STRING_LIST = (SettingType<List<String>>) (SettingType<?>) simple(
            (Class<List<String>>) (Class<?>) List.class,
            input -> input.isBlank() ? List.<String>of()
                    : List.of(input.split("\\s*,\\s*")),
            list -> String.join(", ", list));

    static <E extends Enum<E>> SettingType<E> ofEnum(Class<E> enumClass) {
        return simple(enumClass,
                input -> Enum.valueOf(enumClass, input.trim().toUpperCase(Locale.ROOT)),
                Enum::name);
    }

    private static Duration parseDuration(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.matches("\\d+")) {
            return Duration.ofSeconds(Long.parseLong(s)); // nackte Zahl = Sekunden
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([hms])").matcher(s);
        Duration result = Duration.ZERO;
        int matchedLength = 0;
        while (m.find()) {
            long amount = Long.parseLong(m.group(1));
            result = switch (m.group(2)) {
                case "h" -> result.plusHours(amount);
                case "m" -> result.plusMinutes(amount);
                default -> result.plusSeconds(amount);
            };
            matchedLength += m.group().length();
        }
        if (matchedLength != s.length() || result.isZero() && !s.startsWith("0")) {
            if (matchedLength != s.length()) {
                throw new IllegalArgumentException("Keine gültige Dauer: " + input);
            }
        }
        return result;
    }

    private static <T> SettingType<T> simple(Class<T> clazz,
                                             java.util.function.Function<String, T> parser,
                                             java.util.function.Function<T, String> serializer) {
        return new SettingType<>() {
            @Override
            public T parse(String input) {
                return parser.apply(input);
            }

            @Override
            public String serialize(T value) {
                return serializer.apply(value);
            }

            @Override
            public Class<T> valueClass() {
                return clazz;
            }
        };
    }
}
