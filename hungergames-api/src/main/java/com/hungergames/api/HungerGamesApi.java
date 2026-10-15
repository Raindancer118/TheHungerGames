package com.hungergames.api;

import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Winner;
import com.hungergames.api.service.ParticipantService;
import com.hungergames.api.service.StatsService;
import com.hungergames.api.service.TeamService;
import com.hungergames.api.service.WhitelistService;

import java.util.Optional;

/**
 * Public entry point of the HungerGames plugin API.
 *
 * <p>Obtain an instance via {@link HungerGamesProvider#get()} or through the
 * Bukkit services manager:</p>
 *
 * <pre>{@code
 * HungerGamesApi api = Bukkit.getServicesManager().load(HungerGamesApi.class);
 * }</pre>
 *
 * <p>Unless documented otherwise, all methods must be called from the server
 * main thread. Players are identified by UUID throughout; the API never
 * exposes live {@code org.bukkit.entity.Player} handles or internal manager
 * classes.</p>
 */
public interface HungerGamesApi {

    /** @return the current game phase. */
    GamePhase phase();

    /**
     * @return the round result once the game is {@link GamePhase#FINISHED};
     *         empty while the round is still open
     */
    Optional<Winner> winner();

    /** @return the tournament whitelist. */
    WhitelistService whitelist();

    /** @return read access to participants (alive/eliminated). */
    ParticipantService participants();

    /** @return team management. */
    TeamService teams();

    /** @return live statistics (kills, leaderboard, game time). */
    StatsService stats();
}
