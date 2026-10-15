package com.hungergames.platform.gui.loot;

import com.hungergames.api.model.GamePhase;
import com.hungergames.platform.Messages;
import com.hungergames.platform.Perms;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.admin.AdminServices;
import com.hungergames.platform.gui.admin.ConfirmMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Einstieg in die Loot-Verwaltung: Tabellenliste, neue Tabelle, Validierung,
 * Speichern (mit Backup) und Neuladen. Spielmacher sehen und testen; ändern,
 * speichern und neuladen ist Admins vorbehalten.
 */
public final class LootMainMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public LootMainMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 3, Component.text("Loot-Verwaltung"));
        this.services = services;
        this.onBack = onBack;
    }

    /** Darf dieser Spieler gerade LootTables ändern? */
    public static boolean canEdit(Player player, AdminServices services) {
        if (!Perms.isAdmin(player)
                || !services.config.get(services.settings.lootEditorEnabled)) {
            return false;
        }
        return services.config.get(services.settings.lootEditorRuntimeEdits)
                || services.session.phase() != GamePhase.RUNNING;
    }

    @Override
    protected void build() {
        var repo = services.lootTables;
        boolean edit = canEdit(viewer, services);

        List<Component> statusLore = new ArrayList<>();
        statusLore.add(Component.text(repo.tableNames().size() + " Tabellen", NamedTextColor.GRAY));
        statusLore.add(repo.isDirty()
                ? Component.text("⚠ Ungespeicherte Änderungen!", NamedTextColor.GOLD)
                : Component.text("Alles gespeichert", NamedTextColor.GREEN));
        if (!edit) {
            statusLore.add(Component.text(Perms.isAdmin(viewer)
                            ? "Bearbeitung derzeit gesperrt (Editor/Runde)"
                            : "Nur Ansehen/Testen (Spielmacher)",
                    NamedTextColor.RED));
        }
        set(4, Items.of(Material.CHEST, Component.text("Loot", NamedTextColor.GOLD), statusLore));

        set(10, Items.simple(Material.BOOKSHELF, "Tabellen anzeigen",
                        "Alle LootTables ansehen, testen" + (edit ? ", bearbeiten" : "")),
                click -> new LootTableListMenu(viewer, services, this::open).open());

        if (edit) {
            set(11, Items.simple(Material.EMERALD, "Neue Tabelle",
                            "Legt eine leere LootTable an"),
                    click -> services.anvilInput.open(viewer, Component.text("Tabellenname"), "",
                            input -> {
                                if (!input.isBlank()) {
                                    services.lootTables.createTable(input).ifPresentOrElse(
                                            error -> viewer.sendMessage(Messages.error(error)),
                                            () -> viewer.sendMessage(Messages.ok(
                                                    "Tabelle angelegt (noch nicht gespeichert).")));
                                }
                                open();
                            }, this::open));
        }

        set(12, Items.simple(Material.OBSERVER, "Validieren",
                        "Prüft alle Tabellen auf Fehler"),
                click -> {
                    List<String> problems = repo.validateAll();
                    if (problems.isEmpty()) {
                        viewer.sendMessage(Messages.ok("Alle LootTables sind gültig."));
                    } else {
                        problems.forEach(problem ->
                                viewer.sendMessage(Messages.error(problem)));
                    }
                });

        if (edit) {
            set(14, Items.of(repo.isDirty() ? Material.WRITABLE_BOOK : Material.BOOK,
                            Component.text("Speichern",
                                    repo.isDirty() ? NamedTextColor.GOLD : NamedTextColor.GRAY),
                            Component.text(repo.isDirty()
                                            ? "Schreibt Änderungen in die loot.yml"
                                            + (services.config.get(services.settings.lootEditorBackup)
                                            ? " (mit Backup)" : "")
                                            : "Keine ungespeicherten Änderungen",
                                    NamedTextColor.GRAY)),
                    click -> {
                        List<String> errors = repo.save(viewer.getName());
                        if (errors.isEmpty()) {
                            viewer.sendMessage(Messages.ok("Loot-Konfiguration gespeichert."));
                        } else {
                            errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
                        }
                        refresh();
                    });

            set(15, Items.simple(Material.LEVER, "Neu laden",
                            "Lädt die loot.yml frisch von der Platte",
                            repo.isDirty() ? "⚠ Verwirft ungespeicherte Änderungen!" : ""),
                    click -> {
                        if (repo.isDirty()) {
                            new ConfirmMenu(viewer, "Loot neu laden",
                                    List.of("Alle ungespeicherten Änderungen gehen verloren."),
                                    () -> {
                                        repo.loadFromDisk();
                                        com.hungergames.loot.LootConfig.getInstance().reload();
                                        viewer.sendMessage(Messages.ok("Loot neu geladen."));
                                        open();
                                    }, this::open).open();
                        } else {
                            repo.loadFromDisk();
                            com.hungergames.loot.LootConfig.getInstance().reload();
                            viewer.sendMessage(Messages.ok("Loot neu geladen."));
                            refresh();
                        }
                    });
        }

        set(22, Items.back(), click -> onBack.run());
        set(26, Items.close(), click -> viewer.closeInventory());
    }
}
