package com.hungergames.game;

import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.model.Winner;
import com.hungergames.api.service.TeamOperationResult;
import com.hungergames.game.port.GameClock;
import com.hungergames.game.port.GameEventPublisher;
import com.hungergames.game.port.GameEventPublisher.MembershipCause;
import com.hungergames.game.port.SessionStore;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Aggregat-Wurzel einer Hunger-Games-Runde.
 *
 * <p>Alle Mutationen an Spielzustand, Teilnehmern und Teams laufen über diese
 * Klasse: sie validiert Phasenübergänge, feuert Events über den
 * {@link GameEventPublisher}-Port, persistiert nach jeder Änderung über den
 * {@link SessionStore} und stößt die Gewinnerermittlung an.</p>
 *
 * <p>Bukkit-frei und damit vollständig ohne Server testbar.</p>
 */
public final class GameSession {

    private static final Map<GamePhase, Set<GamePhase>> ALLOWED_TRANSITIONS = new EnumMap<>(Map.of(
            GamePhase.NOT_INITIALIZED, EnumSet.of(GamePhase.PREFLIGHT),
            GamePhase.PREFLIGHT, EnumSet.of(GamePhase.LOBBY),
            GamePhase.LOBBY, EnumSet.of(GamePhase.STARTUP),
            // STARTUP -> LOBBY ist der definierte Recovery-Pfad, falls die
            // Startsequenz abgebrochen werden muss (keine Spieler, Fehler).
            GamePhase.STARTUP, EnumSet.of(GamePhase.READY, GamePhase.LOBBY),
            GamePhase.READY, EnumSet.of(GamePhase.RUNNING),
            GamePhase.RUNNING, EnumSet.of(GamePhase.FINISHED),
            GamePhase.FINISHED, EnumSet.of(GamePhase.NOT_INITIALIZED)));

    private final ParticipantRegistry participants;
    private final TeamRegistry teams;
    private final KillTracker kills = new KillTracker();
    private final GameEventPublisher events;
    private final SessionStore store;
    private final GameClock clock;
    private final Random random;

    private GamePhase phase = GamePhase.NOT_INITIALIZED;
    private Winner winner;
    private Long runningSinceMillis;

    public GameSession(Supplier<TeamRules> teamRules, GameEventPublisher events,
                       SessionStore store, GameClock clock, Random random) {
        this.events = events;
        this.store = store;
        this.clock = clock;
        this.random = random;
        this.teams = new TeamRegistry(teamRules, this::phase, this::isWhitelisted);
        this.participants = new ParticipantRegistry(teams::teamIdOf);
    }

    // ==================== Phasen ====================

    public GamePhase phase() {
        return phase;
    }

    /**
     * Wechselt in die Zielphase, sofern der Übergang erlaubt ist.
     *
     * @return {@code false} bei unzulässigem Übergang (Zustand bleibt unverändert)
     */
    public boolean transitionTo(GamePhase target) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(phase, Set.of()).contains(target)) {
            return false;
        }
        GamePhase old = phase;
        phase = target;
        if (target == GamePhase.RUNNING) {
            runningSinceMillis = clock.nowMillis();
        }
        events.phaseChanged(old, target);
        persist();
        return true;
    }

    /** Setzt die komplette Session zurück (neue Runde, leere Whitelist). */
    public void reset() {
        GamePhase old = phase;
        phase = GamePhase.NOT_INITIALIZED;
        winner = null;
        runningSinceMillis = null;
        participants.clear();
        teams.clear();
        kills.reset();
        store.clear();
        if (old != GamePhase.NOT_INITIALIZED) {
            events.phaseChanged(old, GamePhase.NOT_INITIALIZED);
        }
    }

    /**
     * Beginnt eine neue Runde mit denselben Teilnehmern und Teams:
     * Eliminierungen, Kills und Sieger werden zurückgesetzt.
     */
    public void resetForNextRound() {
        GamePhase old = phase;
        phase = GamePhase.NOT_INITIALIZED;
        winner = null;
        runningSinceMillis = null;
        participants.resetStates();
        kills.reset();
        persist();
        if (old != GamePhase.NOT_INITIALIZED) {
            events.phaseChanged(old, GamePhase.NOT_INITIALIZED);
        }
    }

    // ==================== Whitelist / Teilnehmer ====================

    public boolean whitelistAdd(UUID uuid, String name) {
        if (!participants.add(uuid, name)) {
            return false;
        }
        events.whitelistChanged(uuid, true);
        persist();
        return true;
    }

    public boolean whitelistRemove(UUID uuid) {
        if (!participants.remove(uuid)) {
            return false;
        }
        Optional<TeamId> oldTeam = teams.forceRemove(uuid);
        oldTeam.ifPresent(old ->
                events.teamMembershipChanged(uuid, old, null, MembershipCause.API));
        events.whitelistChanged(uuid, false);
        persist();
        return true;
    }

    public boolean isWhitelisted(UUID uuid) {
        return participants.contains(uuid);
    }

    public ParticipantRegistry participants() {
        return participants;
    }

    /** Aktualisiert den Anzeigenamen (beim Join). */
    public void updateName(UUID uuid, String name) {
        participants.updateName(uuid, name);
        persist();
    }

    // ==================== Eliminierung / Sieg ====================

    /**
     * Eliminiert einen Teilnehmer (Tod oder Admin-Aktion).
     *
     * <p>Zählt ggf. den Kill, feuert Events und prüft anschließend auf einen
     * Sieger. Nur in der Phase RUNNING wirksam.</p>
     *
     * @param killer der Killer, oder {@code null}
     * @return {@code true}, wenn der Teilnehmer dadurch eliminiert wurde
     */
    public boolean eliminate(UUID victim, UUID killer) {
        if (phase != GamePhase.RUNNING) {
            return false;
        }
        if (!participants.eliminate(victim)) {
            return false;
        }

        if (killer != null && !killer.equals(victim) && participants.isAlive(killer)) {
            int total = kills.increment(killer);
            events.kill(killer, victim, total);
        }

        events.participantEliminated(victim, killer, participants.aliveCount());
        persist();

        WinnerResolver.resolve(participants.all()).ifPresent(this::finish);
        return true;
    }

    /** Admin-Korrektur: Eliminierung zurücknehmen. */
    public boolean revive(UUID uuid) {
        if (!participants.revive(uuid)) {
            return false;
        }
        events.participantRevived(uuid);
        persist();
        return true;
    }

    /** Beendet die Runde wegen Zeitablauf (liefert immer ein Ergebnis). */
    public void declareTimeout() {
        if (phase == GamePhase.RUNNING) {
            finish(WinnerResolver.resolveOnTimeout(participants.all()));
        }
    }

    public Optional<Winner> winner() {
        return Optional.ofNullable(winner);
    }

    private void finish(Winner result) {
        winner = result;
        transitionTo(GamePhase.FINISHED);
        events.winnerDeclared(result);
        persist();
    }

    // ==================== Teams (delegiert mit Events) ====================

    public TeamRegistry teamRegistry() {
        return teams;
    }

    public TeamRegistry.CreationResult teamCreate(String name, TeamColor color) {
        TeamRegistry.CreationResult result = teams.create(name, color);
        result.team().ifPresent(team -> {
            events.teamCreated(team);
            persist();
        });
        return result;
    }

    public TeamOperationResult teamDelete(TeamId id) {
        Optional<Team> deleted = teams.delete(id);
        if (deleted.isEmpty()) {
            return TeamOperationResult.TEAM_NOT_FOUND;
        }
        events.teamDeleted(deleted.get());
        persist();
        return TeamOperationResult.SUCCESS;
    }

    public TeamOperationResult teamRename(TeamId id, String newName) {
        TeamOperationResult result = teams.rename(id, newName);
        if (result.isSuccess()) {
            persist();
        }
        return result;
    }

    public TeamOperationResult teamSetColor(TeamId id, TeamColor color) {
        Optional<Team> before = teams.team(id);
        TeamOperationResult result = teams.setColor(id, color);
        if (result.isSuccess() && before.isPresent() && before.get().color() != color) {
            events.teamColorChanged(teams.team(id).orElseThrow(), before.get().color(), color);
            persist();
        }
        return result;
    }

    public TeamOperationResult teamAssign(UUID player, TeamId target, MembershipCause cause) {
        TeamRegistry.MembershipChange change = teams.assign(player, target);
        if (change.status().isSuccess()) {
            events.teamMembershipChanged(player, change.oldTeam().orElse(null), target, cause);
            persist();
        }
        return change.status();
    }

    public TeamOperationResult teamRemovePlayer(UUID player, MembershipCause cause) {
        TeamRegistry.MembershipChange change = teams.removeFromTeam(player);
        if (change.status().isSuccess()) {
            events.teamMembershipChanged(player, change.oldTeam().orElse(null), null, cause);
            persist();
        }
        return change.status();
    }

    public TeamOperationResult teamSetCaptain(TeamId id, UUID player) {
        TeamOperationResult result = teams.setCaptain(id, player);
        if (result.isSuccess()) {
            persist();
        }
        return result;
    }

    /** Zufallszuweisung aller teamlosen Teilnehmer. @return Anzahl zugewiesener Spieler */
    public int teamAssignRandomly() {
        Map<UUID, TeamId> assigned = teams.assignRandomly(participants.alive(), random);
        assigned.forEach((player, team) ->
                events.teamMembershipChanged(player, null, team, MembershipCause.RANDOM));
        if (!assigned.isEmpty()) {
            persist();
        }
        return assigned.size();
    }

    // ==================== Stats ====================

    public KillTracker kills() {
        return kills;
    }

    /** @return Epochen-Zeitpunkt des RUNNING-Beginns, falls gestartet. */
    public Optional<Long> runningSinceMillis() {
        return Optional.ofNullable(runningSinceMillis);
    }

    // ==================== Persistenz ====================

    /** Stellt die Session aus einem gespeicherten Snapshot wieder her. */
    public void restore(SessionSnapshot snapshot) {
        phase = snapshot.phase();
        winner = snapshot.winner();
        runningSinceMillis = snapshot.runningSinceMillis();
        teams.restore(snapshot.teams());
        participants.restore(snapshot.participants());
        kills.restore(snapshot.kills());
    }

    public SessionSnapshot snapshot() {
        return new SessionSnapshot(
                phase,
                participants.all().stream()
                        .map(p -> new SessionSnapshot.ParticipantData(p.uuid(), p.lastKnownName(), p.state()))
                        .toList(),
                teams.all().stream()
                        .map(t -> new SessionSnapshot.TeamData(t.id(), t.name(), t.color(),
                                t.members().stream().toList(), t.captain().orElse(null)))
                        .toList(),
                winner,
                kills.snapshot(),
                runningSinceMillis);
    }

    private void persist() {
        store.save(snapshot());
    }
}
