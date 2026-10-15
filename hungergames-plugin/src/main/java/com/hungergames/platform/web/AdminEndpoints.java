package com.hungergames.platform.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regie-Endpunkte rund um Personen: Spielmacher, Zuschauer-Teleport und die
 * Mannequin-Test-Simulation.
 */
final class AdminEndpoints implements ApiRouter.Module {

    private final ApiSupport support;

    AdminEndpoints(ApiSupport support) {
        this.support = support;
    }

    @Override
    public void register(ApiRouter router) {
        router.get("/api/gamemaster", "Spielmacher-Namen und aktive Spielmacher", this::gamemaster);
        router.post("/api/gamemaster/names",
                "Spielmacher eintragen — {\"name\": \"...\"}", this::gamemasterAdd);
        router.delete("/api/gamemaster/names/{name}", "Spielmacher austragen", this::gamemasterRemove);
        router.post("/api/gamemaster/activate",
                "Spielmacher-Modus aktivieren — {\"player\": \"Name\"}", this::gamemasterActivate);
        router.post("/api/gamemaster/deactivate",
                "Spielmacher-Modus beenden — {\"player\": \"Name\"}", this::gamemasterDeactivate);
        router.post("/api/gamemaster/mode",
                "Spielmodus setzen — {\"player\": \"Name\", \"mode\": \"SPECTATOR\"}", this::gamemasterMode);

        router.post("/api/spectator/teleport",
                "Zuschauer zu einem Tribut teleportieren — {\"player\", \"target\"}", this::teleport);

        router.get("/api/simulation", "Mannequin-Test-Simulation: Zustand", this::simulation);
        router.post("/api/simulation/spawn",
                "Mannequins spawnen — {\"admin\": \"Name\", \"count\": 12}", this::spawn);
        router.post("/api/simulation/eliminate",
                "Eine Attrappe eliminieren — {\"admin\": \"Name\"}", this::eliminateMannequin);
        router.delete("/api/simulation", "Alle Attrappen und Test-Teams entfernen", this::clearSim);
    }

    // ==================== Spielmacher ====================

    private ApiResponse gamemaster(ApiRequest request) {
        var service = support.services().gamemaster;
        JsonObject json = new JsonObject();
        JsonArray names = new JsonArray();
        service.names().forEach(names::add);
        json.add("names", names);
        JsonArray active = new JsonArray();
        for (UUID uuid : service.activeGamemasters()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", uuid.toString());
            entry.addProperty("name", support.services().session.participants().nameOf(uuid)
                    .orElseGet(() -> {
                        Player online = org.bukkit.Bukkit.getPlayer(uuid);
                        return online != null ? online.getName() : "?";
                    }));
            entry.addProperty("online", org.bukkit.Bukkit.getPlayer(uuid) != null);
            active.add(entry);
        }
        json.add("active", active);
        return ApiResponse.json(json);
    }

    private ApiResponse gamemasterAdd(ApiRequest request) {
        String name = request.requireString("name");
        List<String> errors = support.services().gamemaster.addName(ApiSupport.ACTOR, name);
        return errors.isEmpty() ? ApiResponse.ok()
                : ApiResponse.conflict(String.join("; ", errors));
    }

    private ApiResponse gamemasterRemove(ApiRequest request) {
        String name = request.param("name");
        List<String> errors = support.services().gamemaster.removeName(ApiSupport.ACTOR, name);
        return errors.isEmpty() ? ApiResponse.ok()
                : ApiResponse.conflict(String.join("; ", errors));
    }

    private ApiResponse gamemasterActivate(ApiRequest request) {
        Player player = support.requireOnlinePlayer(request.requireString("player"));
        Optional<String> error = support.services().gamemaster.activate(player);
        return ApiResponse.okOrConflict(error.orElse(null));
    }

    private ApiResponse gamemasterDeactivate(ApiRequest request) {
        Player player = support.requireOnlinePlayer(request.requireString("player"));
        Optional<String> error = support.services().gamemaster.deactivate(player);
        return ApiResponse.okOrConflict(error.orElse(null));
    }

    private ApiResponse gamemasterMode(ApiRequest request) {
        Player player = support.requireOnlinePlayer(request.requireString("player"));
        GameMode mode = request.requireEnum("mode", GameMode.class);
        if (!support.services().gamemaster.isActive(player.getUniqueId())) {
            return ApiResponse.conflict(player.getName() + " ist kein aktiver Spielmacher");
        }
        support.services().gamemaster.setMode(player, mode);
        support.log(player.getName() + " per HTTP-API auf Spielmodus " + mode + " gesetzt");
        return ApiResponse.ok();
    }

    // ==================== Zuschauer ====================

    private ApiResponse teleport(ApiRequest request) {
        Player spectator = support.requireOnlinePlayer(request.requireString("player"));
        UUID target = support.resolvePlayerRef(request.requireString("target"));
        if (!support.services().spectator.teleportTo(spectator, target)) {
            return ApiResponse.conflict("Ziel ist nicht online oder kein lebender Tribut");
        }
        return ApiResponse.ok();
    }

    // ==================== Test-Simulation ====================

    private ApiResponse simulation(ApiRequest request) {
        var service = support.services().mannequinSim;
        JsonObject json = new JsonObject();
        json.addProperty("mannequins", service.mannequinCount());
        json.addProperty("alive", service.aliveCount());
        json.addProperty("teams", service.teamCount());
        json.addProperty("canSpawn", service.canSpawn());
        return ApiResponse.json(json);
    }

    private ApiResponse spawn(ApiRequest request) {
        Player admin = support.requireOnlinePlayer(request.requireString("admin"));
        int count = request.requireInt("count");
        if (count < 1) {
            return ApiResponse.badRequest("count muss ≥ 1 sein");
        }
        if (!support.services().mannequinSim.canSpawn()) {
            return ApiResponse.conflict("Mannequins können nur vor der Startsequenz gespawnt werden"
                    + " (Phase " + support.services().session.phase() + ")");
        }
        String message = support.services().mannequinSim.spawn(admin, count);
        return ApiResponse.ok(json -> {
            json.addProperty("message", message);
            json.addProperty("mannequins", support.services().mannequinSim.mannequinCount());
        });
    }

    private ApiResponse eliminateMannequin(ApiRequest request) {
        Player admin = support.requireOnlinePlayer(request.requireString("admin"));
        Optional<String> error = support.services().mannequinSim.eliminateOne(admin);
        return ApiResponse.okOrConflict(error.orElse(null));
    }

    private ApiResponse clearSim(ApiRequest request) {
        int removed = support.services().mannequinSim.clear();
        support.log(removed + " Mannequin(s) per HTTP-API entfernt");
        return ApiResponse.ok(json -> json.addProperty("removed", removed));
    }
}
