package com.hungergames.platform.gui.loot;

import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.admin.AdminServices;
import com.hungergames.platform.gui.admin.ConfirmMenu;
import com.hungergames.platform.loot.LootTableRepository.EntryModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Detailansicht einer LootTable: alle Einträge mit Gewicht/Chance/Meta.
 * Linksklick bearbeitet, Rechtsklick (de)aktiviert, Shift-Rechtsklick
 * dupliziert, Shift-Linksklick löscht (jeweils Admin).
 */
public final class LootTableDetailMenu extends Menu {

    private final AdminServices services;
    private final String tableName;
    private final Runnable onBack;

    public LootTableDetailMenu(Player viewer, AdminServices services, String tableName,
                               Runnable onBack) {
        super(viewer, 6, Component.text("LootTable: " + tableName));
        this.services = services;
        this.tableName = tableName;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        var repo = services.lootTables;
        var table = repo.table(tableName).orElse(null);
        if (table == null) {
            viewer.sendMessage(Messages.error("Tabelle \"" + tableName + "\" existiert nicht mehr."));
            onBack.run();
            return;
        }
        boolean edit = LootMainMenu.canEdit(viewer, services);
        int totalWeight = repo.totalWeight(table);

        int slot = 0;
        for (EntryModel entry : table.entries) {
            if (slot >= 45) {
                break;
            }
            addEntry(slot++, table.entries, entry, totalWeight, edit);
        }

        if (edit) {
            set(45, Items.simple(Material.EMERALD, "Eintrag hinzufügen",
                            "Neuer Eintrag (STONE, Gewicht 10)"),
                    click -> {
                        EntryModel entry = new EntryModel();
                        table.entries.add(entry);
                        services.lootTables.markDirty();
                        new LootEntryEditorMenu(viewer, services, tableName, entry, this::open).open();
                    });
            set(47, Items.of(repo.isDirty() ? Material.WRITABLE_BOOK : Material.BOOK,
                            Component.text("Speichern",
                                    repo.isDirty() ? NamedTextColor.GOLD : NamedTextColor.GRAY),
                            Component.text(repo.isDirty() ? "Schreibt alle Änderungen in die loot.yml"
                                    : "Keine ungespeicherten Änderungen", NamedTextColor.GRAY)),
                    click -> {
                        List<String> errors = repo.save(viewer.getName());
                        if (errors.isEmpty()) {
                            viewer.sendMessage(Messages.ok("Loot-Konfiguration gespeichert."));
                        } else {
                            errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
                        }
                        refresh();
                    });
        }
        set(51, Items.simple(Material.OBSERVER, "Testen",
                        "Simulationen, Test-Chest, Test-Give"),
                click -> new LootTestMenu(viewer, services, tableName, this::open).open());
        set(49, Items.back(), click -> onBack.run());
    }

    private void addEntry(int slot, List<EntryModel> entries, EntryModel entry,
                          int totalWeight, boolean edit) {
        Material icon = entry.custom ? Material.SPYGLASS
                : java.util.Objects.requireNonNullElse(Material.matchMaterial(entry.item), Material.BARRIER);
        var problem = services.lootTables.validateEntry(entry);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Anzahl: " + entry.minAmount
                + (entry.maxAmount != entry.minAmount ? "–" + entry.maxAmount : ""),
                NamedTextColor.GRAY));
        double chance = totalWeight > 0 && entry.enabled
                ? entry.weight * 100.0 / totalWeight : 0;
        lore.add(Component.text("Gewicht: " + entry.weight
                + String.format(Locale.ROOT, " (%.1f%%)", chance), NamedTextColor.GRAY));
        if (entry.displayName != null && !entry.displayName.isBlank()) {
            lore.add(Component.text("Name: " + entry.displayName, NamedTextColor.GRAY));
        }
        if (!entry.lore.isEmpty()) {
            lore.add(Component.text("Lore: " + entry.lore.size() + " Zeilen", NamedTextColor.GRAY));
        }
        if (!entry.enchantments.isEmpty()) {
            lore.add(Component.text("Enchantments: " + String.join(", ", entry.enchantments),
                    NamedTextColor.LIGHT_PURPLE));
        }
        if (entry.unbreakable) {
            lore.add(Component.text("Unzerstörbar", NamedTextColor.AQUA));
        }
        lore.add(entry.enabled
                ? Component.text("Aktiv", NamedTextColor.GREEN)
                : Component.text("Deaktiviert (Draft)", NamedTextColor.DARK_GRAY));
        problem.ifPresent(text -> lore.add(Component.text("⚠ " + text, NamedTextColor.RED)));
        if (edit) {
            lore.add(Component.empty());
            lore.add(Component.text("Links: bearbeiten | Rechts: an/aus", NamedTextColor.AQUA));
            lore.add(Component.text("Shift-Rechts: duplizieren | Shift-Links: löschen",
                    NamedTextColor.AQUA));
        }

        set(slot, Items.of(icon, Component.text(entry.item,
                        entry.enabled ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY), lore),
                edit ? click -> {
                    if (click.isShiftClick() && click.isLeftClick()) {
                        new ConfirmMenu(viewer, "Eintrag \"" + entry.item + "\" löschen",
                                List.of("Wirksam erst nach dem Speichern."),
                                () -> {
                                    entries.remove(entry);
                                    services.lootTables.markDirty();
                                    open();
                                }, this::open).open();
                    } else if (click.isShiftClick() && click.isRightClick()) {
                        entries.add(entry.copy());
                        services.lootTables.markDirty();
                        refresh();
                    } else if (click.isRightClick()) {
                        entry.enabled = !entry.enabled;
                        services.lootTables.markDirty();
                        refresh();
                    } else if (click.isLeftClick()) {
                        new LootEntryEditorMenu(viewer, services, tableName, entry, this::open).open();
                    }
                } : null);
    }
}
