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
}
