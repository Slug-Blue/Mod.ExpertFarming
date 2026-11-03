package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * WR-122, SEC-09 (TAE): {@link NbtBudget} at the exact byte and depth limits of
 * every root, with modified-UTF-8 strings (NUL = 2 B, astral = 6 B) and list
 * nesting, not only compound chains.
 */
class NbtBudgetBoundaryTest {

    static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    /** A root of exactly {@code limit} written bytes made of a tricky string; one more byte fails. */
    static void stringBoundary(int limit, Function<Tag, NbtBudget.Result> check) throws IOException {
        String unit = "\u0000" + cp(0x1F955) + "ж田"; // 2 + 6 + 2 + 3 = 13 B in modified UTF-8
        CompoundTag t = new CompoundTag();
        t.putString(cp(0x1D400), "");
        int base = NbtBudgetTest.written(t);
        StringBuilder sb = new StringBuilder();
        int room = limit - base;
        while (room >= 13) {
            sb.append(unit);
            room -= 13;
        }
        sb.append("a".repeat(room));
        t.putString(cp(0x1D400), sb.toString());
        assertEquals(limit, NbtBudgetTest.written(t));
        NbtBudget.Result r = check.apply(t);
        assertTrue(r.ok(), () -> "at " + limit + ": " + r);
        assertEquals(limit, r.bytes());
        t.putString(cp(0x1D400), sb + "a");
        assertFalse(check.apply(t).ok(), "one byte over " + limit);
        t.putString(cp(0x1D400), sb + "\u0000");
        assertEquals(limit + 2, NbtBudgetTest.written(t));
        assertFalse(check.apply(t).ok(), "NUL is 2 bytes in modified UTF-8");
    }

    @Test
    void everyRootAtTheByteLimitWithModifiedUtf8() throws IOException {
        stringBoundary(NbtLimits.LOT_MAX_BYTES, NbtLimits::lot);
        stringBoundary(NbtLimits.POUCH_MAX_BYTES, NbtLimits::pouch);
        stringBoundary(NbtLimits.MOTHER_MAX_BYTES, NbtLimits::mother);
        stringBoundary(NbtLimits.BLOCK_ENTITY_MAX_BYTES, NbtLimits::blockEntity);
        stringBoundary(NbtLimits.JOURNAL_MAX_BYTES, NbtLimits::journal);
    }

    /** A compound root with {@code depth - 1} nested lists below it. */
    static CompoundTag listChain(int depth) {
        CompoundTag root = new CompoundTag();
        if (depth == 1) {
            return root;
        }
        ListTag cur = new ListTag();
        root.put("l", cur);
        for (int i = 2; i < depth; i++) {
            ListTag next = new ListTag();
            cur.add(next);
            cur = next;
        }
        cur.add(StringTag.valueOf("leaf")); // leaves add no depth
        return root;
    }

    static void depthBoundary(int maxDepth, Function<Tag, NbtBudget.Result> check) {
        assertTrue(check.apply(NbtBudgetTest.nested(maxDepth)).ok(), "compound chain at " + maxDepth);
        assertFalse(check.apply(NbtBudgetTest.nested(maxDepth + 1)).ok(), "compound chain over " + maxDepth);
        assertTrue(check.apply(listChain(maxDepth)).ok(), "list chain at " + maxDepth);
        assertFalse(check.apply(listChain(maxDepth + 1)).ok(), "list chain over " + maxDepth);
        assertEquals(maxDepth, check.apply(listChain(maxDepth)).depth());
    }

    @Test
    void everyRootAtTheDepthLimit() {
        depthBoundary(NbtLimits.LOT_MAX_DEPTH, NbtLimits::lot);
        depthBoundary(NbtLimits.POUCH_MAX_DEPTH, NbtLimits::pouch);
        depthBoundary(NbtLimits.MOTHER_MAX_DEPTH, NbtLimits::mother);
        depthBoundary(NbtLimits.BLOCK_ENTITY_MAX_DEPTH, NbtLimits::blockEntity);
        depthBoundary(NbtLimits.JOURNAL_MAX_DEPTH, NbtLimits::journal);
    }

    @Test
    void pouchOfSixteenEntriesAtTheByteLimitPassesSeventeenFail() throws IOException {
        CompoundTag pouch = new CompoundTag();
        ListTag e = new ListTag();
        for (int i = 0; i < NbtLimits.POUCH_MAX_ENTRIES; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putByteArray("p", new byte[0]);
            e.add(entry);
        }
        pouch.put("e", e);
        int room = NbtLimits.POUCH_MAX_BYTES - NbtBudgetTest.written(pouch);
        ((CompoundTag) e.get(0)).putByteArray("p", new byte[room]);
        assertEquals(NbtLimits.POUCH_MAX_BYTES, NbtBudgetTest.written(pouch));
        assertTrue(NbtLimits.pouch(pouch).ok());
        ((CompoundTag) e.get(0)).putByteArray("p", new byte[room - 64]);
        e.add(new CompoundTag());
        assertTrue(NbtBudgetTest.written(pouch) <= NbtLimits.POUCH_MAX_BYTES);
        assertFalse(NbtLimits.pouch(pouch).ok(), "17 entries within the byte budget");
    }

    @Test
    void wideRootIsMeasuredWithoutRecursionAndRejected() {
        CompoundTag root = new CompoundTag();
        for (int i = 0; i < 50_000; i++) {
            root.putByte("k" + i, (byte) 1);
        }
        NbtBudget.Result r = NbtLimits.lot(root);
        assertFalse(r.ok());
        assertTrue(r.reason().startsWith("size"), r.reason());
    }
}
