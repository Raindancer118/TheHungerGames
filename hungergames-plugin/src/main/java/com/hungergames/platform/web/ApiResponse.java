package com.hungergames.platform.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.function.Consumer;

/**
 * Antwort eines API-Handlers: HTTP-Status plus JSON-Körper. Handler geben
 * dieses Objekt zurück statt selbst auf den {@code HttpExchange} zu schreiben —
 * dadurch sind sie ohne HTTP-Server testbar und der Transport liegt allein in
 * {@link HttpApiService}.
 */
public record ApiResponse(int status, JsonObject body) {

    /** {@code 200 {"ok": true}} */
    public static ApiResponse ok() {
        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        return new ApiResponse(200, json);
    }

    /** {@code 200 {"ok": true, ...}} mit zusätzlichen Feldern. */
    public static ApiResponse ok(Consumer<JsonObject> filler) {
        ApiResponse response = ok();
        filler.accept(response.body());
        return response;
    }

    /** {@code 200} mit einem beliebigen Objekt als Körper (Lese-Endpunkte). */
    public static ApiResponse json(JsonObject body) {
        return new ApiResponse(200, body);
    }

    /** {@code 200 {"<field>": [...]}} */
    public static ApiResponse json(String field, JsonArray array) {
        JsonObject json = new JsonObject();
        json.add(field, array);
        return new ApiResponse(200, json);
    }

    public static ApiResponse error(int status, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("error", message);
        return new ApiResponse(status, json);
    }

    /** {@code 400} — fehlerhafte Eingabe. */
    public static ApiResponse badRequest(String message) {
        return error(400, message);
    }

    /** {@code 404} — unbekannte Ressource. */
    public static ApiResponse notFound(String message) {
        return error(404, message);
    }

    /**
     * {@code 409} — die Aktion ist im aktuellen Zustand nicht möglich
     * (Phase gesperrt, Team voll, Spieler offline …).
     */
    public static ApiResponse conflict(String message) {
        return error(409, message);
    }

    /**
     * {@link #ok()} bei {@code null}, sonst {@link #conflict(String)} — für
     * Services, die eine Fehlermeldung oder {@code null}/leer zurückgeben.
     */
    public static ApiResponse okOrConflict(String errorOrNull) {
        return errorOrNull == null ? ok() : conflict(errorOrNull);
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
