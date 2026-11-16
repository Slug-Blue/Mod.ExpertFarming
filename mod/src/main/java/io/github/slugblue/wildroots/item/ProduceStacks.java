package io.github.slugblue.wildroots.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The only writer of harvest NBT (docs/05-architecture.md §6.3; ADR-0007;
 * GDD §6.4): {@value #GRADE} (byte 1–4) and {@value #BITTER} (byte, only on
 * a bitter item, never above grade 2). Nothing else ever goes into a harvest
 * item, so there are at most {1, 2, 3, 4} ∪ {1b, 2b} = 6 stack variants. An item with
 * root colours also carries {@value #COLOUR}, but never {@value #BITTER}: 4 grades × 4
 * colours = 16 variants (ADR-0007 revision 6).
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

    /**
     * A harvest stack of {@code count} items. An item with root colours ({@link ProduceItem#rootColours}) gets no
     * {@code bitter} key whatever {@code bitter} says (ADR-0007): it gets the default colour of the item.
     */
    public static ItemStack create(Item item, int grade, boolean bitter, int count) {
        return create(item, grade, bitter, item instanceof ProduceItem p ? p.defaultColour() : YELLOW, count);
    }

    /**
     * Root colour of colour-variant produce (WR-136a, WR-702, WR-706; {@code eastern_carrot} and {@code carrot}):
     * four colours, each its own stack: {@value #YELLOW}, {@value #PURPLE}, {@value #RED}, {@value #ORANGE}.
     * {@code wildroots:colour} (a byte with that number) is written only when the colour is not the item's
     * default ({@link ProduceItem#defaultColour}: yellow for the eastern carrot, orange for the carrot), so a stack
     * saved before WR-706 (no key, or 1b/2b) keeps its meaning; an old orange eastern carrot, which was merged
     * with yellow then, reads as yellow. White is a colour of the wild plant only and the wild root has no
     * colour at all. Other produce never carries the key.
     */
    public static final String COLOUR = "wildroots:colour";
    public static final int YELLOW = 0;
    public static final int PURPLE = 1;
    public static final int RED = 2;
    public static final int ORANGE = 3;
    /** Number of root colours of a colour item. */
    public static final int COLOURS = 4;

    /** The tag of an eastern-carrot-like harvest item (default colour yellow); see {@link #tag(int, boolean, int, int)}. */
    public static CompoundTag tag(int grade, boolean bitter, int colour) {
        return tag(grade, bitter, colour, YELLOW);
    }

    /**
     * The tag of a colour-variant harvest item: {@code bitter} is <b>never written</b> (a bitter harvest only
     * keeps its grade cap), so 4 grades × 4 colours = 16 variants; a colour number outside 0-3 is the default.
     */
    public static CompoundTag tag(int grade, boolean bitter, int colour, int defaultColour) {
        CompoundTag t = tag(normalise(grade, bitter), false);
        if (colour >= 0 && colour < COLOURS && colour != defaultColour) {
            t.putByte(COLOUR, (byte) colour);
        }
        return t;
    }

    /** A harvest stack; {@code colour} is written only for an item with root colours ({@link ProduceItem#rootColours}). */
    public static ItemStack create(Item item, int grade, boolean bitter, int colour, int count) {
        ItemStack s = new ItemStack(item, count);
        s.setTag(item instanceof ProduceItem p && p.rootColours() ? tag(grade, bitter, colour, p.defaultColour())
                : tag(grade, bitter));
        return s;
    }

    /** Root colour of a stack of an item whose default is yellow. */
    public static int colour(CompoundTag tag) {
        return colour(tag, YELLOW);
    }

    /**
     * Root colour of a stack: the key's value 0-3, anything else (no key, a string, a forged number) is
     * {@code defaultColour}. A numeric tag of another width reads as its byte value.
     */
    public static int colour(CompoundTag tag, int defaultColour) {
        if (tag == null || !tag.contains(COLOUR, Tag.TAG_ANY_NUMERIC)) {
            return defaultColour;
        }
        byte c = tag.getByte(COLOUR);
        return c >= 0 && c < COLOURS ? c : defaultColour;
    }

    /**
     * Root colour from the plant's {@code variant} (the block state the model draws,
     * {@code GrowthLogic.variant}: 1 yellow, 2 orange, 3 purple, 4 red); a white root (0) cannot be a colour
     * item's colour and gives {@code defaultColour}.
     */
    public static int colourOfVariant(int variant, int defaultColour) {
        return switch (variant) {
            case 1 -> YELLOW;
            case 2 -> ORANGE;
            case 3 -> PURPLE;
            case 4 -> RED;
            default -> defaultColour;
        };
    }

    public static int colourOfVariant(int variant) {
        return colourOfVariant(variant, YELLOW);
    }

    /** Name of a colour for lang keys and the colour ids of the species JSON: yellow, orange, purple or red. */
    public static String colourName(int colour) {
        return switch (colour) {
            case PURPLE -> "purple";
            case RED -> "red";
            case ORANGE -> "orange";
            default -> "yellow";
        };
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

    /**
     * Eating time by grade (owner decision D3, 2026-11-11; GDD §6.4): the
     * vanilla 32 ticks × 1.5 / 1.25 / 1.0 / 0.9 for grades 1–4, so coarse
     * produce takes longest. Bitterness does not change it. Index = grade.
     */
    static final int[] EAT_TICKS = {0, 48, 40, 32, 29};

    /** Ticks to eat produce or a mother root of {@code grade} (clamped to 1–4). */
    public static int eatTicks(int grade) {
        return EAT_TICKS[Math.max(1, Math.min(4, grade))];
    }

    /** {@code max(1, round(n_ref · m))} (GDD §6.4). */
    public static int nutrition(int nRef, int grade) {
        return Math.max(1, (int) Math.round(nRef * multiplier(grade)));
    }

    /** {@code s_ref · m}, × 0.5 when bitter (GDD §6.4, §3.6). */
    public static float saturation(double sRef, int grade, boolean bitter) {
        return saturation(sRef, grade, bitter, 1);
    }

    /**
     * The same times the colour's saturation multiplier (anthocyanins of the purple root, {@code saturation_mult}
     * of the species' colour properties, WR-703); 1 = no change. Only the saturation: no effect is added.
     */
    public static float saturation(double sRef, int grade, boolean bitter, double colourMult) {
        double m = Double.isFinite(colourMult) && colourMult > 0 ? colourMult : 1;
        return (float) (sRef * multiplier(grade) * (bitter ? 0.5 : 1) * m);
    }
}
