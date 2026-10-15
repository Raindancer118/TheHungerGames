package com.hungergames.platform.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.service.TeamOperationResult;
import com.hungergames.game.port.GameEventPublisher.MembershipCause;
import com.hungergames.platform.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Team-Endpunkte der HTTP-API: anlegen, umbenennen, einfärben, löschen,
 * Mitglieder zuordnen/entfernen, Captain setzen und Zufallsverteilung.
 *
 * <p>Alle Mutationen laufen über {@code GameSession} und damit durch dieselben
 * Regeln wie GUI und Commands (Teamgröße, Wechselregeln, Sperrphase); sie
 * feuern die passenden Bukkit-Events und landen im Rundenlog.</p>
 */
final class TeamEndpoints implements ApiRouter.Module {

    private final ApiSupport support;

    TeamEndpoints(ApiSupport support) {
        this.support = support;
    }

    @Override
    public void register(ApiRouter router) {
        router.get("/api/teams", "Teams mit Farbe, Mitgliedern und Captain", this::list);
        router.get("/api/teams/colors", "Freie und belegte Teamfarben", this::colors);
        router.post("/api/teams", "Team erstellen — {\"name\", \"color\"?}", this::create);
        router.delete("/api/teams", "Alle Teams löschen", this::deleteAll);
        router.post("/api/teams/random", "Teamlose Tribute zufällig verteilen", this::random);
        router.get("/api/teams/{id}", "Einzelnes Team", this::detail);
        router.patch("/api/teams/{id}",
                "Team ändern — {\"name\"?, \"color\"?, \"captain\"?}", this::patch);
        router.delete("/api/teams/{id}", "Team löschen", this::delete);
        router.post("/api/teams/{id}/members",
                "Spieler zuordnen — {\"uuid\"|\"player\"}", this::addMember);
        router.delete("/api/teams/{id}/members/{player}",
                "Spieler aus dem Team entfernen (UUID oder Name)", this::removeMember);
        router.post("/api/teams/{id}/captain",
                "Captain setzen — {\"uuid\"|\"player\"}", this::setCaptain);
    }

    // ==================== Lesen ====================

    private ApiResponse list(ApiRequest request) {
        JsonArray array = new JsonArray();
        for (Team team : support.services().session.teamRegistry().all()) {
            array.add(support.teamJson(team));
        }
        return ApiResponse.json("teams", array);
    }

    private ApiResponse detail(ApiRequest request) {
        return team(request)
                .map(team -> ApiResponse.json(support.teamJson(team)))
                .orElseGet(() -> ApiResponse.notFound("Unbekanntes Team: " + request.param("id")));
    }

    private ApiResponse colors(ApiRequest request) {
        var registry = support.services().session.teamRegistry();
        var available = registry.availableColors();
        JsonObject json = new JsonObject();
        JsonArray availableJson = new JsonArray();
        JsonArray allJson = new JsonArray();
        for (TeamColor color : TeamColor.values()) {
            allJson.add(color.name());
            if (available.contains(color)) {
                availableJson.add(color.name());
            }
        }
        JsonObject claimed = new JsonObject();
        for (Team team : registry.all()) {
            claimed.addProperty(team.color().name(), team.id().value());
        }
        json.add("available", availableJson);
        json.add("all", allJson);
        json.add("claimed", claimed);
        return ApiResponse.json(json);
    }

    // ==================== Schreiben ====================

    private ApiResponse create(ApiRequest request) {
        String name = request.requireString("name");
        TeamColor color = request.optEnum("color", TeamColor.class, null);
        var result = support.services().session.teamCreate(name, color);
        if (!result.status().isSuccess()) {
            return ApiResponse.conflict(Messages.describe(result.status()));
        }
        Team team = result.team().orElseThrow();
        support.log("Team \"" + name + "\" per HTTP-API erstellt");
        return ApiResponse.ok(json -> {
            json.addProperty("teamId", team.id().value());
            json.addProperty("color", team.color().name());
        });
    }

    /**
     * Wendet {@code name}, {@code color} und {@code captain} in dieser
     * Reihenfolge an. Schlägt ein Schritt fehl, bleiben die vorherigen
     * wirksam — die Antwort nennt sie unter {@code applied}.
     */
    private ApiResponse patch(ApiRequest request) {
        Optional<Team> existing = team(request);
        if (existing.isEmpty()) {
            return ApiResponse.notFound("Unbekanntes Team: " + request.param("id"));
        }
        TeamId id = existing.get().id();
        List<String> applied = new ArrayList<>();

        if (request.has("name")) {
            String newName = request.requireString("name");
            TeamOperationResult result = support.services().session.teamRename(id, newName);
            if (!result.isSuccess()) {
                return failure(result, applied);
            }
            support.log("Team " + id.value() + " per HTTP-API umbenannt in \"" + newName + "\"");
            applied.add("name");
        }
        if (request.has("color")) {
            TeamColor color = request.requireEnum("color", TeamColor.class);
            TeamOperationResult result = support.services().session.teamSetColor(id, color);
            if (!result.isSuccess()) {
                return failure(result, applied);
            }
            support.log("Team " + id.value() + " per HTTP-API auf Farbe " + color + " gesetzt");
            applied.add("color");
        }
        if (request.has("captain")) {
            if (!captainSystemEnabled()) {
                JsonObject json = new JsonObject();
                json.addProperty("error", "Das Captain-System ist deaktiviert"
                        + " (teams.captain-enabled = false)");
                json.add("applied", toArray(applied));
                return new ApiResponse(409, json);
            }
            UUID captain = support.resolvePlayerRef(request.requireString("captain"));
            TeamOperationResult result = support.services().session.teamSetCaptain(id, captain);
            if (!result.isSuccess()) {
                return failure(result, applied);
            }
            support.log(captain + " per HTTP-API als Captain von " + id.value() + " gesetzt");
            applied.add("captain");
        }
        if (applied.isEmpty()) {
            return ApiResponse.badRequest(
                    "Erwartet mindestens eines der Felder \"name\", \"color\", \"captain\"");
        }
        List<String> changed = List.copyOf(applied);
        return ApiResponse.ok(json -> json.add("applied", toArray(changed)));
    }

    private ApiResponse delete(ApiRequest request) {
        TeamId id = new TeamId(request.param("id"));
        TeamOperationResult result = support.services().session.teamDelete(id);
        if (!result.isSuccess()) {
            return ApiResponse.conflict(Messages.describe(result));
        }
        support.log("Team " + id.value() + " per HTTP-API gelöscht");
        return ApiResponse.ok();
    }

    /** Löscht alle Teams; die Mitglieder bleiben als teamlose Tribute registriert. */
    private ApiResponse deleteAll(ApiRequest request) {
        List<Team> teams = support.services().session.teamRegistry().all();
        int deleted = 0;
        List<String> failures = new ArrayList<>();
        for (Team team : teams) {
            TeamOperationResult result = support.services().session.teamDelete(team.id());
            if (result.isSuccess()) {
                deleted++;
            } else {
                failures.add(team.id().value() + ": " + Messages.describe(result));
            }
        }
        if (deleted == 0 && !failures.isEmpty()) {
            return ApiResponse.conflict(String.join("; ", failures));
        }
        support.log(deleted + " Team(s) per HTTP-API gelöscht");
        int count = deleted;
        return ApiResponse.ok(json -> {
            json.addProperty("deleted", count);
            if (!failures.isEmpty()) {
                json.add("failed", toArray(failures));
            }
        });
    }

    private ApiResponse random(ApiRequest request) {
        if (!support.services().session.teamRegistry().enabled()) {
            return ApiResponse.conflict(Messages.describe(TeamOperationResult.TEAMS_DISABLED));
        }
        int assigned = support.services().session.teamAssignRandomly();
        support.log(assigned + " Tribut(e) per HTTP-API zufällig auf Teams verteilt");
        return ApiResponse.ok(json -> json.addProperty("assigned", assigned));
    }

    private ApiResponse addMember(ApiRequest request) {
        UUID uuid = support.requirePlayerUuid(request);
        TeamId team = new TeamId(request.param("id"));
        TeamOperationResult result =
                support.services().session.teamAssign(uuid, team, MembershipCause.API);
        if (!result.isSuccess()) {
            return ApiResponse.conflict(Messages.describe(result));
        }
        support.log(uuid + " per HTTP-API Team " + team.value() + " zugeordnet");
        return ApiResponse.ok();
    }

    private ApiResponse removeMember(ApiRequest request) {
        UUID uuid = support.resolvePlayerRef(request.param("player"));
        TeamOperationResult result =
                support.services().session.teamRemovePlayer(uuid, MembershipCause.API);
        if (!result.isSuccess()) {
            return ApiResponse.conflict(Messages.describe(result));
        }
        support.log(uuid + " per HTTP-API aus dem Team entfernt");
        return ApiResponse.ok();
    }

    private ApiResponse setCaptain(ApiRequest request) {
        UUID uuid = support.requirePlayerUuid(request);
        TeamId team = new TeamId(request.param("id"));
        if (!captainSystemEnabled()) {
            return ApiResponse.conflict("Das Captain-System ist deaktiviert"
                    + " (teams.captain-enabled = false)");
        }
        TeamOperationResult result = support.services().session.teamSetCaptain(team, uuid);
        if (!result.isSuccess()) {
            return ApiResponse.conflict(Messages.describe(result));
        }
        support.log(uuid + " per HTTP-API als Captain von " + team.value() + " gesetzt");
        return ApiResponse.ok();
    }

    // ==================== intern ====================

    /**
     * Ohne Captain-System liefert die Registry {@code SWITCHING_DISABLED} —
     * über die API wäre „Teamwechsel ist deaktiviert" irreführend.
     */
    private boolean captainSystemEnabled() {
        return support.services().teamRules.get().captainEnabled();
    }

    private Optional<Team> team(ApiRequest request) {
        return support.services().session.teamRegistry().team(new TeamId(request.param("id")));
    }

    private static ApiResponse failure(TeamOperationResult result, List<String> applied) {
        JsonObject json = new JsonObject();
        json.addProperty("error", Messages.describe(result));
        json.add("applied", toArray(applied));
        return new ApiResponse(409, json);
    }

    private static JsonArray toArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
