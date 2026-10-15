package com.hungergames.game.port;

import com.hungergames.game.SessionSnapshot;

import java.util.Optional;

/**
 * Port für die Persistenz der laufenden Game-Session.
 *
 * <p>Die Plugin-Schicht implementiert dies als YAML-Datei im Datenordner;
 * ein Serverneustart während einer Runde stellt die Session daraus wieder
 * her. Tests verwenden eine In-Memory-Implementierung.</p>
 */
public interface SessionStore {

    /** Persistiert den kompletten Sessionzustand (wird bei jeder Mutation gerufen). */
    void save(SessionSnapshot snapshot);

    /** @return der zuletzt gespeicherte Zustand, falls vorhanden. */
    Optional<SessionSnapshot> load();

    /** Entfernt den gespeicherten Zustand (nach Reset/Rundenende). */
    void clear();
}
