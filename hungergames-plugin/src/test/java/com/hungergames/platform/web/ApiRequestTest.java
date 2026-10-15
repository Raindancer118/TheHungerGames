package com.hungergames.platform.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hungergames.api.model.TeamColor;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRequestTest {

    private static ApiRequest withBody(String json) {
        JsonObject body = json == null ? null : JsonParser.parseString(json).getAsJsonObject();
        return new ApiRequest("POST", "/api/test", Map.of(), Map.of(), body);
    }

    @Test
    void liestFelderTypisiert() {
        ApiRequest request = withBody("""
                {"name": " Distrikt 12 ", "count": 24, "speed": 2.5, "flag": true}""");

        assertEquals("Distrikt 12", request.requireString("name"));
        assertEquals(24, request.requireInt("count"));
        assertEquals(2.5, request.requireDouble("speed"));
        assertTrue(request.optBool("flag", false));
        assertEquals(7, request.optInt("fehlt", 7));
    }

    @Test
    void akzeptiertZahlenAlsText() {
        assertEquals(12, withBody("{\"count\": \"12\"}").requireInt("count"));
        assertEquals(1.5, withBody("{\"size\": \"1.5\"}").requireDouble("size"));
    }

    @Test
    void meldetFehlendeUndUngueltigeFelder() {
        ApiRequest empty = withBody(null);
        assertThrows(ApiBadRequestException.class, () -> empty.requireString("name"));
        assertFalse(empty.has("name"));

        ApiRequest wrong = withBody("{\"count\": \"zwölf\", \"uuid\": \"keine-uuid\"}");
        assertThrows(ApiBadRequestException.class, () -> wrong.requireInt("count"));
        assertThrows(ApiBadRequestException.class, () -> wrong.requireUuid("uuid"));
    }

    @Test
    void behandeltJsonNullWieFehlend() {
        ApiRequest request = withBody("{\"name\": null}");

        assertFalse(request.has("name"));
        assertEquals("fallback", request.optString("name", "fallback"));
    }

    @Test
    void parstUuidUndEnum() {
        UUID uuid = UUID.randomUUID();
        ApiRequest request = withBody("{\"uuid\": \"" + uuid + "\", \"color\": \"light_blue\"}");

        assertEquals(uuid, request.requireUuid("uuid"));
        assertEquals(TeamColor.LIGHT_BLUE, request.requireEnum("color", TeamColor.class));
        assertEquals(TeamColor.RED, request.optEnum("fehlt", TeamColor.class, TeamColor.RED));
    }

    @Test
    void nenntErlaubteEnumWerteImFehler() {
        ApiRequest request = withBody("{\"color\": \"NEONGRUEN\"}");

        ApiBadRequestException error = assertThrows(ApiBadRequestException.class,
                () -> request.requireEnum("color", TeamColor.class));

        assertTrue(error.getMessage().contains("RED"), error.getMessage());
    }

    @Test
    void parstQueryParameter() {
        Map<String, String> query = ApiRequest.parseQuery("lines=50&category=BORDER&changed=true");
        ApiRequest request = new ApiRequest("GET", "/api/config", Map.of(), query, null);

        assertEquals(50, request.queryInt("lines", 200));
        assertEquals("BORDER", request.queryString("category", null));
        assertTrue(request.queryBool("changed", false));
        assertEquals(200, request.queryInt("fehlt", 200));
    }

    @Test
    void dekodiertQueryWerte() {
        assertEquals(Map.of("name", "Peeta Mellark"),
                ApiRequest.parseQuery("name=Peeta%20Mellark"));
        assertTrue(ApiRequest.parseQuery(null).isEmpty());
        assertTrue(ApiRequest.parseQuery("").isEmpty());
    }

    @Test
    void kenntPfadparameterNurWennRegistriert() {
        ApiRequest request = new ApiRequest("GET", "/api/teams/x",
                Map.of("id", "distrikt-12"), Map.of(), null);

        assertEquals("distrikt-12", request.param("id"));
        assertThrows(IllegalStateException.class, () -> request.param("unbekannt"));
    }
}
