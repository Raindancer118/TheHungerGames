package com.hungergames.platform.team;

import com.hungergames.api.event.TeamColorChangedEvent;
import com.hungergames.api.event.TeamMembershipChangedEvent;
import com.hungergames.api.model.Team;
import com.hungergames.game.GameSession;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.Scoreboard;

import java.util.UUID;

/**
 * Sichtbare Team-Repräsentation: farbige Nametags über Scoreboard-Teams und
 * gefärbte Lederhelme. Hält sich über die API-Events selbst aktuell.
 */
public final class TeamPresentationService implements Listener {

    private final GameSession session;
    private Scoreboard scoreboard;

    public TeamPresentationService(GameSession session) {
        this.session = session;
    }

    /** Baut die Scoreboard-Teams neu auf und verteilt sie an alle Online-Spieler. */
    public void setupScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        for (Team team : session.teamRegistry().all()) {
            registerTeam(team);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(scoreboard);
        }
    }

    /**
     * Das gemeinsame Scoreboard der Runde. Nametag-Farben und das
     * Kill-Leaderboard leben auf DEMSELBEN Scoreboard — ein Spieler kann nur
     * eines gleichzeitig haben, zwei getrennte würden sich verdrängen.
     */
    public Scoreboard scoreboard() {
        if (scoreboard == null) {
            setupScoreboard();
        }
        return scoreboard;
    }

    public void applyScoreboard(Player player) {
        if (scoreboard != null) {
            player.setScoreboard(scoreboard);
        }
    }

    /** Verteilt an alle lebenden Teilnehmer den Helm in Teamfarbe (Spielstart). */
    public void giveTeamHelmets() {
        for (UUID alive : session.participants().alive()) {
            Player player = Bukkit.getPlayer(alive);
            if (player == null) {
                continue;
            }
            session.teamRegistry().teamOf(alive).ifPresent(team -> {
                ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
                LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
                meta.setColor(team.color().armorColor());
                meta.displayName(Component.text(team.name() + " Helm", team.color().textColor()));
                helmet.setItemMeta(meta);
                player.getInventory().setHelmet(helmet);
            });
        }
    }

    public void reset() {
        if (scoreboard != null) {
            scoreboard.getTeams().forEach(org.bukkit.scoreboard.Team::unregister);
            scoreboard = null;
        }
    }

    // Live-Aktualisierung über die eigenen API-Events

    @EventHandler
    public void onMembershipChange(TeamMembershipChangedEvent event) {
        if (scoreboard == null) {
            return;
        }
        String entry = entryFor(event.getPlayer());
        event.getOldTeam().ifPresent(old -> {
            org.bukkit.scoreboard.Team team = scoreboard.getTeam(scoreboardName(old.value()));
            if (team != null) {
                team.removeEntry(entry);
            }
        });
        event.getNewTeam().flatMap(id -> session.teamRegistry().team(id)).ifPresent(team -> {
            org.bukkit.scoreboard.Team sbTeam = scoreboard.getTeam(scoreboardName(team.id().value()));
            if (sbTeam == null) {
                sbTeam = registerTeam(team);
            }
            sbTeam.addEntry(entry);
        });
    }

    @EventHandler
    public void onColorChange(TeamColorChangedEvent event) {
        if (scoreboard == null) {
            return;
        }
        org.bukkit.scoreboard.Team sbTeam = scoreboard.getTeam(scoreboardName(event.getTeam().id().value()));
        if (sbTeam != null) {
            sbTeam.color(event.getNewColor().namedTextColor());
        }
    }

    private org.bukkit.scoreboard.Team registerTeam(Team team) {
        org.bukkit.scoreboard.Team sbTeam = scoreboard.registerNewTeam(scoreboardName(team.id().value()));
        sbTeam.color(team.color().namedTextColor());
        sbTeam.setAllowFriendlyFire(true);
        sbTeam.setCanSeeFriendlyInvisibles(true);
        for (UUID member : team.members()) {
            sbTeam.addEntry(entryFor(member));
        }
        return sbTeam;
    }

    /** Scoreboard-Einträge brauchen den Spielernamen; UUID bleibt die Quelle. */
    private String entryFor(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        return session.participants().nameOf(uuid).orElse(uuid.toString().substring(0, 16));
    }

    private static String scoreboardName(String teamId) {
        String name = "hg_" + teamId;
        return name.length() > 16 ? name.substring(0, 16) : name;
    }
}
