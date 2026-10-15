package com.hungergames.game.support;

import com.hungergames.game.SessionSnapshot;
import com.hungergames.game.port.SessionStore;

import java.util.Optional;

/** In-Memory-Store für Tests; zählt Speichervorgänge. */
public final class InMemorySessionStore implements SessionStore {

    private SessionSnapshot snapshot;
    public int saveCount;

    @Override
    public void save(SessionSnapshot snapshot) {
        this.snapshot = snapshot;
        saveCount++;
    }

    @Override
    public Optional<SessionSnapshot> load() {
        return Optional.ofNullable(snapshot);
    }

    @Override
    public void clear() {
        snapshot = null;
    }
}
