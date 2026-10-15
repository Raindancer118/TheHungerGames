package com.hungergames.platform.gui.loot;

import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.NumberEditorMenu;
import com.hungergames.platform.gui.admin.AdminServices;
import com.hungergames.platform.loot.LootTableRepository.EntryModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor für einen einzelnen Loot-Eintrag: Material, Anzahl, Gewicht, Name,
 * Lore, Enchantments, Unzerstörbar, Aktiv. Änderungen wirken sofort im
 * Modell (Dirty-Flag); persistent werden sie erst beim Speichern der Tabelle.
 */
public final class LootEntryEditorMenu extends Menu {

    private final AdminServices services;
    private final String tableName;
    private final EntryModel entry;
    private final Runnable onBack;

    public LootEntryEditorMenu(Player viewer, AdminServices services, String tableName,
                               EntryModel entry, Runnable onBack) {
        super(viewer, 5, Component.text("Eintrag: " + entry.item));
        this.services = services;
        this.tableName = tableName;
        this.entry = entry;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        var problem = services.lootTables.validateEntry(entry);

        // Vorschau
        Material icon = entry.custom ? Material.SPYGLASS
                : java.util.Objects.requireNonNullElse(Material.matchMaterial(entry.item), Material.BARRIER);
        List<Component> preview = new ArrayList<>();
        preview.add(Component.text("Tabelle: " + tableName, NamedTextColor.GRAY));
        problem.ifPresentOrElse(
                text -> preview.add(Component.text("⚠ " + text, NamedTextColor.RED)),
                () -> preview.add(Component.text("✔ gültig", NamedTextColor.GREEN)));
        set(4, Items.of(icon, Component.text(entry.item, NamedTextColor.YELLOW), preview));

        // Material / Custom-Item — Linksklick: GUI-Auswahl, Shift-Klick: Texteingabe
        set(10, Items.simple(Material.CHEST, "Item: " + entry.item,
                        entry.custom ? "Custom-Item" : "Material",
                        "Linksklick: aus Liste wählen (GUI)",
                        "Shift-Klick: per Texteingabe"),
                click -> {
                    if (click.isShiftClick()) {
                        services.anvilInput.open(viewer, Component.text("Material"), entry.item,
                                input -> {
                                    String value = input.trim().toUpperCase(Locale.ROOT);
                                    if (!value.isEmpty()) {
                                        entry.item = value;
                                        entry.custom = com.hungergames.platform.sponsor.ShopItemParser
                                                .CUSTOM_ITEM_IDS.contains(value);
                                        markDirty();
                                    }
                                    open();
                                }, this::open);
                    } else {
                        new LootItemPickerMenu(viewer, services, entry, this::open).open();
                    }
                });

        // Anzahl
        set(11, Items.simple(Material.REPEATER, "Min-Anzahl: " + entry.minAmount, "Klick: ändern"),
                click -> new NumberEditorMenu(viewer, "Min-Anzahl", entry.minAmount, true,
                        draft -> draft < 1 ? "Muss ≥ 1 sein" : null,
                        applied -> {
                            entry.minAmount = (int) Math.round(applied);
                            entry.maxAmount = Math.max(entry.maxAmount, entry.minAmount);
                            markDirty();
                            open();
                        }, this::open, services.anvilInput).open());
        set(12, Items.simple(Material.REPEATER, "Max-Anzahl: " + entry.maxAmount, "Klick: ändern"),
                click -> new NumberEditorMenu(viewer, "Max-Anzahl", entry.maxAmount, true,
                        draft -> draft < entry.minAmount ? "Muss ≥ Min-Anzahl sein" : null,
                        applied -> {
                            entry.maxAmount = (int) Math.round(applied);
                            markDirty();
                            open();
                        }, this::open, services.anvilInput).open());

        // Gewicht
        set(13, Items.simple(Material.COMPARATOR, "Gewicht: " + entry.weight,
                        "Relative Chance im Pool", "Klick: ändern"),
                click -> new NumberEditorMenu(viewer, "Gewicht", entry.weight, true,
                        draft -> draft < 1 ? "Muss ≥ 1 sein" : null,
                        applied -> {
                            entry.weight = (int) Math.round(applied);
                            markDirty();
                            open();
                        }, this::open, services.anvilInput).open());

        // Name
        set(14, Items.simple(Material.NAME_TAG, "Name: "
                                + (entry.displayName == null || entry.displayName.isBlank()
                                ? "—" : entry.displayName),
                        "Eigener Anzeigename (§-Codes)", "Klick: ändern, leer = entfernen"),
                click -> services.anvilInput.open(viewer, Component.text("Anzeigename"),
                        entry.displayName == null ? "" : entry.displayName,
                        input -> {
                            entry.displayName = input.isBlank() ? null : input;
                            markDirty();
                            open();
                        }, this::open));

        // Lore
        set(15, Items.simple(Material.PAPER, "Lore: " + entry.lore.size() + " Zeilen",
                        "Klick: Zeilen mit | getrennt eingeben", "Leer = keine Lore"),
                click -> services.anvilInput.open(viewer, Component.text("Lore (| trennt Zeilen)"),
                        String.join("|", entry.lore),
                        input -> {
                            entry.lore.clear();
                            if (!input.isBlank()) {
                                for (String line : input.split("\\|")) {
                                    entry.lore.add(line);
                                }
                            }
                            markDirty();
                            open();
                        }, this::open));

        // Enchantments
        set(16, Items.simple(Material.ENCHANTED_BOOK,
                        "Enchantments: " + (entry.enchantments.isEmpty()
                                ? "—" : String.join(", ", entry.enchantments)),
                        "Format: NAME:LEVEL, mit Komma getrennt",
                        "z. B. sharpness:2, unbreaking:3", "Klick: ändern, leer = keine"),
                click -> services.anvilInput.open(viewer, Component.text("Enchantments"),
                        String.join(", ", entry.enchantments),
                        input -> {
                            entry.enchantments.clear();
                            if (!input.isBlank()) {
                                for (String spec : input.split(",")) {
                                    entry.enchantments.add(spec.trim().toLowerCase(Locale.ROOT));
                                }
                            }
                            markDirty();
                            services.lootTables.validateEntry(entry).ifPresent(error ->
                                    viewer.sendMessage(Messages.error(error)));
                            open();
                        }, this::open));

        // Toggles
        set(28, Items.of(entry.unbreakable ? Material.LIME_DYE : Material.GRAY_DYE,
                        Component.text("Unzerstörbar: " + (entry.unbreakable ? "JA" : "nein"),
                                entry.unbreakable ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                        Component.text("Klick zum Umschalten", NamedTextColor.GRAY)),
                click -> {
                    entry.unbreakable = !entry.unbreakable;
                    markDirty();
                    refresh();
                });
        set(29, Items.of(entry.enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                        Component.text("Aktiv: " + (entry.enabled ? "JA" : "nein (Draft)"),
                                entry.enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                        Component.text("Deaktivierte Einträge landen nie im Spiel",
                                NamedTextColor.GRAY)),
                click -> {
                    entry.enabled = !entry.enabled;
                    markDirty();
                    refresh();
                });

        set(36, Items.back(), click -> onBack.run());
    }

    private void markDirty() {
        services.lootTables.markDirty();
    }
}
