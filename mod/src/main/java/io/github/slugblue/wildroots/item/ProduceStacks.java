package io.github.slugblue.wildroots.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The only writer of harvest NBT (docs/05-architecture.md §6.3; ADR-0007;
 * GDD §6.4): {@value #GRADE} (byte 1–4) and {@value #BITTER} (byte, only on
 * a bitter item, never above grade 2). Nothing else ever goes into a harvest
 * item, so there are at most {1, 2, 3, 4} ∪ {1b, 2b} = 6 stack variants.
 */
public final class ProduceStacks {

    public static final String GRADE = "wildroots:grade";
    public static final String BITTER = "wildroots:bitter";
    /** Grade without the field (processed products of vanilla ids, §6.3 item 4). */
    public static final int DEFAULT_GRADE = 3;
    /** Bitter harvest is never above this grade (GDD §6.4). */
    public static final int BITTER_MAX_GRADE = 2;
    /** Food multiplier of grades 1–4 (GDD §6.4). */
    private static final double[] MULT = {0, 0.4, 0.7, 1.0, 1.2};

    private ProduceStacks() {
    }

    /** Normalised grade: clamped to 1–4, bitter at most 2. */
    public static int normalise(int grade, boolean bitter) {
        int g = Math.max(1, Math.min(4, grade));
        return bitter ? Math.min(g, BITTER_MAX_GRADE) : g;
    }

    /** The whole tag of a harvest item; {@code bitter = false} is not written. */
    public static CompoundTag tag(int grade, boolean bitter) {
        CompoundTag t = new CompoundTag();
        t.putByte(GRADE, (byte) normalise(grade, bitter));
        if (bitter) {
            t.putByte(BITTER, (byte) 1);
        }
        return t;
    }

    public static ItemStack create(Item item, int grade, boolean bitter, int count) {
        ItemStack s = new ItemStack(item, count);
        s.setTag(tag(grade, bitter));
        return s;
    }

    public static ItemStack create(Item item, int grade, boolean bitter) {
        return create(item, grade, bitter, 1);
    }

    /** Grade of a stack; {@value #DEFAULT_GRADE} without the field. */
    public static int grade(CompoundTag tag) {
        if (tag == null || !tag.contains(GRADE, Tag.TAG_BYTE)) {
            return DEFAULT_GRADE;
        }
        return normalise(tag.getByte(GRADE), bitter(tag));
    }

    public static boolean bitter(CompoundTag tag) {
        return tag != null && tag.getByte(BITTER) == 1;
    }

    /** Multiplier of nutrition and saturation (GDD §6.4: 0.4 / 0.7 / 1.0 / 1.2). */
    public static double multiplier(int grade) {
        return MULT[Math.max(1, Math.min(4, grade))];
    }

    /** {@code max(1, round(n_ref · m))} (GDD §6.4). */
    public static int nutrition(int nRef, int grade) {
        return Math.max(1, (int) Math.round(nRef * multiplier(grade)));
    }

    /** {@code s_ref · m}, × 0.5 when bitter (GDD §6.4, §3.6). */
    public static float saturation(double sRef, int grade, boolean bitter) {
        return (float) (sRef * multiplier(grade) * (bitter ? 0.5 : 1));
    }
}
