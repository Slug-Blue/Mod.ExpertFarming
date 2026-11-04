package io.github.slugblue.wildroots.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** WR-153: rich patches never appear where worldgen §5.2 forbids them. */
class RichRulesTest {

    @Test
    void rollAgainstTheChance() {
        assertTrue(RichRules.rich(0.079, 0.08, false, 300, 0, 256));
        assertFalse(RichRules.rich(0.08, 0.08, false, 300, 0, 256));
    }

    @Test
    void neverTheStarterPatch() {
        assertFalse(RichRules.rich(0.0, 1.0, true, 10_000, 10_000, 256));
    }

    @Test
    void notCloserThan256ToSpawn() {
        assertFalse(RichRules.rich(0.0, 1.0, false, 255, 0, 256));
        assertFalse(RichRules.rich(0.0, 1.0, false, 180, 180, 256), "d = 254.6");
        assertTrue(RichRules.rich(0.0, 1.0, false, 256, 0, 256));
        assertTrue(RichRules.rich(0.0, 1.0, false, -200, -200, 256), "d = 282.8");
    }

    /**
     * Review 2, T2: the boulder blob reaches 3 blocks on its -x/-z side, so a
     * plant at Chebyshev distance 3 blocks the landmark; 4 is clear.
     */
    @Test
    void landmarkKeepsMoreThanThreeFromEveryPlant() {
        java.util.List<net.minecraft.core.BlockPos> plants = java.util.List.of(new net.minecraft.core.BlockPos(0, 64, 0));
        assertFalse(RichRules.landmarkClear(plants, 3, 0), "x + 3");
        assertFalse(RichRules.landmarkClear(plants, 3, 3), "diagonal 3");
        assertFalse(RichRules.landmarkClear(plants, -2, 3));
        assertTrue(RichRules.landmarkClear(plants, 4, 0));
        assertTrue(RichRules.landmarkClear(plants, 4, -3));
        assertTrue(RichRules.landmarkClear(java.util.List.of(), 0, 0), "no plants");
    }
}
