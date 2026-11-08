package io.github.slugblue.wildroots.pollination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import org.junit.jupiter.api.Test;

/**
 * WR-142 boundary pass: the bee pollen capability NBT ({@code {sp, src, lh,
 * a, v, age}}) from a crafted or future save. The decoder never throws;
 * anything malformed gives empty pollen (SEC-09), and nothing malformed can
 * make stale pollen fresh again.
 */
class BeePollenNbtBoundaryTest {

    static CompoundTag good() {
        BeePollen p = new BeePollen();
        p.collect("wildroots:wild_carrot", 42L, new FatherRecord(7L, new long[] {1, 2}));
        p.setCounters(1, 77);
        return p.save();
    }

    static BeePollen load(CompoundTag t) {
        BeePollen p = new BeePollen();
        p.load(t);
        return p;
    }

    @Test
    void genomeAtTheLimitLoadsOverItDoesNot() {
        CompoundTag t = good();
        t.put("a", new LongArrayTag(new long[NbtLimits.GENOME_MAX_LONGS]));
        assertTrue(load(t).hasPollen(), "exactly the limit");
        t.put("a", new LongArrayTag(new long[NbtLimits.GENOME_MAX_LONGS + 1]));
        assertFalse(load(t).hasPollen(), "one over the limit");
        t.put("a", new LongArrayTag(new long[0]));
        assertFalse(load(t).hasPollen(), "empty genome");
    }

    @Test
    void oversizeOrBadSpeciesIsEmpty() {
        CompoundTag t = good();
        t.putString("sp", "wildroots:" + "x".repeat(NbtLimits.GENOME_MAX_SPECIES_LENGTH));
        assertFalse(load(t).hasPollen(), "species over the length limit");
        t.putString("sp", "");
        assertFalse(load(t).hasPollen(), "empty species");
        t.putInt("sp", 3);
        assertFalse(load(t).hasPollen(), "species of the wrong type");
    }

    @Test
    void wrongTypedOrMissingCountersAreEmpty() {
        for (String key : new String[] {"v", "age"}) {
            CompoundTag t = good();
            t.putString(key, "1");
            assertFalse(load(t).hasPollen(), key + " as a string");
            t = good();
            t.remove(key);
            assertFalse(load(t).hasPollen(), key + " missing");
            t = good();
            t.put(key, new LongArrayTag(new long[] {1}));
            assertFalse(load(t).hasPollen(), key + " as an array");
        }
        CompoundTag lh = good();
        lh.putString("lh", "7");
        assertFalse(load(lh).hasPollen(), "lh as a string");
    }

    /** A negative counter is malformed: it must not turn stale pollen fresh by clamping to 0. */
    @Test
    void negativeCountersAreEmpty() {
        CompoundTag t = good();
        t.putInt("age", -5_000);
        assertFalse(load(t).fresh(), "negative age");
        assertFalse(load(t).hasPollen(), "negative age is malformed");
        t = good();
        t.putInt("v", -1);
        assertFalse(load(t).hasPollen(), "negative visits is malformed");
    }

    /** Counters from a future save with larger numbers load as they are: stale, never fresh. */
    @Test
    void hugeCountersLoadStale() {
        CompoundTag t = good();
        t.putInt("v", Integer.MAX_VALUE);
        BeePollen p = load(t);
        assertTrue(p.hasPollen() && !p.fresh(), "visits MAX_VALUE");
        p.countVisit();
        assertEquals(Integer.MAX_VALUE, p.visits(), "no overflow");
        t = good();
        t.putInt("age", Integer.MAX_VALUE);
        p = load(t);
        assertTrue(p.hasPollen() && !p.fresh(), "age MAX_VALUE");
        p.tickOutside();
        assertEquals(Integer.MAX_VALUE, p.ageOutside(), "no overflow");
        t = good();
        t.putInt("v", BeePollen.MAX_VISITS);
        assertFalse(load(t).fresh(), "2 visits saved is stale");
        t.putInt("v", BeePollen.MAX_VISITS - 1);
        t.putInt("age", BeePollen.MAX_AGE_OUTSIDE + 1);
        assertFalse(load(t).fresh(), "1201 ticks saved is stale");
    }

    /** Numeric types other than int (a hand-edited save) are read as numbers. */
    @Test
    void otherNumericTypesAreRead() {
        CompoundTag t = good();
        t.putShort("v", (short) 1);
        t.putByte("age", (byte) 10);
        BeePollen p = load(t);
        assertTrue(p.hasPollen() && p.visits() == 1 && p.ageOutside() == 10, "short / byte counters");
    }

    @Test
    void extraUnknownKeysAreIgnored() {
        CompoundTag t = good();
        t.putInt("format", 99);
        t.putString("future", "x");
        BeePollen p = load(t);
        assertTrue(p.hasPollen() && p.visits() == 1 && p.ageOutside() == 77);
    }

    @Test
    void nullAndEmptyLoadAsEmpty() {
        assertFalse(load(null).hasPollen());
        assertFalse(load(new CompoundTag()).hasPollen());
        assertTrue(new BeePollen().save().isEmpty(), "empty pollen saves nothing");
    }
}
