package com.hungergames.platform.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hungergames.platform.effect.EffectService;
import com.hungergames.platform.sound.SoundService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Spielmacher-Ereignisse: Deathmatch, Kapitol-Lieferungen, Sponsor-Tokens und
 * -Beacons, Monster-Wellen, Ansagen und Sound-/Effekt-Tests.
 */
final class EventEndpoints implements ApiRouter.Module {

    private final ApiSupport support;

    EventEndpoints(ApiSupport support) {
        this.support = support;
    }

    @Override
    public void register(ApiRouter router) {
        router.get("/api/deathmatch", "Deathmatch-Zustand", this::deathmatch);
        router.post("/api/deathmatch/start", "Deathmatch auslösen (Warnphase)", request ->
                ApiResponse.okOrConflict(
                        support.services().deathmatch.start(ApiSupport.ACTOR).orElse(null)));
        router.post("/api/deathmatch/cancel", "Warnphase abbrechen", request ->
                ApiResponse.okOrConflict(
                        support.services().deathmatch.cancel(ApiSupport.ACTOR).orElse(null)));

        router.get("/api/supplydrop", "Zeitplan und Status der Lieferungen", this::supplyDrops);
        router.post("/api/supplydrop", "Lieferung manuell auslösen", request ->
                ApiResponse.okOrConflict(
                        support.services().supplyDrops.triggerNow(ApiSupport.ACTOR).orElse(null)));

        router.get("/api/sponsor", "Tokens, Beacons und Status", this::sponsor);
        router.post("/api/sponsor/give",
                "Tokens geben — {\"player\": \"Name\", \"amount\": 1}", this::sponsorGive);
        router.post("/api/sponsor/clear",
                "Tokens eines Spielers einziehen — {\"player\": \"Name\"}", this::sponsorClear);
        router.post("/api/sponsor/beacons",
                "Sponsor-Beacon setzen — {\"player\"|\"world\",\"x\",\"y\",\"z\"}", this::beaconCreate);
        router.delete("/api/sponsor/beacons", "Alle Sponsor-Beacons entfernen", this::beaconsClear);

        router.get("/api/monsterwaves", "Laufende Serien und Standardwerte", this::waves);
        router.post("/api/monsterwaves", "Wellen-Serie starten — {\"player\"|Koordinaten, "
                + "\"mob\"?, \"count\"?, \"waves\"?, \"interval\"?}", this::wavesStart);
        router.delete("/api/monsterwaves", "Alle Wellen-Serien stoppen", this::wavesStop);

        router.post("/api/announce", "Broadcast — {\"message\": \"§6Text\"}", this::announce);

        router.get("/api/sounds", "Alle bekannten Sound-Namen", request ->
                ApiResponse.json("sounds", toArray(support.services().sounds.allSoundNames())));
        router.post("/api/sounds/test",
                "Sound testen — {\"player\": \"Name\", \"sound\": \"...\"}", this::soundTest);
        router.get("/api/effects", "Alle bekannten Partikel-Namen", request ->
                ApiResponse.json("effects", toArray(EffectService.allParticleNames())));
        router.post("/api/effects/test",
                "Effekt testen — {\"player\": \"Name\", \"effect\": \"...\"}", this::effectTest);
    }

    // ==================== Deathmatch / Drops ====================

    private ApiResponse deathmatch(ApiRequest request) {
        JsonObject json = new JsonObject();
        json.addProperty("state", support.services().deathmatch.state().name());
        json.addProperty("status", support.services().deathmatch.statusLine());
        return ApiResponse.json(json);
    }

    private ApiResponse supplyDrops(ApiRequest request) {
        var service = support.services().supplyDrops;
        JsonObject json = new JsonObject();
        json.addProperty("status", service.statusLine());
        JsonArray schedule = new JsonArray();
        var triggered = service.triggeredIndices();
        var times = service.schedule();
        for (int i = 0; i < times.size(); i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("index", i);
            entry.addProperty("afterSeconds", times.get(i).toSeconds());
            entry.addProperty("triggered", triggered.contains(i));
            schedule.add(entry);
        }
        json.add("schedule", schedule);
        return ApiResponse.json(json);
    }

    // ==================== Sponsoren ====================

    private ApiResponse sponsor(ApiRequest request) {
        var services = support.services();
        JsonObject json = new JsonObject();
        json.addProperty("tokensEnabled", services.sponsorTokens.tokensEnabled());
        json.addProperty("beaconsEnabled", services.sponsorBeacons.beaconsEnabled());
        json.addProperty("status", services.sponsorBeacons.statusLine());
        JsonArray beacons = new JsonArray();
        for (Location location : services.sponsorBeacons.activeBeacons()) {
            beacons.add(ApiSupport.locationJson(location));
        }
        json.add("beacons", beacons);
        return ApiResponse.json(json);
    }

    private ApiResponse sponsorGive(ApiRequest request) {
        Player target = support.requireOnlinePlayer(request.requireString("player"));
        int amount = Math.max(1, request.optInt("amount", 1));
        support.services().sponsorTokens.giveManually(ApiSupport.ACTOR, target, amount);
        return ApiResponse.ok(json -> {
            json.addProperty("player", target.getName());
            json.addProperty("amount", amount);
        });
    }

    private ApiResponse sponsorClear(ApiRequest request) {
        Player target = support.requireOnlinePlayer(request.requireString("player"));
        int removed = support.services().sponsorTokens.clearTokens(target);
        support.log(removed + " Token(s) von " + target.getName() + " per HTTP-API eingezogen");
        return ApiResponse.ok(json -> {
            json.addProperty("player", target.getName());
            json.addProperty("removed", removed);
        });
    }

    private ApiResponse beaconCreate(ApiRequest request) {
        Location location = support.requireLocation(request);
        Optional<String> error =
                support.services().sponsorBeacons.createBeacon(location, ApiSupport.ACTOR);
        if (error.isPresent()) {
            return ApiResponse.conflict(error.get());
        }
        return ApiResponse.ok(json -> json.add("location", ApiSupport.locationJson(location)));
    }

    private ApiResponse beaconsClear(ApiRequest request) {
        int removed = support.services().sponsorBeacons.removeAll(ApiSupport.ACTOR);
        return ApiResponse.ok(json -> json.addProperty("removed", removed));
    }

    // ==================== Monster-Wellen ====================

    private ApiResponse waves(ApiRequest request) {
        var service = support.services().monsterWaves;
        JsonObject json = new JsonObject();
        json.addProperty("activeSeries", service.activeSeries());
        JsonObject defaults = new JsonObject();
        defaults.addProperty("mob", service.defaultMob());
        defaults.addProperty("count", service.defaultCount());
        defaults.addProperty("waves", service.defaultWaves());
        defaults.addProperty("interval", service.defaultInterval());
        json.add("defaults", defaults);
        return ApiResponse.json(json);
    }

    private ApiResponse wavesStart(ApiRequest request) {
        var service = support.services().monsterWaves;
        Location center = support.requireLocation(request);
        String mob = request.optString("mob", service.defaultMob());
        int count = request.optInt("count", service.defaultCount());
        int waves = request.optInt("waves", service.defaultWaves());
        int interval = request.optInt("interval", service.defaultInterval());
        if (count < 1 || waves < 1 || interval < 1) {
            return ApiResponse.badRequest("count, waves und interval müssen ≥ 1 sein");
        }
        Optional<String> error =
                service.start(center, mob, count, waves, interval, ApiSupport.ACTOR);
        if (error.isPresent()) {
            return ApiResponse.conflict(error.get());
        }
        return ApiResponse.ok(json -> {
            json.addProperty("mob", mob);
            json.addProperty("count", count);
            json.addProperty("waves", waves);
            json.addProperty("interval", interval);
            json.add("location", ApiSupport.locationJson(center));
        });
    }

    private ApiResponse wavesStop(ApiRequest request) {
        int stopped = support.services().monsterWaves.stopAll();
        support.log(stopped + " Monster-Wellen-Serie(n) per HTTP-API gestoppt");
        return ApiResponse.ok(json -> json.addProperty("stopped", stopped));
    }

    // ==================== Ansagen, Sound, Effekte ====================

    private ApiResponse announce(ApiRequest request) {
        String message = request.requireString("message");
        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(message));
        support.log("Broadcast über HTTP-API: " + message);
        return ApiResponse.ok();
    }

    private ApiResponse soundTest(ApiRequest request) {
        Player player = support.requireOnlinePlayer(request.requireString("player"));
        String spec = request.requireString("sound");
        if (!support.services().sounds.test(player, spec)) {
            return ApiResponse.badRequest("Ungültige Sound-Angabe: " + spec
                    + SoundService.validateSoundKey(spec).map(error -> " (" + error + ")").orElse(""));
        }
        return ApiResponse.ok();
    }

    private ApiResponse effectTest(ApiRequest request) {
        Player player = support.requireOnlinePlayer(request.requireString("player"));
        String spec = request.requireString("effect");
        if (!support.services().effects.test(player, spec)) {
            return ApiResponse.badRequest("Ungültige Effekt-Angabe: " + spec
                    + EffectService.validateEffectSpec(spec).map(error -> " (" + error + ")").orElse(""));
        }
        return ApiResponse.ok();
    }

    // ==================== intern ====================

    private static JsonArray toArray(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
