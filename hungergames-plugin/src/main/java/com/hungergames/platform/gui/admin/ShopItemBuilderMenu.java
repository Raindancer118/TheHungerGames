package com.hungergames.platform.gui.admin;

import com.hungergames.items.CustomItems;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GUI zum Auswählen eines Shop-Artikels (statt Amboss-Freitext): entweder ein
 * Custom-Item oder ein beliebiges Material. Nach der Auswahl geht es zur
 * Preis-/Mengen-Einstellung ({@link ShopCostMenu}).
 */
public final class ShopItemBuilderMenu extends Menu {

    public enum Kind { CUSTOM, MATERIAL, POTION }

    private static final int PER_PAGE = 45;

    private final AdminServices services;
    private final Kind kind;
    private final Runnable onBack;
    private int page;
    private List<Material> materialCache;
    private com.hungergames.platform.sponsor.ShopItemParser.PotionVariant potionVariant =
            com.hungergames.platform.sponsor.ShopItemParser.PotionVariant.NORMAL;

    public ShopItemBuilderMenu(Player viewer, AdminServices services, Kind kind, Runnable onBack) {
        super(viewer, 6, Component.text(switch (kind) {
            case CUSTOM -> "Custom-Item wählen";
            case MATERIAL -> "Material wählen";
            case POTION -> "Trank wählen";
        }));
        this.services = services;
        this.kind = kind;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        switch (kind) {
            case CUSTOM -> buildCustom();
            case MATERIAL -> buildMaterial();
            case POTION -> buildPotion();
        }
        set(53, Items.back(), click -> onBack.run());
    }

    private void buildPotion() {
        // Varianten-Umschalter oben
        var variants = com.hungergames.platform.sponsor.ShopItemParser.PotionVariant.values();
        int vslot = 45;
        for (var variant : variants) {
            boolean sel = variant == potionVariant;
            set(vslot++, Items.of(variant.material(),
                    Component.text((sel ? "▶ " : "") + switch (variant) {
                        case NORMAL -> "Trinkbar";
                        case SPLASH -> "Wurf";
                        case LINGERING -> "Verweil";
                    }, sel ? NamedTextColor.GREEN : NamedTextColor.GRAY)),
                    click -> {
                        potionVariant = variant;
                        refresh();
                    });
        }
        // Trank-Typen (paginiert)
        List<org.bukkit.potion.PotionType> types = potionTypes();
        int totalPages = Math.max(1, (types.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.floorMod(page, totalPages);
        int from = page * PER_PAGE;
        int to = Math.min(types.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            org.bukkit.potion.PotionType type = types.get(i);
            String label = pretty(type.name());
            set(i - from, Items.of(potionVariant.material(),
                    Component.text(label, NamedTextColor.LIGHT_PURPLE),
                    Component.text(type.name(), NamedTextColor.DARK_GRAY),
                    Component.text("Klick: Preis & Menge festlegen", NamedTextColor.AQUA)),
                    click -> new ShopCostMenu(viewer, services, Kind.POTION,
                            potionVariant.name() + ":" + type.name(), label,
                            potionVariant.material(), 6, this::open).open());
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

    private static List<org.bukkit.potion.PotionType> potionTypes() {
        List<org.bukkit.potion.PotionType> list = new ArrayList<>();
        for (org.bukkit.potion.PotionType type : org.bukkit.potion.PotionType.values()) {
            list.add(type);
        }
        list.sort(java.util.Comparator.comparing(Enum::name));
        return list;
    }

    private void buildCustom() {
        int slot = 0;
        for (String id : new java.util.TreeSet<>(CustomItems.ids())) {
            if (slot >= 45) {
                break;
            }
            String label = CustomItems.byId(id).map(CustomItems::displayName).orElse(id);
            set(slot++, Items.of(CustomItems.iconFor(id),
                    Component.text(label, NamedTextColor.LIGHT_PURPLE),
                    Component.text(id, NamedTextColor.DARK_GRAY),
                    Component.text("Klick: Preis & Menge festlegen", NamedTextColor.AQUA)),
                    click -> new ShopCostMenu(viewer, services, Kind.CUSTOM, id, label,
                            CustomItems.iconFor(id), 8, this::open).open());
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
            String label = pretty(material.name());
            set(i - from, Items.of(material,
                    Component.text(label, NamedTextColor.YELLOW),
                    Component.text(material.name(), NamedTextColor.DARK_GRAY),
                    Component.text("Klick: Preis & Menge festlegen", NamedTextColor.AQUA)),
                    click -> new ShopCostMenu(viewer, services, Kind.MATERIAL, material.name(), label,
                            material, 5, this::open).open());
        }
        if (totalPages > 1) {
            set(45, Items.of(Material.SPECTRAL_ARROW,
                    Component.text("◀ Seite " + (page + 1) + "/" + totalPages, NamedTextColor.WHITE)),
                    click -> {
                        page--;
                        refresh();
                    });
            set(52, Items.of(Material.SPECTRAL_ARROW,
                    Component.text("Seite " + (page + 1) + "/" + totalPages + " ▶", NamedTextColor.WHITE)),
                    click -> {
                        page++;
                        refresh();
                    });
        }
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
