package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * WR-122, SEC-09: {@link NbtBudget} counts exactly the bytes of
 * {@code NbtIo.write}, rejects oversized and deep roots early and without
 * recursion; the limits of {@link NbtLimits}.
 */
class NbtBudgetTest {

    static int written(CompoundTag t) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.write(t, new DataOutputStream(bos));
        return bos.size();
    }

    /** Deterministic generator (SplitMix64 step), no java.util.Random in tests of size. */
    static final class Gen {
        long s;

        Gen(long seed) {
            s = seed;
        }

        long next() {
            long z = (s += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        int below(int n) {
            return (int) Long.remainderUnsigned(next(), n);
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            int n = below(12);
            for (int i = 0; i < n; i++) {
                int kind = below(4);
                sb.append(kind == 0 ? (char) ('a' + below(26)) : kind == 1 ? (char) (0x400 + below(0x60))
                        : kind == 2 ? (char) (0x4E00 + below(0x100)) : (char) below(3)); // includes U+0000
            }
            return sb.toString();
        }

        Tag tag(int depth) {
            int kind = depth > 5 ? below(9) : below(11);
            return switch (kind) {
                case 0 -> ByteTag.valueOf((byte) next());
                case 1 -> ShortTag.valueOf((short) next());
                case 2 -> IntTag.valueOf((int) next());
                case 3 -> LongTag.valueOf(next());
                case 4 -> FloatTag.valueOf(below(1000) / 7f);
                case 5 -> DoubleTag.valueOf(below(1000) / 7d);
                case 6 -> StringTag.valueOf(string());
                case 7 -> new LongArrayTag(new long[below(6)]);
                case 8 -> below(2) == 0 ? new IntArrayTag(new int[below(6)]) : new ByteArrayTag(new byte[below(9)]);
                case 9 -> {
                    ListTag l = new ListTag();
                    int n = below(4);
                    for (int i = 0; i < n; i++) {
                        l.add(StringTag.valueOf(string()));
                    }
                    if (below(2) == 0) {
                        ListTag nested = new ListTag();
                        nested.add(compound(depth + 1));
                        yield nested;
                    }
                    yield l;
                }
                default -> compound(depth + 1);
            };
        }

        CompoundTag compound(int depth) {
            CompoundTag c = new CompoundTag();
            int n = below(5);
            for (int i = 0; i < n; i++) {
                c.put(string() + i, tag(depth));
            }
            return c;
        }
    }

    @Test
    void bytesMatchNbtIoOnRandomTrees() throws IOException {
        Gen g = new Gen(42);
        for (int i = 0; i < 5000; i++) {
            CompoundTag t = g.compound(1);
            NbtBudget.Result r = NbtBudget.measure(t, Long.MAX_VALUE, Integer.MAX_VALUE);
            assertTrue(r.ok());
            assertEquals(written(t), r.bytes(), () -> "tree " + t);
        }
    }

    @Test
    void depthCountsContainersFromOne() {
        CompoundTag root = new CompoundTag();
        root.putInt("x", 1);
        assertEquals(1, NbtBudget.measure(root, 1000, 10).depth());
        CompoundTag l2 = new CompoundTag();
        ListTag l3 = new ListTag();
        l3.add(new LongArrayTag(new long[3]));
        l2.put("list", l3);
        root.put("c", l2);
        assertEquals(3, NbtBudget.measure(root, 1000, 10).depth());
        assertFalse(NbtBudget.measure(root, 1000, 2).ok());
    }

    @Test
    void deepNestingIsRejectedWithoutStackOverflow() {
        CompoundTag root = new CompoundTag();
        CompoundTag cur = root;
        for (int i = 0; i < 100_000; i++) {
            CompoundTag next = new CompoundTag();
            cur.put("n", next);
            cur = next;
        }
        NbtBudget.Result r = NbtBudget.measure(root, Long.MAX_VALUE, NbtLimits.POUCH_MAX_DEPTH);
        assertFalse(r.ok());
        assertEquals(NbtLimits.POUCH_MAX_DEPTH + 1, r.depth());
        // and an unlimited walk of the same tree is still iterative
        assertTrue(NbtBudget.measure(root, Long.MAX_VALUE, Integer.MAX_VALUE).ok());
    }

    @Test
    void oversizedRootStopsEarly() {
        CompoundTag root = new CompoundTag();
        ListTag big = new ListTag();
        for (int i = 0; i < 100_000; i++) {
            big.add(new LongArrayTag(new long[8]));
        }
        root.put("big", big);
        NbtBudget.Result r = NbtBudget.measure(root, NbtLimits.LOT_MAX_BYTES, 10);
        assertFalse(r.ok());
        assertTrue(r.bytes() <= NbtLimits.LOT_MAX_BYTES + 100, "walk did not stop early: " + r.bytes());
        assertFalse(NbtBudget.measure(null, 10, 10).ok());
    }

    @Test
    void limitsOfEveryRootAtTheBoundary() throws IOException {
        assertBoundary(NbtLimits.LOT_MAX_BYTES, NbtLimits::lot);
        assertBoundary(NbtLimits.POUCH_MAX_BYTES, NbtLimits::pouch);
        assertBoundary(NbtLimits.MOTHER_MAX_BYTES, NbtLimits::mother);
        assertBoundary(NbtLimits.BLOCK_ENTITY_MAX_BYTES, NbtLimits::blockEntity);
        assertBoundary(NbtLimits.JOURNAL_MAX_BYTES, NbtLimits::journal);
        assertEquals(1024, NbtLimits.LOT_MAX_BYTES);
        assertEquals(17_408, NbtLimits.POUCH_MAX_BYTES);
        assertEquals(512, NbtLimits.MOTHER_MAX_BYTES);
        assertEquals(2048, NbtLimits.BLOCK_ENTITY_MAX_BYTES);
    }

    /** A root of exactly {@code limit} bytes passes, one byte more fails. */
    private static void assertBoundary(int limit, java.util.function.Function<Tag, NbtBudget.Result> check)
            throws IOException {
        CompoundTag t = new CompoundTag();
        t.putByteArray("p", new byte[0]);
        int base = written(t);
        t.putByteArray("p", new byte[limit - base]);
        assertEquals(limit, written(t));
        assertTrue(check.apply(t).ok(), "at the limit " + limit);
        t.putByteArray("p", new byte[limit - base + 1]);
        assertFalse(check.apply(t).ok(), "one byte over " + limit);
    }

    @Test
    void depthLimitsOfLotAndPouch() {
        assertTrue(NbtLimits.lot(nested(5)).ok());
        assertFalse(NbtLimits.lot(nested(6)).ok());
        assertTrue(NbtLimits.pouch(nested(7)).ok());
        assertFalse(NbtLimits.pouch(nested(8)).ok());
    }

    @Test
    void pouchWithSeventeenEntriesIsRejected() {
        CompoundTag pouch = new CompoundTag();
        ListTag e = new ListTag();
        for (int i = 0; i < 17; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putByte("c", (byte) 1);
            e.add(entry);
        }
        pouch.put("e", e);
        assertFalse(NbtLimits.pouch(pouch).ok());
        e.remove(16);
        assertTrue(NbtLimits.pouch(pouch).ok());
    }

    /** Compound chain of {@code depth} levels. */
    static CompoundTag nested(int depth) {
        CompoundTag root = new CompoundTag();
        CompoundTag cur = root;
        for (int i = 1; i < depth; i++) {
            CompoundTag next = new CompoundTag();
            cur.put("n", next);
            cur = next;
        }
        return root;
    }
}
