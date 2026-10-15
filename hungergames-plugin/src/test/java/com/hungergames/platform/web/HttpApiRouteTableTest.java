package com.hungergames.platform.web;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sichert die Routing-Tabelle der HTTP-API ab: Die Registrierung läuft ohne
 * Bukkit (die Endpunkt-Module berühren die Services erst beim Aufruf), daher
 * lässt sich der vollständige Endpunkt-Katalog hier prüfen.
 */
class HttpApiRouteTableTest {

    private final ApiRouter router =
            new HttpApiService(null, null, Logger.getAnonymousLogger()).router();

    /** Endpunkte, die vor der Router-Umstellung dokumentiert waren. */
    private static final List<String> BESTANDS_ENDPUNKTE = List.of(
            "GET /api/status",
            "GET /api/participants",
            "GET /api/participants/{player}",
            "GET /api/teams",
            "GET /api/loot/tables",
            "POST /api/whitelist",
            "DELETE /api/whitelist/{player}",
            "POST /api/teams",
            "DELETE /api/teams/{id}",
            "POST /api/teams/{id}/members",
            "DELETE /api/teams/{id}/members/{player}",
            "POST /api/revive",
            "POST /api/deathmatch/start",
            "POST /api/deathmatch/cancel",
            "POST /api/supplydrop",
            "POST /api/round/end",
            "POST /api/sponsor/give",
            "POST /api/announce");

    /** Neu ergänzte Endpunkte — „alles über die API steuerbar". */
    private static final List<String> NEUE_ENDPUNKTE = List.of(
            "GET /api",
            "GET /api/teams/colors",
            "GET /api/teams/{id}",
            "PATCH /api/teams/{id}",
            "DELETE /api/teams",
            "POST /api/teams/random",
            "POST /api/teams/{id}/captain",
            "GET /api/game",
            "GET /api/game/preflight",
            "POST /api/game/init",
            "POST /api/game/startup",
            "POST /api/game/start",
            "POST /api/game/end",
            "POST /api/game/reset",
            "POST /api/game/phase",
            "POST /api/time/multiplier",
            "GET /api/border",
            "POST /api/border/shrink",
            "POST /api/border/reset",
            "GET /api/players",
            "GET /api/log",
            "POST /api/eliminate",
            "GET /api/deathmatch",
            "GET /api/supplydrop",
            "GET /api/sponsor",
            "POST /api/sponsor/clear",
            "POST /api/sponsor/beacons",
            "DELETE /api/sponsor/beacons",
            "GET /api/monsterwaves",
            "POST /api/monsterwaves",
            "DELETE /api/monsterwaves",
            "GET /api/sounds",
            "POST /api/sounds/test",
            "GET /api/effects",
            "POST /api/effects/test",
            "GET /api/config",
            "GET /api/config/categories",
            "GET /api/config/{key}",
            "PUT /api/config/{key}",
            "POST /api/config",
            "POST /api/config/reload",
            "GET /api/loot/tables/{name}",
            "POST /api/loot/tables",
            "DELETE /api/loot/tables/{name}",
            "POST /api/loot/tables/{name}/entries",
            "PATCH /api/loot/tables/{name}/entries/{index}",
            "DELETE /api/loot/tables/{name}/entries/{index}",
            "GET /api/loot/validate",
            "POST /api/loot/save",
            "POST /api/loot/reload",
            "GET /api/gamemaster",
            "POST /api/gamemaster/names",
            "DELETE /api/gamemaster/names/{name}",
            "POST /api/gamemaster/activate",
            "POST /api/gamemaster/deactivate",
            "POST /api/gamemaster/mode",
            "POST /api/spectator/teleport",
            "GET /api/simulation",
            "POST /api/simulation/spawn",
            "POST /api/simulation/eliminate",
            "DELETE /api/simulation");

    @Test
    void bestandsEndpunkteBleibenErhalten() {
        Set<String> registered = keys();
        for (String endpoint : BESTANDS_ENDPUNKTE) {
            assertTrue(registered.contains(endpoint), "Fehlender Bestands-Endpunkt: " + endpoint);
        }
    }

    @Test
    void neueEndpunkteSindRegistriert() {
        Set<String> registered = keys();
        for (String endpoint : NEUE_ENDPUNKTE) {
            assertTrue(registered.contains(endpoint), "Fehlender Endpunkt: " + endpoint);
        }
    }

    @Test
    void keineDoppeltenRouten() {
        Set<String> seen = new HashSet<>();
        for (ApiRouter.Route route : router.routes()) {
            assertTrue(seen.add(route.method() + " " + route.pattern()),
                    "Doppelt: " + route.method() + " " + route.pattern());
        }
    }

    @Test
    void jedeRouteHatEineBeschreibung() {
        for (ApiRouter.Route route : router.routes()) {
            assertFalse(route.description() == null || route.description().isBlank(),
                    "Ohne Beschreibung: " + route.pattern());
        }
    }

    @Test
    void nurGetIstLesend() {
        for (ApiRouter.Route route : router.routes()) {
            assertEquals(!route.method().equals("GET"), route.write(),
                    "Falsche Schreib-Einstufung: " + route.method() + " " + route.pattern());
        }
    }

    @Test
    void jederPfadIstEindeutigAufloesbar() {
        for (ApiRouter.Route route : router.routes()) {
            String concrete = route.pattern().replaceAll("\\{[^}]+}", "beispiel");
            var match = router.match(route.method(), concrete);
            assertTrue(match.isPresent(), "Nicht auflösbar: " + route.pattern());
        }
    }

    private Set<String> keys() {
        return router.routes().stream()
                .map(route -> route.method() + " " + route.pattern())
                .collect(Collectors.toSet());
    }
}
