package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import org.junit.jupiter.api.Test;

/**
 * WR-122: the compact lot of typical make-up fits the 512 B target, and a
 * pouch of 16 lots at the 1 KiB limit fits 17 KiB (measured as in S10
 * {@code S10Lots}, with fathers stored as {@code {lh, a, w}}).
 */
class LotSizeTest {

    static final String SPECIES = "wildroots:wild_carrot";

    static CompoundTag genome(long lh, long seed) {
        CompoundTag g = new CompoundTag();
        g.putInt("v", 1);
        g.putString("sp", SPECIES);
        g.putLong("lh", lh);
        g.put("a", new LongArrayTag(new long[] {seed, seed * 31, seed * 977}));
        return g;
    }

    /** Lot: mother, {@code fathers} fathers {@code {lh, a, w}}, the given {@code disp}. */
    static CompoundTag lot(int fathers, List<DispCodec.Line> disp, long seed) {
        return lot(fathers, disp, seed, false);
    }

    /**
     * Lot of the S10 shape. With {@code fatherLhImplied} a father's {@code lh}
     * is left out because it equals the mother's (proposal for WR-135).
     */
    static CompoundTag lot(int fathers, List<DispCodec.Line> disp, long seed, boolean fatherLhImplied) {
        long lh = 0x9E3779B97F4A7C15L ^ seed;
        CompoundTag lot = new CompoundTag();
        lot.putInt("v", 1);
        lot.putLong("id", seed * 0x2545F4914F6CDD1DL);
        lot.putString("var", "wildroots:eastern_carrot");
        lot.put("g", genome(lh, seed));
        ListTag f = new ListTag();
        for (int i = 0; i < fathers; i++) {
            CompoundTag father = new CompoundTag();
            if (!fatherLhImplied) {
                father.putLong("lh", lh);
            }
            father.put("a", new LongArrayTag(new long[] {seed + i, seed ^ i, seed - i}));
            father.putFloat("w", 1.0f + i);
            f.add(father);
        }
        lot.put("f", f);
        lot.putFloat("po", 0.35f);
        lot.putShort("gen", (short) 12);
        lot.putString("ln", "Golden Line");
        lot.putByte("fl", (byte) 5);
        lot.put("disp", DispCodec.encode(disp));
        return lot;
    }

    static List<DispCodec.Line> typicalDisp() {
        List<DispCodec.Line> d = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            d.add(DispCodec.Line.of("wildroots.tooltip.lot.line_" + i, Integer.toString(i * 7 % 13)));
        }
        return d;
    }

    /**
     * Typical lot exactly as WR-122 lists it (mother {@code {v, sp, lh, a}}, 4
     * fathers {@code {lh, a, w}}, 6 {@code disp} lines in the compact
     * {@link DispCodec} form): 553 B. It is within the hard limit, but over
     * the 512 B target by 41 B; the open question goes to WR-135.
     */
    @Test
    void typicalLotOfTheTaskShapeIsWithinTheHardLimit() {
        NbtBudget.Result r = NbtLimits.lot(lot(4, typicalDisp(), 7));
        assertTrue(r.ok(), r::toString);
        assertEquals(553, r.bytes(), "size of the S10-shaped lot changed");
        assertTrue(r.depth() <= NbtLimits.LOT_MAX_DEPTH, () -> "depth " + r.depth());
    }

    /** The same lot reaches the 512 B target once a father's {@code lh} defaults to the mother's. */
    @Test
    void typicalLotWithImpliedFatherLayoutIsAtMost512Bytes() {
        NbtBudget.Result r = NbtLimits.lot(lot(4, typicalDisp(), 7, true));
        assertTrue(r.ok(), r::toString);
        assertTrue(r.bytes() <= NbtLimits.LOT_TARGET_BYTES, () -> "typical lot is " + r.bytes() + " B");
    }

    /** A lot whose {@code disp} is padded until the next line would pass 1 KiB. */
    static CompoundTag lotAtLimit(long seed) {
        List<DispCodec.Line> disp = new ArrayList<>(typicalDisp());
        CompoundTag lot = lot(4, disp, seed);
        for (int i = 0; disp.size() < DispCodec.MAX_LINES; i++) {
            disp.add(DispCodec.Line.of("wildroots.tooltip.lot.extra_" + i, "x".repeat(20), "y".repeat(20)));
            CompoundTag next = lot(4, disp, seed);
            if (!NbtLimits.lot(next).ok()) {
                break;
            }
            lot = next;
        }
        return lot;
    }

    @Test
    void pouchOfSixteenLotsAtTheLimitFitsSeventeenKiB() {
        ListTag entries = new ListTag();
        for (int i = 0; i < NbtLimits.POUCH_MAX_ENTRIES; i++) {
            CompoundTag lot = lotAtLimit(i + 1);
            NbtBudget.Result lr = NbtLimits.lot(lot);
            assertTrue(lr.ok() && lr.bytes() > NbtLimits.LOT_MAX_BYTES - 60, () -> "lot near 1 KiB: " + lr);
            CompoundTag e = new CompoundTag();
            e.put("l", lot);
            e.putByte("c", (byte) 1);
            entries.add(e);
        }
        CompoundTag pouch = new CompoundTag();
        pouch.putInt("v", 1);
        pouch.put("e", entries);
        NbtBudget.Result r = NbtLimits.pouch(pouch);
        assertTrue(r.ok(), r::toString);
        assertTrue(r.bytes() <= NbtLimits.POUCH_MAX_BYTES, () -> "pouch " + r.bytes() + " B");
        assertTrue(r.depth() <= NbtLimits.POUCH_MAX_DEPTH, () -> "pouch depth " + r.depth());
        assertEquals(16, entries.size());
    }
}
