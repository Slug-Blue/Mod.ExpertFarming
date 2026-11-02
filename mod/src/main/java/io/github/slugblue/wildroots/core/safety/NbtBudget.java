package io.github.slugblue.wildroots.core.safety;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Iterative size and depth of an NBT root (SEC-09;
 * docs/05-architecture.md §5.2, §6.1; {@code 09-security.md} §1.6.2).
 *
 * <p>Bytes are the uncompressed serialisation of {@code NbtIo.write}: a
 * named root (type byte and an empty name) plus the payload. This is not the
 * {@code NbtAccounter} quota of {@code readNbt} (a memory estimate, S14); the
 * packet budget is checked separately (WR-197). Depth counts compound and
 * list tags with the root at 1; leaves (numbers, strings, arrays) add none.
 *
 * <p>The walk keeps its own stack (no recursion) and stops as soon as the
 * byte or depth limit is passed, so an oversized or deeply nested root is
 * rejected before any decoder looks at it.
 */
public final class NbtBudget {

    /** Outcome of a measurement. {@code bytes} and {@code depth} stop growing at the first overrun. */
    public record Result(boolean ok, long bytes, int depth, String reason) {

        static Result pass(long bytes, int depth) {
            return new Result(true, bytes, depth, "");
        }
    }

    private record Frame(Tag tag, int depth) {
    }

    private NbtBudget() {
    }

    /** Measures {@code root}; {@code ok} is false if it is null, larger than {@code maxBytes} or deeper than {@code maxDepth}. */
    public static Result measure(Tag root, long maxBytes, int maxDepth) {
        if (root == null) {
            return new Result(false, 0, 0, "missing");
        }
        long bytes = 3; // type byte + empty root name (u16 length 0)
        int maxSeen = 0;
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(root, 1));
        while (!stack.isEmpty()) {
            Frame f = stack.pop();
            Tag t = f.tag();
            if (t instanceof CompoundTag c) {
                maxSeen = Math.max(maxSeen, f.depth());
                if (maxSeen > maxDepth) {
                    return new Result(false, bytes, maxSeen, "depth " + maxSeen + " > " + maxDepth);
                }
                bytes += 1; // TAG_End
                for (String key : c.getAllKeys()) {
                    Tag v = c.get(key);
                    bytes += 1 + 2 + modifiedUtf8Length(key);
                    if (v != null) {
                        stack.push(new Frame(v, f.depth() + 1));
                    }
                }
            } else if (t instanceof ListTag l) {
                maxSeen = Math.max(maxSeen, f.depth());
                if (maxSeen > maxDepth) {
                    return new Result(false, bytes, maxSeen, "depth " + maxSeen + " > " + maxDepth);
                }
                bytes += 1 + 4;
                for (int i = l.size() - 1; i >= 0; i--) {
                    stack.push(new Frame(l.get(i), f.depth() + 1));
                }
            } else {
                bytes += leafPayload(t);
            }
            if (bytes > maxBytes) {
                return new Result(false, bytes, maxSeen, "size " + bytes + " B > " + maxBytes + " B");
            }
        }
        return Result.pass(bytes, maxSeen);
    }

    /** Payload size of a non-container tag in {@code NbtIo} format. */
    static long leafPayload(Tag t) {
        return switch (t.getId()) {
            case Tag.TAG_END -> 0;
            case Tag.TAG_BYTE -> 1;
            case Tag.TAG_SHORT -> 2;
            case Tag.TAG_INT, Tag.TAG_FLOAT -> 4;
            case Tag.TAG_LONG, Tag.TAG_DOUBLE -> 8;
            case Tag.TAG_BYTE_ARRAY -> 4L + ((ByteArrayTag) t).size();
            case Tag.TAG_STRING -> 2L + modifiedUtf8Length(((StringTag) t).getAsString());
            case Tag.TAG_INT_ARRAY -> 4L + 4L * ((IntArrayTag) t).size();
            case Tag.TAG_LONG_ARRAY -> 4L + 8L * ((LongArrayTag) t).size();
            default -> throw new IllegalArgumentException("unexpected tag type " + t.getId());
        };
    }

    /** Length of {@code s} in Java's modified UTF-8 ({@code DataOutput#writeUTF}). */
    static long modifiedUtf8Length(String s) {
        long n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            n += c >= 1 && c <= 0x7F ? 1 : c <= 0x7FF ? 2 : 3;
        }
        return n;
    }
}
