package com.hungergames.platform.gui.admin;

import com.hungergames.platform.Messages;
import com.hungergames.platform.event.MonsterWaveService;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Monster-Wellen an der eigenen Position starten/stoppen. „Start hier" nutzt die
 * konfigurierten Standardwerte; die Schnellwahl-Buttons starten typische Monster.
 */
public final class MonsterWaveMenu extends Menu {

    private record Quick(String mob, Material icon, String label) {
    }

    private static final List<Quick> QUICK = List.of(
            new Quick("ZOMBIE", Material.ZOMBIE_HEAD, "Zombies"),
            new Quick("SKELETON", Material.SKELETON_SKULL, "Skelette"),
            new Quick("SPIDER", Material.COBWEB, "Spinnen"),
            new Quick("CREEPER", Material.CREEPER_HEAD, "Creeper"),
            new Quick("BLAZE", Material.BLAZE_POWDER, "Blazes"),
            new Quick("PIGLIN_BRUTE", Material.GOLDEN_AXE, "Piglin-Brutes"));

    private final AdminServices services;
    private final Runnable onBack;

    public MonsterWaveMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 4, Component.text("Monster-Wellen"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        MonsterWaveService svc = services.monsterWaves;
        set(4, Items.of(Material.ZOMBIE_HEAD,
                Component.text("Monster-Wellen", NamedTextColor.RED),
                Component.text("Standard: " + svc.defaultWaves() + " Wellen à "
                        + svc.defaultCount() + " " + svc.defaultMob(), NamedTextColor.YELLOW),
                Component.text("Intervall: " + svc.defaultInterval() + "s", NamedTextColor.GRAY),
                Component.text("Laufende Serien: " + svc.activeSeries(), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Spawnt an deiner Position.", NamedTextColor.DARK_GRAY)));

        // Start mit Standard-Mob
        set(10, Items.of(Material.ZOMBIE_SPAWN_EGG,
                Component.text("Start hier (Standard)", NamedTextColor.GREEN),
                Component.text(svc.defaultMob() + " · " + svc.defaultWaves() + " Wellen",
                        NamedTextColor.GRAY)),
                click -> startHere(svc.defaultMob()));

        int slot = 12;
        for (Quick quick : QUICK) {
            if (slot > 16) {
                break;
            }
            set(slot++, Items.of(quick.icon(),
                    Component.text("Start: " + quick.label(), NamedTextColor.GREEN),
                    Component.text(svc.defaultWaves() + " Wellen à " + svc.defaultCount(),
                            NamedTextColor.GRAY)),
                    click -> startHere(quick.mob()));
        }

        set(22, Items.of(Material.BARRIER,
                Component.text("Alle Wellen stoppen", NamedTextColor.RED),
                Component.text(svc.activeSeries() + " laufende Serien", NamedTextColor.GRAY)),
                click -> {
                    int stopped = svc.stopAll();
                    viewer.sendMessage(Messages.ok(stopped + " Wellen-Serie(n) gestoppt."));
                    refresh();
                });

        set(31, Items.back(), click -> onBack.run());
    }

    private void startHere(String mob) {
        MonsterWaveService svc = services.monsterWaves;
        svc.start(viewer.getLocation(), mob, svc.defaultCount(), svc.defaultWaves(),
                svc.defaultInterval(), viewer.getName()).ifPresentOrElse(
                        error -> viewer.sendMessage(Messages.error(error)),
                        () -> {
                            viewer.sendMessage(Messages.ok(svc.defaultWaves() + " " + mob
                                    + "-Wellen gestartet."));
                            refresh();
                        });
    }
}
