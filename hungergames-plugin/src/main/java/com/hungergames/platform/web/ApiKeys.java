package com.hungergames.platform.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** API-Schlüssel: Erzeugung und zeitkonstanter Vergleich. */
public final class ApiKeys {

    private static final char[] ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int KEY_LENGTH = 40;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeys() {
    }

    /** Erzeugt einen zufälligen 40-Zeichen-Schlüssel. */
    public static String generate() {
        StringBuilder key = new StringBuilder(KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            key.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return key.toString();
    }

    /**
     * Zeitkonstanter Vergleich — verhindert Timing-Seitenkanäle beim
     * Schlüsselabgleich. Leere konfigurierte Schlüssel matchen nie.
     */
    public static boolean matches(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
