package com.hungergames.platform.effect;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.platform.config.HgSettings;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Zentrales System für visuelle Effekte (Partikel) — das optische Gegenstück zum
 * {@link com.hungergames.platform.sound.SoundService}. Ein Event kann mehrere
 * Partikel-Ebenen gleichzeitig zeigen.
 *
 * <p>Format pro Ebene: {@code PARTIKEL[@anzahl][~streuung][*tempo][>delayMs][^wdh][#RRGGBB]},
 * mehrere durch {@code ;}/{@code ,} getrennt. Beispiel:
 * {@code FLAME@40~0.6; DUST@30~0.4#ff0000>200}. Farbe wirkt nur bei DUST-Partikeln.</p>
 */
public final class EffectService {

    private static final Pattern LIST_SEPARATOR = Pattern.compile("[;,]");
    private static final Pattern COUNT_PAT = Pattern.compile("@([0-9]+)");
    private static final Pattern SPREAD_PAT = Pattern.compile("~([0-9.]+)");
    private static final Pattern SPEED_PAT = Pattern.compile("\\*([0-9.]+)");
    private static final Pattern DELAY_PAT = Pattern.compile(">([0-9]+)");
    private static final Pattern REPEAT_PAT = Pattern.compile("\\^([0-9]+)");
    private static final Pattern COLOR_PAT = Pattern.compile("#([0-9A-Fa-f]{6})");
    /** Alle Marker aus dem Partikelnamen strippen. */
    private static final Pattern MARKERS = Pattern.compile("[@~*>^][0-9.]*|#[0-9A-Fa-f]{6}");

    private static final int MAX_COUNT = 500;
    private static final int MAX_DELAY_MS = 10000;
    private static final int MAX_REPEATS = 10;
    private static final long REPEAT_INTERVAL_TICKS = 4L;

    private final Plugin plugin;
    private final ConfigurationService config;
    private final HgSettings settings;

    public EffectService(Plugin plugin, ConfigurationService config, HgSettings settings) {
        this.plugin = plugin;
        this.config = config;
        this.settings = settings;
    }

    /** Eine Partikel-Ebene mit allen Parametern. */
    private record Layer(Particle particle, int count, double spread, double speed,
                         int delayMs, int repeats, Color color) {
    }

    // ==================== Abspielen ====================

    /** Zeigt den Effekt an einer festen Position (für alle in Sichtweite). */
    public void playAt(Location location, SettingDefinition<String> effectSetting) {
        if (!config.get(settings.effectsEnabled) || location == null || location.getWorld() == null) {
            return;
        }
        Location loc = location.clone();
        emit(buildLayers(config.get(effectSetting)), loc);
    }

    /** Zeigt den Effekt bei jedem Online-Spieler an dessen Position. */
    public void playToAll(SettingDefinition<String> effectSetting) {
        if (!config.get(settings.effectsEnabled)) {
            return;
        }
        List<Layer> layers = buildLayers(config.get(effectSetting));
        for (Player player : Bukkit.getOnlinePlayers()) {
            emit(layers, player.getLocation().add(0, 1, 0));
        }
    }

    /**
     * Test-Wiedergabe eines rohen Effekt-Strings (für den Test-Button).
     *
     * @return {@code false}, wenn keine einzige Ebene auflösbar ist
     */
    public boolean test(Player player, String rawSpec) {
        List<Layer> layers = buildLayers(rawSpec);
        if (layers.isEmpty()) {
            return false;
        }
        emit(layers, player.getLocation().add(0, 1, 0));
        return true;
    }

    /** Zeigt alle Ebenen: je Ebene Startverzögerung, dann Burst + Wiederholungen. */
    private void emit(List<Layer> layers, Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        for (Layer layer : layers) {
            Runnable burst = () -> spawn(world, loc, layer);
            long delayTicks = msToTicks(layer.delayMs());
            for (int r = 0; r <= layer.repeats(); r++) {
                long at = delayTicks + (long) r * REPEAT_INTERVAL_TICKS;
                if (at <= 0) {
                    burst.run();
                } else {
                    plugin.getServer().getScheduler().runTaskLater(plugin, burst, at);
                }
            }
        }
    }

    private void spawn(World world, Location loc, Layer layer) {
        double s = layer.spread();
        try {
            if (acceptsDust(layer.particle())) {
                Color c = layer.color() != null ? layer.color() : Color.RED;
                Particle.DustOptions dust = new Particle.DustOptions(c, 1.4f);
                world.spawnParticle(layer.particle(), loc.getX(), loc.getY(), loc.getZ(),
                        layer.count(), s, s, s, layer.speed(), dust);
            } else {
                world.spawnParticle(layer.particle(), loc.getX(), loc.getY(), loc.getZ(),
                        layer.count(), s, s, s, layer.speed());
            }
        } catch (RuntimeException e) {
            // Manche Partikel (BLOCK, ITEM, VIBRATION …) brauchen Sonderdaten und
            // werfen ohne sie — solche Auswahl still ignorieren statt crashen.
            world.spawnParticle(Particle.POOF, loc.getX(), loc.getY(), loc.getZ(),
                    layer.count(), s, s, s, 0);
        }
    }

    private static boolean acceptsDust(Particle particle) {
        return particle == Particle.DUST;
    }

    private static long msToTicks(int ms) {
        return ms <= 0 ? 0 : Math.round(ms / 50.0);
    }

    // ==================== Auflösung ====================

    private List<Layer> buildLayers(String rawSpec) {
        List<Layer> layers = new ArrayList<>();
        for (String token : LIST_SEPARATOR.split(rawSpec == null ? "" : rawSpec)) {
            Layer layer = buildLayer(token);
            if (layer != null) {
                layers.add(layer);
            }
        }
        return layers;
    }

    private Layer buildLayer(String rawEntry) {
        String entry = rawEntry.trim();
        if (entry.isEmpty()) {
            return null;
        }
        int count = 20;
        double spread = 0.4;
        double speed = 0.0;
        int delayMs = 0;
        int repeats = 0;
        Color color = null;

        var cm = COUNT_PAT.matcher(entry);
        if (cm.find()) {
            count = Math.min(MAX_COUNT, Math.max(1, parseInt(cm.group(1), count)));
        }
        var sm = SPREAD_PAT.matcher(entry);
        if (sm.find()) {
            spread = Math.max(0, parseDouble(sm.group(1), spread));
        }
        var vm = SPEED_PAT.matcher(entry);
        if (vm.find()) {
            speed = Math.max(0, parseDouble(vm.group(1), speed));
        }
        var dm = DELAY_PAT.matcher(entry);
        if (dm.find()) {
            delayMs = Math.min(MAX_DELAY_MS, Math.max(0, parseInt(dm.group(1), 0)));
        }
        var rm = REPEAT_PAT.matcher(entry);
        if (rm.find()) {
            repeats = Math.min(MAX_REPEATS, Math.max(0, parseInt(rm.group(1), 0)));
        }
        var colm = COLOR_PAT.matcher(entry);
        if (colm.find()) {
            try {
                color = Color.fromRGB(Integer.parseInt(colm.group(1), 16));
            } catch (IllegalArgumentException ignored) {
                // ungültige Farbe → ohne
            }
        }

        Particle particle = resolveParticle(MARKERS.matcher(entry).replaceAll("").trim());
        if (particle == null) {
            return null;
        }
        return new Layer(particle, count, spread, speed, delayMs, repeats, color);
    }

    private static Particle resolveParticle(String raw) {
        try {
            return Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Alle Partikelnamen (für die GUI-Auswahl), alphabetisch. */
    public static List<String> allParticleNames() {
        List<String> names = new ArrayList<>();
        for (Particle particle : Particle.values()) {
            // Einige Partikel sind interne/Block-gebundene, die ohne Daten crashen —
            // die gängigen, datenlosen decken den GUI-Bedarf ab; DUST wird gesondert
            // (mit Farbe) unterstützt.
            names.add(particle.name());
        }
        names.sort(String::compareTo);
        return names;
    }

    // ==================== Validierung ====================

    /** Format-Validierung eines Effekt-Werts; leer = kein Effekt (erlaubt). */
    public static Optional<String> validateEffectSpec(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        for (String token : LIST_SEPARATOR.split(trimmed)) {
            String name = MARKERS.matcher(token.trim()).replaceAll("").trim();
            if (name.isEmpty()) {
                continue;
            }
            if (resolveParticle(name) == null) {
                return Optional.of("\"" + name + "\" ist kein gültiger Partikel-Typ");
            }
        }
        return Optional.empty();
    }
}
