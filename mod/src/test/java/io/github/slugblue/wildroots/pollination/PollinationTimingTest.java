package io.github.slugblue.wildroots.pollination;

import static org.junit.jupiter.api.Assertions.*;

import io.github.slugblue.wildroots.butterfly.ButterflySpawning;
import org.junit.jupiter.api.Test;

class PollinationTimingTest {
    @Test
    void requestedDurationsAreOrderedAndNeverFinishEarly() {
        int previous = Integer.MAX_VALUE;
        for (var method : PollinationTiming.Method.values()) {
            assertTrue(method.ticks() < previous);
            assertFalse(PollinationTiming.elapsed(500, 500 + method.ticks() - 1, method));
            assertTrue(PollinationTiming.elapsed(500, 500 + method.ticks(), method));
            assertFalse(PollinationTiming.elapsed(-1, 100000, method));
            assertFalse(PollinationTiming.elapsed(500, 499, method));
            previous = method.ticks();
        }
        assertEquals(9600, PollinationTiming.Method.SELF.ticks());
        assertEquals(6000, PollinationTiming.Method.NEIGHBOURS.ticks());
        assertEquals(3600, PollinationTiming.Method.BUTTERFLIES.ticks());
        assertEquals(2400, PollinationTiming.Method.BEES.ticks());
        assertEquals(600, PollinationTiming.Method.BRUSH.ticks());
    }

    @Test
    void gameTickWorkHasExactThresholdsAndCannotOverflow() {
        for (var method : PollinationTiming.Method.values()) {
            assertFalse(PollinationTiming.completed(method.ticks() * 3L - 1, method));
            assertTrue(PollinationTiming.completed(method.ticks() * 3L, method));
        }
        assertEquals(3, PollinationTiming.nextWork(0, 3));
        assertEquals(0, PollinationTiming.nextWork(0, 0));
        assertEquals(12, PollinationTiming.nextWork(12, -1));
        assertEquals(3, PollinationTiming.nextWork(-1, 3));
        assertEquals(PollinationTiming.MAX_WORK, PollinationTiming.nextWork(Long.MAX_VALUE, 3));
        assertEquals(PollinationTiming.MAX_WORK, PollinationTiming.nextWork(PollinationTiming.MAX_WORK, Integer.MAX_VALUE));
    }

    @Test
    void fourFlowersAreEnoughAndBirchIsAnIndependentSource() {
        assertFalse(ButterflySpawning.eligible(false, 3));
        assertTrue(ButterflySpawning.eligible(false, 4));
        assertTrue(ButterflySpawning.eligible(false, 5));
        assertTrue(ButterflySpawning.eligible(true, 0));
    }
}
