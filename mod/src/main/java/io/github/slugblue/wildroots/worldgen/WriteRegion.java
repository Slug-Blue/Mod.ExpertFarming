package io.github.slugblue.wildroots.worldgen;

/**
 * The write region of a decoration step (worldgen §7.2, §12.1): a
 * {@code WorldGenRegion} of {@code ChunkStatus.FEATURES} lets a feature
 * write only into the 3×3 chunks around the chunk being decorated, which
 * is the chunk of the patch origin ({@code in_square}). Plain arithmetic,
 * so JUnit checks it without the game.
 */
public final class WriteRegion {

    /** Largest horizontal distance of a nest from the patch centre (worldgen §7.2). */
    public static final int NEST_REACH = 16;

    private WriteRegion() {
    }

    /** Whether block column {@code (x, z)} lies in the 3×3 chunks around the chunk of {@code (cx, cz)}. */
    public static boolean contains(int centerX, int centerZ, int x, int z) {
        return Math.abs((x >> 4) - (centerX >> 4)) <= 1 && Math.abs((z >> 4) - (centerZ >> 4)) <= 1;
    }

    /** Whether the box {@code (x ± margin, z ± margin)} lies in the region. */
    public static boolean containsBox(int centerX, int centerZ, int x, int z, int margin) {
        return contains(centerX, centerZ, x - margin, z - margin) && contains(centerX, centerZ, x + margin, z + margin);
    }

    /** A nest spot: at most {@value #NEST_REACH} blocks from the centre horizontally and inside the region. */
    public static boolean nestSpot(int centerX, int centerZ, int x, int z) {
        long dx = x - centerX;
        long dz = z - centerZ;
        return dx * dx + dz * dz <= (long) NEST_REACH * NEST_REACH && contains(centerX, centerZ, x, z);
    }
}
