package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.SettingDefinition.SettingCategory;
import com.hungergames.platform.Messages;
import com.hungergames.platform.Perms;
import com.hungergames.platform.deathmatch.DeathmatchService;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.config.ConfigCategoryMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Deathmatch-Steuerung in {@code /hg admin}: Status, manueller Start mit
 * Bestätigung, Abbruch während der Warnphase, Konfigurations-Schnellzugriff.
 */
public final class DeathmatchMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public DeathmatchMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 3, Component.text("Deathmatch"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        DeathmatchService deathmatch = services.deathmatch;
        DeathmatchService.State state = deathmatch.state();
        boolean enabled = services.config.get(services.settings.deathmatchEnabled);

        List<Component> statusLore = new ArrayList<>();
        statusLore.add(Component.text("Status: " + deathmatch.statusLine(),
                state == DeathmatchService.State.IDLE
                        ? (enabled ? NamedTextColor.GRAY : NamedTextColor.RED)
                        : NamedTextColor.GOLD));
        statusLore.add(Component.text("Border-Ziel: "
                + services.config.get(services.settings.deathmatchTargetBorderSize)
                + " Blöcke (aktuell " + (int) services.border.currentSize() + ")",
                NamedTextColor.GRAY));
        statusLore.add(Component.text("Warnzeit: "
                + services.config.get(services.settings.deathmatchWarningSeconds) + "s",
                NamedTextColor.GRAY));
        statusLore.add(Component.text("Teleport zur Mitte: "
                + (services.config.get(services.settings.deathmatchTeleportToCenter) ? "ja" : "nein"),
                NamedTextColor.GRAY));
        set(4, Items.of(Material.NETHERITE_SWORD,
                Component.text("Deathmatch", NamedTextColor.DARK_RED), statusLore));

        if (state == DeathmatchService.State.IDLE) {
            set(11, Items.of(enabled ? Material.RED_CONCRETE : Material.GRAY_DYE,
                            Component.text("Deathmatch starten",
                                    enabled ? NamedTextColor.RED : NamedTextColor.DARK_GRAY),
                            Component.text(enabled
                                            ? "Startet die Warnphase ("
                                            + services.config.get(services.settings.deathmatchWarningSeconds)
                                            + "s), danach schrumpft die Border"
                                            : "Deaktiviert (deathmatch.enabled)",
                                    NamedTextColor.GRAY)),
                    enabled ? click -> requestStart() : null);
        } else if (state == DeathmatchService.State.WARNING) {
            set(13, Items.of(Material.YELLOW_CONCRETE,
                            Component.text("Deathmatch abbrechen", NamedTextColor.YELLOW),
                            Component.text("Bricht die laufende Warnphase ab", NamedTextColor.GRAY)),
                    click -> {
                        services.deathmatch.cancel(viewer.getName()).ifPresentOrElse(
                                error -> viewer.sendMessage(Messages.error(error)),
                                () -> viewer.sendMessage(Messages.ok("Deathmatch abgebrochen.")));
                        refresh();
                    });
        }

        if (Perms.isAdmin(viewer)) {
            set(15, Items.simple(Material.COMPARATOR, "Deathmatch konfigurieren",
                            "Zielgröße, Warnzeit, Teleport, Sounds ..."),
                    click -> new ConfigCategoryMenu(viewer, services.config,
                            SettingCategory.DEATHMATCH, "Deathmatch", services.anvilInput,
                            this::open, services.borderGuard).open());
        }

        set(22, Items.back(), click -> onBack.run());
        set(26, Items.close(), click -> viewer.closeInventory());
    }

    private void requestStart() {
        Runnable doStart = () -> {
            services.deathmatch.start(viewer.getName()).ifPresentOrElse(
                    error -> viewer.sendMessage(Messages.error(error)),
                    () -> viewer.sendMessage(Messages.ok("Deathmatch ausgelöst.")));
        };
        if (services.config.get(services.settings.deathmatchRequireConfirmation)) {
            new ConfirmMenu(viewer, "Deathmatch starten",
                    List.of("Nach der Warnphase ("
                                    + services.config.get(services.settings.deathmatchWarningSeconds)
                                    + "s) schrumpft die Border auf "
                                    + services.config.get(services.settings.deathmatchTargetBorderSize)
                                    + " Blöcke.",
                            "Die regulären Border-Phasen werden übersteuert.",
                            "Abbruch ist nur während der Warnphase möglich."),
                    () -> {
                        doStart.run();
                        new DeathmatchMenu(viewer, services, onBack).open();
                    }, this::open).open();
        } else {
            doStart.run();
            refresh();
        }
    }
}
