package com.hungergames.platform.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.gui.admin.AdminServices;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Eingebettete HTTP-API für den Betrieb in geschlossenen Netzen (Event-Regie,
 * Dashboards, Stream-Overlays). Nutzt den JDK-HttpServer — kein zusätzliches
 * Dependency im Plugin-Jar.
 *
 * <p>Diese Klasse ist reiner Transport: Server-Lebenszyklus, Authentifizierung,
 * Read-only-Sperre, Body-Parsing, Main-Thread-Hop und Serialisierung. Welche
 * Endpunkte es gibt, steht in den {@code *Endpoints}-Modulen; die Zuordnung
 * übernimmt der {@link ApiRouter}. {@code GET /api} listet alle Routen auf.</p>
 *
 * <p><strong>Sicherheit:</strong> Standardmäßig deaktiviert und an
 * {@code 127.0.0.1} gebunden. Jede Anfrage (außer {@code /api/health}) braucht
 * den konfigurierten Schlüssel im {@code X-API-Key}-Header (zeitkonstanter
 * Vergleich); ist keiner konfiguriert, wird beim Start einer generiert und
 * persistiert. {@code api.read-only} sperrt alle schreibenden Endpunkte
 * zentral. Kein TLS — die API gehört hinter ein geschlossenes Netz oder einen
 * Reverse-Proxy, nie ins offene Internet.</p>
 *
 * <p><strong>Threading:</strong> Der HTTP-Server läuft auf eigenen Threads;
 * Routen, die Bukkit berühren, werden über {@code callSyncMethod} auf den
 * Main-Thread geschoben (Timeout 5 s).</p>
 */
public final class HttpApiService {

    private static final long MAIN_THREAD_TIMEOUT_SECONDS = 5;
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Gson GSON = new Gson();

    private final org.bukkit.plugin.Plugin plugin;
    private final AdminServices services;
    private final Logger logger;
    private final ApiRouter router;

    private HttpServer server;
    private ExecutorService executor;

    public HttpApiService(org.bukkit.plugin.Plugin plugin, AdminServices services, Logger logger) {
        this.plugin = plugin;
        this.services = services;
        this.logger = logger;
        this.router = buildRouter(new ApiSupport(services));
    }

    /** Registriert alle Endpunkt-Module plus den Selbstbeschreibungs-Index. */
    private ApiRouter buildRouter(ApiSupport support) {
        ApiRouter created = new ApiRouter();
        created.getAsync("/api", "Diese Übersicht aller Endpunkte", request -> index());
        created.register(
                new StatusEndpoints(support),
                new TeamEndpoints(support),
                new GameEndpoints(support),
                new EventEndpoints(support),
                new ConfigEndpoints(support),
                new LootEndpoints(support),
                new AdminEndpoints(support));
        return created;
    }

    // ==================== Lebenszyklus ====================

    /** Startet den Server, sofern {@code api.enabled} gesetzt ist. */
    public void start() {
        if (!services.config.get(services.settings.apiEnabled)) {
            return;
        }
        ensureKeyExists();
        String bind = services.config.get(services.settings.apiBindAddress);
        int port = services.config.get(services.settings.apiPort);
        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        } catch (IOException e) {
            logger.severe("HTTP-API konnte nicht starten (" + bind + ":" + port + "): "
                    + e.getMessage());
            return;
        }
        executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "HungerGames-HttpApi");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/api", this::handleSafely);
        server.start();
        logger.info("HTTP-API läuft auf " + bind + ":" + port + " mit " + router.routes().size()
                + " Endpunkten"
                + (bind.equals("127.0.0.1") ? " (nur lokal)" : " — nur in geschlossenen Netzen exposen!"));
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    /** @return die Routing-Tabelle (für Tests und Doku-Generierung). */
    public ApiRouter router() {
        return router;
    }

    /** Generiert und persistiert einen Schlüssel, falls keiner konfiguriert ist. */
    private void ensureKeyExists() {
        if (!services.config.get(services.settings.apiKey).isBlank()) {
            return;
        }
        String key = ApiKeys.generate();
        List<String> errors = services.config.begin("System")
                .set(services.settings.apiKey, key, ConfigurationService.Scope.PERSISTENT)
                .commit();
        if (errors.isEmpty()) {
            logger.info("HTTP-API: Schlüssel generiert — siehe config.yml (api.key) "
                    + "bzw. /hg config → HTTP-API.");
        } else {
            errors.forEach(logger::warning);
        }
    }

    // ==================== Transport ====================

    private void handleSafely(HttpExchange exchange) throws IOException {
        try {
            handle(exchange);
        } catch (Exception e) {
            logger.warning("HTTP-API: unbehandelter Fehler bei "
                    + exchange.getRequestURI() + ": " + e.getMessage());
            respond(exchange, ApiResponse.error(500, "Interner Fehler"));
        } finally {
            exchange.close();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "X-API-Key, Authorization, Content-Type");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");

        String method = ApiRouter.normalizeMethod(exchange.getRequestMethod());
        String path = exchange.getRequestURI().getPath();

        if (method.equals("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (path.equals("/api/health")) {
            respond(exchange, ApiResponse.json(health()));
            return;
        }
        if (!authorized(exchange)) {
            respond(exchange, ApiResponse.error(401, "Ungültiger oder fehlender X-API-Key"));
            return;
        }

        Optional<ApiRouter.Match> match = router.match(method, path);
        if (match.isEmpty()) {
            respond(exchange, notMatched(method, path));
            return;
        }
        ApiRouter.Route route = match.get().route();
        if (route.write() && services.config.get(services.settings.apiReadOnly)) {
            respond(exchange, ApiResponse.error(403, "API ist schreibgeschützt (api.read-only)"));
            return;
        }

        ApiRequest request;
        try {
            request = new ApiRequest(method, path, match.get().pathParams(),
                    ApiRequest.parseQuery(exchange.getRequestURI().getRawQuery()),
                    readBody(exchange));
        } catch (ApiBadRequestException e) {
            respond(exchange, ApiResponse.badRequest(e.getMessage()));
            return;
        }
        respond(exchange, dispatch(route, request));
    }

    /** Führt den Handler aus — je nach Route auf dem Main-Thread — und mappt Fehler. */
    private ApiResponse dispatch(ApiRouter.Route route, ApiRequest request) {
        try {
            if (!route.mainThread()) {
                return route.handler().handle(request);
            }
            return onMainThread(() -> route.handler().handle(request));
        } catch (ApiBadRequestException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (ApiConflictException e) {
            return ApiResponse.conflict(e.getMessage());
        } catch (TimeoutException e) {
            return ApiResponse.error(503, "Server ausgelastet (Main-Thread-Timeout)");
        } catch (Exception e) {
            logger.warning("HTTP-API: Fehler in " + route.method() + " " + route.pattern()
                    + ": " + e);
            return ApiResponse.error(500, "Interner Fehler: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Bukkit-Zugriffe gehören auf den Main-Thread. Fehler des Handlers werden
     * ausgepackt, damit {@link #dispatch} sie wie bei direkter Ausführung sieht.
     */
    private <T> T onMainThread(Callable<T> callable) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return callable.call();
        }
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                try {
                    return callable.call();
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }).get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw unwrap(e);
        } catch (java.util.concurrent.CompletionException e) {
            throw unwrap(e);
        }
    }

    private static Exception unwrap(Exception wrapper) {
        Throwable cause = wrapper.getCause();
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof Exception exception) {
            return exception;
        }
        return wrapper;
    }

    private boolean authorized(HttpExchange exchange) {
        String provided = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (provided == null) {
            String bearer = exchange.getRequestHeaders().getFirst("Authorization");
            if (bearer != null && bearer.startsWith("Bearer ")) {
                provided = bearer.substring("Bearer ".length());
            }
        }
        return ApiKeys.matches(services.config.get(services.settings.apiKey), provided);
    }

    /** 405 mit {@code Allow}-Hinweis, wenn der Pfad nur andere Methoden kennt. */
    private ApiResponse notMatched(String method, String path) {
        Set<String> allowed = router.methodsFor(path);
        if (allowed.isEmpty()) {
            return ApiResponse.notFound("Unbekannter Endpunkt: " + method + " " + path
                    + " — GET /api listet alle Endpunkte");
        }
        return ApiResponse.error(405, "Methode " + method + " ist für " + path
                + " nicht erlaubt (erlaubt: " + String.join(", ", allowed) + ")");
    }

    private JsonObject health() {
        JsonObject json = new JsonObject();
        json.addProperty("status", "ok");
        json.addProperty("endpoints", router.routes().size());
        json.addProperty("readOnly", services.config.get(services.settings.apiReadOnly));
        return json;
    }

    /** Selbstbeschreibung: alle Routen mit Methode, Pfad und Kurzbeschreibung. */
    private ApiResponse index() {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        for (ApiRouter.Route route : router.routes()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("method", route.method());
            entry.addProperty("path", route.pattern());
            entry.addProperty("description", route.description());
            entry.addProperty("write", route.write());
            array.add(entry);
        }
        json.add("endpoints", array);
        json.addProperty("count", array.size());
        json.addProperty("readOnly", services.config.get(services.settings.apiReadOnly));
        return ApiResponse.json(json);
    }

    /**
     * Liest den JSON-Body.
     *
     * @return das JSON-Objekt, oder {@code null}, wenn kein Body geschickt wurde
     * @throws ApiBadRequestException bei ungültigem JSON oder zu großem Body
     */
    private JsonObject readBody(HttpExchange exchange) throws IOException {
        byte[] raw = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES);
        if (raw.length == 0) {
            return null;
        }
        if (raw.length == MAX_BODY_BYTES && exchange.getRequestBody().read() != -1) {
            throw new ApiBadRequestException("Body ist größer als " + MAX_BODY_BYTES + " Bytes");
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return null;
        }
        try {
            var element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                throw new ApiBadRequestException("Body muss ein JSON-Objekt sein");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ApiBadRequestException("Body ist kein gültiges JSON");
        }
    }

    private void respond(HttpExchange exchange, ApiResponse response) throws IOException {
        byte[] bytes = GSON.toJson(response.body()).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
