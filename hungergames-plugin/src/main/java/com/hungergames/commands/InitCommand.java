package com.hungergames.commands;

import com.hungergames.GameState;
import com.hungergames.stagerunner.PreflightRunner;
import com.hungergames.utils.ChatInputHandler;
import com.hungergames.utils.Sender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command handler for /init - Initializes the arena at the admin's location.
 * Prompts for player count via chat conversation.
 */
public class InitCommand implements CommandExecutor {

    private final PreflightRunner preflightRunner = new PreflightRunner();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            Sender.log("A non-player entity tried to execute /init command.");
            return true;
        }

        // Check permission
        if (!player.hasPermission("hungergames.gamemaster")) {
            player.sendMessage("§cYou do not have the required permission to use this command.");
            return true;
        }

        // Nach einer beendeten Runde setzt /init die Session zurück und
        // erlaubt so direkt die nächsten Spiele (README-Ablauf).
        if (GameState.getInstance().getCurrentPhase() == com.hungergames.api.model.GamePhase.FINISHED) {
            GameState.getInstance().reset();
            player.sendMessage("§7Vorherige Runde abgeschlossen — bereite die nächsten Spiele vor ...");
        } else if (GameState.getInstance().isInitialized()) {
            player.sendMessage("§cEs läuft bereits eine Runde! Neue Arena erst nach Spielende"
                    + " (oder /fliptable für den harten Reset).");
            return true;
        }

        // Store player location for later use
        GameState.getInstance().setArenaCenter(player.getLocation());

        // Vorbereitung beginnt sofort friedlich: keine Mobs/Schaden, bis die
        // Tribute bei /start released werden (dort wird die Difficulty erhöht).
        player.getWorld().setDifficulty(
                com.hungergames.utils.ConfigManager.getInstance().getPreflightDifficulty());

        // Ask for player count via chat
        player.sendMessage("§a§lHunger Games Arena Setup");
        player.sendMessage("§7How many players will participate in the tournament?");
        player.sendMessage("§7(Enter a number in chat)");

        // Register chat listener for this player
        ChatInputHandler.getInstance().awaitInput(player.getUniqueId(), input -> {
            try {
                int playerCount = Integer.parseInt(input.trim());

                if (playerCount < 2) {
                    player.sendMessage("§cPlayer count must be at least 2!");
                    GameState.getInstance().reset();
                    return;
                }

                if (playerCount > 100) {
                    player.sendMessage("§cPlayer count cannot exceed 100!");
                    GameState.getInstance().reset();
                    return;
                }

                player.sendMessage("§aInitializing arena for §e" + playerCount + " §aplayers...");
                GameState.getInstance().setPlayerCount(playerCount);

                // Run preflight setup
                preflightRunner.setup(player.getUniqueId(), playerCount);

            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid number! Please run /init again and enter a valid number.");
                GameState.getInstance().reset();
            }
        });

        return true;
    }
}
