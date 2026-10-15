package com.hungergames.platform.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hungergames.api.model.GamePhase;
import com.hungergames.game.border.BorderPhaseConfig;
import com.hungergames.game.border.BorderSettings;
import com.hungergames.platform.game.GameControlService;
import com.hungergames.platform.game.PreflightCheckService;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Spielablauf-Endpunkte: Preflight-Check, Arena initialisieren, Startsequenz,
 * Start, Runde beenden/zurücksetzen, Phasenwechsel, Zeitmultiplikator und
 * Border-Steuerung.
 *
 * <p>{@code init}, {@code startup} und {@code start} brauchen einen
 * <strong>online</strong> Referenz-Admin ({@code {"admin": "Name"}}): Die
 * Stage-Runner nehmen dessen Position als Arena-Zentrum, teleportieren ihn in
 * die Lobby und melden ihm den Fortschritt. Ohne Spieler ist die Sequenz nicht
 * ausführbar — daher {@code 409} statt stiller Teilausführung.</p>
 */
final class GameEndpoints implements ApiRouter.Module {

    private final ApiSupport support;

    GameEndpoints(ApiSupport support) {
        this.support = support;
    }

    @Override
    public void register(ApiRouter router) {
        router.get("/api/game", "Phasen-Vorbedingungen und Spielstand", this::game);
        router.get("/api/game/preflight", "Preflight-Check mit allen Befunden", this::preflight);
        router.post("/api/game/init",
                "Arena initialisieren — {\"admin\": \"Name\", \"playerCount\": 24}", this::init);
        router.post("/api/game/startup", "Startsequenz — {\"admin\": \"Name\"}", this::startup);
        router.post("/api/game/start", "Countdown und Start — {\"admin\": \"Name\"}", this::start);
        router.post("/api/game/end", "Laufende Runde wie bei Zeitablauf werten", this::endRound);
        router.post("/api/round/end", "Alias von /api/game/end", this::endRound);
        router.post("/api/game/reset",
                "Runde zurücksetzen (Tribute bleiben registriert)", this::reset);
        router.post("/api/game/phase",
                "Phase erzwingen (Notfall) — {\"phase\": \"LOBBY\"}", this::phase);

        router.post("/api/time/multiplier",
                "Zeitmultiplikator setzen — {\"multiplier\": 4.0}", this::multiplier);

        router.get("/api/border", "Bordergröße, nächste Phase, Konfiguration", this::border);
        router.post("/api/border/shrink",
                "Border sofort auf Größe schrumpfen — {\"size\": 200}", this::shrink);
        router.post("/api/border/reset", "Border auf Startgröße zurücksetzen", this::resetBorder);
    }

    // ==================== Spielablauf ====================

    private ApiResponse game(ApiRequest request) {
        var services = support.services();
        JsonObject json = new JsonObject();
        json.addProperty("phase", services.session.phase().name());
        json.addProperty("canInit", services.gameControl.canInit());
        json.addProperty("canStartup", services.gameControl.canStartup());
        json.addProperty("canStart", services.gameControl.canStart());
        json.addProperty("canEndRound", services.gameControl.canEndRound());
        json.addProperty("minPlayers", GameControlService.MIN_PLAYERS);
        json.addProperty("maxPlayers", GameControlService.MAX_PLAYERS);
        json.addProperty("participantCount", services.session.participants().all().size());
        json.addProperty("aliveCount", services.session.participants().aliveCount());
        JsonArray phases = new JsonArray();
        for (GamePhase phase : GamePhase.values()) {
            phases.add(phase.name());
        }
        json.add("phases", phases);
        return ApiResponse.json(json);
    }

    private ApiResponse preflight(ApiRequest request) {
        List<PreflightCheckService.CheckResult> results = support.services().preflight.runAll();
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        for (var result : results) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", result.name());
            entry.addProperty("severity", result.severity().name());
            entry.addProperty("detail", result.detail());
            array.add(entry);
        }
        json.add("checks", array);
        json.addProperty("blocking", support.services().preflight.hasBlockingErrors(results));
        return ApiResponse.json(json);
    }

    private ApiResponse init(ApiRequest request) {
        Player admin = requireAdmin(request);
        int playerCount = request.requireInt("playerCount");
        if (playerCount < GameControlService.MIN_PLAYERS
                || playerCount > GameControlService.MAX_PLAYERS) {
            return ApiResponse.badRequest("playerCount muss zwischen " + GameControlService.MIN_PLAYERS
                    + " und " + GameControlService.MAX_PLAYERS + " liegen");
        }
        if (!support.services().gameControl.canInit()) {
            return ApiResponse.conflict("Arena ist bereits initialisiert (Phase "
                    + support.services().session.phase() + ") — erst Runde beenden");
        }
        support.log("Arena-Initialisierung per HTTP-API an der Position von " + admin.getName());
        if (!support.services().gameControl.init(admin, playerCount)) {
            return ApiResponse.conflict("Initialisierung abgelehnt — siehe Server-Log");
        }
        return ApiResponse.ok(json -> {
            json.addProperty("admin", admin.getName());
            json.addProperty("playerCount", playerCount);
        });
    }

    private ApiResponse startup(ApiRequest request) {
        Player admin = requireAdmin(request);
        if (!support.services().gameControl.canStartup()) {
            return ApiResponse.conflict("Startsequenz nur in Phase LOBBY möglich (aktuell: "
                    + support.services().session.phase() + ")");
        }
        support.log("Startsequenz per HTTP-API ausgelöst (Admin " + admin.getName() + ")");
        return ApiResponse.okOrConflict(support.services().gameControl.startup(admin)
                ? null : "Startsequenz abgelehnt — siehe Server-Log");
    }

    private ApiResponse start(ApiRequest request) {
        Player admin = requireAdmin(request);
        if (!support.services().gameControl.canStart()) {
            return ApiResponse.conflict("Start nur in Phase READY möglich (aktuell: "
                    + support.services().session.phase() + ")");
        }
        support.log("Spielstart per HTTP-API ausgelöst (Admin " + admin.getName() + ")");
        return ApiResponse.okOrConflict(support.services().gameControl.start(admin)
                ? null : "Start abgelehnt — siehe Server-Log");
    }

    private ApiResponse endRound(ApiRequest request) {
        if (!support.services().gameControl.endRound(ApiSupport.ACTOR)) {
            return ApiResponse.conflict("Keine laufende Runde (Phase "
                    + support.services().session.phase() + ")");
        }
        return ApiResponse.ok();
    }

    private ApiResponse reset(ApiRequest request) {
        support.services().gameControl.prepareNextRound(ApiSupport.ACTOR);
        return ApiResponse.ok(json ->
                json.addProperty("phase", support.services().session.phase().name()));
    }

    /**
     * Erzwingt einen Phasenwechsel — nur für Notfälle (hängengebliebene
     * Sequenz). Ungültige Übergänge lehnt die Session ab.
     */
    private ApiResponse phase(ApiRequest request) {
        GamePhase target = request.requireEnum("phase", GamePhase.class);
        GamePhase current = support.services().session.phase();
        if (!support.services().session.transitionTo(target)) {
            return ApiResponse.conflict("Phasenwechsel " + current + " → " + target
                    + " ist nicht erlaubt");
        }
        support.log("Phase per HTTP-API von " + current + " auf " + target + " gesetzt");
        return ApiResponse.ok(json -> json.addProperty("phase", target.name()));
    }

    // ==================== Zeit ====================

    private ApiResponse multiplier(ApiRequest request) {
        double multiplier = request.requireDouble("multiplier");
        if (multiplier < 1.0 || multiplier > 100.0) {
            return ApiResponse.badRequest("multiplier muss zwischen 1.0 und 100.0 liegen");
        }
        support.services().virtualTime.setMultiplier(multiplier);
        support.log("Zeitmultiplikator per HTTP-API auf ×" + multiplier + " gesetzt");
        return ApiResponse.ok(json -> json.addProperty("multiplier",
                support.services().virtualTime.multiplier()));
    }

    // ==================== Border ====================

    private ApiResponse border(ApiRequest request) {
        var services = support.services();
        BorderSettings settings = services.border.currentSettings();
        JsonObject json = new JsonObject();
        json.addProperty("currentSize", services.border.currentSize());
        json.addProperty("initialSize", settings.initialSize());
        json.addProperty("minimumSize", settings.minimumSize());
        json.addProperty("maxEdgeSpeed", settings.maxEdgeSpeed());
        json.addProperty("nextPhaseIndex", services.border.nextPhaseIndex());
        JsonArray phases = new JsonArray();
        for (BorderPhaseConfig phase : settings.phases()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("targetSize", phase.targetSize());
            entry.addProperty("mode", phase.mode().name());
            phase.trigger().time().ifPresent(time ->
                    entry.addProperty("triggerAfterSeconds", time.toSeconds()));
            phase.trigger().aliveBelow().ifPresent(alive ->
                    entry.addProperty("triggerAliveBelow", alive));
            phase.duration().ifPresent(duration ->
                    entry.addProperty("durationSeconds", duration.toSeconds()));
            phase.edgeSpeed().ifPresent(speed -> entry.addProperty("edgeSpeed", speed));
            phases.add(entry);
        }
        json.add("phases", phases);
        return ApiResponse.json(json);
    }

    private ApiResponse shrink(ApiRequest request) {
        double size = request.requireDouble("size");
        BorderSettings settings = support.services().border.currentSettings();
        if (size < settings.minimumSize()) {
            return ApiResponse.badRequest("size darf die Untergrenze (" + settings.minimumSize()
                    + ") nicht unterschreiten");
        }
        long seconds = support.services().border.overrideShrinkTo(size);
        if (seconds == 0) {
            return ApiResponse.conflict("Keine Schrumpfung ausgeführt — keine Arena-Welt geladen"
                    + " oder Zielgröße ≥ aktuelle Größe (" + support.services().border.currentSize()
                    + ")");
        }
        support.log("Border per HTTP-API auf " + size + " Blöcke geschrumpft (" + seconds + "s)");
        return ApiResponse.ok(json -> {
            json.addProperty("targetSize", size);
            json.addProperty("durationSeconds", seconds);
        });
    }

    private ApiResponse resetBorder(ApiRequest request) {
        support.services().border.resetToInitial();
        support.log("Border per HTTP-API auf Startgröße zurückgesetzt");
        return ApiResponse.ok(json ->
                json.addProperty("currentSize", support.services().border.currentSize()));
    }

    // ==================== intern ====================

    /** Referenz-Admin für die Stage-Runner (Position, Teleport, Rückmeldung). */
    private Player requireAdmin(ApiRequest request) {
        return support.requireOnlinePlayer(request.requireString("admin"));
    }
}
