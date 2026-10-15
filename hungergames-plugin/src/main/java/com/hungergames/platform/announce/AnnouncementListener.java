package com.hungergames.platform.announce;

import com.hungergames.api.event.GamePhaseChangeEvent;
import com.hungergames.api.event.ParticipantEliminatedEvent;
import com.hungergames.api.event.ParticipantKillEvent;
import com.hungergames.api.event.WinnerDeclaredEvent;
import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.announce.AnnouncementService.Style;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.effect.EffectService;
import com.hungergames.platform.sound.SoundService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verbindet Kern-Events mit dem Ansage-/Sound-System: Killfeed mit
 * konfigurierbaren Texten, Verbleibende-Tribute-Schwellen und die
 * konfigurierten Kill-/Eliminierungs-/Sieg-Sounds.
 *
 * <p>Der thematische Kanonenschuss-Broadcast bleibt im EliminationListener —
 * der Killfeed ergänzt ihn um die Killer-Information.</p>
 */
public final class AnnouncementListener implements Listener {

    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final AnnouncementService announcements;
    private final SoundService sounds;
    private final EffectService effects;

    /** Bereits angesagte Schwellen dieser Runde. */
    private final Set<Integer> announcedThresholds = new HashSet<>();

    public AnnouncementListener(GameSession session, ConfigurationService config,
                                HgSettings settings, AnnouncementService announcements,
                                SoundService sounds, EffectService effects) {
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.announcements = announcements;
        this.sounds = sounds;
        this.effects = effects;
    }

    @EventHandler
    public void onKill(ParticipantKillEvent event) {
        if (config.get(settings.announceKillfeed)) {
            announcements.broadcast(settings.textKill, Map.of(
                            "victim", name(event.getVictim()),
                            "killer", name(event.getKiller()),
                            "alive", String.valueOf(session.participants().aliveCount())),
                    Style.CHAT);
        }
        sounds.playToAll(settings.soundKill);
    }

    @EventHandler
    public void onEliminated(ParticipantEliminatedEvent event) {
        // Eliminierung ohne Killer (Disconnect, Admin, Umwelt) — mit Killer
        // übernimmt der Killfeed in onKill
        if (event.getKiller().isEmpty() && config.get(settings.announceKillfeed)) {
            announcements.broadcast(settings.textElimination, Map.of(
                            "victim", name(event.getParticipant()),
                            "alive", String.valueOf(event.getRemainingAlive())),
                    Style.CHAT);
        }
        if (event.getKiller().isEmpty()) {
            sounds.playToAll(settings.soundElimination);
        }

        if (config.get(settings.announceRemainingPlayers)) {
            announceThreshold(event.getRemainingAlive());
        }
    }

    private void announceThreshold(int remaining) {
        for (String raw : config.get(settings.announceRemainingThresholds)) {
            int threshold;
            try {
                threshold = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                continue; // Validator verhindert das; defensiv überspringen
            }
            if (remaining == threshold && announcedThresholds.add(threshold)) {
                announcements.broadcast(settings.textRemainingPlayers,
                        Map.of("alive", String.valueOf(remaining)), Style.CHAT, Style.TITLE);
            }
        }
    }

    @EventHandler
    public void onWinner(WinnerDeclaredEvent event) {
        sounds.playToAll(settings.soundVictory);
        effects.playToAll(settings.effectVictory);
    }

    @EventHandler
    public void onPhaseChange(GamePhaseChangeEvent event) {
        if (event.getNewPhase() == GamePhase.NOT_INITIALIZED
                || event.getNewPhase() == GamePhase.RUNNING) {
            announcedThresholds.clear();
        }
        if (event.getNewPhase() == GamePhase.RUNNING) {
            effects.playToAll(settings.effectGameStart);
        }
    }

    private String name(java.util.UUID uuid) {
        return session.participants().nameOf(uuid).orElse("?");
    }
}
