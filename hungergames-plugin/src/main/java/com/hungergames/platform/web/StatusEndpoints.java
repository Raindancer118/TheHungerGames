package com.hungergames.platform.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Participant;
import com.hungergames.api.model.Team;
import com.hungergames.api.model.Winner;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lese- und Tribut-Endpunkte: Status, Teilnehmer, Rundenlog, Whitelist,
 * Wiederbelebung und manuelle Eliminierung.
 */
final class StatusEndpoints implements ApiRouter.Module {

    /** Obergrenze für {@code GET /api/log?lines=} — schützt vor Riesen-Antworten. */
    private static final int MAX_LOG_LINES = 2000;

    private final ApiSupport support;

    StatusEndpoints(ApiSupport support) {
        this.support = support;
    }

    @Override
    public void register(ApiRouter router) {
        router.get("/api/status", "Phase, lebende Tribute, Deathmatch, Drops, Sieger", this::status);
        router.get("/api/participants", "Alle Tribute mit Status, Team, Kills, Tokens",
                this::participants);
        router.get("/api/participants/{player}", "Einzelner Tribut (UUID oder Name)", this::detail);
        router.get("/api/players", "Alle Online-Spieler mit Position und Modus", this::onlinePlayers);
        router.getAsync("/api/log", "Rundenlog — ?lines=200", this::log);

        router.post("/api/whitelist", "Tribut registrieren — {\"uuid\", \"name\"?}", this::whitelistAdd);
        router.delete("/api/whitelist/{player}", "Tribut aus dem Turnier entfernen",
                this::whitelistRemove);
        router.post("/api/revive", "Eliminierung zurücknehmen — {\"uuid\"|\"player\"}", this::revive);
        router.post("/api/eliminate",
                "Tribut eliminieren — {\"uuid\"|\"player\", \"killer\"?}", this::eliminate);
    }

    // ==================== Status ====================

    private ApiResponse status(ApiRequest request) {
        var services = support.services();
        JsonObject json = new JsonObject();
        GamePhase phase = services.session.phase();
        json.addProperty("phase", phase.name());
        json.addProperty("aliveCount", services.session.participants().aliveCount());
        json.addProperty("participantCount", services.session.participants().all().size());
        // respektiert gamemaster.hide-from-player-count
        json.addProperty("onlineCount", services.gamemaster.countOnlineExcludingGamemasters());
        json.addProperty("deathmatch", services.deathmatch.state().name());
        json.addProperty("supplyDrops", services.supplyDrops.statusLine());
        json.addProperty("sponsorBeacons", services.sponsorBeacons.activeBeacons().size());
        json.addProperty("virtualElapsedSeconds",
                phase == GamePhase.RUNNING ? services.virtualTime.elapsed().toSeconds() : 0);
        json.addProperty("timeMultiplier", services.virtualTime.multiplier());
        json.addProperty("teamCount", services.session.teamRegistry().all().size());
        json.addProperty("borderSize", services.border.currentSize());
        json.addProperty("monsterWaves", services.monsterWaves.activeSeries());
        json.addProperty("readOnly", services.config.get(services.settings.apiReadOnly));
        JsonObject arena = ApiSupport.locationJson(
                com.hungergames.GameState.getInstance().getArenaCenter());
        if (arena == null) {
            json.add("arenaCenter", null);
        } else {
            json.add("arenaCenter", arena);
        }
        services.session.winner().ifPresentOrElse(
                winner -> json.addProperty("winner", describeWinner(winner)),
                () -> json.add("winner", null));
        return ApiResponse.json(json);
    }

    private ApiResponse participants(ApiRequest request) {
        JsonArray array = new JsonArray();
        for (Participant participant : support.services().session.participants().all()) {
            array.add(support.participantJson(participant));
        }
        return ApiResponse.json("participants", array);
    }

    private ApiResponse detail(ApiRequest request) {
        String key = request.param("player");
        Optional<Participant> found = support.services().session.participants().all().stream()
                .filter(p -> p.lastKnownName().equalsIgnoreCase(key)
                        || p.uuid().toString().equalsIgnoreCase(key))
                .findFirst();
        return found.map(participant -> ApiResponse.json(support.participantJson(participant)))
                .orElseGet(() -> ApiResponse.notFound("\"" + key + "\" ist kein registrierter Tribut"));
    }

    private ApiResponse onlinePlayers(ApiRequest request) {
        JsonArray array = new JsonArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", player.getUniqueId().toString());
            entry.addProperty("name", player.getName());
            entry.addProperty("gameMode", player.getGameMode().name());
            entry.addProperty("health", player.getHealth());
            entry.addProperty("participant",
                    support.services().session.isWhitelisted(player.getUniqueId()));
            entry.addProperty("gamemaster",
                    support.services().gamemaster.isActive(player.getUniqueId()));
            entry.add("location", ApiSupport.locationJson(player.getLocation()));
            array.add(entry);
        }
        return ApiResponse.json("players", array);
    }

    /** Kein Bukkit-Zugriff — läuft bewusst ohne Main-Thread-Hop (Datei-I/O). */
    private ApiResponse log(ApiRequest request) throws IOException {
        var services = support.services();
        int requested = Math.max(1, Math.min(MAX_LOG_LINES, request.queryInt("lines", 200)));
        java.io.File file = services.roundLog.currentFile();
        JsonObject json = new JsonObject();
        json.addProperty("file", file.getName());
        json.addProperty("enabled", services.config.get(services.settings.roundLogEnabled));
        JsonArray lines = new JsonArray();
        if (file.isFile()) {
            List<String> all = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            all.subList(Math.max(0, all.size() - requested), all.size()).forEach(lines::add);
        }
        json.add("lines", lines);
        return ApiResponse.json(json);
    }

    // ==================== Tribute verwalten ====================

    private ApiResponse whitelistAdd(ApiRequest request) {
        UUID uuid = request.requireUuid("uuid");
        String requestedName = request.optString("name", "");
        String name = !requestedName.isEmpty() ? requestedName
                : java.util.Objects.requireNonNullElse(
                        Bukkit.getOfflinePlayer(uuid).getName(),
                        uuid.toString().substring(0, 8));
        if (!support.services().session.whitelistAdd(uuid, name)) {
            return ApiResponse.conflict("Bereits als Tribut registriert");
        }
        support.log(name + " (" + uuid + ") per HTTP-API registriert");
        return ApiResponse.ok(json -> {
            json.addProperty("uuid", uuid.toString());
            json.addProperty("name", name);
        });
    }

    private ApiResponse whitelistRemove(ApiRequest request) {
        UUID uuid = support.resolvePlayerRef(request.param("player"));
        if (!support.services().session.whitelistRemove(uuid)) {
            return ApiResponse.conflict("Kein registrierter Tribut: " + uuid);
        }
        support.log(uuid + " per HTTP-API aus dem Turnier entfernt");
        return ApiResponse.ok();
    }

    private ApiResponse revive(ApiRequest request) {
        UUID uuid = support.requirePlayerUuid(request);
        if (!support.services().session.revive(uuid)) {
            return ApiResponse.conflict("Nicht eliminiert oder kein Tribut: " + uuid);
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }
        support.log(uuid + " per HTTP-API wiederbelebt");
        return ApiResponse.ok();
    }

    private ApiResponse eliminate(ApiRequest request) {
        UUID uuid = support.requirePlayerUuid(request);
        UUID killer = request.has("killer")
                ? support.resolvePlayerRef(request.requireString("killer")) : null;
        if (!support.services().session.eliminate(uuid, killer)) {
            return ApiResponse.conflict("Nicht lebend oder kein Tribut: " + uuid);
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            support.services().spectator.makeSpectator(player);
        }
        support.log(uuid + " per HTTP-API eliminiert"
                + (killer == null ? "" : " (Killer: " + killer + ")"));
        return ApiResponse.ok();
    }

    // ==================== intern ====================

    private String describeWinner(Winner winner) {
        var session = support.services().session;
        return switch (winner) {
            case Winner.Solo solo -> session.participants().nameOf(solo.uuid()).orElse("?");
            case Winner.Team team -> session.teamRegistry().team(team.teamId())
                    .map(Team::name).orElse(team.teamId().value());
            case Winner.None none -> "none";
        };
    }
}
