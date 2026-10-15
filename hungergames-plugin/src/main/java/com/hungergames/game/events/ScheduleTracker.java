package com.hungergames.game.events;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure Logik für zeitgeplante Rundenereignisse (Supply Drops, Beacon-Spawns):
 * Welche Einträge eines Zeitplans sind bei der aktuellen Spielzeit fällig und
 * noch nicht ausgelöst?
 *
 * <p>Bereits ausgelöste Indizes persistiert der Aufrufer — dadurch feuert
 * nach einem Server-Neustart kein Ereignis doppelt. Ein während der Downtime
 * fällig gewordenes Ereignis wird beim nächsten Tick nachgeholt.</p>
 */
public final class ScheduleTracker {

    private ScheduleTracker() {
    }

    /**
     * @param schedule  geplante Spielzeitpunkte (Reihenfolge = Indizes)
     * @param triggered bereits ausgelöste Indizes
     * @param elapsed   aktuelle (virtuelle) Spielzeit
     * @return Indizes, die jetzt fällig sind, aufsteigend
     */
    public static List<Integer> dueIndices(List<Duration> schedule, Set<Integer> triggered,
                                           Duration elapsed) {
        List<Integer> due = new ArrayList<>();
        for (int i = 0; i < schedule.size(); i++) {
            if (!triggered.contains(i) && schedule.get(i).compareTo(elapsed) <= 0) {
                due.add(i);
            }
        }
        return due;
    }
}
