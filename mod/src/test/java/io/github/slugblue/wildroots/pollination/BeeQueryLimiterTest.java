package io.github.slugblue.wildroots.pollination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import org.junit.jupiter.api.Test;

/** WR-142: bee query budget (SEC-33) and the pollen record (GDD §4.3; S2). */
class BeeQueryLimiterTest {

    /** One bee asking every tick queries at most once per 20 ticks. */
    @Test
    void oneBeeAtMostOncePer20Ticks() {
        BeeQueryLimiter level = new BeeQueryLimiter();
        BeeQueryLimiter.Gate bee = new BeeQueryLimiter.Gate();
        List<Long> at = new ArrayList<>();
        for (long t = 1000; t < 1200; t++) {
            if (bee.tryQuery(t, level)) {
                at.add(t);
            }
        }
        assertEquals(10, at.size());
        for (int i = 1; i < at.size(); i++) {
            assertTrue(at.get(i) - at.get(i - 1) >= BeeQueryLimiter.BEE_INTERVAL, "queries at " + at);
        }
    }

    /** 100 bees in one tick: 64 query, 36 are put off to the next tick and query there. */
    @Test
    void atMost64PerLevelTickTheRestDeferred() {
        BeeQueryLimiter level = new BeeQueryLimiter();
        List<BeeQueryLimiter.Gate> bees = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            bees.add(new BeeQueryLimiter.Gate());
        }
        int first = 0;
        List<BeeQueryLimiter.Gate> deferred = new ArrayList<>();
        for (BeeQueryLimiter.Gate b : bees) {
            if (b.tryQuery(500, level)) {
                first++;
            } else {
                deferred.add(b);
            }
        }
        assertEquals(BeeQueryLimiter.PER_LEVEL_TICK, first);
        assertEquals(36, deferred.size());
        int second = 0;
        for (BeeQueryLimiter.Gate b : deferred) {
            if (b.tryQuery(501, level)) {
                second++;
            }
        }
        assertEquals(36, second, "deferred bees query on the next tick");
        for (BeeQueryLimiter.Gate b : bees) {
            assertFalse(b.tryQuery(502, level), "every bee waits its 20 ticks after a query");
        }
    }

    @Test
    void pollenFreshnessWindow() {
        BeePollen p = new BeePollen();
        assertFalse(p.fresh(), "no pollen");
        p.collect("wildroots:wild_carrot", 42L, new FatherRecord(7L, new long[] {1, 2}));
        assertTrue(p.fresh());
        p.setCounters(0, BeePollen.MAX_AGE_OUTSIDE);
        assertTrue(p.fresh(), "1200 ticks outside is still fresh");
        p.tickOutside();
        assertFalse(p.fresh(), "1201 ticks outside is stale");
        p.setCounters(1, 0);
        assertTrue(p.fresh(), "one visit in between");
        p.countVisit();
        assertFalse(p.fresh(), "two visits in between");
        p.collect("wildroots:wild_carrot", 43L, new FatherRecord(7L, new long[] {3}));
        assertTrue(p.fresh() && p.visits() == 0 && p.ageOutside() == 0, "a new plant resets the pollen");
    }

    @Test
    void pollenNbtRoundTripAndRejectsGarbage() {
        BeePollen p = new BeePollen();
        p.collect("wildroots:wild_carrot", 42L, new FatherRecord(7L, new long[] {1, 2}));
        p.setCounters(1, 77);
        BeePollen q = new BeePollen();
        q.load(p.save());
        assertEquals("wildroots:wild_carrot", q.species());
        assertEquals(42L, q.source());
        assertEquals(1, q.visits());
        assertEquals(77, q.ageOutside());
        assertEquals(p.genome(), q.genome());

        CompoundTag big = p.save();
        big.put("a", new LongArrayTag(new long[9]));
        q.load(big);
        assertFalse(q.hasPollen(), "genome over 8 longs");
        CompoundTag bad = p.save();
        bad.putString("sp", "Not A Valid:Id!");
        q.load(bad);
        assertFalse(q.hasPollen(), "bad species id");
        CompoundTag wrongType = p.save();
        wrongType.putInt("a", 5);
        q.load(wrongType);
        assertFalse(q.hasPollen(), "wrong tag type");
        q.load(new CompoundTag());
        assertFalse(q.hasPollen());
    }
}
