package com.hungergames.platform.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Generischer, paginierter Material-Picker mit Amboss-Suche. Wird z. B. vom
 * Rezept-Editor genutzt; {@code onPick} erhält das gewählte Material bzw.
 * {@code null}, wenn „Feld leeren" gewählt wurde.
 */
public final class MaterialPickerMenu extends Menu {

    private static final int PER_PAGE = 45;

    /** Alle als Item nutzbaren Materialien, alphabetisch (einmalig berechnet). */
    private static List<Material> allItems;

    private final AnvilInput anvilInput;
    private final boolean allowClear;
    private final Consumer<Material> onPick;
    private final Runnable onBack;
    private int page;
    private String filter = "";

    public MaterialPickerMenu(Player viewer, Component title, AnvilInput anvilInput,
                              boolean allowClear, Consumer<Material> onPick, Runnable onBack) {
        super(viewer, 6, title);
        this.anvilInput = anvilInput;
        this.allowClear = allowClear;
        this.onPick = onPick;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        List<Material> materials = filtered();
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
                    click -> onPick.accept(material));
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

        if (anvilInput != null) {
            set(47, Items.of(Material.SPYGLASS,
                    Component.text("Suchen", NamedTextColor.AQUA),
                    Component.text(filter.isEmpty() ? "Kein Filter aktiv" : "Filter: " + filter,
                            NamedTextColor.GRAY),
                    Component.text("Rechtsklick: Filter löschen", NamedTextColor.DARK_GRAY)),
                    click -> {
                        if (click.isRightClick()) {
                            filter = "";
                            page = 0;
                            refresh();
                            return;
                        }
                        anvilInput.open(viewer, Component.text("Material suchen"), filter,
                                input -> {
                                    filter = input.trim();
                                    page = 0;
                                    open();
                                }, this::open);
                    });
        }

        if (allowClear) {
            set(49, Items.of(Material.GLASS_PANE,
                    Component.text("Feld leeren", NamedTextColor.RED),
                    Component.text("Setzt das Rezeptfeld auf leer", NamedTextColor.GRAY)),
                    click -> onPick.accept(null));
        }

        set(53, Items.back(), click -> onBack.run());
    }

    private List<Material> filtered() {
        List<Material> base = allItemMaterials();
        if (filter.isEmpty()) {
            return base;
        }
        String needle = filter.toUpperCase(Locale.ROOT).replace(' ', '_');
        List<Material> result = new ArrayList<>();
        for (Material material : base) {
            if (material.name().contains(needle)) {
                result.add(material);
            }
        }
        return result;
    }

    private static synchronized List<Material> allItemMaterials() {
        if (allItems == null) {
            List<Material> list = new ArrayList<>();
            for (Material material : Material.values()) {
                if (!material.isLegacy() && material.isItem() && !material.isAir()) {
                    list.add(material);
                }
            }
            list.sort(java.util.Comparator.comparing(Material::name));
            allItems = list;
        }
        return allItems;
    }

    /** „STONE_BRICK_STAIRS" → „Stone Brick Stairs" (für Anzeige-Labels). */
    public static String pretty(String name) {
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
