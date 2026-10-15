package com.hungergames.stagerunner;

import com.hungergames.game.GameSession;
import com.hungergames.platform.Messages;
import com.hungergames.utils.Sender;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Map;
import java.util.UUID;

/**
 * Abschlussphase nach der Siegerkürung: Endstatistik anzeigen, Spieler
 * bereinigen und Gamemastern die Restart-Schritte nennen.
 *
 * <p>Der Rundenzustand selbst (Teilnehmer, Sieger, Kills) bleibt in der
 * {@link GameSession} erhalten und abfragbar, bis ein Admin per /init die
 * nächste Runde beginnt.</p>
 */
public class FinishRunner {

    private final GameSession session;

    public FinishRunner(GameSession session) {
        this.session = session;
    }

    /** Führt die Abschluss-Sequenz aus (wird bei WinnerDeclared aufgerufen). */
    public void execute() {
        Sender.log("Spiel beendet — FinishRunner läuft.");
        showFinalStats();
        cleanupPlayers();
        provideRestartOptions();
    }

    private void showFinalStats() {
        long seconds = session.runningSinceMillis()
                .map(start -> (System.currentTimeMillis() - start) / 1000)
                .orElse(0L);

        Bukkit.broadcast(Messages.info("Dauer der Spiele: " + (seconds / 60) + "m " + (seconds % 60) + "s"));
        Bukkit.broadcast(Messages.info("Die tödlichsten Tribute dieser Spiele:"));
        for (Map.Entry<UUID, Integer> entry : session.kills().top(3)) {
            String name = session.participants().nameOf(entry.getKey()).orElse("?");
            Bukkit.broadcast(Messages.info("  " + name + " — " + entry.getValue() + " Tribute besiegt"));
        }
    }

    private void cleanupPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SPECTATOR);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(5.0f);
        }
    }

    private void provideRestartOptions() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("hungergames.gamemaster")) {
                player.sendMessage(Messages.ok("Die Hungerspiele sind beendet — das Kapitol dankt."));
                player.sendMessage(Messages.info("Neue Spiele: /init errichtet eine neue Arena."));
            }
        }
    }
}
