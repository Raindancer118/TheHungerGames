package com.hungergames.loot;

import com.hungergames.GameState;
import com.hungergames.items.Fiendfinder;
import com.hungergames.loot.LootConfig.ContainerType;
import com.hungergames.loot.LootConfig.LootEntry;
import com.hungergames.utils.ConfigManager;
import com.hungergames.utils.Sender;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import io.papermc.paper.block.TileStateInventoryHolder;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages loot generation and container filling for the arena.
 * Scans the schematic area for containers and fills them based on loot tables.
 */
public class LootManager {

    private final LootConfig config = LootConfig.getInstance();
    private final Random random = new Random();

    /**
     * Fills all containers in the arena with loot.
     * Call this when entering the READY phase.
     */
    public void fillArenaContainers() {
        Location arenaCenter = GameState.getInstance().getArenaCenter();
        if (arenaCenter == null) {
            Sender.error("Cannot fill arena containers: arena center is null");
            return;
        }

        World world = arenaCenter.getWorld();
        if (world == null) {
            Sender.error("Cannot fill arena containers: world is null");
            return;
        }

        // Scan radius around arena center for containers
        // Values are configurable in config.yml
        int scanRadius = ConfigManager.getInstance().getLootScanRadius();
        int yRange = ConfigManager.getInstance().getLootScanYRange();

        int centerX = arenaCenter.getBlockX();
        int centerY = arenaCenter.getBlockY();
        int centerZ = arenaCenter.getBlockZ();

        Sender.log("Scanning for containers around " + centerX + ", " + centerY + ", " + centerZ + " (radius: "
                + scanRadius + ")");

        Map<ContainerType, Integer> containerCounts = new HashMap<>();
        int totalFilled = 0;
        Set<String> unrecognizedContainers = new HashSet<>();

        // Scan the area for containers
        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int y = -yRange; y <= yRange; y++) { // Vertical range from config
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);
                    Material material = block.getType();

                    // Get block state ONCE and reuse it
                    var blockState = block.getState();

                    // Alles mit einem Block-Inventar zählt: Truhen/Fässer sind
                    // `Container`, Regale (Shelf) und Chiseled Bookshelves sind es
                    // NICHT — sie sind nur TileStateInventoryHolder (kein Lockable).
                    // Mit `instanceof Container` wurden Regale schlicht übersprungen.
                    if (blockState instanceof TileStateInventoryHolder holder) {
                        ContainerType type = ContainerType.fromMaterial(material);
                        if (type != null) {
                            fillContainer(holder, type);
                            containerCounts.merge(type, 1, Integer::sum);
                            totalFilled++;
                        } else {
                            // Log unrecognized container types
                            unrecognizedContainers.add(material.name());
                        }
                    }
                }
            }
        }

        // Log unrecognized containers
        if (!unrecognizedContainers.isEmpty()) {
            Sender.warning("Unrecognized container materials: " + unrecognizedContainers);
        }

        // Log results
        if (totalFilled == 0) {
            Sender.warning("No containers found to fill! Check if arena is generated.");
        } else {
            Sender.log("Filled " + totalFilled + " containers with loot:");
            for (Map.Entry<ContainerType, Integer> entry : containerCounts.entrySet()) {
                Sender.log("  - " + entry.getKey().name() + ": " + entry.getValue());
            }
        }
    }

    /**
     * Fills a single container with loot based on its type.
     *
     * @param container Truhe, Fass, Regal oder Chiseled Bookshelf — alles, was ein
     *                  Block-Inventar hat. Regale sind ausdrücklich kein
     *                  {@link org.bukkit.block.Container}, deshalb der allgemeinere Typ.
     */
    private void fillContainer(TileStateInventoryHolder container, ContainerType type) {
        Inventory inventory = container.getInventory();

        // Safety check: specific for DoubleChests (shared inventory)
        // If we already filled this inventory via the other chest half, don't clear and
        // refill it!
        // This prevents the second half from nuking the items we just put in.
        if (!inventory.isEmpty()) {
            Sender.debug("Skipping " + type.name() + " at " + formatLocation(container.getLocation())
                    + " - already contains items (DoubleChest?)");
            return;
        }

        // Clear existing contents
        inventory.clear();

        // Get loot pool for this container type
        List<LootEntry> lootPool = config.getLootPool(type);
        if (lootPool.isEmpty()) {
            Sender.warning("Empty loot pool for container type: " + type.name());
            return;
        }

        // Chiseled Bookshelves nehmen ausschließlich Bücher an — alles andere
        // lehnt das Inventar still ab und das Regal bliebe leer.
        if (container instanceof ChiseledBookshelf) {
            lootPool = lootPool.stream().filter(LootManager::isBookLike).toList();
            if (lootPool.isEmpty()) {
                Sender.warning("Chiseled Bookshelf at " + formatLocation(container.getLocation())
                        + " übersprungen: die Tabelle \"" + type.getConfigKey() + "\" enthält keine "
                        + "Bücher, und Chiseled Bookshelves nehmen nur Bücher an.");
                return;
            }
        }

        // Calculate how many slots to fill
        int inventorySize = inventory.getSize();
        int fillPercentage = config.getFillPercentage(type);
        int slotsToFill = Math.max(1, (inventorySize * fillPercentage) / 100);

        // Regale haben sehr wenige Slots (Shelf 3, Chiseled Bookshelf 6) und zeigen
        // ihren Inhalt sichtbar an — nie mehr Slots füllen, als es gibt.
        slotsToFill = Math.max(1, Math.min(inventorySize, slotsToFill));

        int filled = fillWithLoot(inventory, lootPool, slotsToFill);
        if (filled < 0) {
            Sender.warning("Total weight is 0 for container type: " + type.name());
            return;
        }

        // IMPORTANT: Do NOT call container.update() here!
        // Container inventories in Bukkit/Paper are LIVE-backed by the tile entity.
        // setItem() modifications are applied immediately to the world.
        // Calling update() would actually OVERWRITE the live inventory with the
        // snapshot's data (which was empty when we called getState()).
        // This was causing all containers to be emptied after filling!

        Sender.debug("Filled " + type.name() + " at " + formatLocation(container.getLocation()) +
                " with " + filled + " items");

        // Verification: Check if items are really there
        // Read from a FRESH block state to verify persistence
        Block block = container.getBlock();
        if (block.getState() instanceof TileStateInventoryHolder freshContainer) {
            ItemStack[] currentContents = freshContainer.getInventory().getContents();
            long actualItems = Arrays.stream(currentContents).filter(Objects::nonNull).count();

            if (actualItems == 0 && filled > 0) {
                Sender.error("CRITICAL: Container at " + formatLocation(container.getLocation())
                        + " is empty after filling! Tile entity issue?");
            } else {
                // Log contents for debugging
                String contentStr = Arrays.stream(currentContents)
                        .filter(Objects::nonNull)
                        .map(is -> is.getType() + "x" + is.getAmount())
                        .collect(Collectors.joining(", "));
                Sender.debug("Verified contents: [" + contentStr + "]");
            }
        }
    }

    /**
     * Füllt zufällige Slots eines Inventars gewichtet aus einem Loot-Pool.
     * Wiederverwendbar für Arena-Container, Supply Drops und Loot-Tests.
     *
     * @return Anzahl gefüllter Slots, oder -1 wenn das Gesamtgewicht 0 ist
     */
    public int fillWithLoot(Inventory inventory, List<LootEntry> lootPool, int slotsToFill) {
        int totalWeight = config.getTotalWeight(lootPool);
        if (totalWeight <= 0) {
            return -1;
        }
        int inventorySize = inventory.getSize();
        Set<Integer> usedSlots = new HashSet<>();
        int filled = 0;

        while (filled < slotsToFill && usedSlots.size() < inventorySize) {
            int slot;
            int attempts = 0;
            do {
                slot = random.nextInt(inventorySize);
                attempts++;
            } while (usedSlots.contains(slot) && attempts < 100);

            if (usedSlots.contains(slot)) {
                break; // Safety exit
            }
            usedSlots.add(slot);

            LootEntry selectedEntry = selectWeightedRandom(lootPool, totalWeight);
            if (selectedEntry == null) {
                continue;
            }
            ItemStack item = createItemFromEntry(selectedEntry);
            if (item == null) {
                continue;
            }
            inventory.setItem(slot, item);
            filled++;
        }
        return filled;
    }

    /**
     * Creates an ItemStack from a LootEntry, handling both regular and custom
     * items.
     */
    public ItemStack createItemFromEntry(LootEntry entry) {
        int amount = entry.getRandomAmount(random);

        if (entry.isCustom()) {
            // Custom-Items zentral über die Registry bauen (Fiendfinder + alle
            // Sponsor-Items wie Rauchbombe, Medikit, Hermes' Stiefel …).
            String customId = entry.getCustomId();
            ItemStack custom = com.hungergames.items.CustomItems.create(customId);
            if (custom == null) {
                Sender.warning("Unknown custom item ID: " + customId);
                return null;
            }
            custom.setAmount(Math.max(1, amount));
            return custom;
        } else {
            // Regular material
            return applyMeta(new ItemStack(entry.getItem(), amount), entry);
        }
    }

    /** Wendet optionale Editor-Metadaten (Name, Lore, Enchantments, Unbreakable) an. */
    private ItemStack applyMeta(ItemStack item, LootEntry entry) {
        boolean hasMeta = entry.getDisplayName() != null || !entry.getLore().isEmpty()
                || !entry.getEnchantments().isEmpty() || entry.isUnbreakable();
        if (!hasMeta) {
            return item;
        }
        var meta = item.getItemMeta();
        if (meta != null) {
            var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection();
            if (entry.getDisplayName() != null) {
                meta.displayName(legacy.deserialize(entry.getDisplayName())
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            }
            if (!entry.getLore().isEmpty()) {
                meta.lore(entry.getLore().stream()
                        .map(line -> legacy.deserialize(line).decoration(
                                net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                        .map(c -> (net.kyori.adventure.text.Component) c)
                        .toList());
            }
            if (entry.isUnbreakable()) {
                meta.setUnbreakable(true);
            }
            item.setItemMeta(meta);
        }
        for (String spec : entry.getEnchantments()) {
            String[] parts = spec.split(":");
            var enchantment = org.bukkit.Registry.ENCHANTMENT.get(
                    org.bukkit.NamespacedKey.minecraft(parts[0].trim().toLowerCase(java.util.Locale.ROOT)));
            if (enchantment == null) {
                Sender.warning("Unbekanntes Enchantment im Loot: " + spec);
                continue;
            }
            int level = 1;
            if (parts.length >= 2) {
                try {
                    level = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    // Level 1 als Fallback
                }
            }
            item.addUnsafeEnchantment(enchantment, level);
        }
        return item;
    }

    /**
     * Simuliert Würfe auf einem Pool (für den Loot-Test) — verändert nichts.
     *
     * @return die gezogenen Einträge (leer bei leerem Pool/Gewicht 0)
     */
    public List<LootEntry> rollEntries(List<LootEntry> pool, int rolls) {
        int totalWeight = config.getTotalWeight(pool);
        if (totalWeight <= 0 || pool.isEmpty()) {
            return List.of();
        }
        List<LootEntry> result = new ArrayList<>(rolls);
        for (int i = 0; i < rolls; i++) {
            LootEntry entry = selectWeightedRandom(pool, totalWeight);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Selects a random loot entry based on weights.
     */
    private LootEntry selectWeightedRandom(List<LootEntry> pool, int totalWeight) {
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (LootEntry entry : pool) {
            cumulative += entry.getWeight();
            if (roll < cumulative) {
                return entry;
            }
        }

        // Fallback to last entry
        return pool.get(pool.size() - 1);
    }

    private String formatLocation(Location loc) {
        return String.format("X:%d Y:%d Z:%d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** Materialien, die ein Chiseled Bookshelf überhaupt aufnimmt. */
    private static boolean isBookLike(LootEntry entry) {
        Material material = entry.getItem();
        return material == Material.BOOK
                || material == Material.WRITABLE_BOOK
                || material == Material.WRITTEN_BOOK
                || material == Material.ENCHANTED_BOOK
                || material == Material.KNOWLEDGE_BOOK;
    }
}
