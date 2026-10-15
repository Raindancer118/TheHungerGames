package com.hungergames.platform.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Routing-Tabelle der HTTP-API: Muster wie {@code /api/teams/{id}/members}
 * werden auf Handler abgebildet, Pfadparameter dabei extrahiert.
 *
 * <p>Bukkit- und HTTP-frei — dadurch vollständig unit-testbar. Der Transport
 * (Auth, Body-Parsing, Main-Thread-Hop, Serialisierung) liegt in
 * {@link HttpApiService}, die Fachlogik in den {@code *Endpoints}-Klassen.</p>
 *
 * <p>Alle Methoden außer {@code GET} gelten als schreibend und werden bei
 * {@code api.read-only} zentral mit {@code 403} abgelehnt — Endpunkte können
 * das nicht versehentlich vergessen.</p>
 */
public final class ApiRouter {

    /** Ein Handler bekommt die geparste Anfrage und liefert die Antwort. */
    @FunctionalInterface
    public interface Handler {
        ApiResponse handle(ApiRequest request) throws Exception;
    }

    /** Bündel von Endpunkten eines Fachbereichs. */
    @FunctionalInterface
    public interface Module {
        void register(ApiRouter router);
    }

    /**
     * Eine registrierte Route.
     *
     * @param mainThread {@code true}, wenn der Handler Bukkit-API berührt und
     *                   daher auf dem Server-Main-Thread laufen muss
     */
    public record Route(String method, String pattern, List<String> segments,
                        boolean write, boolean mainThread, String description, Handler handler) {
    }

    /** Treffer einer Pfadauflösung. */
    public record Match(Route route, Map<String, String> pathParams) {
    }

    private final List<Route> routes = new ArrayList<>();

    // ==================== Registrierung ====================

    /** Lesender Endpunkt auf dem Main-Thread (Bukkit-Zugriff erlaubt). */
    public ApiRouter get(String pattern, String description, Handler handler) {
        return add("GET", pattern, true, description, handler);
    }

    /** Lesender Endpunkt ohne Main-Thread-Hop (kein Bukkit-Zugriff!). */
    public ApiRouter getAsync(String pattern, String description, Handler handler) {
        return add("GET", pattern, false, description, handler);
    }

    public ApiRouter post(String pattern, String description, Handler handler) {
        return add("POST", pattern, true, description, handler);
    }

    /** Schreibender Endpunkt ohne Main-Thread-Hop (kein Bukkit-Zugriff!). */
    public ApiRouter postAsync(String pattern, String description, Handler handler) {
        return add("POST", pattern, false, description, handler);
    }

    public ApiRouter patch(String pattern, String description, Handler handler) {
        return add("PATCH", pattern, true, description, handler);
    }

    public ApiRouter put(String pattern, String description, Handler handler) {
        return add("PUT", pattern, true, description, handler);
    }

    public ApiRouter delete(String pattern, String description, Handler handler) {
        return add("DELETE", pattern, true, description, handler);
    }

    /** Schreibender DELETE-Endpunkt ohne Main-Thread-Hop. */
    public ApiRouter deleteAsync(String pattern, String description, Handler handler) {
        return add("DELETE", pattern, false, description, handler);
    }

    public ApiRouter register(Module... modules) {
        for (Module module : modules) {
            module.register(this);
        }
        return this;
    }

    private ApiRouter add(String method, String pattern, boolean mainThread,
                          String description, Handler handler) {
        List<String> segments = split(pattern);
        boolean write = !method.equals("GET");
        Route route = new Route(method, pattern, segments, write, mainThread, description, handler);
        for (Route existing : routes) {
            if (existing.method().equals(method) && existing.pattern().equals(pattern)) {
                throw new IllegalStateException("Route doppelt registriert: " + method + " " + pattern);
            }
        }
        routes.add(route);
        return this;
    }

    // ==================== Auflösung ====================

    /**
     * Sucht die passende Route. Bei mehreren Kandidaten gewinnt die mit den
     * meisten wörtlichen (nicht-parametrisierten) Segmenten, damit
     * {@code /api/teams/random} vor {@code /api/teams/{id}} greift.
     */
    public Optional<Match> match(String method, String path) {
        List<String> actual = split(path);
        return routes.stream()
                .filter(route -> route.method().equalsIgnoreCase(method))
                .filter(route -> matches(route, actual))
                .max(Comparator.comparingInt(ApiRouter::literalCount))
                .map(route -> new Match(route, extract(route, actual)));
    }

    /** @return alle Methoden, die für diesen Pfad registriert sind (für 405). */
    public Set<String> methodsFor(String path) {
        List<String> actual = split(path);
        Set<String> methods = new LinkedHashSet<>();
        for (Route route : routes) {
            if (matches(route, actual)) {
                methods.add(route.method());
            }
        }
        return methods;
    }

    /** Alle Routen in Registrierungsreihenfolge — Grundlage für {@code GET /api}. */
    public List<Route> routes() {
        return List.copyOf(routes);
    }

    // ==================== intern ====================

    private static boolean matches(Route route, List<String> actual) {
        if (route.segments().size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < actual.size(); i++) {
            String expected = route.segments().get(i);
            if (isParam(expected)) {
                if (actual.get(i).isEmpty()) {
                    return false;
                }
            } else if (!expected.equalsIgnoreCase(actual.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> extract(Route route, List<String> actual) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < actual.size(); i++) {
            String expected = route.segments().get(i);
            if (isParam(expected)) {
                params.put(expected.substring(1, expected.length() - 1), actual.get(i));
            }
        }
        return params;
    }

    private static int literalCount(Route route) {
        int count = 0;
        for (String segment : route.segments()) {
            if (!isParam(segment)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isParam(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    /** Zerlegt einen Pfad in dekodierte Segmente; führende/mehrfache Slashes fallen weg. */
    static List<String> split(String path) {
        List<String> segments = new ArrayList<>();
        for (String raw : path.split("/")) {
            if (!raw.isEmpty()) {
                segments.add(URLDecoder.decode(raw, StandardCharsets.UTF_8));
            }
        }
        return segments;
    }

    /** Normalisiert eine HTTP-Methode. */
    static String normalizeMethod(String method) {
        return method == null ? "" : method.toUpperCase(Locale.ROOT);
    }
}
