package com.hungergames.stagerunner;

import com.hungergames.api.model.GamePhase;
import com.hungergames.GameState;
import com.hungergames.HungerGamesPlugin;
import com.hungergames.player.WhitelistManager;
import com.hungergames.utils.ConfigManager;
import com.hungergames.utils.Sender;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;
import java.util.UUID;

/**
 * Handles the start phase - countdown before game begins.
 * Manages boss bar countdown, de-ops admins, clears effects, and triggers game
 * start.
 */
public class StartRunner {

    /**
     * Läuft gerade ein Countdown? Anders als bei {@code /init} und {@code /startup}
     * wechselt die Phase beim Start nicht sofort (READY bleibt bis zum Ablauf des
     * Countdowns), deshalb braucht es hier ein eigenes Flag — sonst startet jeder
     * weitere {@code /start}, GUI-Klick oder API-Call einen zweiten Countdown mit
     * zweiter Bossbar und doppeltem Spielstart.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean COUNTDOWN_ACTIVE =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private final ConfigManager config = ConfigManager.getInstance();
    private final HungerGamesPlugin plugin = JavaPlugin.getPlugin(HungerGamesPlugin.class);
    private final Random random = new Random();

    private BossBar countdownBar;

    /**
     * @return {@code true}, solange ein Countdown läuft. Die zusätzliche
     *         Phasenprüfung räumt das Flag implizit auf, wenn die Runde
     *         zwischendurch zurückgesetzt wurde (Reset, {@code /fliptable}) —
     *         so kann kein hängengebliebenes Flag den Start blockieren.
     */
    public static boolean isCountdownActive() {
        return COUNTDOWN_ACTIVE.get()
                && GameState.getInstance().getCurrentPhase() == GamePhase.READY;
    }

    /** Konfigurierter Countdown-Text ({seconds}-Platzhalter) für die Bossbar. */
    private String countdownTitle(int remaining) {
        var services = plugin.adminServices();
        return com.hungergames.platform.announce.AnnouncementService.format(
                services.config.get(services.settings.textCountdown),
                java.util.Map.of("seconds", String.valueOf(remaining)));
    }

    /**
     * Executes the start sequence.
     *
     * @param adminUuid UUID of the admin who triggered the start
     * @return {@code false}, wenn die Phase keinen Start zulässt oder bereits
     *         ein Countdown läuft — dann passiert nichts
     */
    public boolean execute(UUID adminUuid) {
        Player admin = Bukkit.getPlayer(adminUuid);

        GamePhase phase = GameState.getInstance().getCurrentPhase();
        if (phase != GamePhase.READY) {
            if (admin != null) {
                admin.sendMessage("§cStart nur in der Phase READY möglich (aktuell: " + phase + ").");
            }
            return false;
        }
        if (!COUNTDOWN_ACTIVE.compareAndSet(false, true)) {
            if (admin != null) {
                admin.sendMessage("§cDer Countdown läuft bereits — die Spiele starten gleich von selbst.");
            }
            Sender.warning("Zweiter Start-Versuch ignoriert: Countdown läuft bereits.");
            return false;
        }

        Sender.log("Starting game countdown sequence...");

        // De-Op-Logik entfernt: Sie sperrte den Spielleiter dauerhaft aus der
        // Administration aus (OP wurde nie wiederhergestellt) und ist durch
        // den Permission- und Protection-Layer überflüssig.

        // Step 2: Remove all effects from players except slowness
        clearPlayerEffectsExceptSlowness();
        Sender.log("Cleared player effects (kept slowness)");

        // Step 3: Start countdown with boss bar
        int countdownSeconds = config.getCountdown();
        startCountdown(countdownSeconds, admin);
        return true;
    }

    /**
     * Clears all potion effects from players except slowness.
     */
    private void clearPlayerEffectsExceptSlowness() {
        WhitelistManager whitelist = WhitelistManager.getInstance();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!whitelist.isWhitelisted(player.getName()))
                continue;

            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType() != PotionEffectType.SLOWNESS) {
                    player.removePotionEffect(effect.getType());
                }
            }
        }
    }

    /**
     * Starts the countdown with boss bar display.
     */
    private void startCountdown(int seconds, Player admin) {
        // Create boss bar
        countdownBar = Bukkit.createBossBar(countdownTitle(seconds), BarColor.RED, BarStyle.SOLID);
        countdownBar.setProgress(1.0);

        // Add all players to boss bar
        for (Player player : Bukkit.getOnlinePlayers()) {
            countdownBar.addPlayer(player);
        }

        // Start countdown task
        new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                // Runde wurde zwischendurch zurückgesetzt oder anderweitig
                // weitergeschaltet — Countdown abbrechen statt in die falsche
                // Phase hinein zu starten.
                if (GameState.getInstance().getCurrentPhase() != GamePhase.READY) {
                    countdownBar.removeAll();
                    COUNTDOWN_ACTIVE.set(false);
                    Sender.warning("Countdown abgebrochen: Phase ist nicht mehr READY.");
                    this.cancel();
                    return;
                }

                if (remaining <= 0) {
                    // Countdown complete
                    countdownBar.removeAll();
                    onCountdownComplete(admin);
                    this.cancel();
                    return;
                }

                // Update boss bar
                double progress = (double) remaining / seconds;
                countdownBar.setProgress(progress);
                countdownBar.setTitle(countdownTitle(remaining));

                // Change color as countdown progresses
                if (remaining <= 5) {
                    countdownBar.setColor(BarColor.YELLOW);
                }
                if (remaining <= 3) {
                    countdownBar.setColor(BarColor.GREEN);
                }

                // Play sounds at specific intervals
                if (remaining <= 10) {
                    playCountdownSound(remaining);
                }

                // Show title at key moments
                if (remaining <= 5) {
                    showCountdownTitle(remaining);
                }

                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Every second
    }

    /**
     * Plays the configured countdown sound ({@code sounds.countdown}).
     */
    private void playCountdownSound(int secondsRemaining) {
        var services = plugin.adminServices();
        services.sounds.playToAll(services.settings.soundCountdown);
    }

    /**
     * Shows countdown title to all players.
     */
    private void showCountdownTitle(int secondsRemaining) {
        String color;
        if (secondsRemaining <= 1) {
            color = "§a§l";
        } else if (secondsRemaining <= 3) {
            color = "§e§l";
        } else {
            color = "§c§l";
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(color + secondsRemaining, "§7Get ready...", 0, 25, 5);
        }
    }

    /**
     * Called when countdown reaches zero.
     */
    private void onCountdownComplete(Player admin) {
        COUNTDOWN_ACTIVE.set(false);
        Sender.log("Countdown complete! Starting the Hunger Games!");

        // Fire fireworks above arena center
        fireStartFireworks();
        Sender.log("Launched start fireworks");

        // Release: Barrier-Ringe um die Plattformen entfernen — erst jetzt
        // können die Tribute loslaufen.
        for (Location platformPos : GameState.getInstance().getPlatformPositions()) {
            StartupRunner.removeBarrierRing(platformPos.clone().add(0, 1, 0));
        }
        Sender.log("Removed barrier rings around all platforms");

        WhitelistManager whitelist = WhitelistManager.getInstance();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (whitelist.isWhitelisted(player.getName())) {
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.removePotionEffect(PotionEffectType.JUMP_BOOST);

                // Set to survival mode so players can break blocks and collect items
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        Sender.log("Removed slowness from all players");

        // Konfigurierte Spielstart-Ansage + -Sound (announcements.text.game-start, sounds.game-start)
        var services = plugin.adminServices();
        services.announcements.broadcast(services.settings.textGameStart, java.util.Map.of(),
                com.hungergames.platform.announce.AnnouncementService.Style.CHAT,
                com.hungergames.platform.announce.AnnouncementService.Style.TITLE);
        services.sounds.playToAll(services.settings.soundGameStart);

        // Transition to RUNNING phase
        GameState.getInstance().setCurrentPhase(GamePhase.RUNNING);

        // Spiel-Difficulty (game.difficulty, Default NORMAL) setzen und die
        // Weltzeit auf den Startup-Wert zurückdrehen, damit jede Runde zur
        // gleichen (Sonnenuntergangs-)Zeit beginnt.
        Location arenaCenter = GameState.getInstance().getArenaCenter();
        if (arenaCenter != null && arenaCenter.getWorld() != null) {
            arenaCenter.getWorld().setDifficulty(config.getGameDifficulty());
            arenaCenter.getWorld().setTime(StartupRunner.STARTUP_WORLD_TIME);
        }

    }

    /**
     * Fires celebratory fireworks above the arena center.
     */
    private void fireStartFireworks() {
        Location arenaCenter = GameState.getInstance().getArenaCenter();
        if (arenaCenter == null)
            return;

        World world = arenaCenter.getWorld();
        if (world == null)
            return;

        // Fire multiple fireworks in a pattern
        Color[] colors = { Color.ORANGE, Color.RED, Color.YELLOW, Color.MAROON };

        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI * i) / 8;
            double radius = 5;
            double x = arenaCenter.getX() + radius * Math.cos(angle);
            double z = arenaCenter.getZ() + radius * Math.sin(angle);

            Location fireworkLoc = new Location(world, x, arenaCenter.getY() + 5, z);

            final int index = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    spawnFirework(fireworkLoc, colors[index % colors.length]);
                }
            }.runTaskLater(plugin, i * 3L); // Stagger the fireworks
        }

        // Center burst
        new BukkitRunnable() {
            @Override
            public void run() {
                spawnFirework(arenaCenter.clone().add(0, 8, 0), Color.WHITE);
            }
        }.runTaskLater(plugin, 25L);
    }

    /**
     * Spawns a single firework at the given location.
     */
    private void spawnFirework(Location location, Color color) {
        Firework firework = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = firework.getFireworkMeta();

        FireworkEffect effect = FireworkEffect.builder()
                .flicker(true)
                .trail(true)
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(color)
                .withFade(Color.WHITE)
                .build();

        meta.addEffect(effect);
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

}
