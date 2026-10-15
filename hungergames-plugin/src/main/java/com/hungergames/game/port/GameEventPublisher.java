package com.hungergames.game.port;

import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Team;
import com.hungergames.api.model.TeamColor;
import com.hungergames.api.model.TeamId;
import com.hungergames.api.model.Winner;

import java.util.UUID;

/**
 * Port über den die Spiellogik Ereignisse nach außen meldet.
 *
 * <p>Die Bukkit-Implementierung feuert die entsprechenden Events aus
 * {@code com.hungergames.api.event} auf dem Main-Thread; Tests verwenden
 * einen aufzeichnenden Stub. Die Spiellogik selbst bleibt dadurch frei von
 * Bukkit-Abhängigkeiten.</p>
 */
public interface GameEventPublisher {

    void phaseChanged(GamePhase oldPhase, GamePhase newPhase);

    /** @param killer {@code null} bei Umgebungs-/Admin-Eliminierung */
    void participantEliminated(UUID participant, UUID killer, int remainingAlive);

    void participantRevived(UUID participant);

    void whitelistChanged(UUID player, boolean added);

    void teamCreated(Team team);

    void teamDeleted(Team team);

    void teamColorChanged(Team team, TeamColor oldColor, TeamColor newColor);

    /**
     * @param oldTeam {@code null}, wenn der Spieler vorher kein Team hatte
     * @param newTeam {@code null}, wenn der Spieler sein Team verlassen hat
     */
    void teamMembershipChanged(UUID player, TeamId oldTeam, TeamId newTeam, MembershipCause cause);

    void kill(UUID killer, UUID victim, int killerTotalKills);

    void winnerDeclared(Winner winner);

    /** Auslöser einer Team-Mitgliedschaftsänderung (gespiegelt ins Bukkit-Event). */
    enum MembershipCause {
        API,
        ADMIN,
        PLAYER,
        RANDOM
    }
}
