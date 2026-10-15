package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.SettingDefinition;
import com.hungergames.game.config.SettingDefinition.SettingCategory;
import com.hungergames.platform.Messages;
import com.hungergames.platform.Perms;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Übersicht aller Event-Effekte (Partikel). Pro Event: Linksklick öffnet den
 * Effekt-Picker (Admin), Rechtsklick zeigt den Effekt an der eigenen Position.
 */
public final class EffectsMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public EffectsMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 6, Component.text("Visuelle Effekte"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        boolean admin = Perms.isAdmin(viewer);
        boolean enabled = services.config.get(services.settings.effectsEnabled);

        set(4, Items.of(Material.BLAZE_ROD,
                Component.text("Effekt-System", NamedTextColor.GOLD),
                Component.text("Status: " + (enabled ? "aktiviert" : "deaktiviert"),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text(admin ? "Linksklick: bearbeiten · Rechtsklick: ansehen"
                        : "Rechtsklick: ansehen", NamedTextColor.GRAY)));

        int slot = 9;
        for (SettingDefinition<?> def : services.config.registry().byCategory(SettingCategory.EFFECTS)) {
            if (slot >= 45 || def.type().valueClass() != String.class) {
                continue;
            }
            @SuppressWarnings("unchecked")
            SettingDefinition<String> effectDef = (SettingDefinition<String>) def;
            addEffect(slot++, effectDef, admin);
        }

        set(53, Items.back(), click -> onBack.run());
    }

    private void addEffect(int slot, SettingDefinition<String> def, boolean admin) {
        String value = services.config.get(def);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(def.description(), NamedTextColor.GRAY));
        lore.add(Component.text("Wert: " + (value.isBlank() ? "—" : value), NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text("Rechtsklick: ansehen", NamedTextColor.AQUA));
        if (admin) {
            lore.add(Component.text("Linksklick: Effekte auswählen (mehrere möglich)",
                    NamedTextColor.AQUA));
        }

        set(slot, Items.of(Material.FIREWORK_STAR,
                        Component.text(def.key().replace("effects.", ""), NamedTextColor.WHITE), lore),
                click -> {
                    if (click.isRightClick()) {
                        if (!services.effects.test(viewer, value)) {
                            viewer.sendMessage(Messages.info("Kein Effekt gesetzt."));
                        }
                    } else if (click.isLeftClick() && admin) {
                        new EffectPickerMenu(viewer, services, def, admin, this::open).open();
                    }
                });
    }
}
