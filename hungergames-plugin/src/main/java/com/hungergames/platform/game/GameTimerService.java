package com.hungergames.platform.game;

import com.hungergames.api.event.GamePhaseChangeEvent;
import com.hungergames.api.event.ParticipantEliminatedEvent;
import com.hungergames.api.event.WinnerDeclaredEvent;
import com.hungergames.api.model.GamePhase;
import com.hungergames.api.model.Winner;
import com.hungergames.game.GameSession;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.VirtualTime;
import com.hungergames.platform.border.BorderService;
import com.hungergames.platform.config.HgSettings;
import com.hungergames.platform.team.TeamPresentationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Laufzeit-Dienst der RUNNING-Phase: Bossbar-Timer, Grace Period,
 * Kill-Leaderboard und Border-Ticks. Ersetzt den alten 820-Zeilen-GameRunner;
 * Eliminierung/Sieg liegen in {@link GameSession} und den Listenern.
 */
public final class GameTimerService implements Listener {

    private final Plugin plugin;
    private final GameSession session;
    private final ConfigurationService config;
    private final HgSettings settings;
    private final VirtualTime virtualTime;
    private final BorderService borderService;
    private final TeamPresentationService teamPresentation;
    private final com.hungergames.platform.persistence.RuntimeStateStore runtimeStore;
    private final com.hungergames.platform.announce.AnnouncementService announcements;

    private BossBar bossBar;
    private Scoreboard leaderboard;
    private Objective killObjective;
    private BukkitTask tickTask;
    private boolean graceActive;
    private int ticksSinceRuntimeSave;

    public GameTimerService(Plugin plugin, GameSession session, ConfigurationService config,
                            HgSettings settings, VirtualTime virtualTime,
                            BorderService borderService, TeamPresentationService teamPresentation,
                            com.hungergames.platform.persistence.RuntimeStateStore runtimeStore,
                            com.hungergames.platform.announce.AnnouncementService announcements) {
        this.plugin = plugin;
        this.session = session;
        this.config = config;
        this.settings = settings;
        this.virtualTime = virtualTime;
        this.borderService = borderService;
        this.teamPresentation = teamPresentation;
        this.runtimeStore = runtimeStore;
        this.announcements = announcements;
    }

    /** Startet den Spielbetrieb (RUNNING-Beginn). */
    public void start() {
        virtualTime.start();
        borderService.start();
        graceActive = !config.get(settings.gracePeriod).isZero();

        teamPresentation.setupScoreboard();
        teamPresentation.giveTeamHelmets();

        createBossBar();
        createLeaderboard();
        applyGraceEffects();

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Setzt den Spielbetrieb nach einem Serverneustart fort. */
    public void resume(Duration elapsedBefore, int borderPhaseIndex) {
        virtualTime.resumeAt(elapsedBefore);
        borderService.resumeAt(borderPhaseIndex);
        graceActive = virtualTime.elapsed().compareTo(config.get(settings.gracePeriod)) < 0;

        teamPresentation.setupScoreboard();
        createBossBar();
        createLeaderboard();

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
            saveRuntimeState();
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        virtualTime.stop();
        borderService.stop();
    }

    /** Persistiert Spielfortschritt (virtuelle Zeit + Border-Index) für den Restore. */
    private void saveRuntimeState() {
        runtimeStore.saveRuntime(virtualTime.elapsed().toMillis(), borderService.nextPhaseIndex());
    }

    private void tick() {
        if (session.phase() != GamePhase.RUNNING) {
            stop();
            return;
        }
        Duration elapsed = virtualTime.elapsed();
        Duration gameDuration = config.get(settings.gameDuration);
        Duration grace = config.get(settings.gracePeriod);

        if (graceActive && elapsed.compareTo(grace) >= 0) {
            endGracePeriod();
        }

        if (elapsed.compareTo(gameDuration) >= 0) {
            session.declareTimeout();
            return;
        }

        borderService.tick();
        updateBossBar(elapsed, gameDuration, grace);

        // Spielfortschritt alle 15s sichern (für Restore nach Crash/Neustart)
        if (++ticksSinceRuntimeSave >= 15) {
            ticksSinceRuntimeSave = 0;
            saveRuntimeState();
        }
    }

    // ==================== Anzeige ====================

    private void createBossBar() {
        bossBar = Bukkit.createBossBar("HungerGames", BarColor.GREEN, BarStyle.SEGMENTED_10);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
    }

    /** Zeigt einem (später gejointen) Spieler Timer-Bossbar und Scoreboard. */
    public void addViewer(Player player) {
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
        if (leaderboard != null) {
            player.setScoreboard(leaderboard);
        }
    }

    public void removeViewer(Player player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    private void updateBossBar(Duration elapsed, Duration gameDuration, Duration grace) {
        if (bossBar == null) {
            return;
        }
        if (graceActive) {
            long remaining = grace.minus(elapsed).toSeconds();
            bossBar.setTitle("§a§lSCHUTZFRIST §e" + Math.max(0, remaining) + "s §7— noch fließt kein Blut");
            bossBar.setColor(BarColor.GREEN);
            bossBar.setProgress(clamp01((double) remaining / Math.max(1, grace.toSeconds())));
        } else {
            Duration remaining = gameDuration.minus(elapsed);
            long minutes = remaining.toMinutes();
            bossBar.setTitle(String.format("§e§lDIE SPIELE: §f%d:%02d §7| §cTribute: §f%d",
                    minutes, remaining.toSecondsPart(), session.participants().aliveCount()));
            bossBar.setColor(minutes <= 5 ? BarColor.RED : minutes <= 15 ? BarColor.YELLOW : BarColor.BLUE);
            bossBar.setProgress(clamp01((double) remaining.toMillis() / gameDuration.toMillis()));
        }
    }

    private void createLeaderboard() {
        // Objective auf dem TEAM-Scoreboard registrieren — nicht auf einem
        // eigenen, sonst verdrängen sich Nametag-Farben und Sidebar gegenseitig.
        leaderboard = teamPresentation.scoreboard();
        Objective existing = leaderboard.getObjective("kills");
        if (existing != null) {
            existing.unregister();
        }
        killObjective = leaderboard.registerNewObjective("kills", Criteria.DUMMY,
                Component.text("KILL LEADERBOARD", NamedTextColor.GOLD));
        killObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        updateLeaderboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(leaderboard);
        }
    }

    private void updateLeaderboard() {
        if (killObjective == null) {
            return;
        }
        for (String entry : leaderboard.getEntries()) {
            leaderboard.resetScores(entry);
        }
        for (Map.Entry<UUID, Integer> entry : session.kills().top(10)) {
            String name = session.participants().nameOf(entry.getKey()).orElse("?");
            killObjective.getScore("§7" + name).setScore(entry.getValue());
        }
        killObjective.getScore("§c§lLebend: §f" + session.participants().aliveCount()).setScore(-1);
    }

    // ==================== Grace Period ====================

    private void applyGraceEffects() {
        if (!graceActive) {
            return;
        }
        int graceSeconds = (int) config.get(settings.gracePeriod).toSeconds();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(Component.empty(),
                    Component.text("SCHUTZFRIST — sammelt, was ihr könnt", NamedTextColor.GREEN)));
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                    graceSeconds * 20, 0, false, false, true));
        }
    }

    private void endGracePeriod() {
        graceActive = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (session.isWhitelisted(player.getUniqueId())) {
                player.removePotionEffect(PotionEffectType.GLOWING);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.0f);
        }
        announcements.broadcast(settings.textGraceEnd, java.util.Map.of(),
                com.hungergames.platform.announce.AnnouncementService.Style.CHAT,
                com.hungergames.platform.announce.AnnouncementService.Style.TITLE);
    }

    /** Unverwundbarkeit während der Grace Period (außer Void). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!graceActive || session.phase() != GamePhase.RUNNING) {
            return;
        }
        if (event.getEntity() instanceof Player player
                && session.isWhitelisted(player.getUniqueId())
                && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
        }
    }

    // ==================== Event-Reaktionen ====================

    @EventHandler
    public void onElimination(ParticipantEliminatedEvent event) {
        updateLeaderboard();
    }

    @EventHandler
    public void onWinner(WinnerDeclaredEvent event) {
        announceWinner(event.getWinner());
    }

    @EventHandler
    public void onPhaseChange(GamePhaseChangeEvent event) {
        // Pending-Konfigurationswerte für "nächste Phase" aktivieren
        config.activatePending(com.hungergames.game.config.SettingDefinition.ApplyTiming.NEXT_PHASE);

        // Arena-Geometrie sichern, sobald sie steht (ab LOBBY vollständig)
        runtimeStore.saveArena(com.hungergames.GameState.getInstance());

        if (event.getNewPhase() == GamePhase.RUNNING && tickTask == null) {
            start();
        }
        if (event.getNewPhase() == GamePhase.FINISHED) {
            stop();
        }
        if (event.getNewPhase() == GamePhase.NOT_INITIALIZED) {
            // Neue Runde: Overrides verwerfen, NEXT_ROUND-Werte aktivieren
            config.clearRoundOverrides();
            config.activatePending(com.hungergames.game.config.SettingDefinition.ApplyTiming.NEXT_ROUND);
            teamPresentation.reset();
            borderService.resetToInitial();
            runtimeStore.clear();
        }
    }

    private void announceWinner(Winner winner) {
        String name = switch (winner) {
            case Winner.Solo solo -> session.participants().nameOf(solo.uuid()).orElse("?");
            case Winner.Team team -> "Team " + session.teamRegistry().team(team.teamId())
                    .map(t -> t.name()).orElse(team.teamId().value());
            case Winner.None none -> "niemand — das Kapitol ist nicht erfreut";
        };
        // Der Sieg-Sound kommt konfigurierbar aus dem AnnouncementListener (sounds.victory)
        announcements.broadcast(settings.textWinner, java.util.Map.of("winner", name),
                com.hungergames.platform.announce.AnnouncementService.Style.CHAT,
                com.hungergames.platform.announce.AnnouncementService.Style.TITLE);
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
