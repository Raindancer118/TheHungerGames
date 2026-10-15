package com.hungergames.commands;

import com.hungergames.api.model.GamePhase;
import com.hungergames.GameState;
import com.hungergames.stagerunner.StartRunner;
import com.hungergames.utils.ChatInputHandler;
import com.hungergames.utils.ConfigManager;
import com.hungergames.utils.Sender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command handler for /start - Begins the game countdown.
 * Shows game settings and asks for admin confirmation before starting.
 */
public class StartCommand implements CommandExecutor {

    private final StartRunner startRunner = new StartRunner();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            Sender.log("A non-player entity tried to execute /start command.");
            return true;
        }

        // Check permission
        if (!player.hasPermission("hungergames.gamemaster")) {
            player.sendMessage("§cYou do not have the required permission to use this command.");
            return true;
        }

        // Check game state
        GameState gameState = GameState.getInstance();
        GamePhase currentPhase = gameState.getCurrentPhase();

        if (currentPhase != GamePhase.READY) {
            player.sendMessage("§cThe game can only be started during the READY phase!");
            player.sendMessage("§7Current phase: §e" + currentPhase.name());
            return true;
        }

        if (StartRunner.isCountdownActive()) {
            player.sendMessage("§cDer Countdown läuft bereits — die Spiele starten gleich von selbst.");
            return true;
        }

        // Show game settings summary
        ConfigManager config = ConfigManager.getInstance();

        player.sendMessage("");
        player.sendMessage("§6§l══════════════════════════════════");
        player.sendMessage("§6§l       GAME SETTINGS SUMMARY");
        player.sendMessage("§6§l══════════════════════════════════");
        player.sendMessage("");
        player.sendMessage("§7Countdown:        §e" + config.getCountdown() + " seconds");
        player.sendMessage("§7Game Duration:    §e" + config.getGameDuration() + " minutes");
        player.sendMessage("§7Grace Period:     §e" + config.getGracePeriod() + " seconds");
        player.sendMessage("§7Prep Time:        §e" + config.getPrepTime() + "% of game duration");
        player.sendMessage("§7Worldborder:      §e" + config.getWorldborderSize() + " blocks");
        player.sendMessage("§7Players:          §e" + gameState.getPlayerCount());
        player.sendMessage("");
        player.sendMessage("§6§l══════════════════════════════════");
        player.sendMessage("");
        player.sendMessage("§aType §e'yes'§a to start the game, or §e'no'§a to cancel.");
        player.sendMessage("");

        // Await confirmation
        ChatInputHandler.getInstance().awaitInput(player.getUniqueId(), input -> {
            if (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("y")) {
                // Erneut prüfen: zwischen Abfrage und Bestätigung kann ein anderer
                // Spielmacher den Countdown bereits gestartet haben.
                if (startRunner.execute(player.getUniqueId())) {
                    player.sendMessage("§a§lStarting the Hunger Games...");
                }
            } else {
                player.sendMessage("§cGame start cancelled.");
            }
        });

        return true;
    }
}
