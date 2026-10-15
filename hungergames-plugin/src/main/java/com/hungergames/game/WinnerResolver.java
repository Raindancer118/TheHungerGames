package com.hungergames.game;

import com.hungergames.api.model.Participant;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.model.Winner;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reine Gewinnerermittlung — keine Bukkit-Abhängigkeit, kein Zustand.
 *
 * <p>Grundsatz: Der Online-Status spielt keine Rolle. Ein Teilnehmer mit
 * Verbindungsabbruch bleibt lebendig und verhindert damit eine vorzeitige
 * Siegerkürung.</p>
 *
 * <p>Regeln:</p>
 * <ul>
 *   <li>Mehrere Lebende aus mehr als einem Team (oder teamlose neben anderen
 *       Lebenden) → noch kein Sieger.</li>
 *   <li>Alle Lebenden gehören demselben Team an → Teamsieg.</li>
 *   <li>Genau ein Lebender: Teamsieg seines Teams, falls er eines hat
 *       (bewusste Änderung gegenüber v0.16, die hier immer „solo" meldete),
 *       sonst Solosieg.</li>
 *   <li>Keine Lebenden mehr → {@link Winner.None}.</li>
 * </ul>
 */
public final class WinnerResolver {

    private WinnerResolver() {
    }

    /**
     * Prüft, ob der aktuelle Teilnehmerstand einen Sieger ergibt.
     *
     * @param participants alle Teilnehmer der Runde (mit Teamzuordnung)
     * @return der Sieger, oder leer solange das Spiel weiterläuft
     */
    public static Optional<Winner> resolve(Collection<Participant> participants) {
        List<Participant> alive = participants.stream()
                .filter(Participant::isAlive)
                .toList();

        if (alive.isEmpty()) {
            return Optional.of(new Winner.None());
        }

        if (alive.size() == 1) {
            Participant last = alive.get(0);
            return Optional.of(last.teamId()
                    .<Winner>map(teamId -> teamWin(teamId, participants))
                    .orElseGet(() -> new Winner.Solo(last.uuid())));
        }

        Set<TeamId> aliveTeams = new HashSet<>();
        for (Participant p : alive) {
            if (p.teamId().isEmpty()) {
                // Teamloser Lebender neben anderen Lebenden → Spiel läuft weiter.
                return Optional.empty();
            }
            aliveTeams.add(p.teamId().get());
        }

        if (aliveTeams.size() == 1) {
            return Optional.of(teamWin(aliveTeams.iterator().next(), participants));
        }

        return Optional.empty();
    }

    /**
     * Ermittelt das Ergebnis bei Zeitablauf. Liefert — anders als
     * {@link #resolve} — immer ein Ergebnis: steht kein Sieger fest, endet
     * die Runde mit {@link Winner.None}.
     */
    public static Winner resolveOnTimeout(Collection<Participant> participants) {
        return resolve(participants).orElseGet(Winner.None::new);
    }

    private static Winner.Team teamWin(TeamId teamId, Collection<Participant> participants) {
        Set<UUID> members = new HashSet<>();
        for (Participant p : participants) {
            if (p.teamId().filter(teamId::equals).isPresent()) {
                members.add(p.uuid());
            }
        }
        return new Winner.Team(teamId, members);
    }
}
