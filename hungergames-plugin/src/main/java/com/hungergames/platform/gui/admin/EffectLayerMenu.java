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
 * Feineinstellungen für eine einzelne Partikel-Ebene eines Effekts: Anzahl,
 * Streuung, Tempo, Verzögerung, Wiederholungen und (bei DUST) Farbe.
 */
public final class EffectLayerMenu extends Menu {

    private static final Pattern COUNT_PAT = Pattern.compile("@([0-9]+)");
    private static final Pattern SPREAD_PAT = Pattern.compile("~([0-9.]+)");
    private static final Pattern SPEED_PAT = Pattern.compile("\\*([0-9.]+)");
    private static final Pattern DELAY_PAT = Pattern.compile(">([0-9]+)");
    private static final Pattern REPEAT_PAT = Pattern.compile("\\^([0-9]+)");
    private static final Pattern COLOR_PAT = Pattern.compile("#([0-9A-Fa-f]{6})");

    /** Farbpaletten-Zyklus für DUST. */
    private static final String[] COLORS = {
            "ff0000", "ff8800", "ffee00", "00ff00", "00ffcc",
            "00aaff", "0000ff", "aa00ff", "ff00ff", "ffffff", "000000"};

    private final AdminServices services;
    private final SettingDefinition<String> effectDef;
    private final String name;
    private final Runnable onBack;

    public EffectLayerMenu(Player viewer, AdminServices services,
                           SettingDefinition<String> effectDef, String name, Runnable onBack) {
        super(viewer, 6, Component.text("Effekt: " + name));
        this.services = services;
        this.effectDef = effectDef;
        this.name = name;
        this.onBack = onBack;
    }

    @Override
    protected void build() {
        String token = currentToken();
        int count = readInt(COUNT_PAT, token, 20);
        double spread = readDouble(SPREAD_PAT, token, 0.4);
        double speed = readDouble(SPEED_PAT, token, 0.0);
        int delay = readInt(DELAY_PAT, token, 0);
        int repeat = readInt(REPEAT_PAT, token, 0);
        String color = readColor(token);
        boolean isDust = "DUST".equals(name);

        set(4, Items.of(Material.BLAZE_ROD,
                Component.text(name, NamedTextColor.GOLD),
                Component.empty(),
                Component.text("Token: " + (token != null ? token : name), NamedTextColor.YELLOW)));

        row(10, Material.GUNPOWDER, "Anzahl", String.valueOf(count),
                () -> rewrite(Math.max(1, count - 5), spread, speed, delay, repeat, color),
                () -> rewrite(Math.min(500, count + 5), spread, speed, delay, repeat, color));

        row(19, Material.SNOWBALL, "Streuung", fmt(spread),
                () -> rewrite(count, Math.max(0, round(spread - 0.1)), speed, delay, repeat, color),
                () -> rewrite(count, round(spread + 0.1), speed, delay, repeat, color));

        row(28, Material.FEATHER, "Tempo", fmt(speed),
                () -> rewrite(count, spread, Math.max(0, round(speed - 0.05)), delay, repeat, color),
                () -> rewrite(count, spread, round(speed + 0.05), delay, repeat, color));

        row(37, Material.CLOCK, "Verzögerung", delay + " ms",
                () -> rewrite(count, spread, speed, Math.max(0, delay - 250), repeat, color),
                () -> rewrite(count, spread, speed, Math.min(10000, delay + 250), repeat, color));

        row(46, Material.REPEATER, "Wiederholungen", repeat == 0 ? "keine" : repeat + "×",
                () -> rewrite(count, spread, speed, delay, Math.max(0, repeat - 1), color),
                () -> rewrite(count, spread, speed, delay, Math.min(10, repeat + 1), color));

        if (isDust) {
            set(16, Items.of(Material.RED_DYE,
                    Component.text("Farbe: #" + (color != null ? color : "ff0000"), NamedTextColor.LIGHT_PURPLE),
                    Component.text("Klick: nächste Farbe", NamedTextColor.GRAY),
                    Component.text("(nur DUST hat Farbe)", NamedTextColor.DARK_GRAY)),
                    click -> rewrite(count, spread, speed, delay, repeat, nextColor(color)));
        }

        set(25, Items.of(Material.FIREWORK_ROCKET,
                Component.text("▶ Diesen Effekt ansehen", NamedTextColor.GREEN)),
                click -> services.effects.test(viewer, token != null ? token : name));

        set(34, Items.of(Material.LAVA_BUCKET,
                Component.text("Auf Standard zurück", NamedTextColor.RED)),
                click -> rewrite(20, 0.4, 0.0, 0, 0, null));

        set(53, Items.back(), click -> onBack.run());
    }

    private void row(int slot, Material icon, String label, String value, Runnable minus, Runnable plus) {
        set(slot, Items.of(Material.RED_STAINED_GLASS_PANE,
                Component.text("− " + label, NamedTextColor.RED)), click -> {
            minus.run();
            refresh();
        });
        set(slot + 1, Items.of(icon, Component.text(label + ": " + value, NamedTextColor.YELLOW)));
        set(slot + 2, Items.of(Material.LIME_STAINED_GLASS_PANE,
                Component.text("+ " + label, NamedTextColor.GREEN)), click -> {
            plus.run();
            refresh();
        });
    }

    private void rewrite(int count, double spread, double speed, int delay, int repeat, String color) {
        String newToken = buildToken(count, spread, speed, delay, repeat, color);
        List<String> tokens = currentTokens();
        boolean replaced = false;
        for (int i = 0; i < tokens.size(); i++) {
            if (bareName(tokens.get(i)).equals(name)) {
                tokens.set(i, newToken);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            tokens.add(newToken);
        }
        List<String> errors = services.config.begin(viewer.getName())
                .setFromInput(effectDef, String.join("; ", tokens), Scope.PERSISTENT).commit();
        errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
        refresh();
    }

    private String buildToken(int count, double spread, double speed, int delay, int repeat, String color) {
        StringBuilder sb = new StringBuilder(name);
        if (count != 20) {
            sb.append('@').append(count);
        }
        if (Math.abs(spread - 0.4) > 0.001) {
            sb.append('~').append(fmt(spread));
        }
        if (speed > 0.001) {
            sb.append('*').append(fmt(speed));
        }
        if (delay > 0) {
            sb.append('>').append(delay);
        }
        if (repeat > 0) {
            sb.append('^').append(repeat);
        }
        if (color != null && "DUST".equals(name)) {
            sb.append('#').append(color);
        }
        return sb.toString();
    }

    // ==================== Lesen ====================

    private String currentToken() {
        for (String token : currentTokens()) {
            if (bareName(token).equals(name)) {
                return token;
            }
        }
        return null;
    }

    private List<String> currentTokens() {
        List<String> tokens = new ArrayList<>();
        for (String token : services.config.get(effectDef).split("[;,]")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private static int readInt(Pattern pattern, String token, int fallback) {
        if (token == null) {
            return fallback;
        }
        Matcher m = pattern.matcher(token);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                // Default
            }
        }
        return fallback;
    }

    private static double readDouble(Pattern pattern, String token, double fallback) {
        if (token == null) {
            return fallback;
        }
        Matcher m = pattern.matcher(token);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {
                // Default
            }
        }
        return fallback;
    }

    private static String readColor(String token) {
        if (token == null) {
            return null;
        }
        Matcher m = COLOR_PAT.matcher(token);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private static String nextColor(String current) {
        if (current == null) {
            return COLORS[0];
        }
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equalsIgnoreCase(current)) {
                return COLORS[(i + 1) % COLORS.length];
            }
        }
        return COLORS[0];
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String fmt(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String bareName(String token) {
        return token.trim().replaceAll("[@~*>^][0-9.]*|#[0-9A-Fa-f]{6}", "").trim();
    }
}
