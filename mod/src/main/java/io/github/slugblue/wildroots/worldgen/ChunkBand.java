package io.github.slugblue.wildroots.worldgen;

import io.github.slugblue.wildroots.core.rng.RngKeys;

/**
 * The chunk band of worldgen §4.2 as plain arithmetic (also used by
 * {@code /wildroots locate_patch}): one number per chunk,
 * {@code u = Hash64(worldSeed, chunkX, chunkZ, "wildroots_patch")} mapped to
 * {@code [0, 1)}; a species owns {@code [from, to)}.
 */
public final class ChunkBand {

    private ChunkBand() {
    }

    /** The band number of chunk {@code (chunkX, chunkZ)} in {@code [0, 1)}. */
    public static double u(long worldSeed, int chunkX, int chunkZ) {
        return (RngKeys.chunkBand(worldSeed, chunkX, chunkZ) >>> 11) * 0x1.0p-53;
    }

    /** Whether chunk {@code (chunkX, chunkZ)} falls into {@code [from, to)}. */
    public static boolean candidate(long worldSeed, int chunkX, int chunkZ, double from, double to) {
        double u = u(worldSeed, chunkX, chunkZ);
        return u >= from && u < to;
    }
}
