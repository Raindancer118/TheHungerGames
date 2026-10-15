package com.hungergames.commands;

import com.hungergames.player.WhitelistManager;
import com.hungergames.utils.Sender;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to allow a player onto the server whitelist.
 * Used by the game master to let staff or spectators join the server.
 * 
 * Usage: /allow <playername>
 * - Adds the player to the server whitelist
 * - Gives the player operator permissions
 * 
 * Requires operator permissions to execute.
 */
public class AllowCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permission
        if (!sender.isOp()) {
            sender.sendMessage("§cDu hast keine Berechtigung für diesen Befehl.");
            return true;
        }

        // Check arguments
        if (args.length < 1) {
            sender.sendMessage("§cVerwendung: /allow <spielername>");
            sender.sendMessage("§7Fügt einen Spieler zur Server-Whitelist hinzu.");
            return true;
        }

        String playerName = args[0];

        // Validate player name (basic sanitization)
        if (!isValidPlayerName(playerName)) {
            sender.sendMessage("§cUngültiger Spielername: " + playerName);
            return true;
        }

        // Add player to whitelist (kein OP mehr — Sicherheitsentscheidung v2)
        WhitelistManager.getInstance().allowPlayer(playerName);

        sender.sendMessage("§a✓ Spieler §e" + playerName + " §awurde zur Server-Whitelist hinzugefügt.");
        Sender.log("Allow command executed by " + sender.getName() + " for player: " + playerName);

        // If player is online, notify them
        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null && target.isOnline()) {
            target.sendMessage("§aDu wurdest zur Server-Whitelist hinzugefügt.");
        }

        return true;
    }

    /**
     * Validates a player name.
     * Minecraft usernames are 3-16 characters, alphanumeric plus underscore.
     * 
     * @param name The name to validate
     * @return true if valid
     */
    private boolean isValidPlayerName(String name) {
        if (name == null || name.length() < 3 || name.length() > 16) {
            return false;
        }
        // Only allow alphanumeric and underscore
        return name.matches("^[a-zA-Z0-9_]+$");
    }
}
