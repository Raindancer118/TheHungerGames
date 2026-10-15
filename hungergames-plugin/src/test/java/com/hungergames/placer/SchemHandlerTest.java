package com.hungergames.placer;

import com.hungergames.HungerGamesPlugin;
import com.hungergames.utils.Sender;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class SchemHandlerTest {

    @TempDir
    Path tempDir;

    private HungerGamesPlugin mockPlugin;
    private SchemHandler schemHandler;
    private MockedStatic<JavaPlugin> javaPluginMockedStatic;
    private MockedStatic<Bukkit> bukkitMockedStatic;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(HungerGamesPlugin.class);
        when(mockPlugin.getDataFolder()).thenReturn(tempDir.toFile());

        javaPluginMockedStatic = mockStatic(JavaPlugin.class);
        javaPluginMockedStatic.when(() -> JavaPlugin.getPlugin(HungerGamesPlugin.class)).thenReturn(mockPlugin);

        bukkitMockedStatic = mockStatic(Bukkit.class);
        bukkitMockedStatic.when(Bukkit::getLogger).thenReturn(Logger.getLogger("Minecraft"));

        schemHandler = new SchemHandler();
    }

    @AfterEach
    void tearDown() {
        javaPluginMockedStatic.close();
        bukkitMockedStatic.close();
    }

    @Test
    void testGetSchematicHeightWithUnsafePaths() {
        assertEquals(-1, schemHandler.getSchematicHeight("../secret.txt"));
        assertEquals(-1, schemHandler.getSchematicHeight("sub/test.schem"));
        assertEquals(-1, schemHandler.getSchematicHeight("/etc/passwd"));
        assertEquals(-1, schemHandler.getSchematicHeight(null));
        assertEquals(-1, schemHandler.getSchematicHeight(""));
    }
}
