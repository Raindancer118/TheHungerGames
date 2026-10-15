package com.hungergames.platform.gui.loot;

import com.hungergames.items.CustomItems;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.admin.AdminServices;
import com.hungergames.platform.loot.LootTableRepository.EntryModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * GUI zum Auswählen des Loot-Item-Typs: Umschaltbar zwischen der vollständigen
 * Material-Liste und den Custom-Items des Plugins. Setzt {@code entry.item} und
 * {@code entry.custom} direkt (kein Amboss nötig).
 */
public final class LootItemPickerMenu extends Menu {

    private enum Mode { MATERIAL, CUSTOM }

    private static final int PER_PAGE = 45;

    private final AdminServices services;
    private final EntryModel entry;
    private final Runnable onBack;
    private Mode mode = Mode.MATERIAL;
    private int page;
    private List<Material> materialCache;

    public LootItemPickerMenu(Player viewer, AdminServices services, EntryModel entry, Runnable onBack) {
        super(viewer, 6, Component.text("Item wählen"));
        this.services = services;
        this.entry = entry;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        // Modus-Umschalter
        set(45, Items.of(Material.CHEST,
                Component.text((mode == Mode.MATERIAL ? "▶ " : "") + "Materialien",
                        mode == Mode.MATERIAL ? NamedTextColor.GREEN : NamedTextColor.GRAY)),
                click -> {
                    mode = Mode.MATERIAL;
                    page = 0;
                    refresh();
                });
        set(46, Items.of(Material.NETHER_STAR,
                Component.text((mode == Mode.CUSTOM ? "▶ " : "") + "Custom-Items",
                        mode == Mode.CUSTOM ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY)),
                click -> {
                    mode = Mode.CUSTOM;
                    page = 0;
                    refresh();
                });

        if (mode == Mode.CUSTOM) {
            buildCustom();
        } else {
            buildMaterial();
        }
        set(53, Items.back(), click -> onBack.run());
    }

    private void buildCustom() {
        int slot = 0;
        for (String id : new TreeSet<>(CustomItems.ids())) {
            if (slot >= PER_PAGE) {
                break;
            }
            String label = CustomItems.byId(id).map(CustomItems::displayName).orElse(id);
            set(slot++, Items.of(CustomItems.iconFor(id),
                    Component.text(label, NamedTextColor.LIGHT_PURPLE),
                    Component.text(id, NamedTextColor.DARK_GRAY),
                    Component.text("Klick: auswählen", NamedTextColor.AQUA)),
                    click -> apply(id, true));
        }
    }

    private void buildMaterial() {
        List<Material> materials = materials();
        int totalPages = Math.max(1, (materials.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.floorMod(page, totalPages);
        int from = page * PER_PAGE;
        int to = Math.min(materials.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            Material material = materials.get(i);
            set(i - from, Items.of(material,
                    Component.text(pretty(material.name()), NamedTextColor.YELLOW),
                    Component.text(material.name(), NamedTextColor.DARK_GRAY),
                    Component.text("Klick: auswählen", NamedTextColor.AQUA)),
                    click -> apply(material.name(), false));
        }
        if (totalPages > 1) {
            set(48, Items.of(Material.SPECTRAL_ARROW,
                    Component.text("◀ Seite " + (page + 1) + "/" + totalPages, NamedTextColor.WHITE)),
                    click -> {
                        page--;
                        refresh();
                    });
            set(50, Items.of(Material.SPECTRAL_ARROW,
                    Component.text("Seite " + (page + 1) + "/" + totalPages + " ▶", NamedTextColor.WHITE)),
                    click -> {
                        page++;
                        refresh();
                    });
        }
    }

    private void apply(String item, boolean custom) {
        entry.item = item.toUpperCase(Locale.ROOT);
        entry.custom = custom;
        services.lootTables.markDirty();
        onBack.run();
    }

    private List<Material> materials() {
        if (materialCache == null) {
            List<Material> list = new ArrayList<>();
            for (Material material : Material.values()) {
                if (!material.isLegacy() && material.isItem() && !material.isAir()) {
                    list.add(material);
                }
            }
            list.sort(java.util.Comparator.comparing(Material::name));
            materialCache = list;
        }
        return materialCache;
    }

    private static String pretty(String name) {
        String[] words = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
