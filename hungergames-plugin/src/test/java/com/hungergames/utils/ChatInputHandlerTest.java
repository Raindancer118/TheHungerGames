package com.hungergames.utils;

import com.hungergames.HungerGamesPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatInputHandler.
 */
class ChatInputHandlerTest {

    private ChatInputHandler handler;
    private MockedStatic<JavaPlugin> javaPluginMockedStatic;
    private HungerGamesPlugin plugin;
    private Server server;
    private BukkitScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        // Reset singleton instance using reflection for test isolation
        Field instanceField = ChatInputHandler.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        handler = ChatInputHandler.getInstance();

        plugin = mock(HungerGamesPlugin.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        javaPluginMockedStatic = mockStatic(JavaPlugin.class);
        javaPluginMockedStatic.when(() -> JavaPlugin.getPlugin(HungerGamesPlugin.class)).thenReturn(plugin);
    }

    @AfterEach
    void tearDown() {
        javaPluginMockedStatic.close();
    }

    @Test
    @DisplayName("Should register callback and check for pending input")
    void testAwaitInputAndHasPendingInput() {
        UUID playerUuid = UUID.randomUUID();
        Consumer<String> callback = s -> {};

        assertFalse(handler.hasPendingInput(playerUuid), "Should not have pending input initially");
        handler.awaitInput(playerUuid, callback);
        assertTrue(handler.hasPendingInput(playerUuid), "Should have pending input after registration");
    }

    @Test
    @DisplayName("Should cancel pending input")
    void testCancelInput() {
        UUID playerUuid = UUID.randomUUID();
        Consumer<String> callback = s -> {};

        handler.awaitInput(playerUuid, callback);
        assertTrue(handler.hasPendingInput(playerUuid));

        handler.cancelInput(playerUuid);
        assertFalse(handler.hasPendingInput(playerUuid), "Should not have pending input after cancellation");
    }

    @Test
    @DisplayName("Should handle chat event when input is pending")
    void testOnChatWithPendingInput() {
        UUID playerUuid = UUID.randomUUID();
        String testMessage = "Test Input";

        @SuppressWarnings("unchecked")
        Consumer<String> callback = mock(Consumer.class);
        handler.awaitInput(playerUuid, callback);

        AsyncChatEvent event = mock(AsyncChatEvent.class);
        Player player = mock(Player.class);
        // Adventure-Components sind seit Paper 26.2 sealed → echte Instanz statt Mock.
        Component messageComponent = Component.text(testMessage);

        when(event.getPlayer()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(event.message()).thenReturn(messageComponent);

        handler.onChat(event);

        // Verify event was cancelled
        verify(event).setCancelled(true);

        // Verify task was scheduled
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), runnableCaptor.capture());

        // Run the captured runnable and verify callback was called
        runnableCaptor.getValue().run();
        verify(callback).accept(testMessage);

        // Verify input is removed from pending map
        assertFalse(handler.hasPendingInput(playerUuid), "Input should no longer be pending after event");
    }

    @Test
    @DisplayName("Should not cancel chat event when no input is pending")
    void testOnChatWithoutPendingInput() {
        UUID playerUuid = UUID.randomUUID();

        AsyncChatEvent event = mock(AsyncChatEvent.class);
        Player player = mock(Player.class);

        when(event.getPlayer()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(playerUuid);

        handler.onChat(event);

        // Verify event was NOT cancelled and no task was scheduled
        verify(event, never()).setCancelled(anyBoolean());
        verify(scheduler, never()).runTask(any(), any(Runnable.class));
    }

    @Test
    @DisplayName("Should register as an event listener")
    void testRegister() {
        PluginManager pm = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(pm);

        handler.register();

        verify(pm).registerEvents(handler, plugin);
    }
}
