package io.github.slugblue.wildroots.item;

import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

/**
 * Fathers of a seed lot in NBT (ADR-0007 rev. 2; docs/05-architecture.md
 * §6.1; {@code 09-security.md} SEC-09; decision of review 1 of batch 2):
 * {@code f: [{a: [L; …], w: 2.0f}, …]}.
 *
 * <ul>
 *   <li>{@code a} — the packed alleles of the father;</li>
 *   <li>{@code lh} — written only when it differs from the mother's
 *       {@code lh}; a missing {@code lh} means "the mother's" (species and
 *       {@code v} come from the lot, S10);</li>
 *   <li>{@code w} — the father's weight among the lot fathers
 *       ({@code FatherPick.weight}: number of picks, bee donor 2.0): meiosis
 *       chooses the father of every seed by it, so it cannot be dropped;</li>
 *   <li>{@code d: 1b} — only for pollen donors (bee, brush).</li>
 * </ul>
 * The decoder is type-safe and never throws: a broken list gives
 * {@code Optional.empty()}, and the lot is inert (SEC-09, SEC-10).
 */
public final class LotFathersNbt {

    /** One father as stored in the lot. */
    public record Father(FatherRecord genome, float weight, boolean donor) {
    }

    private LotFathersNbt() {
    }

    public static ListTag encode(List<Father> fathers, long motherLh) {
        ListTag out = new ListTag();
        for (Father f : fathers) {
            CompoundTag c = new CompoundTag();
            if (f.genome().lh() != motherLh) {
                c.putLong("lh", f.genome().lh());
            }
            c.put("a", new LongArrayTag(f.genome().a()));
            c.putFloat("w", f.weight());
            if (f.donor()) {
                c.putByte("d", (byte) 1);
            }
            out.add(c);
        }
        return out;
    }

    /** Fathers of {@code tag}; empty unless ≤ 4 fathers and ≤ 4 donors with finite weights in [0, 16], not all 0. */
    public static Optional<List<Father>> decode(Tag tag, long motherLh) {
        if (!(tag instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                || list.size() > NbtLimits.LOT_MAX_FATHERS + NbtLimits.BLOCK_ENTITY_MAX_DONORS) {
            return Optional.empty();
        }
        List<Father> out = new ArrayList<>(list.size());
        boolean anyWeight = list.isEmpty();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (!c.contains("a", Tag.TAG_LONG_ARRAY) || !c.contains("w", Tag.TAG_FLOAT)
                    || (c.contains("lh") && !c.contains("lh", Tag.TAG_LONG))) {
                return Optional.empty();
            }
            long[] a = c.getLongArray("a");
            float w = c.getFloat("w");
            if (a.length == 0 || a.length > NbtLimits.GENOME_MAX_LONGS || !Float.isFinite(w) || w < 0
                    || w > NbtLimits.LOT_MAX_FATHER_WEIGHT) {
                return Optional.empty();
            }
            anyWeight |= w > 0;
            long lh = c.contains("lh", Tag.TAG_LONG) ? c.getLong("lh") : motherLh;
            out.add(new Father(new FatherRecord(lh, a), w, c.getByte("d") == 1));
        }
        long donors = out.stream().filter(Father::donor).count();
        if (donors > NbtLimits.BLOCK_ENTITY_MAX_DONORS || out.size() - donors > NbtLimits.LOT_MAX_FATHERS) {
            return Optional.empty();
        }
        return anyWeight ? Optional.of(out) : Optional.empty();
    }
}
