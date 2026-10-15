package com.hungergames.platform.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hungergames.platform.loot.LootTableRepository.EntryModel;
import com.hungergames.platform.loot.LootTableRepository.TableModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LootTable-Endpunkte: Tabellen anlegen, duplizieren, löschen, Einträge
 * bearbeiten, validieren und auf die Platte schreiben.
 *
 * <p>Änderungen wirken sofort im Spiel (das Repository ist die Laufzeitquelle),
 * bleiben aber bis {@code POST /api/loot/save} ungespeichert —
 * {@code dirty: true} zeigt das an, genau wie im GUI-Editor.</p>
 */
final class LootEndpoints implements ApiRouter.Module {

    private final ApiSupport support;

    LootEndpoints(ApiSupport support) {
        this.support = support;
    }

    @Override
    public void register(ApiRouter router) {
        router.get("/api/loot/tables", "LootTables mit Eintragszahl und Gewicht", this::tables);
        router.post("/api/loot/tables",
                "Tabelle anlegen — {\"name\"} oder duplizieren — {\"source\", \"name\"}", this::create);
        router.get("/api/loot/validate", "Alle Tabellen validieren", this::validate);
        router.post("/api/loot/save", "Tabellen auf die Platte schreiben", this::save);
        router.post("/api/loot/reload",
                "Tabellen von der Platte laden (verwirft Ungespeichertes)", this::reload);
        router.get("/api/loot/tables/{name}", "Tabelle mit allen Einträgen", this::detail);
        router.delete("/api/loot/tables/{name}", "Tabelle löschen", this::delete);
        router.post("/api/loot/tables/{name}/entries",
                "Eintrag anlegen — {\"item\", \"weight\"?, \"minAmount\"?, \"maxAmount\"?, ...}",
                this::entryCreate);
        router.patch("/api/loot/tables/{name}/entries/{index}",
                "Eintrag ändern (nur übergebene Felder)", this::entryPatch);
        router.delete("/api/loot/tables/{name}/entries/{index}", "Eintrag löschen", this::entryDelete);
    }

    // ==================== Tabellen ====================

    private ApiResponse tables(ApiRequest request) {
        var repository = support.services().lootTables;
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        for (String name : repository.tableNames()) {
            repository.table(name).ifPresent(table -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", name);
                entry.addProperty("entries", table.entries.size());
                entry.addProperty("totalWeight", repository.totalWeight(table));
                entry.addProperty("usage", repository.usageOf(name));
                array.add(entry);
            });
        }
        json.add("tables", array);
        json.addProperty("dirty", repository.isDirty());
        return ApiResponse.json(json);
    }

    private ApiResponse detail(ApiRequest request) {
        Optional<TableModel> table = table(request);
        if (table.isEmpty()) {
            return ApiResponse.notFound("Unbekannte LootTable: " + request.param("name"));
        }
        var repository = support.services().lootTables;
        JsonObject json = new JsonObject();
        json.addProperty("name", table.get().name);
        json.addProperty("usage", repository.usageOf(table.get().name));
        json.addProperty("totalWeight", repository.totalWeight(table.get()));
        JsonArray entries = new JsonArray();
        List<EntryModel> models = table.get().entries;
        for (int i = 0; i < models.size(); i++) {
            entries.add(entryJson(i, models.get(i)));
        }
        json.add("entries", entries);
        json.addProperty("dirty", repository.isDirty());
        return ApiResponse.json(json);
    }

    private ApiResponse create(ApiRequest request) {
        var repository = support.services().lootTables;
        String name = request.requireString("name");
        String source = request.optString("source", null);
        Optional<String> error = source == null
                ? repository.createTable(name)
                : repository.duplicateTable(source, name);
        if (error.isPresent()) {
            return ApiResponse.conflict(error.get());
        }
        support.log("LootTable \"" + name + "\" per HTTP-API "
                + (source == null ? "angelegt" : "aus \"" + source + "\" dupliziert"));
        return ApiResponse.ok(json -> json.addProperty("name", name));
    }

    private ApiResponse delete(ApiRequest request) {
        String name = request.param("name");
        if (!support.services().lootTables.deleteTable(name)) {
            return ApiResponse.conflict("LootTable \"" + name
                    + "\" existiert nicht oder ist in Benutzung");
        }
        support.log("LootTable \"" + name + "\" per HTTP-API gelöscht");
        return ApiResponse.ok();
    }

    private ApiResponse validate(ApiRequest request) {
        List<String> problems = support.services().lootTables.validateAll();
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        problems.forEach(array::add);
        json.add("problems", array);
        json.addProperty("valid", problems.isEmpty());
        json.addProperty("dirty", support.services().lootTables.isDirty());
        return ApiResponse.json(json);
    }

    private ApiResponse save(ApiRequest request) {
        List<String> errors = support.services().lootTables.save(ApiSupport.ACTOR);
        if (!errors.isEmpty()) {
            JsonObject json = new JsonObject();
            json.addProperty("error", String.join("; ", errors));
            JsonArray array = new JsonArray();
            errors.forEach(array::add);
            json.add("errors", array);
            return new ApiResponse(409, json);
        }
        return ApiResponse.ok();
    }

    private ApiResponse reload(ApiRequest request) {
        support.services().lootTables.loadFromDisk();
        support.log("LootTables per HTTP-API von der Platte neu geladen");
        return ApiResponse.ok();
    }

    // ==================== Einträge ====================

    private ApiResponse entryCreate(ApiRequest request) {
        Optional<TableModel> table = table(request);
        if (table.isEmpty()) {
            return ApiResponse.notFound("Unbekannte LootTable: " + request.param("name"));
        }
        EntryModel entry = new EntryModel();
        entry.item = request.requireString("item");
        applyFields(request, entry);
        Optional<String> problem = support.services().lootTables.validateEntry(entry);
        if (problem.isPresent()) {
            return ApiResponse.badRequest(problem.get());
        }
        table.get().entries.add(entry);
        support.services().lootTables.markDirty();
        support.log("LootTable \"" + table.get().name + "\": Eintrag " + entry.item
                + " per HTTP-API hinzugefügt");
        int index = table.get().entries.size() - 1;
        return ApiResponse.ok(json -> json.add("entry", entryJson(index, entry)));
    }

    private ApiResponse entryPatch(ApiRequest request) {
        Optional<TableModel> table = table(request);
        if (table.isEmpty()) {
            return ApiResponse.notFound("Unbekannte LootTable: " + request.param("name"));
        }
        int index = index(request);
        List<EntryModel> entries = table.get().entries;
        if (index < 0 || index >= entries.size()) {
            return ApiResponse.notFound("Kein Eintrag mit Index " + index);
        }
        EntryModel candidate = entries.get(index).copy();
        candidate.item = request.optString("item", candidate.item);
        applyFields(request, candidate);
        Optional<String> problem = support.services().lootTables.validateEntry(candidate);
        if (problem.isPresent()) {
            return ApiResponse.badRequest(problem.get());
        }
        entries.set(index, candidate);
        support.services().lootTables.markDirty();
        support.log("LootTable \"" + table.get().name + "\": Eintrag " + index
                + " per HTTP-API geändert");
        return ApiResponse.ok(json -> json.add("entry", entryJson(index, candidate)));
    }

    private ApiResponse entryDelete(ApiRequest request) {
        Optional<TableModel> table = table(request);
        if (table.isEmpty()) {
            return ApiResponse.notFound("Unbekannte LootTable: " + request.param("name"));
        }
        int index = index(request);
        List<EntryModel> entries = table.get().entries;
        if (index < 0 || index >= entries.size()) {
            return ApiResponse.notFound("Kein Eintrag mit Index " + index);
        }
        EntryModel removed = entries.remove(index);
        support.services().lootTables.markDirty();
        support.log("LootTable \"" + table.get().name + "\": Eintrag " + removed.item
                + " per HTTP-API gelöscht");
        return ApiResponse.ok();
    }

    // ==================== intern ====================

    /** Übernimmt alle optionalen Eintragsfelder aus dem Body. */
    private static void applyFields(ApiRequest request, EntryModel entry) {
        entry.custom = request.optBool("custom", entry.custom);
        entry.weight = request.optInt("weight", entry.weight);
        entry.minAmount = request.optInt("minAmount", entry.minAmount);
        entry.maxAmount = request.optInt("maxAmount", Math.max(entry.maxAmount, entry.minAmount));
        entry.enabled = request.optBool("enabled", entry.enabled);
        entry.unbreakable = request.optBool("unbreakable", entry.unbreakable);
        if (request.has("displayName")) {
            entry.displayName = request.requireString("displayName");
        }
        if (request.has("lore")) {
            entry.lore = stringList(request, "lore");
        }
        if (request.has("enchantments")) {
            entry.enchantments = stringList(request, "enchantments");
        }
    }

    private static List<String> stringList(ApiRequest request, String field) {
        JsonElement element = request.body().get(field);
        if (!element.isJsonArray()) {
            throw new ApiBadRequestException("Feld \"" + field + "\" muss eine Liste sein");
        }
        List<String> values = new ArrayList<>();
        element.getAsJsonArray().forEach(item -> values.add(item.getAsString()));
        return values;
    }

    private static JsonObject entryJson(int index, EntryModel entry) {
        JsonObject json = new JsonObject();
        json.addProperty("index", index);
        json.addProperty("item", entry.item);
        json.addProperty("custom", entry.custom);
        json.addProperty("weight", entry.weight);
        json.addProperty("minAmount", entry.minAmount);
        json.addProperty("maxAmount", entry.maxAmount);
        json.addProperty("enabled", entry.enabled);
        json.addProperty("unbreakable", entry.unbreakable);
        json.addProperty("displayName", entry.displayName);
        JsonArray lore = new JsonArray();
        entry.lore.forEach(lore::add);
        json.add("lore", lore);
        JsonArray enchantments = new JsonArray();
        entry.enchantments.forEach(enchantments::add);
        json.add("enchantments", enchantments);
        return json;
    }

    private Optional<TableModel> table(ApiRequest request) {
        return support.services().lootTables.table(request.param("name"));
    }

    private static int index(ApiRequest request) {
        try {
            return Integer.parseInt(request.param("index"));
        } catch (NumberFormatException e) {
            throw new ApiBadRequestException("Index muss eine Zahl sein: " + request.param("index"));
        }
    }
}
