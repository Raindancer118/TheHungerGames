package com.hungergames.commands;

import com.hungergames.loot.LootConfig;
import com.hungergames.platform.Messages;
import com.hungergames.player.WhitelistManager;
import com.hungergames.utils.Sender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * /config-reload — lädt config.yml (nach manuellen Datei-Edits) und loot.yml
 * neu und überträgt die Turnier-Whitelist auf die Server-Whitelist.
 * Alternativ ist die Konfiguration live über /hg config verwaltbar; eine
 * Referenz aller Keys liegt in config-reference.yml.
 */
public class ConfigReloadCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (!sender.hasPermission("hungergames.admin") && !sender.isOp()) {
            sender.sendMessage(Messages.error("Dafür fehlt dir die Berechtigung."));
            return true;
        }

        com.hungergames.utils.ConfigManager.getInstance().reloadFromFile();
        LootConfig.getInstance().reload();
        WhitelistManager.getInstance().applyToServerWhitelist();
        // Craft-Rezepte (z. B. Exmatrikulator) nach Config-Änderung neu aufbauen.
        com.hungergames.items.CustomItems.registerRecipes(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(com.hungergames.HungerGamesPlugin.class));

        sender.sendMessage(Messages.ok("config.yml und loot.yml neu geladen, Rezepte & "
                + "Server-Whitelist aktualisiert."));
        Sender.log("Reload durch " + sender.getName());
        return true;
    }
}
