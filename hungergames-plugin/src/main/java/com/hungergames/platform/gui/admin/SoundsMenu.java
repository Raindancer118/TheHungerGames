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
 * Sound-Verwaltung: pro Sound Linksklick = bearbeiten (Admin),
 * Rechtsklick = probehören. Ergänzt das generische Kategorie-Menü um den
 * Test-Button.
 */
public final class SoundsMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public SoundsMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 6, Component.text("Sounds"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        boolean admin = Perms.isAdmin(viewer);
        boolean enabled = services.config.get(services.settings.soundsEnabled);

        set(4, Items.of(Material.NOTE_BLOCK,
                Component.text("Sound-System", NamedTextColor.GOLD),
                Component.text("Status: " + (enabled ? "aktiviert" : "deaktiviert"),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text("Lautstärke "
                        + services.config.get(services.settings.soundDefaultVolume)
                        + ", Tonhöhe "
                        + services.config.get(services.settings.soundDefaultPitch),
                        NamedTextColor.GRAY)));

        int slot = 9;
        for (SettingDefinition<?> def : services.config.registry()
                .byCategory(SettingCategory.SOUNDS)) {
            if (slot >= 53 || def.type().valueClass() != String.class) {
                continue;
            }
            @SuppressWarnings("unchecked")
            SettingDefinition<String> soundDef = (SettingDefinition<String>) def;
            addSound(slot++, soundDef, admin);
        }

        set(53, Items.back(), click -> onBack.run());
    }

    private void addSound(int slot, SettingDefinition<String> def, boolean admin) {
        String value = services.config.get(def);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(def.description(), NamedTextColor.GRAY));
        lore.add(Component.text("Wert: " + value, NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text("Rechtsklick: probehören", NamedTextColor.AQUA));
        if (admin) {
            lore.add(Component.text("Linksklick: Sounds auswählen (mehrere gleichzeitig)",
                    NamedTextColor.AQUA));
        }

        set(slot, Items.of(Material.NOTE_BLOCK,
                        Component.text(def.key().replace("sounds.", ""), NamedTextColor.WHITE), lore),
                click -> {
                    if (click.isRightClick()) {
                        if (services.sounds.test(viewer, value)) {
                            viewer.sendMessage(Messages.info("♪ " + value));
                        } else {
                            viewer.sendMessage(Messages.error("Sound \"" + value
                                    + "\" ist nicht auflösbar (Resource-Pack-Sounds brauchen den Client)."));
                        }
                    } else if (click.isLeftClick() && admin) {
                        new SoundPickerMenu(viewer, services, def, admin, this::open).open();
                    }
                });
    }
}
