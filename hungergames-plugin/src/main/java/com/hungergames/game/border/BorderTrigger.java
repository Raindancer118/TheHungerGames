package com.hungergames.game.border;

import java.time.Duration;
import java.util.Optional;

/**
 * Auslöser einer Borderphase. Mindestens eine Bedingung muss gesetzt sein;
 * sind beide gesetzt, gilt ODER-Semantik (was zuerst eintritt).
 *
 * @param time       Spielzeit seit RUNNING-Beginn (virtuelle Zeit), oder leer
 * @param aliveBelow löst aus, sobald weniger als so viele Teilnehmer leben, oder leer
 */
public record BorderTrigger(Optional<Duration> time, Optional<Integer> aliveBelow) {

    public BorderTrigger {
        if (time.isEmpty() && aliveBelow.isEmpty()) {
            throw new IllegalArgumentException("BorderTrigger braucht mindestens eine Bedingung (time oder aliveBelow)");
        }
    }

    public static BorderTrigger atTime(Duration time) {
        return new BorderTrigger(Optional.of(time), Optional.empty());
    }

    public static BorderTrigger aliveBelow(int count) {
        return new BorderTrigger(Optional.empty(), Optional.of(count));
    }

    public static BorderTrigger either(Duration time, int aliveBelow) {
        return new BorderTrigger(Optional.of(time), Optional.of(aliveBelow));
    }

    /** @return {@code true}, sobald eine der Bedingungen erfüllt ist. */
    public boolean isTriggered(Duration elapsed, int aliveCount) {
        boolean byTime = time.map(t -> elapsed.compareTo(t) >= 0).orElse(false);
        boolean byAlive = aliveBelow.map(threshold -> aliveCount < threshold).orElse(false);
        return byTime || byAlive;
    }
}
