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

    /** Review 2, T1: a miss with fewer than half of the ring columns loaded is NOT_LOADED. */
    @Test
    void missIsNotLoadedBelowHalf() {
        assertEquals(Outcome.NOT_LOADED, StarterPatchRules.miss(0, 0), "no columns at all");
        assertEquals(Outcome.NOT_LOADED, StarterPatchRules.miss(100, 51));
        assertEquals(Outcome.NOT_FOUND, StarterPatchRules.miss(100, 50), "exactly half loaded is representative");
        assertEquals(Outcome.NOT_FOUND, StarterPatchRules.miss(100, 0));
    }

    /** Review 2, T1: the third failed attempt gives up, earlier ones retry. */
    @Test
    void thirdFailureGivesUp() {
        org.junit.jupiter.api.Assertions.assertFalse(StarterPatchRules.giveUp(1));
        org.junit.jupiter.api.Assertions.assertFalse(StarterPatchRules.giveUp(2));
        org.junit.jupiter.api.Assertions.assertTrue(StarterPatchRules.giveUp(3));
        assertEquals(3, StarterPatchRules.MAX_ATTEMPTS);
    }

    /** Only a placed or marked patch sets the flag; only NOT_FOUND / NOT_LOADED count an attempt. */
    @Test
    void successAndFailureOutcomes() {
        for (Outcome o : Outcome.values()) {
            boolean ok = o == Outcome.MARKED_EXISTING || o == Outcome.PLACED || o == Outcome.FALLBACK
                    || o == Outcome.ISLAND;
            boolean miss = o == Outcome.NOT_FOUND || o == Outcome.NOT_LOADED;
            assertEquals(ok, o.success(), o.name());
            assertEquals(miss, o.failure(), o.name());
        }
    }
}
