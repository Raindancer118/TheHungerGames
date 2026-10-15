package com.hungergames.platform.web;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.gui.admin.AdminServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ende-zu-Ende-Test der Transportschicht gegen einen echt gestarteten Server:
 * Authentifizierung, Read-only-Sperre, Routing-Fehler und Body-Validierung.
 *
 * <p>Geprüft werden nur Endpunkte ohne Bukkit-Zugriff (Health, Index) sowie die
 * Pfade, die <em>vor</em> dem Handler greifen — der Rest braucht einen
 * laufenden Server und ist über {@link HttpApiRouteTableTest} und die
 * Endpunkt-Module abgedeckt.</p>
 */
class HttpApiServiceTest {

    private static final String KEY = "test-schluessel-1234567890";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private ConfigurationService config;
    private HgSettings settings;
    private HttpApiService api;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        settings = new HgSettings();
        Map<String, String> stored = new HashMap<>();
        config = new ConfigurationService(settings.registry, new ConfigurationService.Backend() {
            @Override
            public Map<String, String> load() {
                return stored;
            }

            @Override
            public void save(Map<String, String> serializedValues) {
                stored.clear();
                stored.putAll(serializedValues);
            }
        }, entry -> {
        }, System::currentTimeMillis);

        port = freePort();
        config.begin("Test")
                .set(settings.apiEnabled, true, ConfigurationService.Scope.PERSISTENT)
                .set(settings.apiPort, port, ConfigurationService.Scope.PERSISTENT)
                .set(settings.apiKey, KEY, ConfigurationService.Scope.PERSISTENT)
                .commit();
        // api.enabled/port wirken erst nach Neustart — im Test sofort aktivieren.
        config.activatePending(com.hungergames.game.config.SettingDefinition.ApplyTiming.RESTART);

        AdminServices services = new AdminServices(null, null, config, settings, null, null,
                null, null, null, null, null, null, null, null, null);
        api = new HttpApiService(null, services, Logger.getAnonymousLogger());
        api.start();
        assertTrue(api.isRunning(), "Server sollte laufen");
    }

    @AfterEach
    void stopServer() {
        if (api != null) {
            api.stop();
        }
    }

    @Test
    void healthBrauchtKeinenSchluessel() throws Exception {
        HttpResponse<String> response = send("GET", "/api/health", null, null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"ok\""), response.body());
        assertTrue(response.body().contains("\"endpoints\""), response.body());
    }

    @Test
    void ohneSchluesselKeinZugriff() throws Exception {
        assertEquals(401, send("GET", "/api", null, null).statusCode());
        assertEquals(401, send("GET", "/api", null, "falscher-schluessel").statusCode());
    }

    @Test
    void indexListetAlleEndpunkte() throws Exception {
        HttpResponse<String> response = send("GET", "/api", null, KEY);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("/api/teams/{id}/captain"), response.body());
        assertTrue(response.body().contains("/api/config/{key}"), response.body());
        assertTrue(response.body().contains("\"write\":true"), response.body());
    }

    @Test
    void bearerTokenWirdAkzeptiert() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api"))
                        .header("Authorization", "Bearer " + KEY)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }

    @Test
    void unbekannterPfadIst404() throws Exception {
        HttpResponse<String> response = send("GET", "/api/gibtesnicht", null, KEY);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("GET /api"), response.body());
    }

    @Test
    void falscheMethodeIst405MitHinweis() throws Exception {
        HttpResponse<String> response = send("DELETE", "/api", null, KEY);

        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("GET"), response.body());
    }

    @Test
    void readOnlySperrtSchreibendeEndpunkte() throws Exception {
        config.begin("Test")
                .set(settings.apiReadOnly, true, ConfigurationService.Scope.PERSISTENT)
                .commit();

        HttpResponse<String> blocked = send("POST", "/api/config", "{\"values\":{}}", KEY);
        assertEquals(403, blocked.statusCode());
        assertTrue(blocked.body().contains("read-only"), blocked.body());

        // Lesen bleibt erlaubt
        assertEquals(200, send("GET", "/api", null, KEY).statusCode());
    }

    @Test
    void ungueltigesJsonIst400() throws Exception {
        HttpResponse<String> response = send("POST", "/api/config", "{kaputt", KEY);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("JSON"), response.body());
    }

    @Test
    void optionsAntwortetMitCorsHeadern() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/teams"))
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(204, response.statusCode());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Methods")
                .orElse("").contains("PATCH"));
    }

    // ==================== Helfer ====================

    private HttpResponse<String> send(String method, String path, String body, String key)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path));
        if (key != null) {
            builder.header("X-API-Key", key);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
