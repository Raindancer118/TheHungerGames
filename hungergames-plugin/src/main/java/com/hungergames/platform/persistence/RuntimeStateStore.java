package com.hungergames.platform.persistence;

import com.hungergames.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Persistiert den Laufzeit-Zustand, der nicht in der Bukkit-freien Session
 * liegt: die Arena-Geometrie (Welt, Zentren, Plattform- und Spawn-Positionen)
 * sowie den Spielfortschritt (virtuelle Spielzeit, nächste Borderphase).
 *
 * <p>Ohne diese Daten wäre ein Session-Restore unvollständig: Border und
 * Gebietsschutz brauchen das Arena-Zentrum, die Border-Engine ihren
 * Phasenindex und der Timer die bereits verstrichene virtuelle Zeit
 * (Server-Downtime zählt je nach Policy nicht als Spielzeit).</p>
 */
public final class RuntimeStateStore {

    /** Wiederhergestellter Spielfortschritt. */
    public record RuntimeData(long virtualElapsedMs, int nextBorderPhaseIndex) {
    }

    private final File file;
    private final Logger logger;

    public RuntimeStateStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    // ==================== Arena-Geometrie ====================

    /** Speichert die aktuelle Arena-Geometrie aus dem {@link GameState}. */
    public void saveArena(GameState state) {
        Location center = state.getArenaCenter();
        if (center == null || center.getWorld() == null) {
            return; // noch keine Arena initialisiert
        }
        YamlConfiguration yaml = loadFile();
        yaml.set("arena.world", center.getWorld().getName());
        setLocation(yaml, "arena.center", center);
        setLocation(yaml, "arena.lobby-center", state.getLobbyCenter());
        setLocation(yaml, "arena.underground-center", state.getUndergroundRoomCenter());
        yaml.set("arena.underground-floor-y", state.getUndergroundFloorY());
        yaml.set("arena.player-count", state.getPlayerCount());
        yaml.set("arena.platforms", serializeLocations(state.getPlatformPositions()));
        yaml.set("arena.underground-spawns", serializeLocations(state.getUndergroundSpawnPositions()));
        save(yaml);
    }

    /** Stellt die Arena-Geometrie in den {@link GameState} wieder her. */
    public boolean restoreArena(GameState state) {
        if (!file.exists()) {
            return false;
        }
        YamlConfiguration yaml = loadFile();
        String worldName = yaml.getString("arena.world");
        if (worldName == null) {
            return false;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.warning("runtime.yml: Arena-Welt '" + worldName + "' ist nicht geladen — "
                    + "Geometrie kann nicht wiederhergestellt werden.");
            return false;
        }
        Location center = getLocation(yaml, "arena.center", world);
        if (center == null) {
            return false;
        }
        state.setArenaCenter(center);
        state.setLobbyCenter(getLocation(yaml, "arena.lobby-center", world));
        state.setUndergroundRoomCenter(getLocation(yaml, "arena.underground-center", world));
        state.setUndergroundFloorY(yaml.getInt("arena.underground-floor-y", 0));
        state.setPlayerCount(yaml.getInt("arena.player-count", 0));
        state.setPlatformPositions(deserializeLocations(yaml.getStringList("arena.platforms"), world));
        state.setUndergroundSpawnPositions(
                deserializeLocations(yaml.getStringList("arena.underground-spawns"), world));
        return true;
    }

    // ==================== Spielfortschritt ====================

    /** Speichert virtuelle Spielzeit und Border-Phasenindex (periodisch im Tick). */
    public void saveRuntime(long virtualElapsedMs, int nextBorderPhaseIndex) {
        YamlConfiguration yaml = loadFile();
        yaml.set("runtime.virtual-elapsed-ms", virtualElapsedMs);
        yaml.set("runtime.next-border-phase", nextBorderPhaseIndex);
        save(yaml);
    }

    public Optional<RuntimeData> loadRuntime() {
        if (!file.exists()) {
            return Optional.empty();
        }
        YamlConfiguration yaml = loadFile();
        if (!yaml.contains("runtime.virtual-elapsed-ms")) {
            return Optional.empty();
        }
        // Gegen manipulierte/beschädigte Werte absichern
        return Optional.of(new RuntimeData(
                Math.max(0, yaml.getLong("runtime.virtual-elapsed-ms", 0)),
                Math.max(0, yaml.getInt("runtime.next-border-phase", 0))));
    }

    // ==================== Supply Drops ====================

    /**
     * Persistiert ausgelöste Drop-Indizes und noch nicht gelandete Drops —
     * ein Neustart löst so weder doppelt aus noch verschluckt eine Lieferung.
     */
    public void saveSupplyDropState(java.util.Set<Integer> triggeredIndices,
                                    List<String> pendingLandings) {
        YamlConfiguration yaml = loadFile();
        yaml.set("runtime.supply-drops.triggered",
                triggeredIndices.stream().sorted().toList());
        yaml.set("runtime.supply-drops.pending",
                pendingLandings.isEmpty() ? null : pendingLandings);
        save(yaml);
    }

    public java.util.Set<Integer> loadSupplyDropTriggered() {
        if (!file.exists()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(loadFile().getIntegerList("runtime.supply-drops.triggered"));
    }

    public List<String> loadSupplyDropPending() {
        if (!file.exists()) {
            return List.of();
        }
        return loadFile().getStringList("runtime.supply-drops.pending");
    }

    // ==================== Sponsor-System ====================

    /** Persistiert erhaltene Token-Wellen und verdiente Tokens pro Spieler. */
    public void saveSponsorTokenState(java.util.Map<java.util.UUID, int[]> perPlayer) {
        YamlConfiguration yaml = loadFile();
        yaml.set("runtime.sponsor-tokens", null);
        for (var entry : perPlayer.entrySet()) {
            yaml.set("runtime.sponsor-tokens." + entry.getKey(),
                    entry.getValue()[0] + "," + entry.getValue()[1]);
        }
        save(yaml);
    }

    /** @return uuid → {erhaltene Wellen, verdiente Tokens} */
    public java.util.Map<java.util.UUID, int[]> loadSponsorTokenState() {
        java.util.Map<java.util.UUID, int[]> result = new java.util.HashMap<>();
        if (!file.exists()) {
            return result;
        }
        var section = loadFile().getConfigurationSection("runtime.sponsor-tokens");
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            try {
                String[] parts = section.getString(key, "0,0").split(",");
                result.put(java.util.UUID.fromString(key), new int[]{
                        Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())});
            } catch (RuntimeException e) {
                logger.warning("runtime.yml: ungültiger Sponsor-Token-Eintrag übersprungen: " + key);
            }
        }
        return result;
    }

    /** Persistiert aktive Beacon-Positionen und ausgelöste Spawn-Zeitpunkte. */
    public void saveSponsorBeacons(List<String> locations, java.util.Set<Integer> triggeredSpawns) {
        YamlConfiguration yaml = loadFile();
        yaml.set("runtime.sponsor-beacons.locations", locations.isEmpty() ? null : locations);
        yaml.set("runtime.sponsor-beacons.triggered", triggeredSpawns.stream().sorted().toList());
        save(yaml);
    }

    public List<String> loadSponsorBeaconLocations() {
        return file.exists()
                ? loadFile().getStringList("runtime.sponsor-beacons.locations") : List.of();
    }

    public java.util.Set<Integer> loadSponsorBeaconTriggered() {
        return file.exists()
                ? new java.util.HashSet<>(loadFile().getIntegerList("runtime.sponsor-beacons.triggered"))
                : java.util.Set.of();
    }

    // ==================== Deathmatch ====================

    /** Merkt sich ein aktives Deathmatch, damit ein Neustart es nicht „vergisst". */
    public void saveDeathmatchActive(boolean active) {
        YamlConfiguration yaml = loadFile();
        yaml.set("runtime.deathmatch-active", active ? true : null);
        save(yaml);
    }

    public boolean loadDeathmatchActive() {
        return file.exists() && loadFile().getBoolean("runtime.deathmatch-active", false);
    }

    // ==================== OP-Snapshot (mitspielende Admins) ====================

    /**
     * Speichert, welche Tribute beim Rundenstart OP waren und deoped wurden —
     * damit ein Neustart während der Runde den OP-Status nicht verliert.
     */
    public void saveOpSnapshot(java.util.Set<java.util.UUID> deoppedAdmins) {
        YamlConfiguration yaml = loadFile();
        yaml.set("runtime.deopped-admins", deoppedAdmins.stream()
                .map(java.util.UUID::toString).sorted().toList());
        save(yaml);
    }

    public java.util.Set<java.util.UUID> loadOpSnapshot() {
        if (!file.exists()) {
            return java.util.Set.of();
        }
        java.util.Set<java.util.UUID> result = new java.util.HashSet<>();
        for (String raw : loadFile().getStringList("runtime.deopped-admins")) {
            try {
                result.add(java.util.UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                logger.warning("runtime.yml: ungültige UUID im OP-Snapshot übersprungen: " + raw);
            }
        }
        return result;
    }

    /** Entfernt den kompletten Laufzeit-Zustand (Rundenende/Reset). */
    public void clear() {
        if (file.exists() && !file.delete()) {
            logger.warning("runtime.yml konnte nicht gelöscht werden");
        }
    }

    // ==================== intern ====================

    private YamlConfiguration loadFile() {
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private void save(YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.severe("runtime.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private static void setLocation(YamlConfiguration yaml, String path, Location location) {
        if (location == null) {
            yaml.set(path, null);
            return;
        }
        yaml.set(path, location.getX() + "," + location.getY() + "," + location.getZ());
    }

    private Location getLocation(YamlConfiguration yaml, String path, World world) {
        String raw = yaml.getString(path);
        if (raw == null) {
            return null;
        }
        Location parsed = parseLocation(raw, world);
        if (parsed == null) {
            logger.warning("runtime.yml: beschädigte Koordinate unter '" + path + "': " + raw);
        }
        return parsed;
    }

    private static List<String> serializeLocations(List<Location> locations) {
        List<String> result = new ArrayList<>();
        for (Location location : locations) {
            result.add(location.getX() + "," + location.getY() + "," + location.getZ());
        }
        return result;
    }

    private List<Location> deserializeLocations(List<String> raw, World world) {
        List<Location> result = new ArrayList<>();
        for (String entry : raw) {
            Location parsed = parseLocation(entry, world);
            if (parsed != null) {
                result.add(parsed);
            } else {
                logger.warning("runtime.yml: beschädigter Positionseintrag übersprungen: " + entry);
            }
        }
        return result;
    }

    /** @return {@code null} bei nicht parsebaren oder unendlichen Koordinaten. */
    private static Location parseLocation(String raw, World world) {
        try {
            String[] parts = raw.split(",");
            if (parts.length != 3) {
                return null;
            }
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return null;
            }
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
