package com.hungergames.game;

import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.service.TeamOperationResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Teamverwaltung einer Runde: Mitgliedschaften, exklusive Farben, Regeln.
 *
 * <p>Einzige Quelle der Wahrheit für Teamzugehörigkeit. Alle Wege
 * (API, Admin, Spieler-GUI, Zufallszuweisung) laufen über diese Klasse und
 * unterliegen denselben Regeln ({@link TeamRules}).</p>
 */
public final class TeamRegistry {

    private static final class MutableTeam {
        String name;
        TeamColor color;
        final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        UUID captain;

        MutableTeam(String name, TeamColor color) {
            this.name = name;
            this.color = color;
        }
    }

    /** Ergebnis einer Mitgliedschafts-Operation inklusive vorherigem Team (für Events). */
    public record MembershipChange(TeamOperationResult status, Optional<TeamId> oldTeam) {

        static MembershipChange failure(TeamOperationResult status) {
            return new MembershipChange(status, Optional.empty());
        }
    }

    private final Map<TeamId, MutableTeam> teams = new LinkedHashMap<>();
    private final Supplier<TeamRules> rules;
    private final Supplier<GamePhase> phase;
    private final Predicate<UUID> isWhitelisted;

    /**
     * @param rules         aktuelle Regeln (bei jedem Zugriff frisch gelesen)
     * @param phase         aktuelle Spielphase
     * @param isWhitelisted prüft, ob ein Spieler Turnierteilnehmer ist
     */
    public TeamRegistry(Supplier<TeamRules> rules, Supplier<GamePhase> phase, Predicate<UUID> isWhitelisted) {
        this.rules = rules;
        this.phase = phase;
        this.isWhitelisted = isWhitelisted;
    }

    // ==================== CRUD ====================

    /**
     * Legt ein Team an. {@code requestedColor == null} wählt automatisch die
     * erste freie Farbe.
     */
    public CreationResult create(String name, TeamColor requestedColor) {
        if (!rules.get().enabled()) {
            return CreationResult.failure(TeamOperationResult.TEAMS_DISABLED);
        }
        if (locked()) {
            return CreationResult.failure(TeamOperationResult.PHASE_LOCKED);
        }
        TeamId id = TeamId.fromName(name);
        if (teams.containsKey(id) || nameTaken(name, null)) {
            return CreationResult.failure(TeamOperationResult.NAME_TAKEN);
        }
        TeamRules r = rules.get();
        if (r.maxTeams() > 0 && teams.size() >= r.maxTeams()) {
            return CreationResult.failure(TeamOperationResult.TEAM_LIMIT_REACHED);
        }

        TeamColor color = requestedColor;
        if (color == null) {
            Set<TeamColor> free = availableColors();
            if (free.isEmpty()) {
                return CreationResult.failure(TeamOperationResult.COLOR_TAKEN);
            }
            color = free.iterator().next();
        } else if (colorTaken(color, null)) {
            return CreationResult.failure(TeamOperationResult.COLOR_TAKEN);
        }

        teams.put(id, new MutableTeam(name.trim(), color));
        return new CreationResult(TeamOperationResult.SUCCESS, Optional.of(snapshot(id)));
    }

    /** Ergebnis von {@link #create}; {@code team} vorhanden bei Erfolg. */
    public record CreationResult(TeamOperationResult status, Optional<Team> team) {

        static CreationResult failure(TeamOperationResult status) {
            return new CreationResult(status, Optional.empty());
        }
    }

    /**
     * Löscht ein Team.
     *
     * @return Snapshot des gelöschten Teams (für das Event), oder leer bei Fehler
     */
    public Optional<Team> delete(TeamId id) {
        if (!teams.containsKey(id)) {
            return Optional.empty();
        }
        Team snapshot = snapshot(id);
        teams.remove(id);
        return Optional.of(snapshot);
    }

    public TeamOperationResult rename(TeamId id, String newName) {
        MutableTeam team = teams.get(id);
        if (team == null) {
            return TeamOperationResult.TEAM_NOT_FOUND;
        }
        if (locked()) {
            return TeamOperationResult.PHASE_LOCKED;
        }
        if (nameTaken(newName, id)) {
            return TeamOperationResult.NAME_TAKEN;
        }
        team.name = newName.trim();
        return TeamOperationResult.SUCCESS;
    }

    // ==================== Farben ====================

    /**
     * Weist einem Team eine Farbe zu. Bereits vergebene Farben werden strikt
     * abgelehnt — niemals stillschweigend umverteilt.
     */
    public TeamOperationResult setColor(TeamId id, TeamColor color) {
        MutableTeam team = teams.get(id);
        if (team == null) {
            return TeamOperationResult.TEAM_NOT_FOUND;
        }
        if (team.color == color) {
            return TeamOperationResult.SUCCESS;
        }
        if (locked()) {
            return TeamOperationResult.PHASE_LOCKED;
        }
        if (colorTaken(color, id)) {
            return TeamOperationResult.COLOR_TAKEN;
        }
        team.color = color;
        return TeamOperationResult.SUCCESS;
    }

    /** @return alle aktuell nicht beanspruchten Farben. */
    public Set<TeamColor> availableColors() {
        EnumSet<TeamColor> free = EnumSet.allOf(TeamColor.class);
        for (MutableTeam team : teams.values()) {
            free.remove(team.color);
        }
        return free;
    }

    // ==================== Mitgliedschaft ====================

    /** Weist einen Spieler einem Team zu (inkl. Wechsel aus dem alten Team). */
    public MembershipChange assign(UUID player, TeamId target) {
        if (!rules.get().enabled()) {
            return MembershipChange.failure(TeamOperationResult.TEAMS_DISABLED);
        }
        if (!isWhitelisted.test(player)) {
            return MembershipChange.failure(TeamOperationResult.PLAYER_NOT_WHITELISTED);
        }
        MutableTeam team = teams.get(target);
        if (team == null) {
            return MembershipChange.failure(TeamOperationResult.TEAM_NOT_FOUND);
        }
        if (team.members.contains(player)) {
            return MembershipChange.failure(TeamOperationResult.ALREADY_MEMBER);
        }
        if (locked()) {
            return MembershipChange.failure(TeamOperationResult.PHASE_LOCKED);
        }

        Optional<TeamId> oldTeam = teamIdOf(player);
        TeamRules r = rules.get();
        if (oldTeam.isPresent() && !r.allowSwitching()) {
            return MembershipChange.failure(TeamOperationResult.SWITCHING_DISABLED);
        }
        if (r.maxTeamSize() > 0 && team.members.size() >= r.maxTeamSize()) {
            return MembershipChange.failure(TeamOperationResult.TEAM_FULL);
        }

        oldTeam.ifPresent(old -> removeMember(old, player));
        team.members.add(player);
        return new MembershipChange(TeamOperationResult.SUCCESS, oldTeam);
    }

    /** Entfernt einen Spieler aus seinem Team. */
    public MembershipChange removeFromTeam(UUID player) {
        Optional<TeamId> oldTeam = teamIdOf(player);
        if (oldTeam.isEmpty()) {
            return MembershipChange.failure(TeamOperationResult.NOT_A_MEMBER);
        }
        if (locked()) {
            return MembershipChange.failure(TeamOperationResult.PHASE_LOCKED);
        }
        removeMember(oldTeam.get(), player);
        return new MembershipChange(TeamOperationResult.SUCCESS, oldTeam);
    }

    /** Entfernt einen Spieler regellos (Whitelist-Austrag, kein Phasen-Check). */
    public Optional<TeamId> forceRemove(UUID player) {
        Optional<TeamId> oldTeam = teamIdOf(player);
        oldTeam.ifPresent(old -> removeMember(old, player));
        return oldTeam;
    }

    public TeamOperationResult setCaptain(TeamId id, UUID player) {
        MutableTeam team = teams.get(id);
        if (team == null) {
            return TeamOperationResult.TEAM_NOT_FOUND;
        }
        if (!rules.get().captainEnabled()) {
            return TeamOperationResult.SWITCHING_DISABLED;
        }
        if (!team.members.contains(player)) {
            return TeamOperationResult.NOT_A_MEMBER;
        }
        team.captain = player;
        return TeamOperationResult.SUCCESS;
    }

    /**
     * Verteilt teamlose Turnierteilnehmer zufällig: erst vorhandene Teams
     * auffüllen (kleinste zuerst), bei Bedarf neue Teams anlegen, solange
     * Regeln und Farben es zulassen.
     *
     * @return die zugewiesenen Spieler mit ihrem neuen Team
     */
    public Map<UUID, TeamId> assignRandomly(Collection<UUID> candidates, Random random) {
        Map<UUID, TeamId> assigned = new LinkedHashMap<>();
        if (!rules.get().enabled() || locked()) {
            return assigned;
        }

        List<UUID> unassigned = new ArrayList<>();
        for (UUID candidate : candidates) {
            if (isWhitelisted.test(candidate) && teamIdOf(candidate).isEmpty()) {
                unassigned.add(candidate);
            }
        }
        java.util.Collections.shuffle(unassigned, random);

        TeamRules r = rules.get();
        int autoTeamCounter = teams.size();

        for (UUID player : unassigned) {
            TeamId target = smallestOpenTeam(r);
            if (target == null) {
                if (r.maxTeams() > 0 && teams.size() >= r.maxTeams()) {
                    break;
                }
                CreationResult created = create("Team " + (++autoTeamCounter), null);
                while (created.status() == TeamOperationResult.NAME_TAKEN) {
                    created = create("Team " + (++autoTeamCounter), null);
                }
                if (!created.status().isSuccess()) {
                    break; // keine Farben/Teams mehr frei
                }
                target = created.team().orElseThrow().id();
            }
            teams.get(target).members.add(player);
            assigned.put(player, target);
        }
        return assigned;
    }

    // ==================== Abfragen ====================

    public Optional<TeamId> teamIdOf(UUID player) {
        for (Map.Entry<TeamId, MutableTeam> entry : teams.entrySet()) {
            if (entry.getValue().members.contains(player)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public Optional<Team> team(TeamId id) {
        return teams.containsKey(id) ? Optional.of(snapshot(id)) : Optional.empty();
    }

    public Optional<Team> teamOf(UUID player) {
        return teamIdOf(player).map(this::snapshot);
    }

    /** @return Snapshots aller Teams in Anlage-Reihenfolge. */
    public List<Team> all() {
        return teams.keySet().stream().map(this::snapshot).toList();
    }

    public void clear() {
        teams.clear();
    }

    /** @return {@code false}, wenn {@code teams.enabled=false} konfiguriert ist (Solo-Modus). */
    public boolean enabled() {
        return rules.get().enabled();
    }

    /** Stellt einen gespeicherten Zustand wieder her (Session-Restore). */
    public void restore(Collection<SessionSnapshot.TeamData> saved) {
        teams.clear();
        for (SessionSnapshot.TeamData data : saved) {
            MutableTeam team = new MutableTeam(data.name(), data.color());
            team.members.addAll(data.members());
            team.captain = data.captain();
            teams.put(data.id(), team);
        }
    }

    // ==================== intern ====================

    private Team snapshot(TeamId id) {
        MutableTeam team = teams.get(id);
        return new Team(id, team.name, team.color, Set.copyOf(team.members),
                Optional.ofNullable(team.captain));
    }

    private boolean locked() {
        return rules.get().isLocked(phase.get());
    }

    private boolean nameTaken(String name, TeamId exclude) {
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        for (Map.Entry<TeamId, MutableTeam> entry : teams.entrySet()) {
            if (!entry.getKey().equals(exclude)
                    && entry.getValue().name.toLowerCase(java.util.Locale.ROOT).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean colorTaken(TeamColor color, TeamId exclude) {
        for (Map.Entry<TeamId, MutableTeam> entry : teams.entrySet()) {
            if (!entry.getKey().equals(exclude) && entry.getValue().color == color) {
                return true;
            }
        }
        return false;
    }

    private void removeMember(TeamId id, UUID player) {
        MutableTeam team = teams.get(id);
        if (team != null) {
            team.members.remove(player);
            if (player.equals(team.captain)) {
                team.captain = null;
            }
        }
    }

    private TeamId smallestOpenTeam(TeamRules r) {
        TeamId best = null;
        int bestSize = Integer.MAX_VALUE;
        for (Map.Entry<TeamId, MutableTeam> entry : teams.entrySet()) {
            int size = entry.getValue().members.size();
            boolean open = r.maxTeamSize() == 0 || size < r.maxTeamSize();
            if (open && size < bestSize) {
                best = entry.getKey();
                bestSize = size;
            }
        }
        return best;
    }
}
