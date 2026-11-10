package io.github.slugblue.wildroots.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.NbtBudget;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** WR-180: the journal model, its NBT, limits and the JournalAction rules (SEC-04, SEC-05, SEC-09). */
class JournalDataTest {

    static ResourceLocation rl(String id) {
        return new ResourceLocation("wildroots", id);
    }

    @Test
    void journalActionWithAForeignOrUnknownIdIsDropped() {
        JournalData mine = new JournalData();
        JournalData theirs = new JournalData();
        mine.unlock("hint.welcome", List.of(), List.of());
        theirs.unlock("hint.bees", List.of(), List.of());
        mine.lock("cultivar.carrot", List.of());
        int rev = mine.revision();
        // the other player's entry, an id nobody has, a foreign namespace, a locked silhouette, a bad action
        assertFalse(mine.apply(rl("hint.bees"), JournalActionPacket.PIN));
        assertFalse(mine.apply(rl("hint.nothing_like_this"), JournalActionPacket.MARK_READ));
        assertFalse(mine.apply(new ResourceLocation("minecraft", "hint.welcome"), JournalActionPacket.PIN));
        assertFalse(mine.apply(rl("cultivar.carrot"), JournalActionPacket.MARK_READ));
        assertFalse(mine.apply(rl("hint.welcome"), 7));
        assertEquals(rev, mine.revision(), "a refused action changed the journal");
        assertFalse(theirs.has("hint.welcome"));
        assertFalse(mine.entry("hint.welcome").orElseThrow().is(JournalData.PINNED));

        assertTrue(mine.apply(rl("hint.welcome"), JournalActionPacket.PIN));
        assertTrue(mine.entry("hint.welcome").orElseThrow().is(JournalData.PINNED));
        assertTrue(mine.apply(rl("hint.welcome"), JournalActionPacket.MARK_READ));
        JournalData.Entry e = mine.entry("hint.welcome").orElseThrow();
        assertTrue(e.is(JournalData.READ) && !e.is(JournalData.NEW));
        assertTrue(mine.apply(rl("hint.welcome"), JournalActionPacket.UNPIN));
        assertFalse(mine.entry("hint.welcome").orElseThrow().is(JournalData.PINNED));
    }

    @Test
    void unlockOncePerPlayerAndUpdateKeepsOrderAndMarks() {
        JournalData j = new JournalData();
        assertEquals(JournalData.Unlock.NEW, j.unlock("hint.welcome", List.of("\u0001species.wild_carrot"), List.of()));
        assertEquals(JournalData.Unlock.NONE, j.unlock("hint.welcome", List.of("\u0001species.wild_carrot"), List.of()));
        assertEquals(JournalData.Unlock.NEW, j.unlock("species.wild_carrot", List.of(), List.of()));
        j.apply(rl("species.wild_carrot"), JournalActionPacket.PIN);
        DispCodec.Line row = DispCodec.Line.of("wildroots.journal.species.found_at", "\u0001biome.minecraft.plains", "10", "-4");
        assertEquals(JournalData.Unlock.UPDATED, j.unlock("species.wild_carrot", List.of(), List.of(row)));
        JournalData.Entry e = j.entry("species.wild_carrot").orElseThrow();
        assertEquals(1, e.order());
        assertTrue(e.is(JournalData.PINNED));
        assertEquals(List.of(row), e.rows());
    }

    @Test
    void lockedSilhouetteIsReplacedByTheRealEntryButNeverTheOtherWay() {
        JournalData j = new JournalData();
        assertTrue(j.lock("cultivar.eastern_carrot", List.of("\u0001journal.cultivar.eastern_carrot.goal")));
        assertFalse(j.has("cultivar.eastern_carrot"));
        assertEquals(JournalData.Unlock.NEW, j.unlock("cultivar.eastern_carrot", List.of("3"), List.of()));
        assertTrue(j.has("cultivar.eastern_carrot"));
        assertFalse(j.lock("cultivar.eastern_carrot", List.of()));
        assertTrue(j.has("cultivar.eastern_carrot"));
    }

    @Test
    void invalidIdsArgsAndRowsAreRefused() {
        JournalData j = new JournalData();
        assertEquals(JournalData.Unlock.NONE, j.unlock("welcome", List.of(), List.of()));
        assertEquals(JournalData.Unlock.NONE, j.unlock("hint.Welcome", List.of(), List.of()));
        assertEquals(JournalData.Unlock.NONE, j.unlock("hint.a", List.of("x‮y"), List.of()));
        assertEquals(JournalData.Unlock.NONE, j.unlock("hint.a", List.of("1", "2", "3", "4", "5"), List.of()));
        assertEquals(JournalData.Unlock.NONE, j.unlock("hint.a", List.of(), List.of(DispCodec.Line.of("minecraft.oops"))));
        assertTrue(j.entries().isEmpty());
    }

    @Test
    void saveLoadRoundTripKeepsEverything() {
        JournalData j = new JournalData();
        j.markGiven();
        j.unlock("hint.welcome", List.of("\u0001species.wild_carrot"), List.of());
        j.unlock("species.wild_carrot", List.of(), List.of(DispCodec.Line.of("wildroots.journal.cultivar.carrot.goal")));
        j.lock("cultivar.carrot", List.of());
        j.apply(rl("hint.welcome"), JournalActionPacket.PIN);
        j.takeToast(1234);
        j.setCounter("select_quarter.wild_carrot", 5);
        CompoundTag t = j.save();
        assertTrue(NbtLimits.journal(t).ok());
        JournalData back = JournalData.load(t);
        assertFalse(back.inert());
        assertTrue(back.given());
        assertEquals(j.ordered(), back.ordered());
        assertEquals(5, back.counter("select_quarter.wild_carrot"));
        assertEquals(t, back.save());
        assertFalse(back.takeToast(1234 + JournalData.TOAST_INTERVAL_TICKS - 1));
        assertTrue(back.takeToast(1234 + JournalData.TOAST_INTERVAL_TICKS));
    }

    @Test
    void journalStaysWithin64KiBAndRefusesTheEntryThatWouldBreakIt() {
        JournalData j = new JournalData();
        String arg = "x".repeat(DispCodec.MAX_ARG_LENGTH);
        List<DispCodec.Line> rows = new ArrayList<>();
        for (int i = 0; i < JournalData.MAX_ROWS; i++) {
            rows.add(DispCodec.Line.of("wildroots.journal.species.found_at", arg, arg, arg, arg));
        }
        int accepted = 0;
        for (int i = 0; i < JournalData.MAX_ENTRIES + 10; i++) {
            if (j.unlock("hint.filler_" + i, List.of(arg, arg, arg, arg), rows) == JournalData.Unlock.NEW) {
                accepted++;
            }
        }
        assertTrue(accepted > 0 && accepted < JournalData.MAX_ENTRIES, "accepted " + accepted);
        assertTrue(NbtLimits.journal(j.save()).ok(), "saved journal over the limit");
        // a small entry still fits after the big ones were refused
        assertEquals(JournalData.Unlock.NEW, j.unlock("hint.small", List.of(), List.of()));
    }

    @Test
    void entryCountIsCapped() {
        JournalData j = new JournalData();
        for (int i = 0; i < JournalData.MAX_ENTRIES + 5; i++) {
            j.unlock("hint.n" + i, List.of(), List.of());
        }
        assertEquals(JournalData.MAX_ENTRIES, j.entries().size());
    }

    @Test
    void damagedOrFutureJournalIsInertAndKeepsItsRawTag() {
        CompoundTag future = new CompoundTag();
        future.putInt("v", JournalData.CURRENT + 1);
        future.putString("other", "kept");
        JournalData f = JournalData.load(future);
        assertTrue(f.inert());
        assertEquals(future, f.save());
        assertEquals(JournalData.Unlock.NONE, f.unlock("hint.welcome", List.of(), List.of()));
        assertFalse(f.markGiven());

        CompoundTag bad = new JournalData().save();
        ListTag e = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("id", "not an id");
        e.add(entry);
        bad.put("e", e);
        assertTrue(JournalData.load(bad).inert());
    }

    @Test
    void copyFromCarriesTheJournalOverDeath() {
        JournalData old = new JournalData();
        old.markGiven();
        old.unlock("hint.welcome", List.of(), List.of());
        JournalData fresh = new JournalData();
        fresh.copyFrom(old);
        assertTrue(fresh.given());
        assertTrue(fresh.has("hint.welcome"));
        assertEquals(old.save(), fresh.save());
    }

    /** Review 1, m3: 40 named lines (4+ counters each) never lock out other counters; LRU eviction. */
    @Test
    void countersEvictTheOldestSoLineHintsKeepWorking() {
        JournalData j = new JournalData();
        for (int line = 0; line < 40; line++) {
            String key = LineProgress.lineKey("Line " + line);
            for (int gen = 1; gen <= 3; gen++) {
                LineProgress.observe(j, key, gen, 0.3);
            }
            assertTrue(LineProgress.mayFire(j, "stagnation", key, 5), "line " + line);
        }
        assertTrue(j.counters().size() <= JournalData.MAX_COUNTERS, "counters " + j.counters().size());
        assertTrue(j.setCounter("sq.wild_carrot.n", 3), "select_quarter counter refused");
        assertEquals(3, j.counter("sq.wild_carrot.n"));
        // the newest line still has its state; the oldest was evicted
        String newest = LineProgress.lineKey("Line 39");
        assertTrue(j.counter("lp." + newest + ".n") != 0);
        assertEquals(0, j.counter("lp." + LineProgress.lineKey("Line 0") + ".n"));
        // setting an existing counter makes it the newest
        j.setCounter("sq.wild_carrot.n", 4);
        assertEquals("sq.wild_carrot.n", j.counters().keySet().stream().reduce((a, b) -> b).orElseThrow());
    }

    /** Review 1, m3: a counter never takes the journal over 64 KiB; old counters make room. */
    @Test
    void countersRespectThe64KiBLimit() {
        JournalData j = new JournalData();
        String arg = "x".repeat(DispCodec.MAX_ARG_LENGTH);
        List<DispCodec.Line> rows = new ArrayList<>();
        for (int i = 0; i < JournalData.MAX_ROWS; i++) {
            rows.add(DispCodec.Line.of("wildroots.journal.species.found_at", arg, arg, arg, arg));
        }
        for (int i = 0; i < JournalData.MAX_COUNTERS; i++) {
            j.setCounter("c.k" + i, i + 1);
        }
        int n = 0;
        while (j.unlock("note.filler_" + n, List.of(arg, arg, arg, arg), rows) == JournalData.Unlock.NEW) {
            n++;
        }
        for (int i = 0; i < 300; i++) {
            j.setCounter("z.counter_with_a_rather_long_name_" + i, Long.MAX_VALUE - i);
            assertTrue(NbtLimits.journal(j.save()).ok(), "over the limit after counter " + i);
        }
        assertFalse(JournalData.load(j.save()).inert(), "saved journal loads inert");
    }

    /** A journal of filler entries a little under 64 KiB (rows of long literal arguments). */
    static JournalData nearlyFull() {
        return fill(new JournalData());
    }

    static JournalData fill(JournalData j) {
        String arg = "x".repeat(DispCodec.MAX_ARG_LENGTH);
        List<DispCodec.Line> rows = new ArrayList<>();
        for (int i = 0; i < JournalData.MAX_ROWS; i++) {
            rows.add(DispCodec.Line.of("wildroots.journal.species.found_at", arg, arg, arg, arg));
        }
        int n = 0;
        while (j.unlock("note.filler_" + n, List.of(arg, arg, arg, arg), rows) == JournalData.Unlock.NEW) {
            n++;
        }
        // a smaller entry tops it up so the last bytes are close to the limit
        int m = 0;
        while (j.unlock("note.small_" + m, List.of(arg), List.of()) == JournalData.Unlock.NEW) {
            m++;
        }
        return j;
    }

    static String randomCounterKey(java.util.Random r) {
        int len = 1 + r.nextInt(80);
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789_.";
        StringBuilder b = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            b.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return b.toString();
    }

    /**
     * T1: {@code setCounter} keeps a running upper bound of the NBT size
     * instead of serialising the whole journal on every call. Under 10 000
     * random sets (new keys, rewrites, zeroes, interleaved with toasts,
     * pins and unlocks) on a journal a few hundred bytes under the limit,
     * the saved journal never passes 64 KiB, the bound is never below the
     * real size, and it reloads live.
     */
    @Test
    void the64KiBBoundHoldsUnder10000RandomCounterSets() {
        JournalData j = nearlyFull();
        java.util.Random r = new java.util.Random(0x5EED_0001L);
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            String key = !seen.isEmpty() && r.nextInt(3) == 0 ? seen.get(r.nextInt(seen.size())) : randomCounterKey(r);
            seen.add(key);
            long value = r.nextInt(10) == 0 ? 0 : r.nextLong();
            j.setCounter(key, value);
            switch (r.nextInt(50)) {
                case 0 -> j.takeToast(i * 1000L);
                case 1 -> j.markGiven();
                case 2 -> j.apply(new net.minecraft.resources.ResourceLocation("wildroots", "note.filler_0"),
                        r.nextBoolean() ? io.github.slugblue.wildroots.network.c2s.JournalActionPacket.PIN
                                : io.github.slugblue.wildroots.network.c2s.JournalActionPacket.UNPIN);
                case 3 -> j.unlock("note.late_" + i, List.of("y".repeat(r.nextInt(DispCodec.MAX_ARG_LENGTH) + 1)), List.of());
                case 4 -> j.clearCounters(key.substring(0, 1));
                default -> { }
            }
            NbtBudget.Result size = NbtLimits.journal(j.save());
            assertTrue(size.ok(), "over 64 KiB after set " + i + ": " + size.reason());
            if (j.sizeBound() >= 0) {
                assertTrue(j.sizeBound() >= size.bytes(), "bound " + j.sizeBound() + " < real " + size.bytes() + " at " + i);
            }
        }
        assertFalse(JournalData.load(j.save()).inert(), "saved journal loads inert");
        assertTrue(j.counters().size() <= JournalData.MAX_COUNTERS);
    }

    /** T1: on a journal far from the limit, counter sets do not serialise it at all after the first measure. */
    @Test
    void counterSetsOnASmallJournalMeasureItAtMostOnce() {
        JournalData j = new JournalData();
        j.unlock("hint.welcome", List.of(), List.of());
        int before = j.fullMeasures();
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 10_000; i++) {
            j.setCounter(randomCounterKey(r), r.nextLong() | 1);
        }
        assertTrue(j.fullMeasures() - before <= 1, "full measures: " + (j.fullMeasures() - before));
        assertTrue(NbtLimits.journal(j.save()).ok());
    }

    /** T1: near the limit the bound is exact for counters, so rewrites and same-size churn do not measure either. */
    @Test
    void counterRewritesNearTheLimitDoNotMeasure() {
        JournalData j = new JournalData();
        assertTrue(j.setCounter("lp.a", 1));
        fill(j); // the last refused unlock leaves the bound unknown
        int before = j.fullMeasures();
        for (int i = 0; i < 1000; i++) {
            assertTrue(j.setCounter("lp.a", i + 2));
        }
        assertTrue(j.fullMeasures() - before <= 1, "rewrites measured " + (j.fullMeasures() - before) + " times");
        assertEquals(1001L, j.counter("lp.a"));
    }

    /** Review 1, m4: a key with the wrong tag type makes the journal inert instead of silently empty. */
    @Test
    void wrongTagTypesMakeTheJournalInert() {
        JournalData ok = new JournalData();
        ok.unlock("hint.welcome", List.of("a"), List.of(DispCodec.Line.of("wildroots.journal.ui.empty")));
        CompoundTag base = ok.save();
        List<java.util.function.Consumer<CompoundTag>> breaks = List.of(
                t -> t.putString("e", "not a list"),
                t -> t.putString("c", "not a compound"),
                t -> t.putString("given", "yes"),
                t -> t.putInt("toast", 3),
                t -> { ListTag l = new ListTag(); l.add(net.minecraft.nbt.StringTag.valueOf("x")); t.put("e", l); },
                t -> t.getList("e", 10).getCompound(0).putInt("a", 1),
                t -> { ListTag l = new ListTag(); l.add(net.minecraft.nbt.IntTag.valueOf(1));
                    t.getList("e", 10).getCompound(0).put("a", l); },
                t -> t.getList("e", 10).getCompound(0).putString("r", "row"),
                t -> { ListTag l = new ListTag(); l.add(net.minecraft.nbt.StringTag.valueOf("not a row key"));
                    t.getList("e", 10).getCompound(0).put("r", l); },
                t -> t.getList("e", 10).getCompound(0).putString("o", "first"),
                t -> t.getList("e", 10).getCompound(0).putInt("id", 5));
        for (int i = 0; i < breaks.size(); i++) {
            CompoundTag t = base.copy();
            breaks.get(i).accept(t);
            JournalData d = JournalData.load(t);
            assertTrue(d.inert(), "break " + i + " loaded live: " + t);
            assertEquals(t, d.save(), "break " + i + ": raw tag not kept");
        }
        assertFalse(JournalData.load(base).inert());
    }
}
