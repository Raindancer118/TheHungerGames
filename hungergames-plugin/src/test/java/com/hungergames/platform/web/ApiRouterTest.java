package com.hungergames.platform.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRouterTest {

    private static final ApiRouter.Handler NOOP = request -> ApiResponse.ok();

    @Test
    void loestPfadparameterAuf() {
        ApiRouter router = new ApiRouter();
        router.post("/api/teams/{id}/members", "zuordnen", NOOP);

        var match = router.match("POST", "/api/teams/distrikt-12/members").orElseThrow();

        assertEquals("distrikt-12", match.pathParams().get("id"));
        assertTrue(match.route().write());
        assertTrue(match.route().mainThread());
    }

    @Test
    void wörtlicheSegmenteSchlagenParameter() {
        ApiRouter router = new ApiRouter();
        router.post("/api/teams/{id}", "team", request -> ApiResponse.ok(json ->
                json.addProperty("route", "param")));
        router.post("/api/teams/random", "zufall", request -> ApiResponse.ok(json ->
                json.addProperty("route", "literal")));

        var match = router.match("POST", "/api/teams/random").orElseThrow();

        assertEquals("/api/teams/random", match.route().pattern());
    }

    @Test
    void dekodiertPfadsegmente() {
        ApiRouter router = new ApiRouter();
        router.delete("/api/gamemaster/names/{name}", "austragen", NOOP);

        var match = router.match("DELETE", "/api/gamemaster/names/Peeta%20Mellark").orElseThrow();

        assertEquals("Peeta Mellark", match.pathParams().get("name"));
    }

    @Test
    void unterscheidetMethodeUndLaenge() {
        ApiRouter router = new ApiRouter();
        router.get("/api/teams", "liste", NOOP);
        router.delete("/api/teams", "alle löschen", NOOP);

        assertTrue(router.match("GET", "/api/teams").isPresent());
        assertTrue(router.match("DELETE", "/api/teams").isPresent());
        assertTrue(router.match("POST", "/api/teams").isEmpty());
        assertTrue(router.match("GET", "/api/teams/abc").isEmpty());
        assertEquals(java.util.Set.of("GET", "DELETE"), router.methodsFor("/api/teams"));
    }

    @Test
    void getIstLesendAllesAndereSchreibend() {
        ApiRouter router = new ApiRouter();
        router.get("/api/status", "lesen", NOOP);
        router.patch("/api/teams/{id}", "ändern", NOOP);

        assertFalse(router.match("GET", "/api/status").orElseThrow().route().write());
        assertTrue(router.match("PATCH", "/api/teams/x").orElseThrow().route().write());
    }

    @Test
    void asyncRoutenLaufenOhneMainThread() {
        ApiRouter router = new ApiRouter();
        router.getAsync("/api/log", "log", NOOP);

        assertFalse(router.match("GET", "/api/log").orElseThrow().route().mainThread());
    }

    @Test
    void lehntDoppelteRegistrierungAb() {
        ApiRouter router = new ApiRouter();
        router.get("/api/status", "lesen", NOOP);

        assertThrows(IllegalStateException.class,
                () -> router.get("/api/status", "nochmal", NOOP));
    }

    @Test
    void leeresSegmentMatchtKeinenParameter() {
        ApiRouter router = new ApiRouter();
        router.get("/api/participants/{player}", "detail", NOOP);

        assertTrue(router.match("GET", "/api/participants/").isEmpty());
        assertTrue(router.match("GET", "/api/participants/Katniss").isPresent());
    }

    @Test
    void handlerBekommtDieAnfrage() throws Exception {
        ApiRouter router = new ApiRouter();
        router.get("/api/echo/{value}", "echo", request ->
                ApiResponse.ok(json -> json.addProperty("value", request.param("value"))));

        var match = router.match("GET", "/api/echo/42").orElseThrow();
        ApiRequest request = new ApiRequest("GET", "/api/echo/42", match.pathParams(),
                Map.of(), null);

        ApiResponse response = match.route().handler().handle(request);

        assertTrue(response.isSuccess());
        assertEquals("42", response.body().get("value").getAsString());
    }
}
