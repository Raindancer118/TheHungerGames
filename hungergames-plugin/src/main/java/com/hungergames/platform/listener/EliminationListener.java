package com.hungergames.platform.listener;

import com.hungergames.api.event.ParticipantEliminatedEvent;
import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.spectator.SpectatorService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Date;
import java.util.UUID;

/**
 * Übersetzt Spieler-Tode in Eliminierungen der {@link GameSession} und setzt
 * die konfigurierte Death-Action um (Default: echter Spectator-Modus).
 *
 * <p>Kanonenschuss und Broadcast hängen am {@link ParticipantEliminatedEvent}
 * und gelten damit für JEDE Eliminierungsquelle — Tod, Admin-Aktion oder
 * Disconnect-Timeout.</p>
 */
public final class EliminationListener implements Listener {

    private final org.bukkit.plugin.Plugin plugin;
    private final GameSession session;
    private final SpectatorService spectatorService;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final com.hungergames.platform.effect.EffectService effects;
    private final com.hungergames.platform.sound.SoundService sounds;

    public EliminationListener(org.bukkit.plugin.Plugin plugin, GameSession session,
                               SpectatorService spectatorService,
                               ConfigurationService config, HgSettings settings,
                               com.hungergames.platform.effect.EffectService effects,
                               com.hungergames.platform.sound.SoundService sounds) {
        this.plugin = plugin;
        this.session = session;
        this.spectatorService = spectatorService;
        this.config = config;
        this.settings = settings;
        this.effects = effects;
        this.sounds = sounds;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!session.isWhitelisted(victim.getUniqueId())) {
            return;
        }

        Player killerPlayer = victim.getKiller();
        UUID killer = killerPlayer != null ? killerPlayer.getUniqueId() : null;

        // GameSession prüft die Phase, zählt Kills, feuert Events (inkl. des
        // Eliminierungs-Broadcasts unten) und stößt die Gewinnerermittlung an.
        org.bukkit.Location deathLoc = victim.getLocation().add(0, 1, 0);
        if (!session.eliminate(victim.getUniqueId(), killer)) {
            return;
        }
        event.deathMessage(null);
        // Visuelle Effekte am Ort des Falls (Kill bzw. neutrale Eliminierung)
        effects.playAt(deathLoc, killer != null ? settings.effectKill : settings.effectElimination);
        applyDeathAction(victim);
    }

    /** Kanonenschuss + Broadcast für jede Eliminierung (Tod, Admin, Disconnect). */
    @EventHandler
    public void onEliminated(ParticipantEliminatedEvent event) {
        String name = session.participants().nameOf(event.getParticipant())
                .orElse(event.getParticipant().toString().substring(0, 8));
        String team = session.teamRegistry().teamOf(event.getParticipant())
                .map(t -> "[" + t.name() + "] ")
                .orElse("");

        // Der Kanonenschuss — in Panem das Zeichen für einen gefallenen Tribut.
        // Kill durch den Exmatrikulator? Dann wird standesgemäß exmatrikuliert
        // (zufällige Phrase mit Modul & Killer, beim Treffer ausgewürfelt).
        String fallen = com.hungergames.items.CustomItems.exmatrikulationPhrase(event.getParticipant())
                .map(phrase -> " " + phrase)
                .orElse(" ist gefallen.");
        Bukkit.broadcast(Component.text("☠ Ein Kanonenschuss ertönt. ", NamedTextColor.DARK_RED)
                .append(Component.text("Tribut " + team + name + fallen, NamedTextColor.RED))
                .append(Component.text(" Noch " + event.getRemainingAlive()
                        + " Tribute in der Arena.", NamedTextColor.GRAY)));
        // Konfigurierbarer Kanonenschuss-Sound (Standard: Totem + Explosion +
        // verzögerter Nachhall über die Layer-Marker) und optionale Partikel.
        sounds.playToAll(settings.soundCannon);
        effects.playToAll(settings.effectCannon);
    }

    private void applyDeathAction(Player victim) {
        switch (config.get(settings.deathAction)) {
            case SPECTATOR -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (victim.isOnline()) {
                    victim.spigot().respawn();
                    spectatorService.makeSpectator(victim);
                }
            });
            case KICK -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) {
                    victim.kick(Component.text("Du bist in den Hunger Games gestorben!",
                            NamedTextColor.RED));
                }
            }, 60L);
            case BAN -> Bukkit.getScheduler().runTaskLater(plugin, () -> {
                victim.ban("Du bist in den Hunger Games gestorben!", (Date) null,
                        "HungerGames", true);
            }, 60L);
        }
    }
}
