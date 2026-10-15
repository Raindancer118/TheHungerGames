package com.hungergames.commands;

import com.hungergames.api.model.GamePhase;
import com.hungergames.GameState;
import com.hungergames.stagerunner.StartupRunner;
import com.hungergames.utils.Sender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command handler for /startup - Starts the engaging sequence.
 * Teleports players to underground tubes and levitates them to platforms.
 */
public class StartupCommand implements CommandExecutor {

    private final StartupRunner startupRunner = new StartupRunner();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be executed by a player.");
            Sender.log("A non-player entity tried to execute /startup command.");
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

        if (currentPhase == GamePhase.NOT_INITIALIZED) {
            player.sendMessage("§cThe arena must be initialized first! Use /init");
            return true;
        }

        if (currentPhase != GamePhase.LOBBY) {
            player.sendMessage("§cDie Startsequenz kann nur aus der LOBBY-Phase gestartet werden!");
            player.sendMessage("§7Aktuelle Phase: " + currentPhase.name());
            return true;
        }

        player.sendMessage("§a§lStarting engaging sequence...");

        // Run startup sequence
        startupRunner.execute(player.getUniqueId());

        return true;
    }
}
