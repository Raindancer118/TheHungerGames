package com.hungergames.platform.listener;

import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.protection.ProtectionRules;
import com.hungergames.game.protection.ProtectionRules.ActionType;
import com.hungergames.game.protection.ProtectionRules.Query;
import com.hungergames.game.protection.ProtectionRules.Region;
import com.hungergames.platform.Messages;
import com.hungergames.platform.config.HgSettings;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Phasen- und regionsabhängiger Gebietsschutz. Ersetzt den alten
 * ProtectionListener: Die Entscheidung trifft die Bukkit-freie
 * {@link ProtectionRules}-Matrix; hier wird nur übersetzt.
 */
public final class ProtectionService implements Listener {

    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final Supplier<Location> arenaCenter;

    public ProtectionService(GameSession session, ConfigurationService config, HgSettings settings,
                             Supplier<Location> arenaCenter) {
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.arenaCenter = arenaCenter;
    }

    private ProtectionRules rules() {
        return new ProtectionRules(new ProtectionRules.Config(
                config.get(settings.protectCornucopiaPreGame),
                config.get(settings.protectCornucopiaRunning),
                config.get(settings.protectCornucopiaFinished),
                new HashSet<>(config.get(settings.allowedInteractions))));
    }

    private Region regionOf(Location location) {
        Location center = arenaCenter.get();
        if (center == null || center.getWorld() == null
                || !center.getWorld().equals(location.getWorld())) {
            return Region.ARENA;
        }
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        int radius = config.get(settings.cornucopiaRadius);
        return (dx * dx + dz * dz) <= (double) radius * radius ? Region.CORNUCOPIA : Region.ARENA;
    }

    private boolean deny(Player player, Location location, ActionType action, String material) {
        return rules().shouldDeny(new Query(
                regionOf(location), action, session.phase(),
                player.hasPermission(config.get(settings.bypassPermission)), material));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation(), ActionType.BREAK,
                event.getBlock().getType().name())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.error("Dieser Bereich ist derzeit geschützt."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation(), ActionType.PLACE,
                event.getBlock().getType().name())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.error("Dieser Bereich ist derzeit geschützt."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Set<String> containers = new HashSet<>(config.get(settings.allowedInteractions));
        ActionType action = containers.contains(block.getType().name())
                ? ActionType.CONTAINER : ActionType.INTERACT;

        if (deny(event.getPlayer(), block.getLocation(), action, block.getType().name())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.error("Das ist hier gerade nicht möglich."));
        }
    }
}
