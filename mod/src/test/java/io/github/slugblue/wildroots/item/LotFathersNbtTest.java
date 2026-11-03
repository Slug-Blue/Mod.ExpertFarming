package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/** Review 1 of batch 2: a father's lh is written only if it differs from the mother's; missing lh = mother's. */
class LotFathersNbtTest {

    static final long MOTHER = 0x1234L;
    static final long OTHER = 0x5678L;

    @Test
    void lhOfTheMotherIsImpliedAndOtherLayoutsAreWritten() {
        List<LotFathersNbt.Father> in = List.of(
                new LotFathersNbt.Father(new FatherRecord(MOTHER, new long[] {1, 2, 3}), 2f, false),
                new LotFathersNbt.Father(new FatherRecord(OTHER, new long[] {4, 5, 6}), 1f, false),
                new LotFathersNbt.Father(new FatherRecord(MOTHER, new long[] {7, 8, 9}), 2f, true));
        ListTag tag = LotFathersNbt.encode(in, MOTHER);
        assertFalse(tag.getCompound(0).contains("lh"));
        assertEquals(OTHER, tag.getCompound(1).getLong("lh"));
        assertFalse(tag.getCompound(0).contains("d"));
        assertEquals(1, tag.getCompound(2).getByte("d"));
        assertEquals(in, LotFathersNbt.decode(tag, MOTHER).orElseThrow());
    }

    static CompoundTag father(float w) {
        CompoundTag c = new CompoundTag();
        c.put("a", new LongArrayTag(new long[] {1}));
        c.putFloat("w", w);
        return c;
    }

    @Test
    void brokenListsAreRejectedWithoutExceptions() {
        assertTrue(LotFathersNbt.decode(StringTag.valueOf("x"), MOTHER).isEmpty());
        ListTag five = new ListTag();
        for (int i = 0; i < 5; i++) {
            five.add(father(1));
        }
        assertTrue(LotFathersNbt.decode(five, MOTHER).isEmpty(), "five fathers");
        ListTag zero = new ListTag();
        zero.add(father(0));
        assertTrue(LotFathersNbt.decode(zero, MOTHER).isEmpty(), "all weights 0");
        ListTag nan = new ListTag();
        nan.add(father(Float.NaN));
        assertTrue(LotFathersNbt.decode(nan, MOTHER).isEmpty(), "NaN weight");
        ListTag big = new ListTag();
        big.add(father(17));
        assertTrue(LotFathersNbt.decode(big, MOTHER).isEmpty(), "weight over 16");
        ListTag badLh = new ListTag();
        CompoundTag c = father(1);
        c.putString("lh", "x");
        badLh.add(c);
        assertTrue(LotFathersNbt.decode(badLh, MOTHER).isEmpty(), "lh of a wrong type");
        ListTag longGenome = new ListTag();
        CompoundTag g = father(1);
        g.put("a", new LongArrayTag(new long[9]));
        longGenome.add(g);
        assertTrue(LotFathersNbt.decode(longGenome, MOTHER).isEmpty(), "more than 8 longs");
        List<LotFathersNbt.Father> fourAndFour = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fourAndFour.add(new LotFathersNbt.Father(new FatherRecord(MOTHER, new long[] {i}), 1f, false));
            fourAndFour.add(new LotFathersNbt.Father(new FatherRecord(MOTHER, new long[] {i}), 2f, true));
        }
        assertEquals(8, LotFathersNbt.decode(LotFathersNbt.encode(fourAndFour, MOTHER), MOTHER).orElseThrow().size());
        assertTrue(LotFathersNbt.decode(new ListTag(), MOTHER).orElseThrow().isEmpty(), "self-pollinated lot");
    }
}
