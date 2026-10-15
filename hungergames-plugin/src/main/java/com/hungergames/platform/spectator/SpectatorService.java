package com.hungergames.platform.spectator;

import com.hungergames.game.GameSession;
import com.hungergames.platform.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

/**
 * Verwaltet eliminierte Teilnehmer als echte Spectator.
 *
 * <p>Eliminierte erhalten {@link GameMode#SPECTATOR} (kein Interagieren,
 * keine Kollision, für Lebende unsichtbar — Vanilla-Garantien) plus einen
 * Teleport-Kompass. Ziele sind ausschließlich lebende, online befindliche
 * Teilnehmer ({@link SpectatorTeleportMenu}).</p>
 */
public final class SpectatorService implements Listener {


    private final Plugin plugin;
    private final GameSession session;

    public SpectatorService(Plugin plugin, GameSession session) {
        this.plugin = plugin;
        this.session = session;
    }

    /** Versetzt einen eliminierten Teilnehmer in den Spectator-Zustand. */
    public void makeSpectator(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.sendMessage(Messages.info("Dein Kanonenschuss ist verhallt — für dich sind die "
                + "Spiele vorbei. Mit /hg spectate begleitest du die verbliebenen Tribute."));
        teleportToFirstTarget(player);

        // Im echten Spectator-Modus sind Hotbar-Items nicht bedienbar —
        // deshalb öffnet sich das Teleport-Menü automatisch kurz nach dem Tod.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
                openTeleportMenu(player);
            }
        }, 40L);
    }

    /**
     * Teleportiert einen Spectator zu einem Ziel — nur wenn das Ziel ein
     * lebender, online befindlicher Teilnehmer ist.
     */
    public boolean teleportTo(Player spectator, UUID target) {
        if (!session.participants().isAlive(target)) {
            spectator.sendMessage(Messages.error("Dieser Spieler ist nicht mehr im Spiel."));
            return false;
        }
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null) {
            spectator.sendMessage(Messages.error("Dieser Spieler ist gerade offline."));
            return false;
        }
        spectator.teleport(targetPlayer.getLocation());
        spectator.sendMessage(Messages.info("Du beobachtest jetzt "
                + targetPlayer.getName() + "."));
        return true;
    }

    public void openTeleportMenu(Player spectator) {
        new SpectatorTeleportMenu(spectator, session, this).open();
    }

    private void teleportToFirstTarget(Player spectator) {
        // Bevorzugt lebender Teamkollege, sonst irgendein Lebender online
        UUID preferred = session.teamRegistry().teamOf(spectator.getUniqueId())
                .flatMap(team -> team.members().stream()
                        .filter(session.participants()::isAlive)
                        .filter(uuid -> Bukkit.getPlayer(uuid) != null)
                        .findFirst())
                .orElse(null);
        if (preferred != null) {
            teleportTo(spectator, preferred);
            return;
        }
        session.participants().alive().stream()
                .filter(uuid -> Bukkit.getPlayer(uuid) != null)
                .findFirst()
                .ifPresent(target -> teleportTo(spectator, target));
    }

    /**
     * Blockiert das Vanilla-Spectating ungültiger Ziele: Ein Spectator darf
     * nur lebende Teilnehmer der Runde verfolgen.
     */
    @EventHandler
    public void onSpectateTarget(com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent event) {
        if (!(event.getNewSpectatorTarget() instanceof Player target)) {
            return;
        }
        if (!session.participants().isAlive(target.getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.error(
                    "Dieser Spieler ist kein lebender Tribut."));
        }
    }
}
