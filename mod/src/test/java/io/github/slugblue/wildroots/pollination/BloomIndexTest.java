package io.github.slugblue.wildroots.pollination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** WR-140: bloom index (docs/05-architecture.md §10.2; ADR-0008; SEC-33). */
class BloomIndexTest {

    static final int CARROT = 0;
    static final int OTHER = 1;

    static long p(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    @Test
    void addRemoveAndChunkCleanup() {
        BloomIndex idx = new BloomIndex();
        assertTrue(idx.add(CARROT, p(1, 64, 1)));
        assertFalse(idx.add(CARROT, p(1, 64, 1)), "a set: the same plant once");
        assertTrue(idx.add(OTHER, p(1, 64, 1)));
        assertTrue(idx.add(CARROT, p(20, 64, 1)));
        assertEquals(3, idx.size());
        assertEquals(2, idx.chunkCount());
        assertTrue(idx.remove(CARROT, p(1, 64, 1)));
        assertFalse(idx.remove(CARROT, p(1, 64, 1)));
        assertFalse(idx.contains(CARROT, p(1, 64, 1)));
        assertTrue(idx.contains(OTHER, p(1, 64, 1)));
        assertEquals(1, idx.removeChunk(net.minecraft.world.level.ChunkPos.asLong(0, 0)));
        assertEquals(1, idx.size());
        assertEquals(1, idx.chunkCount());
        assertTrue(idx.remove(CARROT, p(20, 64, 1)));
        assertEquals(0, idx.size());
        assertEquals(0, idx.chunkCount(), "empty chunks do not stay behind");
    }

    /** Removal is O(1): 100 000 removals of a 100 000-entry index stay fast (a list would be O(n²)). */
    @Test
    void removalIsConstantTime() {
        BloomIndex idx = new BloomIndex();
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            idx.add(CARROT, p(i % 64, 64 + (i / 4096), (i / 64) % 64));
        }
        assertEquals(n, idx.size());
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            assertTrue(idx.remove(CARROT, p(i % 64, 64 + (i / 4096), (i / 64) % 64)));
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(0, idx.size());
        assertTrue(ms < 2_000, "100 000 removals took " + ms + " ms");
    }

    /** SEC-33: 100 000 entries around the mother, the query visits at most 1024 and keeps at most 64. */
    @Test
    void queryVisitsAtMost1024() {
        BloomIndex idx = new BloomIndex();
        for (int i = 0; i < 100_000; i++) {
            idx.add(CARROT, p(Math.floorMod(i, 32) - 16, i / 1024, Math.floorMod(i / 32, 32) - 16));
        }
        assertEquals(100_000, idx.size());
        BloomIndex.Sample s = idx.sample(CARROT, new BlockPos(0, 64, 0), 8, 2, 7L);
        assertTrue(s.visited() <= BloomIndex.MAX_VISITS, "visited " + s.visited());
        assertEquals(BloomIndex.MAX_VISITS, s.visited());
        assertTrue(s.count() <= BloomIndex.MAX_CANDIDATES);
        int bees = idx.forEachNear(new BlockPos(0, 64, 0), 5, sp -> true, (sp, pos) -> true);
        assertTrue(bees <= BloomIndex.MAX_VISITS, "bee walk visited " + bees);
    }

    @Test
    void queryFiltersRadiusYSelfAndSpecies() {
        BloomIndex idx = new BloomIndex();
        BlockPos c = new BlockPos(8, 64, 8);
        idx.add(CARROT, c.asLong());                 // the mother itself
        idx.add(CARROT, p(10, 64, 8));               // d = 2: in
        idx.add(CARROT, p(9, 66, 9));                // dy = 2: in
        idx.add(CARROT, p(9, 67, 9));                // dy = 3: out
        idx.add(CARROT, p(10, 64, 10));              // d² = 8 > 4: out
        idx.add(OTHER, p(9, 64, 8));                 // other species: out
        idx.add(CARROT, p(17, 64, 8));               // next chunk, d = 9: out
        BloomIndex.Sample s = idx.sample(CARROT, c, 2, 2, 1L);
        Set<Long> got = new HashSet<>();
        for (int i = 0; i < s.count(); i++) {
            got.add(s.positions()[i]);
        }
        assertEquals(Set.of(p(10, 64, 8), p(9, 66, 9)), got);
    }

    /** Radius ≤ 8 touches at most 4 chunks; a plant across the chunk border is found. */
    @Test
    void queryCrossesChunkBorders() {
        BloomIndex idx = new BloomIndex();
        BlockPos c = new BlockPos(15, 64, 15);
        idx.add(CARROT, p(16, 64, 16));
        idx.add(CARROT, p(14, 64, 16));
        idx.add(CARROT, p(16, 64, 14));
        assertEquals(3, idx.sample(CARROT, c, 2, 2, 1L).count());
        int touched = 0;
        for (int cx = -1; cx <= 2; cx++) {
            for (int cz = -1; cz <= 2; cz++) {
                if (BloomIndex.chunkTouchesCircle(cx, cz, 15, 15, 64)) {
                    touched++;
                }
            }
        }
        assertEquals(4, touched);
    }

    /** More than 64 matches: reservoir keeps exactly 64, deterministic for the same key. */
    @Test
    void reservoirKeeps64Deterministically() {
        BloomIndex idx = new BloomIndex();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                idx.add(CARROT, p(x, 64, z));
            }
        }
        BloomIndex.Sample a = idx.sample(CARROT, new BlockPos(0, 64, 0), 8, 2, 99L);
        BloomIndex.Sample b = idx.sample(CARROT, new BlockPos(0, 64, 0), 8, 2, 99L);
        assertEquals(64, a.count());
        assertTrue(a.matched() > 64);
        assertEquals(java.util.Arrays.toString(a.positions()), java.util.Arrays.toString(b.positions()));
    }
}
