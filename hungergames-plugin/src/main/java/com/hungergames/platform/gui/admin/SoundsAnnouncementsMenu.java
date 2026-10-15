package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.game.config.SettingDefinition.SettingCategory;
import com.hungergames.platform.Perms;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.config.ConfigCategoryMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Verteiler für Sounds und Ansagen: Sound-Menü mit Probehören und die
 * Announcement-Konfiguration (Texte, Kanäle, Schwellen).
 */
public final class SoundsAnnouncementsMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public SoundsAnnouncementsMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 3, Component.text("Sounds, Ansagen & Effekte"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        boolean admin = Perms.isAdmin(viewer);

        set(10, Items.simple(Material.NOTE_BLOCK, "Sounds",
                        "Rechtsklick hört Probe" + (admin ? ", Linksklick ändert" : "")),
                click -> new SoundsMenu(viewer, services, this::open).open());

        set(12, Items.simple(Material.BLAZE_ROD, "Visuelle Effekte",
                        "Partikel pro Event",
                        "Rechtsklick ansehen" + (admin ? ", Linksklick bearbeiten" : "")),
                click -> new EffectsMenu(viewer, services, this::open).open());

        if (admin) {
            set(14, Items.simple(Material.WRITABLE_BOOK, "Ansagen",
                            "Texte, Kanäle (Chat/Title/Actionbar),", "Killfeed, Schwellen"),
                    click -> new ConfigCategoryMenu(viewer, services.config,
                            SettingCategory.ANNOUNCEMENTS, "Ansagen", services.anvilInput,
                            this::open, services.borderGuard).open());

            set(16, Items.simple(Material.FIREWORK_STAR, "Effekt-System an/aus",
                            "Status: " + (services.config.get(services.settings.effectsEnabled)
                                    ? "AN" : "AUS")),
                    click -> {
                        boolean now = services.config.get(services.settings.effectsEnabled);
                        services.config.begin(viewer.getName())
                                .set(services.settings.effectsEnabled, !now, Scope.PERSISTENT).commit();
                        refresh();
                    });
        } else {
            set(14, Items.of(Material.GRAY_STAINED_GLASS_PANE,
                    Component.text("Ansagen", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY),
                    Component.text("Erfordert " + Perms.ADMIN,
                            net.kyori.adventure.text.format.NamedTextColor.RED)));
        }

        set(22, Items.back(), click -> onBack.run());
    }
}
