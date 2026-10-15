package com.hungergames.game;

import com.hungergames.api.model.Participant;
import com.hungergames.api.model.ParticipantState;
import com.hungergames.api.model.TeamId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Zentrale Teilnehmerverwaltung einer Runde. UUID ist die einzige Identität;
 * Namen sind reiner Anzeige-Cache.
 *
 * <p>Der Online-Status ist bewusst kein Teil des Zustands: Disconnects ändern
 * nichts an {@link ParticipantState#ALIVE}.</p>
 *
 * <p>Die Teamzuordnung lebt ausschließlich im {@link TeamRegistry}; beim
 * Erzeugen von {@link Participant}-Snapshots wird sie über die injizierte
 * {@code teamLookup}-Funktion angereichert (keine doppelte Wahrheit).</p>
 */
public final class ParticipantRegistry {

    private record Entry(String name, ParticipantState state) {
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Function<UUID, Optional<TeamId>> teamLookup;

    /**
     * @param teamLookup liefert die aktuelle Teamzugehörigkeit eines Spielers
     *                   (typisch {@code teamRegistry::teamIdOf})
     */
    public ParticipantRegistry(Function<UUID, Optional<TeamId>> teamLookup) {
        this.teamLookup = teamLookup;
    }

    /** Registriert einen Teilnehmer (Whitelist-Aufnahme). */
    public boolean add(UUID uuid, String name) {
        if (entries.containsKey(uuid)) {
            return false;
        }
        entries.put(uuid, new Entry(name, ParticipantState.ALIVE));
        return true;
    }

    /** Entfernt einen Teilnehmer vollständig (Whitelist-Austrag). */
    public boolean remove(UUID uuid) {
        return entries.remove(uuid) != null;
    }

    public boolean contains(UUID uuid) {
        return entries.containsKey(uuid);
    }

    /** Aktualisiert den Anzeige-Namen (z. B. beim Join). */
    public void updateName(UUID uuid, String name) {
        Entry entry = entries.get(uuid);
        if (entry != null && !entry.name().equals(name)) {
            entries.put(uuid, new Entry(name, entry.state()));
        }
    }

    /**
     * Markiert einen Teilnehmer als eliminiert.
     *
     * @return {@code false}, wenn unbekannt oder bereits eliminiert
     */
    public boolean eliminate(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry == null || entry.state() == ParticipantState.ELIMINATED) {
            return false;
        }
        entries.put(uuid, new Entry(entry.name(), ParticipantState.ELIMINATED));
        return true;
    }

    /** Macht eine Eliminierung rückgängig (Admin-Korrektur). */
    public boolean revive(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry == null || entry.state() == ParticipantState.ALIVE) {
            return false;
        }
        entries.put(uuid, new Entry(entry.name(), ParticipantState.ALIVE));
        return true;
    }

    public boolean isAlive(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry != null && entry.state() == ParticipantState.ALIVE;
    }

    public Optional<Participant> get(UUID uuid) {
        Entry entry = entries.get(uuid);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(toParticipant(uuid, entry));
    }

    public Optional<String> nameOf(UUID uuid) {
        Entry entry = entries.get(uuid);
        return entry == null ? Optional.empty() : Optional.of(entry.name());
    }

    /** @return Snapshot aller Teilnehmer. */
    public Collection<Participant> all() {
        return entries.entrySet().stream()
                .map(e -> toParticipant(e.getKey(), e.getValue()))
                .toList();
    }

    /** @return UUIDs aller Lebenden (online wie offline). */
    public Set<UUID> alive() {
        return byState(ParticipantState.ALIVE);
    }

    /** @return UUIDs aller Eliminierten. */
    public Set<UUID> eliminated() {
        return byState(ParticipantState.ELIMINATED);
    }

    public int aliveCount() {
        return alive().size();
    }

    /** Setzt alle Eliminierungen zurück (neue Runde, Whitelist bleibt). */
    public void resetStates() {
        entries.replaceAll((uuid, entry) -> new Entry(entry.name(), ParticipantState.ALIVE));
    }

    /** Leert die Registry vollständig. */
    public void clear() {
        entries.clear();
    }

    /** Stellt einen gespeicherten Zustand wieder her (Session-Restore). */
    public void restore(Collection<SessionSnapshot.ParticipantData> saved) {
        entries.clear();
        for (SessionSnapshot.ParticipantData data : saved) {
            entries.put(data.uuid(), new Entry(data.name(), data.state()));
        }
    }

    private Set<UUID> byState(ParticipantState state) {
        Set<UUID> result = new LinkedHashSet<>();
        entries.forEach((uuid, entry) -> {
            if (entry.state() == state) {
                result.add(uuid);
            }
        });
        return Set.copyOf(result);
    }

    private Participant toParticipant(UUID uuid, Entry entry) {
        return new Participant(uuid, entry.name(), entry.state(), teamLookup.apply(uuid));
    }
}
