package com.hungergames.platform.gui.team;

import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.service.TeamOperationResult;
import com.hungergames.game.GameSession;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;

/**
 * Farbwahl eines Teams. Bereits vergebene Farben sind ausgegraut und zeigen
 * in der Lore, welches Team sie hält — Doppelvergabe ist ausgeschlossen.
 */
public final class ColorPickerMenu extends Menu {

    private final GameSession session;
    private final TeamId teamId;
    private final Runnable onBack;

    public ColorPickerMenu(Player viewer, GameSession session, TeamId teamId, Runnable onBack) {
        super(viewer, 3, Component.text("Teamfarbe wählen"));
        this.session = session;
        this.teamId = teamId;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        Set<TeamColor> available = session.teamRegistry().availableColors();
        TeamColor ownColor = session.teamRegistry().team(teamId).map(Team::color).orElse(null);

        int slot = 0;
        for (TeamColor color : TeamColor.values()) {
            boolean own = color == ownColor;
            boolean free = available.contains(color) || own;
            String label = color.name().toLowerCase(Locale.ROOT).replace('_', ' ');

            if (free) {
                Material dye = Material.valueOf(color.name() + "_DYE");
                set(slot++, Items.of(dye,
                                Component.text(label, color.textColor()),
                                own ? Component.text("Aktuelle Farbe", NamedTextColor.GREEN)
                                        : Component.text("Klick: Farbe übernehmen", NamedTextColor.YELLOW)),
                        click -> {
                            TeamOperationResult result = session.teamSetColor(teamId, color);
                            viewer.sendMessage(result.isSuccess()
                                    ? Messages.ok("Teamfarbe geändert.")
                                    : Messages.error(Messages.describe(result)));
                            refresh();
                        });
            } else {
                String holder = session.teamRegistry().all().stream()
                        .filter(team -> team.color() == color)
                        .map(Team::name)
                        .findFirst().orElse("?");
                set(slot++, Items.of(Material.GRAY_DYE,
                        Component.text(label, NamedTextColor.DARK_GRAY),
                        Component.text("Vergeben an \"" + holder + "\"", NamedTextColor.RED)));
            }
        }

        set(22, Items.back(), click -> onBack.run());
    }
}
