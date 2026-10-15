package com.hungergames.game.protection;

import com.hungergames.api.model.GamePhase;
import com.hungergames.game.protection.ProtectionRules.ActionType;
import com.hungergames.game.protection.ProtectionRules.Config;
import com.hungergames.game.protection.ProtectionRules.Query;
import com.hungergames.game.protection.ProtectionRules.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schutzregeln aus Sicht eines normalen Spielers (ohne OP, ohne
 * Bypass-Permission) — die zentrale Anforderung: Cornucopia ist während
 * RUNNING vollständig frei.
 */
class ProtectionRulesTest {

    private final ProtectionRules rules = new ProtectionRules(Config.defaults());

    private static Query normalPlayer(Region region, ActionType action, GamePhase phase, String material) {
        return new Query(region, action, phase, false, material);
    }

    @Test
    @DisplayName("RUNNING: normaler Spieler darf in der Cornucopia alles — Kisten, Abbau, Platzieren")
    void cornucopiaFreeDuringRunning() {
        for (ActionType action : ActionType.values()) {
            assertFalse(rules.shouldDeny(normalPlayer(
                            Region.CORNUCOPIA, action, GamePhase.RUNNING, "STONE")),
                    action + " muss während RUNNING erlaubt sein");
        }
        assertFalse(rules.shouldDeny(normalPlayer(
                Region.CORNUCOPIA, ActionType.CONTAINER, GamePhase.RUNNING, "CHEST")));
    }

    @Test
    @DisplayName("Vor dem Spiel (LOBBY/READY): Cornucopia ist für normale Spieler geschützt")
    void cornucopiaProtectedPreGame() {
        for (GamePhase phase : new GamePhase[]{GamePhase.LOBBY, GamePhase.STARTUP, GamePhase.READY}) {
            assertTrue(rules.shouldDeny(normalPlayer(
                    Region.CORNUCOPIA, ActionType.BREAK, phase, "STONE")));
            assertTrue(rules.shouldDeny(normalPlayer(
                    Region.CORNUCOPIA, ActionType.PLACE, phase, "STONE")));
        }
    }

    @Test
    @DisplayName("Vor dem Spiel bleiben Kisten (allowed materials) benutzbar")
    void chestsUsablePreGame() {
        assertFalse(rules.shouldDeny(normalPlayer(
                Region.CORNUCOPIA, ActionType.CONTAINER, GamePhase.LOBBY, "CHEST")));
        assertFalse(rules.shouldDeny(normalPlayer(
                Region.CORNUCOPIA, ActionType.INTERACT, GamePhase.LOBBY, "BARREL")));
        // Aber kein Abbau, auch nicht von Kisten
        assertTrue(rules.shouldDeny(normalPlayer(
                Region.CORNUCOPIA, ActionType.BREAK, GamePhase.LOBBY, "CHEST")));
    }

    @Test
    @DisplayName("Arena-Region ist nie plugin-geschützt")
    void arenaNeverProtected() {
        for (GamePhase phase : GamePhase.values()) {
            assertFalse(rules.shouldDeny(normalPlayer(
                    Region.ARENA, ActionType.BREAK, phase, "STONE")));
        }
    }

    @Test
    @DisplayName("Bypass-Permission umgeht jeden Schutz")
    void bypassAlwaysAllowed() {
        assertFalse(rules.shouldDeny(new Query(
                Region.CORNUCOPIA, ActionType.BREAK, GamePhase.LOBBY, true, "STONE")));
    }

    @Test
    @DisplayName("Konfigurierbar: Schutz während RUNNING einschaltbar")
    void runningProtectionConfigurable() {
        ProtectionRules strict = new ProtectionRules(new Config(true, true, true,
                java.util.Set.of("CHEST")));

        assertTrue(strict.shouldDeny(normalPlayer(
                Region.CORNUCOPIA, ActionType.BREAK, GamePhase.RUNNING, "STONE")));
        // Kisten bleiben trotz aktiven Schutzes benutzbar
        assertFalse(strict.shouldDeny(normalPlayer(
                Region.CORNUCOPIA, ActionType.CONTAINER, GamePhase.RUNNING, "CHEST")));
    }

    @Test
    @DisplayName("Konfigurierbar: Vor-Spiel-Schutz abschaltbar")
    void preGameProtectionConfigurable() {
        ProtectionRules open = new ProtectionRules(new Config(false, false, false, java.util.Set.of()));

        assertFalse(open.shouldDeny(normalPlayer(
                Region.CORNUCOPIA, ActionType.BREAK, GamePhase.LOBBY, "STONE")));
    }
}
