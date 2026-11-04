package io.github.slugblue.wildroots.worldgen;

import io.github.slugblue.wildroots.core.config.ServerConfig;

/** Pure rules of the starter patch (worldgen §6.2), apart so that JUnit loads them without the game. */
public final class StarterPatchRules {

    /** What the starter step did. */
    public enum Outcome { DISABLED, ALREADY_DONE, EXISTING_WORLD, NO_LINE, MARKED_EXISTING, PLACED, FALLBACK, ISLAND,
        NOT_FOUND }

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
