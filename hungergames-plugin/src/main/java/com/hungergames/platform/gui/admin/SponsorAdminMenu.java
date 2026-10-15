package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.game.config.SettingDefinition.SettingCategory;
import com.hungergames.platform.Messages;
import com.hungergames.platform.Perms;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.config.ConfigCategoryMenu;
import com.hungergames.platform.gui.sponsor.SponsorShopMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Sponsor-System-Verwaltung in {@code /hg admin}: Master-/Token-/Beacon-/
 * Shop-Toggles, Beacons setzen/entfernen, Test-Token, Shop-Vorschau.
 */
public final class SponsorAdminMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public SponsorAdminMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 6, Component.text("Sponsor-System"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        boolean admin = Perms.isAdmin(viewer);

        List<Component> statusLore = new ArrayList<>();
        statusLore.add(line("Sponsor-System", services.settings.sponsorsEnabled));
        statusLore.add(line("Tokens", services.settings.sponsorTokensEnabled));
        statusLore.add(line("Beacons", services.settings.sponsorBeaconsEnabled));
        statusLore.add(line("Shop", services.settings.sponsorShopEnabled));
        statusLore.add(Component.text("Beacons: " + services.sponsorBeacons.statusLine(),
                NamedTextColor.YELLOW));
        statusLore.add(Component.text("Token-Intervall: "
                + services.config.get(services.settings.sponsorTokenInterval).toMinutes()
                + "m", NamedTextColor.GRAY));
        set(4, Items.of(Material.NETHER_STAR,
                Component.text("Sponsoren", NamedTextColor.GOLD), statusLore));

        // === Toggles (Admin) ===
        toggle(10, "Sponsor-System", services.settings.sponsorsEnabled, admin);
        toggle(11, "Tokens", services.settings.sponsorTokensEnabled, admin);
        toggle(12, "Beacons", services.settings.sponsorBeaconsEnabled, admin);
        toggle(13, "Shop", services.settings.sponsorShopEnabled, admin);

        if (admin) {
            set(15, Items.simple(Material.COMPARATOR, "Sponsor-Konfiguration",
                            "Alle Sponsor-Einstellungen", "(inkl. Beacon-Material/-Schutz)"),
                    click -> new ConfigCategoryMenu(viewer, services.config,
                            SettingCategory.SPONSORS, "Sponsoren", services.anvilInput,
                            this::open, services.borderGuard).open());

            set(16, Items.simple(Material.EMERALD, "Shop-Editor",
                            "Angebote hinzufügen/bearbeiten/", "entfernen & einzeln an-/ausschalten"),
                    click -> new ShopEditorMenu(viewer, services, this::open).open());
        }

        // === Beacon-Aktionen (Spielmacher) ===
        set(19, Items.simple(Material.BEACON, "Beacon hier erstellen",
                        "Setzt einen Sponsor-Beacon", "an deiner Position"),
                click -> {
                    services.sponsorBeacons.createBeacon(viewer.getLocation(), viewer.getName())
                            .ifPresentOrElse(
                                    error -> viewer.sendMessage(Messages.error(error)),
                                    () -> viewer.sendMessage(Messages.ok("Sponsor-Beacon erstellt.")));
                    refresh();
                });

        set(20, Items.simple(Material.TNT_MINECART, "Nächsten Beacon entfernen",
                        "Entfernt den Sponsor-Beacon", "in deiner Nähe (15 Blöcke)"),
                click -> {
                    services.sponsorBeacons.removeNearest(viewer.getLocation(), viewer.getName())
                            .ifPresentOrElse(
                                    error -> viewer.sendMessage(Messages.error(error)),
                                    () -> viewer.sendMessage(Messages.ok("Beacon entfernt.")));
                    refresh();
                });

        if (admin) {
            set(21, Items.simple(Material.TNT, "Alle Beacons entfernen",
                            "Entfernt alle aktiven Sponsor-Beacons"),
                    click -> new ConfirmMenu(viewer, "Alle Beacons entfernen",
                            List.of("Entfernt " + services.sponsorBeacons.activeBeacons().size()
                                    + " aktive Sponsor-Beacons aus der Welt."),
                            () -> {
                                int removed = services.sponsorBeacons.removeAll(viewer.getName());
                                viewer.sendMessage(Messages.ok(removed + " Beacon(s) entfernt."));
                                open();
                            }, this::open).open());
        }

        set(23, Items.simple(Material.NETHER_STAR, "Test-Token geben",
                        "Gibt dir 1 echtes Sponsor Token"),
                click -> {
                    services.sponsorTokens.giveManually(viewer.getName(), viewer, 1);
                    viewer.sendMessage(Messages.ok("1 Sponsor Token erhalten."));
                });

        set(24, Items.simple(Material.EMERALD, "Shop-Vorschau",
                        "Zeigt den Sponsor-Shop", "(Käufe gesperrt)"),
                click -> new SponsorShopMenu(viewer, services.config, services.settings,
                        services.sponsorTokens, services.announcements, services.sounds,
                        services.roundLog, true).open());

        // === Aktive Beacons ===
        int slot = 28;
        for (Location beacon : services.sponsorBeacons.activeBeacons()) {
            if (slot >= 45) {
                break;
            }
            set(slot++, Items.simple(Material.BEACON,
                            beacon.getBlockX() + " / " + beacon.getBlockY() + " / " + beacon.getBlockZ(),
                            "Welt: " + (beacon.getWorld() != null ? beacon.getWorld().getName() : "?"),
                            "Klick: hinteleportieren"),
                    click -> viewer.teleport(beacon.clone().add(0.5, 1, 0.5)));
        }

        set(49, Items.back(), click -> onBack.run());
        set(53, Items.close(), click -> viewer.closeInventory());
    }

    private Component line(String label, SettingDefinition<Boolean> def) {
        boolean on = services.config.get(def);
        return Component.text(label + ": " + (on ? "an" : "aus"),
                on ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    private void toggle(int slot, String label, SettingDefinition<Boolean> def, boolean admin) {
        boolean on = services.config.get(def);
        if (!admin) {
            set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE,
                    Component.text(label + ": " + (on ? "an" : "aus"), NamedTextColor.DARK_GRAY),
                    Component.text("Erfordert " + Perms.ADMIN, NamedTextColor.RED)));
            return;
        }
        set(slot, Items.of(on ? Material.LIME_DYE : Material.GRAY_DYE,
                        Component.text(label + ": " + (on ? "AN" : "AUS"),
                                on ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                        Component.text("Klick zum Umschalten", NamedTextColor.GRAY)),
                click -> {
                    List<String> errors = services.config.begin(viewer.getName())
                            .set(def, !on, Scope.PERSISTENT).commit();
                    if (errors.isEmpty()) {
                        viewer.sendMessage(Messages.ok(def.key() + " = " + !on));
                    } else {
                        errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
                    }
                    refresh();
                });
    }
}
