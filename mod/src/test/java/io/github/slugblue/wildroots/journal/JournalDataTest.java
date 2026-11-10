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
