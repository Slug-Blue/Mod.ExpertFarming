package io.github.slugblue.wildroots.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.slugblue.wildroots.core.config.ServerConfig.StarterPatch;
import io.github.slugblue.wildroots.worldgen.StarterPatchRules.Outcome;
import org.junit.jupiter.api.Test;

/** WR-152: when the starter patch runs (worldgen §6.2). */
class StarterPatchRulesTest {

    @Test
    void newWorldRuns() {
        assertNull(StarterPatchRules.decide(StarterPatch.TRUE, false, false, 0));
        assertNull(StarterPatchRules.decide(StarterPatch.FORCE, false, false, 0));
    }

    @Test
    void existingWorldNeedsForce() {
        assertEquals(Outcome.EXISTING_WORLD, StarterPatchRules.decide(StarterPatch.TRUE, false, false, 12_000));
        assertNull(StarterPatchRules.decide(StarterPatch.FORCE, false, false, 12_000));
        // the data file exists (the mod was there from the start) but the patch is not done yet: it runs
        assertNull(StarterPatchRules.decide(StarterPatch.TRUE, true, false, 12_000));
    }

    @Test
    void onceAndOff() {
        assertEquals(Outcome.ALREADY_DONE, StarterPatchRules.decide(StarterPatch.TRUE, true, true, 0));
        assertEquals(Outcome.ALREADY_DONE, StarterPatchRules.decide(StarterPatch.FORCE, true, true, 99));
        assertEquals(Outcome.DISABLED, StarterPatchRules.decide(StarterPatch.FALSE, false, false, 0));
    }
}
