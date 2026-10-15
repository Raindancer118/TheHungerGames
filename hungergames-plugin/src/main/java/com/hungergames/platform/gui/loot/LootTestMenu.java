package com.hungergames.platform.gui.loot;

import com.hungergames.loot.LootConfig.LootEntry;
import com.hungergames.loot.LootManager;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.admin.AdminServices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loot-Test für eine Tabelle: Simulationen mit Häufigkeitsübersicht,
 * Test-Chest am Standort, Test-Loot ins Inventar. Nutzt das aktuelle Modell
 * inklusive ungespeicherter Änderungen; verändert keine Rundendaten.
 */
public final class LootTestMenu extends Menu {

    private final AdminServices services;
    private final String tableName;
    private final Runnable onBack;
    private final LootManager lootManager = new LootManager();

    private Map<String, Integer> lastSimulation = Map.of();
    private int lastRolls;

    public LootTestMenu(Player viewer, AdminServices services, String tableName, Runnable onBack) {
        super(viewer, 6, Component.text("Loot-Test: " + tableName));
        this.services = services;
        this.tableName = tableName;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        int maxRolls = services.config.get(services.settings.lootEditorMaxTestRolls);

        set(4, Items.simple(Material.OBSERVER, "Loot-Test: " + tableName,
                "Simulationen verfälschen keine Rundendaten",
                "Nutzt ungespeicherte Änderungen"));

        set(10, Items.simple(Material.LIME_DYE, "1× würfeln"),
                click -> simulate(1));
        set(11, Items.simple(Material.LIME_DYE, "10× simulieren"),
                click -> simulate(Math.min(10, maxRolls)));
        set(12, Items.simple(Material.LIME_DYE, Math.min(100, maxRolls) + "× simulieren"),
                click -> simulate(Math.min(100, maxRolls)));

        if (services.config.get(services.settings.lootEditorAllowTestChest)) {
            set(14, Items.simple(Material.CHEST, "Test-Chest am Standort",
                            "Platziert eine gefüllte Kiste", "an deiner Position"),
                    click -> placeTestChest());
        }
        if (services.config.get(services.settings.lootEditorAllowTestGive)) {
            set(15, Items.simple(Material.BUNDLE, "Test-Loot ins Inventar",
                            "Gibt dir 5 zufällige Items"),
                    click -> giveTestLoot());
        }

        // Häufigkeitsübersicht der letzten Simulation
        if (!lastSimulation.isEmpty()) {
            int slot = 27;
            set(19, Items.simple(Material.MAP, "Ergebnis: " + lastRolls + " Würfe",
                    "Häufigkeiten der gezogenen Items"));
            for (Map.Entry<String, Integer> result : lastSimulation.entrySet()) {
                if (slot >= 45) {
                    break;
                }
                Material icon = java.util.Objects.requireNonNullElse(
                        Material.matchMaterial(result.getKey()), Material.SPYGLASS);
                double percent = result.getValue() * 100.0 / Math.max(1, lastRolls);
                set(slot++, Items.simple(icon, result.getKey(),
                        result.getValue() + "× gezogen ("
                                + String.format(Locale.ROOT, "%.1f%%", percent) + ")"));
            }
        }

        set(49, Items.back(), click -> onBack.run());
    }

    private List<LootEntry> currentPool() {
        return services.lootTables.table(tableName)
                .map(table -> services.lootTables.toRuntimePool(table))
                .orElse(List.of());
    }

    private void simulate(int rolls) {
        List<LootEntry> pool = currentPool();
        List<LootEntry> drawn = lootManager.rollEntries(pool, rolls);
        if (drawn.isEmpty()) {
            viewer.sendMessage(Messages.error("Tabelle ist leer oder hat Gesamtgewicht 0."));
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LootEntry entry : drawn) {
            String key = entry.isCustom() ? entry.getCustomId() : entry.getItem().name();
            counts.merge(key, 1, Integer::sum);
        }
        lastSimulation = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(LinkedHashMap::new,
                        (map, e) -> map.put(e.getKey(), e.getValue()), Map::putAll);
        lastRolls = rolls;
        services.roundLog.log("LOOT", viewer.getName() + " simuliert " + rolls
                + " Würfe auf \"" + tableName + "\" (Test)");
        refresh();
    }

    private void placeTestChest() {
        Block block = viewer.getLocation().getBlock();
        if (block.getType() != Material.AIR) {
            block = block.getRelative(0, 1, 0);
        }
        if (block.getType() != Material.AIR) {
            viewer.sendMessage(Messages.error("Kein Platz für eine Test-Chest an deiner Position."));
            return;
        }
        block.setType(Material.CHEST);
        if (block.getState() instanceof Container container) {
            int slots = Math.max(1, container.getInventory().getSize()
                    * com.hungergames.loot.LootConfig.getInstance()
                    .getFillPercentageByKey(tableName) / 100);
            int filled = lootManager.fillWithLoot(container.getInventory(), currentPool(), slots);
            if (filled <= 0) {
                viewer.sendMessage(Messages.error("Tabelle ist leer oder hat Gesamtgewicht 0."));
                block.setType(Material.AIR);
                return;
            }
            viewer.sendMessage(Messages.ok("Test-Chest mit " + filled + " Items platziert."));
            services.roundLog.log("LOOT", viewer.getName() + " platziert Test-Chest (\""
                    + tableName + "\")", block.getLocation());
        }
    }

    private void giveTestLoot() {
        List<LootEntry> drawn = lootManager.rollEntries(currentPool(), 5);
        if (drawn.isEmpty()) {
            viewer.sendMessage(Messages.error("Tabelle ist leer oder hat Gesamtgewicht 0."));
            return;
        }
        int given = 0;
        for (LootEntry entry : drawn) {
            ItemStack item = lootManager.createItemFromEntry(entry);
            if (item != null) {
                viewer.getInventory().addItem(item).values().forEach(rest ->
                        viewer.getWorld().dropItemNaturally(viewer.getLocation(), rest));
                given++;
            }
        }
        viewer.sendMessage(Messages.ok(given + " Test-Items erhalten."));
    }
}
