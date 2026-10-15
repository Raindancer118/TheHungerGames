package com.hungergames.platform.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Generischer Zahlen-Editor: ±1/±10/±100 per Klick, exakte Eingabe über den
 * Amboss, Übernahme erst per Bestätigen-Knopf. Validierungsfehler werden
 * angezeigt statt angewendet.
 */
public final class NumberEditorMenu extends Menu {

    private final String settingName;
    private final Function<Double, String> validator; // null = gültig
    private final Consumer<Double> onApply;
    private final Runnable onBack;
    private final AnvilInput anvilInput;
    private final boolean integer;

    private double value;

    public NumberEditorMenu(Player viewer, String settingName, double current, boolean integer,
                            Function<Double, String> validator, Consumer<Double> onApply,
                            Runnable onBack, AnvilInput anvilInput) {
        super(viewer, 3, Component.text(settingName));
        this.settingName = settingName;
        this.value = current;
        this.integer = integer;
        this.validator = validator;
        this.onApply = onApply;
        this.onBack = onBack;
        this.anvilInput = anvilInput;
    }

    private String format(double v) {
        return integer ? String.valueOf((long) v) : String.valueOf(v);
    }

    @Override
    protected void build() {
        double[] steps = {-100, -10, -1, 1, 10, 100};
        int[] slots = {9, 10, 11, 15, 16, 17};
        for (int i = 0; i < steps.length; i++) {
            double step = steps[i];
            Material mat = step < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE;
            set(slots[i], Items.simple(mat, (step > 0 ? "+" : "") + format(step)),
                    click -> {
                        value += step;
                        refresh();
                    });
        }

        String error = validator.apply(value);
        set(13, Items.of(Material.NAME_TAG,
                Component.text(format(value), error == null ? NamedTextColor.YELLOW : NamedTextColor.RED),
                error == null
                        ? Component.text("Aktueller Entwurf", NamedTextColor.GRAY)
                        : Component.text(error, NamedTextColor.RED)));

        set(21, Items.simple(Material.ANVIL, "Exakte Eingabe", "Öffnet die Amboss-Eingabe"),
                click -> anvilInput.open(viewer, Component.text(settingName), format(value),
                        input -> {
                            try {
                                value = Double.parseDouble(input.replace(',', '.'));
                            } catch (NumberFormatException e) {
                                viewer.sendMessage(Component.text("Keine Zahl: " + input, NamedTextColor.RED));
                            }
                            open();
                        },
                        this::open));

        String applyError = validator.apply(value);
        if (applyError == null) {
            set(23, Items.of(Material.LIME_CONCRETE,
                            Component.text("Übernehmen: " + format(value), NamedTextColor.GREEN)),
                    click -> onApply.accept(value));
        } else {
            set(23, Items.of(Material.GRAY_CONCRETE,
                    Component.text("Ungültig", NamedTextColor.RED),
                    Component.text(applyError, NamedTextColor.RED)));
        }

        set(26, Items.back(), click -> onBack.run());
    }
}
