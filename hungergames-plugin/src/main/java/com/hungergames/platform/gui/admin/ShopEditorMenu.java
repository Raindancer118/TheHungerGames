package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.items.CustomItems;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.sponsor.ShopItemParser;
import com.hungergames.platform.sponsor.ShopItemParser.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedizierter Sponsor-Shop-Editor — wie der Loot-Kisten-Editor: Angebote
 * hinzufügen, bearbeiten, entfernen und einzeln aktivieren/deaktivieren.
 * Arbeitet direkt auf {@code sponsors.shop.items}.
 *
 * <ul>
 *   <li>Linksklick: Angebot an/aus (deaktivierte bleiben erhalten, mit {@code #})</li>
 *   <li>Rechtsklick: Zeile bearbeiten (Freitext)</li>
 *   <li>Shift+Linksklick: Angebot entfernen</li>
 * </ul>
 */
public final class ShopEditorMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public ShopEditorMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 6, Component.text("Sponsor-Shop bearbeiten"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        List<String> lines = lines();

        set(4, Items.of(Material.EMERALD,
                Component.text("Shop-Angebote", NamedTextColor.GOLD),
                Component.text(lines.size() + " Einträge", NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("Links: an/aus · Rechts: bearbeiten", NamedTextColor.AQUA),
                Component.text("Shift+Links: entfernen", NamedTextColor.AQUA)));

        int slot = 9;
        for (int i = 0; i < lines.size() && slot < 45; i++) {
            renderEntry(slot++, i);
        }

        // === Hinzufügen (per GUI, kein Amboss) ===
        set(47, Items.of(Material.NETHER_STAR,
                Component.text("+ Custom-Item hinzufügen", NamedTextColor.LIGHT_PURPLE),
                Component.text("Item auswählen, dann Preis & Menge", NamedTextColor.GRAY)),
                click -> new ShopItemBuilderMenu(viewer, services,
                        ShopItemBuilderMenu.Kind.CUSTOM, this::open).open());

        set(48, Items.of(Material.CHEST,
                Component.text("+ Material hinzufügen", NamedTextColor.GREEN),
                Component.text("Beliebiges Item auswählen,", NamedTextColor.GRAY),
                Component.text("dann Preis & Menge", NamedTextColor.GRAY)),
                click -> new ShopItemBuilderMenu(viewer, services,
                        ShopItemBuilderMenu.Kind.MATERIAL, this::open).open());

        set(49, Items.of(Material.POTION,
                Component.text("+ Trank hinzufügen", NamedTextColor.LIGHT_PURPLE),
                Component.text("Variante & Trank-Typ wählen,", NamedTextColor.GRAY),
                Component.text("dann Preis & Menge", NamedTextColor.GRAY)),
                click -> new ShopItemBuilderMenu(viewer, services,
                        ShopItemBuilderMenu.Kind.POTION, this::open).open());

        // Freitext bleibt als Fortgeschrittenen-Option (z. B. Trank-Effekte)
        set(50, Items.of(Material.WRITABLE_BOOK,
                Component.text("+ Freitext / Effekt-Trank", NamedTextColor.YELLOW),
                Component.text("Format: id|reward|kosten|Anzeigename", NamedTextColor.DARK_GRAY),
                Component.text("reward: EFFECT:TYP:SEK:STÄRKE u. a.", NamedTextColor.DARK_GRAY)),
                click -> services.anvilInput.open(viewer, Component.text("Neuer Shop-Eintrag"),
                        "id|EFFECT:SPEED:15:0|5|Anzeigename", input -> {
                            List<String> updated = lines();
                            updated.add(input.trim());
                            save(updated);
                            open();
                        }, this::open));

        set(53, Items.back(), click -> onBack.run());
    }

    private void renderEntry(int slot, int index) {
        List<String> lines = lines();
        String raw = lines.get(index);
        ShopItem item;
        try {
            item = ShopItemParser.parse(raw);
        } catch (IllegalArgumentException e) {
            set(slot, Items.of(Material.BARRIER,
                    Component.text("Ungültiger Eintrag", NamedTextColor.RED),
                    Component.text(raw, NamedTextColor.DARK_GRAY),
                    Component.text(e.getMessage(), NamedTextColor.RED),
                    Component.empty(),
                    Component.text("Rechtsklick: bearbeiten · Shift+Links: entfernen",
                            NamedTextColor.AQUA)),
                    click -> {
                        if (click.isShiftClick() && click.isLeftClick()) {
                            remove(index);
                        } else if (click.isRightClick()) {
                            editLine(index, raw);
                        }
                    });
            return;
        }

        boolean on = item.enabled();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("ID: " + item.id(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text(describe(item), NamedTextColor.GRAY));
        if (!item.enchantments().isEmpty()) {
            lore.add(Component.text("Verzaubert: " + String.join(", ", item.enchantments()),
                    NamedTextColor.LIGHT_PURPLE));
        }
        lore.add(Component.text("Preis: " + item.cost() + " Token", NamedTextColor.GOLD));
        lore.add(Component.text("Status: " + (on ? "aktiv" : "deaktiviert"),
                on ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(Component.empty());
        lore.add(Component.text("Links: " + (on ? "deaktivieren" : "aktivieren"), NamedTextColor.AQUA));
        lore.add(Component.text("Rechts: bearbeiten (Text) · Shift+Rechts: Verzauberungen",
                NamedTextColor.AQUA));
        lore.add(Component.text("Shift+Links: entfernen", NamedTextColor.AQUA));

        Material icon = iconFor(item);
        set(slot, Items.of(icon, Component.text(item.displayName(),
                on ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY), lore),
                click -> {
                    if (click.isShiftClick() && click.isLeftClick()) {
                        remove(index);
                    } else if (click.isShiftClick() && click.isRightClick()) {
                        new ShopEnchantMenu(viewer, services, index, this::open).open();
                    } else if (click.isRightClick()) {
                        editLine(index, stripDisabled(raw));
                    } else if (click.isLeftClick()) {
                        toggle(index);
                    }
                });
    }

    // ==================== Aktionen ====================

    private void toggle(int index) {
        List<String> lines = lines();
        String raw = lines.get(index).trim();
        lines.set(index, raw.startsWith("#") ? raw.substring(1).trim() : "#" + raw);
        save(lines);
        refresh();
    }

    private void remove(int index) {
        new ConfirmMenu(viewer, "Angebot entfernen",
                List.of("Der Shop-Eintrag wird dauerhaft gelöscht."),
                () -> {
                    List<String> lines = lines();
                    if (index < lines.size()) {
                        lines.remove(index);
                        save(lines);
                    }
                    open();
                }, this::open).open();
    }

    private void editLine(int index, String initial) {
        services.anvilInput.open(viewer, Component.text("Eintrag bearbeiten"), initial,
                input -> {
                    List<String> lines = lines();
                    if (index < lines.size()) {
                        lines.set(index, input.trim());
                        save(lines);
                    }
                    open();
                }, this::open);
    }

    // ==================== Helfer ====================

    private List<String> lines() {
        return new ArrayList<>(services.config.get(services.settings.sponsorShopItems));
    }

    private void save(List<String> lines) {
        List<String> errors = services.config.begin(viewer.getName())
                .set(services.settings.sponsorShopItems, lines, Scope.PERSISTENT).commit();
        errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
    }

    private static String stripDisabled(String raw) {
        String t = raw.trim();
        return t.startsWith("#") ? t.substring(1).trim() : t;
    }


    private static String describe(ShopItem item) {
        return switch (item.reward()) {
            case ShopItemParser.MaterialReward m -> m.amount() + "× " + m.material().name();
            case ShopItemParser.EffectReward e -> "Effekt " + e.effectName() + " " + e.durationSeconds()
                    + "s (Stärke " + (e.amplifier() + 1) + ")";
            case ShopItemParser.CustomItemReward c -> c.amount() + "× "
                    + CustomItems.byId(c.customId()).map(CustomItems::displayName).orElse(c.customId());
            case ShopItemParser.PotionReward p -> p.amount() + "× Trank ("
                    + p.variant().name().toLowerCase(java.util.Locale.ROOT) + ", " + p.potionType() + ")";
        };
    }

    private static Material iconFor(ShopItem item) {
        return switch (item.reward()) {
            case ShopItemParser.MaterialReward m -> m.material();
            case ShopItemParser.EffectReward e -> Material.POTION;
            case ShopItemParser.CustomItemReward c -> CustomItems.iconFor(c.customId());
            case ShopItemParser.PotionReward p -> p.variant().material();
        };
    }
}
