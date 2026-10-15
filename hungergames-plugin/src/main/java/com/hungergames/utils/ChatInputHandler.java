package com.hungergames.utils;

import com.hungergames.HungerGamesPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles chat input for interactive commands.
 * Only accepts input from the specific player who initiated the conversation.
 */
public class ChatInputHandler implements Listener {

    private static ChatInputHandler instance;
    private final Map<UUID, Consumer<String>> pendingInputs = new ConcurrentHashMap<>();

    private ChatInputHandler() {
    }

    public static ChatInputHandler getInstance() {
        if (instance == null) {
            instance = new ChatInputHandler();
        }
        return instance;
    }

    /**
     * Registers this handler as an event listener.
     * Call this from the HungerGamesPlugin plugin class.
     */
    public void register() {
        HungerGamesPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Registers a callback to await input from a specific player.
     * 
     * @param playerUuid The UUID of the player to await input from
     * @param callback   The callback to invoke with the player's input
     */
    public void awaitInput(UUID playerUuid, Consumer<String> callback) {
        pendingInputs.put(playerUuid, callback);
    }

    /**
     * Cancels any pending input request for a player.
     * 
     * @param playerUuid The UUID of the player
     */
    public void cancelInput(UUID playerUuid) {
        pendingInputs.remove(playerUuid);
    }

    /**
     * Checks if a player has a pending input request.
     * 
     * @param playerUuid The UUID of the player
     * @return true if the player has a pending input request
     */
    public boolean hasPendingInput(UUID playerUuid) {
        return pendingInputs.containsKey(playerUuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();

        Consumer<String> callback = pendingInputs.remove(playerUuid);
        if (callback != null) {
            // Cancel the chat message so it doesn't appear in chat
            event.setCancelled(true);

            // Extract plain text from the Adventure component
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());

            // Run callback on main thread
            HungerGamesPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                callback.accept(message);
            });
        }
    }
}
