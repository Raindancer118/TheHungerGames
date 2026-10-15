package com.hungergames.platform.gui.admin;

import com.hungergames.api.model.GamePhase;
import com.hungergames.platform.Messages;
import com.hungergames.platform.Perms;
import com.hungergames.platform.game.GameControlService;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Spielsteuerung innerhalb von {@code /hg admin}: Phasenfortschritt (Init →
 * Startsequenz → Start), Runde beenden/zurücksetzen und Schnellzugriffe.
 * Aktionen rufen den {@link GameControlService} direkt.
 */
public final class GameControlMenu extends Menu {

    private final AdminServices services;

    public GameControlMenu(Player viewer, AdminServices services) {
        super(viewer, 5, Component.text("Spielsteuerung"));
        this.services = services;
    }

    @Override
    protected void build() {
        GameControlService control = services.gameControl;
        GamePhase phase = services.session.phase();
        boolean admin = Perms.isAdmin(viewer);

        set(4, statusItem(phase));

        // === Phasenfortschritt ===
        action(19, Material.LIGHTNING_ROD, "Arena initialisieren",
                List.of("Baut die Arena an deiner Position", "Phase: NOT_INITIALIZED/FINISHED → LOBBY"),
                control.canInit(), this::askInitPlayerCount);

        action(20, Material.REDSTONE_LAMP, "Startsequenz",
                List.of("Tribute in die Röhren und nach oben", "Phase: LOBBY → STARTUP → READY"),
                control.canStartup(), () -> {
                    viewer.closeInventory();
                    control.startup(viewer);
                });

        action(21, Material.LIME_CONCRETE, "Spiele starten",
                List.of("Countdown und Spielbeginn", "Phase: READY → RUNNING"),
                control.canStart(), () -> new ConfirmMenu(viewer, "Spiele starten",
                        List.of("Startet den Countdown ("
                                + services.config.get(services.settings.countdown) + "s)",
                                "und eröffnet die Hungerspiele."),
                        () -> control.start(viewer), this::open).open());

        action(23, Material.RED_CONCRETE, "Runde beenden",
                List.of("Wertet die Runde sofort wie bei Zeitablauf", "Phase: RUNNING → FINISHED"),
                control.canEndRound(), () -> new ConfirmMenu(viewer, "Runde beenden",
                        List.of("Die laufende Runde wird sofort beendet.",
                                "Der Sieger wird nach Zeitablauf-Regeln ermittelt."),
                        () -> control.endRound(viewer.getName()), this::open).open());

        if (admin) {
            action(24, Material.TNT, "Runde zurücksetzen",
                    List.of("Setzt Arena-Geometrie und Rundenzustand zurück.",
                            "Teilnehmer und Teams bleiben registriert.",
                            "Danach: /init für die nächste Runde"),
                    phase != GamePhase.NOT_INITIALIZED,
                    () -> new ConfirmMenu(viewer, "Runde zurücksetzen",
                            List.of("Eliminierungen, Kills und Sieger werden verworfen.",
                                    "Eine laufende Runde wird dabei ABGEBROCHEN.",
                                    "Teilnehmer und Teams bleiben erhalten."),
                            () -> {
                                services.gameControl.prepareNextRound(viewer.getName());
                                viewer.sendMessage(Messages.ok(
                                        "Runde zurückgesetzt — /init startet die nächste Runde."));
                            }, this::open).open());
        } else {
            set(24, Items.of(Material.GRAY_STAINED_GLASS_PANE,
                    Component.text("Runde zurücksetzen", NamedTextColor.DARK_GRAY),
                    Component.text("Erfordert " + Perms.ADMIN, NamedTextColor.RED)));
        }

        // === Schnellzugriffe ===
        if (services.preflight != null) {
            set(29, Items.simple(Material.OBSERVER, "Preflight-Check",
                            "Prüft, ob die Runde starten kann"),
                    click -> new PreflightMenu(viewer, services, this::open).open());
        }

        set(30, Items.simple(Material.ENDER_EYE, "Spectator-Teleport",
                        "Zu lebenden Tributen teleportieren"),
                click -> services.spectator.openTeleportMenu(viewer));

        set(31, Items.simple(Material.PLAYER_HEAD, "Tribute",
                        services.session.participants().aliveCount() + " lebend / "
                                + services.session.participants().all().size() + " registriert",
                        admin ? "Klick: Tribut-Verwaltung" : "Erfordert " + Perms.ADMIN),
                admin ? click -> new TributesMenu(viewer, services, this::open).open() : null);

        if (services.mannequinSim != null) {
            set(32, Items.simple(Material.ARMOR_STAND, "Test-Simulation",
                            services.mannequinSim.mannequinCount() + " Attrappen aktiv",
                            admin ? "Klick: Mannequins spawnen/simulieren" : "Erfordert " + Perms.ADMIN),
                    admin ? click -> new SimulationMenu(viewer, services, this::open).open() : null);
        }

        if (services.monsterWaves != null) {
            set(33, Items.simple(Material.ZOMBIE_HEAD, "Monster-Wellen",
                            services.monsterWaves.activeSeries() + " laufende Serien",
                            "Klick: Wellen an deiner Position"),
                    click -> new MonsterWaveMenu(viewer, services, this::open).open());
        }

        set(36, Items.back(), click -> new AdminMainMenu(viewer, services).open());
        set(44, Items.close(), click -> viewer.closeInventory());
    }

    private void askInitPlayerCount() {
        // Bewusst keine GUI: Spielerzahl wird wie bei /init im Chat abgefragt.
        viewer.closeInventory();
        viewer.sendMessage(Messages.info("Wie viele Tribute nehmen teil? (Zahl im Chat eingeben)"));
        com.hungergames.utils.ChatInputHandler.getInstance().awaitInput(viewer.getUniqueId(), input -> {
            int count;
            try {
                count = Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                viewer.sendMessage(Messages.error("\"" + input + "\" ist keine Zahl — bitte erneut versuchen."));
                return;
            }
            services.gameControl.init(viewer, count);
        });
    }

    private void action(int slot, Material icon, String label, List<String> description,
                        boolean available, Runnable onClick) {
        List<Component> lore = new ArrayList<>();
        for (String line : description) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        if (!available) {
            lore.add(Component.text("In der aktuellen Phase nicht möglich ("
                    + services.session.phase() + ")", NamedTextColor.RED));
            set(slot, Items.of(Material.GRAY_DYE, Component.text(label, NamedTextColor.DARK_GRAY), lore));
            return;
        }
        set(slot, Items.of(icon, Component.text(label, NamedTextColor.YELLOW), lore),
                click -> onClick.run());
    }

    private org.bukkit.inventory.ItemStack statusItem(GamePhase phase) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Phase: ", NamedTextColor.GRAY)
                .append(Component.text(phase.name(), AdminMainMenu.phaseColor(phase))));
        lore.add(Component.text("Tribute lebend: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(services.session.participants().aliveCount()),
                        NamedTextColor.YELLOW)));
        if (phase == GamePhase.RUNNING) {
            Duration elapsed = services.virtualTime.elapsed();
            Duration total = services.config.get(services.settings.gameDuration);
            lore.add(Component.text("Spielzeit: ", NamedTextColor.GRAY)
                    .append(Component.text(elapsed.toMinutes() + "m / " + total.toMinutes() + "m",
                            NamedTextColor.YELLOW)));
            if (services.virtualTime.multiplier() > 1.0) {
                lore.add(Component.text("Speedup aktiv: ×" + services.virtualTime.multiplier(),
                        NamedTextColor.LIGHT_PURPLE));
            }
        }
        return Items.of(Material.NETHER_STAR,
                Component.text("Spielstatus", NamedTextColor.GOLD), lore);
    }
}
