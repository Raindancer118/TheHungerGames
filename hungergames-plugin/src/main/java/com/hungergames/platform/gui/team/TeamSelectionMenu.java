package com.hungergames.platform.gui.team;

import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.service.TeamOperationResult;
import com.hungergames.game.GameSession;
import com.hungergames.game.TeamRules;
import com.hungergames.game.port.GameEventPublisher.MembershipCause;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.AnvilInput;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Teamwahl für Spieler in der Startphase: Team beitreten/wechseln/verlassen,
 * eigenes Team erstellen (falls erlaubt) und Farbwahl öffnen.
 */
public final class TeamSelectionMenu extends Menu {

    private final GameSession session;
    private final Supplier<TeamRules> rules;
    private final AnvilInput anvilInput;

    public TeamSelectionMenu(Player viewer, GameSession session, Supplier<TeamRules> rules,
                             AnvilInput anvilInput) {
        super(viewer, 6, Component.text("Teamwahl"));
        this.session = session;
        this.rules = rules;
        this.anvilInput = anvilInput;
    }

    @Override
    protected void build() {
        if (!session.teamRegistry().enabled()) {
            set(22, Items.simple(Material.BARRIER, "Teams sind deaktiviert",
                    "In diesem Spiel spielt jeder für sich (Solo-Modus)."));
            set(53, Items.close(), click -> viewer.closeInventory());
            return;
        }

        TeamRules r = rules.get();
        Optional<Team> ownTeam = session.teamRegistry().teamOf(viewer.getUniqueId());
        List<Team> teams = session.teamRegistry().all();

        int slot = 0;
        for (Team team : teams) {
            if (slot >= 45) {
                break;
            }
            boolean isMember = team.isMember(viewer.getUniqueId());
            boolean full = r.maxTeamSize() > 0 && team.size() >= r.maxTeamSize();

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Mitglieder: " + team.size()
                    + (r.maxTeamSize() > 0 ? "/" + r.maxTeamSize() : ""), NamedTextColor.GRAY));
            for (var member : team.members()) {
                String name = session.participants().nameOf(member).orElse("?");
                boolean captain = team.captain().filter(member::equals).isPresent();
                lore.add(Component.text(" • " + name + (captain ? " ♛" : ""), NamedTextColor.DARK_GRAY));
            }
            if (isMember) {
                lore.add(Component.text("Dein Team", NamedTextColor.GREEN));
                if (r.playersCanChooseColor()) {
                    lore.add(Component.text("Klick: Farbe wählen", NamedTextColor.YELLOW));
                }
            } else if (full) {
                lore.add(Component.text("Voll", NamedTextColor.RED));
            } else {
                lore.add(Component.text("Klick: beitreten", NamedTextColor.YELLOW));
            }

            Material wool = Material.valueOf(team.color().name() + "_WOOL");
            set(slot++, Items.of(wool,
                            Component.text(team.name(), team.color().textColor()), lore),
                    click -> {
                        if (isMember) {
                            if (r.playersCanChooseColor() && mayPickColor(team)) {
                                new ColorPickerMenu(viewer, session, team.id(), this::open).open();
                            }
                        } else {
                            TeamOperationResult result = session.teamAssign(
                                    viewer.getUniqueId(), team.id(), MembershipCause.PLAYER);
                            viewer.sendMessage(result.isSuccess()
                                    ? Messages.ok("Du bist Team \"" + team.name() + "\" beigetreten.")
                                    : Messages.error(Messages.describe(result)));
                            refresh();
                        }
                    });
        }

        if (r.playersCanCreateTeams()) {
            set(45, Items.simple(Material.NETHER_STAR, "Team erstellen",
                            "Öffnet die Namenseingabe"),
                    click -> anvilInput.open(viewer, Component.text("Teamname"), "Mein Team",
                            name -> {
                                if (name.isBlank()) {
                                    viewer.sendMessage(Messages.error("Der Teamname darf nicht leer sein."));
                                    open();
                                    return;
                                }
                                var result = session.teamCreate(name, null);
                                if (result.status().isSuccess()) {
                                    session.teamAssign(viewer.getUniqueId(),
                                            result.team().orElseThrow().id(), MembershipCause.PLAYER);
                                    viewer.sendMessage(Messages.ok("Team \"" + name + "\" erstellt."));
                                } else {
                                    viewer.sendMessage(Messages.error(Messages.describe(result.status())));
                                }
                                open();
                            },
                            this::open));
        }

        if (ownTeam.isPresent()) {
            set(49, Items.simple(Material.RED_BED, "Team verlassen",
                            "Aktuell: " + ownTeam.get().name()),
                    click -> {
                        TeamOperationResult result = session.teamRemovePlayer(
                                viewer.getUniqueId(), MembershipCause.PLAYER);
                        viewer.sendMessage(result.isSuccess()
                                ? Messages.ok("Du hast dein Team verlassen.")
                                : Messages.error(Messages.describe(result)));
                        refresh();
                    });
        }

        set(53, Items.close(), click -> viewer.closeInventory());
    }

    /** Farbe wählen darf: jedes Mitglied — oder nur der Captain, falls aktiv. */
    private boolean mayPickColor(Team team) {
        TeamRules r = rules.get();
        if (!r.captainEnabled()) {
            return true;
        }
        return team.captain().filter(viewer.getUniqueId()::equals).isPresent();
    }
}
