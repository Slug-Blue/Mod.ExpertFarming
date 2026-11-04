package io.github.slugblue.wildroots.worldgen;

import io.github.slugblue.wildroots.core.config.ServerConfig;

/** Pure rules of the starter patch (worldgen §6.2), apart so that JUnit loads them without the game. */
public final class StarterPatchRules {

    /** What the starter step did. */
    public enum Outcome { DISABLED, ALREADY_DONE, EXISTING_WORLD, NO_LINE, MARKED_EXISTING, PLACED, FALLBACK, ISLAND,
        NOT_FOUND, NOT_LOADED;

        /** A patch exists now (placed, or an existing one marked): the world flag is set. */
        public boolean success() {
            return this == MARKED_EXISTING || this == PLACED || this == FALLBACK || this == ISLAND;
        }

        /** The search ran and found no place: a retry may follow (review 2, T1). */
        public boolean failure() {
            return this == NOT_FOUND || this == NOT_LOADED;
        }
    }

    /** Attempts before the starter patch gives up (review 2, T1; owner default pending confirmation). */
    public static final int MAX_ATTEMPTS = 3;

    /**
     * Below this share of loaded ring columns a failed search is
     * {@link Outcome#NOT_LOADED}: the area was not representative.
     */
    public static final double MIN_LOADED_SHARE = 0.5;

    /** The outcome of a search that found no place: too few loaded columns, or a real miss. */
    public static Outcome miss(int columns, int unloaded) {
        return columns == 0 || (columns - unloaded) < MIN_LOADED_SHARE * columns ? Outcome.NOT_LOADED
                : Outcome.NOT_FOUND;
    }

    /**
     * After a failed attempt: whether the world flag is set now, that is
     * {@code attempts} (counting this one) reached {@value #MAX_ATTEMPTS}.
     */
    public static boolean giveUp(int attempts) {
        return attempts >= MAX_ATTEMPTS;
    }

    private StarterPatchRules() {
    }

    /**
     * Why the starter patch does not run now, or {@code null} when it runs:
     * off by config, done already, or a world played before the mod
     * ({@code gameTime > 0}, no data file) without {@code FORCE}.
     */
    public static Outcome decide(ServerConfig.StarterPatch mode, boolean hasData, boolean done, long gameTime) {
        if (mode == ServerConfig.StarterPatch.FALSE) {
            return Outcome.DISABLED;
        }
        if (done) {
            return Outcome.ALREADY_DONE;
        }
        if (!hasData && gameTime > 0 && mode != ServerConfig.StarterPatch.FORCE) {
            return Outcome.EXISTING_WORLD;
        }
        return null;
    }
}
