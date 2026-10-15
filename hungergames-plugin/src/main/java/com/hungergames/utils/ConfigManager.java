package com.hungergames.utils;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.config.HgSettings;
import org.bukkit.Difficulty;
import org.bukkit.Material;

import java.util.List;

/**
 * Legacy-Fassade über dem typisierten {@link ConfigurationService} für die
 * verbliebenen Alt-Aufrufer (Stage-Runner, Items, Portale, Loot).
 *
 * <p>Der frühere Reload-Bug (Konsumenten hielten eine veraltete Instanz) ist
 * damit behoben: Alle Getter lesen live aus dem ConfigurationService, der
 * Overrides und pending Values kennt.</p>
 */
public final class ConfigManager {

    private static ConfigManager instance;

    private final ConfigurationService config;
    private final HgSettings settings;

    private ConfigManager(ConfigurationService config, HgSettings settings) {
        this.config = config;
        this.settings = settings;
    }

    /** Wird beim Plugin-Start verdrahtet. */
    public static void init(ConfigurationService config, HgSettings settings) {
        instance = new ConfigManager(config, settings);
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConfigManager wurde nicht initialisiert");
        }
        return instance;
    }

    /** Lädt config.yml neu (für /config-reload nach manuellen Datei-Edits). */
    public void reloadFromFile() {
        config.reloadFromBackend();
    }

    // === GAME ===

    public int getCountdown() {
        return config.get(settings.countdown);
    }

    /** @return Spieldauer in Minuten (Legacy-Einheit). */
    public int getGameDuration() {
        return (int) config.get(settings.gameDuration).toMinutes();
    }

    /** @return Schutzzeit in Sekunden (Legacy-Einheit). */
    public int getGracePeriod() {
        return (int) config.get(settings.gracePeriod).toSeconds();
    }

    /** @return Prep-Phase als Prozent der Spielzeit. */
    public int getPrepTime() {
        return config.get(settings.prepTimePercent);
    }

    public Difficulty getPreflightDifficulty() {
        try {
            return Difficulty.valueOf(config.get(settings.preflightDifficulty).toUpperCase());
        } catch (IllegalArgumentException e) {
            return Difficulty.PEACEFUL;
        }
    }

    /** @return Difficulty ab Spielstart/Release ({@code game.difficulty}). */
    public Difficulty getGameDifficulty() {
        try {
            return Difficulty.valueOf(config.get(settings.gameDifficulty).toUpperCase());
        } catch (IllegalArgumentException e) {
            return Difficulty.NORMAL;
        }
    }

    public List<String> getPreInitAdmins() {
        return config.get(settings.preInitAdmins);
    }

    // === ARENA ===

    public int getWorldborderSize() {
        return config.get(settings.borderInitialSize);
    }

    public int getPlatformMinGap() {
        return config.get(settings.platformMinGap);
    }

    public int getPlatformWidth() {
        return config.get(settings.platformWidth);
    }

    public int getUndergroundRoomHeight() {
        return config.get(settings.undergroundRoomHeight);
    }

    public int getUndergroundRoomExtraRadius() {
        return config.get(settings.undergroundRoomExtraRadius);
    }

    public int getTubeDepth() {
        return config.get(settings.tubeDepth);
    }

    // === LOBBY ===

    public int getLobbyHeightOffset() {
        return config.get(settings.lobbyHeightOffset);
    }

    public int getLobbyWidth() {
        return config.get(settings.lobbyWidth);
    }

    public int getLobbyDepth() {
        return config.get(settings.lobbyDepth);
    }

    public int getLobbyHeight() {
        return config.get(settings.lobbyHeight);
    }

    public Material getLobbyBlockType() {
        try {
            return Material.valueOf(config.get(settings.lobbyBlockType).toUpperCase());
        } catch (IllegalArgumentException e) {
            Sender.warning("Ungültiger Lobby-Blocktyp, verwende GLASS");
            return Material.GLASS;
        }
    }

    // === PORTALE ===

    public boolean shouldBlockNetherPortals() {
        return config.get(settings.blockNetherPortals);
    }

    public int getNetherAllowRadius() {
        return config.get(settings.netherAllowRadius);
    }

    public boolean shouldBlockEndPortals() {
        return config.get(settings.blockEndPortals);
    }

    // === STARTUP ===

    public int getStartupLampDelay() {
        return config.get(settings.lampDelay);
    }

    public int getStartupLevitationStartDelay() {
        return config.get(settings.levitationStartDelay);
    }

    public int getStartupPlayerLevitationDelay() {
        return config.get(settings.playerLevitationDelay);
    }

    public int getStartupLevitationAmplifier() {
        return config.get(settings.levitationAmplifier);
    }

    // === ITEMS ===

    public int getFiendfinderGlowDuration() {
        return config.get(settings.fiendfinderGlowDuration);
    }

    public int getFiendfinderSearchRadius() {
        return config.get(settings.fiendfinderSearchRadius);
    }

    // === LOOT ===

    public int getLootScanRadius() {
        return config.get(settings.lootScanRadius);
    }

    public int getLootScanYRange() {
        return config.get(settings.lootScanYRange);
    }
}
