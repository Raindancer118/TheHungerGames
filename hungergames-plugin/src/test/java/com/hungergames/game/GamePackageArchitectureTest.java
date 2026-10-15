package com.hungergames.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architektur-Regel: Die Spiellogik unter {@code com.hungergames.game} bleibt
 * Bukkit-frei, damit sie ohne Server testbar ist. Zugriff auf die Plattform
 * läuft ausschließlich über die Ports in {@code com.hungergames.game.port}.
 */
class GamePackageArchitectureTest {

    @Test
    @DisplayName("com.hungergames.game importiert kein org.bukkit")
    void gamePackageIsBukkitFree() throws IOException {
        Path gameSources = Path.of("src/main/java/com/hungergames/game");
        assertTrue(Files.isDirectory(gameSources), "game-Paket nicht gefunden: " + gameSources.toAbsolutePath());

        try (Stream<Path> files = Files.walk(gameSources)) {
            List<String> violations = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> {
                        try {
                            return Files.readAllLines(p).stream()
                                    .filter(line -> line.startsWith("import org.bukkit")
                                            || line.startsWith("import net.md_5")
                                            || line.startsWith("import io.papermc"))
                                    .map(line -> p.getFileName() + ": " + line.trim());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();

            assertTrue(violations.isEmpty(),
                    "Bukkit-Imports in der Spiellogik gefunden:\n" + String.join("\n", violations));
        }
    }
}
