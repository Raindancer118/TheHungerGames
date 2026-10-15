package com.hungergames.utils;

import org.bukkit.Bukkit;

public class Sender {

    /*
    Logger Methods
     */
    public static void log(String message) {
        Bukkit.getLogger().info("[HungerGames] " + message);
    }

    public static void error(String message) {
        Bukkit.getLogger().severe("[HungerGames] " + message);
    }

    public static void error(String message, Throwable throwable) {
        Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "[HungerGames] " + message, throwable);
    }

    public static void warning(String message) {
        Bukkit.getLogger().warning("[HungerGames] " + message);
    }

    public static void debug(String message) {
        Bukkit.getLogger().info("[HungerGames] [DEBUG] " + message);
    }

    /*
    Sender Methods
     */
    public static void say(String message) {
        Bukkit.broadcastMessage(message);
    }

    public static void msg (String player, String message) {
        Bukkit.getPlayer(player).sendMessage(message);
    }
}
