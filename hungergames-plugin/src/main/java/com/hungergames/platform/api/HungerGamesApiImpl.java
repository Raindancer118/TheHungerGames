package com.hungergames.platform.api;

import com.hungergames.api.HungerGamesApi;
import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Participant;
import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.model.Winner;
import com.hungergames.api.service.ParticipantService;
import com.hungergames.api.service.StatsService;
import com.hungergames.api.service.TeamOperationResult;
import com.hungergames.api.service.TeamService;
import com.hungergames.api.service.WhitelistService;
import com.hungergames.game.GameSession;
import com.hungergames.game.port.GameEventPublisher.MembershipCause;
import com.hungergames.platform.NameResolver;
import com.hungergames.platform.VirtualTime;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.game.config.ConfigurationService;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementierung der öffentlichen API — eine dünne Fassade über der
 * {@link GameSession}. Gibt ausschließlich UUIDs und unveränderliche
 * API-Modelle heraus, niemals interne Manager oder Bukkit-Player.
 *
 * <p>Thread-Regel: Die Session ist nicht synchronisiert. Asynchrone Pfade
 * (Namensauflösung) dispatchen ihre Mutation deshalb über
 * {@code mainThread} zurück auf den Server-Main-Thread.</p>
 */
public final class HungerGamesApiImpl implements HungerGamesApi {

    private final GameSession session;
    private final NameResolver nameResolver;
    private final VirtualTime virtualTime;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final java.util.concurrent.Executor mainThread;

    private final WhitelistService whitelist = new WhitelistImpl();
    private final ParticipantService participants = new ParticipantsImpl();
    private final TeamService teams = new TeamsImpl();
    private final StatsService stats = new StatsImpl();

    public HungerGamesApiImpl(GameSession session, NameResolver nameResolver,
                              VirtualTime virtualTime, ConfigurationService config,
                              HgSettings settings, java.util.concurrent.Executor mainThread) {
        this.session = session;
        this.nameResolver = nameResolver;
        this.virtualTime = virtualTime;
        this.config = config;
        this.settings = settings;
        this.mainThread = mainThread;
    }

    @Override
    public GamePhase phase() {
        return session.phase();
    }

    @Override
    public Optional<Winner> winner() {
        return session.winner();
    }

    @Override
    public WhitelistService whitelist() {
        return whitelist;
    }

    @Override
    public ParticipantService participants() {
        return participants;
    }

    @Override
    public TeamService teams() {
        return teams;
    }

    @Override
    public StatsService stats() {
        return stats;
    }

    private final class WhitelistImpl implements WhitelistService {

        @Override
        public boolean add(UUID uuid) {
            return session.whitelistAdd(uuid, uuid.toString().substring(0, 8));
        }

        @Override
        public boolean add(UUID uuid, String knownName) {
            return session.whitelistAdd(uuid, knownName);
        }

        @Override
        public CompletableFuture<UUID> addByName(String name) {
            // Auflösung async, Mutation auf dem Main-Thread — die Session
            // ist nicht für konkurrierende Schreibzugriffe ausgelegt.
            return nameResolver.resolve(name).thenApplyAsync(resolved -> {
                session.whitelistAdd(resolved.uuid(), resolved.name());
                return resolved.uuid();
            }, mainThread);
        }

        @Override
        public boolean remove(UUID uuid) {
            return session.whitelistRemove(uuid);
        }

        @Override
        public CompletableFuture<Boolean> removeByName(String name) {
            return nameResolver.resolve(name).thenApplyAsync(
                    resolved -> session.whitelistRemove(resolved.uuid()), mainThread);
        }

        @Override
        public boolean isWhitelisted(UUID uuid) {
            return session.isWhitelisted(uuid);
        }

        @Override
        public Set<UUID> entries() {
            return session.participants().all().stream()
                    .map(Participant::uuid)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        @Override
        public Optional<String> nameOf(UUID uuid) {
            return session.participants().nameOf(uuid);
        }
    }

    private final class ParticipantsImpl implements ParticipantService {

        @Override
        public Collection<Participant> participants() {
            return session.participants().all();
        }

        @Override
        public Optional<Participant> participant(UUID uuid) {
            return session.participants().get(uuid);
        }

        @Override
        public Set<UUID> alive() {
            return session.participants().alive();
        }

        @Override
        public Set<UUID> eliminated() {
            return session.participants().eliminated();
        }

        @Override
        public boolean isAlive(UUID uuid) {
            return session.participants().isAlive(uuid);
        }
    }

    private final class TeamsImpl implements TeamService {

        @Override
        public TeamCreation create(String name) {
            return toApi(session.teamCreate(name, null));
        }

        @Override
        public TeamCreation create(String name, TeamColor color) {
            return toApi(session.teamCreate(name, color));
        }

        private TeamCreation toApi(com.hungergames.game.TeamRegistry.CreationResult result) {
            return new TeamCreation(result.status(), result.team());
        }

        @Override
        public TeamOperationResult delete(TeamId id) {
            return session.teamDelete(id);
        }

        @Override
        public TeamOperationResult rename(TeamId id, String newName) {
            return session.teamRename(id, newName);
        }

        @Override
        public TeamOperationResult setColor(TeamId id, TeamColor color) {
            return session.teamSetColor(id, color);
        }

        @Override
        public Set<TeamColor> availableColors() {
            return session.teamRegistry().availableColors();
        }

        @Override
        public TeamOperationResult assign(UUID player, TeamId team) {
            return session.teamAssign(player, team, MembershipCause.API);
        }

        @Override
        public TeamOperationResult removeFromTeam(UUID player) {
            return session.teamRemovePlayer(player, MembershipCause.API);
        }

        @Override
        public TeamOperationResult setCaptain(TeamId id, UUID player) {
            return session.teamSetCaptain(id, player);
        }

        @Override
        public int assignRandomly() {
            return session.teamAssignRandomly();
        }

        @Override
        public List<Team> teams() {
            return session.teamRegistry().all();
        }

        @Override
        public Optional<Team> team(TeamId id) {
            return session.teamRegistry().team(id);
        }

        @Override
        public Optional<Team> teamOf(UUID player) {
            return session.teamRegistry().teamOf(player);
        }
    }

    private final class StatsImpl implements StatsService {

        @Override
        public int kills(UUID participant) {
            return session.kills().kills(participant);
        }

        @Override
        public Map<UUID, Integer> killCounts() {
            return session.kills().snapshot();
        }

        @Override
        public List<KillEntry> topKillers(int limit) {
            return session.kills().top(limit).stream()
                    .map(entry -> new KillEntry(entry.getKey(),
                            session.participants().nameOf(entry.getKey()).orElse("?"),
                            entry.getValue()))
                    .toList();
        }

        @Override
        public int teamKills(TeamId teamId) {
            return session.teamRegistry().team(teamId)
                    .map(team -> team.members().stream()
                            .mapToInt(member -> session.kills().kills(member))
                            .sum())
                    .orElse(0);
        }

        @Override
        public Optional<Duration> elapsedGameTime() {
            if (virtualTime.isRunning()) {
                return Optional.of(virtualTime.elapsed());
            }
            return Optional.empty();
        }

        @Override
        public Duration configuredGameDuration() {
            return config.get(settings.gameDuration);
        }

        @Override
        public int aliveCount() {
            return session.participants().aliveCount();
        }

        @Override
        public int eliminatedCount() {
            return session.participants().eliminated().size();
        }
    }
}
