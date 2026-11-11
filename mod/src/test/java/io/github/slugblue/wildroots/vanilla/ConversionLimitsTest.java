package io.github.slugblue.wildroots.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** WR-162 (SEC-35): at most 16 conversions per level per tick; the POI answer is kept 1200 ticks. */
class ConversionLimitsTest {

    @Test
    void sixteenPerTickThenTheNextTick() {
        TickBudget b = new TickBudget(16);
        for (int i = 0; i < 16; i++) {
            assertTrue(b.tryTake(100), "take " + i);
        }
        assertFalse(b.tryTake(100), "17th in one tick");
        assertFalse(b.tryTake(100), "18th in one tick");
        assertTrue(b.tryTake(101), "next tick");
        for (int i = 1; i < 16; i++) {
            assertTrue(b.tryTake(101));
        }
        assertFalse(b.tryTake(101));
        // a clock that goes back (a command) starts a fresh budget, never a negative one
        assertTrue(b.tryTake(50));
    }

    @Test
    void poiAnswerIsCachedFor1200Ticks() {
        TimedFlagCache c = new TimedFlagCache(1200);
        AtomicInteger queries = new AtomicInteger();
        assertTrue(c.get(7L, 1000, () -> queries.incrementAndGet() > 0));
        assertTrue(c.get(7L, 2199, () -> {
            queries.incrementAndGet();
            return false;
        }), "within 1200 ticks the first answer stays");
        assertEquals(1, queries.get());
        assertFalse(c.get(7L, 2200, () -> {
            queries.incrementAndGet();
            return false;
        }), "after 1200 ticks the query runs again");
        assertEquals(2, queries.get());
        // another chunk has its own answer
        assertTrue(c.get(8L, 2200, () -> true));
        // time going back is a miss, not a stale hit
        assertTrue(c.get(7L, 10, () -> true));
        c.clear();
        assertEquals(0, c.size());
    }

    @Test
    void expiredEntriesArePruned() {
        TimedFlagCache c = new TimedFlagCache(10);
        for (long k = 0; k < TimedFlagCache.PRUNE_AT; k++) {
            c.get(k, 0, () -> true);
        }
        c.get(-1L, 100, () -> true);
        assertEquals(1, c.size());
    }
}
