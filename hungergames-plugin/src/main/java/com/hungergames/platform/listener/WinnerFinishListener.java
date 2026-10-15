package com.hungergames.platform.listener;

import com.hungergames.api.event.WinnerDeclaredEvent;
import com.hungergames.stagerunner.FinishRunner;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Startet die Abschluss-Sequenz ({@link FinishRunner}) kurz nach der
 * Siegerkürung, damit Sieges-Titel und Sounds zuerst sichtbar sind.
 */
public final class WinnerFinishListener implements Listener {

    private final FinishRunner finishRunner;
    private final Plugin plugin;

    public WinnerFinishListener(FinishRunner finishRunner, Plugin plugin) {
        this.finishRunner = finishRunner;
        this.plugin = plugin;
    }

    @EventHandler
    public void onWinner(WinnerDeclaredEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, finishRunner::execute, 100L);
    }
}
