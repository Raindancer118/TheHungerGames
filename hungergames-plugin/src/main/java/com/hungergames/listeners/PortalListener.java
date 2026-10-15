package com.hungergames.listeners;

import com.hungergames.GameState;
import com.hungergames.utils.ConfigManager;
import com.hungergames.utils.Sender;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

/**
 * Listener for portal events.
 * Blocks nether and end portals based on configuration.
 */
public class PortalListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        ConfigManager config = ConfigManager.getInstance();
        GameState gameState = GameState.getInstance();

        // Only block portals if game is initialized
        if (!gameState.isInitialized()) {
            return;
        }

        PortalType portalType = event.getCause() == PlayerPortalEvent.TeleportCause.NETHER_PORTAL
                ? PortalType.NETHER
                : PortalType.ENDER;

        // Handle Nether Portal blocking
        if (portalType == PortalType.NETHER && config.shouldBlockNetherPortals()) {
            Location arenaCenter = gameState.getArenaCenter();
            Location playerLocation = event.getPlayer().getLocation();

            if (arenaCenter != null && arenaCenter.getWorld() != null) {
                // Only restrict in Overworld (Arena World)
                if (playerLocation.getWorld().equals(arenaCenter.getWorld())) {
                    // Check if player is OUTSIDE the allowed radius (2D distance, ignoring Y)
                    double distance = Math.sqrt(
                            Math.pow(playerLocation.getX() - arenaCenter.getX(), 2) +
                                    Math.pow(playerLocation.getZ() - arenaCenter.getZ(), 2));

                    int allowRadius = config.getNetherAllowRadius();

                    if (distance > allowRadius) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage("§cNether portals are only allowed in the center area!");
                        Sender.debug("Blocked nether portal use for " + event.getPlayer().getName() +
                                " at distance " + (int) distance + " (allowed radius: " + allowRadius + ")");
                    }
                }
            }
        }

        // Handle End Portal blocking
        if (portalType == PortalType.ENDER && config.shouldBlockEndPortals()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cEnd portals are disabled during the tournament!");
            Sender.debug("Blocked end portal use for " + event.getPlayer().getName());
        }
    }
}
