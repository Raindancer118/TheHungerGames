package com.hungergames.commands;

import com.hungergames.GameState;
import com.hungergames.utils.Sender;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Command handler for /test-start - Allows admin to enter spectator mode for
 * testing.
 * Players in test mode can still change their gamemode.
 */
public class TestStartCommand implements CommandExecutor {

    // Track players currently in test mode
    private static final Set<UUID> playersInTestMode = new HashSet<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return true;
        }

        // Check permission
        if (!player.hasPermission("hungergames.gamemaster")) {
            player.sendMessage("§cYou do not have the required permission to use this command.");
            return true;
        }

        // Check if already in test mode
        if (isInTestMode(player.getUniqueId())) {
            player.sendMessage("§cYou are already in test mode! Use §e/test-end§c to exit.");
            return true;
        }

        // Enter test mode
        playersInTestMode.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);

        player.sendMessage("§a§lEntered test mode!");
        player.sendMessage("§7You are now in spectator mode but can change your gamemode freely.");
        player.sendMessage("§7Use §e/test-end§7 to return to your proper position based on the current phase.");

        Sender.log("Player " + player.getName() + " entered test mode");

        return true;
    }

    /**
     * Checks if a player is currently in test mode.
     */
    public static boolean isInTestMode(UUID playerUuid) {
        return playersInTestMode.contains(playerUuid);
    }

    /**
     * Removes a player from test mode.
     */
    public static void exitTestMode(UUID playerUuid) {
        playersInTestMode.remove(playerUuid);
    }

    /**
     * Gets all players currently in test mode.
     */
    public static Set<UUID> getPlayersInTestMode() {
        return new HashSet<>(playersInTestMode);
    }
}
