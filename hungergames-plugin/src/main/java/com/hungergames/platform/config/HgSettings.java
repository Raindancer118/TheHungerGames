package com.hungergames.platform.config;

import com.hungergames.api.model.GamePhase;
import com.hungergames.game.config.SettingDefinition;
import com.hungergames.game.config.SettingDefinition.ApplyTiming;
import com.hungergames.game.config.SettingDefinition.SettingCategory;
import com.hungergames.game.config.SettingDefinition.Validator;
import com.hungergames.game.config.SettingType;
import com.hungergames.game.config.SettingsRegistry;

import java.time.Duration;
import java.util.List;

/**
 * Katalog aller Einstellungen des Plugins. Die Registrierungs-Reihenfolge
 * bestimmt die Reihenfolge in den Config-GUIs.
 */
public final class HgSettings {

    /** Verhalten beim Tod eines Teilnehmers. */
    public enum DeathAction {
        SPECTATOR,
        KICK,
        BAN
    }

    /** Wie Server-Downtime beim Restore einer laufenden Runde zählt. */
    public enum OfflineTimePolicy {
        /** Downtime zählt NICHT als Spielzeit (sicherer Default). */
        PAUSE,
        /** Downtime zählt als verstrichene Spielzeit. */
        COUNT
    }

    /** Standard-Spielmodus eines Spielmachers (Game Master). */
    public enum GamemasterMode {
        SPECTATOR,
        CREATIVE
    }

    /** Wie Spielmacher erkannt werden. */
    public enum GamemasterPermissionMode {
        /** Nur über die Permission {@code hungergames.gamemaster}. */
        PERMISSION,
        /** Nur über die Namensliste {@code gamemaster.names}. */
        LIST,
        /** Permission ODER Namensliste. */
        BOTH
    }

    /** Spawn-Modus der Sponsor-Beacons. */
    public enum BeaconSpawnMode {
        DISABLED,
        CENTER,
        RANDOM_TIMED,
        MANUAL
    }

    public final SettingsRegistry registry = new SettingsRegistry();

    // === GAME ===
    public final SettingDefinition<Duration> gameDuration = register(
            "game.duration", SettingType.DURATION, Duration.ofMinutes(180),
            v -> v.compareTo(Duration.ofMinutes(5)) >= 0 ? java.util.Optional.<String>empty()
                    : java.util.Optional.of("Spieldauer muss mindestens 5m betragen"),
            "Gesamtdauer einer Runde", SettingCategory.GAME, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Duration> gracePeriod = register(
            "game.grace-period", SettingType.DURATION, Duration.ofSeconds(60),
            Validator.any(), "Schutzzeit ohne PvP nach Spielstart",
            SettingCategory.GAME, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Integer> countdown = register(
            "game.countdown", SettingType.INT, 20,
            Validator.range(3, 300), "Countdown in Sekunden vor Spielbeginn",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> prepTimePercent = register(
            "game.prep-time-percent", SettingType.INT, 30,
            Validator.range(0, 90), "Anteil der Spielzeit vor der ersten Border-Phase (Prozent)",
            SettingCategory.GAME, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<HgSettings.DeathAction> deathAction = register(
            "game.death-action", SettingType.ofEnum(DeathAction.class), DeathAction.SPECTATOR,
            Validator.any(), "Verhalten bei Eliminierung: SPECTATOR, KICK oder BAN",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Duration> disconnectElimination = register(
            "game.disconnect-elimination", SettingType.DURATION, Duration.ZERO,
            Validator.any(),
            "Eliminiert Tribute, die länger als diese Zeit getrennt sind (z. B. 2m; 0 = deaktiviert)",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<OfflineTimePolicy> offlineTimePolicy = register(
            "game.restart.offline-time-policy", SettingType.ofEnum(OfflineTimePolicy.class),
            OfflineTimePolicy.PAUSE, Validator.any(),
            "Zählt Server-Downtime beim Fortsetzen einer Runde als Spielzeit? PAUSE = nein",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<List<String>> preInitAdmins = register(
            "game.pre-init-admins", SettingType.STRING_LIST, List.of(),
            Validator.any(), "Spieler, die vor /init joinen dürfen",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> preflightDifficulty = register(
            "game.preflight-difficulty", SettingType.STRING, "PEACEFUL",
            difficultyValidator(), "Schwierigkeitsgrad während der Vorbereitung",
            SettingCategory.GAME, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<String> gameDifficulty = register(
            "game.difficulty", SettingType.STRING, "NORMAL",
            difficultyValidator(), "Schwierigkeitsgrad ab Spielstart (Release)",
            SettingCategory.GAME, ApplyTiming.NEXT_ROUND);

    // === ARENA ===
    public final SettingDefinition<Integer> platformMinGap = register(
            "arena.platform-min-gap", SettingType.INT, 4,
            Validator.nonNegativeInt(), "Mindestabstand zwischen Plattformen (Blöcke)",
            SettingCategory.ARENA, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Integer> platformWidth = register(
            "arena.platform-width", SettingType.INT, 3,
            Validator.positiveInt(), "Breite der Spielerplattformen (Blöcke)",
            SettingCategory.ARENA, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Integer> undergroundRoomHeight = register(
            "arena.underground-room-height", SettingType.INT, 4,
            Validator.positiveInt(), "Innenhöhe des unterirdischen Raums (Blöcke)",
            SettingCategory.ARENA, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Integer> undergroundRoomExtraRadius = register(
            "arena.underground-room-extra-radius", SettingType.INT, 5,
            Validator.nonNegativeInt(), "Zusatzradius des unterirdischen Raums über den Plattformkreis hinaus",
            SettingCategory.ARENA, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Integer> tubeDepth = register(
            "arena.tube-depth", SettingType.INT, 12,
            Validator.positiveInt(), "Tiefe der Röhren (Fallback ohne Schematic)",
            SettingCategory.ARENA, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Boolean> blockNetherPortals = register(
            "arena.block-nether-portals", SettingType.BOOLEAN, true,
            Validator.any(), "Nether-Portale außerhalb des erlaubten Radius blockieren",
            SettingCategory.ARENA, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> netherAllowRadius = register(
            "arena.nether-allow-radius", SettingType.INT, 20,
            Validator.nonNegativeInt(), "Radius um das Zentrum, in dem Nether-Portale erlaubt sind",
            SettingCategory.ARENA, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> blockEndPortals = register(
            "arena.block-end-portals", SettingType.BOOLEAN, true,
            Validator.any(), "End-Portale blockieren",
            SettingCategory.ARENA, ApplyTiming.IMMEDIATE);

    // === LOBBY ===
    public final SettingDefinition<Integer> lobbyHeightOffset = register(
            "lobby.height-offset", SettingType.INT, 100,
            Validator.positiveInt(), "Höhe der Lobby über dem Arena-Zentrum",
            SettingCategory.LOBBY, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Integer> lobbyWidth = register(
            "lobby.width", SettingType.INT, 20, Validator.positiveInt(),
            "Breite der Lobby", SettingCategory.LOBBY, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Integer> lobbyDepth = register(
            "lobby.depth", SettingType.INT, 20, Validator.positiveInt(),
            "Tiefe der Lobby", SettingCategory.LOBBY, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Integer> lobbyHeight = register(
            "lobby.height", SettingType.INT, 5, Validator.positiveInt(),
            "Innenhöhe der Lobby", SettingCategory.LOBBY, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<String> lobbyBlockType = register(
            "lobby.block-type", SettingType.STRING, "GLASS",
            Validator.any(), "Blocktyp der Lobbyhülle (Material-Name)",
            SettingCategory.LOBBY, ApplyTiming.NEXT_ROUND);

    // === STARTUP ===
    public final SettingDefinition<Integer> lampDelay = register(
            "startup.lamp-delay", SettingType.INT, 5,
            Validator.positiveInt(), "Ticks zwischen den Redstone-Lampen",
            SettingCategory.STARTUP, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> levitationStartDelay = register(
            "startup.levitation-start-delay", SettingType.INT, 40,
            Validator.nonNegativeInt(), "Ticks bis zum Levitationsbeginn nach den Lampen",
            SettingCategory.STARTUP, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> playerLevitationDelay = register(
            "startup.player-levitation-delay", SettingType.INT, 10,
            Validator.nonNegativeInt(), "Ticks zwischen den Spielern beim Levitieren",
            SettingCategory.STARTUP, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> levitationAmplifier = register(
            "startup.levitation-amplifier", SettingType.INT, 2,
            Validator.nonNegativeInt(), "Stärke des Levitationseffekts",
            SettingCategory.STARTUP, ApplyTiming.IMMEDIATE);

    // === TEAMS ===
    public final SettingDefinition<Boolean> teamsEnabled = register(
            "teams.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Teams aktiviert? Bei false spielt jeder für sich (Solo-Modus)",
            SettingCategory.TEAMS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> teamMaxSize = register(
            "teams.max-size", SettingType.INT, 2,
            Validator.nonNegativeInt(), "Maximale Teamgröße (0 = unbegrenzt)",
            SettingCategory.TEAMS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> teamMaxTeams = register(
            "teams.max-teams", SettingType.INT, 0,
            Validator.nonNegativeInt(), "Maximale Teamanzahl (0 = unbegrenzt)",
            SettingCategory.TEAMS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> teamAllowSwitching = register(
            "teams.allow-switching", SettingType.BOOLEAN, true,
            Validator.any(), "Dürfen Spieler das Team wechseln (bis zur Sperre)?",
            SettingCategory.TEAMS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> teamCaptainEnabled = register(
            "teams.captain-enabled", SettingType.BOOLEAN, false,
            Validator.any(), "Captain-System aktivieren",
            SettingCategory.TEAMS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> teamPlayersCanCreate = register(
            "teams.players-can-create", SettingType.BOOLEAN, true,
            Validator.any(), "Dürfen Spieler über die GUI eigene Teams anlegen?",
            SettingCategory.TEAMS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> teamPlayersChooseColor = register(
            "teams.players-choose-color", SettingType.BOOLEAN, true,
            Validator.any(), "Dürfen Spieler/Captains die Teamfarbe wählen?",
            SettingCategory.TEAMS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<GamePhase> teamLockPhase = register(
            "teams.lock-phase", SettingType.ofEnum(GamePhase.class), GamePhase.STARTUP,
            Validator.any(), "Ab dieser Phase sind Teams eingefroren",
            SettingCategory.TEAMS, ApplyTiming.IMMEDIATE);

    // === BORDER ===
    public final SettingDefinition<Integer> borderInitialSize = register(
            "border.initial-size", SettingType.INT, 2500,
            Validator.positiveInt(), "Startdurchmesser der Worldborder (Blöcke)",
            SettingCategory.BORDER, ApplyTiming.NEXT_ROUND);

    public final SettingDefinition<Double> borderMinimumSize = register(
            "border.minimum-size", SettingType.DOUBLE, 100.0,
            Validator.positiveDouble(), "Absolute Mindestgröße der Border",
            SettingCategory.BORDER, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Double> borderMaxEdgeSpeed = register(
            "border.max-edge-speed", SettingType.DOUBLE, 2.5,
            Validator.positiveDouble(), "Fairness-Grenze: max. Blöcke/Sekunde pro Borderkante",
            SettingCategory.BORDER, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> borderScaleNether = register(
            "border.scale-nether", SettingType.BOOLEAN, true,
            Validator.any(), "Nether-Border im Maßstab 1:8 mitführen",
            SettingCategory.BORDER, ApplyTiming.IMMEDIATE);

    /**
     * Borderphasen in kompakter Syntax, eine pro Listeneintrag:
     * {@code <trigger> -> <zielgröße> @ <modus>}.
     * Trigger: {@code 20m} (Spielzeit) oder {@code alive<4} oder {@code 20m|alive<4}.
     * Modus: {@code duration:10m}, {@code speed:1.0} oder {@code max:2.5}.
     */
    public final SettingDefinition<List<String>> borderPhases = register(
            "border.phases", SettingType.STRING_LIST,
            List.of("50% -> 1000 @ max:2.5", "80% -> 200 @ max:2.5"),
            Validator.custom(com.hungergames.platform.border.BorderPhaseParser::validateList),
            "Borderphasen: '<trigger> -> <größe> @ <modus>' (z. B. '20m -> 1000 @ duration:10m', 'alive<4 -> 200 @ speed:1.0')",
            SettingCategory.BORDER, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<List<String>> borderPrepWarnings = register(
            "border.prep-warnings", SettingType.STRING_LIST, List.of("10", "5", "1"),
            Validator.any(), "Warnungen (Minuten vor der ersten Border-Phase)",
            SettingCategory.BORDER, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> borderShrinkWarning = register(
            "border.shrink-warning", SettingType.INT, 30,
            Validator.nonNegativeInt(), "Sekunden Vorwarnung vor jedem Schrumpfen",
            SettingCategory.BORDER, ApplyTiming.IMMEDIATE);

    // === PROTECTION ===
    public final SettingDefinition<Integer> cornucopiaRadius = register(
            "protection.cornucopia-radius", SettingType.INT, 20,
            Validator.positiveInt(), "Radius der Cornucopia-/Spawnregion um das Zentrum",
            SettingCategory.PROTECTION, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> protectCornucopiaPreGame = register(
            "protection.cornucopia.before-running", SettingType.BOOLEAN, true,
            Validator.any(), "Cornucopia vor Spielbeginn schützen",
            SettingCategory.PROTECTION, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> protectCornucopiaRunning = register(
            "protection.cornucopia.during-running", SettingType.BOOLEAN, false,
            Validator.any(), "Cornucopia WÄHREND des Spiels schützen (normal: aus — Kisten/Abbau frei)",
            SettingCategory.PROTECTION, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> protectCornucopiaFinished = register(
            "protection.cornucopia.after-game", SettingType.BOOLEAN, false,
            Validator.any(), "Cornucopia nach Spielende schützen",
            SettingCategory.PROTECTION, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<List<String>> allowedInteractions = register(
            "protection.allowed-interactions", SettingType.STRING_LIST,
            List.of("CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "BARREL", "SHULKER_BOX"),
            Validator.any(), "Materialien, die trotz Schutz benutzbar bleiben",
            SettingCategory.PROTECTION, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> bypassPermission = register(
            "protection.bypass-permission", SettingType.STRING, "hungergames.protection.bypass",
            Validator.any(), "Permission, die den Gebietsschutz umgeht",
            SettingCategory.PROTECTION, ApplyTiming.IMMEDIATE);

    // === ITEMS ===
    public final SettingDefinition<Integer> fiendfinderGlowDuration = register(
            "items.fiendfinder.glow-duration", SettingType.INT, 15,
            Validator.positiveInt(), "Dauer des Fiendfinder-Glowings (Sekunden)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> fiendfinderSearchRadius = register(
            "items.fiendfinder.search-radius", SettingType.INT, 0,
            Validator.nonNegativeInt(), "Suchradius des Fiendfinders (0 = unbegrenzt)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Rauchbombe
    public final SettingDefinition<Integer> smokeBombRadius = register(
            "items.smoke-bomb.radius", SettingType.INT, 6,
            Validator.positiveInt(), "Rauchbombe: Wirkradius auf Gegner (Blöcke)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> smokeBombEnemyDuration = register(
            "items.smoke-bomb.enemy-duration", SettingType.INT, 6,
            Validator.positiveInt(), "Rauchbombe: Blindheit+Langsamkeit der Gegner (Sekunden)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> smokeBombInvisSeconds = register(
            "items.smoke-bomb.self-invisibility", SettingType.INT, 3,
            Validator.nonNegativeInt(), "Rauchbombe: eigene Unsichtbarkeit inkl. Rüstung (Sekunden, 0 = aus)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Medikit
    public final SettingDefinition<Integer> medikitRegenSeconds = register(
            "items.medikit.regen-seconds", SettingType.INT, 6,
            Validator.nonNegativeInt(), "Medikit: Regenerations-Dauer (Sekunden)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> medikitRegenLevel = register(
            "items.medikit.regen-level", SettingType.INT, 2,
            Validator.positiveInt(), "Medikit: Regenerations-Stärke",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> medikitAbsorptionSeconds = register(
            "items.medikit.absorption-seconds", SettingType.INT, 60,
            Validator.nonNegativeInt(), "Medikit: Absorptions-Dauer (Sekunden)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> medikitAbsorptionLevel = register(
            "items.medikit.absorption-level", SettingType.INT, 2,
            Validator.positiveInt(), "Medikit: Absorptions-Stärke (Extraherzen-Paare)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> medikitCountdownSeconds = register(
            "items.medikit.countdown-seconds", SettingType.INT, 3,
            Validator.nonNegativeInt(),
            "Medikit: Countdown bis zur Wirkung (Sekunden, 0 = sofort); Schaden bricht ab",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Blitzschlag ("Zorn des Kapitols" — konfigurierbares Gewitter)
    public final SettingDefinition<Integer> lightningRange = register(
            "items.lightning.range", SettingType.INT, 80,
            Validator.positiveInt(), "Blitzschlag: Reichweite des anvisierten Ziels (Blöcke)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> lightningBoltCount = register(
            "items.lightning.bolt-count", SettingType.INT, 6,
            Validator.positiveInt(), "Blitzschlag: Anzahl der Blitze (Salve)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> lightningSpread = register(
            "items.lightning.spread", SettingType.INT, 3,
            Validator.nonNegativeInt(), "Blitzschlag: Streuung der Blitze um das Ziel (Blöcke)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> lightningBonusDamage = register(
            "items.lightning.bonus-damage", SettingType.INT, 8,
            Validator.nonNegativeInt(), "Blitzschlag: Zusatzschaden pro Blitz (1 = halbes Herz)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> lightningDamageRadius = register(
            "items.lightning.damage-radius", SettingType.INT, 4,
            Validator.positiveInt(), "Blitzschlag: Radius des Zusatzschadens je Blitz (Blöcke)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> lightningFireTicks = register(
            "items.lightning.fire-ticks", SettingType.INT, 80,
            Validator.nonNegativeInt(), "Blitzschlag: Brenndauer getroffener Ziele (Ticks, 0 = aus)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> lightningBoltDelay = register(
            "items.lightning.bolt-delay", SettingType.INT, 3,
            Validator.nonNegativeInt(), "Blitzschlag: Verzögerung zwischen den Blitzen (Ticks)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Boolean> lightningKnockup = register(
            "items.lightning.knockup", SettingType.BOOLEAN, true,
            Validator.any(), "Blitzschlag: getroffene Ziele werden hochgeschleudert",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Hermes' Stiefel
    public final SettingDefinition<Integer> hermesFlightSeconds = register(
            "items.hermes-boots.flight-seconds", SettingType.INT, 4,
            Validator.positiveInt(), "Hermes' Stiefel: Flugdauer (Sekunden)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> hermesWarningSeconds = register(
            "items.hermes-boots.warning-seconds", SettingType.INT, 3,
            Validator.nonNegativeInt(), "Hermes' Stiefel: Warnsound in den letzten N Sekunden (0 = aus)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Krückauwasser
    public final SettingDefinition<Integer> krueckauRadius = register(
            "items.krueckau.radius", SettingType.INT, 4,
            Validator.positiveInt(), "Krückauwasser: Wirkradius (Blöcke)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> krueckauNauseaSeconds = register(
            "items.krueckau.nausea-seconds", SettingType.INT, 12,
            Validator.positiveInt(), "Krückauwasser: Übelkeit-Dauer (Sekunden)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> krueckauBlindnessSeconds = register(
            "items.krueckau.blindness-seconds", SettingType.INT, 0,
            Validator.nonNegativeInt(), "Krückauwasser: Blindheit-Dauer (Sekunden, 0 = aus)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Aura der Bewahrung (Kill-Aura auf Zeit)
    public final SettingDefinition<Integer> auraDurationSeconds = register(
            "items.aura.duration-seconds", SettingType.INT, 5,
            Validator.positiveInt(), "Aura: Dauer (Sekunden)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> auraRadius = register(
            "items.aura.radius", SettingType.INT, 4,
            Validator.positiveInt(), "Aura: Wirkradius (Blöcke)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> auraDamage = register(
            "items.aura.damage", SettingType.INT, 6,
            Validator.nonNegativeInt(), "Aura: Schaden pro Tick (1 = halbes Herz)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> auraInterval = register(
            "items.aura.interval-ticks", SettingType.INT, 10,
            Validator.positiveInt(), "Aura: Intervall zwischen Treffern (Ticks)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> auraKnockback = register(
            "items.aura.knockback", SettingType.INT, 6,
            Validator.nonNegativeInt(), "Aura: Rückstoß-Stärke (×0.1)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Boolean> auraAffectMobs = register(
            "items.aura.affect-mobs", SettingType.BOOLEAN, true,
            Validator.any(), "Aura: greift auch feindliche Mobs an (nicht nur Spieler)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Enterhaken
    public final SettingDefinition<Integer> grapplingRange = register(
            "items.grappling.range", SettingType.INT, 40,
            Validator.positiveInt(), "Enterhaken: Reichweite (Blöcke)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> grapplingPower = register(
            "items.grappling.power", SettingType.INT, 14,
            Validator.positiveInt(), "Enterhaken: Zugstärke (×0.1)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Druckwelle
    public final SettingDefinition<Integer> repulseRadius = register(
            "items.repulse.radius", SettingType.INT, 6,
            Validator.positiveInt(), "Druckwelle: Radius (Blöcke)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> repulseStrength = register(
            "items.repulse.strength", SettingType.INT, 12,
            Validator.positiveInt(), "Druckwelle: Stärke (×0.1)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> repulseSlowSeconds = register(
            "items.repulse.slow-seconds", SettingType.INT, 2,
            Validator.nonNegativeInt(), "Druckwelle: Langsamkeit der Getroffenen (Sekunden, 0 = aus)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Boolean> repulseAffectMobs = register(
            "items.repulse.affect-mobs", SettingType.BOOLEAN, true,
            Validator.any(), "Druckwelle: stößt auch feindliche Mobs weg (nicht nur Spieler)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Festmahl
    public final SettingDefinition<Integer> feastGoldenApples = register(
            "items.feast.golden-apples", SettingType.INT, 2,
            Validator.nonNegativeInt(), "Festmahl: Anzahl goldener Äpfel (0 = keine)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Kriegsausrüstung
    public final SettingDefinition<String> warKitMaterial = register(
            "items.war-kit.material", SettingType.STRING, "IRON",
            Validator.any(), "Kriegsausrüstung: Rüstungsmaterial (LEATHER, CHAINMAIL, IRON, GOLDEN, DIAMOND, NETHERITE)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Sprungfeder
    public final SettingDefinition<Integer> leapPower = register(
            "items.leap.power", SettingType.INT, 15,
            Validator.positiveInt(), "Sprungfeder: Sprungstärke (×0.1)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Exmatrikulator (Blitz-Minigun-Aura, craftbar)
    public final SettingDefinition<Integer> exmatrikulatorDuration = register(
            "items.exmatrikulator.duration-seconds", SettingType.INT, 5,
            Validator.positiveInt(), "Exmatrikulator: Dauer der Blitz-Aura (Sekunden)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> exmatrikulatorRadius = register(
            "items.exmatrikulator.radius", SettingType.INT, 8,
            Validator.positiveInt(), "Exmatrikulator: Wirkradius (Blöcke)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> exmatrikulatorInterval = register(
            "items.exmatrikulator.interval-ticks", SettingType.INT, 4,
            Validator.positiveInt(), "Exmatrikulator: Feuerrate (Ticks zwischen Salven, kleiner = schneller)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> exmatrikulatorDamage = register(
            "items.exmatrikulator.bonus-damage", SettingType.INT, 6,
            Validator.nonNegativeInt(), "Exmatrikulator: Zusatzschaden pro Blitz (1 = halbes Herz)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> exmatrikulatorMaxTargets = register(
            "items.exmatrikulator.max-targets", SettingType.INT, 5,
            Validator.positiveInt(), "Exmatrikulator: max. Ziele pro Salve",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> exmatrikulatorFireTicks = register(
            "items.exmatrikulator.fire-ticks", SettingType.INT, 40,
            Validator.nonNegativeInt(), "Exmatrikulator: Brenndauer getroffener Ziele (Ticks, 0 = aus)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<List<String>> exmatrikulatorModules = register(
            "items.exmatrikulator.modules", SettingType.STRING_LIST,
            List.of("Mathematik I", "Mathematik II", "Theoretische Informatik",
                    "Programmierung", "Rechnungswesen", "Statistik",
                    "Datenbanken", "Software Engineering", "BWL"),
            Validator.any(), "Exmatrikulator: Modul-Namen für die Todesnachricht "
                    + "(\"… versagte im Drittversuch in <Modul>\"); leer = ohne Modul",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<List<String>> exmatrikulatorDeathMessages = register(
            "items.exmatrikulator.death-messages", SettingType.STRING_LIST,
            List.of("wurde exmatrikuliert, als er im Drittversuch in %modul% gegen %killer% versagte.",
                    "wurde nach nicht bestandener Klausur in %modul% von %killer% exmatrikuliert.",
                    "scheiterte in %modul% an Prüfer %killer% und wurde exmatrikuliert.",
                    "hat den Drittversuch in %modul% gegen %killer% vergeigt — exmatrikuliert.",
                    "wurde von Prüfungsausschuss %killer% wegen %modul% exmatrikuliert."),
            Validator.any(), "Exmatrikulator: Todesnachrichten (folgen auf den Spielernamen; "
                    + "Platzhalter %killer% und %modul%); eine wird zufällig gewählt",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<List<String>> exmatrikulatorRecipe = register(
            "items.exmatrikulator.recipe", SettingType.STRING_LIST,
            List.of("LIGHTNING_ROD DIAMOND_BLOCK LIGHTNING_ROD",
                    "NETHERITE_INGOT DIAMOND_BLOCK NETHERITE_INGOT",
                    "LIGHTNING_ROD DIAMOND_BLOCK LIGHTNING_ROD"),
            Validator.any(), "Exmatrikulator: Crafting-Rezept (3 Zeilen à 3 Materialien, "
                    + "'-' = leeres Feld). Nach Änderung /config-reload.",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // Trottel-Schutz (rettet vor Umweltschaden)
    public final SettingDefinition<Integer> stupidnessHealHearts = register(
            "items.stupidness-protector.heal-health", SettingType.INT, 8,
            Validator.positiveInt(), "Trottel-Schutz: Leben nach Rettung (1 = halbes Herz)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> stupidnessRegenSeconds = register(
            "items.stupidness-protector.regen-seconds", SettingType.INT, 8,
            Validator.nonNegativeInt(), "Trottel-Schutz: Regeneration nach Rettung (Sekunden)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> stupidnessFireResistSeconds = register(
            "items.stupidness-protector.fire-resistance-seconds", SettingType.INT, 10,
            Validator.nonNegativeInt(), "Trottel-Schutz: Feuerschutz nach Rettung (Sekunden, gegen Lava)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> stupidnessShoveRadius = register(
            "items.stupidness-protector.shove-radius", SettingType.INT, 5,
            Validator.nonNegativeInt(), "Trottel-Schutz: Umkreis, aus dem Gegner weggestoßen werden (0 = aus)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> stupidnessShoveStrength = register(
            "items.stupidness-protector.shove-strength", SettingType.INT, 12,
            Validator.nonNegativeInt(), "Trottel-Schutz: Stärke des Wegstoßens (×0.1)",
            SettingCategory.ITEMS, ApplyTiming.IMMEDIATE);

    // === LOOT ===
    public final SettingDefinition<Integer> lootScanRadius = register(
            "loot.scan-radius", SettingType.INT, 50,
            Validator.positiveInt(), "Scan-Radius für Loot-Container",
            SettingCategory.LOOT, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> lootScanYRange = register(
            "loot.scan-y-range", SettingType.INT, 30,
            Validator.positiveInt(), "Vertikaler Scan-Bereich für Loot-Container",
            SettingCategory.LOOT, ApplyTiming.IMMEDIATE);

    // === LOOT (Editor) ===
    public final SettingDefinition<Boolean> lootEditorEnabled = register(
            "loot.editor.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Ingame-LootTable-Editor aktivieren",
            SettingCategory.LOOT, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> lootEditorRuntimeEdits = register(
            "loot.editor.allow-runtime-edits", SettingType.BOOLEAN, true,
            Validator.any(), "LootTables auch während einer laufenden Runde bearbeitbar",
            SettingCategory.LOOT, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> lootEditorBackup = register(
            "loot.editor.backup-before-save", SettingType.BOOLEAN, true,
            Validator.any(), "Vor dem Speichern ein Backup der loot.yml anlegen",
            SettingCategory.LOOT, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> lootEditorMaxTestRolls = register(
            "loot.editor.max-test-rolls", SettingType.INT, 100,
            Validator.range(1, 10000), "Maximale Anzahl Simulationen pro Loot-Test",
            SettingCategory.LOOT, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> lootEditorAllowTestGive = register(
            "loot.editor.allow-test-give", SettingType.BOOLEAN, true,
            Validator.any(), "Test-Loot direkt ins eigene Inventar geben erlauben",
            SettingCategory.LOOT, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> lootEditorAllowTestChest = register(
            "loot.editor.allow-test-chest", SettingType.BOOLEAN, true,
            Validator.any(), "Test-Chest am eigenen Standort füllen erlauben",
            SettingCategory.LOOT, ApplyTiming.IMMEDIATE);

    // === GAME: mitspielende Admins (Deop/Re-Op) ===
    public final SettingDefinition<Boolean> adminDeopOnStart = register(
            "game.admin-participants.deop-on-start", SettingType.BOOLEAN, true,
            Validator.any(), "OP-Tribute beim Übergang zu RUNNING automatisch deopen",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> adminReopOnElimination = register(
            "game.admin-participants.reop-on-elimination", SettingType.BOOLEAN, true,
            Validator.any(), "Deopten Admins nach ihrer Eliminierung OP zurückgeben",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> adminReopOnFinish = register(
            "game.admin-participants.reop-on-finish", SettingType.BOOLEAN, true,
            Validator.any(), "Alle gespeicherten OP-Status am Rundenende wiederherstellen",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> adminCreativeOnElimination = register(
            "game.admin-participants.set-creative-on-elimination", SettingType.BOOLEAN, true,
            Validator.any(), "Eliminierte Admin-Tribute in den Creative-Modus versetzen",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> adminTeleportCenterOnElimination = register(
            "game.admin-participants.teleport-to-center-on-elimination", SettingType.BOOLEAN, true,
            Validator.any(), "Eliminierte Admin-Tribute über die Arena-Mitte teleportieren",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> adminCenterYOffset = register(
            "game.admin-participants.center-y-offset", SettingType.INT, 10,
            Validator.nonNegativeInt(), "Höhe über der Arena-Mitte beim Eliminierungs-Teleport",
            SettingCategory.GAME, ApplyTiming.IMMEDIATE);

    // === EVENTS: Supply Drops ===
    public final SettingDefinition<Boolean> supplyDropsEnabled = register(
            "events.supply-drops.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Zeitgesteuerte Kapitol-Lieferungen (Supply Drops) aktivieren",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<List<String>> supplyDropSchedule = register(
            "events.supply-drops.schedule", SettingType.STRING_LIST, List.of("20m", "45m", "75m"),
            durationList(), "Spielzeitpunkte der Drops (z. B. 20m, 45m, 75m)",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> supplyDropWarningSeconds = register(
            "events.supply-drops.warning-seconds", SettingType.INT, 60,
            Validator.nonNegativeInt(), "Vorwarnzeit vor der Landung (Sekunden)",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> supplyDropCount = register(
            "events.supply-drops.drop-count", SettingType.INT, 1,
            Validator.range(1, 10), "Anzahl Kisten pro Drop-Zeitpunkt",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> supplyDropRadiusMin = register(
            "events.supply-drops.radius-min", SettingType.INT, 30,
            Validator.nonNegativeInt(), "Mindestabstand der Drops vom Zentrum",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> supplyDropRadiusMax = register(
            "events.supply-drops.radius-max", SettingType.INT, 250,
            Validator.positiveInt(), "Maximalabstand der Drops vom Zentrum",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> supplyDropOnlyOverworld = register(
            "events.supply-drops.only-overworld", SettingType.BOOLEAN, true,
            Validator.any(), "Drops nur in der Oberwelt landen lassen",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> supplyDropAnnounceCoords = register(
            "events.supply-drops.announce-coordinates", SettingType.BOOLEAN, true,
            Validator.any(), "Landekoordinaten im Broadcast nennen",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> supplyDropCoordinateFuzz = register(
            "events.supply-drops.coordinate-fuzz", SettingType.INT, 0,
            Validator.nonNegativeInt(), "Ungenauigkeit der angesagten Koordinaten (Blöcke, 0 = exakt)",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> supplyDropBeacon = register(
            "events.supply-drops.beacon-enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Lichtsäule/Beacon an der Landestelle",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> supplyDropBaseMaterial = register(
            "events.supply-drops.base-material", SettingType.STRING, "IRON_BLOCK",
            material(), "Blocktyp der 3×3-Basis unter dem Supply-Drop-Beacon",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> supplyDropProtected = register(
            "events.supply-drops.protected", SettingType.BOOLEAN, true,
            Validator.any(), "Supply-Drop-Kiste, -Beacon und -Basis vor Zerstörung schützen "
                    + "(öffnen bleibt möglich; Spielmacher können trotzdem abbauen)",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    // Monster-Wellen (Spielmacher spawnt Wellen an einem Ort)
    public final SettingDefinition<String> monsterWaveMob = register(
            "events.monster-waves.default-mob", SettingType.STRING, "ZOMBIE",
            Validator.any(), "Monster-Wellen: Standard-Monster (z. B. ZOMBIE, SKELETON, SPIDER)",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> monsterWaveCount = register(
            "events.monster-waves.count-per-wave", SettingType.INT, 6,
            Validator.positiveInt(), "Monster-Wellen: Monster pro Welle",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> monsterWaveWaves = register(
            "events.monster-waves.wave-count", SettingType.INT, 5,
            Validator.positiveInt(), "Monster-Wellen: Anzahl der Wellen",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> monsterWaveInterval = register(
            "events.monster-waves.interval-seconds", SettingType.INT, 15,
            Validator.positiveInt(), "Monster-Wellen: Sekunden zwischen den Wellen",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);
    public final SettingDefinition<Integer> monsterWaveSpread = register(
            "events.monster-waves.spread", SettingType.INT, 4,
            Validator.nonNegativeInt(), "Monster-Wellen: Streuung um den Spawn-Ort (Blöcke)",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> supplyDropFirework = register(
            "events.supply-drops.firework-enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Feuerwerk bei der Landung",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> supplyDropParticles = register(
            "events.supply-drops.particles-enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Partikeleffekte an der Landestelle",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> supplyDropLootTable = register(
            "events.supply-drops.loot-table", SettingType.STRING, "supply_drop",
            Validator.custom(v -> v.isBlank()
                    ? java.util.Optional.of("LootTable-Name darf nicht leer sein")
                    : java.util.Optional.empty()),
            "LootTable, mit der Supply-Drop-Kisten befüllt werden",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    // === EVENTS: Rundenlog ===
    public final SettingDefinition<Boolean> roundLogEnabled = register(
            "events.round-log.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Rundenlog (Kills, Drops, Käufe, Admin-Aktionen) schreiben",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> roundLogFilePerRound = register(
            "events.round-log.file-per-round", SettingType.BOOLEAN, true,
            Validator.any(), "Pro Runde eine eigene Logdatei anlegen",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> roundLogCoordinates = register(
            "events.round-log.include-coordinates", SettingType.BOOLEAN, false,
            Validator.any(), "Koordinaten in Logeinträge aufnehmen",
            SettingCategory.EVENTS, ApplyTiming.IMMEDIATE);

    // === DEATHMATCH ===
    public final SettingDefinition<Boolean> deathmatchEnabled = register(
            "deathmatch.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Deathmatch-Feature aktivieren (manuell auslösbar)",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> deathmatchManualOnly = register(
            "deathmatch.manual-only", SettingType.BOOLEAN, true,
            Validator.any(),
            "Deathmatch nur manuell starten (aus = startet automatisch bei 2 verbleibenden Tributen)",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> deathmatchTargetBorderSize = register(
            "deathmatch.target-border-size", SettingType.INT, 100,
            Validator.positiveInt(), "Zieldurchmesser der Border im Deathmatch",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> deathmatchWarningSeconds = register(
            "deathmatch.warning-seconds", SettingType.INT, 60,
            Validator.nonNegativeInt(), "Vorwarnzeit vor Deathmatch-Beginn (Sekunden)",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> deathmatchTeleportToCenter = register(
            "deathmatch.teleport-to-center", SettingType.BOOLEAN, false,
            Validator.any(), "Lebende Tribute zum Deathmatch-Start in die Mitte teleportieren",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> deathmatchTeleportYOffset = register(
            "deathmatch.teleport-y-offset", SettingType.INT, 2,
            Validator.nonNegativeInt(), "Höhe über der Mitte beim Deathmatch-Teleport",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Duration> deathmatchGraceAfterTeleport = register(
            "deathmatch.grace-after-teleport", SettingType.DURATION, Duration.ofSeconds(10),
            Validator.any(), "Kurze Schutzzeit nach dem Deathmatch-Teleport",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> deathmatchRequireConfirmation = register(
            "deathmatch.require-confirmation", SettingType.BOOLEAN, true,
            Validator.any(), "Bestätigungs-GUI vor dem Deathmatch-Start verlangen",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<List<String>> deathmatchAllowedPhases = register(
            "deathmatch.allowed-phases", SettingType.STRING_LIST, List.of("RUNNING"),
            phaseList(), "Phasen, in denen das Deathmatch gestartet werden darf",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> deathmatchBroadcast = register(
            "deathmatch.broadcast-enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Deathmatch-Warnung und -Start broadcasten",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> deathmatchSound = register(
            "deathmatch.sound-enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Sounds bei Deathmatch-Warnung und -Start",
            SettingCategory.DEATHMATCH, ApplyTiming.IMMEDIATE);

    // === GAMEMASTER ===
    public final SettingDefinition<Boolean> gmEnabled = register(
            "gamemaster.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Spielmacher-Modus aktivieren",
            SettingCategory.GAMEMASTER, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<GamemasterMode> gmDefaultMode = register(
            "gamemaster.default-mode", SettingType.ofEnum(GamemasterMode.class), GamemasterMode.SPECTATOR,
            Validator.any(), "Spielmodus beim Aktivieren des Spielmacher-Modus",
            SettingCategory.GAMEMASTER, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> gmKeepOp = register(
            "gamemaster.keep-op", SettingType.BOOLEAN, true,
            Validator.any(), "Spielmacher behalten ihren OP-Status",
            SettingCategory.GAMEMASTER, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> gmAllowTeleportMenu = register(
            "gamemaster.allow-teleport-menu", SettingType.BOOLEAN, true,
            Validator.any(), "Spielmacher dürfen das Teleport-Menü zu Tributen nutzen",
            SettingCategory.GAMEMASTER, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> gmHideFromPlayerCount = register(
            "gamemaster.hide-from-player-count", SettingType.BOOLEAN, true,
            Validator.any(), "Spielmacher nicht in Spielerzahl-Anzeigen mitzählen",
            SettingCategory.GAMEMASTER, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<List<String>> gmNames = register(
            "gamemaster.names", SettingType.STRING_LIST, List.of(),
            Validator.any(), "Spielernamen, die (je nach Modus) als Spielmacher gelten",
            SettingCategory.GAMEMASTER, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<GamemasterPermissionMode> gmPermissionMode = register(
            "gamemaster.permission-mode", SettingType.ofEnum(GamemasterPermissionMode.class),
            GamemasterPermissionMode.PERMISSION, Validator.any(),
            "Erkennung: PERMISSION (Recht), LIST (Namensliste) oder BOTH",
            SettingCategory.GAMEMASTER, ApplyTiming.IMMEDIATE);

    // === SPONSORS: Tokens ===
    public final SettingDefinition<Boolean> sponsorsEnabled = register(
            "sponsors.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Sponsor-System (Tokens + Beacons) als Ganzes aktivieren",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorTokensEnabled = register(
            "sponsors.tokens.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Sponsor Tokens fürs Überleben vergeben",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> sponsorTokenMaterial = register(
            "sponsors.tokens.material", SettingType.STRING, "NETHER_STAR",
            material(), "Material des Sponsor-Token-Items",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> sponsorTokenDisplayName = register(
            "sponsors.tokens.display-name", SettingType.STRING, "§6Sponsor Token",
            Validator.any(), "Anzeigename der Sponsor Tokens",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<List<String>> sponsorTokenLore = register(
            "sponsors.tokens.lore", SettingType.STRING_LIST,
            List.of("§7Verdient durch Überleben.", "§7Einlösbar an Sponsor Beacons."),
            Validator.any(), "Lore-Zeilen der Sponsor Tokens",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Duration> sponsorTokenInterval = register(
            "sponsors.tokens.interval", SettingType.DURATION, Duration.ofMinutes(10),
            v -> v.compareTo(Duration.ofSeconds(10)) >= 0 ? java.util.Optional.<String>empty()
                    : java.util.Optional.of("Intervall muss mindestens 10s betragen"),
            "Abstand zwischen zwei Token-Vergaben",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> sponsorTokenAmount = register(
            "sponsors.tokens.amount-per-interval", SettingType.INT, 1,
            Validator.range(1, 64), "Tokens pro Intervall und Tribut",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Duration> sponsorTokenFirstAfter = register(
            "sponsors.tokens.first-token-after", SettingType.DURATION, Duration.ofMinutes(10),
            Validator.any(), "Spielzeit bis zum ersten Token",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> sponsorTokenMaxPerPlayer = register(
            "sponsors.tokens.max-per-player", SettingType.INT, 0,
            Validator.nonNegativeInt(), "Maximal verdienbare Tokens pro Spieler (0 = unbegrenzt)",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorTokenOnlyAlive = register(
            "sponsors.tokens.only-alive-players", SettingType.BOOLEAN, true,
            Validator.any(), "Nur lebende Tribute erhalten Tokens",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorTokenAnnouncePersonal = register(
            "sponsors.tokens.announce-personal", SettingType.BOOLEAN, true,
            Validator.any(), "Empfänger persönlich über neue Tokens informieren",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorTokenBroadcastMilestones = register(
            "sponsors.tokens.broadcast-milestones", SettingType.BOOLEAN, false,
            Validator.any(), "Token-Meilensteine öffentlich broadcasten",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorTokenDropOnDeath = register(
            "sponsors.tokens.drop-on-death", SettingType.BOOLEAN, true,
            Validator.any(), "Tokens droppen beim Tod wie normale Items",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorTokenClearOnElimination = register(
            "sponsors.tokens.clear-on-elimination", SettingType.BOOLEAN, false,
            Validator.any(), "Tokens bei Eliminierung entfernen (hat Vorrang vor drop-on-death)",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorTokenClearOnRoundReset = register(
            "sponsors.tokens.clear-on-round-reset", SettingType.BOOLEAN, true,
            Validator.any(), "Token-Zähler beim Rundenreset zurücksetzen",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    // === SPONSORS: Beacons ===
    public final SettingDefinition<Boolean> sponsorBeaconsEnabled = register(
            "sponsors.beacons.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Sponsor-Beacons (Shop-Stationen) aktivieren",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<BeaconSpawnMode> sponsorBeaconSpawnMode = register(
            "sponsors.beacons.spawn-mode", SettingType.ofEnum(BeaconSpawnMode.class),
            BeaconSpawnMode.CENTER, Validator.any(),
            "Spawn-Modus: DISABLED, CENTER, RANDOM_TIMED oder MANUAL",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorBeaconCenterOnStart = register(
            "sponsors.beacons.center-on-start", SettingType.BOOLEAN, true,
            Validator.any(), "Beim Spielstart direkt einen Sponsor-Beacon nahe der Mitte "
                    + "spawnen (unabhängig vom Spawn-Modus kombinierbar mit RANDOM_TIMED)",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> sponsorBeaconMaterial = register(
            "sponsors.beacons.material", SettingType.STRING, "BEACON",
            material(), "Blocktyp der Sponsor-Beacons",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> sponsorBeaconBaseMaterial = register(
            "sponsors.beacons.base-material", SettingType.STRING, "IRON_BLOCK",
            material(), "Blocktyp der 3×3-Basis unter dem Sponsor-Beacon",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorBeaconProtected = register(
            "sponsors.beacons.protected", SettingType.BOOLEAN, true,
            Validator.any(), "Sponsor-Beacon und -Basis vor Zerstörung schützen "
                    + "(Nutzung bleibt möglich; Spielmacher können trotzdem abbauen)",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> sponsorBeaconRadiusMin = register(
            "sponsors.beacons.radius-min", SettingType.INT, 30,
            Validator.nonNegativeInt(), "Mindestabstand zufälliger Beacons vom Zentrum",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> sponsorBeaconRadiusMax = register(
            "sponsors.beacons.radius-max", SettingType.INT, 200,
            Validator.positiveInt(), "Maximalabstand zufälliger Beacons vom Zentrum",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<List<String>> sponsorBeaconSchedule = register(
            "sponsors.beacons.spawn-schedule", SettingType.STRING_LIST, List.of("15m", "35m", "60m"),
            durationList(), "Spawn-Zeitpunkte bei RANDOM_TIMED (Spielzeit)",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> sponsorBeaconMaxActive = register(
            "sponsors.beacons.max-active", SettingType.INT, 3,
            Validator.positiveInt(), "Maximal gleichzeitig aktive Sponsor-Beacons",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorBeaconAnnounceSpawn = register(
            "sponsors.beacons.announce-spawn", SettingType.BOOLEAN, true,
            Validator.any(), "Beacon-Spawns broadcasten",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorBeaconAnnounceCoords = register(
            "sponsors.beacons.announce-coordinates", SettingType.BOOLEAN, true,
            Validator.any(), "Beacon-Koordinaten in der Ansage nennen",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Integer> sponsorBeaconCoordinateFuzz = register(
            "sponsors.beacons.coordinate-fuzz", SettingType.INT, 0,
            Validator.nonNegativeInt(), "Ungenauigkeit der angesagten Beacon-Koordinaten (Blöcke)",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorBeaconParticles = register(
            "sponsors.beacons.particles-enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Partikeleffekte an aktiven Beacons",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> sponsorBeaconSound = register(
            "sponsors.beacons.sound-enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Sound beim Beacon-Spawn",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    // === SPONSORS: Shop ===
    public final SettingDefinition<Boolean> sponsorShopEnabled = register(
            "sponsors.shop.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Sponsor-Shop an Beacons aktivieren",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<List<String>> sponsorShopItems = register(
            "sponsors.shop.items", SettingType.STRING_LIST,
            List.of("bread_pack|BREAD:8|1|Essen Paket",
                    "arrows|ARROW:16|1|Pfeile",
                    "iron_ingot|IRON_INGOT:2|2|Eisen",
                    "glow_10s|EFFECT:GLOWING:10:0|2|Glow 10 Sekunden",
                    "speed_15s|EFFECT:SPEED:15:0|3|Speed 15 Sekunden",
                    "fiendfinder|ITEM:FIENDFINDER:1|4|Fiendfinder"),
            Validator.custom(com.hungergames.platform.sponsor.ShopItemParser::validateList),
            "Shop-Einträge: 'id|reward|kosten|Anzeigename' — Reward: MATERIAL:ANZAHL, "
                    + "EFFECT:TYP:SEKUNDEN:STÄRKE oder ITEM:FIENDFINDER:ANZAHL",
            SettingCategory.SPONSORS, ApplyTiming.IMMEDIATE);

    // === SOUNDS ===
    public final SettingDefinition<Boolean> soundsEnabled = register(
            "sounds.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Zentrales Sound-System aktivieren",
            SettingCategory.SOUNDS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Double> soundDefaultVolume = register(
            "sounds.default-volume", SettingType.DOUBLE, 1.0,
            Validator.range(0.0, 10.0), "Standard-Lautstärke",
            SettingCategory.SOUNDS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Double> soundDefaultPitch = register(
            "sounds.default-pitch", SettingType.DOUBLE, 1.0,
            Validator.range(0.1, 2.0), "Standard-Tonhöhe",
            SettingCategory.SOUNDS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> soundCountdown = registerSound(
            "sounds.countdown", "BLOCK_NOTE_BLOCK_PLING", "Countdown-Tick");
    public final SettingDefinition<String> soundGameStart = registerSound(
            "sounds.game-start", "ENTITY_ENDER_DRAGON_GROWL", "Spielstart");
    public final SettingDefinition<String> soundKill = registerSound(
            "sounds.kill", "ENTITY_LIGHTNING_BOLT_THUNDER", "Kill");
    public final SettingDefinition<String> soundElimination = registerSound(
            "sounds.elimination", "ENTITY_WITHER_HURT", "Eliminierung");
    public final SettingDefinition<String> soundSupplyDropWarning = registerSound(
            "sounds.supply-drop-warning", "custom.halt", "Supply-Drop-Warnung");
    public final SettingDefinition<String> soundSupplyDropLanded = registerSound(
            "sounds.supply-drop-landed", "ENTITY_FIREWORK_ROCKET_LARGE_BLAST", "Supply-Drop-Landung");
    public final SettingDefinition<String> soundSponsorTokenEarned = registerSound(
            "sounds.sponsor-token-earned", "ENTITY_EXPERIENCE_ORB_PICKUP", "Sponsor-Token erhalten");
    public final SettingDefinition<String> soundSponsorBeaconSpawn = registerSound(
            "sounds.sponsor-beacon-spawn", "BLOCK_BEACON_ACTIVATE", "Sponsor-Beacon-Spawn");
    public final SettingDefinition<String> soundSponsorPurchaseSuccess = registerSound(
            "sounds.sponsor-purchase-success", "ENTITY_PLAYER_LEVELUP", "Sponsor-Kauf erfolgreich");
    public final SettingDefinition<String> soundSponsorPurchaseFailed = registerSound(
            "sounds.sponsor-purchase-failed", "BLOCK_NOTE_BLOCK_BASS", "Sponsor-Kauf fehlgeschlagen");
    public final SettingDefinition<String> soundDeathmatchWarning = registerSound(
            "sounds.deathmatch-warning", "custom.halt", "Deathmatch-Warnung");
    public final SettingDefinition<String> soundDeathmatchStart = registerSound(
            "sounds.deathmatch-start", "ENTITY_WITHER_SPAWN", "Deathmatch-Start");
    public final SettingDefinition<String> soundVictory = registerSound(
            "sounds.victory", "UI_TOAST_CHALLENGE_COMPLETE", "Sieg");

    // Sounds der Custom-Items (im Menü konfigurierbar wie alle anderen)
    public final SettingDefinition<String> soundItemSmokeBomb = registerSound(
            "sounds.item-smoke-bomb", "ENTITY_TNT_PRIMED~1.4; BLOCK_FIRE_EXTINGUISH~0.8", "Item: Rauchbombe");
    public final SettingDefinition<String> soundItemMedikit = registerSound(
            "sounds.item-medikit", "ITEM_TOTEM_USE~1.4; ENTITY_PLAYER_LEVELUP~1.6", "Item: Medikit");
    public final SettingDefinition<String> soundItemHermes = registerSound(
            "sounds.item-hermes-boots", "ENTITY_PHANTOM_FLAP~1.6", "Item: Hermes' Stiefel");
    public final SettingDefinition<String> soundItemHermesWarning = registerSound(
            "sounds.item-hermes-warning", "BLOCK_NOTE_BLOCK_PLING~1.6",
            "Item: Hermes' Stiefel — Warnsound (Flug endet gleich)");
    public final SettingDefinition<String> soundItemKrueckauThrow = registerSound(
            "sounds.item-krueckau-throw", "ENTITY_SPLASH_POTION_THROW", "Item: Krückauwasser werfen");
    public final SettingDefinition<String> soundItemKrueckauImpact = registerSound(
            "sounds.item-krueckau-impact", "ENTITY_SPLASH_POTION_BREAK", "Item: Krückauwasser Aufprall");
    public final SettingDefinition<String> soundItemFiendfinder = registerSound(
            "sounds.item-fiendfinder", "BLOCK_BEACON_ACTIVATE~2.0; ENTITY_ENDER_EYE_DEATH~0.5",
            "Item: Fiendfinder");
    public final SettingDefinition<String> soundItemAura = registerSound(
            "sounds.item-aura", "BLOCK_BEACON_POWER_SELECT; BLOCK_CONDUIT_ACTIVATE", "Item: Aura");
    public final SettingDefinition<String> soundItemGrappling = registerSound(
            "sounds.item-grappling", "ENTITY_FISHING_BOBBER_THROW; ENTITY_ENDER_DRAGON_FLAP", "Item: Enterhaken");
    public final SettingDefinition<String> soundItemRepulse = registerSound(
            "sounds.item-repulse", "ENTITY_WIND_CHARGE_WIND_BURST; ITEM_MACE_SMASH_AIR", "Item: Druckwelle");
    public final SettingDefinition<String> soundItemFeast = registerSound(
            "sounds.item-feast", "ENTITY_PLAYER_BURP; ENTITY_VILLAGER_CELEBRATE", "Item: Festmahl");
    public final SettingDefinition<String> soundItemWarKit = registerSound(
            "sounds.item-war-kit", "ITEM_ARMOR_EQUIP_IRON~0.8; BLOCK_ANVIL_USE~1.4", "Item: Kriegsausrüstung");
    public final SettingDefinition<String> soundItemLeap = registerSound(
            "sounds.item-leap", "ENTITY_SLIME_JUMP; ENTITY_FIREWORK_ROCKET_LAUNCH", "Item: Sprungfeder");
    public final SettingDefinition<String> soundItemStupidness = registerSound(
            "sounds.item-stupidness-protector", "ITEM_TOTEM_USE", "Item: Trottel-Schutz");
    public final SettingDefinition<String> soundItemExmatrikulator = registerSound(
            "sounds.item-exmatrikulator", "ENTITY_LIGHTNING_BOLT_THUNDER~0.8; BLOCK_BEACON_POWER_SELECT",
            "Item: Exmatrikulator (Aktivierung)");

    // Startsequenz + Kanonenschuss (leerer Wert = aus)
    public final SettingDefinition<String> soundStartupLaunch = registerSound(
            "sounds.startup-launch", "ENTITY_FIREWORK_ROCKET_LAUNCH", "Startsequenz: Abschuss aus der Röhre");
    public final SettingDefinition<String> soundStartupUnderground = registerSound(
            "sounds.startup-underground", "hgpack:countdown",
            "Startsequenz: Ambiance im unterirdischen Warteraum, direkt nach dem TP dorthin "
                    + "(braucht das hgpack-Resourcepack für den Countdown-Song)");
    public final SettingDefinition<String> soundStartupArrive = registerSound(
            "sounds.startup-arrive", "BLOCK_STONE_PLACE", "Startsequenz: Ankunft auf der Plattform");
    public final SettingDefinition<String> soundStartupLamp = registerSound(
            "sounds.startup-lamp", "BLOCK_NOTE_BLOCK_BASS~0.5", "Startsequenz: Lampen-Ring");
    public final SettingDefinition<String> soundCannon = registerSound(
            "sounds.cannon",
            "ITEM_TOTEM_USE; ENTITY_GENERIC_EXPLODE@0.8~0.5; ENTITY_GENERIC_EXPLODE@0.4~0.4>200",
            "Kanonenschuss (bei jeder Eliminierung)");

    // === EFFECTS (Partikel) ===
    public final SettingDefinition<Boolean> effectsEnabled = register(
            "effects.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Zentrales Effekt-System (Partikel) aktivieren",
            SettingCategory.EFFECTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> effectGameStart = registerEffect(
            "effects.game-start", "FIREWORK@60~0.8; FLAME@30~0.5", "Spielstart");
    public final SettingDefinition<String> effectKill = registerEffect(
            "effects.kill", "DUST@40~0.5#ff2020; CRIT@30~0.5", "Kill");
    public final SettingDefinition<String> effectElimination = registerEffect(
            "effects.elimination", "LARGE_SMOKE@40~0.5; SOUL@25~0.4", "Eliminierung (am Opfer)");
    public final SettingDefinition<String> effectVictory = registerEffect(
            "effects.victory", "TOTEM_OF_UNDYING@80~1.0; FIREWORK@40~0.8", "Sieg");
    public final SettingDefinition<String> effectSupplyDrop = registerEffect(
            "effects.supply-drop", "END_ROD@50~0.3; HAPPY_VILLAGER@20~0.5", "Supply-Drop-Landung");
    public final SettingDefinition<String> effectDeathmatchStart = registerEffect(
            "effects.deathmatch-start", "SOUL_FIRE_FLAME@60~0.8; DUST@40~0.6#aa00ff", "Deathmatch-Start");
    public final SettingDefinition<String> effectSponsorBeaconSpawn = registerEffect(
            "effects.sponsor-beacon-spawn", "GLOW@40~0.4; END_ROD@30~0.3", "Sponsor-Beacon-Spawn");

    // Effekte der Custom-Items (im Menü konfigurierbar wie alle anderen)
    public final SettingDefinition<String> effectItemSmokeBomb = registerEffect(
            "effects.item-smoke-bomb", "LARGE_SMOKE@140~2.2; CAMPFIRE_COSY_SMOKE@60~2.0", "Item: Rauchbombe");
    public final SettingDefinition<String> effectItemMedikit = registerEffect(
            "effects.item-medikit", "HEART@20~0.5", "Item: Medikit");
    public final SettingDefinition<String> effectItemHermes = registerEffect(
            "effects.item-hermes-boots", "CLOUD@30~0.4", "Item: Hermes' Stiefel");
    public final SettingDefinition<String> effectItemLightning = registerEffect(
            "effects.item-lightning", "ELECTRIC_SPARK@40~0.6; FLASH@2", "Item: Blitzschlag");
    public final SettingDefinition<String> effectItemKrueckauImpact = registerEffect(
            "effects.item-krueckau-impact", "LARGE_SMOKE@60~1.5; ITEM_SLIME@40~1.5", "Item: Krückauwasser");
    public final SettingDefinition<String> effectItemAura = registerEffect(
            "effects.item-aura", "ENCHANT@10~1.0; END_ROD@4~0.6", "Item: Aura");
    public final SettingDefinition<String> effectItemRepulse = registerEffect(
            "effects.item-repulse", "GUST@1; SWEEP_ATTACK@8~1.0", "Item: Druckwelle");
    public final SettingDefinition<String> effectItemFeast = registerEffect(
            "effects.item-feast", "HAPPY_VILLAGER@25~0.6", "Item: Festmahl");
    public final SettingDefinition<String> effectItemLeap = registerEffect(
            "effects.item-leap", "CLOUD@20~0.3; FIREWORK@15~0.3", "Item: Sprungfeder");
    public final SettingDefinition<String> effectItemStupidness = registerEffect(
            "effects.item-stupidness-protector", "TOTEM_OF_UNDYING@60~1.0", "Item: Trottel-Schutz");
    public final SettingDefinition<String> effectItemExmatrikulator = registerEffect(
            "effects.item-exmatrikulator", "ELECTRIC_SPARK@30~1.2; FLASH@1", "Item: Exmatrikulator");

    // Startsequenz-Partikel (leerer Wert = aus)
    public final SettingDefinition<String> effectStartupLaunch = registerEffect(
            "effects.startup-launch", "FLAME@25~0.3; CLOUD@15~0.3", "Startsequenz: Abschuss aus der Röhre");
    public final SettingDefinition<String> effectStartupArrive = registerEffect(
            "effects.startup-arrive", "CLOUD@20~0.4", "Startsequenz: Ankunft auf der Plattform");
    public final SettingDefinition<String> effectCannon = registerEffect(
            "effects.cannon", "", "Kanonenschuss-Partikel (global, Standard aus)");

    // === ANNOUNCEMENTS ===
    public final SettingDefinition<Boolean> announcementsEnabled = register(
            "announcements.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Zentrales Ansage-System aktivieren",
            SettingCategory.ANNOUNCEMENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> announceUseTitle = register(
            "announcements.use-title", SettingType.BOOLEAN, true,
            Validator.any(), "Wichtige Ansagen zusätzlich als Title zeigen",
            SettingCategory.ANNOUNCEMENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> announceUseActionbar = register(
            "announcements.use-actionbar", SettingType.BOOLEAN, true,
            Validator.any(), "Ansagen zusätzlich in der Actionbar zeigen",
            SettingCategory.ANNOUNCEMENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> announceUseChat = register(
            "announcements.use-chat", SettingType.BOOLEAN, true,
            Validator.any(), "Ansagen im Chat zeigen",
            SettingCategory.ANNOUNCEMENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> announceKillfeed = register(
            "announcements.killfeed-enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Killfeed-Ansagen aktivieren",
            SettingCategory.ANNOUNCEMENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> announceRemainingPlayers = register(
            "announcements.remaining-players-enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Ansage bei Unterschreiten der Tribut-Schwellen",
            SettingCategory.ANNOUNCEMENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<List<String>> announceRemainingThresholds = register(
            "announcements.remaining-players-thresholds", SettingType.STRING_LIST,
            List.of("10", "5", "3", "2"), intList(),
            "Schwellen für die Verbleibende-Tribute-Ansage",
            SettingCategory.ANNOUNCEMENTS, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> textGameStart = registerText(
            "announcements.text.game-start",
            "§c⚔ Die Hungerspiele haben begonnen — möge das Glück stets mit euch sein!",
            "Spielstart");
    public final SettingDefinition<String> textCountdown = registerText(
            "announcements.text.countdown",
            "§eDie Spiele beginnen in §c{seconds}§e Sekunden!",
            "Countdown ({seconds})");
    public final SettingDefinition<String> textGraceEnd = registerText(
            "announcements.text.grace-end",
            "§c⚔ Die Schutzfrist ist vorbei — die Hungerspiele haben wahrhaft begonnen!",
            "Ende der Schutzfrist");
    public final SettingDefinition<String> textKill = registerText(
            "announcements.text.kill",
            "§c☠ {victim} §7wurde von §c{killer} §7erledigt. §8({alive} Tribute verbleiben)",
            "Kill ({victim}, {killer}, {alive})");
    public final SettingDefinition<String> textElimination = registerText(
            "announcements.text.elimination",
            "§c☠ {victim} §7ist ausgeschieden. §8({alive} Tribute verbleiben)",
            "Eliminierung ohne Killer ({victim}, {alive})");
    public final SettingDefinition<String> textSupplyDropWarning = registerText(
            "announcements.text.supply-drop-warning",
            "§6✈ Eine Kapitol-Lieferung ist unterwegs! Landung in {seconds}s{coords}.",
            "Supply-Drop-Warnung ({seconds}, {coords})");
    public final SettingDefinition<String> textSupplyDropLanded = registerText(
            "announcements.text.supply-drop-landed",
            "§6✈ Die Kapitol-Lieferung ist gelandet{coords}!",
            "Supply-Drop-Landung ({coords})");
    public final SettingDefinition<String> textSponsorTokenEarned = registerText(
            "announcements.text.sponsor-token-earned",
            "§6★ Ein Sponsor glaubt an dich: §e+{amount} Sponsor Token§6!",
            "Sponsor-Token erhalten ({amount})");
    public final SettingDefinition<String> textSponsorBeaconSpawned = registerText(
            "announcements.text.sponsor-beacon-spawned",
            "§b◆ Ein Sponsor-Beacon ist erschienen{coords}!",
            "Sponsor-Beacon gespawnt ({coords})");
    public final SettingDefinition<String> textSponsorPurchase = registerText(
            "announcements.text.sponsor-purchase",
            "§a✔ Kauf erfolgreich: §f{item} §7(-{cost} Token)",
            "Sponsor-Kauf erfolgreich ({item}, {cost})");
    public final SettingDefinition<String> textSponsorNotEnough = registerText(
            "announcements.text.sponsor-not-enough",
            "§cNicht genug Sponsor Tokens — du brauchst {cost}.",
            "Zu wenig Tokens ({cost})");
    public final SettingDefinition<String> textSponsorShopDisabled = registerText(
            "announcements.text.sponsor-shop-disabled",
            "§cDer Sponsor-Shop ist derzeit deaktiviert.",
            "Shop deaktiviert");
    public final SettingDefinition<String> textSponsorBeaconDisabled = registerText(
            "announcements.text.sponsor-beacon-disabled",
            "§cSponsor-Beacons sind derzeit deaktiviert.",
            "Beacons deaktiviert");
    public final SettingDefinition<String> textDeathmatchWarning = registerText(
            "announcements.text.deathmatch-warning",
            "§4⚔ DEATHMATCH in {seconds} Sekunden — die Border schrumpft auf {size} Blöcke!",
            "Deathmatch-Warnung ({seconds}, {size})");
    public final SettingDefinition<String> textDeathmatchStart = registerText(
            "announcements.text.deathmatch-start",
            "§4⚔ DAS DEATHMATCH HAT BEGONNEN!",
            "Deathmatch-Start");
    public final SettingDefinition<String> textWinner = registerText(
            "announcements.text.winner",
            "§6👑 SIEGER DER HUNGERSPIELE: §a{winner}",
            "Gewinner ({winner})");
    public final SettingDefinition<String> textRemainingPlayers = registerText(
            "announcements.text.remaining-players",
            "§eNur noch §c{alive}§e Tribute am Leben!",
            "Verbleibende Tribute ({alive})");

    // === API ===
    public final SettingDefinition<Boolean> apiEnabled = register(
            "api.enabled", SettingType.BOOLEAN, false,
            Validator.any(), "HTTP-API aktivieren (nur in geschlossenen Netzen exposen!)",
            SettingCategory.API, ApplyTiming.RESTART);

    public final SettingDefinition<String> apiBindAddress = register(
            "api.bind-address", SettingType.STRING, "127.0.0.1",
            Validator.custom(v -> v.isBlank()
                    ? java.util.Optional.of("Bind-Adresse darf nicht leer sein")
                    : java.util.Optional.empty()),
            "Bind-Adresse: 127.0.0.1 = nur lokal, 0.0.0.0 = alle Interfaces",
            SettingCategory.API, ApplyTiming.RESTART);

    public final SettingDefinition<Integer> apiPort = register(
            "api.port", SettingType.INT, 8567,
            Validator.range(1024, 65535), "TCP-Port der HTTP-API",
            SettingCategory.API, ApplyTiming.RESTART);

    public final SettingDefinition<String> apiKey = register(
            "api.key", SettingType.STRING, "",
            Validator.any(),
            "API-Schlüssel (X-API-Key-Header); leer = wird beim Start generiert",
            SettingCategory.API, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> apiReadOnly = register(
            "api.read-only", SettingType.BOOLEAN, false,
            Validator.any(), "Nur lesende Endpunkte erlauben (POST wird abgelehnt)",
            SettingCategory.API, ApplyTiming.IMMEDIATE);

    // === RESOURCEPACK ===
    public final SettingDefinition<Boolean> resourcepackEnabled = register(
            "resourcepack.enabled", SettingType.BOOLEAN, true,
            Validator.any(), "Custom-Resourcepack (Item-Texturen, Countdown-Song) an Spieler senden",
            SettingCategory.RESOURCEPACK, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> resourcepackUrl = register(
            "resourcepack.url", SettingType.STRING,
            "https://mc-packs.raindancer118.de/hgpack/hgpack.zip",
            Validator.any(), "Download-URL des Resourcepacks",
            SettingCategory.RESOURCEPACK, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> resourcepackSha1 = register(
            "resourcepack.sha1", SettingType.STRING,
            "01c3a8e27f2db78aa5c0286e68925235574c54b5",
            Validator.custom(v -> v.matches("(?i)[0-9a-f]{40}")
                    ? java.util.Optional.empty()
                    : java.util.Optional.of("Muss ein 40-stelliger SHA1-Hex-Hash sein")),
            "SHA1-Hash der Resourcepack-Zip (siehe sha1.txt beim Hosting-Build)",
            SettingCategory.RESOURCEPACK, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<Boolean> resourcepackRequired = register(
            "resourcepack.required", SettingType.BOOLEAN, true,
            Validator.any(), "Ablehnen des Packs kickt den Spieler (streng)",
            SettingCategory.RESOURCEPACK, ApplyTiming.IMMEDIATE);

    public final SettingDefinition<String> resourcepackPrompt = register(
            "resourcepack.prompt", SettingType.STRING,
            "The Hunger Games — Item-Texturen & Sounds",
            Validator.any(), "Hinweistext im Client-Resourcepack-Dialog",
            SettingCategory.RESOURCEPACK, ApplyTiming.IMMEDIATE);

    // ==================== Validator-Helfer ====================

    /** Jeder Eintrag muss eine parsebare Dauer sein (z. B. 20m, 90s, 1h30m). */
    private static Validator<List<String>> durationList() {
        return Validator.custom(list -> {
            for (String entry : list) {
                try {
                    SettingType.DURATION.parse(entry);
                } catch (IllegalArgumentException e) {
                    return java.util.Optional.of("\"" + entry + "\" ist keine gültige Dauer");
                }
            }
            return java.util.Optional.empty();
        });
    }

    /** Jeder Eintrag muss eine ganze Zahl sein. */
    private static Validator<List<String>> intList() {
        return Validator.custom(list -> {
            for (String entry : list) {
                try {
                    Integer.parseInt(entry.trim());
                } catch (NumberFormatException e) {
                    return java.util.Optional.of("\"" + entry + "\" ist keine ganze Zahl");
                }
            }
            return java.util.Optional.empty();
        });
    }

    /** Jeder Eintrag muss eine gültige {@link GamePhase} sein. */
    private static Validator<List<String>> phaseList() {
        return Validator.custom(list -> {
            for (String entry : list) {
                try {
                    GamePhase.valueOf(entry.trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return java.util.Optional.of("\"" + entry + "\" ist keine gültige Phase");
                }
            }
            return java.util.Optional.empty();
        });
    }

    /** Wert muss ein gültiger Bukkit-{@link org.bukkit.Difficulty}-Name sein. */
    private static Validator<String> difficultyValidator() {
        return Validator.custom(value -> {
            try {
                org.bukkit.Difficulty.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
                return java.util.Optional.empty();
            } catch (IllegalArgumentException e) {
                return java.util.Optional.of("\"" + value + "\" ist keine Difficulty"
                        + " (PEACEFUL, EASY, NORMAL, HARD)");
            }
        });
    }

    /** Wert muss ein existierendes Bukkit-Material sein. */
    private static Validator<String> material() {
        return Validator.custom(value -> org.bukkit.Material.matchMaterial(value) != null
                ? java.util.Optional.empty()
                : java.util.Optional.of("\"" + value + "\" ist kein bekanntes Material"));
    }

    private SettingDefinition<String> registerSound(String key, String defaultValue, String label) {
        return register(key, SettingType.STRING, defaultValue,
                Validator.custom(com.hungergames.platform.sound.SoundService::validateSoundKey),
                "Sound: " + label + " (Bukkit-Name oder Resource-Pack-Key wie custom.halt)",
                SettingCategory.SOUNDS, ApplyTiming.IMMEDIATE);
    }

    private SettingDefinition<String> registerEffect(String key, String defaultValue, String label) {
        return register(key, SettingType.STRING, defaultValue,
                Validator.custom(com.hungergames.platform.effect.EffectService::validateEffectSpec),
                "Effekt: " + label + " (Partikel, z. B. FLAME@40~0.6#ff0000)",
                SettingCategory.EFFECTS, ApplyTiming.IMMEDIATE);
    }

    private SettingDefinition<String> registerText(String key, String defaultValue, String label) {
        return register(key, SettingType.STRING, defaultValue,
                Validator.any(), "Ansage-Text: " + label,
                SettingCategory.ANNOUNCEMENTS, ApplyTiming.IMMEDIATE);
    }

    private <T> SettingDefinition<T> register(String key, SettingType<T> type, T defaultValue,
                                              SettingDefinition.Validator<T> validator, String description,
                                              SettingCategory category, ApplyTiming timing) {
        return registry.register(new SettingDefinition<>(key, type, defaultValue, validator,
                description, category, timing));
    }
}
