package com.hungergames.game.sponsor;

import java.time.Duration;

/**
 * Pure Logik der zeitbasierten Sponsor-Token-Vergabe: Wie viele
 * Vergabe-Wellen sind bei gegebener Spielzeit fällig?
 *
 * <p>Welle 1 fällt auf {@code firstAfter}, jede weitere im Abstand
 * {@code interval}. Der Aufrufer merkt sich pro Spieler, wie viele Wellen er
 * bereits erhalten hat — Rejoin und Server-Neustart holen fehlende Wellen
 * nach, ohne doppelt zu vergeben.</p>
 */
public final class TokenSchedule {

    private TokenSchedule() {
    }

    /**
     * @param elapsed    aktuelle (virtuelle) Spielzeit
     * @param firstAfter Spielzeit bis zur ersten Welle
     * @param interval   Abstand zwischen den Wellen (&gt; 0)
     * @return Anzahl der bis jetzt fälligen Vergabe-Wellen
     */
    public static int dueWaves(Duration elapsed, Duration firstAfter, Duration interval) {
        if (elapsed.compareTo(firstAfter) < 0) {
            return 0;
        }
        if (interval.isZero() || interval.isNegative()) {
            return 1;
        }
        long sinceFirst = elapsed.minus(firstAfter).toMillis();
        return 1 + (int) (sinceFirst / interval.toMillis());
    }

    /**
     * Tokens, die einem Spieler noch zustehen — respektiert das optionale
     * Maximum verdienter Tokens pro Spieler.
     *
     * @param dueWaves          fällige Wellen laut {@link #dueWaves}
     * @param receivedWaves     bereits erhaltene Wellen des Spielers
     * @param amountPerInterval Tokens pro Welle
     * @param earnedTotal       bisher insgesamt verdiente Tokens des Spielers
     * @param maxPerPlayer      Obergrenze verdienter Tokens (0 = unbegrenzt)
     */
    public static int pendingTokens(int dueWaves, int receivedWaves, int amountPerInterval,
                                    int earnedTotal, int maxPerPlayer) {
        int missingWaves = Math.max(0, dueWaves - receivedWaves);
        int amount = missingWaves * amountPerInterval;
        if (maxPerPlayer > 0) {
            amount = Math.min(amount, Math.max(0, maxPerPlayer - earnedTotal));
        }
        return amount;
    }
}
