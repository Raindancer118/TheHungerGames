package com.hungergames.platform.border;

import com.hungergames.game.GameSession;
import com.hungergames.game.border.BorderEngine;
import com.hungergames.game.border.BorderSettings;
import com.hungergames.game.config.ConfigurationService;
import com.hungergames.platform.VirtualTime;
import com.hungergames.platform.config.HgSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Bukkit-Adapter der Border-Engine: baut die {@link BorderSettings} aus der
 * Konfiguration, tickt die Engine mit virtueller Spielzeit und wendet
 * Schrumpfbefehle auf Overworld und (skaliert 1:8) Nether an.
 */
public final class BorderService {

    private final ConfigurationService config;
    private final HgSettings settings;
    private final GameSession session;
    private final VirtualTime virtualTime;
    private final Supplier<World> arenaWorld;

    private BorderEngine engine;
    /** Phasenindex, für den bereits eine Schrumpf-Vorwarnung lief. */
    private int warnedPhaseIndex = -1;

    public BorderService(ConfigurationService config, HgSettings settings, GameSession session,
                         VirtualTime virtualTime, Supplier<World> arenaWorld) {
        this.config = config;
        this.settings = settings;
        this.session = session;
        this.virtualTime = virtualTime;
        this.arenaWorld = arenaWorld;
    }

    /** Baut die aktuellen Border-Einstellungen aus der Konfiguration. */
    public BorderSettings currentSettings() {
        Duration gameDuration = config.get(settings.gameDuration);
        return new BorderSettings(
                config.get(settings.borderInitialSize),
                config.get(settings.borderMinimumSize),
                config.get(settings.borderMaxEdgeSpeed),
                BorderPhaseParser.parseAll(config.get(settings.borderPhases), gameDuration));
    }

    /** Startet die Engine für eine neue Runde (bei RUNNING-Beginn). */
    public void start() {
        engine = new BorderEngine(currentSettings());
        warnedPhaseIndex = -1;
    }

    /**
     * Setzt nach einem Restore am persistierten Phasenindex wieder auf.
     *
     * <p>Eine beim Neustart LAUFENDE Border-Bewegung muss nicht selbst
     * persistiert werden: Vanilla speichert Größe, Lerp-Ziel und Restdauer
     * der Worldborder in {@code level.dat} und setzt die Bewegung beim
     * Weltstart fort. Hier wird nur verhindert, dass frühere Phasen erneut
     * feuern.</p>
     */
    public void resumeAt(int nextPhaseIndex) {
        engine = new BorderEngine(currentSettings(), nextPhaseIndex);
        warnedPhaseIndex = -1;
    }

    public void stop() {
        engine = null;
    }

    public int nextPhaseIndex() {
        return engine != null ? engine.nextPhaseIndex() : 0;
    }

    /** Wird sekündlich vom Spiel-Timer gerufen. */
    public void tick() {
        if (engine == null) {
            return;
        }
        World world = arenaWorld.get();
        if (world == null) {
            return;
        }
        warnBeforeShrink();
        double currentSize = world.getWorldBorder().getSize();
        engine.tick(virtualTime.elapsed(), session.participants().aliveCount(), currentSize)
                .ifPresent(command -> applyShrink(world, command));
    }

    /**
     * Vorwarnung {@code border.shrink-warning} Sekunden vor jeder
     * zeitgesteuerten Phase (alive-Trigger sind nicht vorhersagbar).
     */
    private void warnBeforeShrink() {
        int warnSeconds = config.get(settings.borderShrinkWarning);
        if (warnSeconds <= 0 || engine.isFinished()
                || warnedPhaseIndex == engine.nextPhaseIndex()) {
            return;
        }
        var trigger = engine.settings().phases().get(engine.nextPhaseIndex()).trigger();
        if (trigger.time().isEmpty()) {
            return;
        }
        Duration elapsed = virtualTime.elapsed();
        Duration triggerTime = trigger.time().get();
        if (elapsed.compareTo(triggerTime) < 0
                && elapsed.compareTo(triggerTime.minusSeconds(warnSeconds)) >= 0) {
            warnedPhaseIndex = engine.nextPhaseIndex();
            long remaining = Math.max(1, triggerTime.minus(elapsed).toSeconds());
            Bukkit.broadcast(Component.text("⚠ Die Arena schrumpft in " + remaining
                    + " Sekunden — sucht Deckung Richtung Mitte!", NamedTextColor.GOLD));
        }
    }

    private void applyShrink(World world, BorderEngine.ShrinkCommand command) {
        long seconds = Math.max(1, command.duration().toSeconds());
        world.getWorldBorder().changeSize(command.targetSize(), seconds * 20L);

        if (config.get(settings.borderScaleNether)) {
            World nether = Bukkit.getWorld(world.getName() + "_nether");
            if (nether != null) {
                // Nether-Koordinaten sind 1:8 skaliert
                nether.getWorldBorder().changeSize(Math.max(16, command.targetSize() / 8.0), seconds * 20L);
            }
        }

        Bukkit.broadcast(Component.text("⚠ Die Spielmacher verkleinern die Arena! ", NamedTextColor.RED)
                .append(Component.text("Neue Grenze: " + (int) command.targetSize()
                                + " Blöcke (Kante: "
                                + String.format(java.util.Locale.ROOT, "%.2f", command.effectiveSpeed())
                                + " Blöcke/s). Flieht zur Mitte, Tribute!",
                        NamedTextColor.GRAY)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
        }
    }

    /**
     * Übernimmt die Border für ein Sonderereignis (Deathmatch): stoppt die
     * geplanten Phasen und schrumpft beide Welten auf die Zielgröße. Die
     * Schrumpfdauer respektiert die konfigurierte maximale Kantengeschwindigkeit.
     *
     * @return Schrumpfdauer in Sekunden
     */
    public long overrideShrinkTo(double targetSize) {
        engine = null; // geplante Phasen feuern nicht mehr in diese Runde
        World world = arenaWorld.get();
        if (world == null) {
            return 0;
        }
        double currentSize = world.getWorldBorder().getSize();
        if (targetSize >= currentSize) {
            return 0;
        }
        double maxEdgeSpeed = config.get(settings.borderMaxEdgeSpeed);
        long seconds = Math.max(1, (long) Math.ceil(
                (currentSize - targetSize) / 2.0 / Math.max(0.01, maxEdgeSpeed)));
        world.getWorldBorder().changeSize(targetSize, seconds * 20L);
        if (config.get(settings.borderScaleNether)) {
            World nether = Bukkit.getWorld(world.getName() + "_nether");
            if (nether != null) {
                nether.getWorldBorder().changeSize(Math.max(16, targetSize / 8.0), seconds * 20L);
            }
        }
        return seconds;
    }

    /** Aktuelle Bordergröße der Arena-Welt (0, wenn keine Welt). */
    public double currentSize() {
        World world = arenaWorld.get();
        return world != null ? world.getWorldBorder().getSize() : 0;
    }

    /** Setzt die Border beider Welten auf die Startgröße zurück (Rundenende). */
    public void resetToInitial() {
        World world = arenaWorld.get();
        if (world == null) {
            return;
        }
        double initial = config.get(settings.borderInitialSize);
        world.getWorldBorder().setSize(initial);
        World nether = Bukkit.getWorld(world.getName() + "_nether");
        if (nether != null) {
            nether.getWorldBorder().setSize(config.get(settings.borderScaleNether)
                    ? Math.max(16, initial / 8.0) : initial);
        }
        stop();
    }
}
