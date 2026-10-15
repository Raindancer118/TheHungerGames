package com.hungergames;

import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Arena-Geometrie der aktuellen Runde (Zentren, Plattform- und
 * Spawn-Positionen). Die Spielphase liegt in der {@link GameSession} — die
 * Phase-Methoden hier delegieren nur noch und existieren für die
 * Legacy-Runner; neuer Code nutzt die Session direkt.
 */
public class GameState {

    private static GameState instance;
    private static GameSession session;

    private Location arenaCenter;
    private Location lobbyCenter;
    private Location undergroundRoomCenter;
    private int undergroundFloorY;
    private int playerCount;
    private List<Location> platformPositions = new ArrayList<>();
    private List<Location> undergroundSpawnPositions = new ArrayList<>();

    private GameState() {
    }

    public static GameState getInstance() {
        if (instance == null) {
            instance = new GameState();
        }
        return instance;
    }

    /** Wird beim Plugin-Start von der Hauptklasse gesetzt. */
    public static void bindSession(GameSession gameSession) {
        session = gameSession;
    }

    // === Phase (delegiert an GameSession) ===

    public GamePhase getCurrentPhase() {
        return session != null ? session.phase() : GamePhase.NOT_INITIALIZED;
    }

    /**
     * Wechselt die Phase über die Session (validierte Übergänge).
     * Unzulässige Übergänge werden geloggt und ignoriert.
     */
    public void setCurrentPhase(GamePhase phase) {
        tryTransition(phase);
    }

    /**
     * Wie {@link #setCurrentPhase}, meldet aber, ob der Übergang gelang —
     * Aufrufer müssen bei {@code false} abbrechen statt weiterzulaufen.
     */
    public boolean tryTransition(GamePhase phase) {
        if (session == null) {
            return false;
        }
        if (!session.transitionTo(phase)) {
            com.hungergames.utils.Sender.warning(
                    "Unzulässiger Phasenwechsel " + session.phase() + " -> " + phase + " ignoriert");
            return false;
        }
        return true;
    }

    public boolean isInitialized() {
        return getCurrentPhase() != GamePhase.NOT_INITIALIZED;
    }

    /** @return Zahl aller registrierten Tribute (inkl. offline/Mannequin-Attrappen). */
    public int registeredTributeCount() {
        return session != null ? session.participants().all().size() : 0;
    }

    // === Arena-Geometrie ===

    public Location getArenaCenter() {
        return arenaCenter;
    }

    public void setArenaCenter(Location arenaCenter) {
        this.arenaCenter = arenaCenter;
    }

    public Location getLobbyCenter() {
        return lobbyCenter;
    }

    public void setLobbyCenter(Location lobbyCenter) {
        this.lobbyCenter = lobbyCenter;
    }

    public Location getUndergroundRoomCenter() {
        return undergroundRoomCenter;
    }

    public void setUndergroundRoomCenter(Location undergroundRoomCenter) {
        this.undergroundRoomCenter = undergroundRoomCenter;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }

    public List<Location> getPlatformPositions() {
        return new ArrayList<>(platformPositions);
    }

    public void setPlatformPositions(List<Location> positions) {
        this.platformPositions = new ArrayList<>(positions);
    }

    public int getUndergroundFloorY() {
        return undergroundFloorY;
    }

    public void setUndergroundFloorY(int floorY) {
        this.undergroundFloorY = floorY;
    }

    public List<Location> getUndergroundSpawnPositions() {
        return new ArrayList<>(undergroundSpawnPositions);
    }

    public void setUndergroundSpawnPositions(List<Location> positions) {
        this.undergroundSpawnPositions = new ArrayList<>(positions);
    }

    /** Setzt Geometrie und (über die Session) die Runde zurück. */
    public void reset() {
        this.arenaCenter = null;
        this.lobbyCenter = null;
        this.undergroundRoomCenter = null;
        this.undergroundFloorY = 0;
        this.playerCount = 0;
        this.platformPositions.clear();
        this.undergroundSpawnPositions.clear();
        if (session != null) {
            session.resetForNextRound();
        }
    }
}
