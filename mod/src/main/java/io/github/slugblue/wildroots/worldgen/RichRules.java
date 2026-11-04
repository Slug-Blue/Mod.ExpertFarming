package io.github.slugblue.wildroots.worldgen;

/**
 * Where a rich patch may be (worldgen §5.1–5.2), as plain arithmetic: the
 * decision is the patch roll against {@code rich.chance}; the starter patch
 * is never rich (the first lesson is that wild plants are small), and a
 * patch closer to the world spawn than {@code min_distance_from_spawn}
 * (256) is downgraded to an ordinary one (a landmark is a goal of travel).
 */
public final class RichRules {

    private RichRules() {
    }

    /**
     * @param roll          the patch roll in {@code [0, 1)}
     * @param chance        {@code rich.chance}
     * @param starter       the starter patch
     * @param dx            horizontal offset from the world spawn, X
     * @param dz            horizontal offset from the world spawn, Z
     * @param minFromSpawn  {@code rich.min_distance_from_spawn}
     */
    public static boolean rich(double roll, double chance, boolean starter, long dx, long dz, int minFromSpawn) {
        return !starter && roll < chance && dx * dx + dz * dz >= (long) minFromSpawn * minFromSpawn;
    }
}
