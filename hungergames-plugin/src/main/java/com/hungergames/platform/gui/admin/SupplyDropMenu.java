package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.SettingDefinition.SettingCategory;
import com.hungergames.platform.Messages;
import com.hungergames.platform.Perms;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.config.ConfigCategoryMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Supply-Drop-Steuerung in {@code /hg admin}: Zeitplan mit Auslöse-Status,
 * manueller Drop, Konfigurations-Schnellzugriff.
 */
public final class SupplyDropMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public SupplyDropMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 3, Component.text("Supply Drops"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        boolean enabled = services.config.get(services.settings.supplyDropsEnabled);

        List<Component> statusLore = new ArrayList<>();
        statusLore.add(Component.text("Status: " + services.supplyDrops.statusLine(),
                enabled ? NamedTextColor.YELLOW : NamedTextColor.RED));
        List<Duration> schedule = services.supplyDrops.schedule();
        var triggered = services.supplyDrops.triggeredIndices();
        for (int i = 0; i < schedule.size(); i++) {
            boolean done = triggered.contains(i);
            statusLore.add(Component.text((done ? "✔ " : "○ ") + schedule.get(i).toMinutes()
                            + "m Spielzeit", done ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        statusLore.add(Component.text("LootTable: "
                + services.config.get(services.settings.supplyDropLootTable), NamedTextColor.GRAY));
        set(4, Items.of(Material.CHEST_MINECART,
                Component.text("Kapitol-Lieferungen", NamedTextColor.GOLD), statusLore));

        set(11, Items.of(enabled ? Material.FIREWORK_ROCKET : Material.GRAY_DYE,
                        Component.text("Jetzt ausliefern",
                                enabled ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY),
                        Component.text(enabled
                                ? "Löst sofort eine Lieferung aus (mit Warnphase)"
                                : "Deaktiviert (events.supply-drops.enabled)", NamedTextColor.GRAY)),
                enabled ? click -> new ConfirmMenu(viewer, "Supply Drop auslösen",
                        List.of("Warnung an alle, Landung nach "
                                + services.config.get(services.settings.supplyDropWarningSeconds)
                                + "s."),
                        () -> services.supplyDrops.triggerNow(viewer.getName()).ifPresentOrElse(
                                error -> viewer.sendMessage(Messages.error(error)),
                                () -> viewer.sendMessage(Messages.ok("Lieferung unterwegs."))),
                        this::open).open() : null);

        if (Perms.isAdmin(viewer)) {
            set(15, Items.simple(Material.COMPARATOR, "Drops konfigurieren",
                            "Zeitplan, Radius, Effekte, LootTable ..."),
                    click -> new ConfigCategoryMenu(viewer, services.config,
                            SettingCategory.EVENTS, "Events & Drops", services.anvilInput,
                            this::open, services.borderGuard).open());
        }

        set(22, Items.back(), click -> onBack.run());
        set(26, Items.close(), click -> viewer.closeInventory());
    }
}
