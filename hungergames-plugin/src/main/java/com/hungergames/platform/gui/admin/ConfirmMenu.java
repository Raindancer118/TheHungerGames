package com.hungergames.platform.gui.admin;

import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Generische Bestätigungs-GUI für gefährliche Aktionen: zeigt die Aktion und
 * ihre Folgen, führt erst nach explizitem Klick auf „Bestätigen" aus.
 */
public final class ConfirmMenu extends Menu {

    private final String action;
    private final List<String> consequences;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    /**
     * @param action       Kurzbeschreibung der Aktion (Titelzeile des Info-Items)
     * @param consequences Lore: was passiert bei Bestätigung
     * @param onConfirm    wird bei Bestätigung ausgeführt
     * @param onCancel     wird bei Abbruch ausgeführt (z. B. vorheriges Menü öffnen)
     */
    public ConfirmMenu(Player viewer, String action, List<String> consequences,
                       Runnable onConfirm, Runnable onCancel) {
        super(viewer, 3, Component.text("Bestätigen: " + action));
        this.action = action;
        this.consequences = consequences;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void build() {
        List<Component> lore = new ArrayList<>();
        for (String line : consequences) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        set(4, Items.of(Material.PAPER,
                Component.text(action, NamedTextColor.YELLOW), lore));

        set(11, Items.of(Material.LIME_CONCRETE,
                        Component.text("Bestätigen", NamedTextColor.GREEN),
                        Component.text("Aktion ausführen", NamedTextColor.GRAY)),
                click -> {
                    viewer.closeInventory();
                    onConfirm.run();
                });

        set(15, Items.of(Material.RED_CONCRETE,
                        Component.text("Abbrechen", NamedTextColor.RED)),
                click -> onCancel.run());
    }
}
