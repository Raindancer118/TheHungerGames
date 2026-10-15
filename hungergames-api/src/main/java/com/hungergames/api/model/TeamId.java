package com.hungergames.api.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Stable, case-insensitive identifier of a team.
 *
 * <p>The id is independent of the team's display name and color, both of
 * which may change during the lobby phase.</p>
 *
 * @param value the normalized (lowercase) id, e.g. {@code "team-3"} or {@code "rote-raben"}
 */
public record TeamId(String value) {

    public TeamId {
        Objects.requireNonNull(value, "value");
        value = value.toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("TeamId must not be blank");
        }
    }

    /** Creates a TeamId from a display name (lowercased, spaces to dashes). */
    public static TeamId fromName(String name) {
        return new TeamId(name.trim().replaceAll("\\s+", "-"));
    }

    @Override
    public String toString() {
        return value;
    }
}
