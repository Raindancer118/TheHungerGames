package com.hungergames.platform.sound;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.platform.config.HgSettings;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Zentrales Sound-System. Akzeptiert sowohl Bukkit-Sound-Namen
 * ({@code ENTITY_WITHER_SPAWN}) als auch freie Resource-Pack-Keys
 * ({@code custom.halt}, {@code meinpack:event.start}).
 *
 * <p>Ein Event darf <b>mehrere Sounds gleichzeitig</b> auslösen: der Wert ist
 * eine mit {@code ;} oder {@code ,} getrennte Liste. Jeder Eintrag kann optional
 * eine eigene Lautstärke ({@code @2.0}) und Tonhöhe ({@code ~0.8}) tragen, z. B.
 * {@code ENTITY_WITHER_HURT; ENTITY_GENERIC_EXPLODE@1.5~0.7}. Ohne Angabe gelten
 * {@code sounds.default-volume/-pitch}.</p>
 *
 * <p>Unbekannte Sounds werden geloggt und übersprungen — nie geworfen.</p>
 */
public final class SoundService {

    /** Bukkit-Enum-Stil: Großbuchstaben, Ziffern, Unterstriche. */
    private static final java.util.regex.Pattern ENUM_STYLE =
            java.util.regex.Pattern.compile("[A-Z][A-Z0-9_]*");

    /** Trennzeichen zwischen mehreren Sounds eines Events. */
    private static final Pattern LIST_SEPARATOR = Pattern.compile("[;,]");
    private static final Pattern VOLUME_PAT = Pattern.compile("@([0-9.]+)");
    private static final Pattern PITCH_PAT = Pattern.compile("~([0-9.]+)");
    private static final Pattern ECHO_PAT = Pattern.compile("\\^(\\d*)");
    private static final Pattern DELAY_PAT = Pattern.compile(">(\\d+)");
    private static final Pattern DURATION_PAT = Pattern.compile("<(\\d+)");
    /** Alle Marker ({@code @1.5}, {@code ~0.8}, {@code ^2}, {@code >500}, {@code <2000}) strippen. */
    private static final Pattern MARKERS = Pattern.compile("[@~^><][0-9.]*");

    private static final int MAX_ECHOES = 5;
    private static final int DEFAULT_ECHOES = 2;
    private static final long ECHO_DELAY_TICKS = 5L;
    private static final double ECHO_DECAY = 0.55;
    private static final int MAX_DELAY_MS = 10000;
    private static final int MAX_DURATION_MS = 20000;

    private final Plugin plugin;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final Logger logger;
    private final Set<String> warnedKeys = ConcurrentHashMap.newKeySet();

    /** Lazy: Bukkit-Enum-Name → Sound-Key (aus der Registry abgeleitet). */
    private Map<String, Key> enumNameIndex;

    /**
     * Eine Klang-Ebene: Sound-Key mit Lautstärke/Tonhöhe, optionalen abklingenden
     * Echos, Startverzögerung ({@code delayMs}) und Abschneiden nach
     * {@code durationMs} (0 = voll ausspielen).
     */
    private record Layer(Key key, float volume, float pitch, int echoes, int delayMs, int durationMs) {
    }

    public SoundService(Plugin plugin, ConfigurationService config, HgSettings settings, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.settings = settings;
        this.logger = logger;
    }

    // ==================== Abspielen ====================

    /** Spielt den konfigurierten Sound allen Online-Spielern (am eigenen Ohr). */
    public void playToAll(SettingDefinition<String> soundSetting) {
        playTo(Bukkit.getOnlinePlayers(), soundSetting);
    }

    public void playTo(Collection<? extends Player> players, SettingDefinition<String> soundSetting) {
        if (!config.get(settings.soundsEnabled)) {
            return;
        }
        List<Player> audience = new ArrayList<>(players);
        emit(buildLayers(config.get(soundSetting)),
                sound -> {
                    for (Player player : audience) {
                        if (player.isOnline()) {
                            player.playSound(sound);
                        }
                    }
                },
                key -> {
                    for (Player player : audience) {
                        if (player.isOnline()) {
                            player.stopSound(SoundStop.named(key));
                        }
                    }
                });
    }

    public void playTo(Player player, SettingDefinition<String> soundSetting) {
        playTo(List.of(player), soundSetting);
    }

    /** Spielt alle Sounds positional in der Welt (hörbar in Reichweite). */
    public void playAt(Location location, SettingDefinition<String> soundSetting) {
        if (!config.get(settings.soundsEnabled) || location.getWorld() == null) {
            return;
        }
        Location loc = location.clone();
        emit(buildLayers(config.get(soundSetting)),
                sound -> loc.getWorld().playSound(sound, loc.getX(), loc.getY(), loc.getZ()),
                key -> {
                    // Positionale Sounds lassen sich nur pro Spieler stoppen.
                    for (Player player : loc.getWorld().getPlayers()) {
                        player.stopSound(SoundStop.named(key));
                    }
                });
    }

    /**
     * Test-Wiedergabe eines rohen Sound-Strings (für den Test-Button) — spielt
     * die komplette Ebenen-Kombination inkl. Echos, Verzögerung und Zeitlimit.
     *
     * @return {@code false}, wenn kein einziger Sound auflösbar ist
     */
    public boolean test(Player player, String rawSpec) {
        List<Layer> layers = buildLayers(rawSpec);
        if (layers.isEmpty()) {
            return false;
        }
        emit(layers, player::playSound, key -> player.stopSound(SoundStop.named(key)));
        return true;
    }

    /**
     * Spielt alle Ebenen: je Ebene Startverzögerung ({@code delayMs}), danach
     * Basis-Sound + abklingende Echos, optional gestoppt nach {@code durationMs}.
     */
    private void emit(List<Layer> layers, Consumer<Sound> play, Consumer<Key> stop) {
        for (Layer layer : layers) {
            Runnable start = () -> {
                play.accept(Sound.sound(layer.key(), Sound.Source.MASTER,
                        layer.volume(), layer.pitch()));
                for (int i = 1; i <= layer.echoes(); i++) {
                    float echoVolume = (float) (layer.volume() * Math.pow(ECHO_DECAY, i));
                    Sound echo = Sound.sound(layer.key(), Sound.Source.MASTER,
                            echoVolume, layer.pitch());
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> play.accept(echo), i * ECHO_DELAY_TICKS);
                }
                if (layer.durationMs() > 0) {
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> stop.accept(layer.key()), Math.max(1, msToTicks(layer.durationMs())));
                }
            };
            long delayTicks = msToTicks(layer.delayMs());
            if (delayTicks <= 0) {
                start.run();
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, start, delayTicks);
            }
        }
    }

    private static long msToTicks(int ms) {
        return ms <= 0 ? 0 : Math.round(ms / 50.0);
    }

    // ==================== Auflösung ====================

    /** Zerlegt einen Event-Wert in seine (ggf. mehreren) Klang-Ebenen. */
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

    /** Baut eine Ebene {@code KEY[@volume][~pitch][^echoes]}. */
    private Layer buildLayer(String rawEntry) {
        String entry = rawEntry.trim();
        if (entry.isEmpty()) {
            return null;
        }
        float volume = config.get(settings.soundDefaultVolume).floatValue();
        float pitch = config.get(settings.soundDefaultPitch).floatValue();
        int echoes = 0;

        var vm = VOLUME_PAT.matcher(entry);
        if (vm.find()) {
            volume = parseFloat(vm.group(1), volume);
        }
        var pm = PITCH_PAT.matcher(entry);
        if (pm.find()) {
            pitch = parseFloat(pm.group(1), pitch);
        }
        var em = ECHO_PAT.matcher(entry);
        if (em.find()) {
            echoes = em.group(1).isEmpty() ? DEFAULT_ECHOES
                    : Math.min(MAX_ECHOES, Math.max(0, parseInt(em.group(1), DEFAULT_ECHOES)));
        }
        int delayMs = 0;
        var dm = DELAY_PAT.matcher(entry);
        if (dm.find()) {
            delayMs = Math.min(MAX_DELAY_MS, Math.max(0, parseInt(dm.group(1), 0)));
        }
        int durationMs = 0;
        var um = DURATION_PAT.matcher(entry);
        if (um.find()) {
            durationMs = Math.min(MAX_DURATION_MS, Math.max(0, parseInt(um.group(1), 0)));
        }

        Key key = resolveKey(MARKERS.matcher(entry).replaceAll("").trim());
        if (key == null) {
            return null;
        }
        return new Layer(key, Math.max(0f, volume), pitch, echoes, delayMs, durationMs);
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Key resolveKey(String raw) {
        String trimmed = raw.trim();
        if (ENUM_STYLE.matcher(trimmed).matches()) {
            Key key = enumNameIndex().get(trimmed);
            if (key == null) {
                warnOnce(trimmed, "kein bekannter Bukkit-Sound-Name");
            }
            return key;
        }
        if (Key.parseable(trimmed)) {
            // Freier Key: Vanilla-Namespace, sofern keiner angegeben —
            // deckt Resource-Pack-Sounds wie "custom.halt" ab.
            return Key.key(trimmed);
        }
        warnOnce(trimmed, "kein gültiger Sound-Key");
        return null;
    }

    /**
     * Alle bekannten Sound-Namen (Bukkit-Enum-Stil), alphabetisch sortiert —
     * für die vollständige Sound-Auswahl im GUI.
     */
    public List<String> allSoundNames() {
        return enumNameIndex().keySet().stream().sorted().toList();
    }

    /** Index ENUM_NAME → Key, aus der Sound-Registry abgeleitet (einmalig). */
    private synchronized Map<String, Key> enumNameIndex() {
        if (enumNameIndex == null) {
            Map<String, Key> index = new HashMap<>();
            try {
                for (org.bukkit.Sound sound : Registry.SOUNDS) {
                    Key key = Registry.SOUNDS.getKey(sound);
                    if (key == null) {
                        continue; // Sounds können ohne Registry-Key existieren
                    }
                    index.put(key.value().toUpperCase(Locale.ROOT).replace('.', '_'), key);
                }
            } catch (RuntimeException | NoClassDefFoundError e) {
                logger.warning("Sound-Registry nicht verfügbar: " + e.getMessage());
            }
            enumNameIndex = index;
        }
        return enumNameIndex;
    }

    private void warnOnce(String key, String reason) {
        if (warnedKeys.add(key)) {
            logger.warning("Sound \"" + key + "\" wird übersprungen: " + reason);
        }
    }

    // ==================== Validierung (für HgSettings) ====================

    /**
     * Format-Validierung eines Sound-Werts. Erlaubt eine {@code ;}/{@code ,}-Liste
     * mehrerer Sounds, je optional mit {@code @volume}/{@code ~pitch}. Jeder
     * Key-Anteil muss Bukkit-Enum-Stil oder ein parsebarer (Namespaced-)Key sein.
     * Ob ein Enum-Name existiert, prüft erst die Laufzeit — Resource-Pack-Keys
     * sind ohnehin nicht vorab prüfbar.
     */
    public static Optional<String> validateSoundKey(String value) {
        String trimmed = value == null ? "" : value.trim();
        // Leer = bewusst kein Sound (Stille) für dieses Event — erlaubt.
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        for (String token : LIST_SEPARATOR.split(trimmed)) {
            String key = MARKERS.matcher(token.trim()).replaceAll("").trim();
            if (key.isEmpty()) {
                continue;
            }
            if (!ENUM_STYLE.matcher(key).matches() && !Key.parseable(key)) {
                return Optional.of("\"" + key
                        + "\" ist weder Bukkit-Sound-Name noch gültiger Sound-Key");
            }
        }
        return Optional.empty();
    }
}
