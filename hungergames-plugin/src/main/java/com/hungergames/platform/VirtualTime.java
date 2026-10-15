package com.hungergames.platform;

import java.time.Duration;

/**
 * Virtuelle Spielzeit mit {@code /speedup}-Multiplikator.
 *
 * <p>Ersetzt den statischen Zustand des alten {@code SpeedupCommand}:
 * {@link #tick()} wird periodisch (z. B. sekündlich) vom Spiel-Timer gerufen
 * und akkumuliert reale Zeit multipliziert mit dem aktuellen Faktor.</p>
 */
public final class VirtualTime {

    private double multiplier = 1.0;
    private long virtualElapsedMs;
    private long lastRealTime;
    private boolean running;

    /** Startet die Zeitmessung bei null (Spielbeginn). */
    public void start() {
        running = true;
        virtualElapsedMs = 0;
        lastRealTime = System.currentTimeMillis();
    }

    /** Setzt die Messung nach einem Restore fort. */
    public void resumeAt(Duration elapsed) {
        running = true;
        virtualElapsedMs = elapsed.toMillis();
        lastRealTime = System.currentTimeMillis();
    }

    public void stop() {
        tick();
        running = false;
    }

    /** Akkumuliert die seit dem letzten Aufruf vergangene (multiplizierte) Zeit. */
    public void tick() {
        if (!running) {
            return;
        }
        long now = System.currentTimeMillis();
        virtualElapsedMs += (long) ((now - lastRealTime) * multiplier);
        lastRealTime = now;
    }

    public Duration elapsed() {
        tick();
        return Duration.ofMillis(virtualElapsedMs);
    }

    public boolean isRunning() {
        return running;
    }

    public double multiplier() {
        return multiplier;
    }

    /** @param multiplier ≥ 1.0; 1.0 = Normalzeit */
    public void setMultiplier(double multiplier) {
        tick(); // bisherige Zeit mit altem Faktor abrechnen
        this.multiplier = Math.max(1.0, multiplier);
    }
}
