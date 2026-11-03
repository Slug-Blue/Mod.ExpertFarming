package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.NbtBudget;
import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/** WR-135: lot NBT (docs/05-architecture.md §6.1; SEC-07, SEC-09, SEC-13). */
class LotNbtTest {

    static final String SP = "wildroots:wild_carrot";
    static final long LH = 0x7E57_1A70_0000_0001L;

    static GenomeRecord genome(long seed) {
        return new GenomeRecord(1, SP, LH, new long[] {seed * 0x9E3779B97F4A7C15L, seed * 31, seed * 977});
    }

    static List<LotFathersNbt.Father> fathers(int n) {
        List<LotFathersNbt.Father> f = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            f.add(new LotFathersNbt.Father(new FatherRecord(LH, new long[] {i * 0x5DEECE66DL, ~i, i * 7L}), 1 + i, false));
        }
        return f;
    }

    /** A typical lot as harvest makes it: 4 fathers, line name, generation, from the wild, cultivar id. */
    static CompoundTag typical() {
        LotStacks.Parent mother = new LotStacks.Parent(SP, Optional.of("wildroots:eastern_carrot"), genome(3),
                Optional.of("Golden Line"), 11, LotFlags.FROM_WILD);
        return LotStacks.seedsTag(mother, fathers(4), 0.35f, LotFlags.BEES, 0x1234_5678_9ABCL, LotStacks.RareClaim.NONE);
    }

    @Test
    void typicalLotWithFourFathersIsAtMost512Bytes() {
        NbtBudget.Result r = NbtLimits.lot(typical());
        assertTrue(r.ok() && r.bytes() <= NbtLimits.LOT_TARGET_BYTES, r::toString);
        assertTrue(r.depth() <= NbtLimits.LOT_MAX_DEPTH);
    }

    @Test
    void roundTrip() {
        LotNbt.Lot lot = assertInstanceOf(LotNbt.Lot.class, LotNbt.read(typical()));
        assertEquals(0x1234_5678_9ABCL, lot.lotId());
        assertEquals(12, lot.generation());
        assertEquals(Optional.of("Golden Line"), lot.lineName());
        assertEquals(4, lot.fathers().size());
        assertEquals(0.35f, lot.pOut());
        assertEquals(LotFlags.FROM_WILD | LotFlags.BEES, lot.flags());
        assertEquals(Optional.of("wildroots:eastern_carrot"), lot.cultivar());
        assertTrue(LotNbt.disp(typical()).size() <= 6, "unexamined lot has at most 6 disp lines");
    }

    @Test
    void lazyLotCarriesItsIdFromCreation() {
        CompoundTag t = LotStacks.lazyTag(SP, "meadow", 99L, 77L, LotNbt.RareSource.CHEST);
        LotNbt.Lazy lazy = assertInstanceOf(LotNbt.Lazy.class, LotNbt.read(t));
        assertEquals(77L, lazy.lotId());
        assertEquals(LotNbt.RareSource.CHEST, lazy.rare());
        LotNbt.Lot real = assertInstanceOf(LotNbt.Lot.class,
                LotNbt.read(LotStacks.realisedTag(lazy, genome(5), fathers(2), 0.6f)));
        assertEquals(77L, real.lotId(), "realisation keeps the lotId");
        assertEquals(LotNbt.RareSource.CHEST, real.rare());
        assertEquals(LotFlags.FROM_WILD, real.flags());
    }

    @Test
    void damagedOversizedAndFutureLotsAreInert() {
        CompoundTag future = typical();
        future.putInt("v", LotNbt.CURRENT + 1);
        assertInstanceOf(LotNbt.Inert.class, LotNbt.read(future));
        CompoundTag big = typical();
        big.putString("pad", "x".repeat(700));
        assertInstanceOf(LotNbt.Inert.class, LotNbt.read(big));
        CompoundTag po = typical();
        po.putFloat("po", 1.5f);
        assertInstanceOf(LotNbt.Inert.class, LotNbt.read(po));
        CompoundTag nine = typical();
        ListTag f = LotFathersNbt.encode(fathers(4), LH);
        for (int i = 0; i < 5; i++) {
            CompoundTag d = f.getCompound(0).copy();
            d.putByte("d", (byte) 1);
            f.add(d);
        }
        nine.put("f", f);
        assertInstanceOf(LotNbt.Inert.class, LotNbt.read(nine));
        CompoundTag sp = typical();
        sp.getCompound("g").putString("sp", "Bad Id!");
        assertInstanceOf(LotNbt.Inert.class, LotNbt.read(sp));
        assertInstanceOf(LotNbt.Inert.class, LotNbt.read(null));
        CompoundTag rs = typical();
        rs.putByte("rs", (byte) 9);
        assertInstanceOf(LotNbt.Inert.class, LotNbt.read(rs));
    }
}
