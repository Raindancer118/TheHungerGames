package com.hungergames.api.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the tournament whitelist (who may take part in the round).
 *
 * <p>All players are identified by UUID. Name-based methods resolve the name
 * asynchronously via the Mojang profile API and must therefore not be awaited
 * on the server main thread.</p>
 *
 * <p>All mutating methods fire {@code WhitelistChangedEvent} on success.</p>
 */
public interface WhitelistService {

    /**
     * Adds a player to the tournament whitelist by UUID.
     *
     * @param uuid the player's UUID
     * @return {@code true} if the player was added, {@code false} if already whitelisted
     */
    boolean add(UUID uuid);

    /**
     * Adds a player by UUID with a known display name (avoids a later lookup).
     *
     * @return {@code true} if the player was added, {@code false} if already whitelisted
     */
    boolean add(UUID uuid, String knownName);

    /**
     * Resolves the given username to a UUID (asynchronously, via server cache
     * or the Mojang profile API) and adds the player to the whitelist.
     *
     * @param name the current username
     * @return a future completing with the resolved UUID; completes
     *         exceptionally with {@link IllegalArgumentException} if no such
     *         account exists
     */
    CompletableFuture<UUID> addByName(String name);

    /**
     * Removes a player from the tournament whitelist.
     *
     * @return {@code true} if the player had been whitelisted
     */
    boolean remove(UUID uuid);

    /**
     * Removes a player by name, resolving asynchronously.
     *
     * @return a future completing with {@code true} if the player had been whitelisted
     */
    CompletableFuture<Boolean> removeByName(String name);

    /** @return whether the given player is on the tournament whitelist. */
    boolean isWhitelisted(UUID uuid);

    /** @return the UUIDs of all whitelisted players (immutable snapshot). */
    Set<UUID> entries();

    /** @return the last known username of a whitelisted player. */
    Optional<String> nameOf(UUID uuid);
}
