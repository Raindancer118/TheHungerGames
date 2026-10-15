package com.hungergames.platform.web;

/**
 * Signalisiert eine fehlerhafte Anfrage (HTTP 400). Wird von den Lese-Helfern
 * in {@link ApiRequest} geworfen, damit Handler Parameter direkt entpacken
 * können, ohne jede Prüfung selbst zu verzweigen; {@link HttpApiService}
 * übersetzt sie in eine {@code {"error": ...}}-Antwort.
 */
public class ApiBadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ApiBadRequestException(String message) {
        super(message);
    }
}
