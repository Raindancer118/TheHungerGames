package com.hungergames.platform.resourcepack;

import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.config.HgSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.HexFormat;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Liefert das optionale Custom-Resourcepack (Item-Texturen, Countdown-Song)
 * an Spieler aus. Nutzt {@code Player#addResourcePack}, das mehrere Packs
 * nebeneinander erlaubt — ein serverweit erzwungenes Pack (server.properties)
 * bleibt dabei unangetastet.
 *
 * <p>Ohne konfigurierte URL ({@code resourcepack.enabled=false} oder leere URL)
 * passiert nichts; ein Client ohne das Pack hört die konfigurierten
 * Resource-Pack-Sound-Keys (z. B. {@code hgpack:countdown}) einfach nicht —
 * das ist vanilla-Verhalten, kein Fehler.</p>
 *
 * <p>Als {@link Listener} protokolliert diese Klasse zusätzlich, ob ein Client
 * das Pack ablehnt oder der Download scheitert — sonst ist "der Sound fehlt"
 * am Spielabend nicht von "der Spieler hat den Download-Dialog nie bestätigt"
 * zu unterscheiden.</p>
 */
public final class ResourcepackService implements Listener {

    /** Feste Pack-ID, damit Neuversand/Entfernen dasselbe Pack trifft. */
    private static final UUID PACK_ID = UUID.fromString("7f6a6b3a-4c2e-4f4a-9c7e-6c0c9b1a3d10");

    private final ConfigurationService config;
    private final HgSettings settings;
    private final Logger logger;

    public ResourcepackService(ConfigurationService config, HgSettings settings, Logger logger) {
        this.config = config;
        this.settings = settings;
        this.logger = logger;
    }

    /** Sendet das Pack, falls konfiguriert und der Hash gültig ist. Sonst ein No-Op. */
    public void send(Player player) {
        if (!config.get(settings.resourcepackEnabled)) {
            return;
        }
        String url = config.get(settings.resourcepackUrl).trim();
        if (url.isEmpty()) {
            return;
        }
        byte[] hash = decodeSha1(config.get(settings.resourcepackSha1));
        if (hash == null) {
            logger.warning("Resourcepack nicht gesendet: resourcepack.sha1 ist kein gültiger "
                    + "40-stelliger SHA1-Hash");
            return;
        }
        player.addResourcePack(PACK_ID, url, hash,
                config.get(settings.resourcepackPrompt),
                config.get(settings.resourcepackRequired));
    }

    /** Diagnose-Log: DECLINED/FAILED_* sichtbar machen, sonst wäre "kein Sound" nicht erklärbar. */
    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case DECLINED -> logger.warning(player.getName()
                    + " hat das Resourcepack abgelehnt — hört keine Custom-Sounds (z. B. den "
                    + "Countdown-Song) und keine Custom-Texturen.");
            case FAILED_DOWNLOAD -> logger.warning(player.getName()
                    + ": Resourcepack-Download fehlgeschlagen (Netzwerk/URL?) — "
                    + config.get(settings.resourcepackUrl));
            case FAILED_RELOAD -> logger.warning(player.getName()
                    + ": Resourcepack konnte nicht neu geladen werden.");
            case SUCCESSFULLY_LOADED -> logger.info(player.getName()
                    + ": Resourcepack erfolgreich geladen.");
            default -> {
                // ACCEPTED/DOWNLOADED/DISCARDED/INVALID_URL sind Zwischenschritte
                // bzw. für den Spielbetrieb irrelevant — keine Konsole-Meldung nötig.
            }
        }
    }

    static byte[] decodeSha1(String hex) {
        String trimmed = hex == null ? "" : hex.trim();
        if (!trimmed.matches("(?i)[0-9a-f]{40}")) {
            return null;
        }
        return HexFormat.of().parseHex(trimmed);
    }
}
