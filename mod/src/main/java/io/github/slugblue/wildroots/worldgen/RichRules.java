package io.github.slugblue.wildroots.worldgen;

/**
 * Where a rich patch may be (worldgen §5.1–5.2), as plain arithmetic: the
 * decision is the patch roll against {@code rich.chance}; the starter patch
 * is never rich (the first lesson is that wild plants are small), and a
 * patch closer to the world spawn than {@code min_distance_from_spawn}
 * (256) is downgraded to an ordinary one (a landmark is a goal of travel).
 */
public final class RichRules {

    /**
     * A landmark keeps more than this far (Chebyshev) from every plant, so
     * the boulder blob never replaces one: {@code BlockBlobFeature} moves its
     * centre by up to −1 in x and z twice and has a radius of up to 1, so it
     * reaches 3 blocks on its −x/−z side (review 2, T2).
     */
    public static final int LANDMARK_CLEARANCE = 3;

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

    /** Whether a landmark at column {@code (x, z)} keeps clear of every plant (worldgen §5.3). */
    public static boolean landmarkClear(java.util.List<net.minecraft.core.BlockPos> plants, int x, int z) {
        return plants.stream().allMatch(p -> Math.max(Math.abs(p.getX() - x), Math.abs(p.getZ() - z))
                > LANDMARK_CLEARANCE);
    }
}
