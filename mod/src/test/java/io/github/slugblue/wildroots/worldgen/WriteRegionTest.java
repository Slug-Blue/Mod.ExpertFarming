package io.github.slugblue.wildroots.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Review M1: nests stay in the 3×3 write region of the decorated chunk and within 16 blocks. */
class WriteRegionTest {

    @Test
    void originAtLocal15CannotReachChunkPlus2() {
        int cx = 15; // last column of chunk 0
        assertTrue(WriteRegion.nestSpot(cx, 0, cx + 16, 0), "x = 31, chunk +1");
        assertFalse(WriteRegion.nestSpot(cx, 0, cx + 17, 0), "x = 32 is chunk +2 and 17 away");
        assertTrue(WriteRegion.contains(cx, 0, 31, 0));
        assertFalse(WriteRegion.contains(cx, 0, 32, 0));
    }

    @Test
    void originAtLocal0CannotReachChunkMinus2() {
        int cx = 16; // first column of chunk 1
        assertTrue(WriteRegion.nestSpot(cx, 0, 0, 0), "x = 0, chunk 0 = 1 - 1");
        assertFalse(WriteRegion.contains(cx, 0, -1, 0), "x = -1 is chunk -1 = 1 - 2");
        assertTrue(WriteRegion.nestSpot(-16, -16, -32, -16), "negative coordinates, chunk -2 from -1");
        assertFalse(WriteRegion.contains(-16, -16, -33, -16));
    }

    @Test
    void distanceIs16NotMore() {
        assertTrue(WriteRegion.nestSpot(8, 8, 8 + 16, 8));
        assertFalse(WriteRegion.nestSpot(8, 8, 8 + 12, 8 + 12), "d = 16.97");
        assertTrue(WriteRegion.nestSpot(8, 8, 8 + 11, 8 + 11), "d = 15.56");
    }

    @Test
    void boxForLoneTrees() {
        assertTrue(WriteRegion.containsBox(15, 15, 15 + 12, 15, 3), "27 ± 3 stays in chunk 1");
        assertFalse(WriteRegion.containsBox(15, 15, 15 + 14, 15, 3), "29 + 3 = 32 is chunk 2");
    }
}
