package com.hungergames.api;

/**
 * Static access to the {@link HungerGamesApi} instance registered by the
 * HungerGames plugin.
 *
 * <p>Available from the moment the HungerGames plugin has been enabled;
 * declare a {@code depend} or {@code softdepend} on {@code HungerGames} in
 * your {@code plugin.yml} to guarantee ordering.</p>
 */
public final class HungerGamesProvider {

    private static HungerGamesApi instance;

    private HungerGamesProvider() {
    }

    /**
     * @return the API instance
     * @throws IllegalStateException if the HungerGames plugin is not enabled
     */
    public static HungerGamesApi get() {
        HungerGamesApi api = instance;
        if (api == null) {
            throw new IllegalStateException(
                    "HungerGamesApi is not available. Is the HungerGames plugin enabled and "
                            + "does your plugin.yml declare a (soft)depend on it?");
        }
        return api;
    }

    /**
     * Registers the API implementation. Internal use by the HungerGames
     * plugin only.
     *
     * @throws IllegalStateException if an instance is already registered
     */
    public static void register(HungerGamesApi api) {
        if (instance != null && api != null) {
            throw new IllegalStateException("HungerGamesApi is already registered");
        }
        instance = api;
    }

    /** Unregisters the API on plugin disable. Internal use only. */
    public static void unregister() {
        instance = null;
    }
}
