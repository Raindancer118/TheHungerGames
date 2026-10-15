package com.hungergames.loot;

import com.hungergames.HungerGamesPlugin;
import com.hungergames.utils.Sender;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * Configuration loader for loot tables.
 * Reads loot.yml and provides access to loot pools and fill percentages.
 */
public class LootConfig {

    private static LootConfig instance;
    private final HungerGamesPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);
    private FileConfiguration lootConfig;

    // Container types we support
    public enum ContainerType {
        CHEST("chest"),
        COPPER_CHEST("copper-chest"),
        TRAPPED_CHEST("trapped-chest"),
        BARREL("barrel"),
        SHELF("shelf");

        private final String configKey;

        ContainerType(String configKey) {
            this.configKey = configKey;
        }

        public String getConfigKey() {
            return configKey;
        }

        /**
         * Get ContainerType from a Minecraft Material.
         */
        public static ContainerType fromMaterial(Material material) {
            String name = material.name().toUpperCase();

            // Check trapped chest first (avoid matching before regular chest)
            if (name.contains("TRAPPED_CHEST")) {
                return TRAPPED_CHEST;
            }
            // Copper chests (oxidized, waxed, etc.) - 1.21+
            if (name.contains("COPPER") && name.contains("CHEST")) {
                return COPPER_CHEST;
            }
            // Regular chest
            if (name.equals("CHEST")) {
                return CHEST;
            }
            // Barrel
            if (name.equals("BARREL")) {
                return BARREL;
            }
            // Chiseled bookshelf (stores books, 6 slots) - 1.20+
            if (name.contains("CHISELED") && name.contains("BOOKSHELF")) {
                return SHELF;
            }
            // Any other shelf-like container
            if (name.contains("SHELF")) {
                return SHELF;
            }
            return null;
        }
    }

    /**
     * Represents a single loot entry with item, weight, and amount range.
     * Optional: Anzeigename, Lore, Enchantments ("SHARPNESS:2"), Unbreakable.
     */
    public static class LootEntry {
        private final Material item;
        private final String customId; // For custom items
        private final boolean isCustom;
        private final int weight;
        private final int minAmount;
        private final int maxAmount;
        private final String displayName;
        private final List<String> lore;
        private final List<String> enchantments;
        private final boolean unbreakable;

        public LootEntry(Material item, int weight, int minAmount, int maxAmount) {
            this(item, null, false, weight, minAmount, maxAmount, null, List.of(), List.of(), false);
        }

        public LootEntry(String customId, int weight, int minAmount, int maxAmount) {
            this(null, customId, true, weight, minAmount, maxAmount, null, List.of(), List.of(), false);
        }

        public LootEntry(Material item, String customId, boolean isCustom, int weight,
                         int minAmount, int maxAmount, String displayName, List<String> lore,
                         List<String> enchantments, boolean unbreakable) {
            this.item = item;
            this.customId = customId;
            this.isCustom = isCustom;
            this.weight = weight;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.displayName = displayName;
            this.lore = List.copyOf(lore);
            this.enchantments = List.copyOf(enchantments);
            this.unbreakable = unbreakable;
        }

        public Material getItem() {
            return item;
        }

        public String getCustomId() {
            return customId;
        }

        public boolean isCustom() {
            return isCustom;
        }

        public int getWeight() {
            return weight;
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<String> getLore() {
            return lore;
        }

        public List<String> getEnchantments() {
            return enchantments;
        }

        public boolean isUnbreakable() {
            return unbreakable;
        }

        public int getRandomAmount(Random random) {
            if (minAmount == maxAmount) {
                return minAmount;
            }
            return random.nextInt(maxAmount - minAmount + 1) + minAmount;
        }
    }

    private LootConfig() {
        loadConfig();
    }

    public static LootConfig getInstance() {
        if (instance == null) {
            instance = new LootConfig();
        }
        return instance;
    }

    /**
     * Loads or reloads the loot configuration from disk.
     */
    public void reload() {
        loadConfig();
        Sender.log("Loot configuration reloaded.");
    }

    private void loadConfig() {
        // Save default loot.yml if it doesn't exist
        File lootFile = new File(plugin.getDataFolder(), "loot.yml");
        if (!lootFile.exists()) {
            plugin.saveResource("loot.yml", false);
        }

        lootConfig = YamlConfiguration.loadConfiguration(lootFile);
        Sender.debug("Loaded loot.yml configuration");
    }

    /**
     * Gets the fill percentage for a container type.
     * This determines how many slots (as a percentage) will be filled with loot.
     *
     * @param type Container type
     * @return Fill percentage (0-100)
     */
    public int getFillPercentage(ContainerType type) {
        return getFillPercentageByKey(type.getConfigKey());
    }

    /** Füllgrad (Prozent) für beliebige Loot-Tabellen-Keys, z. B. "supply_drop". */
    public int getFillPercentageByKey(String key) {
        return lootConfig.getInt("loot.fill-percentage." + key, 40);
    }

    /**
     * Gets the loot pool for a container type.
     *
     * @param type Container type
     * @return List of LootEntry objects representing the loot pool
     */
    public List<LootEntry> getLootPool(ContainerType type) {
        return getLootPoolByKey(type.getConfigKey());
    }

    /**
     * Loot-Pool für beliebige Tabellen-Keys unter {@code loot.<key>},
     * z. B. "supply_drop" für Kapitol-Lieferungen.
     */
    public List<LootEntry> getLootPoolByKey(String key) {
        List<LootEntry> pool = new ArrayList<>();

        List<?> entries = lootConfig.getList("loot." + key);
        if (entries == null) {
            Sender.warning("No loot pool found for loot table: " + key);
            return pool;
        }

        for (Object entry : entries) {
            if (entry instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) entry;

                String itemName = (String) map.get("item");
                if (itemName == null)
                    continue;

                // Deaktivierte Einträge (Editor-Drafts) überspringen
                if (Boolean.FALSE.equals(map.get("enabled")))
                    continue;

                // Check if this is a custom item
                boolean isCustom = map.containsKey("custom") &&
                        Boolean.TRUE.equals(map.get("custom"));

                int weight = 10; // Default weight
                if (map.containsKey("weight")) {
                    weight = ((Number) map.get("weight")).intValue();
                }

                int minAmount = 1;
                int maxAmount = 1;
                if (map.containsKey("amount")) {
                    Object amountObj = map.get("amount");
                    if (amountObj instanceof Number) {
                        minAmount = maxAmount = ((Number) amountObj).intValue();
                    } else if (amountObj instanceof String) {
                        String amountStr = (String) amountObj;
                        if (amountStr.contains("-")) {
                            String[] parts = amountStr.split("-");
                            minAmount = Integer.parseInt(parts[0].trim());
                            maxAmount = Integer.parseInt(parts[1].trim());
                        } else {
                            minAmount = maxAmount = Integer.parseInt(amountStr.trim());
                        }
                    }
                }

                String displayName = map.get("name") instanceof String name ? name : null;
                List<String> lore = stringList(map.get("lore"));
                List<String> enchantments = stringList(map.get("enchantments"));
                boolean unbreakable = Boolean.TRUE.equals(map.get("unbreakable"));

                if (isCustom) {
                    // Custom item - store the ID
                    pool.add(new LootEntry(null, itemName.toUpperCase(), true, weight,
                            minAmount, maxAmount, displayName, lore, enchantments, unbreakable));
                } else {
                    // Regular material
                    Material material;
                    try {
                        material = Material.valueOf(itemName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        Sender.warning("Invalid material in loot config: " + itemName);
                        continue;
                    }
                    pool.add(new LootEntry(material, null, false, weight,
                            minAmount, maxAmount, displayName, lore, enchantments, unbreakable));
                }
            }
        }

        Sender.debug("Loaded " + pool.size() + " loot entries for " + key);
        return pool;
    }

    /**
     * Gets the total weight of a loot pool (used for probability calculations).
     */
    public int getTotalWeight(List<LootEntry> pool) {
        return pool.stream().mapToInt(LootEntry::getWeight).sum();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
