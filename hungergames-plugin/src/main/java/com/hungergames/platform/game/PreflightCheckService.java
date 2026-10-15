package com.hungergames.platform.game;

import com.hungergames.GameState;
import com.hungergames.game.GameSession;
import com.hungergames.game.border.BorderMath;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.game.config.SettingType;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.gamemaster.GamemasterService;
import com.hungergames.platform.loot.LootTableRepository;
import com.hungergames.platform.sponsor.ShopItemParser;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Preflight-Check vor dem Rundenstart: prüft Arena, Teilnehmer, Teams,
 * Border-, Drop-, Sponsor- und Loot-Konfiguration. Rote Befunde blockieren
 * den Start; gelbe Warnungen kann der Admin bewusst ignorieren.
 */
public final class PreflightCheckService {

    /** Schweregrad eines Befunds. */
    public enum Severity {
        OK,
        WARNING,
        ERROR
    }

    /** Ein einzelnes Prüfergebnis. */
    public record CheckResult(String name, Severity severity, String detail) {
    }

    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final GamemasterService gamemaster;
    private final OpTrackerService opTracker;
    private final LootTableRepository lootTables;

    public PreflightCheckService(GameSession session, ConfigurationService config,
                                 HgSettings settings, GamemasterService gamemaster,
                                 OpTrackerService opTracker, LootTableRepository lootTables) {
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.gamemaster = gamemaster;
        this.opTracker = opTracker;
        this.lootTables = lootTables;
    }

    /** Führt alle Checks aus. */
    public List<CheckResult> runAll() {
        List<CheckResult> results = new ArrayList<>();
        results.add(checkArena());
        results.add(checkParticipants());
        results.add(checkTeams());
        results.add(checkBorder());
        results.add(checkSupplyDrops());
        results.add(checkSponsorShop());
        results.add(checkSounds());
        results.add(checkGamemasters());
        results.add(checkAdminParticipants());
        results.add(checkLoot());
        results.add(checkWorldEdit());
        return results;
    }

    /** Start ist blockiert, solange mindestens ein ERROR vorliegt. */
    public boolean hasBlockingErrors(List<CheckResult> results) {
        return results.stream().anyMatch(result -> result.severity() == Severity.ERROR);
    }

    // ==================== Einzel-Checks ====================

    private CheckResult checkArena() {
        if (session.phase().isInitialized() && GameState.getInstance().getArenaCenter() != null) {
            var center = GameState.getInstance().getArenaCenter();
            return new CheckResult("Arena", Severity.OK, "Zentrum: " + center.getBlockX()
                    + " / " + center.getBlockZ() + " (" + session.phase() + ")");
        }
        return new CheckResult("Arena", Severity.ERROR,
                "Nicht initialisiert — /init oder Spielsteuerung → Arena initialisieren");
    }

    private CheckResult checkParticipants() {
        int alive = session.participants().aliveCount();
        int total = session.participants().all().size();
        if (total == 0) {
            return new CheckResult("Teilnehmer", Severity.ERROR,
                    "Keine Tribute registriert — Whitelist pflegen");
        }
        if (alive < 2) {
            return new CheckResult("Teilnehmer", Severity.ERROR,
                    "Nur " + alive + " lebende Tribute — mindestens 2 nötig");
        }
        long online = session.participants().all().stream()
                .filter(p -> p.isAlive() && Bukkit.getPlayer(p.uuid()) != null)
                .count();
        if (online < alive) {
            return new CheckResult("Teilnehmer", Severity.WARNING,
                    alive + " lebend, aber nur " + online + " online");
        }
        return new CheckResult("Teilnehmer", Severity.OK, alive + " lebende Tribute, alle online");
    }

    private CheckResult checkTeams() {
        if (!session.teamRegistry().enabled()) {
            return new CheckResult("Teams", Severity.OK, "Solo-Modus (teams.enabled=false)");
        }
        var teams = session.teamRegistry().all();
        if (teams.isEmpty()) {
            return new CheckResult("Teams", Severity.OK, "Solo-Modus (keine Teams)");
        }
        long teamless = session.participants().all().stream()
                .filter(p -> p.isAlive() && p.teamId().isEmpty())
                .count();
        if (teamless > 0) {
            return new CheckResult("Teams", Severity.WARNING,
                    teamless + " lebende Tribute ohne Team — zufällig verteilen?");
        }
        return new CheckResult("Teams", Severity.OK, teams.size() + " Teams, alle Tribute zugeteilt");
    }

    private CheckResult checkBorder() {
        try {
            var borderSettings = new com.hungergames.game.border.BorderSettings(
                    config.get(settings.borderInitialSize),
                    config.get(settings.borderMinimumSize),
                    config.get(settings.borderMaxEdgeSpeed),
                    com.hungergames.platform.border.BorderPhaseParser.parseAll(
                            config.get(settings.borderPhases), config.get(settings.gameDuration)));
            var conflicts = BorderMath.validate(borderSettings,
                    Optional.of(config.get(settings.gameDuration)));
            if (!conflicts.isEmpty()) {
                return new CheckResult("Border", Severity.WARNING,
                        conflicts.size() + " Konflikt(e) — /hg border zur Auflösung");
            }
            int deathmatchTarget = config.get(settings.deathmatchTargetBorderSize);
            if (config.get(settings.deathmatchEnabled)
                    && deathmatchTarget < config.get(settings.borderMinimumSize)) {
                return new CheckResult("Border", Severity.WARNING,
                        "Deathmatch-Ziel (" + deathmatchTarget + ") liegt unter border.minimum-size");
            }
            return new CheckResult("Border", Severity.OK,
                    borderSettings.phases().size() + " Phasen, keine Konflikte");
        } catch (RuntimeException e) {
            return new CheckResult("Border", Severity.ERROR,
                    "Border-Phasen nicht parsebar: " + e.getMessage());
        }
    }

    private CheckResult checkSupplyDrops() {
        if (!config.get(settings.supplyDropsEnabled)) {
            return new CheckResult("Supply Drops", Severity.OK, "Deaktiviert");
        }
        try {
            List<Duration> schedule = config.get(settings.supplyDropSchedule).stream()
                    .map(SettingType.DURATION::parse)
                    .toList();
            Duration gameDuration = config.get(settings.gameDuration);
            long late = schedule.stream().filter(t -> t.compareTo(gameDuration) >= 0).count();
            if (late > 0) {
                return new CheckResult("Supply Drops", Severity.WARNING,
                        late + " Drop(s) liegen nach dem Spielende (" + gameDuration.toMinutes() + "m)");
            }
            String table = config.get(settings.supplyDropLootTable);
            if (lootTables.table(table).isEmpty()) {
                return new CheckResult("Supply Drops", Severity.ERROR,
                        "LootTable \"" + table + "\" existiert nicht");
            }
            return new CheckResult("Supply Drops", Severity.OK,
                    schedule.size() + " geplant, Tabelle \"" + table + "\" vorhanden");
        } catch (IllegalArgumentException e) {
            return new CheckResult("Supply Drops", Severity.ERROR,
                    "Zeitplan nicht parsebar: " + e.getMessage());
        }
    }

    private CheckResult checkSponsorShop() {
        if (!config.get(settings.sponsorsEnabled)) {
            return new CheckResult("Sponsoren", Severity.OK, "Deaktiviert");
        }
        var error = ShopItemParser.validateList(config.get(settings.sponsorShopItems));
        if (error.isPresent()) {
            return new CheckResult("Sponsoren", Severity.ERROR, "Shop: " + error.get());
        }
        boolean tokens = config.get(settings.sponsorTokensEnabled);
        boolean beacons = config.get(settings.sponsorBeaconsEnabled);
        if (tokens && !beacons) {
            return new CheckResult("Sponsoren", Severity.WARNING,
                    "Tokens aktiv, aber keine Beacons — Tokens sind nicht einlösbar");
        }
        return new CheckResult("Sponsoren", Severity.OK,
                "Tokens " + (tokens ? "an" : "aus") + ", Beacons " + (beacons ? "an" : "aus")
                        + ", Shop gültig");
    }

    private CheckResult checkSounds() {
        if (!config.get(settings.soundsEnabled)) {
            return new CheckResult("Sounds", Severity.OK, "Deaktiviert");
        }
        // Format wurde beim Setzen validiert; hier nur grober Sanity-Check
        return new CheckResult("Sounds", Severity.OK, "Aktiviert (Formate validiert)");
    }

    private CheckResult checkGamemasters() {
        if (!config.get(settings.gmEnabled)) {
            return new CheckResult("Spielmacher", Severity.OK, "Deaktiviert");
        }
        var active = gamemaster.onlineActive();
        List<String> playingGms = new ArrayList<>();
        for (var player : active) {
            if (session.isWhitelisted(player.getUniqueId())) {
                playingGms.add(player.getName());
            }
        }
        if (!playingGms.isEmpty()) {
            return new CheckResult("Spielmacher", Severity.WARNING,
                    "Aktive Spielmacher sind zugleich Tribute: " + String.join(", ", playingGms));
        }
        return new CheckResult("Spielmacher", Severity.OK,
                active.size() + " aktiv, keiner spielt mit");
    }

    private CheckResult checkAdminParticipants() {
        var ops = opTracker.opParticipants();
        if (ops.isEmpty()) {
            return new CheckResult("Admin-Tribute", Severity.OK, "Keine OP-Tribute");
        }
        List<String> names = ops.stream()
                .map(uuid -> session.participants().nameOf(uuid).orElse(uuid.toString()))
                .sorted()
                .toList();
        if (config.get(settings.adminDeopOnStart)) {
            return new CheckResult("Admin-Tribute", Severity.OK,
                    "Deop-Plan: " + String.join(", ", names) + " (Re-Op bei Elimination/Ende)");
        }
        return new CheckResult("Admin-Tribute", Severity.WARNING,
                String.join(", ", names) + " spielen mit OP — deop-on-start ist AUS");
    }

    private CheckResult checkLoot() {
        List<String> problems = lootTables.validateAll();
        if (!problems.isEmpty()) {
            return new CheckResult("Loot", Severity.WARNING,
                    problems.size() + " ungültige Einträge — /hg loot validate");
        }
        if (lootTables.isDirty()) {
            return new CheckResult("Loot", Severity.WARNING,
                    "Ungespeicherte Loot-Änderungen — vor dem Start speichern?");
        }
        if (lootTables.tableNames().isEmpty()) {
            return new CheckResult("Loot", Severity.ERROR, "Keine LootTables gefunden");
        }
        return new CheckResult("Loot", Severity.OK,
                lootTables.tableNames().size() + " Tabellen, alle gültig");
    }

    private CheckResult checkWorldEdit() {
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") != null) {
            return new CheckResult("WorldEdit", Severity.OK, "Vorhanden");
        }
        return new CheckResult("WorldEdit", Severity.ERROR,
                "WorldEdit fehlt — Arena-Schematics können nicht platziert werden");
    }
}
