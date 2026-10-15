package com.hungergames.platform.gui.loot;

import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.admin.AdminServices;
import com.hungergames.platform.gui.admin.ConfirmMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Liste aller LootTables: Linksklick öffnet, Rechtsklick testet,
 * Shift-Rechtsklick dupliziert, Shift-Linksklick löscht (Admin, mit
 * Bestätigung).
 */
public final class LootTableListMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public LootTableListMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 6, Component.text("LootTables"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        var repo = services.lootTables;
        boolean edit = LootMainMenu.canEdit(viewer, services);

        int slot = 0;
        for (String name : repo.tableNames()) {
            if (slot >= 45) {
                break;
            }
            addTable(slot++, name, edit);
        }
        set(49, Items.back(), click -> onBack.run());
    }

    private void addTable(int slot, String name, boolean edit) {
        var repo = services.lootTables;
        var table = repo.table(name).orElse(null);
        if (table == null) {
            return;
        }
        long invalid = table.entries.stream()
                .filter(entry -> repo.validateEntry(entry).isPresent())
                .count();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(table.entries.size() + " Einträge, Gesamtgewicht "
                + repo.totalWeight(table), NamedTextColor.GRAY));
        lore.add(Component.text("Verwendung: " + repo.usageOf(name), NamedTextColor.GRAY));
        lore.add(invalid == 0
                ? Component.text("✔ gültig", NamedTextColor.GREEN)
                : Component.text("⚠ " + invalid + " ungültige Einträge", NamedTextColor.RED));
        lore.add(Component.empty());
        lore.add(Component.text("Linksklick: öffnen", NamedTextColor.AQUA));
        lore.add(Component.text("Rechtsklick: testen", NamedTextColor.AQUA));
        if (edit) {
            lore.add(Component.text("Shift-Rechtsklick: duplizieren", NamedTextColor.AQUA));
            lore.add(Component.text("Shift-Linksklick: löschen", NamedTextColor.RED));
        }

        Material icon = name.equals("supply_drop") ? Material.CHEST_MINECART
                : name.contains("barrel") ? Material.BARREL
                : name.contains("shelf") ? Material.CHISELED_BOOKSHELF
                : Material.CHEST;

        set(slot, Items.of(icon, Component.text(name,
                        invalid == 0 ? NamedTextColor.YELLOW : NamedTextColor.RED), lore),
                click -> {
                    if (click.isShiftClick() && click.isLeftClick() && edit) {
                        new ConfirmMenu(viewer, "Tabelle \"" + name + "\" löschen",
                                List.of("Alle " + table.entries.size() + " Einträge werden entfernt.",
                                        "Wirksam erst nach dem Speichern."),
                                () -> {
                                    services.lootTables.deleteTable(name);
                                    viewer.sendMessage(Messages.ok("Tabelle \"" + name
                                            + "\" gelöscht (noch nicht gespeichert)."));
                                    open();
                                }, this::open).open();
                    } else if (click.isShiftClick() && click.isRightClick() && edit) {
                        services.anvilInput.open(viewer, Component.text("Name der Kopie"),
                                name + "_kopie",
                                input -> {
                                    if (!input.isBlank()) {
                                        services.lootTables.duplicateTable(name, input)
                                                .ifPresentOrElse(
                                                        error -> viewer.sendMessage(Messages.error(error)),
                                                        () -> viewer.sendMessage(Messages.ok(
                                                                "Tabelle dupliziert (noch nicht gespeichert).")));
                                    }
                                    open();
                                }, this::open);
                    } else if (click.isRightClick()) {
                        new LootTestMenu(viewer, services, name, this::open).open();
                    } else if (click.isLeftClick()) {
                        new LootTableDetailMenu(viewer, services, name, this::open).open();
                    }
                });
    }
}
