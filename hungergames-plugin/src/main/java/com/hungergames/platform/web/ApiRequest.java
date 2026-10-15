package com.hungergames.platform.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Eine geparste API-Anfrage: Methode, Pfad, Pfadparameter (aus dem
 * Routen-Muster), Query-Parameter und JSON-Body.
 *
 * <p>Die {@code require*}-Helfer werfen {@link ApiBadRequestException}, wenn
 * ein Feld fehlt oder nicht parsebar ist — damit bleiben Handler frei von
 * Validierungs-Verzweigungen. Bukkit-frei und damit unit-testbar.</p>
 */
public final class ApiRequest {

    private final String method;
    private final String path;
    private final Map<String, String> pathParams;
    private final Map<String, String> query;
    private final JsonObject body;

    public ApiRequest(String method, String path, Map<String, String> pathParams,
                      Map<String, String> query, JsonObject body) {
        this.method = method;
        this.path = path;
        this.pathParams = Map.copyOf(pathParams);
        this.query = Map.copyOf(query);
        this.body = body;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    /** @return der Pfadparameter aus dem Routen-Muster (z. B. {@code {id}}). */
    public String param(String name) {
        String value = pathParams.get(name);
        if (value == null) {
            throw new IllegalStateException("Route kennt keinen Pfadparameter \"" + name + "\"");
        }
        return value;
    }

    /** @return der JSON-Body, oder {@code null}, wenn keiner geschickt wurde. */
    public JsonObject body() {
        return body;
    }

    public boolean has(String field) {
        return body != null && body.has(field) && !body.get(field).isJsonNull();
    }

    // ==================== Body-Felder ====================

    public String requireString(String field) {
        String value = optString(field, null);
        if (value == null || value.isBlank()) {
            throw new ApiBadRequestException("Feld \"" + field + "\" fehlt im JSON-Body");
        }
        return value;
    }

    public String optString(String field, String fallback) {
        if (!has(field)) {
            return fallback;
        }
        return body.get(field).getAsString().trim();
    }

    public int requireInt(String field) {
        return asInt(field, require(field));
    }

    public int optInt(String field, int fallback) {
        return has(field) ? asInt(field, body.get(field)) : fallback;
    }

    public double requireDouble(String field) {
        return asDouble(field, require(field));
    }

    public double optDouble(String field, double fallback) {
        return has(field) ? asDouble(field, body.get(field)) : fallback;
    }

    public boolean optBool(String field, boolean fallback) {
        if (!has(field)) {
            return fallback;
        }
        JsonElement element = body.get(field);
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            throw new ApiBadRequestException("Feld \"" + field + "\" muss true oder false sein");
        }
    }

    public UUID requireUuid(String field) {
        return parseUuid(requireString(field));
    }

    public Optional<UUID> optUuid(String field) {
        return has(field) ? Optional.of(requireUuid(field)) : Optional.empty();
    }

    /** Enum-Wert aus dem Body, Groß-/Kleinschreibung egal. */
    public <E extends Enum<E>> E requireEnum(String field, Class<E> type) {
        return parseEnum(field, requireString(field), type);
    }

    public <E extends Enum<E>> E optEnum(String field, Class<E> type, E fallback) {
        return has(field) ? parseEnum(field, requireString(field), type) : fallback;
    }

    // ==================== Query-Parameter ====================

    public String queryString(String name, String fallback) {
        String value = query.get(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    public int queryInt(String name, int fallback) {
        String raw = query.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiBadRequestException("Query-Parameter \"" + name + "\" ist keine Zahl: " + raw);
        }
    }

    public boolean queryBool(String name, boolean fallback) {
        String raw = query.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "ja" -> true;
            case "false", "0", "no", "nein" -> false;
            default -> throw new ApiBadRequestException(
                    "Query-Parameter \"" + name + "\" muss true oder false sein");
        };
    }

    // ==================== Statische Helfer ====================

    /** Parst eine UUID; wirft {@link ApiBadRequestException} bei ungültigem Format. */
    public static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiBadRequestException("\"" + raw + "\" ist keine gültige UUID");
        }
    }

    /** Zerlegt {@code a=1&b=2} in eine Map; leere Eingabe ergibt eine leere Map. */
    public static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            result.put(decode(key), decode(value));
        }
        return result;
    }

    private static String decode(String raw) {
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    // ==================== intern ====================

    private JsonElement require(String field) {
        if (!has(field)) {
            throw new ApiBadRequestException("Feld \"" + field + "\" fehlt im JSON-Body");
        }
        return body.get(field);
    }

    private static int asInt(String field, JsonElement element) {
        try {
            return Integer.parseInt(element.getAsString().trim());
        } catch (RuntimeException e) {
            throw new ApiBadRequestException("Feld \"" + field + "\" ist keine ganze Zahl");
        }
    }

    private static double asDouble(String field, JsonElement element) {
        try {
            return Double.parseDouble(element.getAsString().trim());
        } catch (RuntimeException e) {
            throw new ApiBadRequestException("Feld \"" + field + "\" ist keine Zahl");
        }
    }

    private static <E extends Enum<E>> E parseEnum(String field, String raw, Class<E> type) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            StringBuilder allowed = new StringBuilder();
            for (E constant : type.getEnumConstants()) {
                allowed.append(allowed.isEmpty() ? "" : ", ").append(constant.name());
            }
            throw new ApiBadRequestException("Feld \"" + field + "\": \"" + raw
                    + "\" ist unbekannt (erlaubt: " + allowed + ")");
        }
    }
}
