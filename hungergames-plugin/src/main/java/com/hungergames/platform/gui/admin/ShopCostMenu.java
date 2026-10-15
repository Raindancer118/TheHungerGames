package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.admin.ShopItemBuilderMenu.Kind;
import com.hungergames.platform.sponsor.ShopItemParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Letzter Schritt beim GUI-basierten Hinzufügen eines Shop-Artikels: Preis (in
 * Tokens) und Menge per +/- festlegen, dann bestätigen. Baut den Shop-Eintrag
 * und hängt ihn an {@code sponsors.shop.items} an — ganz ohne Amboss.
 */
public final class ShopCostMenu extends Menu {

    private final AdminServices services;
    private final Kind kind;
    private final String itemId;
    private final String label;
    private final Material icon;
    private final Runnable onBack;

    private int cost;
    private int amount = 1;

    public ShopCostMenu(Player viewer, AdminServices services, Kind kind, String itemId,
                        String label, Material icon, int defaultCost, Runnable onBack) {
        super(viewer, 5, Component.text("Preis: " + label));
        this.services = services;
        this.kind = kind;
        this.itemId = itemId;
        this.label = label;
        this.icon = icon;
        this.cost = Math.max(1, defaultCost);
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        set(4, Items.of(icon,
                Component.text(label, NamedTextColor.GOLD),
                Component.text("Preis: " + cost + " Token", NamedTextColor.YELLOW),
                Component.text("Menge: " + amount, NamedTextColor.YELLOW)));

        // Preis
        set(19, Items.of(Material.RED_STAINED_GLASS_PANE,
                Component.text("− 1 Token", NamedTextColor.RED)), click -> {
            cost = Math.max(1, cost - 1);
            refresh();
        });
        set(20, Items.of(Material.RED_STAINED_GLASS_PANE,
                Component.text("− 5 Token", NamedTextColor.RED)), click -> {
            cost = Math.max(1, cost - 5);
            refresh();
        });
        set(21, Items.of(Material.GOLD_NUGGET,
                Component.text("Preis: " + cost + " Token", NamedTextColor.YELLOW)));
        set(22, Items.of(Material.LIME_STAINED_GLASS_PANE,
                Component.text("+ 5 Token", NamedTextColor.GREEN)), click -> {
            cost += 5;
            refresh();
        });
        set(23, Items.of(Material.LIME_STAINED_GLASS_PANE,
                Component.text("+ 1 Token", NamedTextColor.GREEN)), click -> {
            cost += 1;
            refresh();
        });

        // Menge
        set(29, Items.of(Material.RED_STAINED_GLASS_PANE,
                Component.text("− Menge", NamedTextColor.RED)), click -> {
            amount = Math.max(1, amount - 1);
            refresh();
        });
        set(31, Items.of(Material.REPEATER,
                Component.text("Menge: " + amount, NamedTextColor.YELLOW)));
        set(33, Items.of(Material.LIME_STAINED_GLASS_PANE,
                Component.text("+ Menge", NamedTextColor.GREEN)), click -> {
            amount = Math.min(64, amount + 1);
            refresh();
        });

        set(40, Items.of(Material.EMERALD_BLOCK,
                Component.text("✔ Zum Shop hinzufügen", NamedTextColor.GREEN),
                Component.text(amount + "× " + label + " für " + cost + " Token", NamedTextColor.GRAY)),
                click -> confirm());

        set(44, Items.back(), click -> onBack.run());
    }

    private void confirm() {
        List<String> lines = new ArrayList<>(services.config.get(services.settings.sponsorShopItems));
        // itemId ist bei POTION als "VARIANT:TYPE" kodiert, sonst der reine Item-/Material-Name.
        String reward = switch (kind) {
            case CUSTOM -> "ITEM:" + itemId + ":" + amount;
            case POTION -> "POTION:" + itemId + ":" + amount;
            case MATERIAL -> itemId + ":" + amount;
        };
        String idBase = (kind == Kind.POTION
                ? itemId.substring(itemId.indexOf(':') + 1) : itemId).toLowerCase(Locale.ROOT);
        String id = uniqueId(idBase, lines);
        lines.add(id + "|" + reward + "|" + cost + "|" + label);

        List<String> errors = services.config.begin(viewer.getName())
                .set(services.settings.sponsorShopItems, lines, Scope.PERSISTENT).commit();
        if (errors.isEmpty()) {
            viewer.sendMessage(Messages.ok(amount + "× " + label + " für " + cost
                    + " Token zum Shop hinzugefügt."));
        } else {
            errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
        }
        onBack.run();
    }

    private static String uniqueId(String base, List<String> lines) {
        String id = base;
        int n = 2;
        while (idTaken(id, lines)) {
            id = base + n++;
        }
        return id;
    }

    private static boolean idTaken(String id, List<String> lines) {
        for (String line : lines) {
            try {
                if (ShopItemParser.parse(line).id().equalsIgnoreCase(id)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // ungültige Zeilen zählen nicht
            }
        }
        return false;
    }
}
