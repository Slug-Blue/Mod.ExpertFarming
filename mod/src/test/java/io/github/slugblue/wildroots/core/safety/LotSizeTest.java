package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import io.github.slugblue.wildroots.item.LotFathersNbt;
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

    /** Lot: mother, {@code fathers} fathers of the mother's layout (format of {@link LotFathersNbt}), the given {@code disp}. */
    static CompoundTag lot(int fathers, List<DispCodec.Line> disp, long seed) {
        long lh = 0x9E3779B97F4A7C15L ^ seed;
        CompoundTag lot = new CompoundTag();
        lot.putInt("v", 1);
        lot.putLong("id", seed * 0x2545F4914F6CDD1DL);
        lot.putString("var", "wildroots:eastern_carrot");
        lot.put("g", genome(lh, seed));
        List<LotFathersNbt.Father> f = new ArrayList<>();
        for (int i = 0; i < fathers; i++) {
            f.add(new LotFathersNbt.Father(new FatherRecord(lh, new long[] {seed + i, seed ^ i, seed - i}), 1.0f + i, false));
        }
        lot.put("f", LotFathersNbt.encode(f, lh));
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
     * WR-122 criterion: a typical lot (mother {@code {v, sp, lh, a}}, 4 fathers
     * {@code {lh, a}} of the mother's layout, 6 {@code disp} lines) is at most
     * 512 B. Decision of review 1: a father's {@code lh} is written only when
     * it differs from the mother's ({@link LotFathersNbt}); measured 501 B.
     */
    @Test
    void typicalLotIsAtMost512Bytes() {
        NbtBudget.Result r = NbtLimits.lot(lot(4, typicalDisp(), 7));
        assertTrue(r.ok(), r::toString);
        assertTrue(r.bytes() <= NbtLimits.LOT_TARGET_BYTES, () -> "typical lot is " + r.bytes() + " B");
        assertTrue(r.depth() <= NbtLimits.LOT_MAX_DEPTH, () -> "depth " + r.depth());
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
