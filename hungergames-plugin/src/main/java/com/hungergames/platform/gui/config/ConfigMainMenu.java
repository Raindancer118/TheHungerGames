package com.hungergames.platform.gui.config;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.SettingDefinition.SettingCategory;
import com.hungergames.platform.gui.AnvilInput;
import com.hungergames.platform.gui.Items;
import com.hungergames.platform.gui.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Einstieg in die Laufzeit-Konfiguration: ein Untermenü pro Kategorie statt
 * einer einzigen großen GUI.
 */
public final class ConfigMainMenu extends Menu {

    private static final Map<SettingCategory, Material> ICONS = Map.ofEntries(
            Map.entry(SettingCategory.GAME, Material.CLOCK),
            Map.entry(SettingCategory.ARENA, Material.GRASS_BLOCK),
            Map.entry(SettingCategory.LOBBY, Material.GLASS),
            Map.entry(SettingCategory.STARTUP, Material.REDSTONE_LAMP),
            Map.entry(SettingCategory.TEAMS, Material.WHITE_BANNER),
            Map.entry(SettingCategory.BORDER, Material.BARRIER),
            Map.entry(SettingCategory.PROTECTION, Material.SHIELD),
            Map.entry(SettingCategory.ITEMS, Material.SPYGLASS),
            Map.entry(SettingCategory.LOOT, Material.CHEST),
            Map.entry(SettingCategory.EVENTS, Material.FIREWORK_ROCKET),
            Map.entry(SettingCategory.SOUNDS, Material.NOTE_BLOCK),
            Map.entry(SettingCategory.EFFECTS, Material.BLAZE_ROD),
            Map.entry(SettingCategory.SPONSORS, Material.NETHER_STAR),
            Map.entry(SettingCategory.DEATHMATCH, Material.NETHERITE_SWORD),
            Map.entry(SettingCategory.GAMEMASTER, Material.COMPASS),
            Map.entry(SettingCategory.ANNOUNCEMENTS, Material.WRITABLE_BOOK),
            Map.entry(SettingCategory.API, Material.COMMAND_BLOCK),
            Map.entry(SettingCategory.RESOURCEPACK, Material.PAINTING));

    private static final Map<SettingCategory, String> LABELS = Map.ofEntries(
            Map.entry(SettingCategory.GAME, "Spiel"),
            Map.entry(SettingCategory.ARENA, "Arena"),
            Map.entry(SettingCategory.LOBBY, "Lobby"),
            Map.entry(SettingCategory.STARTUP, "Startsequenz"),
            Map.entry(SettingCategory.TEAMS, "Teams"),
            Map.entry(SettingCategory.BORDER, "Worldborder"),
            Map.entry(SettingCategory.PROTECTION, "Gebietsschutz"),
            Map.entry(SettingCategory.ITEMS, "Items"),
            Map.entry(SettingCategory.LOOT, "Loot"),
            Map.entry(SettingCategory.EVENTS, "Events & Drops"),
            Map.entry(SettingCategory.SOUNDS, "Sounds"),
            Map.entry(SettingCategory.EFFECTS, "Effekte"),
            Map.entry(SettingCategory.SPONSORS, "Sponsoren"),
            Map.entry(SettingCategory.DEATHMATCH, "Deathmatch"),
            Map.entry(SettingCategory.GAMEMASTER, "Spielmacher"),
            Map.entry(SettingCategory.ANNOUNCEMENTS, "Ansagen"),
            Map.entry(SettingCategory.API, "HTTP-API"),
            Map.entry(SettingCategory.RESOURCEPACK, "Resourcepack"));

    private final ConfigurationService config;
    private final AnvilInput anvilInput;
    private final ConfigCategoryMenu.ChangeInterceptor interceptor;

    public ConfigMainMenu(Player viewer, ConfigurationService config, AnvilInput anvilInput,
                          ConfigCategoryMenu.ChangeInterceptor interceptor) {
        super(viewer, 3, Component.text("HungerGames-Konfiguration"));
        this.config = config;
        this.anvilInput = anvilInput;
        this.interceptor = interceptor;
    }

    @Override
    protected void build() {
        int slot = 0;
        for (SettingCategory category : SettingCategory.values()) {
            int count = config.registry().byCategory(category).size();
            if (count == 0) {
                continue;
            }
            // Fallback, falls eine neue Kategorie hier noch nicht gepflegt ist —
            // so bleibt das Menü nutzbar statt komplett leer (NPE).
            Material icon = ICONS.getOrDefault(category, Material.PAPER);
            String label = LABELS.getOrDefault(category, category.name());
            set(slot++, Items.simple(icon, label, count + " Einstellungen"),
                    click -> new ConfigCategoryMenu(viewer, config, category,
                            label, anvilInput, this::open, interceptor).open());
        }
        set(26, Items.close(), click -> viewer.closeInventory());
    }
}
