package io.github.slugblue.wildroots.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Test shapes for the NBT limits (SEC-09): roots padded to an exact size or nesting depth, wrong-typed values. */
public final class NbtShapes {

    /** Key of the padding string; no decoder reads it. */
    public static final String PAD = "pad";

    private NbtShapes() {
    }

    /** Bytes of {@code root} as {@link NbtBudget} counts them. */
    public static long bytes(Tag root) {
        return NbtBudget.measure(root, Long.MAX_VALUE, Integer.MAX_VALUE).bytes();
    }

    /** Depth of {@code root} as {@link NbtBudget} counts it (root compound = 1). */
    public static int depth(Tag root) {
        return NbtBudget.measure(root, Long.MAX_VALUE, Integer.MAX_VALUE).depth();
    }

    /** A copy of {@code root} with a {@value #PAD} string that makes it exactly {@code target} bytes. */
    public static CompoundTag padTo(CompoundTag root, long target) {
        CompoundTag c = root.copy();
        c.remove(PAD);
        long base = bytes(c);
        // entry: type byte + u16 name length + "pad" + u16 value length + value
        long n = target - base - (1 + 2 + PAD.length() + 2);
        if (n < 0) {
            throw new IllegalArgumentException("root already " + base + " B > " + target + " B");
        }
        c.putString(PAD, "p".repeat((int) n));
        assertEquals(target, bytes(c), "padding arithmetic");
        return c;
    }

    /** A copy of {@code root} whose deepest container is at exactly {@code depth} (key {@code deep}). */
    public static CompoundTag nestTo(CompoundTag root, int depth) {
        CompoundTag c = root.copy();
        c.remove("deep");
        int have = depth(c);
        if (depth > have) {
            CompoundTag inner = new CompoundTag();
            for (int d = 3; d <= depth; d++) {
                CompoundTag outer = new CompoundTag();
                outer.put("n", inner);
                inner = outer;
            }
            c.put("deep", inner);
        }
        assertEquals(Math.max(have, depth), depth(c), "nesting arithmetic");
        return c;
    }

    /** A value of another tag type: a string for anything else, an int for a string. */
    public static Tag wrongType(Tag t) {
        return t instanceof StringTag ? IntTag.valueOf(7) : StringTag.valueOf("x");
    }

    /** A numeric value of another width (byte, int or long), or {@code null} if {@code t} is not numeric. */
    public static Tag otherWidth(Tag t) {
        if (!(t instanceof NumericTag n)) {
            return null;
        }
        if (t instanceof ByteTag) {
            return IntTag.valueOf(n.getAsInt());
        }
        if (t instanceof LongTag) {
            return IntTag.valueOf(n.getAsInt());
        }
        return LongTag.valueOf(n.getAsLong());
    }
}
