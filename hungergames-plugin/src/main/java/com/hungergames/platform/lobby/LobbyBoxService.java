package com.hungergames.platform.lobby;

import com.hungergames.GameState;
import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import com.hungergames.platform.Messages;
import com.hungergames.utils.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Regeln rund um die Glas-Lobby („Glasbox") über der Arena.
 *
 * <p>Der Welt-Spawn liegt AUF dem Dach der Box (siehe PreflightRunner);
 * whitelistete Tribute werden beim Join in die aktive Box teleportiert.
 * Innerhalb der Box ist außerdem kein PVP möglich.</p>
 *
 * <p>Die Box gilt als aktiv von PREFLIGHT bis STARTUP — beim Übergang nach
 * READY entfernt der StartupRunner sie.</p>
 */
public final class LobbyBoxService implements Listener {

    private final GameSession session;

    public LobbyBoxService(GameSession session) {
        this.session = session;
    }

    /** @return {@code true}, solange die Glasbox existiert (READY entfernt sie). */
    public boolean isActive() {
        GamePhase phase = session.phase();
        return GameState.getInstance().getLobbyCenter() != null
                && (phase == GamePhase.PREFLIGHT || phase == GamePhase.LOBBY
                        || phase == GamePhase.STARTUP);
    }

    /** @return {@code true}, wenn die Position innerhalb der Box liegt (inkl. Wände/Dach). */
    public boolean isInside(Location location) {
        Location arenaCenter = GameState.getInstance().getArenaCenter();
        Location lobbyCenter = GameState.getInstance().getLobbyCenter();
        if (arenaCenter == null || lobbyCenter == null || location.getWorld() == null
                || !location.getWorld().equals(lobbyCenter.getWorld())) {
            return false;
        }
        ConfigManager config = ConfigManager.getInstance();
        int width = config.getLobbyWidth();
        int depth = config.getLobbyDepth();
        int height = config.getLobbyHeight();
        int baseX = arenaCenter.getBlockX() - width / 2;
        int baseY = arenaCenter.getBlockY() + config.getLobbyHeightOffset();
        int baseZ = arenaCenter.getBlockZ() - depth / 2;
        return location.getBlockX() >= baseX && location.getBlockX() < baseX + width
                && location.getBlockZ() >= baseZ && location.getBlockZ() < baseZ + depth
                && location.getBlockY() >= baseY && location.getBlockY() <= baseY + height + 1;
    }

    /**
     * Tribute landen beim Join in der aktiven Box — der Welt-Spawn liegt auf
     * dem Dach. Während der Startsequenz (STARTUP) wird niemand mehr
     * hineingeholt, die Tribute sind da bereits unter der Arena.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isActive() || session.phase() == GamePhase.STARTUP
                || !session.isWhitelisted(player.getUniqueId())) {
            return;
        }
        if (!isInside(player.getLocation())) {
            player.teleport(GameState.getInstance().getLobbyCenter());
            player.sendMessage(Messages.info(
                    "Willkommen in der Lobby, Tribut — die Spiele beginnen bald."));
        }
    }

    /** Innerhalb der Glasbox ist kein PVP möglich. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !isActive()) {
            return;
        }
        Player attacker = attackerOf(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (isInside(victim.getLocation()) || isInside(attacker.getLocation())) {
            event.setCancelled(true);
            attacker.sendMessage(Messages.error("In der Lobby ist kein Kampf möglich."));
        }
    }

    private static Player attackerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
