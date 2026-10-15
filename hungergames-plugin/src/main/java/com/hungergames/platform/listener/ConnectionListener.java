package com.hungergames.platform.listener;

import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import com.hungergames.platform.Messages;
import com.hungergames.platform.resourcepack.ResourcepackService;
import com.hungergames.platform.spectator.SpectatorService;
import com.hungergames.platform.team.TeamPresentationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Join-Handling auf UUID-Basis.
 *
 * <p>Kernsemantik: Ein Disconnect ändert nichts am Lebend-Status. Lebende
 * Rejoiner spielen einfach weiter (letzte Position bleibt durch Vanilla
 * erhalten); Eliminierte kommen in den Spectator-Modus; Nicht-Teilnehmer
 * werden während des Spiels Zuschauer.</p>
 */
public final class ConnectionListener implements Listener {

    private final GameSession session;
    private final SpectatorService spectatorService;
    private final TeamPresentationService teamPresentation;
    private final com.hungergames.platform.game.GameTimerService gameTimer;
    private final ResourcepackService resourcepack;

    public ConnectionListener(GameSession session, SpectatorService spectatorService,
                              TeamPresentationService teamPresentation,
                              com.hungergames.platform.game.GameTimerService gameTimer,
                              ResourcepackService resourcepack) {
        this.session = session;
        this.spectatorService = spectatorService;
        this.teamPresentation = teamPresentation;
        this.gameTimer = gameTimer;
        this.resourcepack = resourcepack;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        resourcepack.send(player);

        // Anzeige-Namen aktuell halten (UUID bleibt die Identität)
        if (session.isWhitelisted(player.getUniqueId())) {
            session.updateName(player.getUniqueId(), player.getName());
            teamPresentation.applyScoreboard(player);
        }

        GamePhase phase = session.phase();
        if (phase != GamePhase.RUNNING && phase != GamePhase.FINISHED) {
            return;
        }

        // Timer-Bossbar und (gemeinsames) Scoreboard auch für Late-Joiner
        gameTimer.addViewer(player);

        if (!session.isWhitelisted(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(Messages.info("Die Hungerspiele laufen bereits — du verfolgst sie "
                    + "als Zuschauer aus dem Kapitol."));
            return;
        }

        if (session.participants().isAlive(player.getUniqueId())) {
            player.sendMessage(Messages.ok("Willkommen zurück in der Arena, Tribut — "
                    + "du bist weiterhin im Spiel!"));
        } else {
            spectatorService.makeSpectator(player);
            player.sendMessage(Component.text("Für dich sind die Spiele vorbei.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gameTimer.removeViewer(event.getPlayer());
    }
}
