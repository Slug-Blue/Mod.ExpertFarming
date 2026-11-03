package io.github.slugblue.wildroots.data.sync;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.network.DecoderFuzzTest;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * SEC-31 (review 1 of batch 2): 100 000 mutated buffers into each client
 * decoder of the definitions sync. Neither throws: a broken packet is the
 * {@code BROKEN} marker, a broken payload is an empty result, and the client
 * then disconnects with a message instead of crashing.
 */
class ClientDecoderFuzzTest {

    static final int RUNS = 100_000;

    static byte[] payload() {
        return DefinitionsSyncPacket.serialise(DefinitionsSyncTest.synthetic(3, 4, "pack"));
    }

    @Test
    void syncedDefinitionsReaderNeverThrows() {
        byte[] seed = payload();
        SplitMix64 rng = new SplitMix64(31L);
        int decoded = 0;
        for (int i = 0; i < RUNS; i++) {
            Optional<SyncedDefinitions> r = SyncAssembler.decode(DecoderFuzzTest.mutate(seed, rng));
            if (r.isPresent()) {
                decoded++;
            }
        }
        assertTrue(decoded >= 0);
        assertTrue(SyncAssembler.decode(seed).isPresent(), "the unmutated payload decodes");
    }

    @Test
    void packetDecoderNeverThrows() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new DefinitionsSyncPacket(5, 0, 1, payload()).encode(buf);
        byte[] seed = new byte[buf.readableBytes()];
        buf.readBytes(seed);
        SplitMix64 rng = new SplitMix64(32L);
        SyncAssembler assembler = new SyncAssembler();
        for (int i = 0; i < RUNS; i++) {
            DefinitionsSyncPacket p = DefinitionsSync.clientDecode(
                    new FriendlyByteBuf(Unpooled.wrappedBuffer(DecoderFuzzTest.mutate(seed, rng))));
            if (p != DefinitionsSync.BROKEN) {
                assembler.accept(p); // Waiting, Done or Failed, never an exception
            }
        }
        assertTrue(DefinitionsSync.clientDecode(new FriendlyByteBuf(Unpooled.wrappedBuffer(seed))) != DefinitionsSync.BROKEN);
    }
}
