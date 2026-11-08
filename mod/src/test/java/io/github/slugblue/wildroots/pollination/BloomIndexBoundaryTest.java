package io.github.slugblue.wildroots.pollination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** WR-140/142 boundary pass: the bloom index and the bee query budget under load and at the edges. */
class BloomIndexBoundaryTest {

    static final int CARROT = 0;
    static final int OTHER = 1;

    /** Thousands of plants in one chunk (every column, 20 layers): a query still visits ≤ 1024. */
    @Test
    void thousandsInOneChunkVisitAtMost1024() {
        BloomIndex idx = new BloomIndex();
        for (int y = 54; y < 74; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    idx.add(CARROT, BlockPos.asLong(x, y, z));
                }
            }
        }
        assertEquals(5120, idx.size());
        assertEquals(1, idx.chunkCount());
        BloomIndex.Sample s = idx.sample(CARROT, new BlockPos(8, 64, 8), 16, 2, 1L);
        assertEquals(1024, s.visited());
        assertTrue(s.count() == BloomIndex.MAX_CANDIDATES && s.matched() <= s.visited());
        int bees = idx.forEachNear(new BlockPos(8, 64, 8), 6, sp -> true, (sp, p) -> true);
        assertEquals(1024, bees);
        // other species in the same chunk do not use the budget of a carrot query
        BloomIndex mixed = new BloomIndex();
        for (int i = 0; i < 4000; i++) {
            mixed.add(OTHER, BlockPos.asLong(i % 16, 60 + i / 256, (i / 16) % 16));
        }
        mixed.add(CARROT, BlockPos.asLong(9, 64, 8));
        BloomIndex.Sample m = mixed.sample(CARROT, new BlockPos(8, 64, 8), 4, 2, 1L);
        assertEquals(1, m.count(), "the one carrot among 4000 others");
        assertEquals(1, m.visited(), "only the carrot set is walked");
    }

    /** Radius over the limit is clamped to 16, negative to 0 (SEC-33). */
    @Test
    void radiusIsClamped() {
        BloomIndex idx = new BloomIndex();
        idx.add(CARROT, BlockPos.asLong(16, 64, 0));
        idx.add(CARROT, BlockPos.asLong(17, 64, 0));
        idx.add(CARROT, BlockPos.asLong(40, 64, 0));
        assertEquals(1, idx.sample(CARROT, new BlockPos(0, 64, 0), 1000, 2, 1L).count(), "clamped to 16");
        assertEquals(0, idx.sample(CARROT, new BlockPos(0, 64, 0), -5, 2, 1L).count(), "negative radius");
        assertEquals(0, idx.sample(CARROT, new BlockPos(16, 64, 0), 0, 2, 1L).count(), "radius 0: only itself");
    }

    /** Chunk borders at negative coordinates: -1 is chunk -1, -16 is chunk -1, -17 is chunk -2. */
    @Test
    void negativeChunkBorders() {
        BloomIndex idx = new BloomIndex();
        BlockPos c = new BlockPos(-16, 64, -16);
        long[] around = {BlockPos.asLong(-17, 64, -16), BlockPos.asLong(-15, 64, -16), BlockPos.asLong(-16, 64, -17),
            BlockPos.asLong(-16, 64, -15), BlockPos.asLong(-1, 64, -1), BlockPos.asLong(0, 64, 0)};
        for (long p : around) {
            idx.add(CARROT, p);
        }
        assertEquals(ChunkPos.asLong(-2, -1), BloomIndex.chunkKey(around[0]));
        assertEquals(ChunkPos.asLong(-1, -1), BloomIndex.chunkKey(around[1]));
        BloomIndex.Sample s = idx.sample(CARROT, c, 1, 0, 1L);
        Set<Long> got = new HashSet<>();
        for (int i = 0; i < s.count(); i++) {
            got.add(s.positions()[i]);
        }
        assertEquals(Set.of(around[0], around[1], around[2], around[3]), got, "all four neighbours across the borders");
        assertEquals(4, idx.sample(CARROT, c, 16, 0, 1L).count(), "(-1,-1) and (0,0) are 21+ blocks away");
        assertEquals(6, idx.sample(CARROT, new BlockPos(-8, 64, -8), 16, 0, 1L).count(), "all within 16 of (-8,-8)");
    }

    /** A removal during a walk is not possible (server thread); removing the last entry of a chunk drops it. */
    @Test
    void removeChunkOfUnknownAndEmpty() {
        BloomIndex idx = new BloomIndex();
        assertEquals(0, idx.removeChunk(ChunkPos.asLong(5, 5)));
        assertFalse(idx.remove(CARROT, BlockPos.asLong(0, 0, 0)));
        idx.add(CARROT, BlockPos.asLong(0, 0, 0));
        idx.add(OTHER, BlockPos.asLong(1, 0, 0));
        assertEquals(2, idx.removeChunk(ChunkPos.asLong(0, 0)));
        assertEquals(0, idx.size());
        assertEquals(0, idx.chunkCount());
    }

    /**
     * Load: 1000 bees ask in the same tick. 64 get an answer, the rest are
     * put off by one tick; within 16 ticks every bee was answered once, and
     * no tick answered more than 64. A bee then waits its 20 ticks.
     */
    @Test
    void thousandBeesUnderTheLevelBudget() {
        BeeQueryLimiter level = new BeeQueryLimiter();
        List<BeeQueryLimiter.Gate> bees = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            bees.add(new BeeQueryLimiter.Gate());
        }
        long[] answered = new long[1000];
        java.util.Arrays.fill(answered, -1);
        for (long t = 100; t < 100 + BeeQueryLimiter.BEE_INTERVAL; t++) {
            int inTick = 0;
            for (int i = 0; i < bees.size(); i++) {
                if (bees.get(i).tryQuery(t, level)) {
                    inTick++;
                    assertEquals(-1, answered[i], "bee " + i + " answered twice within 20 ticks");
                    answered[i] = t;
                }
            }
            assertTrue(inTick <= BeeQueryLimiter.PER_LEVEL_TICK, "tick " + t + " answered " + inTick);
        }
        for (int i = 0; i < 1000; i++) {
            assertTrue(answered[i] >= 100 && answered[i] < 116, "bee " + i + " answered at " + answered[i]);
        }
        // the first bee answered at 100 may ask again at 120, not at 119
        assertFalse(bees.get(0).tryQuery(119, level));
        assertTrue(bees.get(0).tryQuery(120, level));
    }
}
