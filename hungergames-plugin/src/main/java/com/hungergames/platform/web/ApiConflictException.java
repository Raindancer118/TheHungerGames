package com.hungergames.platform.web;

/**
 * Signalisiert, dass eine Aktion im aktuellen Zustand nicht möglich ist
 * (HTTP 409) — etwa ein angeforderter Spieler ist offline oder die Phase
 * lässt die Aktion nicht zu. Wird von den Auflöse-Helfern in
 * {@link ApiSupport} geworfen und von {@link HttpApiService} in eine
 * {@code {"error": ...}}-Antwort übersetzt.
 */
public class ApiConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ApiConflictException(String message) {
        super(message);
    }
}
