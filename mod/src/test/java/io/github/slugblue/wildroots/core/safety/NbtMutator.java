package io.github.slugblue.wildroots.core.safety;

import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * WR-197: deterministic structural mutations of an NBT tree (seeded
 * {@link SplitMix64}). Each mutation copies the seed tree and applies 1–4
 * edits at random nodes: remove an entry, replace a value by a random tag of
 * any type (also nested or oversized), set a number to a boundary value
 * (0, −1, MIN, MAX, NaN, ∞), garble a string (control, bidi, surrogate,
 * long), resize or fill an array, add a key the decoders know, change the
 * type of an entry, or copy a subtree into another place.
 */
public final class NbtMutator {

    /** Keys the mod's decoders read, so additions and type swaps hit real code paths. */
    static final String[] KEYS = {"v", "sp", "g", "a", "lh", "e", "pp", "o", "at", "c", "r", "s", "lr", "vp", "hc",
            "um", "ul", "cv", "ct", "ci", "ln", "gen", "fl", "pf", "pl", "f", "po", "br", "bee", "w", "d", "l", "id",
            "var", "disp", "rs", "gr", "bt", "given", "toast", "next", "src", "age", "k"};

    private final SplitMix64 rng;

    public NbtMutator(long seed) {
        rng = new SplitMix64(seed);
    }

    /** A mutated copy of {@code seed}. */
    public CompoundTag mutate(CompoundTag seed) {
        CompoundTag t = seed.copy();
        int ops = 1 + rng.nextInt(4);
        for (int i = 0; i < ops; i++) {
            once(t);
        }
        return t;
    }

    private void once(CompoundTag root) {
        List<Tag> containers = new ArrayList<>();
        collect(root, containers, 0);
        Tag at = containers.get(rng.nextInt(containers.size()));
        if (at instanceof CompoundTag c) {
            mutateCompound(c, root);
        } else if (at instanceof ListTag l) {
            mutateList(l);
        } else if (at instanceof CollectionTag<?> arr) {
            mutateArray(arr);
        }
    }

    private static void collect(Tag t, List<Tag> out, int depth) {
        if (depth > 64) {
            return;
        }
        if (t instanceof CompoundTag c) {
            out.add(c);
            for (String k : c.getAllKeys()) {
                collect(c.get(k), out, depth + 1);
            }
        } else if (t instanceof ListTag l) {
            out.add(l);
            for (Tag e : l) {
                collect(e, out, depth + 1);
            }
        } else if (t instanceof ByteArrayTag || t instanceof IntArrayTag || t instanceof LongArrayTag) {
            out.add(t);
        }
    }

    private void mutateCompound(CompoundTag c, CompoundTag root) {
        List<String> keys = new ArrayList<>(c.getAllKeys());
        String key = keys.isEmpty() || rng.nextInt(5) == 0 ? KEYS[rng.nextInt(KEYS.length)] : keys.get(rng.nextInt(keys.size()));
        Tag old = c.get(key);
        switch (rng.nextInt(7)) {
            case 0 -> c.remove(key);
            case 1 -> c.put(key, randomTag(3));
            case 2 -> c.put(key, old instanceof NumericTag n ? boundary(n) : randomNumber());
            case 3 -> c.put(key, StringTag.valueOf(randomString()));
            case 4 -> c.put(key, old instanceof CollectionTag<?> && !(old instanceof ListTag) ? resized(old) : randomArray());
            case 5 -> c.put(key, retyped(old));
            default -> c.put(key, someSubtree(root).copy());
        }
    }

    private void mutateList(ListTag l) {
        switch (rng.nextInt(4)) {
            case 0 -> {
                if (!l.isEmpty()) {
                    l.remove(rng.nextInt(l.size()));
                }
            }
            case 1 -> {
                if (!l.isEmpty()) {
                    l.add(l.get(rng.nextInt(l.size())).copy());
                }
            }
            case 2 -> {
                if (!l.isEmpty()) {
                    // same element type or the change is refused by ListTag; try a random tag
                    l.setTag(rng.nextInt(l.size()), randomTag(2));
                }
            }
            default -> {
                int n = rng.nextInt(40);
                Tag e = l.isEmpty() ? randomTag(2) : l.get(0);
                for (int i = 0; i < n; i++) {
                    l.addTag(l.size(), e.copy());
                }
            }
        }
    }

    private void mutateArray(CollectionTag<?> arr) {
        if (arr.isEmpty() || rng.nextBoolean()) {
            int n = rng.nextInt(3);
            for (int i = 0; i < n && !arr.isEmpty(); i++) {
                arr.remove(rng.nextInt(arr.size()));
            }
            int add = rng.nextInt(12);
            for (int i = 0; i < add; i++) {
                arr.addTag(arr.size(), arr instanceof ByteArrayTag ? ByteTag.valueOf((byte) rng.nextLong())
                        : arr instanceof IntArrayTag ? IntTag.valueOf((int) rng.nextLong()) : LongTag.valueOf(rng.nextLong()));
            }
        } else {
            int i = rng.nextInt(arr.size());
            arr.setTag(i, arr instanceof ByteArrayTag ? ByteTag.valueOf((byte) rng.nextLong())
                    : arr instanceof IntArrayTag ? IntTag.valueOf((int) rng.nextLong()) : LongTag.valueOf(rng.nextLong()));
        }
    }

    private Tag someSubtree(CompoundTag root) {
        List<Tag> all = new ArrayList<>();
        collect(root, all, 0);
        return all.get(rng.nextInt(all.size()));
    }

    private Tag boundary(NumericTag n) {
        int k = rng.nextInt(6);
        if (n instanceof ByteTag) {
            return ByteTag.valueOf(new byte[] {0, -1, Byte.MIN_VALUE, Byte.MAX_VALUE, 1, 2}[k]);
        }
        if (n instanceof ShortTag) {
            return ShortTag.valueOf(new short[] {0, -1, Short.MIN_VALUE, Short.MAX_VALUE, 1, 2}[k]);
        }
        if (n instanceof IntTag) {
            return IntTag.valueOf(new int[] {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, n.getAsInt() + 1, 65_536}[k]);
        }
        if (n instanceof LongTag) {
            return LongTag.valueOf(new long[] {0, -1, Long.MIN_VALUE, Long.MAX_VALUE, n.getAsLong() + 1, rng.nextLong()}[k]);
        }
        if (n instanceof FloatTag) {
            return FloatTag.valueOf(new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1e30f}[k]);
        }
        return DoubleTag.valueOf(new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, -0.0, 1e300}[k]);
    }

    private Tag randomNumber() {
        return switch (rng.nextInt(6)) {
            case 0 -> ByteTag.valueOf((byte) rng.nextLong());
            case 1 -> ShortTag.valueOf((short) rng.nextLong());
            case 2 -> IntTag.valueOf((int) rng.nextLong());
            case 3 -> LongTag.valueOf(rng.nextLong());
            case 4 -> FloatTag.valueOf(Float.intBitsToFloat((int) rng.nextLong()));
            default -> DoubleTag.valueOf(Double.longBitsToDouble(rng.nextLong()));
        };
    }

    private String randomString() {
        int kind = rng.nextInt(6);
        if (kind == 0) {
            return "";
        }
        if (kind == 1) {
            return "wildroots:" + "x".repeat(rng.nextInt(300));
        }
        int n = kind == 2 ? 2000 + rng.nextInt(4000) : rng.nextInt(80);
        StringBuilder sb = new StringBuilder();
        int[] specials = {0, 1, 0x1F, 0x7F, 0x202E, 0x2066, 0x200B, 0xFEFF, 0xD800, 0xDFFF, 0x6F22, '.', ':', '/', 'A'};
        for (int i = 0; i < n; i++) {
            if (rng.nextInt(3) == 0) {
                sb.append((char) specials[rng.nextInt(specials.length)]);
            } else {
                sb.append((char) ('a' + rng.nextInt(26)));
            }
        }
        return sb.toString();
    }

    private Tag randomArray() {
        int n = rng.nextInt(10) == 0 ? 1000 + rng.nextInt(5000) : rng.nextInt(12);
        return switch (rng.nextInt(3)) {
            case 0 -> {
                byte[] b = new byte[n];
                for (int i = 0; i < n; i++) {
                    b[i] = (byte) rng.nextLong();
                }
                yield new ByteArrayTag(b);
            }
            case 1 -> {
                int[] a = new int[n];
                for (int i = 0; i < n; i++) {
                    a[i] = (int) rng.nextLong();
                }
                yield new IntArrayTag(a);
            }
            default -> {
                long[] a = new long[n];
                for (int i = 0; i < n; i++) {
                    a[i] = rng.nextLong();
                }
                yield new LongArrayTag(a);
            }
        };
    }

    private Tag resized(Tag arr) {
        CollectionTag<?> c = (CollectionTag<?>) arr.copy();
        mutateArray(c);
        return c;
    }

    private Tag retyped(Tag old) {
        if (old instanceof StringTag) {
            return rng.nextBoolean() ? IntTag.valueOf(1) : new CompoundTag();
        }
        if (old instanceof NumericTag) {
            return rng.nextBoolean() ? StringTag.valueOf(old.getAsString()) : (rng.nextBoolean() ? DoubleTag.valueOf(1) : ByteTag.valueOf((byte) 1));
        }
        if (old instanceof CompoundTag) {
            return rng.nextBoolean() ? new ListTag() : StringTag.valueOf("{}");
        }
        if (old instanceof ListTag) {
            return rng.nextBoolean() ? new CompoundTag() : new IntArrayTag(new int[] {1, 2});
        }
        return randomTag(1);
    }

    /** A random tag of any type; containers nest up to {@code depth} (sometimes much deeper). */
    private Tag randomTag(int depth) {
        int kind = rng.nextInt(depth <= 0 ? 4 : 7);
        switch (kind) {
            case 0:
                return randomNumber();
            case 1:
                return StringTag.valueOf(randomString());
            case 2:
                return randomArray();
            case 3:
                return boundary(IntTag.valueOf(0));
            case 4: {
                CompoundTag c = new CompoundTag();
                int n = rng.nextInt(5);
                for (int i = 0; i < n; i++) {
                    c.put(KEYS[rng.nextInt(KEYS.length)], randomTag(depth - 1));
                }
                return c;
            }
            case 5: {
                ListTag l = new ListTag();
                int n = rng.nextInt(6);
                Tag e = randomTag(depth - 1);
                for (int i = 0; i < n; i++) {
                    l.add(e.copy());
                }
                return l;
            }
            default: {
                // a deep chain: depth limits of every root must hold
                CompoundTag top = new CompoundTag();
                CompoundTag cur = top;
                int d = 4 + rng.nextInt(60);
                for (int i = 0; i < d; i++) {
                    CompoundTag next = new CompoundTag();
                    cur.put(KEYS[rng.nextInt(KEYS.length)], next);
                    cur = next;
                }
                return top;
            }
        }
    }
}
