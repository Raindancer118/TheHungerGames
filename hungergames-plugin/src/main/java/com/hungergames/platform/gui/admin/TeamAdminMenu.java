package com.hungergames.platform.gui.admin;

import com.hungergames.api.model.Team;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Team-Administration: Teams anlegen/löschen, zufällige Verteilung.
 * Die Team-Zuweisung einzelner Spieler bleibt über {@code /hg team assign}
 * bzw. die Spieler-Teamwahl erreichbar.
 */
public final class TeamAdminMenu extends Menu {

    private final AdminServices services;
    private final Runnable onBack;

    public TeamAdminMenu(Player viewer, AdminServices services, Runnable onBack) {
        super(viewer, 6, Component.text("Team-Verwaltung"));
        this.services = services;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        if (!services.session.teamRegistry().enabled()) {
            set(22, Items.simple(Material.BARRIER, "Teams sind deaktiviert",
                    "Solo-Modus: jeder Tribut spielt für sich.",
                    "Aktivieren über /hg config → Teams (teams.enabled)."));
            set(49, Items.back(), click -> onBack.run());
            return;
        }

        int slot = 0;
        for (Team team : services.session.teamRegistry().all()) {
            if (slot >= 45) {
                break;
            }
            addTeam(slot++, team);
        }

        set(46, Items.simple(Material.EMERALD, "Team erstellen",
                        "Neues Team mit freier Farbe anlegen"),
                click -> askTeamName());
        set(47, Items.simple(Material.ENDER_PEARL, "Zufällig verteilen",
                        "Verteilt alle teamlosen Tribute auf Teams"),
                click -> {
                    int assigned = services.session.teamAssignRandomly();
                    services.roundLog.log("ADMIN", viewer.getName() + " verteilt "
                            + assigned + " Tribute zufällig auf Teams");
                    viewer.sendMessage(Messages.ok(assigned + " Tribute zufällig verteilt."));
                    refresh();
                });
        set(49, Items.back(), click -> onBack.run());
    }

    private void addTeam(int slot, Team team) {
        Material banner = Material.matchMaterial(team.color().name() + "_BANNER");
        if (banner == null) {
            banner = Material.WHITE_BANNER;
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Farbe: " + team.color().name(), team.color().namedTextColor()));
        if (team.members().isEmpty()) {
            lore.add(Component.text("Keine Mitglieder", NamedTextColor.DARK_GRAY));
        } else {
            for (var member : team.members()) {
                String name = services.session.participants().nameOf(member).orElse("?");
                boolean alive = services.session.participants().isAlive(member);
                lore.add(Component.text("• " + name
                                + (team.captain().map(member::equals).orElse(false) ? " (Captain)" : ""),
                        alive ? NamedTextColor.GRAY : NamedTextColor.DARK_RED));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text("Rechtsklick: Team löschen", NamedTextColor.AQUA));

        set(slot, Items.of(banner, Component.text(team.name(), team.color().namedTextColor()), lore),
                click -> {
                    if (click.isRightClick()) {
                        new ConfirmMenu(viewer, "Team \"" + team.name() + "\" löschen",
                                List.of("Alle Mitglieder werden teamlos.",
                                        "Die Farbe wird wieder frei."),
                                () -> {
                                    var result = services.session.teamDelete(team.id());
                                    services.roundLog.log("ADMIN", viewer.getName()
                                            + " löscht Team " + team.name());
                                    viewer.sendMessage(result.isSuccess()
                                            ? Messages.ok("Team \"" + team.name() + "\" gelöscht.")
                                            : Messages.error(Messages.describe(result)));
                                    open();
                                }, this::open).open();
                    }
                });
    }

    private void askTeamName() {
        services.anvilInput.open(viewer, Component.text("Teamname"), "",
                input -> {
                    String name = input.trim();
                    if (name.isEmpty()) {
                        open();
                        return;
                    }
                    var result = services.session.teamCreate(name, null);
                    if (result.status().isSuccess()) {
                        services.roundLog.log("ADMIN", viewer.getName() + " erstellt Team " + name);
                        viewer.sendMessage(Messages.ok("Team \"" + name + "\" erstellt ("
                                + result.team().orElseThrow().color().name().toLowerCase(Locale.ROOT) + ")."));
                    } else {
                        viewer.sendMessage(Messages.error(Messages.describe(result.status())));
                    }
                    open();
                },
                this::open);
    }
}
