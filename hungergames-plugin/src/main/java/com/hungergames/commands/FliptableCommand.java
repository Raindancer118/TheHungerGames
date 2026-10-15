package com.hungergames.commands;

import com.hungergames.GameState;
import com.hungergames.HungerGamesPlugin;
import com.hungergames.utils.Sender;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Command to completely reset the HungerGames environment.
 * Deletes worlds, clears plugin data, and restarts the server.
 *
 * Usage: /fliptable confirm
 * Permission: hungergames.admin (OP only by default)
 *
 * WARNING: This is a destructive operation! All world data will be lost.
 *
 * <p><b>Warum erst beim Shutdown gelöscht wird:</b> Löscht man die Weltordner,
 * während der Server läuft, schreibt Paper sie beim Herunterfahren teilweise
 * wieder (level.dat, Regionen, session.lock). Zurück bleibt ein halber
 * Weltordner, den Paper beim nächsten Start migriert und dann mit
 * {@code IllegalStateException: Overworld settings missing} abbricht
 * (fehlende {@code world_gen_settings.dat}). Deshalb läuft die eigentliche
 * Löschung in einem JVM-Shutdown-Hook — also nachdem der Server die Welten
 * gespeichert und geschlossen hat.</p>
 */
public class FliptableCommand implements CommandExecutor {

    /** Weltordner, die beim Reset verschwinden. */
    private static final String[] WORLD_NAMES = { "world", "world_nether", "world_the_end" };

    /**
     * Plugin-Dateien mit Spielzustand. Konfiguration (config.yml, loot.yml,
     * gamemasters.yml, Schematics) bleibt bewusst erhalten.
     */
    private static final String[] STATE_FILES = { "whitelist.yml", "teams.yml", "session.yml", "runtime.yml" };

    /** Maximale Wartezeit, bis Paper die session.lock einer Welt freigibt. */
    private static final long UNLOCK_TIMEOUT_MILLIS = 30_000L;

    private final HungerGamesPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);

    /** Verhindert doppelt registrierte Shutdown-Hooks bei mehrfachem Aufruf. */
    private static volatile boolean resetArmed = false;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permission check
        if (sender instanceof Player player) {
            if (!player.hasPermission("hungergames.admin") && !player.isOp()) {
                Sender.msg(player.getName(), "§c§lAccess Denied. §cThis command requires admin privileges.");
                return true;
            }
        }

        // Confirmation check
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage("§c§l⚠ WARNING: This will DELETE ALL WORLDS and RESET ALL DATA!");
            sender.sendMessage("§eType §6/fliptable confirm §eto proceed.");
            sender.sendMessage("§7This action cannot be undone.");
            return true;
        }

        if (resetArmed) {
            sender.sendMessage("§eReset läuft bereits — der Server fährt gleich herunter.");
            return true;
        }
        resetArmed = true;

        Sender.log("§c§l(╯°□°)╯︵ ┻━┻ FLIPTABLE INITIATED by " + sender.getName());

        // Step 1: Kick all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.kickPlayer("§c§l(╯°□°)╯︵ ┻━┻\n\n§eServer is being reset.\n§7Please rejoin in a moment.");
        }

        // Step 2: Reset GameState
        GameState.getInstance().reset();
        Sender.log("GameState reset.");

        // Step 3: Löschung für den Shutdown vormerken (Welten UND Zustandsdateien —
        // beides würde der laufende Server sonst noch einmal schreiben).
        armShutdownReset();

        // Step 4: Schedule server shutdown (give time for cleanup)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Sender.log("Shutting down server for reset — Welten werden nach dem Speichern gelöscht.");
            Bukkit.shutdown();
        }, 40L); // 2 seconds delay

        return true;
    }

    /**
     * Registriert einen JVM-Shutdown-Hook, der Welten und Zustandsdateien löscht,
     * sobald der Server sie geschlossen hat. Zu diesem Zeitpunkt sind Plugins
     * deaktiviert und die Level gespeichert — es kann also nichts mehr
     * nachträglich in die Ordner geschrieben werden.
     */
    private void armShutdownReset() {
        File worldContainer = Bukkit.getWorldContainer();
        File dataFolder = plugin.getDataFolder();

        Thread hook = new Thread(() -> performReset(worldContainer, dataFolder), "HungerGames-Fliptable-Reset");
        Runtime.getRuntime().addShutdownHook(hook);

        Sender.log("Fliptable vorgemerkt: Welten " + String.join(", ", WORLD_NAMES)
                + " werden beim Herunterfahren gelöscht.");
    }

    /**
     * Läuft im Shutdown-Hook — kein Bukkit-API mehr verfügbar, daher Ausgaben
     * über {@link System#out}.
     */
    private static void performReset(File worldContainer, File dataFolder) {
        note("(╯°□°)╯︵ ┻━┻ Fliptable: lösche Welten und Spielzustand …");

        for (String worldName : WORLD_NAMES) {
            File worldFolder = new File(worldContainer, worldName);
            if (!worldFolder.isDirectory()) {
                continue;
            }
            if (!awaitWorldClosed(worldFolder)) {
                note("WARNUNG: " + worldName + " ist nach " + (UNLOCK_TIMEOUT_MILLIS / 1000)
                        + "s noch gesperrt — es wird trotzdem gelöscht.");
            }
            if (deleteDirectory(worldFolder.toPath())) {
                note("Weltordner gelöscht: " + worldName);
            } else {
                note("FEHLER: Weltordner " + worldName + " konnte nicht vollständig gelöscht werden."
                        + " Bitte manuell entfernen, sonst startet der Server nicht sauber neu.");
            }
        }

        for (String fileName : STATE_FILES) {
            File file = new File(dataFolder, fileName);
            if (!file.exists()) {
                continue;
            }
            if (file.delete()) {
                note("Gelöscht: " + fileName);
            } else {
                note("WARNUNG: Konnte " + fileName + " nicht löschen.");
            }
        }

        note("Fliptable abgeschlossen — beim nächsten Start werden Welten neu generiert.");
    }

    /**
     * Wartet, bis Paper die {@code session.lock} der Welt freigegeben hat, also
     * das Level wirklich geschlossen ist.
     *
     * @return true, wenn die Welt frei ist; false bei Timeout
     */
    private static boolean awaitWorldClosed(File worldFolder) {
        File sessionLock = new File(worldFolder, "session.lock");
        long deadline = System.currentTimeMillis() + UNLOCK_TIMEOUT_MILLIS;

        while (true) {
            if (!sessionLock.exists() || isUnlocked(sessionLock)) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /** Prüft, ob die session.lock der Welt frei ist (Lock testweise anfordern). */
    private static boolean isUnlocked(File sessionLock) {
        try (RandomAccessFile raf = new RandomAccessFile(sessionLock, "rw");
                FileChannel channel = raf.getChannel()) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                return false; // von einem anderen Prozess gehalten
            }
            lock.release();
            return true;
        } catch (OverlappingFileLockException e) {
            return false; // noch von dieser JVM (dem Server) gehalten
        } catch (IOException e) {
            return true; // Datei nicht (mehr) sinnvoll prüfbar — Löschen versuchen
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @return true, wenn danach nichts mehr übrig ist
     */
    private static boolean deleteDirectory(Path path) {
        if (!Files.exists(path)) {
            return true;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            note("Konnte nicht löschen: " + p + " (" + e.getMessage() + ")");
                        }
                    });
        } catch (IOException e) {
            note("Fehler beim Löschen von " + path + ": " + e.getMessage());
        }
        return !Files.exists(path);
    }

    private static void note(String message) {
        System.out.println("[HungerGames] " + message);
    }
}
