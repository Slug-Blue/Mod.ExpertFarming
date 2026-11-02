package io.github.slugblue.wildroots.network;

import io.github.slugblue.wildroots.core.safety.PlayerText;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Bounded reads for packet decoders (SEC-03; docs/05-architecture.md §14
 * item 3). Every length is checked before anything is allocated; a broken
 * field throws {@link DecoderException}, which the guard turns into a dropped
 * packet.
 */
public final class BoundedReader {

    /** Largest C→S payload of a mod channel ({@code ServerboundCustomPayloadPacket.MAX_PAYLOAD_SIZE}, S14). */
    public static final int MAX_C2S_PAYLOAD = 32_767;
    /** Largest S→C payload of a mod channel ({@code ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE}, S14). */
    public static final int MAX_S2C_PAYLOAD = 1_048_576;
    /** Longest resource location in a packet. */
    public static final int MAX_ID_LENGTH = 128;

    private BoundedReader() {
    }

    /**
     * A string field of at most {@code maxCodePoints} code points:
     * {@code readUtf} checks bytes ≤ 3·max and UTF-16 units ≤ max before and
     * after decoding (S14), so it gets 2 × the code-point limit; the real
     * limit is checked here after reading.
     */
    public static String readText(FriendlyByteBuf buf, int maxCodePoints) {
        String s = buf.readUtf(PlayerText.readUtfLimit(maxCodePoints));
        if (s.codePointCount(0, s.length()) > maxCodePoints) {
            throw new DecoderException("string longer than " + maxCodePoints + " code points");
        }
        return s;
    }

    /** A resource location of at most {@value #MAX_ID_LENGTH} characters. */
    public static ResourceLocation readId(FriendlyByteBuf buf) {
        String s = buf.readUtf(MAX_ID_LENGTH);
        ResourceLocation rl = ResourceLocation.tryParse(s);
        if (rl == null) {
            throw new DecoderException("invalid resource location");
        }
        return rl;
    }

    /** A collection size in {@code [0, max]}, checked before the collection is built. */
    public static int readSize(FriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) {
            throw new DecoderException("collection of " + n + " > " + max);
        }
        return n;
    }

    /** No bytes may be left after a decoder: extra bytes mean a forged or foreign packet. */
    public static void requireFullyRead(FriendlyByteBuf buf) {
        if (buf.isReadable()) {
            throw new DecoderException(buf.readableBytes() + " extra bytes");
        }
    }
}
