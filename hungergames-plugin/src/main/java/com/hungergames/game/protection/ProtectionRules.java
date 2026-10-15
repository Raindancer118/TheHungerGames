package com.hungergames.game.protection;

import com.hungergames.api.model.GamePhase;

import java.util.Set;

/**
 * Phasen- und regionsabhängige Schutzregeln — reine Entscheidungslogik ohne
 * Bukkit. Der Plattform-Listener übersetzt Events in {@link Query}s.
 *
 * <p>Kernanforderung: Die Cornucopia-/Spawnregion ist während RUNNING für
 * normale Spieler vollständig frei (Kisten öffnen, Blöcke abbauen und
 * platzieren); vor dem Spiel ist sie geschützt. Beides ist konfigurierbar.</p>
 */
public final class ProtectionRules {

    /** Benannte Schutzregionen. */
    public enum Region {
        /** Kreis um das Arena-Zentrum (Radius konfigurierbar). */
        CORNUCOPIA,
        /** Rest der Arena. */
        ARENA
    }

    /** Geprüfte Aktionsarten. */
    public enum ActionType {
        BREAK,
        PLACE,
        INTERACT,
        CONTAINER
    }

    /**
     * Eine Schutz-Anfrage.
     *
     * @param region     Region, in der die Aktion stattfindet
     * @param action     Art der Aktion
     * @param phase      aktuelle Spielphase
     * @param hasBypass  hat der Spieler die Bypass-Permission?
     * @param material   Material-Name des Blocks/Items (für Ausnahmeliste)
     */
    public record Query(Region region, ActionType action, GamePhase phase,
                        boolean hasBypass, String material) {
    }

    /**
     * Konfiguration der Regelmatrix.
     *
     * @param protectCornucopiaPreGame  Cornucopia vor RUNNING schützen
     * @param protectCornucopiaRunning  Cornucopia während RUNNING schützen (Default: nein)
     * @param protectCornucopiaFinished Cornucopia nach Spielende schützen
     * @param allowedMaterials          trotz Schutz benutzbare Materialien (z. B. CHEST)
     */
    public record Config(
            boolean protectCornucopiaPreGame,
            boolean protectCornucopiaRunning,
            boolean protectCornucopiaFinished,
            Set<String> allowedMaterials) {

        public Config {
            allowedMaterials = Set.copyOf(allowedMaterials);
        }

        /** Standard: vor dem Spiel geschützt, während und nach dem Spiel frei. */
        public static Config defaults() {
            return new Config(true, false, false,
                    Set.of("CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "BARREL", "SHULKER_BOX"));
        }
    }

    private final Config config;

    public ProtectionRules(Config config) {
        this.config = config;
    }

    /** @return {@code true}, wenn die Aktion unterbunden werden muss. */
    public boolean shouldDeny(Query query) {
        if (query.hasBypass()) {
            return false;
        }
        if (query.region() != Region.CORNUCOPIA) {
            return false; // Arena ist nie plugin-geschützt
        }

        boolean protectedNow = switch (query.phase()) {
            case RUNNING -> config.protectCornucopiaRunning();
            case FINISHED -> config.protectCornucopiaFinished();
            // NOT_INITIALIZED/PREFLIGHT/LOBBY/STARTUP/READY = vor dem Spiel
            default -> config.protectCornucopiaPreGame();
        };
        if (!protectedNow) {
            return false;
        }

        // Container-Interaktionen mit erlaubten Materialien bleiben immer möglich
        if ((query.action() == ActionType.CONTAINER || query.action() == ActionType.INTERACT)
                && config.allowedMaterials().contains(query.material())) {
            return false;
        }
        return true;
    }
}
