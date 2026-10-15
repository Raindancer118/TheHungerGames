package com.hungergames.platform.gui.admin;

import com.hungergames.platform.Messages;
import com.hungergames.platform.Perms;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * System- und Debug-Menü (Admin): Reloads, Laufzeit-Infos, Speedup,
 * Test-Aktionen und der Fliptable-Reset hinter doppelter Bestätigung.
 */
public final class SystemDebugMenu extends Menu {

    private static final double[] SPEEDUP_STEPS = {1.0, 2.0, 4.0, 8.0};

    private final AdminServices services;
    private final Runnable onBack;

    public SystemDebugMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 6, Component.text("System & Debug"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        if (!Perms.isAdmin(viewer)) {
            viewer.sendMessage(Messages.error("Nur Admins dürfen das System-Menü nutzen."));
            onBack.run();
            return;
        }

        // === Status ===
        List<Component> statusLore = new ArrayList<>();
        statusLore.add(Component.text("Phase: " + services.session.phase(), NamedTextColor.GRAY));
        statusLore.add(Component.text("Rundenlog: "
                + (services.config.get(services.settings.roundLogEnabled)
                ? services.roundLog.currentFile().getName() : "deaktiviert"),
                NamedTextColor.GRAY));
        statusLore.add(Component.text("Loot: "
                + (services.lootTables.isDirty() ? "⚠ ungespeichert" : "gespeichert"),
                services.lootTables.isDirty() ? NamedTextColor.GOLD : NamedTextColor.GRAY));
        statusLore.add(Component.text("Virtuelle Zeit: ×" + services.virtualTime.multiplier()
                + (services.virtualTime.isRunning()
                ? " (" + services.virtualTime.elapsed().toMinutes() + "m)" : " (steht)"),
                NamedTextColor.GRAY));
        statusLore.add(Component.text("Audit-Log: audit.log (Config-Änderungen)",
                NamedTextColor.DARK_GRAY));
        boolean apiEnabled = services.config.get(services.settings.apiEnabled);
        statusLore.add(Component.text("HTTP-API: " + (apiEnabled
                        ? services.config.get(services.settings.apiBindAddress) + ":"
                        + services.config.get(services.settings.apiPort)
                        + (services.config.get(services.settings.apiReadOnly) ? " (read-only)" : "")
                        : "deaktiviert (api.enabled)"),
                apiEnabled ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
        set(4, Items.of(Material.REDSTONE, Component.text("System", NamedTextColor.GOLD), statusLore));

        // === Reloads ===
        set(10, Items.simple(Material.LEVER, "Loot neu laden",
                        "loot.yml von der Platte laden",
                        services.lootTables.isDirty() ? "⚠ Ungespeicherte Änderungen!" : ""),
                click -> {
                    if (services.lootTables.isDirty()) {
                        new ConfirmMenu(viewer, "Loot neu laden",
                                List.of("Ungespeicherte Loot-Änderungen gehen verloren."),
                                () -> {
                                    services.lootTables.loadFromDisk();
                                    com.hungergames.loot.LootConfig.getInstance().reload();
                                    viewer.sendMessage(Messages.ok("Loot neu geladen."));
                                    open();
                                }, this::open).open();
                    } else {
                        services.lootTables.loadFromDisk();
                        com.hungergames.loot.LootConfig.getInstance().reload();
                        viewer.sendMessage(Messages.ok("Loot neu geladen."));
                    }
                });

        set(11, Items.simple(Material.COMMAND_BLOCK, "Server-Whitelist anwenden",
                        "Turnier-Teilnehmer auf die", "Server-Whitelist übertragen"),
                click -> {
                    com.hungergames.player.WhitelistManager.getInstance().applyToServerWhitelist();
                    viewer.sendMessage(Messages.ok("Server-Whitelist aktualisiert."));
                });

        // === Speedup — derselbe Pfad wie /speedup (Welt-Ticks + VirtualTime + AutoStop) ===
        double current = com.hungergames.commands.SpeedupCommand.getCurrentMultiplier();
        int speedupSlot = 19;
        for (double step : SPEEDUP_STEPS) {
            boolean active = Math.abs(current - step) < 0.01;
            set(speedupSlot++, Items.of(active ? Material.LIME_DYE : Material.SUGAR,
                            Component.text("Spielzeit ×" + (int) step,
                                    active ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                            Component.text(step == 1.0 ? "Normalgeschwindigkeit"
                                    : "Beschleunigt Timer, Border, Drops, Tokens",
                                    NamedTextColor.GRAY)),
                    click -> {
                        runSpeedup(step == 1.0 ? "off" : String.valueOf((int) step));
                        services.roundLog.log("ADMIN", viewer.getName()
                                + " setzt die Spielzeit auf ×" + (int) step);
                        refresh();
                    });
        }

        // === Tests ===
        set(28, Items.simple(Material.NOTE_BLOCK, "Sounds testen",
                        "Öffnet das Sound-Menü (Rechtsklick hört Probe)"),
                click -> new SoundsMenu(viewer, services, this::open).open());
        set(29, Items.simple(Material.CHEST, "Loot testen",
                        "Öffnet den Loot-Test (chest-Tabelle)"),
                click -> new com.hungergames.platform.gui.loot.LootTestMenu(viewer, services,
                        "chest", this::open).open());
        set(30, Items.simple(Material.FIREWORK_ROCKET, "Supply Drop testen",
                        "Löst eine Lieferung aus (nur RUNNING)"),
                click -> services.supplyDrops.triggerNow(viewer.getName()).ifPresentOrElse(
                        error -> viewer.sendMessage(Messages.error(error)),
                        () -> viewer.sendMessage(Messages.ok("Test-Lieferung unterwegs."))));
        set(31, Items.simple(Material.NETHER_STAR, "Sponsor-Token testen",
                        "Gibt dir 1 echtes Sponsor Token"),
                click -> {
                    services.sponsorTokens.giveManually(viewer.getName(), viewer, 1);
                    viewer.sendMessage(Messages.ok("1 Sponsor Token erhalten."));
                });
        set(32, Items.simple(Material.SPYGLASS, "Arena-Loot füllen",
                        "Füllt alle Arena-Container neu (wie /test-loot)"),
                click -> new ConfirmMenu(viewer, "Arena-Loot füllen",
                        List.of("Scannt und befüllt alle Container um das Arena-Zentrum.",
                                "Bereits gefüllte Container werden übersprungen."),
                        () -> {
                            new com.hungergames.loot.LootManager().fillArenaContainers();
                            viewer.sendMessage(Messages.ok("Arena-Container befüllt."));
                        }, this::open).open());

        // === Fliptable — doppelt bestätigt ===
        set(42, Items.of(Material.TNT,
                        Component.text("FLIPTABLE — Server-Reset", NamedTextColor.DARK_RED),
                        Component.text("Löscht ALLE Welten und Spieldaten,", NamedTextColor.RED),
                        Component.text("kickt alle Spieler, stoppt den Server!", NamedTextColor.RED)),
                click -> new ConfirmMenu(viewer, "FLIPTABLE — wirklich?",
                        List.of("Alle Welten (world, nether, end) werden GELÖSCHT.",
                                "Whitelist und Teams werden zurückgesetzt.",
                                "Der Server fährt herunter.", "Das ist NICHT rückgängig zu machen!"),
                        () -> new ConfirmMenu(viewer, "FLIPTABLE — letzte Warnung",
                                List.of("Letzte Chance zum Abbrechen.",
                                        "Danach: (╯°□°)╯︵ ┻━┻"),
                                () -> {
                                    services.roundLog.log("ADMIN", viewer.getName()
                                            + " löst FLIPTABLE aus (GUI, doppelt bestätigt)");
                                    var command = Bukkit.getPluginCommand("fliptable");
                                    new com.hungergames.commands.FliptableCommand().onCommand(
                                            viewer, command, "fliptable", new String[]{"confirm"});
                                }, this::open).open(),
                        this::open).open());

        set(49, Items.back(), click -> onBack.run());
        set(53, Items.close(), click -> viewer.closeInventory());
    }

    /** Ruft den /speedup-Executor direkt (kein Chat-Command-Umweg). */
    private void runSpeedup(String arg) {
        var command = Bukkit.getPluginCommand("speedup");
        new com.hungergames.commands.SpeedupCommand().onCommand(viewer, command, "speedup",
                new String[]{arg});
    }
}
