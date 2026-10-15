package com.hungergames.platform.announce;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.platform.config.HgSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;

/**
 * Zentrales Ansage-System: konfigurierbare Texte mit {@code {platzhaltern}},
 * ausgespielt über Chat, Title und/oder Actionbar.
 *
 * <p>Welche Kanäle tatsächlich genutzt werden, ist der Schnitt aus den vom
 * Aufrufer gewünschten Kanälen und den global aktivierten
 * ({@code announcements.use-chat/-title/-actionbar}). Ist
 * {@code announcements.enabled} aus, passiert nichts.</p>
 */
public final class AnnouncementService {

    /** Ausspielkanal einer Ansage. */
    public enum Style {
        CHAT,
        TITLE,
        ACTIONBAR
    }

    private final ConfigurationService config;
    private final HgSettings settings;

    public AnnouncementService(ConfigurationService config, HgSettings settings) {
        this.config = config;
        this.settings = settings;
    }

    /** Ansage an alle Online-Spieler. */
    public void broadcast(SettingDefinition<String> textSetting, Map<String, String> placeholders,
                          Style... styles) {
        send(Bukkit.getOnlinePlayers(), textSetting, placeholders, styles);
    }

    public void send(Collection<? extends Player> players, SettingDefinition<String> textSetting,
                     Map<String, String> placeholders, Style... styles) {
        if (!config.get(settings.announcementsEnabled)) {
            return;
        }
        String text = format(config.get(textSetting), placeholders);
        if (text.isBlank()) {
            return; // leerer Text = Ansage bewusst abgeschaltet
        }
        Component rendered = render(text);
        for (Style style : styles) {
            if (!styleEnabled(style)) {
                continue;
            }
            for (Player player : players) {
                switch (style) {
                    case CHAT -> player.sendMessage(rendered);
                    case TITLE -> player.showTitle(Title.title(rendered, Component.empty()));
                    case ACTIONBAR -> player.sendActionBar(rendered);
                }
            }
        }
    }

    public void sendTo(Player player, SettingDefinition<String> textSetting,
                       Map<String, String> placeholders, Style... styles) {
        send(java.util.List.of(player), textSetting, placeholders, styles);
    }

    /** Rendert einen konfigurierten Text (mit §-Codes) als Component. */
    public Component render(String legacyText) {
        return LegacyComponentSerializer.legacySection().deserialize(legacyText);
    }

    private boolean styleEnabled(Style style) {
        return switch (style) {
            case CHAT -> config.get(settings.announceUseChat);
            case TITLE -> config.get(settings.announceUseTitle);
            case ACTIONBAR -> config.get(settings.announceUseActionbar);
        };
    }

    /** Ersetzt {@code {schlüssel}}-Platzhalter; unbekannte bleiben stehen. */
    public static String format(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
