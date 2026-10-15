package com.hungergames.platform.event;

import com.hungergames.api.event.GamePhaseChangeEvent;
import com.hungergames.api.model.GamePhase;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.log.RoundLogService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Monster-Wellen: Ein Spielmacher wählt einen Ort und startet dort in mehreren
 * Wellen (mit konfigurierbarem Abstand) Gruppen von Monstern. Jede Welle spawnt
 * eine konfigurierte Anzahl im Umkreis; nach einer konfigurierten Zahl von
 * Wellen endet die Serie.
 */
public final class MonsterWaveService implements Listener {

    /** Eine laufende Wellen-Serie an einem Ort. */
    private static final class ActiveWaves {
        final Location center;
        final EntityType type;
        final int countPerWave;
        final int totalWaves;
        final int spread;
        int wavesDone;
        BukkitTask task;

        ActiveWaves(Location center, EntityType type, int countPerWave, int totalWaves, int spread) {
            this.center = center;
            this.type = type;
            this.countPerWave = countPerWave;
            this.totalWaves = totalWaves;
            this.spread = spread;
        }
    }

    private final Plugin plugin;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final RoundLogService roundLog;
    private final List<ActiveWaves> active = new CopyOnWriteArrayList<>();

    public MonsterWaveService(Plugin plugin, ConfigurationService config, HgSettings settings,
                              RoundLogService roundLog) {
        this.plugin = plugin;
        this.config = config;
        this.settings = settings;
        this.roundLog = roundLog;
    }

    // ==================== Steuerung ====================

    public int activeSeries() {
        return active.size();
    }

    public String defaultMob() {
        return config.get(settings.monsterWaveMob);
    }

    public int defaultCount() {
        return config.get(settings.monsterWaveCount);
    }

    public int defaultWaves() {
        return config.get(settings.monsterWaveWaves);
    }

    public int defaultInterval() {
        return config.get(settings.monsterWaveInterval);
    }

    /**
     * Startet eine Wellen-Serie am angegebenen Ort.
     *
     * @return Fehlermeldung oder leer bei Erfolg
     */
    public Optional<String> start(Location center, String mobName, int countPerWave,
                                  int totalWaves, int intervalSeconds, String actor) {
        if (center.getWorld() == null) {
            return Optional.of("Kein gültiger Ort.");
        }
        EntityType type = resolveMonster(mobName);
        if (type == null) {
            return Optional.of("\"" + mobName + "\" ist kein spawnbares Monster.");
        }
        if (countPerWave <= 0 || totalWaves <= 0) {
            return Optional.of("Anzahl und Wellen müssen > 0 sein.");
        }
        int intervalTicks = Math.max(1, intervalSeconds) * 20;
        int spread = Math.max(0, config.get(settings.monsterWaveSpread));

        ActiveWaves waves = new ActiveWaves(center.clone(), type, countPerWave, totalWaves, spread);
        // Erste Welle sofort, danach im Intervall.
        waves.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (waves.wavesDone >= waves.totalWaves) {
                stop(waves);
                return;
            }
            spawnWave(waves);
            waves.wavesDone++;
            if (waves.wavesDone >= waves.totalWaves) {
                stop(waves);
            }
        }, 0L, intervalTicks);
        active.add(waves);

        roundLog.log("WAVES", actor + " startet " + totalWaves + " " + type + "-Wellen à "
                + countPerWave + " (Intervall " + intervalSeconds + "s)", center);
        return Optional.empty();
    }

    private void spawnWave(ActiveWaves waves) {
        World world = waves.center.getWorld();
        if (world == null) {
            return;
        }
        var rng = ThreadLocalRandom.current();
        for (int i = 0; i < waves.countPerWave; i++) {
            double ox = waves.spread > 0 ? (rng.nextDouble() * 2 - 1) * waves.spread : 0;
            double oz = waves.spread > 0 ? (rng.nextDouble() * 2 - 1) * waves.spread : 0;
            int x = waves.center.getBlockX() + (int) Math.round(ox);
            int z = waves.center.getBlockZ() + (int) Math.round(oz);
            Location spawn = new Location(world, x + 0.5,
                    world.getHighestBlockYAt(x, z) + 1, z + 0.5);
            try {
                world.spawnEntity(spawn, waves.type);
            } catch (RuntimeException e) {
                // z. B. unmöglicher Spawn — überspringen
                plugin.getLogger().warning("Monster-Welle: Spawn fehlgeschlagen: " + e.getMessage());
            }
        }
    }

    /** Stoppt alle laufenden Serien und gibt die Anzahl zurück. */
    public int stopAll() {
        int count = active.size();
        for (ActiveWaves waves : active) {
            if (waves.task != null) {
                waves.task.cancel();
            }
        }
        active.clear();
        return count;
    }

    private void stop(ActiveWaves waves) {
        if (waves.task != null) {
            waves.task.cancel();
        }
        active.remove(waves);
    }

    /** Bei Rundenreset/-ende alle Wellen beenden. */
    @EventHandler
    public void onPhaseChange(GamePhaseChangeEvent event) {
        if (event.getNewPhase() == GamePhase.NOT_INITIALIZED
                || event.getNewPhase() == GamePhase.FINISHED) {
            stopAll();
        }
    }

    // ==================== Auflösung ====================

    /** Prüft, ob {@code name} ein spawnbares, lebendes Monster ist. */
    public static EntityType resolveMonster(String name) {
        EntityType type;
        try {
            type = EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Class<? extends Entity> clazz = type.getEntityClass();
        if (clazz == null || !type.isSpawnable() || !LivingEntity.class.isAssignableFrom(clazz)) {
            return null;
        }
        return type;
    }
}
