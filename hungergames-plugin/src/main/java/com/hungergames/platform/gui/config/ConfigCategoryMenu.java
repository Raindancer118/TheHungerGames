package com.hungergames.platform.gui.config;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.ConfigurationService.Scope;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.game.config.SettingDefinition.SettingCategory;
import com.hungergames.platform.Messages;
import com.hungergames.platform.gui.AnvilInput;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import com.hungergames.platform.gui.NumberEditorMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Untermenü einer Einstellungs-Kategorie. Pro Setting ein Item mit
 * Beschreibung, aktuellem Wert, Default und Anwendungszeitpunkt in der Lore.
 *
 * <p>Bedienung: Linksklick ändert dauerhaft, Rechtsklick nur für die aktuelle
 * Runde (Override). Booleans togglen, Enums rotieren, Zahlen öffnen den
 * Klickschritt-Editor, Texte/Dauern die Amboss-Eingabe.</p>
 */
public final class ConfigCategoryMenu extends Menu {

    /**
     * Hook, der eine Änderung VOR dem Commit abfangen kann (z. B. der
     * Border-Draft-Guard, der erst eine Konflikt-GUI öffnet).
     */
    public interface ChangeInterceptor {

        /**
         * @param reopen öffnet dieses Menü wieder (nach Verwerfen/Abschluss)
         * @return {@code true}, wenn die Änderung abgefangen wurde — der
         *         Aufrufer darf dann NICHT mehr committen
         */
        <T> boolean intercept(Player viewer, SettingDefinition<T> def, T value,
                              Scope scope, Runnable reopen);
    }

    private final ConfigurationService config;
    private final SettingCategory category;
    private final AnvilInput anvilInput;
    private final Runnable onBack;
    private final ChangeInterceptor interceptor;

    public ConfigCategoryMenu(Player viewer, ConfigurationService config, SettingCategory category,
                              String label, AnvilInput anvilInput, Runnable onBack,
                              ChangeInterceptor interceptor) {
        super(viewer, 6, Component.text("Konfiguration: " + label));
        this.config = config;
        this.category = category;
        this.anvilInput = anvilInput;
        this.onBack = onBack;
        this.interceptor = interceptor;
    }

    @Override
    protected void build() {
        int slot = 0;
        for (SettingDefinition<?> definition : config.registry().byCategory(category)) {
            if (slot >= 45) {
                break;
            }
            addSetting(slot++, definition);
        }
        set(53, Items.back(), click -> onBack.run());
    }

    private <T> void addSetting(int slot, SettingDefinition<T> def) {
        T value = config.get(def);
        String serialized = def.type().serialize(value);
        boolean overridden = config.hasRoundOverride(def.key());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(def.description(), NamedTextColor.GRAY));
        lore.add(Component.text("Wert: " + serialized
                + (overridden ? " (nur diese Runde)" : ""), NamedTextColor.YELLOW));
        lore.add(Component.text("Default: " + def.type().serialize(def.defaultValue()),
                NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Wirksam: " + switch (def.applyTiming()) {
            case IMMEDIATE -> "sofort";
            case NEXT_PHASE -> "nächste Phase";
            case NEXT_ROUND -> "nächste Runde";
            case RESTART -> "nach Neustart";
        }, NamedTextColor.DARK_GRAY));
        config.pendingValue(def).ifPresent(pending ->
                lore.add(Component.text("Vorgemerkt: " + pending, NamedTextColor.LIGHT_PURPLE)));
        lore.add(Component.empty());
        lore.add(Component.text("Linksklick: dauerhaft ändern", NamedTextColor.AQUA));
        lore.add(Component.text("Rechtsklick: nur diese Runde", NamedTextColor.AQUA));

        set(slot, Items.of(iconFor(def, value), Component.text(def.key(), NamedTextColor.WHITE), lore),
                click -> edit(def, click));
    }

    private <T> void edit(SettingDefinition<T> def, InventoryClickEvent click) {
        Scope scope = click.isRightClick() ? Scope.ROUND : Scope.PERSISTENT;
        Class<T> type = def.type().valueClass();

        if (type == Boolean.class) {
            @SuppressWarnings("unchecked")
            T toggled = (T) Boolean.valueOf(!(Boolean) config.get(def));
            if (commit(def, toggled, scope)) {
                refresh();
            }
        } else if (type.isEnum()) {
            if (commit(def, nextEnumValue(def), scope)) {
                refresh();
            }
        } else if (type == Integer.class || type == Double.class) {
            boolean integer = type == Integer.class;
            double current = ((Number) config.get(def)).doubleValue();
            new NumberEditorMenu(viewer, def.key(), current, integer,
                    draft -> validateNumber(def, draft, integer),
                    applied -> {
                        @SuppressWarnings("unchecked")
                        T typed = (T) (integer ? (Object) (int) Math.round(applied) : (Object) applied);
                        // Nur zurück ins Kategorienmenü, wenn NICHT der
                        // Interceptor übernommen hat — sonst würde dessen
                        // Konflikt-GUI sofort wieder überdeckt.
                        if (commit(def, typed, scope)) {
                            open();
                        }
                    },
                    this::open, anvilInput).open();
        } else {
            // Duration, String, Listen: Amboss-Eingabe mit Parse-Validierung
            String current = def.type().serialize(config.get(def));
            anvilInput.open(viewer, Component.text(def.key()), current,
                    input -> {
                        boolean reopen = true;
                        try {
                            reopen = commit(def, def.type().parse(input.trim()), scope);
                        } catch (IllegalArgumentException e) {
                            viewer.sendMessage(Messages.error(def.key() + ": Eingabe \"" + input
                                    + "\" ist kein gültiger Wert (" + e.getMessage() + ")"));
                        }
                        if (reopen) {
                            open();
                        }
                    },
                    this::open);
        }
    }

    /**
     * Committet eine Änderung, sofern kein Interceptor sie übernimmt.
     *
     * @return {@code false}, wenn der Interceptor die Änderung abgefangen und
     *         eine eigene GUI geöffnet hat — der Aufrufer darf dann kein
     *         Menü mehr öffnen
     */
    private <T> boolean commit(SettingDefinition<T> def, T value, Scope scope) {
        if (interceptor != null && interceptor.intercept(viewer, def, value, scope, this::open)) {
            return false;
        }
        List<String> errors = config.begin(viewer.getName()).set(def, value, scope).commit();
        if (errors.isEmpty()) {
            String note = switch (def.applyTiming()) {
                case IMMEDIATE -> "";
                case NEXT_PHASE -> " (wirksam ab nächster Phase)";
                case NEXT_ROUND -> " (wirksam ab nächster Runde)";
                case RESTART -> " (wirksam nach Neustart)";
            };
            viewer.sendMessage(Messages.ok(def.key() + " = " + def.type().serialize(value)
                    + (scope == Scope.ROUND ? " (nur diese Runde)" : "") + note));
        } else {
            errors.forEach(error -> viewer.sendMessage(Messages.error(error)));
        }
        return true;
    }

    private <T> String validateNumber(SettingDefinition<T> def, double draft, boolean integer) {
        @SuppressWarnings("unchecked")
        T typed = (T) (integer ? (Object) (int) Math.round(draft) : (Object) draft);
        return def.validator().validate(typed).orElse(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T nextEnumValue(SettingDefinition<T> def) {
        Enum<?> current = (Enum<?>) config.get(def);
        Object[] constants = def.type().valueClass().getEnumConstants();
        return (T) constants[(current.ordinal() + 1) % constants.length];
    }

    private org.bukkit.Material iconFor(SettingDefinition<?> def, Object value) {
        Class<?> type = def.type().valueClass();
        if (type == Boolean.class) {
            return Boolean.TRUE.equals(value) ? Material.LIME_DYE : Material.GRAY_DYE;
        }
        if (type == Integer.class || type == Double.class) {
            return Material.REPEATER;
        }
        if (type == Duration.class) {
            return Material.CLOCK;
        }
        if (type.isEnum()) {
            return Material.COMPARATOR;
        }
        return Material.PAPER;
    }
}
