package com.hungergames.platform.gui.admin;

import com.hungergames.api.model.GamePhase;
import com.hungergames.platform.Messages;
import com.hungergames.platform.Perms;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.sim.MannequinSimService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Test-Simulation: Mannequin-Tribute spawnen, eliminieren und aufräumen. Die
 * Attrappen sind echte Teilnehmer auf Auto-Test-Teams und durchlaufen dieselben
 * Flows wie echte Spieler.
 */
public final class SimulationMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public SimulationMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 4, Component.text("Test-Simulation (Mannequins)"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        MannequinSimService sim = services.mannequinSim;
        boolean admin = Perms.isAdmin(viewer);
        GamePhase phase = services.session.phase();

        set(4, Items.of(Material.ARMOR_STAND,
                Component.text("Mannequin-Simulation", NamedTextColor.GOLD),
                Component.text("Attrappen: " + sim.mannequinCount()
                        + " (lebend " + sim.aliveCount() + ")", NamedTextColor.YELLOW),
                Component.text("Test-Teams: " + sim.teamCount(), NamedTextColor.YELLOW),
                Component.text("Phase: " + phase, NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Spawnen vor der Startsequenz, dann /startup + /start,",
                        NamedTextColor.GRAY),
                Component.text("dann die Attrappen totschlagen oder hier eliminieren.",
                        NamedTextColor.GRAY)));

        if (admin) {
            spawnButton(10, 1);
            spawnButton(11, 5);
            spawnButton(12, 10);

            boolean canSpawn = sim.canSpawn();
            if (!canSpawn) {
                set(13, Items.of(Material.BARRIER,
                        Component.text("Spawn gesperrt", NamedTextColor.RED),
                        Component.text("Nur vor der Startsequenz möglich.", NamedTextColor.GRAY)));
            }

            // Eliminieren (nur RUNNING)
            set(15, Items.of(Material.IRON_SWORD,
                    Component.text("Eine Attrappe eliminieren", NamedTextColor.RED),
                    Component.text("Löst Kanonenschuss + Winner-Check aus", NamedTextColor.GRAY),
                    Component.text(phase == GamePhase.RUNNING ? "Bereit" : "Nur im laufenden Spiel",
                            phase == GamePhase.RUNNING ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)),
                    click -> {
                        sim.eliminateOne(viewer).ifPresentOrElse(
                                msg -> viewer.sendMessage(Messages.info(msg)),
                                () -> refresh());
                        refresh();
                    });

            set(16, Items.of(Material.LAVA_BUCKET,
                    Component.text("Alle Attrappen entfernen", NamedTextColor.RED),
                    Component.text("Entfernt Entities, Teilnehmer & Test-Teams", NamedTextColor.GRAY)),
                    click -> {
                        int removed = sim.clear();
                        viewer.sendMessage(Messages.ok(removed + " Attrappen entfernt."));
                        refresh();
                    });
        } else {
            set(13, Items.of(Material.BARRIER,
                    Component.text("Nur Admins", NamedTextColor.RED)));
        }

        set(31, Items.back(), click -> onBack.run());
    }

    private void spawnButton(int slot, int amount) {
        set(slot, Items.of(Material.PLAYER_HEAD,
                Component.text("Spawn +" + amount, NamedTextColor.GREEN),
                Component.text(amount + " Mannequin-Tribut(e) auf Test-Teams", NamedTextColor.GRAY)),
                click -> {
                    String result = services.mannequinSim.spawn(viewer, amount);
                    viewer.sendMessage(Messages.info(result));
                    refresh();
                });
    }
}
