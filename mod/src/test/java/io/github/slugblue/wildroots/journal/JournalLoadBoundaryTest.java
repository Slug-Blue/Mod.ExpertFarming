package io.github.slugblue.wildroots.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Batch 5 test pass: the stored journal at its limits, read back
 * (docs/05-architecture.md §6.1): exactly 64 KiB loads live and one byte
 * more is inert; 256 / 257 entries, 16 / 17 rows, 128 / 129 counters,
 * {@code v} 0, current and future. An inert journal keeps its raw tag and
 * refuses every change. {@code apply} accepts exactly the three actions.
 */
class JournalLoadBoundaryTest {

    static CompoundTag entry(String id, int order, List<DispCodec.Line> rows) {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putInt("o", order);
        if (!rows.isEmpty()) {
            t.put("r", DispCodec.encode(rows));
        }
        return t;
    }

    static CompoundTag root(int entries, int rowsOfFirst, int counters) {
        CompoundTag root = new CompoundTag();
        root.putInt("v", JournalData.CURRENT);
        root.putInt("next", entries);
        ListTag e = new ListTag();
        List<DispCodec.Line> rows = new ArrayList<>();
        for (int i = 0; i < rowsOfFirst; i++) {
            rows.add(DispCodec.Line.of("wildroots.journal.species.found_at", "r" + i));
        }
        for (int i = 0; i < entries; i++) {
            e.add(entry("hint.n" + i, i, i == 0 ? rows : List.of()));
        }
        root.put("e", e);
        if (counters > 0) {
            CompoundTag c = new CompoundTag();
            for (int i = 0; i < counters; i++) {
                c.putLong("c" + i, i + 1);
            }
            root.put("c", c);
        }
        return root;
    }

    static void assertLive(CompoundTag t, String what) {
        JournalData d = JournalData.load(t);
        assertFalse(d.inert(), what + ": " + d.inertReason());
        assertEquals(t, d.save(), what + " does not round-trip");
    }

    static void assertInert(CompoundTag t, String what) {
        JournalData d = JournalData.load(t);
        assertTrue(d.inert(), what + " loaded live");
        assertEquals(t, d.save(), what + ": the raw tag is not kept");
        assertEquals(JournalData.Unlock.NONE, d.unlock("hint.welcome", List.of(), List.of()), what);
        assertFalse(d.setCounter("x", 1), what);
        assertFalse(d.markGiven(), what);
        assertFalse(d.apply(new ResourceLocation("wildroots", "hint.n0"), JournalActionPacket.PIN), what);
        assertEquals(t, d.save(), what + ": an inert journal changed");
    }

    @Test
    void entriesRowsAndCountersAtTheirLimitsAndOneMore() {
        assertLive(root(JournalData.MAX_ENTRIES, 0, 0), "256 entries");
        assertInert(root(JournalData.MAX_ENTRIES + 1, 0, 0), "257 entries");
        assertLive(root(1, JournalData.MAX_ROWS, 0), "16 rows");
        assertInert(root(1, JournalData.MAX_ROWS + 1, 0), "17 rows");
        assertLive(root(1, 0, JournalData.MAX_COUNTERS), "128 counters");
        assertInert(root(1, 0, JournalData.MAX_COUNTERS + 1), "129 counters");
    }

    @Test
    void versionZeroCurrentAndFuture() {
        CompoundTag t = root(1, 0, 0);
        assertLive(t, "current");
        for (int v : new int[] {0, -1, JournalData.CURRENT + 1, Integer.MAX_VALUE}) {
            CompoundTag c = t.copy();
            c.putInt("v", v);
            assertInert(c, "v " + v);
        }
        CompoundTag noV = t.copy();
        noV.remove("v");
        assertInert(noV, "no v");
        CompoundTag stringV = t.copy();
        stringV.putString("v", "1");
        assertInert(stringV, "v as a string");
    }

    /**
     * A journal of exactly 64 KiB (the budget counts the uncompressed
     * {@code NbtIo} bytes) loads live; one byte more (a counter key one
     * character longer) is inert.
     */
    @Test
    void exactly64KiBLoadsLiveAndOneByteMoreIsInert() {
        String arg = "x".repeat(DispCodec.MAX_ARG_LENGTH);
        CompoundTag base = root(0, 0, 0);
        ListTag e = base.getList("e", 10);
        int i = 0;
        // big entries until fewer than ~2 KiB are left
        while (NbtLimits.journal(base).bytes() < NbtLimits.JOURNAL_MAX_BYTES - 2048) {
            List<DispCodec.Line> rows = new ArrayList<>();
            for (int r = 0; r < JournalData.MAX_ROWS; r++) {
                rows.add(DispCodec.Line.of("wildroots.journal.species.found_at", arg));
            }
            e.add(entry("hint.f" + i, i, rows));
            i++;
            base.putInt("next", i);
        }
        assertTrue(e.size() <= JournalData.MAX_ENTRIES);
        // counters fill the rest exactly: each costs 1 + 2 + key + 8 bytes, keys 1..80 characters
        CompoundTag c = new CompoundTag();
        c.putLong("seed", 1);
        base.put("c", c);
        long left = NbtLimits.JOURNAL_MAX_BYTES - NbtLimits.journal(base).bytes();
        int n = 0;
        while (left > 0) {
            assertTrue(c.size() < JournalData.MAX_COUNTERS, "ran out of counters");
            long cost = Math.min(left, 11 + 80);
            if (left - cost > 0 && left - cost < 12) {
                cost = left - 12; // leave room for one more counter of at least one character
            }
            String key = ("k" + n++ + "_").concat("z".repeat(80)).substring(0, (int) cost - 11);
            c.putLong(key, 7);
            left = NbtLimits.JOURNAL_MAX_BYTES - NbtLimits.journal(base).bytes();
        }
        assertEquals(NbtLimits.JOURNAL_MAX_BYTES, NbtLimits.journal(base).bytes(), "not exactly 64 KiB");
        assertLive(base, "exactly 64 KiB");

        CompoundTag over = base.copy();
        over.getCompound("c").remove("seed");
        over.getCompound("c").putLong("seed2", 1); // one character longer
        assertEquals(NbtLimits.JOURNAL_MAX_BYTES + 1, NbtLimits.journal(over).bytes());
        assertInert(over, "64 KiB + 1");
    }

    /** {@code JournalAction}: every action number except 0, 1, 2 is refused; so are null and odd ids. */
    @Test
    void applyAcceptsExactlyTheThreeActions() {
        JournalData j = new JournalData();
        j.unlock("hint.welcome", List.of(), List.of());
        ResourceLocation id = new ResourceLocation("wildroots", "hint.welcome");
        int rev = j.revision();
        for (int a : new int[] {Integer.MIN_VALUE, -2, -1, 3, 4, 255, 256, 65_536, Integer.MAX_VALUE}) {
            assertFalse(j.apply(id, a), "action " + a);
        }
        assertFalse(j.apply(null, JournalActionPacket.PIN), "null id");
        assertFalse(j.apply(JournalActionPacket.MALFORMED.entry(), JournalActionPacket.PIN), "the malformed marker");
        assertFalse(j.apply(new ResourceLocation("wildroots", "welcome"), JournalActionPacket.PIN), "no kind");
        assertFalse(j.apply(new ResourceLocation("wildroots", "hint.welcome/x"), JournalActionPacket.PIN), "path");
        assertFalse(j.apply(new ResourceLocation("wildroots", "hint." + "w".repeat(200)), JournalActionPacket.PIN));
        assertEquals(rev, j.revision(), "a refused action changed the journal");
        for (int a = 0; a <= JournalActionPacket.MAX_ACTION; a++) {
            assertTrue(j.apply(id, a), "action " + a);
        }
        // a change that changes nothing (already read) is accepted but does not count as a change
        int r2 = j.revision();
        assertTrue(j.apply(id, JournalActionPacket.MARK_READ));
        assertTrue(j.apply(id, JournalActionPacket.MARK_READ));
        assertTrue(j.revision() - r2 <= 1, "repeated mark-read changed the journal twice");
    }

    /** A stored entry string that is not a lens/disp row line is damage (the raw tag is kept). */
    @Test
    void forgedRowStringsMakeTheJournalInert() {
        // a stored row is the key without "wildroots.", then arguments after U+001F
        for (String forged : new String[] {"", "", "UPPER.case", "xNot A Key", "xab",
            "x" + "a".repeat(DispCodec.MAX_ARGS + 1), "x" + "a".repeat(DispCodec.MAX_ARG_LENGTH + 1)}) {
            CompoundTag t = root(1, 0, 0);
            ListTag r = new ListTag();
            r.add(StringTag.valueOf(forged));
            t.getList("e", 10).getCompound(0).put("r", r);
            assertInert(t, "row '" + forged + "'");
        }
    }
}
