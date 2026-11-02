package io.github.slugblue.wildroots.core.safety;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Hard NBT limits of every stored root (SEC-09; {@code 09-security.md}
 * §1.6.2; docs/05-architecture.md §6.1). The numbers change only through
 * that document. A root over its limit makes the object inert: it is not
 * planted, not accepted by machines, the pouch or trades, and its raw tag is
 * kept but never copied (§6.1).
 *
 * <p>Size and depth are measured by {@link NbtBudget} on the mod's own root
 * compound (the value stored under {@code wildroots:lot},
 * {@code wildroots:pouch}, the block entity tag …), not on the whole
 * {@code ItemStack} tag, so vanilla keys such as an anvil name do not eat
 * into our budget. Depth counts compound and list tags with that root at 1.
 */
public final class NbtLimits {

    private NbtLimits() {
    }

    /** Seed lot: hard limit, target and depth (compact lot, ADR-0007 rev. 2, S10). */
    public static final int LOT_MAX_BYTES = 1024;
    public static final int LOT_TARGET_BYTES = 512;
    public static final int LOT_MAX_DEPTH = 5;
    public static final int LOT_MAX_FATHERS = 4;
    public static final int LOT_MAX_GENERATION = 65_535;
    public static final float LOT_MAX_FATHER_WEIGHT = 16f;

    /** Seed pouch: 16 entries of one seed each (00-vision.md item 21), 17 KiB, depth 7. */
    public static final int POUCH_MAX_BYTES = 17 * 1024;
    public static final int POUCH_MAX_DEPTH = 7;
    public static final int POUCH_MAX_ENTRIES = 16;

    /** Mother root, clone, scion: genome and passport. */
    public static final int MOTHER_MAX_BYTES = 512;
    public static final int MOTHER_MAX_DEPTH = 4;

    /** {@code PlantBlockEntity} / {@code FruitBlockEntity}: pending lot of ≤ 4 fathers and ≤ 4 donors. */
    public static final int BLOCK_ENTITY_MAX_BYTES = 2048;
    public static final int BLOCK_ENTITY_MAX_DEPTH = 6;
    public static final int BLOCK_ENTITY_MAX_NOISE = 48;
    public static final int BLOCK_ENTITY_MAX_DONORS = 4;

    /** Journal capability. */
    public static final int JOURNAL_MAX_BYTES = 64 * 1024;
    public static final int JOURNAL_MAX_DEPTH = 4;

    /** Genome {@code g}: at most 8 longs (512 bits), species id ≤ 128 characters. */
    public static final int GENOME_MAX_LONGS = 8;
    public static final int GENOME_MAX_SPECIES_LENGTH = 128;

    public static NbtBudget.Result lot(Tag lot) {
        return NbtBudget.measure(lot, LOT_MAX_BYTES, LOT_MAX_DEPTH);
    }

    /** Pouch budget plus the entry count of its {@code e} list, both before the entries are decoded. */
    public static NbtBudget.Result pouch(Tag pouch) {
        NbtBudget.Result r = NbtBudget.measure(pouch, POUCH_MAX_BYTES, POUCH_MAX_DEPTH);
        if (r.ok() && pouch instanceof CompoundTag c && c.contains("e", Tag.TAG_LIST)) {
            int n = c.getList("e", Tag.TAG_COMPOUND).size();
            if (n > POUCH_MAX_ENTRIES) {
                return new NbtBudget.Result(false, r.bytes(), r.depth(), "entries " + n + " > " + POUCH_MAX_ENTRIES);
            }
        }
        return r;
    }

    public static NbtBudget.Result mother(Tag mother) {
        return NbtBudget.measure(mother, MOTHER_MAX_BYTES, MOTHER_MAX_DEPTH);
    }

    public static NbtBudget.Result blockEntity(Tag be) {
        return NbtBudget.measure(be, BLOCK_ENTITY_MAX_BYTES, BLOCK_ENTITY_MAX_DEPTH);
    }

    public static NbtBudget.Result journal(Tag journal) {
        return NbtBudget.measure(journal, JOURNAL_MAX_BYTES, JOURNAL_MAX_DEPTH);
    }
}
