package com.hungergames.commands;

import com.hungergames.loot.LootManager;
import com.hungergames.utils.Sender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TestLootCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (!player.hasPermission("hungergames.gamemaster") && !player.isOp()) {
                Sender.msg(player.getName(), "§cYou do not have permission to use this command.");
                return true;
            }
        }

        Sender.log("Manual loot generation triggered by " + sender.getName());
        if (sender instanceof Player) {
            sender.sendMessage("§eTriggering loot generation...");
        }

        // Run async to avoid lag? LootManager does block access, so must be SYNC.
        // But we can verify if we want to pass a custom radius later.
        // For now, it uses config values.

        new LootManager().fillArenaContainers();

        if (sender instanceof Player) {
            sender.sendMessage("§aLoot generation complete! Check console for detailed logs.");
        }

        return true;
    }
}
