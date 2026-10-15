package com.hungergames.api.model;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An immutable snapshot of a team.
 *
 * @param id      stable identifier, never changes for the lifetime of the team
 * @param name    display name, may be changed until teams are locked
 * @param color   the claimed color; unique across all teams of a round
 * @param members the UUIDs of all members (alive and eliminated)
 * @param captain the captain, if the captain system is enabled and one is set
 */
public record Team(
        TeamId id,
        String name,
        TeamColor color,
        Set<UUID> members,
        Optional<UUID> captain) {

    public Team {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(captain, "captain");
        members = Set.copyOf(members);
    }

    /** @return {@code true} if the given player is a member of this team. */
    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    /** @return the number of members (alive and eliminated). */
    public int size() {
        return members.size();
    }
}
