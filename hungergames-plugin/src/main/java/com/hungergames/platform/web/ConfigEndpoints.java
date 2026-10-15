package com.hungergames.platform.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.game.config.SettingDefinition.SettingCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Konfigurations-Endpunkte: alle Einstellungen lesen, einzeln oder als
 * Transaktion setzen, Kategorien auflisten und die Dateien neu laden.
 *
 * <p>Werte werden als Text übergeben und über {@code SettingType} geparst und
 * validiert — genau wie in der GUI. Eine Batch-Änderung ist atomar: ist ein
 * Wert ungültig, wird keiner übernommen.</p>
 *
 * <p>Geheime Schlüssel (z. B. {@code api.key}) werden beim Lesen maskiert,
 * damit ein Dashboard nicht versehentlich den API-Schlüssel ausliefert;
 * gesetzt werden dürfen sie weiterhin.</p>
 */
final class ConfigEndpoints implements ApiRouter.Module {

    /** Schlüssel, deren Werte nie ausgeliefert werden. */
    static final Set<String> SECRET_KEYS = Set.of("api.key");

    /** Platzhalter für maskierte Werte. */
    static final String MASKED = "***";

    private final ApiSupport support;

    ConfigEndpoints(ApiSupport support) {
        this.support = support;
    }

    @Override
    public void register(ApiRouter router) {
        router.get("/api/config",
                "Alle Einstellungen — ?category=BORDER&changed=true", this::list);
        router.get("/api/config/categories", "Kategorien mit Anzahl", this::categories);
        router.get("/api/config/{key}", "Einzelne Einstellung", this::detail);
        router.put("/api/config/{key}",
                "Wert setzen — {\"value\": \"...\", \"scope\": \"PERSISTENT|ROUND\"}", this::set);
        router.post("/api/config",
                "Mehrere Werte atomar setzen — {\"values\": {...}, \"scope\"?}", this::setMany);
        router.post("/api/config/reload",
                "config.yml, loot.yml, Rezepte und Server-Whitelist neu laden", this::reload);
    }

    // ==================== Lesen ====================

    private ApiResponse list(ApiRequest request) {
        String categoryFilter = request.queryString("category", null);
        SettingCategory category = null;
        if (categoryFilter != null) {
            try {
                category = SettingCategory.valueOf(
                        categoryFilter.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ApiResponse.badRequest("Unbekannte Kategorie: " + categoryFilter);
            }
        }
        boolean onlyChanged = request.queryBool("changed", false);

        JsonArray array = new JsonArray();
        for (SettingDefinition<?> definition : support.services().config.registry().all()) {
            if (category != null && definition.category() != category) {
                continue;
            }
            JsonObject entry = settingJson(definition);
            if (onlyChanged && !entry.get("changed").getAsBoolean()) {
                continue;
            }
            array.add(entry);
        }
        return ApiResponse.json("settings", array);
    }

    private ApiResponse categories(ApiRequest request) {
        JsonArray array = new JsonArray();
        for (SettingCategory category : SettingCategory.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", category.name());
            entry.addProperty("settings",
                    support.services().config.registry().byCategory(category).size());
            array.add(entry);
        }
        return ApiResponse.json("categories", array);
    }

    private ApiResponse detail(ApiRequest request) {
        return definition(request.param("key"))
                .map(definition -> ApiResponse.json(settingJson(definition)))
                .orElseGet(() -> ApiResponse.notFound(
                        "Unbekannte Einstellung: " + request.param("key")));
    }

    // ==================== Schreiben ====================

    private ApiResponse set(ApiRequest request) {
        String key = request.param("key");
        Optional<SettingDefinition<?>> definition = definition(key);
        if (definition.isEmpty()) {
            return ApiResponse.notFound("Unbekannte Einstellung: " + key);
        }
        String value = request.requireString("value");
        ConfigurationService.Scope scope = request.optEnum("scope",
                ConfigurationService.Scope.class, ConfigurationService.Scope.PERSISTENT);
        return apply(Map.of(key, value), scope);
    }

    private ApiResponse setMany(ApiRequest request) {
        if (!request.has("values") || !request.body().get("values").isJsonObject()) {
            return ApiResponse.badRequest(
                    "Erwartet JSON-Body: {\"values\": {\"key\": \"wert\", ...}, \"scope\"?}");
        }
        ConfigurationService.Scope scope = request.optEnum("scope",
                ConfigurationService.Scope.class, ConfigurationService.Scope.PERSISTENT);
        JsonObject values = request.body().getAsJsonObject("values");
        Map<String, String> changes = new java.util.LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        for (String key : values.keySet()) {
            if (definition(key).isEmpty()) {
                unknown.add(key);
            } else {
                changes.put(key, values.get(key).getAsString());
            }
        }
        if (!unknown.isEmpty()) {
            return ApiResponse.badRequest("Unbekannte Einstellungen: " + String.join(", ", unknown));
        }
        if (changes.isEmpty()) {
            return ApiResponse.badRequest("Keine Werte übergeben");
        }
        return apply(changes, scope);
    }

    /** Wendet die Änderungen als eine Transaktion an (alles oder nichts). */
    private ApiResponse apply(Map<String, String> changes, ConfigurationService.Scope scope) {
        var transaction = support.services().config.begin(ApiSupport.ACTOR);
        changes.forEach((key, value) ->
                setFromInput(transaction, definition(key).orElseThrow(), value, scope));
        List<String> errors = transaction.commit();
        if (!errors.isEmpty()) {
            JsonObject json = new JsonObject();
            json.addProperty("error", String.join("; ", errors));
            JsonArray array = new JsonArray();
            errors.forEach(array::add);
            json.add("errors", array);
            return new ApiResponse(400, json);
        }
        changes.forEach((key, value) -> support.log("Konfiguration " + key + " = "
                + (SECRET_KEYS.contains(key) ? MASKED : value) + " (" + scope + ") per HTTP-API"));
        return ApiResponse.ok(json -> {
            JsonArray applied = new JsonArray();
            changes.keySet().forEach(applied::add);
            json.add("applied", applied);
            json.addProperty("scope", scope.name());
            JsonArray pending = new JsonArray();
            changes.keySet().forEach(key -> definition(key)
                    .filter(def -> def.applyTiming()
                            != SettingDefinition.ApplyTiming.IMMEDIATE)
                    .ifPresent(def -> {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("key", def.key());
                        entry.addProperty("activeAt", def.applyTiming().name());
                        pending.add(entry);
                    }));
            json.add("pending", pending);
        });
    }

    private ApiResponse reload(ApiRequest request) {
        support.services().config.reloadFromBackend();
        com.hungergames.utils.ConfigManager.getInstance().reloadFromFile();
        com.hungergames.loot.LootConfig.getInstance().reload();
        support.services().lootTables.loadFromDisk();
        com.hungergames.player.WhitelistManager.getInstance().applyToServerWhitelist();
        com.hungergames.items.CustomItems.registerRecipes(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(com.hungergames.HungerGamesPlugin.class));
        support.log("Konfiguration, Loot und Rezepte per HTTP-API neu geladen");
        return ApiResponse.ok();
    }

    // ==================== intern ====================

    private Optional<SettingDefinition<?>> definition(String key) {
        return support.services().config.registry().get(key);
    }

    /** Generisch, damit der Typparameter von {@code definition} erhalten bleibt. */
    private static <T> void setFromInput(ConfigurationService.Transaction transaction,
                                         SettingDefinition<T> definition, String value,
                                         ConfigurationService.Scope scope) {
        transaction.setFromInput(definition, value, scope);
    }

    private JsonObject settingJson(SettingDefinition<?> definition) {
        var config = support.services().config;
        boolean secret = SECRET_KEYS.contains(definition.key());
        JsonObject json = new JsonObject();
        json.addProperty("key", definition.key());
        json.addProperty("category", definition.category().name());
        json.addProperty("applyTiming", definition.applyTiming().name());
        json.addProperty("description", definition.description());
        json.addProperty("type", definition.type().valueClass().getSimpleName());
        json.addProperty("secret", secret);
        String value = serialize(definition, config);
        String defaultValue = serializeDefault(definition);
        json.addProperty("value", secret ? maskIfSet(value) : value);
        json.addProperty("default", secret ? maskIfSet(defaultValue) : defaultValue);
        json.addProperty("changed", !value.equals(defaultValue));
        json.addProperty("roundOverride", config.hasRoundOverride(definition.key()));
        config.pendingValue(definition).ifPresentOrElse(
                pending -> json.addProperty("pending", secret ? MASKED : pending),
                () -> json.add("pending", null));
        return json;
    }

    private static String maskIfSet(String value) {
        return value == null || value.isEmpty() ? "" : MASKED;
    }

    private static <T> String serialize(SettingDefinition<T> definition,
                                        ConfigurationService config) {
        return definition.type().serialize(config.get(definition));
    }

    private static <T> String serializeDefault(SettingDefinition<T> definition) {
        return definition.type().serialize(definition.defaultValue());
    }
}
