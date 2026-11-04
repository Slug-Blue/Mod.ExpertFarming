package io.github.slugblue.wildroots.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** WR-150: chunk band (worldgen §4.2, §4.3) and the climate window edges (§2.2, S11). */
class ChunkBandTest {

    /** The carrot band [0, 0.036) takes ≈ 3.6 % of chunks on any seed; u is deterministic and in [0, 1). */
    @Test
    void bandWidthIsTheChunkShare() {
        for (long seed : new long[] {0L, 1L, -7L, 123_456_789L, 0x5DEECE66DL}) {
            int hits = 0;
            int n = 0;
            for (int cx = -150; cx < 150; cx++) {
                for (int cz = -150; cz < 150; cz++) {
                    double u = ChunkBand.u(seed, cx, cz);
                    assertTrue(u >= 0 && u < 1);
                    assertEquals(u, ChunkBand.u(seed, cx, cz));
                    if (ChunkBand.candidate(seed, cx, cz, 0.0, 0.036)) {
                        hits++;
                    }
                    n++;
                }
            }
            double share = hits / (double) n;
            assertTrue(Math.abs(share - 0.036) < 0.003, "seed " + seed + ": share " + share);
        }
    }

    /** Disjoint intervals: a chunk is a candidate of at most one species (≤ 1 patch per chunk). */
    @Test
    void disjointBandsNeverShareAChunk() {
        for (int cx = -100; cx < 100; cx++) {
            for (int cz = -100; cz < 100; cz++) {
                assertFalse(ChunkBand.candidate(42L, cx, cz, 0.0, 0.036) && ChunkBand.candidate(42L, cx, cz, 0.036, 0.061));
            }
        }
    }

    @Test
    void climateWindowEdgesGetSlackOnlyWhereVanillaIsNoisy() {
        assertTrue(ClimateWindow.fits(0.2, 0.2, 1.0, 0, false));
        assertTrue(ClimateWindow.fits(1.0, 0.2, 1.0, 0, false));
        assertFalse(ClimateWindow.fits(0.195, 0.2, 1.0, 0, false));
        assertTrue(ClimateWindow.fits(0.195, 0.2, 1.0, 0, true), "±0.01 above Y 80 / frozen");
        assertFalse(ClimateWindow.fits(0.185, 0.2, 1.0, 0, true));
        assertTrue(ClimateWindow.fits(0.15, 0.2, 1.0, 0.1, false), "tolerance");
        assertFalse(ClimateWindow.fits(2.0, 0.2, 1.0, 0, true));
    }
}
