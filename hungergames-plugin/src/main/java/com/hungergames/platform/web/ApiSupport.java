package com.hungergames.platform.web;

import com.google.gson.JsonObject;
import com.hungergames.GameState;
import com.hungergames.api.model.Participant;
import com.hungergames.api.model.Team;
import com.hungergames.platform.gui.admin.AdminServices;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Gemeinsame Helfer aller API-Endpunkte: Zugriff auf die {@link AdminServices},
 * Auflösung von Spielern/Orten aus dem JSON-Body, Rundenlog und die
 * JSON-Darstellung eines Tributs.
 *
 * <p>Alle Methoden, die Bukkit berühren, setzen den Main-Thread voraus — das
 * garantiert {@link HttpApiService} für Routen, die nicht als {@code Async}
 * registriert sind.</p>
 */
public final class ApiSupport {

    /** Akteur-Name in Rundenlog und Audit-Log. */
    public static final String ACTOR = "HTTP-API";

    private final AdminServices services;

    public ApiSupport(AdminServices services) {
        this.services = services;
    }

    public AdminServices services() {
        return services;
    }

    /** Schreibt eine Zeile ins Rundenlog (Kategorie {@code API}). */
    public void log(String message) {
        services.roundLog.log("API", message);
    }

    // ==================== Spieler auflösen ====================

    /**
     * Löst einen Online-Spieler nach Name oder UUID auf.
     *
     * @throws ApiConflictException wenn der Spieler nicht online ist
     */
    public Player requireOnlinePlayer(String nameOrUuid) {
        Player player = findOnlinePlayer(nameOrUuid);
        if (player == null) {
            throw new ApiConflictException("\"" + nameOrUuid + "\" ist nicht online");
        }
        return player;
    }

    /** @return der Online-Spieler zu Name oder UUID, oder {@code null}. */
    public Player findOnlinePlayer(String nameOrUuid) {
        Player byName = Bukkit.getPlayerExact(nameOrUuid);
        if (byName != null) {
            return byName;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(nameOrUuid.trim()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Löst einen Spieler aus dem Body auf: Feld {@code uuid} (UUID) oder
     * {@code player} (Name eines registrierten Tributs oder Online-Spielers).
     *
     * @throws ApiBadRequestException wenn beide Felder fehlen
     * @throws ApiConflictException   wenn der Name niemandem zuzuordnen ist
     */
    public UUID requirePlayerUuid(ApiRequest request) {
        Optional<UUID> direct = request.optUuid("uuid");
        if (direct.isPresent()) {
            return direct.get();
        }
        if (!request.has("player")) {
            throw new ApiBadRequestException(
                    "Erwartet \"uuid\" (UUID) oder \"player\" (Name) im JSON-Body");
        }
        return resolvePlayerRef(request.requireString("player"));
    }

    /**
     * Löst eine Spielerangabe aus einem Pfadsegment auf: eine UUID direkt,
     * sonst über {@link #resolveName(String)}.
     */
    public UUID resolvePlayerRef(String uuidOrName) {
        try {
            return UUID.fromString(uuidOrName.trim());
        } catch (IllegalArgumentException e) {
            return resolveName(uuidOrName);
        }
    }

    /**
     * Löst einen Namen zu einer UUID auf: erst registrierte Tribute, dann
     * Online-Spieler. Offline-Spieler ohne Registrierung sind bewusst nicht
     * abgedeckt (Mojang-Lookup würde den Main-Thread blockieren) — dafür die
     * UUID direkt schicken.
     */
    public UUID resolveName(String name) {
        for (Participant participant : services.session.participants().all()) {
            if (participant.lastKnownName().equalsIgnoreCase(name)) {
                return participant.uuid();
            }
        }
        Player online = findOnlinePlayer(name);
        if (online != null) {
            return online.getUniqueId();
        }
        throw new ApiConflictException("\"" + name + "\" ist weder registrierter Tribut noch online"
                + " — bei Offline-Spielern die UUID schicken");
    }

    // ==================== Orte auflösen ====================

    /**
     * Liest einen Ort aus dem Body: entweder {@code {"player": "Name"}} (dessen
     * Position) oder {@code {"world": "...", "x": .., "y": .., "z": ..}}. Fehlt
     * {@code world}, wird die Welt des Arena-Zentrums genutzt.
     */
    public Location requireLocation(ApiRequest request) {
        if (request.has("player")) {
            return requireOnlinePlayer(request.requireString("player")).getLocation();
        }
        World world = resolveWorld(request.optString("world", null));
        return new Location(world, request.requireDouble("x"), request.requireDouble("y"),
                request.requireDouble("z"));
    }

    /** @return benannte Welt, oder — ohne Namen — die Welt des Arena-Zentrums. */
    public World resolveWorld(String name) {
        if (name != null && !name.isBlank()) {
            World world = Bukkit.getWorld(name);
            if (world == null) {
                throw new ApiBadRequestException("Unbekannte Welt: " + name);
            }
            return world;
        }
        Location center = GameState.getInstance().getArenaCenter();
        if (center == null || center.getWorld() == null) {
            throw new ApiBadRequestException(
                    "Keine Arena initialisiert — Feld \"world\" wird benötigt");
        }
        return center.getWorld();
    }

    // ==================== JSON-Darstellungen ====================

    /** Vollständige JSON-Darstellung eines Tributs (Main-Thread vorausgesetzt). */
    public JsonObject participantJson(Participant participant) {
        JsonObject entry = new JsonObject();
        entry.addProperty("uuid", participant.uuid().toString());
        entry.addProperty("name", participant.lastKnownName());
        entry.addProperty("alive", participant.isAlive());
        entry.addProperty("online", Bukkit.getPlayer(participant.uuid()) != null);
        entry.addProperty("kills", services.session.kills().kills(participant.uuid()));
        entry.addProperty("sponsorTokensEarned",
                services.sponsorTokens.earnedTotal(participant.uuid()));
        participant.teamId().flatMap(id -> services.session.teamRegistry().team(id))
                .ifPresentOrElse(team -> {
                    entry.addProperty("team", team.name());
                    entry.addProperty("teamId", team.id().value());
                    entry.addProperty("teamColor", team.color().name());
                }, () -> entry.add("team", null));
        return entry;
    }

    /** JSON-Darstellung eines Teams inklusive Mitglieder und Captain. */
    public JsonObject teamJson(Team team) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", team.id().value());
        entry.addProperty("name", team.name());
        entry.addProperty("color", team.color().name());
        var members = new com.google.gson.JsonArray();
        for (UUID member : team.members()) {
            JsonObject memberJson = new JsonObject();
            memberJson.addProperty("uuid", member.toString());
            memberJson.addProperty("name",
                    services.session.participants().nameOf(member).orElse("?"));
            memberJson.addProperty("alive", services.session.participants().isAlive(member));
            members.add(memberJson);
        }
        entry.add("members", members);
        entry.addProperty("size", team.size());
        team.captain().ifPresentOrElse(captain -> {
            entry.addProperty("captain", captain.toString());
            entry.addProperty("captainName",
                    services.session.participants().nameOf(captain).orElse("?"));
        }, () -> entry.add("captain", null));
        return entry;
    }

    /** Ort als JSON ({@code null}, wenn nicht gesetzt). */
    public static JsonObject locationJson(Location location) {
        if (location == null) {
            return null;
        }
        JsonObject json = new JsonObject();
        json.addProperty("world", location.getWorld() == null ? null : location.getWorld().getName());
        json.addProperty("x", location.getBlockX());
        json.addProperty("y", location.getBlockY());
        json.addProperty("z", location.getBlockZ());
        return json;
    }
}
