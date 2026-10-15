package com.hungergames.platform.gui.admin;

import com.hungergames.platform.Messages;
import com.hungergames.platform.game.PreflightCheckService;
import com.hungergames.platform.game.PreflightCheckService.CheckResult;
import com.hungergames.platform.game.PreflightCheckService.Severity;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Preflight-Check-GUI: Statusliste grün/gelb/rot. Der Startbutton ist bei
 * roten Fehlern gesperrt; Warnungen kann der Admin bewusst ignorieren
 * (Bestätigung im Confirm-Dialog).
 */
public final class PreflightMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public PreflightMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 6, Component.text("Preflight-Check"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        List<CheckResult> results = services.preflight.runAll();
        boolean blocked = services.preflight.hasBlockingErrors(results);
        long warnings = results.stream().filter(r -> r.severity() == Severity.WARNING).count();

        int slot = 9;
        for (CheckResult result : results) {
            if (slot >= 45) {
                break;
            }
            set(slot++, Items.of(iconFor(result.severity()),
                    Component.text(result.name(), colorFor(result.severity())),
                    Component.text(result.detail(), NamedTextColor.GRAY)));
        }

        set(4, Items.of(blocked ? Material.RED_CONCRETE
                        : warnings > 0 ? Material.YELLOW_CONCRETE : Material.LIME_CONCRETE,
                Component.text(blocked ? "Start blockiert"
                                : warnings > 0 ? "Startklar mit Warnungen" : "Startklar",
                        blocked ? NamedTextColor.RED
                                : warnings > 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN),
                Component.text(blocked
                        ? "Rote Fehler müssen behoben werden"
                        : warnings > 0 ? warnings + " Warnung(en) — bewusst ignorierbar"
                        : "Alle Checks bestanden", NamedTextColor.GRAY)));

        set(46, Items.simple(Material.SUNFLOWER, "Neu prüfen"),
                click -> refresh());

        if (!blocked && services.gameControl.canStartup()) {
            set(50, Items.of(Material.REDSTONE_LAMP,
                            Component.text("Startsequenz auslösen", NamedTextColor.GREEN),
                            Component.text(warnings > 0
                                    ? "Trotz " + warnings + " Warnung(en) fortfahren"
                                    : "Tribute in die Röhren", NamedTextColor.GRAY)),
                    click -> new ConfirmMenu(viewer, "Startsequenz auslösen",
                            warnings > 0
                                    ? List.of("Es gibt " + warnings + " Warnung(en).",
                                    "Diese werden bewusst ignoriert.")
                                    : List.of("Alle Checks bestanden."),
                            () -> {
                                viewer.closeInventory();
                                services.gameControl.startup(viewer);
                            }, this::open).open());
        } else if (!blocked && services.gameControl.canStart()) {
            set(50, Items.of(Material.LIME_CONCRETE,
                            Component.text("Spiele starten", NamedTextColor.GREEN),
                            Component.text(warnings > 0
                                    ? "Trotz " + warnings + " Warnung(en) starten"
                                    : "Countdown beginnt", NamedTextColor.GRAY)),
                    click -> new ConfirmMenu(viewer, "Spiele starten",
                            warnings > 0
                                    ? List.of("Es gibt " + warnings + " Warnung(en).",
                                    "Diese werden bewusst ignoriert.")
                                    : List.of("Alle Checks bestanden."),
                            () -> {
                                viewer.closeInventory();
                                services.gameControl.start(viewer);
                            }, this::open).open());
        } else if (!blocked) {
            set(50, Items.simple(Material.GRAY_DYE, "Kein Startschritt verfügbar",
                    "Aktuelle Phase: " + services.session.phase()));
        }

        set(49, Items.back(), click -> onBack.run());

        if (blocked) {
            viewer.sendMessage(Messages.error("Preflight: "
                    + results.stream().filter(r -> r.severity() == Severity.ERROR).count()
                    + " Fehler blockieren den Start."));
        }
    }

    private static Material iconFor(Severity severity) {
        return switch (severity) {
            case OK -> Material.LIME_DYE;
            case WARNING -> Material.YELLOW_DYE;
            case ERROR -> Material.RED_DYE;
        };
    }

    private static NamedTextColor colorFor(Severity severity) {
        return switch (severity) {
            case OK -> NamedTextColor.GREEN;
            case WARNING -> NamedTextColor.YELLOW;
            case ERROR -> NamedTextColor.RED;
        };
    }
}
