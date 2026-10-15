package com.hungergames.platform.gui.admin;

import com.hungergames.platform.Messages;
import com.hungergames.platform.Perms;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Spielmacher-Verwaltung in {@code /hg admin}: eigenen Modus togglen,
 * Spielmodus wechseln, Teleport-Menü, Namensliste pflegen (Admin).
 */
public final class GamemasterMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public GamemasterMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 6, Component.text("Spielmacher"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        var gm = services.gamemaster;
        boolean admin = Perms.isAdmin(viewer);
        boolean self = gm.isGamemaster(viewer);
        boolean activeSelf = gm.isActive(viewer.getUniqueId());

        // Status
        List<Component> statusLore = new ArrayList<>();
        statusLore.add(Component.text("System: ", NamedTextColor.GRAY)
                .append(Component.text(services.config.get(services.settings.gmEnabled)
                        ? "aktiviert" : "deaktiviert",
                        services.config.get(services.settings.gmEnabled)
                                ? NamedTextColor.GREEN : NamedTextColor.RED)));
        statusLore.add(Component.text("Erkennung: "
                + services.config.get(services.settings.gmPermissionMode), NamedTextColor.GRAY));
        statusLore.add(Component.text("Standard-Modus: "
                + services.config.get(services.settings.gmDefaultMode), NamedTextColor.GRAY));
        statusLore.add(Component.text("Aktiv: " + gm.activeGamemasters().size()
                + " Spielmacher", NamedTextColor.YELLOW));
        set(4, Items.of(Material.COMPASS,
                Component.text("Spielmacher-System", NamedTextColor.GOLD), statusLore));

        // Eigener Modus
        if (self) {
            set(10, Items.of(activeSelf ? Material.LIME_DYE : Material.GRAY_DYE,
                            Component.text("Spielmacher-Modus: " + (activeSelf ? "AN" : "AUS"),
                                    activeSelf ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                            Component.text("Klick zum Umschalten", NamedTextColor.GRAY)),
                    click -> {
                        var error = activeSelf ? gm.deactivate(viewer) : gm.activate(viewer);
                        error.ifPresentOrElse(
                                message -> viewer.sendMessage(Messages.error(message)),
                                () -> viewer.sendMessage(Messages.ok("Spielmacher-Modus "
                                        + (activeSelf ? "deaktiviert." : "aktiviert."))));
                        refresh();
                    });
        } else {
            set(10, Items.of(Material.GRAY_STAINED_GLASS_PANE,
                    Component.text("Kein Spielmacher", NamedTextColor.DARK_GRAY),
                    Component.text("Du bist nicht als Spielmacher konfiguriert", NamedTextColor.RED)));
        }

        if (activeSelf) {
            set(12, Items.simple(Material.ENDER_EYE, "Modus: Spectator"),
                    click -> {
                        gm.setMode(viewer, GameMode.SPECTATOR);
                        viewer.sendMessage(Messages.ok("Spectator-Modus gesetzt."));
                    });
            set(13, Items.simple(Material.GRASS_BLOCK, "Modus: Creative"),
                    click -> {
                        gm.setMode(viewer, GameMode.CREATIVE);
                        viewer.sendMessage(Messages.ok("Creative-Modus gesetzt."));
                    });
        }

        if (services.config.get(services.settings.gmAllowTeleportMenu)) {
            set(15, Items.simple(Material.RECOVERY_COMPASS, "Zu Tribut teleportieren",
                            "Öffnet das Teleport-Menü"),
                    click -> services.spectator.openTeleportMenu(viewer));
        }

        if (admin) {
            set(16, Items.simple(Material.EMERALD, "Name hinzufügen",
                            "Fügt einen Spieler zur", "Spielmacher-Liste hinzu"),
                    click -> services.anvilInput.open(viewer,
                            Component.text("Spielername"), "",
                            input -> {
                                String name = input.trim();
                                if (!name.isEmpty()) {
                                    var errors = gm.addName(viewer.getName(), name);
                                    if (errors.isEmpty()) {
                                        viewer.sendMessage(Messages.ok(name
                                                + " zur Spielmacher-Liste hinzugefügt."));
                                    } else {
                                        errors.forEach(e -> viewer.sendMessage(Messages.error(e)));
                                    }
                                }
                                open();
                            },
                            this::open));
        }

        // Aktive Spielmacher
        int slot = 19;
        for (Player online : gm.onlineActive()) {
            if (slot >= 27) {
                break;
            }
            set(slot++, Items.simple(Material.PLAYER_HEAD, online.getName(),
                    "Aktiver Spielmacher (" + online.getGameMode() + ")"));
        }

        // Namensliste
        slot = 27;
        for (String name : gm.names()) {
            if (slot >= 45) {
                break;
            }
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Auf der Spielmacher-Liste", NamedTextColor.GRAY));
            if (admin) {
                lore.add(Component.text("Rechtsklick: entfernen", NamedTextColor.AQUA));
            }
            set(slot++, Items.of(Material.PAPER, Component.text(name, NamedTextColor.WHITE), lore),
                    admin ? click -> {
                        if (click.isRightClick()) {
                            var errors = gm.removeName(viewer.getName(), name);
                            if (errors.isEmpty()) {
                                viewer.sendMessage(Messages.ok(name + " von der Liste entfernt."));
                            } else {
                                errors.forEach(e -> viewer.sendMessage(Messages.error(e)));
                            }
                            refresh();
                        }
                    } : null);
        }

        set(49, Items.back(), click -> onBack.run());
        set(53, Items.close(), click -> viewer.closeInventory());
    }
}
