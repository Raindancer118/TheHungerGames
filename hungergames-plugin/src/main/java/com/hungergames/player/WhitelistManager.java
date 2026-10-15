package com.hungergames.player;

import com.hungergames.api.model.Participant;
import com.hungergames.api.model.TeamId;
import com.hungergames.game.GameSession;
import com.hungergames.platform.NameResolver;
import com.hungergames.utils.Sender;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Legacy-Fassade über der {@link GameSession} für die verbliebenen
 * namensbasierten Aufrufer (Stage-Runner, Commands).
 *
 * <p>Die frühere Doppel-Wahrheit (dead per Name in whitelist.yml, eliminated
 * per UUID im RAM) ist beseitigt: einzige Quelle ist die Session. Diese
 * Klasse übersetzt nur noch Namen ↔ UUIDs und migriert beim ersten Start das
 * alte whitelist.yml-Format.</p>
 */
public final class WhitelistManager {

    private static WhitelistManager instance;

    private final Plugin plugin;
    private final GameSession session;
    private final NameResolver nameResolver;
    private final File legacyWhitelistFile;

    private WhitelistManager(Plugin plugin, GameSession session, NameResolver nameResolver) {
        this.plugin = plugin;
        this.session = session;
        this.nameResolver = nameResolver;
        this.legacyWhitelistFile = new File(plugin.getDataFolder(), "whitelist.yml");
    }

    /** Wird beim Plugin-Start verdrahtet. */
    public static void init(Plugin plugin, GameSession session, NameResolver nameResolver) {
        instance = new WhitelistManager(plugin, session, nameResolver);
    }

    public static WhitelistManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("WhitelistManager wurde nicht initialisiert");
        }
        return instance;
    }

    // ==================== Namens-Lookups ====================

    public boolean isWhitelisted(String playerName) {
        return findByName(playerName).isPresent();
    }

    /** @return Legacy-Teamnummer ({@code team-<n>}), oder -1 wenn ohne Team. */
    public int getTeam(String playerName) {
        return findByName(playerName)
                .flatMap(Participant::teamId)
                .map(WhitelistManager::legacyTeamNumber)
                .orElse(-1);
    }

    public List<String> getPlayersInTeam(int teamNumber) {
        return session.teamRegistry().all().stream()
                .filter(team -> legacyTeamNumber(team.id()) == teamNumber)
                .flatMap(team -> team.members().stream())
                .map(uuid -> session.participants().nameOf(uuid).orElse(uuid.toString()))
                .toList();
    }

    /** @return Spieler sortiert nach Teams (Teampartner nebeneinander), Teamlose zuletzt. */
    public List<String> getPlayersSortedByTeam() {
        List<Participant> all = new ArrayList<>(session.participants().all());
        all.sort(Comparator.comparing(p -> p.teamId().map(TeamId::value).orElse("zzz-none")));
        return all.stream().map(Participant::lastKnownName).toList();
    }

    public String getLivingTeammate(String playerName) {
        return findByName(playerName)
                .flatMap(participant -> session.teamRegistry().teamOf(participant.uuid())
                        .flatMap(team -> team.members().stream()
                                .filter(member -> !member.equals(participant.uuid()))
                                .filter(session.participants()::isAlive)
                                .findFirst()))
                .flatMap(session.participants()::nameOf)
                .orElse(null);
    }

    public boolean isDead(String playerName) {
        return findByName(playerName)
                .map(participant -> !participant.isAlive())
                .orElse(false);
    }

    public void markAsDead(String playerName) {
        findByName(playerName).ifPresent(participant ->
                session.eliminate(participant.uuid(), null));
    }

    public void clearAllDeadStatus() {
        for (UUID eliminated : session.participants().eliminated()) {
            session.revive(eliminated);
        }
    }

    public int getPlayerCount() {
        return session.participants().all().size();
    }

    public int getAlivePlayerCount() {
        return session.participants().aliveCount();
    }

    // ==================== Server-Whitelist ====================

    /** Überträgt die Turnier-Whitelist auf die Server-Whitelist. */
    public void applyToServerWhitelist() {
        Bukkit.setWhitelist(true);
        int added = 0;
        for (Participant participant : session.participants().all()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(participant.uuid());
            if (!offline.isWhitelisted()) {
                offline.setWhitelisted(true);
                added++;
            }
        }
        Sender.log("Server-Whitelist aktualisiert (" + added + " Spieler ergänzt)");
    }

    /** Leert die Server-Whitelist vollständig. */
    public void clearServerWhitelist() {
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            player.setWhitelisted(false);
        }
    }

    /** Pre-Init-Modus: nur konfigurierte Admins dürfen joinen. */
    public void setupPreInitWhitelist(List<String> adminNames) {
        clearServerWhitelist();
        Bukkit.setWhitelist(true);
        for (String adminName : adminNames) {
            nameResolver.resolve(adminName).whenComplete((resolved, error) -> {
                if (resolved != null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            Bukkit.getOfflinePlayer(resolved.uuid()).setWhitelisted(true));
                } else {
                    Sender.warning("Pre-Init-Admin '" + adminName + "' nicht auflösbar: "
                            + (error != null ? error.getMessage() : "unbekannt"));
                }
            });
        }
        Sender.log("Server-Whitelist im Pre-Init-Modus (" + adminNames.size() + " Admins)");
    }

    /**
     * Fügt einen Spieler zur Server-Whitelist hinzu.
     * Vergibt — anders als v0.16 — kein OP mehr (Sicherheitsentscheidung).
     */
    public void allowPlayer(String playerName) {
        nameResolver.resolve(playerName).thenAccept(resolved ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.getOfflinePlayer(resolved.uuid()).setWhitelisted(true)));
    }

    // ==================== Migration whitelist.yml v1 ====================

    /**
     * Migriert das alte whitelist.yml-Format (Name/Team-Nummer/dead-Flag) in
     * die Session. Nicht auflösbare Namen werden gemeldet, nicht verworfen.
     */
    public void migrateLegacyFile() {
        if (!legacyWhitelistFile.exists() || !session.participants().all().isEmpty()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyWhitelistFile);
        List<?> players = yaml.getList("players");
        if (players == null || players.isEmpty()) {
            return;
        }
        Sender.log("Migriere " + players.size() + " Einträge aus whitelist.yml (v1) ...");

        for (Object entry : players) {
            String name;
            int team = 0;
            boolean dead = false;
            if (entry instanceof Map<?, ?> map) {
                name = String.valueOf(map.get("name"));
                Object teamValue = map.get("team");
                team = teamValue instanceof Number number ? number.intValue() : 0;
                dead = Boolean.TRUE.equals(map.get("dead"));
            } else if (entry instanceof String s) {
                name = s;
            } else {
                continue;
            }

            final int teamNumber = team;
            final boolean wasDead = dead;
            nameResolver.resolve(name).whenComplete((resolved, error) -> {
                if (resolved == null) {
                    Sender.warning("whitelist.yml: '" + name + "' konnte nicht zu einer UUID "
                            + "aufgelöst werden — Eintrag bitte manuell prüfen");
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    session.whitelistAdd(resolved.uuid(), resolved.name());
                    if (teamNumber > 0) {
                        ensureLegacyTeam(teamNumber);
                        session.teamAssign(resolved.uuid(), new TeamId("team-" + teamNumber),
                                com.hungergames.game.port.GameEventPublisher.MembershipCause.ADMIN);
                    }
                    if (wasDead) {
                        session.participants().eliminate(resolved.uuid());
                    }
                });
            });
        }
    }

    private void ensureLegacyTeam(int number) {
        TeamId id = new TeamId("team-" + number);
        if (session.teamRegistry().team(id).isEmpty()) {
            session.teamCreate("Team " + number, null);
        }
    }

    // ==================== intern ====================

    private Optional<Participant> findByName(String playerName) {
        if (playerName == null) {
            return Optional.empty();
        }
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && session.isWhitelisted(online.getUniqueId())) {
            return session.participants().get(online.getUniqueId());
        }
        return session.participants().all().stream()
                .filter(participant -> participant.lastKnownName().equalsIgnoreCase(playerName))
                .findFirst();
    }

    /** {@code team-<n>} → n; andere TeamIds erhalten eine stabile positive Pseudo-Nummer. */
    private static int legacyTeamNumber(TeamId id) {
        String value = id.value();
        if (value.startsWith("team-")) {
            try {
                return Integer.parseInt(value.substring(5));
            } catch (NumberFormatException ignored) {
                // kein numerisches Legacy-Team
            }
        }
        return Math.abs(value.hashCode() % 100_000) + 1000;
    }
}
