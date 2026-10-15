package com.hungergames.platform.gamemaster;

import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.Perms;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.config.HgSettings.GamemasterPermissionMode;
import com.hungergames.platform.log.RoundLogService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Spielmacher-Modus (Game Master): Nicht-Teilnehmer, die das Spiel beobachten
 * und steuern. Aktivierung setzt den konfigurierten Spielmodus
 * (SPECTATOR/CREATIVE); Deaktivierung stellt den vorherigen wieder her.
 *
 * <p>Spielmacher lösen keine Eliminierungs-/Sieger-Logik aus, weil sie keine
 * Turnier-Teilnehmer sind. Der Aktiv-Zustand überlebt Server-Neustarts
 * (eigene Datei, bewusst unabhängig vom Runden-Reset).</p>
 */
public final class GamemasterService implements Listener {

    /** Gespeicherter Zustand eines aktiven Spielmachers. */
    private record ActiveState(GameMode previousMode, boolean deopped) {
    }

    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final RoundLogService roundLog;
    private final File stateFile;
    private final Logger logger;

    private final Map<UUID, ActiveState> active = new HashMap<>();

    public GamemasterService(GameSession session, ConfigurationService config, HgSettings settings,
                             RoundLogService roundLog, File stateFile, Logger logger) {
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.roundLog = roundLog;
        this.stateFile = stateFile;
        this.logger = logger;
        load();
    }

    // ==================== Erkennung ====================

    /** Ist dieser Spieler laut Konfiguration Spielmacher (unabhängig vom Aktiv-Zustand)? */
    public boolean isGamemaster(Player player) {
        if (!config.get(settings.gmEnabled)) {
            return false;
        }
        GamemasterPermissionMode mode = config.get(settings.gmPermissionMode);
        boolean byPermission = Perms.isGamemaster(player);
        boolean byList = config.get(settings.gmNames).stream()
                .anyMatch(name -> name.equalsIgnoreCase(player.getName()));
        return switch (mode) {
            case PERMISSION -> byPermission;
            case LIST -> byList;
            case BOTH -> byPermission || byList;
        };
    }

    /** Ist der Spielmacher-Modus für diesen Spieler gerade aktiv? */
    public boolean isActive(UUID uuid) {
        return active.containsKey(uuid);
    }

    public Set<UUID> activeGamemasters() {
        return Set.copyOf(active.keySet());
    }

    /** Aktive Spielmacher, die gerade online sind. */
    public List<Player> onlineActive() {
        List<Player> result = new ArrayList<>();
        for (UUID uuid : active.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    /** Online-Spieler ohne aktive Spielmacher (für Anzeigen/Preflight). */
    public long countOnlineExcludingGamemasters() {
        if (!config.get(settings.gmHideFromPlayerCount)) {
            return Bukkit.getOnlinePlayers().size();
        }
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> !isActive(player.getUniqueId()))
                .count();
    }

    // ==================== Aktivieren / Deaktivieren ====================

    /**
     * Aktiviert den Spielmacher-Modus.
     *
     * @return Fehlermeldung oder leer bei Erfolg
     */
    public java.util.Optional<String> activate(Player player) {
        if (!config.get(settings.gmEnabled)) {
            return java.util.Optional.of("Der Spielmacher-Modus ist deaktiviert (gamemaster.enabled).");
        }
        if (!isGamemaster(player)) {
            return java.util.Optional.of("Du bist nicht als Spielmacher konfiguriert.");
        }
        if (isActive(player.getUniqueId())) {
            return java.util.Optional.of("Spielmacher-Modus ist bereits aktiv.");
        }

        boolean deopped = false;
        if (player.isOp() && !config.get(settings.gmKeepOp)) {
            player.setOp(false);
            deopped = true;
        }
        active.put(player.getUniqueId(), new ActiveState(player.getGameMode(), deopped));
        applyMode(player);
        save();
        roundLog.log("GM", player.getName() + " aktiviert den Spielmacher-Modus");
        return java.util.Optional.empty();
    }

    /** @return Fehlermeldung oder leer bei Erfolg */
    public java.util.Optional<String> deactivate(Player player) {
        ActiveState state = active.remove(player.getUniqueId());
        if (state == null) {
            return java.util.Optional.of("Spielmacher-Modus ist nicht aktiv.");
        }
        player.setGameMode(state.previousMode());
        if (state.deopped()) {
            player.setOp(true);
        }
        save();
        roundLog.log("GM", player.getName() + " deaktiviert den Spielmacher-Modus");
        return java.util.Optional.empty();
    }

    /** Setzt den Spielmodus eines aktiven Spielmachers explizit. */
    public void setMode(Player player, GameMode mode) {
        if (isActive(player.getUniqueId())) {
            player.setGameMode(mode);
        }
    }

    private void applyMode(Player player) {
        GameMode target = switch (config.get(settings.gmDefaultMode)) {
            case SPECTATOR -> GameMode.SPECTATOR;
            case CREATIVE -> GameMode.CREATIVE;
        };
        player.setGameMode(target);
    }

    // ==================== Namensliste ====================

    public List<String> names() {
        return config.get(settings.gmNames);
    }

    /** @return Fehlerliste des Config-Commits (leer bei Erfolg) */
    public List<String> addName(String actor, String name) {
        List<String> current = new ArrayList<>(names());
        if (current.stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
            return List.of(name + " steht bereits auf der Spielmacher-Liste.");
        }
        current.add(name);
        List<String> errors = config.begin(actor)
                .set(settings.gmNames, List.copyOf(current), ConfigurationService.Scope.PERSISTENT)
                .commit();
        if (errors.isEmpty()) {
            roundLog.log("GM", actor + " setzt " + name + " auf die Spielmacher-Liste");
        }
        return errors;
    }

    /** @return Fehlerliste des Config-Commits (leer bei Erfolg) */
    public List<String> removeName(String actor, String name) {
        List<String> current = new ArrayList<>(names());
        if (!current.removeIf(existing -> existing.equalsIgnoreCase(name))) {
            return List.of(name + " steht nicht auf der Spielmacher-Liste.");
        }
        List<String> errors = config.begin(actor)
                .set(settings.gmNames, List.copyOf(current), ConfigurationService.Scope.PERSISTENT)
                .commit();
        if (errors.isEmpty()) {
            roundLog.log("GM", actor + " entfernt " + name + " von der Spielmacher-Liste");
        }
        return errors;
    }

    // ==================== Join-Reapply ====================

    /** Nach dem ConnectionListener: aktiven Spielmachern ihren Modus zurückgeben. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isActive(player.getUniqueId())) {
            return;
        }
        if (!config.get(settings.gmEnabled) || !isGamemaster(player)) {
            // Konfiguration hat sich geändert — Zustand sauber beenden
            deactivate(player);
            return;
        }
        applyMode(player);
    }

    // ==================== Persistenz ====================

    private void load() {
        if (!stateFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection section = yaml.getConfigurationSection("active");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                GameMode previous = GameMode.valueOf(
                        section.getString(key + ".previous-mode", "SURVIVAL")
                                .toUpperCase(Locale.ROOT));
                boolean deopped = section.getBoolean(key + ".deopped", false);
                active.put(uuid, new ActiveState(previous, deopped));
            } catch (IllegalArgumentException e) {
                logger.warning("gamemasters.yml: ungültiger Eintrag übersprungen: " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, ActiveState> entry : active.entrySet()) {
            String base = "active." + entry.getKey();
            yaml.set(base + ".previous-mode", entry.getValue().previousMode().name());
            yaml.set(base + ".deopped", entry.getValue().deopped());
        }
        try {
            yaml.save(stateFile);
        } catch (IOException e) {
            logger.warning("gamemasters.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
