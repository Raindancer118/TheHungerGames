package com.hungergames.platform.gui.admin;

import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feineinstellungen für einen einzelnen Sub-Sound eines Events: Lautstärke,
 * Tonhöhe, Echo, Startverzögerung (ms) und Zeitlimit (ms). Schreibt das
 * jeweilige Token ({@code KEY@vol~pitch^echo>delay<dauer}) in die Event-Liste.
 */
public final class SoundLayerMenu extends Menu {

    private static final Pattern VOLUME_PAT = Pattern.compile("@([0-9.]+)");
    private static final Pattern PITCH_PAT = Pattern.compile("~([0-9.]+)");
    private static final Pattern ECHO_PAT = Pattern.compile("\\^(\\d*)");
    private static final Pattern DELAY_PAT = Pattern.compile(">(\\d+)");
    private static final Pattern DURATION_PAT = Pattern.compile("<(\\d+)");

    private static final int MAX_ECHOES = 5;
    private static final int MAX_DELAY_MS = 10000;
    private static final int MAX_DURATION_MS = 20000;
    private static final int MS_STEP = 250;

    private final AdminServices services;
    private final SettingDefinition<String> soundDef;
    private final String key;
    private final Runnable onBack;

    public SoundLayerMenu(Player viewer, AdminServices services,
                          SettingDefinition<String> soundDef, String key, Runnable onBack) {
        super(viewer, 6, Component.text("Sound: " + shorten(key)));
        this.services = services;
        this.soundDef = soundDef;
        this.key = key;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        String token = currentToken();
        float volume = readFloat(VOLUME_PAT, token, 1.0f);
        float pitch = readFloat(PITCH_PAT, token, 1.0f);
        int echoes = readEcho(token);
        int delay = readInt(DELAY_PAT, token);
        int duration = readInt(DURATION_PAT, token);

        set(4, Items.of(Material.JUKEBOX,
                Component.text(shorten(key), NamedTextColor.GOLD),
                Component.text(key, NamedTextColor.DARK_GRAY),
                Component.empty(),
                Component.text("Token: " + (token != null ? token : key), NamedTextColor.YELLOW)));

        // Lautstärke (0.0 – 2.0)
        row(10, Material.BELL, "Lautstärke", formatFloat(volume),
                () -> rewrite(clampVol(volume - 0.25f), pitch, echoes, delay, duration),
                () -> rewrite(clampVol(volume + 0.25f), pitch, echoes, delay, duration));

        // Tonhöhe (0.5 – 2.0)
        row(19, Material.NOTE_BLOCK, "Tonhöhe", formatFloat(pitch),
                () -> rewrite(volume, clampPitch(pitch - 0.1f), echoes, delay, duration),
                () -> rewrite(volume, clampPitch(pitch + 0.1f), echoes, delay, duration));

        // Echo (0 – 5 Wiederholungen)
        row(28, Material.SCULK_SENSOR, "Echo", echoes == 0 ? "aus" : echoes + "×",
                () -> rewrite(volume, pitch, Math.max(0, echoes - 1), delay, duration),
                () -> rewrite(volume, pitch, Math.min(MAX_ECHOES, echoes + 1), delay, duration));

        // Startverzögerung (ms)
        row(37, Material.CLOCK, "Verzögerung", delay + " ms",
                () -> rewrite(volume, pitch, echoes, Math.max(0, delay - MS_STEP), duration),
                () -> rewrite(volume, pitch, echoes, Math.min(MAX_DELAY_MS, delay + MS_STEP), duration));

        // Zeitlimit (ms), 0 = voll ausspielen
        row(46, Material.REDSTONE_TORCH, "Zeitlimit", duration == 0 ? "voll" : duration + " ms",
                () -> rewrite(volume, pitch, echoes, delay, Math.max(0, duration - MS_STEP)),
                () -> rewrite(volume, pitch, echoes, delay, Math.min(MAX_DURATION_MS, duration + MS_STEP)));

        set(25, Items.of(Material.NOTE_BLOCK,
                Component.text("▶ Diesen Sound testen", NamedTextColor.GREEN)),
                click -> services.sounds.test(viewer, token != null ? token : key));

        set(26, Items.of(Material.BARRIER,
                Component.text("⏹ Sounds stoppen", NamedTextColor.RED)),
                click -> viewer.stopSound(net.kyori.adventure.sound.SoundStop.all()));

        set(34, Items.of(Material.LAVA_BUCKET,
                Component.text("Auf Standard zurück", NamedTextColor.RED),
                Component.text("Nur der reine Sound (Lautstärke 1, kein Echo/Delay)",
                        NamedTextColor.GRAY)),
                click -> rewrite(1.0f, 1.0f, 0, 0, 0));

        set(53, Items.back(), click -> onBack.run());
    }

    private void row(int slot, Material icon, String label, String value, Runnable minus, Runnable plus) {
        set(slot, Items.of(Material.RED_STAINED_GLASS_PANE,
                Component.text("− " + label, NamedTextColor.RED)), click -> {
            minus.run();
            refresh();
        });
        set(slot + 1, Items.of(icon,
                Component.text(label + ": " + value, NamedTextColor.YELLOW)));
        set(slot + 2, Items.of(Material.LIME_STAINED_GLASS_PANE,
                Component.text("+ " + label, NamedTextColor.GREEN)), click -> {
            plus.run();
            refresh();
        });
    }

    private void rewrite(float volume, float pitch, int echoes, int delay, int duration) {
        String newToken = buildToken(volume, pitch, echoes, delay, duration);
        List<String> tokens = currentTokens();
        boolean replaced = false;
        for (int i = 0; i < tokens.size(); i++) {
            if (bareKey(tokens.get(i)).equals(key)) {
                tokens.set(i, newToken);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            tokens.add(newToken);
        }
        save(tokens);
    }

    private String buildToken(float volume, float pitch, int echoes, int delay, int duration) {
        StringBuilder sb = new StringBuilder(key);
        if (Math.abs(volume - 1.0f) > 0.001f) {
            sb.append('@').append(formatFloat(volume));
        }
        if (Math.abs(pitch - 1.0f) > 0.001f) {
            sb.append('~').append(formatFloat(pitch));
        }
        if (echoes > 0) {
            sb.append('^').append(echoes);
        }
        if (delay > 0) {
            sb.append('>').append(delay);
        }
        if (duration > 0) {
            sb.append('<').append(duration);
        }
        return sb.toString();
    }

    private void save(List<String> tokens) {
        List<String> errors = services.config.begin(viewer.getName())
                .setFromInput(soundDef, String.join("; ", tokens), Scope.PERSISTENT).commit();
        errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
    }

    // ==================== Lesen ====================

    private String currentToken() {
        for (String token : currentTokens()) {
            if (bareKey(token).equals(key)) {
                return token;
            }
        }
        return null;
    }

    private List<String> currentTokens() {
        String raw = services.config.get(soundDef);
        List<String> tokens = new ArrayList<>();
        for (String token : raw.split("[;,]")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private static float readFloat(Pattern pattern, String token, float fallback) {
        if (token == null) {
            return fallback;
        }
        Matcher m = pattern.matcher(token);
        if (m.find()) {
            try {
                return Float.parseFloat(m.group(1));
            } catch (NumberFormatException ignored) {
                // Default
            }
        }
        return fallback;
    }

    private static int readInt(Pattern pattern, String token) {
        if (token == null) {
            return 0;
        }
        Matcher m = pattern.matcher(token);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                // 0
            }
        }
        return 0;
    }

    private static int readEcho(String token) {
        if (token == null) {
            return 0;
        }
        Matcher m = ECHO_PAT.matcher(token);
        if (m.find()) {
            return m.group(1).isEmpty() ? 2 : Integer.parseInt(m.group(1));
        }
        return 0;
    }

    private static float clampVol(float v) {
        return Math.max(0f, Math.min(2.0f, Math.round(v * 100) / 100f));
    }

    private static float clampPitch(float v) {
        return Math.max(0.5f, Math.min(2.0f, Math.round(v * 100) / 100f));
    }

    private static String formatFloat(float v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String bareKey(String token) {
        return token.trim().replaceAll("[@~^><][0-9.]*", "").trim();
    }

    private static String shorten(String key) {
        return key.length() > 24 ? key.substring(key.length() - 24) : key;
    }
}
