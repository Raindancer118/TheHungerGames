package com.hungergames.platform.loot;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.loot.LootConfig;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.log.RoundLogService;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Editierbares Modell der Loot-Konfiguration ({@code loot.yml}) für den
 * Ingame-Editor: lädt alle Tabellen, hält ungespeicherte Änderungen im
 * Speicher (Dirty-Flag) und schreibt sie erst bei explizitem Speichern zurück
 * — optional mit Backup. Nach dem Speichern lädt {@link LootConfig} neu,
 * damit die Laufzeit die Änderungen übernimmt.
 */
public final class LootTableRepository {

    /** Bekannte Verwendungszwecke von Tabellen (für die GUI-Anzeige). */
    private static final Map<String, String> USAGE = Map.of(
            "chest", "Arena-Kisten (normal)",
            "copper-chest", "Arena-Kisten (Kupfer)",
            "trapped-chest", "Arena-Kisten (Falle)",
            "barrel", "Arena-Fässer",
            "shelf", "Arena-Regale",
            "supply_drop", "Supply Drops");

    /** Ein bearbeitbarer Loot-Eintrag. */
    public static final class EntryModel {
        public String item = "STONE";
        public boolean custom;
        public int weight = 10;
        public int minAmount = 1;
        public int maxAmount = 1;
        public boolean enabled = true;
        public String displayName;
        public List<String> lore = new ArrayList<>();
        public List<String> enchantments = new ArrayList<>();
        public boolean unbreakable;

        public EntryModel copy() {
            EntryModel copy = new EntryModel();
            copy.item = item;
            copy.custom = custom;
            copy.weight = weight;
            copy.minAmount = minAmount;
            copy.maxAmount = maxAmount;
            copy.enabled = enabled;
            copy.displayName = displayName;
            copy.lore = new ArrayList<>(lore);
            copy.enchantments = new ArrayList<>(enchantments);
            copy.unbreakable = unbreakable;
            return copy;
        }
    }

    /** Eine bearbeitbare Loot-Tabelle. */
    public static final class TableModel {
        public final String name;
        public final List<EntryModel> entries = new ArrayList<>();

        TableModel(String name) {
            this.name = name;
        }
    }

    private final File lootFile;
    private final Logger logger;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final RoundLogService roundLog;

    private final Map<String, TableModel> tables = new LinkedHashMap<>();
    private final Set<String> deletedTables = new HashSet<>();
    private boolean dirty;

    public LootTableRepository(File lootFile, Logger logger, ConfigurationService config,
                               HgSettings settings, RoundLogService roundLog) {
        this.lootFile = lootFile;
        this.logger = logger;
        this.config = config;
        this.settings = settings;
        this.roundLog = roundLog;
        loadFromDisk();
    }

    // ==================== Laden / Speichern ====================

    /** Lädt alle Tabellen frisch von der Platte (verwirft ungespeicherte Änderungen). */
    public synchronized void loadFromDisk() {
        tables.clear();
        deletedTables.clear();
        dirty = false;
        if (!lootFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(lootFile);
        var lootSection = yaml.getConfigurationSection("loot");
        if (lootSection == null) {
            return;
        }
        for (String key : lootSection.getKeys(false)) {
            if (key.equals("fill-percentage")) {
                continue;
            }
            List<?> rawEntries = lootSection.getList(key);
            if (rawEntries == null) {
                continue;
            }
            TableModel table = new TableModel(key);
            for (Object raw : rawEntries) {
                if (raw instanceof Map<?, ?> map) {
                    table.entries.add(parseEntry(map));
                }
            }
            tables.put(key, table);
        }
    }

    /**
     * Schreibt alle Tabellen in die loot.yml (Fill-Percentages und unbekannte
     * Schlüssel bleiben erhalten) und lädt die Laufzeit-Konfiguration neu.
     *
     * @return Fehlerliste; leer bei Erfolg
     */
    public synchronized List<String> save(String actor) {
        List<String> problems = validateAll();
        if (!problems.isEmpty()) {
            return problems;
        }
        if (config.get(settings.lootEditorBackup) && lootFile.exists()) {
            File backup = new File(lootFile.getParentFile(), "loot.yml.bak-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            try {
                Files.copy(lootFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                return List.of("Backup fehlgeschlagen — nicht gespeichert: " + e.getMessage());
            }
        }
        YamlConfiguration yaml = lootFile.exists()
                ? YamlConfiguration.loadConfiguration(lootFile) : new YamlConfiguration();
        for (String deleted : deletedTables) {
            yaml.set("loot." + deleted, null);
        }
        for (TableModel table : tables.values()) {
            yaml.set("loot." + table.name, table.entries.stream()
                    .map(LootTableRepository::serializeEntry)
                    .toList());
        }
        try {
            yaml.save(lootFile);
        } catch (IOException e) {
            return List.of("loot.yml konnte nicht geschrieben werden: " + e.getMessage());
        }
        deletedTables.clear();
        dirty = false;
        LootConfig.getInstance().reload();
        roundLog.log("LOOT", actor + " speichert die Loot-Konfiguration ("
                + tables.size() + " Tabellen)");
        return List.of();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    // ==================== Tabellen ====================

    public List<String> tableNames() {
        return List.copyOf(tables.keySet());
    }

    public Optional<TableModel> table(String name) {
        return Optional.ofNullable(tables.get(name));
    }

    /** @return Fehlermeldung oder leer bei Erfolg */
    public Optional<String> createTable(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (key.isEmpty() || key.equals("fill-percentage")) {
            return Optional.of("Ungültiger Tabellenname.");
        }
        if (tables.containsKey(key)) {
            return Optional.of("Tabelle \"" + key + "\" existiert bereits.");
        }
        tables.put(key, new TableModel(key));
        deletedTables.remove(key);
        dirty = true;
        return Optional.empty();
    }

    public Optional<String> duplicateTable(String source, String newName) {
        TableModel original = tables.get(source);
        if (original == null) {
            return Optional.of("Tabelle \"" + source + "\" existiert nicht.");
        }
        Optional<String> error = createTable(newName);
        if (error.isPresent()) {
            return error;
        }
        TableModel copy = tables.get(newName.trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
        original.entries.forEach(entry -> copy.entries.add(entry.copy()));
        return Optional.empty();
    }

    public boolean deleteTable(String name) {
        if (tables.remove(name) == null) {
            return false;
        }
        deletedTables.add(name);
        dirty = true;
        return true;
    }

    /** Anzeigename des Verwendungszwecks, falls bekannt. */
    public String usageOf(String tableName) {
        String supplyTable = config.get(settings.supplyDropLootTable);
        if (tableName.equals(supplyTable)) {
            return USAGE.getOrDefault(tableName, "Supply Drops");
        }
        return USAGE.getOrDefault(tableName, "—");
    }

    // ==================== Validierung ====================

    /** Prüft alle Tabellen; leere Liste = alles gültig. */
    public List<String> validateAll() {
        List<String> problems = new ArrayList<>();
        for (TableModel table : tables.values()) {
            for (int i = 0; i < table.entries.size(); i++) {
                validateEntry(table.entries.get(i)).ifPresent(problem ->
                        problems.add(table.name + ": " + problem));
            }
        }
        return problems;
    }

    /**
     * Prüft einen Eintrag. Deaktivierte Einträge dürfen als Drafts ungültig
     * bleiben — sie landen nie im Spiel.
     */
    public Optional<String> validateEntry(EntryModel entry) {
        if (!entry.enabled) {
            return Optional.empty();
        }
        if (entry.custom) {
            if (!com.hungergames.platform.sponsor.ShopItemParser.CUSTOM_ITEM_IDS
                    .contains(entry.item.toUpperCase(Locale.ROOT))) {
                return Optional.of("Unbekanntes Custom-Item: " + entry.item);
            }
        } else if (Material.matchMaterial(entry.item) == null) {
            return Optional.of("Unbekanntes Material: " + entry.item);
        }
        if (entry.minAmount < 1) {
            return Optional.of(entry.item + ": Min-Anzahl muss ≥ 1 sein");
        }
        if (entry.maxAmount < entry.minAmount) {
            return Optional.of(entry.item + ": Max-Anzahl muss ≥ Min-Anzahl sein");
        }
        if (entry.weight <= 0) {
            return Optional.of(entry.item + ": Gewicht muss > 0 sein");
        }
        for (String spec : entry.enchantments) {
            String[] parts = spec.split(":");
            if (parts[0].isBlank()) {
                return Optional.of(entry.item + ": leeres Enchantment");
            }
            if (parts.length >= 2) {
                try {
                    if (Integer.parseInt(parts[1].trim()) < 1) {
                        return Optional.of(entry.item + ": Enchantment-Level muss ≥ 1 sein");
                    }
                } catch (NumberFormatException e) {
                    return Optional.of(entry.item + ": Enchantment-Level \"" + parts[1]
                            + "\" ist keine Zahl");
                }
            }
        }
        return Optional.empty();
    }

    public int totalWeight(TableModel table) {
        return table.entries.stream().filter(e -> e.enabled).mapToInt(e -> e.weight).sum();
    }

    /** Konvertiert das aktuelle Modell (inkl. ungespeicherter Änderungen) in Laufzeit-Einträge. */
    public List<LootConfig.LootEntry> toRuntimePool(TableModel table) {
        List<LootConfig.LootEntry> pool = new ArrayList<>();
        for (EntryModel entry : table.entries) {
            if (!entry.enabled) {
                continue;
            }
            Material material = entry.custom ? null : Material.matchMaterial(entry.item);
            if (!entry.custom && material == null) {
                continue;
            }
            pool.add(new LootConfig.LootEntry(material,
                    entry.custom ? entry.item.toUpperCase(Locale.ROOT) : null, entry.custom,
                    entry.weight, entry.minAmount, entry.maxAmount, entry.displayName,
                    entry.lore, entry.enchantments, entry.unbreakable));
        }
        return pool;
    }

    // ==================== (De-)Serialisierung ====================

    private static EntryModel parseEntry(Map<?, ?> map) {
        EntryModel entry = new EntryModel();
        Object rawItem = map.get("item");
        entry.item = rawItem != null ? String.valueOf(rawItem) : "STONE";
        entry.custom = Boolean.TRUE.equals(map.get("custom"));
        entry.enabled = !Boolean.FALSE.equals(map.get("enabled"));
        entry.unbreakable = Boolean.TRUE.equals(map.get("unbreakable"));
        if (map.get("weight") instanceof Number number) {
            entry.weight = number.intValue();
        }
        Object amount = map.get("amount");
        if (amount instanceof Number number) {
            entry.minAmount = entry.maxAmount = number.intValue();
        } else if (amount instanceof String range) {
            String[] parts = range.split("-");
            try {
                entry.minAmount = Integer.parseInt(parts[0].trim());
                entry.maxAmount = parts.length >= 2
                        ? Integer.parseInt(parts[1].trim()) : entry.minAmount;
            } catch (NumberFormatException ignored) {
                // Defaults behalten
            }
        }
        if (map.get("name") instanceof String name) {
            entry.displayName = name;
        }
        if (map.get("lore") instanceof List<?> lore) {
            lore.forEach(line -> entry.lore.add(String.valueOf(line)));
        }
        if (map.get("enchantments") instanceof List<?> enchantments) {
            enchantments.forEach(spec -> entry.enchantments.add(String.valueOf(spec)));
        }
        return entry;
    }

    private static Map<String, Object> serializeEntry(EntryModel entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("item", entry.item.toUpperCase(Locale.ROOT));
        if (entry.custom) {
            map.put("custom", true);
        }
        map.put("weight", entry.weight);
        map.put("amount", entry.minAmount == entry.maxAmount
                ? entry.minAmount : entry.minAmount + "-" + entry.maxAmount);
        if (!entry.enabled) {
            map.put("enabled", false);
        }
        if (entry.displayName != null && !entry.displayName.isBlank()) {
            map.put("name", entry.displayName);
        }
        if (!entry.lore.isEmpty()) {
            map.put("lore", List.copyOf(entry.lore));
        }
        if (!entry.enchantments.isEmpty()) {
            map.put("enchantments", List.copyOf(entry.enchantments));
        }
        if (entry.unbreakable) {
            map.put("unbreakable", true);
        }
        return map;
    }
}
