package io.github.slugblue.wildroots.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.network.c2s.JournalActionPacket;
import io.github.slugblue.wildroots.network.c2s.NameSelectionPacket;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * WR-127, SEC-03: 100 000 mutated buffers per C→S decoder (fixed seed):
 * no exception leaves the guarded decoder, every result is either a valid
 * packet that re-encodes to the same bytes or the malformed marker; payloads
 * stay within the C→S limit; lengths are checked before allocation.
 */
public class DecoderFuzzTest {

    static final int RUNS = 100_000;

    static byte[] bytesOf(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    /** One mutation of {@code seed}: flips, inserts, deletes, truncates or appends bytes. */
    public static byte[] mutate(byte[] seed, SplitMix64 rng) {
        byte[] b = seed.clone();
        int ops = 1 + (int) Long.remainderUnsigned(rng.nextLong(), 4);
        for (int k = 0; k < ops; k++) {
            int kind = (int) Long.remainderUnsigned(rng.nextLong(), 5);
            int at = b.length == 0 ? 0 : (int) Long.remainderUnsigned(rng.nextLong(), b.length);
            switch (kind) {
                case 0 -> {
                    if (b.length > 0) {
                        b[at] = (byte) rng.nextLong();
                    }
                }
                case 1 -> b = java.util.Arrays.copyOf(b, Math.max(0, at)); // truncate
                case 2 -> { // append junk
                    int n = 1 + (int) Long.remainderUnsigned(rng.nextLong(), 8);
                    byte[] c = java.util.Arrays.copyOf(b, b.length + n);
                    for (int i = b.length; i < c.length; i++) {
                        c[i] = (byte) rng.nextLong();
                    }
                    b = c;
                }
                case 3 -> { // huge VarInt length prefix at the start
                    byte[] c = new byte[b.length + 5];
                    c[0] = (byte) 0xFF;
                    c[1] = (byte) 0xFF;
                    c[2] = (byte) 0xFF;
                    c[3] = (byte) 0xFF;
                    c[4] = (byte) 0x07;
                    System.arraycopy(b, 0, c, 5, b.length);
                    b = c;
                }
                default -> { // random bytes
                    int n = (int) Long.remainderUnsigned(rng.nextLong(), 64);
                    b = new byte[n];
                    for (int i = 0; i < n; i++) {
                        b[i] = (byte) rng.nextLong();
                    }
                }
            }
        }
        return b;
    }

    static <M> void fuzz(byte[] seed, Function<FriendlyByteBuf, M> decoder, M malformed,
                         java.util.function.BiConsumer<M, FriendlyByteBuf> encoder, long rngSeed) {
        Function<FriendlyByteBuf, M> guarded = ServerPacketGuard.guardedDecoder(decoder, malformed);
        SplitMix64 rng = new SplitMix64(rngSeed);
        int valid = 0;
        for (int i = 0; i < RUNS; i++) {
            byte[] input = mutate(seed, rng);
            M m = guarded.apply(new FriendlyByteBuf(Unpooled.wrappedBuffer(input)));
            if (m != malformed) {
                valid++;
                FriendlyByteBuf again = new FriendlyByteBuf(Unpooled.buffer());
                encoder.accept(m, again);
                byte[] re = new byte[again.readableBytes()];
                again.readBytes(re);
                assertTrue(re.length <= BoundedReader.MAX_C2S_PAYLOAD);
                assertSame(m.getClass(), decoder.apply(new FriendlyByteBuf(Unpooled.wrappedBuffer(re))).getClass());
            }
        }
        assertTrue(valid > 0, "the fuzzer never produced a valid packet (mutations too coarse)");
    }

    @Test
    void nameSelectionDecoderNeverThrows() {
        byte[] seed = bytesOf(b -> new NameSelectionPacket(123456789L, "Морковь Вавилова").encode(b));
        fuzz(seed, NameSelectionPacket::decode, NameSelectionPacket.MALFORMED, NameSelectionPacket::encode, 1L);
    }

    @Test
    void journalActionDecoderNeverThrows() {
        byte[] seed = bytesOf(b -> new JournalActionPacket(Ids.mod("hint/inbreeding"), JournalActionPacket.PIN).encode(b));
        fuzz(seed, JournalActionPacket::decode, JournalActionPacket.MALFORMED, JournalActionPacket::encode, 2L);
    }

    @Test
    void roundTripAndLimits() {
        NameSelectionPacket p = new NameSelectionPacket(-5, "x".repeat(24));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        p.encode(buf);
        assertTrue(buf.readableBytes() <= BoundedReader.MAX_C2S_PAYLOAD);
        assertEquals(p, NameSelectionPacket.decode(buf));
        FriendlyByteBuf tooLong = new FriendlyByteBuf(Unpooled.buffer());
        tooLong.writeLong(1);
        tooLong.writeUtf("x".repeat(25));
        assertThrows(DecoderException.class, () -> NameSelectionPacket.decode(tooLong));
    }

    @Test
    void collectionSizeIsCheckedBeforeAllocation() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(Integer.MAX_VALUE);
        assertThrows(DecoderException.class, () -> BoundedReader.readSize(buf, 16));
        FriendlyByteBuf neg = new FriendlyByteBuf(Unpooled.buffer());
        neg.writeVarInt(-1);
        assertThrows(DecoderException.class, () -> BoundedReader.readSize(neg, 16));
        FriendlyByteBuf okBuf = new FriendlyByteBuf(Unpooled.buffer());
        okBuf.writeVarInt(16);
        assertEquals(16, BoundedReader.readSize(okBuf, 16));
    }

    @Test
    void stringLengthIsCheckedBeforeDecoding() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(1_000_000); // claims a megabyte, has none
        assertThrows(DecoderException.class, () -> BoundedReader.readText(buf, 24));
    }
}
