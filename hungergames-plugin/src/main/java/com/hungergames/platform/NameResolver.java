package com.hungergames.platform;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Löst Spielernamen asynchron zu UUIDs auf: erst Online-Spieler, dann
 * Server-Cache, zuletzt Mojang-Profil-API — nie blockierend auf dem
 * Main-Thread.
 */
public final class NameResolver {

    /** Ergebnis einer Auflösung: UUID plus kanonischer Name. */
    public record Resolved(UUID uuid, String name) {
    }

    private final Plugin plugin;

    public NameResolver(Plugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Resolved> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(new Resolved(online.getUniqueId(), online.getName()));
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.getName() != null) {
            return CompletableFuture.completedFuture(new Resolved(cached.getUniqueId(), cached.getName()));
        }

        CompletableFuture<Resolved> future = new CompletableFuture<>();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            PlayerProfile profile = Bukkit.createProfile(name);
            if (profile.complete(false) && profile.getId() != null) {
                future.complete(new Resolved(profile.getId(),
                        profile.getName() != null ? profile.getName() : name));
            } else {
                future.completeExceptionally(new IllegalArgumentException(
                        "Kein Minecraft-Account mit dem Namen '" + name + "' gefunden"));
            }
        });
        return future;
    }
}
