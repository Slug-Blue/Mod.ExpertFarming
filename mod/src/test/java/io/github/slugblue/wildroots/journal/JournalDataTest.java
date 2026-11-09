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
        assertEquals(JournalData.Unlock.NEW, j.unlock("hint.welcome", List.of("@species.wild_carrot"), List.of()));
        assertEquals(JournalData.Unlock.NONE, j.unlock("hint.welcome", List.of("@species.wild_carrot"), List.of()));
        assertEquals(JournalData.Unlock.NEW, j.unlock("species.wild_carrot", List.of(), List.of()));
        j.apply(rl("species.wild_carrot"), JournalActionPacket.PIN);
        DispCodec.Line row = DispCodec.Line.of("wildroots.journal.species.found_at", "@biome.minecraft.plains", "10", "-4");
        assertEquals(JournalData.Unlock.UPDATED, j.unlock("species.wild_carrot", List.of(), List.of(row)));
        JournalData.Entry e = j.entry("species.wild_carrot").orElseThrow();
        assertEquals(1, e.order());
        assertTrue(e.is(JournalData.PINNED));
        assertEquals(List.of(row), e.rows());
    }

    @Test
    void lockedSilhouetteIsReplacedByTheRealEntryButNeverTheOtherWay() {
        JournalData j = new JournalData();
        assertTrue(j.lock("cultivar.eastern_carrot", List.of("@journal.cultivar.eastern_carrot.goal")));
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
        j.unlock("hint.welcome", List.of("@species.wild_carrot"), List.of());
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
}
