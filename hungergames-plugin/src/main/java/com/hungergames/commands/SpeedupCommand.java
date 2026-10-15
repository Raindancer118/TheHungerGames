package com.hungergames.commands;

import com.hungergames.GameState;
import com.hungergames.HungerGamesPlugin;
import com.hungergames.utils.Sender;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Command handler for /speedup - Accelerates game time for testing purposes.
 * 
 * This command allows gamemasters to speed up the in-game time during testing.
 * The speedup affects the world's day/night cycle, making time pass faster.
 * 
 * Usage:
 * - /speedup <multiplier> [autoStop] - Sets time speed multiplier
 * Examples:
 * - /speedup 4 - Time runs 4x faster, continues until manually disabled
 * - /speedup 4 true - Time runs 4x faster, stops automatically on game events
 * - /speedup off - Disables time speedup and returns to normal time flow
 * - /speedup - Without arguments, shows current speedup status
 * 
 * Auto-stop events (when autoStop is true):
 * - Player death
 * - Worldborder shrink starts
 * - Worldborder shrink completes
 * 
 * The multiplier must be a positive number. Higher values = faster time.
 * A multiplier of 1 is equivalent to normal time flow.
 * 
 * Note: This is intended for testing only. Time speedup affects all players
 * and the world's day/night cycle.
 * 
 * Technical implementation:
 * - Uses a repeating task that adds extra time ticks to the world each second
 * - Normal time: 20 ticks/second = 1 second real time per 1 second game time
 * - With multiplier N: adds (N-1) * 20 extra ticks per second
 */
public class SpeedupCommand implements CommandExecutor {

    private final HungerGamesPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);

    /** Current speedup task, null if no speedup is active */
    private static BukkitTask speedupTask = null;

    /** Current speedup multiplier, 1.0 = normal speed */
    private static double currentMultiplier = 1.0;

    /** Von der Hauptklasse gesetzt: koppelt die virtuelle Spielzeit an den Speedup. */
    private static com.hungergames.platform.VirtualTime virtualTime;

    public static void bindVirtualTime(com.hungergames.platform.VirtualTime vt) {
        virtualTime = vt;
    }

    /** Whether to automatically stop speedup on game events */
    private static boolean autoStopEnabled = false;

    /**
     * Game event types that can trigger auto-stop.
     * Used by external classes (e.g., GameRunner) to notify this command
     * when significant game events occur.
     */
    public enum GameEvent {
        /** A player has died */
        PLAYER_DEATH,
        /** Worldborder has started shrinking */
        WORLDBORDER_SHRINK_START,
        /** Worldborder has finished shrinking to target size */
        WORLDBORDER_SHRINK_COMPLETE
    }

    /**
     * Handles the /speedup command execution.
     * 
     * @param sender  Command sender (must be a player with gamemaster permission)
     * @param command The command object
     * @param label   The command label used
     * @param args    Command arguments: [multiplier|off] [autoStop]
     * @return true if command was handled
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // No arguments - show current status
        if (args.length == 0) {
            showStatus(player);
            return true;
        }

        String arg = args[0].toLowerCase();

        // Handle "off" argument - disable speedup
        if (arg.equals("off") || arg.equals("0") || arg.equals("disable") || arg.equals("reset")) {
            disableSpeedup(player);
            return true;
        }

        // Parse multiplier
        double multiplier;
        try {
            multiplier = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid multiplier: §e" + args[0]);
            player.sendMessage("§7Usage: §e/speedup <multiplier> [true|false] §7or §e/speedup off");
            return true;
        }

        // Validate multiplier
        if (multiplier <= 0) {
            player.sendMessage("§cMultiplier must be a positive number!");
            return true;
        }

        if (multiplier > 100) {
            player.sendMessage("§cMultiplier capped at 100x for stability!");
            multiplier = 100;
        }

        // Parse optional auto-stop flag (default: false)
        boolean autoStop = false;
        if (args.length >= 2) {
            String autoStopArg = args[1].toLowerCase();
            autoStop = autoStopArg.equals("true") || autoStopArg.equals("1") || autoStopArg.equals("yes");
        }

        // Enable speedup
        enableSpeedup(player, multiplier, autoStop);
        return true;
    }

    /**
     * Shows the current speedup status to the player.
     * 
     * @param player The player to show status to
     */
    private void showStatus(Player player) {
        if (speedupTask == null || currentMultiplier == 1.0) {
            player.sendMessage("§7Time speedup is currently §cdisabled§7 (1x normal speed).");
        } else {
            String autoStopText = autoStopEnabled ? " §7(§eauto-stop enabled§7)" : "";
            player.sendMessage(
                    "§7Time speedup is currently §aactive§7 at §e" + currentMultiplier + "x§7 speed." + autoStopText);
        }
        player.sendMessage("§7Usage: §e/speedup <multiplier> [true|false] §7or §e/speedup off");
        player.sendMessage("§7Example: §e/speedup 4 true §7- 4x speed with auto-stop on events");
    }

    /**
     * Enables time speedup with the specified multiplier.
     * 
     * The speedup is implemented by adding extra time ticks to the world's time
     * on a repeating schedule. This effectively makes the day/night cycle faster.
     * 
     * @param player     The player who triggered the command
     * @param multiplier The speedup multiplier (e.g., 4 = 4x faster)
     * @param autoStop   Whether to automatically stop on game events
     */
    private void enableSpeedup(Player player, double multiplier, boolean autoStop) {
        // Cancel existing task if any
        if (speedupTask != null) {
            speedupTask.cancel();
            speedupTask = null;
        }

        currentMultiplier = multiplier;
        if (virtualTime != null) virtualTime.setMultiplier(multiplier);
        autoStopEnabled = autoStop;

        // If multiplier is 1, no need for a task
        if (multiplier == 1.0) {
            player.sendMessage("§7Time speedup §cdisabled§7 - running at normal speed.");
            Sender.log("Time speedup disabled by " + player.getName());
            return;
        }

        // Calculate extra ticks to add per second
        // Normal: 20 ticks/second game time = 1x speed
        // With multiplier N: we need to add (N-1) * 20 extra ticks per second
        // But we run the task more frequently (every 5 ticks) for smoother time
        // progression
        final int tickInterval = 5; // Run every 5 game ticks (0.25 seconds)
        final long extraTicksPerInterval = (long) ((multiplier - 1) * tickInterval);

        // Start the speedup task
        speedupTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Get the arena world
                World world = getArenaWorld();
                if (world == null) {
                    return;
                }

                // Add extra ticks to the world's time
                world.setFullTime(world.getFullTime() + extraTicksPerInterval);
            }
        }.runTaskTimer(plugin, tickInterval, tickInterval);

        String autoStopText = autoStop ? " §7(auto-stop enabled)" : "";
        player.sendMessage("§aTime speedup enabled at §e" + multiplier + "x§a speed!" + autoStopText);
        player.sendMessage("§7Use §e/speedup off§7 to disable.");

        Sender.log("Time speedup enabled at " + multiplier + "x by " + player.getName()
                + (autoStop ? " (auto-stop enabled)" : ""));
    }

    /**
     * Disables time speedup and returns to normal time flow.
     * 
     * @param player The player who triggered the command (can be null for automatic
     *               stops)
     */
    private void disableSpeedup(Player player) {
        if (speedupTask != null) {
            speedupTask.cancel();
            speedupTask = null;
        }

        currentMultiplier = 1.0;
        if (virtualTime != null) virtualTime.setMultiplier(1.0);
        autoStopEnabled = false;

        if (player != null) {
            player.sendMessage("§7Time speedup §cdisabled§7 - running at normal speed.");
            Sender.log("Time speedup disabled by " + player.getName());
        }
    }

    /**
     * Called by external game systems (e.g., GameRunner) to notify of game events.
     * If auto-stop is enabled, this will disable the speedup and notify players.
     * 
     * @param event The type of game event that occurred
     */
    public static void onGameEvent(GameEvent event) {
        // Only process if speedup is active and auto-stop is enabled
        if (!isSpeedupActive() || !autoStopEnabled) {
            return;
        }

        // Stop the speedup
        if (speedupTask != null) {
            speedupTask.cancel();
            speedupTask = null;
        }

        double previousMultiplier = currentMultiplier;
        currentMultiplier = 1.0;
        if (virtualTime != null) virtualTime.setMultiplier(1.0);
        autoStopEnabled = false;

        // Determine event description for message
        String eventDescription = switch (event) {
            case PLAYER_DEATH -> "Player death";
            case WORLDBORDER_SHRINK_START -> "Worldborder shrink started";
            case WORLDBORDER_SHRINK_COMPLETE -> "Worldborder shrink completed";
        };

        // Notify all online players (especially gamemasters)
        Bukkit.broadcastMessage("§e[Speedup] §7Time returned to normal speed. §8(" + eventDescription + ")");
        Sender.log("Auto-stopped speedup (" + previousMultiplier + "x) due to: " + eventDescription);
    }

    /**
     * Gets the arena world (where the game takes place).
     * Falls back to the first available world if arena center is not set.
     * 
     * @return The arena world, or null if no worlds are available
     */
    private World getArenaWorld() {
        GameState gameState = GameState.getInstance();
        if (gameState.getArenaCenter() != null && gameState.getArenaCenter().getWorld() != null) {
            return gameState.getArenaCenter().getWorld();
        }

        // Fallback to first world
        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0);
        }

        return null;
    }

    /**
     * Gets the current speedup multiplier.
     * 
     * @return Current multiplier (1.0 = normal speed)
     */
    public static double getCurrentMultiplier() {
        return currentMultiplier;
    }

    /**
     * Checks if time speedup is currently active.
     * 
     * @return true if speedup is active and multiplier > 1
     */
    public static boolean isSpeedupActive() {
        return speedupTask != null && currentMultiplier > 1.0;
    }

    /**
     * Checks if auto-stop is currently enabled.
     * 
     * @return true if auto-stop is enabled
     */
    public static boolean isAutoStopEnabled() {
        return autoStopEnabled;
    }
}
