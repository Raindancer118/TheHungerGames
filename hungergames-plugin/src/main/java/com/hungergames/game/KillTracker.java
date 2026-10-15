package com.hungergames.game;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zählt Kills pro Teilnehmer innerhalb einer Runde.
 */
public final class KillTracker {

    private final Map<UUID, Integer> kills = new LinkedHashMap<>();

    /**
     * Erhöht den Kill-Zähler.
     *
     * @return der neue Gesamtstand des Killers
     */
    public int increment(UUID killer) {
        return kills.merge(killer, 1, Integer::sum);
    }

    public int kills(UUID participant) {
        return kills.getOrDefault(participant, 0);
    }

    /** @return unveränderliche Momentaufnahme aller Zählerstände. */
    public Map<UUID, Integer> snapshot() {
        return Map.copyOf(kills);
    }

    /** @return Teilnehmer absteigend nach Kills, maximal {@code limit} Einträge. */
    public List<Map.Entry<UUID, Integer>> top(int limit) {
        return kills.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(limit)
                .toList();
    }

    /** Stellt gespeicherte Zählerstände wieder her (Session-Restore). */
    public void restore(Map<UUID, Integer> saved) {
        kills.clear();
        kills.putAll(saved);
    }

    public void reset() {
        kills.clear();
    }
}
