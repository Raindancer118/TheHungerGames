package com.hungergames.platform.persistence;

import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.ParticipantState;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.model.Winner;
import com.hungergames.game.SessionSnapshot;
import com.hungergames.game.port.SessionStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Persistiert die Game-Session als {@code session.yml} im Datenordner.
 * Ermöglicht die Wiederaufnahme einer laufenden Runde nach Serverneustart.
 */
public final class YamlSessionStore implements SessionStore {

    private final File file;
    private final Logger logger;

    public YamlSessionStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public void save(SessionSnapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("phase", snapshot.phase().name());
        if (snapshot.runningSinceMillis() != null) {
            yaml.set("running-since", snapshot.runningSinceMillis());
        }

        List<Map<String, Object>> players = new ArrayList<>();
        for (SessionSnapshot.ParticipantData p : snapshot.participants()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", p.uuid().toString());
            entry.put("name", p.name());
            entry.put("state", p.state().name());
            players.add(entry);
        }
        yaml.set("participants", players);

        for (SessionSnapshot.TeamData team : snapshot.teams()) {
            String path = "teams." + team.id().value();
            yaml.set(path + ".name", team.name());
            yaml.set(path + ".color", team.color().name());
            yaml.set(path + ".members", team.members().stream().map(UUID::toString).toList());
            if (team.captain() != null) {
                yaml.set(path + ".captain", team.captain().toString());
            }
        }

        Map<String, Object> kills = new LinkedHashMap<>();
        snapshot.kills().forEach((uuid, count) -> kills.put(uuid.toString(), count));
        yaml.set("kills", kills);

        switch (snapshot.winner()) {
            case Winner.Solo solo -> yaml.set("winner.solo", solo.uuid().toString());
            case Winner.Team team -> {
                yaml.set("winner.team", team.teamId().value());
                yaml.set("winner.members", team.members().stream().map(UUID::toString).toList());
            }
            case Winner.None none -> yaml.set("winner.none", true);
            case null -> {
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.severe("session.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    @Override
    public Optional<SessionSnapshot> load() {
        if (!file.exists()) {
            return Optional.empty();
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            GamePhase phase = GamePhase.valueOf(yaml.getString("phase", "NOT_INITIALIZED"));

            List<SessionSnapshot.ParticipantData> participants = new ArrayList<>();
            for (Map<?, ?> entry : yaml.getMapList("participants")) {
                participants.add(new SessionSnapshot.ParticipantData(
                        UUID.fromString(String.valueOf(entry.get("uuid"))),
                        String.valueOf(entry.get("name")),
                        ParticipantState.valueOf(String.valueOf(entry.get("state")))));
            }

            List<SessionSnapshot.TeamData> teams = new ArrayList<>();
            ConfigurationSection teamsSection = yaml.getConfigurationSection("teams");
            if (teamsSection != null) {
                for (String id : teamsSection.getKeys(false)) {
                    ConfigurationSection t = teamsSection.getConfigurationSection(id);
                    if (t == null) {
                        continue;
                    }
                    List<UUID> members = t.getStringList("members").stream().map(UUID::fromString).toList();
                    String captain = t.getString("captain");
                    teams.add(new SessionSnapshot.TeamData(
                            new TeamId(id),
                            t.getString("name", id),
                            TeamColor.valueOf(t.getString("color", "WHITE")),
                            members,
                            captain == null ? null : UUID.fromString(captain)));
                }
            }

            Map<UUID, Integer> kills = new LinkedHashMap<>();
            ConfigurationSection killsSection = yaml.getConfigurationSection("kills");
            if (killsSection != null) {
                for (String uuid : killsSection.getKeys(false)) {
                    kills.put(UUID.fromString(uuid), killsSection.getInt(uuid));
                }
            }

            Winner winner = null;
            if (yaml.contains("winner.solo")) {
                winner = new Winner.Solo(UUID.fromString(yaml.getString("winner.solo")));
            } else if (yaml.contains("winner.team")) {
                winner = new Winner.Team(new TeamId(yaml.getString("winner.team")),
                        yaml.getStringList("winner.members").stream()
                                .map(UUID::fromString)
                                .collect(java.util.stream.Collectors.toSet()));
            } else if (yaml.getBoolean("winner.none", false)) {
                winner = new Winner.None();
            }

            Long runningSince = yaml.contains("running-since") ? yaml.getLong("running-since") : null;

            return Optional.of(new SessionSnapshot(phase, participants, teams, winner, kills, runningSince));
        } catch (RuntimeException e) {
            logger.severe("session.yml ist beschädigt und wird ignoriert: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void clear() {
        if (file.exists() && !file.delete()) {
            logger.warning("session.yml konnte nicht gelöscht werden");
        }
    }
}
