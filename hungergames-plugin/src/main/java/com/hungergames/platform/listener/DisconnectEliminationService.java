package com.hungergames.platform.listener;

import com.hungergames.api.model.GamePhase;
import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.config.HgSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Optionale Disconnect-Eliminierung: Ist ein lebender Tribut länger als die
 * konfigurierte Zeit ({@code game.disconnect-elimination}, z. B. {@code 2m})
 * vom Server getrennt, wird er eliminiert. Default {@code 0} = deaktiviert —
 * dann gilt uneingeschränkt „Disconnect ist kein Tod".
 *
 * <p>Die Überwachung läuft lazy im Sekunden-Takt: Offline-Lebende werden beim
 * ersten Sichten mit Zeitstempel erfasst, Rückkehrer wieder ausgetragen. Das
 * deckt auch Fälle ab, in denen kein Quit-Event beobachtet wurde (Spieler war
 * schon während eines Server-Neustarts offline) — die Frist beginnt dann mit
 * dem Serverstart, nie rückwirkend. Gemessen wird reale Zeit; {@code /speedup}
 * hat darauf bewusst keinen Einfluss.</p>
 */
public final class DisconnectEliminationService {

    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;

    /** Lebende Offline-Tribute → Epochen-Zeitpunkt der ersten Offline-Sichtung. */
    private final Map<UUID, Long> offlineSince = new HashMap<>();

    public DisconnectEliminationService(GameSession session, ConfigurationService config,
                                        HgSettings settings) {
        this.session = session;
        this.config = config;
        this.settings = settings;
    }

    /** Startet die sekündliche Überwachung (ein Task für die Plugin-Laufzeit). */
    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (session.phase() != GamePhase.RUNNING) {
            if (!offlineSince.isEmpty()) {
                offlineSince.clear();
            }
            return;
        }
        Duration timeout = config.get(settings.disconnectElimination);
        if (timeout.isZero() || timeout.isNegative()) {
            offlineSince.clear();
            return;
        }

        long now = System.currentTimeMillis();

        // Offline-Lebende erfassen, Rückkehrer und Eliminierte austragen
        for (UUID alive : session.participants().alive()) {
            if (Bukkit.getPlayer(alive) != null) {
                offlineSince.remove(alive);
            } else {
                offlineSince.putIfAbsent(alive, now);
            }
        }
        offlineSince.keySet().removeIf(uuid -> !session.participants().isAlive(uuid));

        // Fristablauf prüfen
        Iterator<Map.Entry<UUID, Long>> iterator = offlineSince.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (now - entry.getValue() < timeout.toMillis()) {
                continue;
            }
            UUID uuid = entry.getKey();
            iterator.remove();

            String name = session.participants().nameOf(uuid).orElse("?");
            Bukkit.broadcast(Component.text("Tribut " + name + " hat die Arena verlassen ("
                            + "über " + timeout.toSeconds() / 60 + " Minute"
                            + (timeout.toSeconds() / 60 == 1 ? "" : "n")
                            + " getrennt) — die Spielmacher greifen ein.",
                    NamedTextColor.GRAY));
            // Kanonenschuss, Broadcast und Gewinnerermittlung folgen über
            // das ParticipantEliminatedEvent der Session.
            session.eliminate(uuid, null);
        }
    }
}
