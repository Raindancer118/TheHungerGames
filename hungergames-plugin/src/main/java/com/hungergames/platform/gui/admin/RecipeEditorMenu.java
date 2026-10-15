package com.hungergames.platform.gui.admin;

import com.hungergames.HungerGamesPlugin;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.items.CustomItems;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.MaterialPickerMenu;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Grafischer Crafting-Rezept-Editor für den Exmatrikulator: Das 3×3-Raster
 * wird direkt im Menü bearbeitet (Linksklick: Material wählen, Rechtsklick:
 * Feld leeren). „Speichern" schreibt {@code items.exmatrikulator.recipe} in
 * die Config und registriert das Rezept sofort neu — kein /config-reload
 * nötig.
 */
public final class RecipeEditorMenu extends Menu {

    /** Inventar-Slots des 3×3-Rasters (Zeile für Zeile). */
    private static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    private final AdminServices services;
    private final Runnable onBack;
    /** Aktueller Editor-Zustand, Zeile für Zeile ({@code null} = leeres Feld). */
    private final Material[] grid = new Material[9];
    private boolean dirty;

    public RecipeEditorMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 5, Component.text("Rezept: Exmatrikulator"));
        this.services = services;
        this.onBack = onBack;
        loadFromConfig();
    }

    private void loadFromConfig() {
        List<String> rows = services.config.get(services.settings.exmatrikulatorRecipe);
        for (int r = 0; r < 3 && r < rows.size(); r++) {
            String[] tokens = rows.get(r).trim().split("\\s+");
            for (int c = 0; c < 3 && c < tokens.length; c++) {
                String token = tokens[c].trim();
                if (token.isEmpty() || token.equals("-") || token.equalsIgnoreCase("AIR")) {
                    continue;
                }
                grid[r * 3 + c] = Material.matchMaterial(token);
            }
        }
    }

    @Override
    protected void build() {
        set(4, Items.of(Material.CRAFTING_TABLE,
                Component.text("Exmatrikulator-Rezept", NamedTextColor.LIGHT_PURPLE),
                Component.text("Linksklick auf ein Feld: Material wählen", NamedTextColor.GRAY),
                Component.text("Rechtsklick: Feld leeren", NamedTextColor.GRAY),
                dirty ? Component.text("⚠ Ungespeicherte Änderungen", NamedTextColor.RED)
                        : Component.text("Alles gespeichert", NamedTextColor.GREEN)));

        for (int i = 0; i < GRID_SLOTS.length; i++) {
            final int index = i;
            Material material = grid[i];
            org.bukkit.inventory.ItemStack icon = material != null
                    ? Items.of(material,
                            Component.text(MaterialPickerMenu.pretty(material.name()),
                                    NamedTextColor.YELLOW),
                            Component.text(material.name(), NamedTextColor.DARK_GRAY),
                            Component.text("Linksklick: Material ändern", NamedTextColor.AQUA),
                            Component.text("Rechtsklick: Feld leeren", NamedTextColor.RED))
                    : Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                            Component.text("Leeres Feld", NamedTextColor.GRAY),
                            Component.text("Linksklick: Material wählen", NamedTextColor.AQUA));
            set(GRID_SLOTS[i], icon, click -> {
                if (click.isRightClick()) {
                    if (grid[index] != null) {
                        grid[index] = null;
                        dirty = true;
                        refresh();
                    }
                    return;
                }
                new MaterialPickerMenu(viewer, Component.text("Zutat wählen"),
                        services.anvilInput, true,
                        picked -> {
                            grid[index] = picked;
                            dirty = true;
                            open();
                        }, this::open).open();
            });
        }

        // Ergebnis-Vorschau rechts neben dem Raster
        set(23, Items.of(Material.SPECTRAL_ARROW, Component.text("→", NamedTextColor.WHITE)));
        set(24, CustomItems.EXMATRIKULATOR.create());

        set(38, Items.of(Material.PAPER,
                Component.text("Standard-Rezept laden", NamedTextColor.WHITE),
                Component.text("Setzt das Raster auf den Default", NamedTextColor.GRAY),
                Component.text("(erst Speichern macht es wirksam)", NamedTextColor.DARK_GRAY)),
                click -> {
                    List<String> defaults = services.settings.exmatrikulatorRecipe.defaultValue();
                    java.util.Arrays.fill(grid, null);
                    for (int r = 0; r < 3 && r < defaults.size(); r++) {
                        String[] tokens = defaults.get(r).trim().split("\\s+");
                        for (int c = 0; c < 3 && c < tokens.length; c++) {
                            grid[r * 3 + c] = Material.matchMaterial(tokens[c]);
                        }
                    }
                    dirty = true;
                    refresh();
                });

        set(40, Items.of(dirty ? Material.LIME_CONCRETE : Material.GREEN_STAINED_GLASS_PANE,
                Component.text("Speichern & Rezept aktivieren", NamedTextColor.GREEN),
                Component.text("Schreibt das Rezept in die Config und", NamedTextColor.GRAY),
                Component.text("registriert es sofort neu.", NamedTextColor.GRAY)),
                click -> save());

        set(44, Items.back(), click -> onBack.run());
    }

    private void save() {
        boolean anyIngredient = false;
        List<String> rows = new ArrayList<>(3);
        for (int r = 0; r < 3; r++) {
            StringBuilder row = new StringBuilder();
            for (int c = 0; c < 3; c++) {
                Material material = grid[r * 3 + c];
                if (c > 0) {
                    row.append(' ');
                }
                row.append(material != null ? material.name() : "-");
                anyIngredient |= material != null;
            }
            rows.add(row.toString());
        }
        if (!anyIngredient) {
            viewer.sendMessage(Messages.error("Das Rezept braucht mindestens eine Zutat."));
            return;
        }

        List<String> errors = services.config.begin(viewer.getName())
                .set(services.settings.exmatrikulatorRecipe, rows,
                        ConfigurationService.Scope.PERSISTENT)
                .commit();
        if (!errors.isEmpty()) {
            errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
            return;
        }
        CustomItems.registerRecipes(JavaPlugin.getPlugin(HungerGamesPlugin.class));
        dirty = false;
        viewer.sendMessage(Messages.ok("Exmatrikulator-Rezept gespeichert und aktiviert."));
        refresh();
    }
}
