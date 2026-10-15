package com.hungergames.commands;

import com.hungergames.api.model.GamePhase;
import com.hungergames.GameState;
import com.hungergames.player.WhitelistManager;
import com.hungergames.utils.Sender;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Command handler for /test-end - Returns player to their proper position based
 * on game phase.
 */
public class TestEndCommand implements CommandExecutor {

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

        // Check if in test mode
        if (!TestStartCommand.isInTestMode(player.getUniqueId())) {
            player.sendMessage("§cYou are not in test mode! Use §e/test-start§c first.");
            return true;
        }

        // Exit test mode
        TestStartCommand.exitTestMode(player.getUniqueId());

        // Get current game phase
        GameState gameState = GameState.getInstance();
        GamePhase currentPhase = gameState.getCurrentPhase();

        Location targetLocation = null;
        String locationName = "";

        switch (currentPhase) {
            case NOT_INITIALIZED:
                player.sendMessage("§cThe arena has not been initialized yet!");
                player.setGameMode(GameMode.CREATIVE);
                return true;

            case PREFLIGHT:
            case LOBBY:
                // Teleport to glass lobby
                targetLocation = gameState.getLobbyCenter();
                locationName = "glass lobby";
                player.setGameMode(GameMode.ADVENTURE);
                break;

            case STARTUP:
                // Teleport to underground room
                targetLocation = gameState.getUndergroundRoomCenter();
                locationName = "underground room";
                player.setGameMode(GameMode.ADVENTURE);
                break;

            case READY:
                // Teleport to player's platform
                targetLocation = getPlayerPlatformPosition(player, gameState);
                locationName = "platform";
                player.setGameMode(GameMode.ADVENTURE);
                break;

            case RUNNING:
                // Game is running, set to survival and don't teleport
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage("§aExited test mode. Game is running - you're on your own!");
                Sender.log("Player " + player.getName() + " exited test mode during RUNNING phase");
                return true;

            case FINISHED:
                player.sendMessage("§aExited test mode. The game has finished.");
                player.setGameMode(GameMode.SPECTATOR);
                return true;

            default:
                player.sendMessage("§cUnknown game phase!");
                return true;
        }

        if (targetLocation == null) {
            player.sendMessage("§cCould not find target location for phase " + currentPhase.name());
            return true;
        }

        player.teleport(targetLocation);
        player.sendMessage("§aExited test mode!");
        player.sendMessage("§7Teleported to: §e" + locationName);
        player.sendMessage("§7Current phase: §e" + currentPhase.name());

        Sender.log("Player " + player.getName() + " exited test mode, teleported to " + locationName);

        return true;
    }

    /**
     * Gets the platform position for a specific player based on their whitelist
     * order.
     */
    private Location getPlayerPlatformPosition(Player player, GameState gameState) {
        WhitelistManager whitelist = WhitelistManager.getInstance();
        List<String> sortedPlayers = whitelist.getPlayersSortedByTeam();
        List<Location> platforms = gameState.getPlatformPositions();

        // Find player's index in sorted list
        int playerIndex = -1;
        for (int i = 0; i < sortedPlayers.size(); i++) {
            if (sortedPlayers.get(i).equalsIgnoreCase(player.getName())) {
                playerIndex = i;
                break;
            }
        }

        if (playerIndex >= 0 && playerIndex < platforms.size()) {
            Location platform = platforms.get(playerIndex).clone();
            platform.add(0.5, 1, 0.5); // Stand on top of platform
            return platform;
        }

        // Fallback to first platform
        if (!platforms.isEmpty()) {
            Location platform = platforms.get(0).clone();
            platform.add(0.5, 1, 0.5);
            return platform;
        }

        return null;
    }
}
