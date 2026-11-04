package io.github.slugblue.wildroots.pollination;

import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Index of flowering plants (docs/05-architecture.md §10.2; ADR-0008;
 * GDD §4.1; SEC-33). One per {@link ServerLevel}, <b>memory only</b>: it is
 * never written to disk and rebuilds itself as chunks load, because every
 * flowering plant registers in {@code onLoad} of its block entity.
 *
 * <pre>
 * Long2ObjectOpenHashMap&lt;ChunkBloom&gt;          key = ChunkPos.toLong
 * ChunkBloom = Int2ObjectOpenHashMap&lt;LongOpenHashSet&gt;   speciesIndex → packed BlockPos
 * </pre>
 *
 * Add and remove are O(1) ({@link LongOpenHashSet}). A query walks only
 * the chunks that the circle of the radius touches (at most 4 for a radius
 * ≤ 8, 9 for the limit 16) and visits at most {@value #MAX_VISITS} entries,
 * however dense the index is; the matching entries are reservoir-sampled
 * down to {@value #MAX_CANDIDATES}. Server thread only.
 */
public final class BloomIndex {

    /** Entries one query may look at (SEC-33). */
    public static final int MAX_VISITS = 1024;
    /** Candidates kept by a query (GDD §4.1, reservoir sampling). */
    public static final int MAX_CANDIDATES = 64;
    /** Largest radius a query accepts (SEC-33, {@code DataLimits} radius 0–16). */
    public static final int MAX_RADIUS = 16;

    private static final Map<ServerLevel, BloomIndex> LEVELS = new WeakHashMap<>();

    private final Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<LongOpenHashSet>> chunks =
            new Long2ObjectOpenHashMap<>();
    private int size;

    /** The index of {@code level}, created on first use. */
    public static BloomIndex of(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, l -> new BloomIndex());
    }

    /** The index of {@code level} if one exists (no creation). */
    public static BloomIndex peek(ServerLevel level) {
        return LEVELS.get(level);
    }

    /** Level unload: the whole index of {@code level} goes. */
    public static void forget(ServerLevel level) {
        LEVELS.remove(level);
    }

    // ------------------------------------------------------------------ maintenance

    /** Registers a flowering plant; {@code false} if it was there already. */
    public boolean add(int species, long pos) {
        long chunk = chunkKey(pos);
        Int2ObjectOpenHashMap<LongOpenHashSet> bloom = chunks.get(chunk);
        if (bloom == null) {
            bloom = new Int2ObjectOpenHashMap<>(2);
            chunks.put(chunk, bloom);
        }
        LongOpenHashSet set = bloom.get(species);
        if (set == null) {
            set = new LongOpenHashSet(4);
            bloom.put(species, set);
        }
        boolean added = set.add(pos);
        if (added) {
            size++;
        }
        return added;
    }

    /** Removes a plant; {@code false} if it was not registered. */
    public boolean remove(int species, long pos) {
        long chunk = chunkKey(pos);
        Int2ObjectOpenHashMap<LongOpenHashSet> bloom = chunks.get(chunk);
        if (bloom == null) {
            return false;
        }
        LongOpenHashSet set = bloom.get(species);
        if (set == null || !set.remove(pos)) {
            return false;
        }
        size--;
        if (set.isEmpty()) {
            bloom.remove(species);
            if (bloom.isEmpty()) {
                chunks.remove(chunk);
            }
        }
        return true;
    }

    /** Chunk unload: every entry of chunk {@code chunkKey} goes; returns how many. */
    public int removeChunk(long chunkKey) {
        Int2ObjectOpenHashMap<LongOpenHashSet> bloom = chunks.remove(chunkKey);
        if (bloom == null) {
            return 0;
        }
        int n = 0;
        for (LongOpenHashSet s : bloom.values()) {
            n += s.size();
        }
        size -= n;
        return n;
    }

    public boolean contains(int species, long pos) {
        Int2ObjectOpenHashMap<LongOpenHashSet> bloom = chunks.get(chunkKey(pos));
        LongOpenHashSet set = bloom == null ? null : bloom.get(species);
        return set != null && set.contains(pos);
    }

    /** Entries in the whole index. */
    public int size() {
        return size;
    }

    /** Chunks with at least one entry. */
    public int chunkCount() {
        return chunks.size();
    }

    // ------------------------------------------------------------------ queries

    /**
     * Positions found by a query.
     *
     * @param positions packed positions, the first {@code count} are valid
     * @param count     candidates kept (≤ {@value #MAX_CANDIDATES})
     * @param matched   entries that passed the filters (may exceed {@code count})
     * @param visited   entries looked at (≤ {@value #MAX_VISITS})
     */
    public record Sample(long[] positions, int count, int matched, int visited) {
    }

    /**
     * Flowering plants of {@code species} around {@code center}: horizontal
     * Euclidean distance ≤ {@code radius}, {@code |dy| ≤ maxDy}, the center
     * itself excluded; at most {@value #MAX_VISITS} entries visited, at
     * most {@value #MAX_CANDIDATES} kept by reservoir sampling with
     * {@code rngKey}.
     */
    public Sample sample(int species, BlockPos center, int radius, int maxDy, long rngKey) {
        int r = Math.max(0, Math.min(MAX_RADIUS, radius));
        long r2 = (long) r * r;
        long self = center.asLong();
        long[] out = new long[MAX_CANDIDATES];
        SplitMix64 rng = new SplitMix64(rngKey);
        int matched = 0;
        int visited = 0;
        int cx0 = (center.getX() - r) >> 4;
        int cx1 = (center.getX() + r) >> 4;
        int cz0 = (center.getZ() - r) >> 4;
        int cz1 = (center.getZ() + r) >> 4;
        scan:
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!chunkTouchesCircle(cx, cz, center.getX(), center.getZ(), r2)) {
                    continue;
                }
                Int2ObjectOpenHashMap<LongOpenHashSet> bloom = chunks.get(ChunkPos.asLong(cx, cz));
                LongOpenHashSet set = bloom == null ? null : bloom.get(species);
                if (set == null) {
                    continue;
                }
                LongIterator it = set.iterator();
                while (it.hasNext()) {
                    if (visited >= MAX_VISITS) {
                        break scan;
                    }
                    visited++;
                    long p = it.nextLong();
                    if (p == self) {
                        continue;
                    }
                    long dx = BlockPos.getX(p) - center.getX();
                    long dz = BlockPos.getZ(p) - center.getZ();
                    int dy = BlockPos.getY(p) - center.getY();
                    if (dx * dx + dz * dz > r2 || Math.abs(dy) > maxDy) {
                        continue;
                    }
                    if (matched < MAX_CANDIDATES) {
                        out[matched] = p;
                    } else {
                        long j = Math.floorMod(rng.nextLong(), (long) matched + 1);
                        if (j < MAX_CANDIDATES) {
                            out[(int) j] = p;
                        }
                    }
                    matched++;
                }
            }
        }
        return new Sample(out, Math.min(matched, MAX_CANDIDATES), matched, visited);
    }

    /** Visitor of {@link #forEachNear}: species number and packed position; {@code false} stops. */
    @FunctionalInterface
    public interface Visitor {
        boolean visit(int species, long pos);
    }

    /**
     * Every entry of the species accepted by {@code speciesFilter} in the
     * chunks that the circle {@code radius} around {@code center} touches,
     * at most {@value #MAX_VISITS} entries; distance filtering is the
     * visitor's job (bees, §10.3). Returns the entries visited.
     */
    public int forEachNear(BlockPos center, int radius, IntPredicate speciesFilter, Visitor visitor) {
        int r = Math.max(0, Math.min(MAX_RADIUS, radius));
        long r2 = (long) r * r;
        int visited = 0;
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                if (!chunkTouchesCircle(cx, cz, center.getX(), center.getZ(), r2)) {
                    continue;
                }
                Int2ObjectOpenHashMap<LongOpenHashSet> bloom = chunks.get(ChunkPos.asLong(cx, cz));
                if (bloom == null) {
                    continue;
                }
                for (Int2ObjectMap.Entry<LongOpenHashSet> e : bloom.int2ObjectEntrySet()) {
                    if (!speciesFilter.test(e.getIntKey())) {
                        continue;
                    }
                    LongIterator it = e.getValue().iterator();
                    while (it.hasNext()) {
                        if (visited >= MAX_VISITS) {
                            return visited;
                        }
                        visited++;
                        if (!visitor.visit(e.getIntKey(), it.nextLong())) {
                            return visited;
                        }
                    }
                }
            }
        }
        return visited;
    }

    static long chunkKey(long pos) {
        return ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
    }

    /** Whether chunk {@code (cx, cz)} has a column within {@code sqrt(r2)} of {@code (x, z)}. */
    static boolean chunkTouchesCircle(int cx, int cz, int x, int z, long r2) {
        long nx = Math.max(cx * 16L, Math.min(x, cx * 16L + 15));
        long nz = Math.max(cz * 16L, Math.min(z, cz * 16L + 15));
        long dx = nx - x;
        long dz = nz - z;
        return dx * dx + dz * dz <= r2;
    }
}
