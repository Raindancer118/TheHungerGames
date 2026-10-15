package com.hungergames.commands;

import com.hungergames.loot.LootConfig;
import com.hungergames.platform.Messages;
import com.hungergames.utils.Sender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /hg-reload — lädt loot.yml neu. Die Hauptkonfiguration wird über den
 * typisierten ConfigurationService (GUI: /hg config) verwaltet und braucht
 * keinen Datei-Reload mehr.
 */
public class ReloadCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (sender instanceof Player player && !player.hasPermission("hungergames.gamemaster")) {
            player.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return true;
        }

        LootConfig.getInstance().reload();
        sender.sendMessage(Messages.ok("loot.yml neu geladen."));
        sender.sendMessage(Messages.info("Die Hauptkonfiguration wird live über /hg config verwaltet."));
        Sender.log("Loot-Konfiguration neu geladen durch " + sender.getName());
        return true;
    }
}
