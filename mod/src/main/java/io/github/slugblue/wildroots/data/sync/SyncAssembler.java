package io.github.slugblue.wildroots.data.sync;

import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client side of {@link DefinitionsSyncPacket}: collects the parts of one
 * sync in order and decodes the whole. Any broken sequence (another sync id
 * mid-way, a gap, a total change, more than {@value DefinitionsSyncPacket#TOTAL_MAX}
 * bytes, a payload that does not decode) is a failure, never an exception:
 * the client then disconnects with a message (SEC-31). Not thread-safe; the
 * client feeds it from one thread.
 */
public final class SyncAssembler {

    /** Result of one received part. */
    public sealed interface Step {
    }

    /** More parts are expected. */
    public record Waiting(int received, int total) implements Step {
    }

    /** The sync is complete. */
    public record Done(SyncedDefinitions definitions) implements Step {
    }

    /** The sync is broken; {@code reason} is for the log only. */
    public record Failed(String reason) implements Step {
    }

    private int syncId = -1;
    private int total;
    private int next;
    private ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    public Step accept(DefinitionsSyncPacket p) {
        if (p.part() == 0) {
            syncId = p.syncId();
            total = p.total();
            next = 0;
            bytes = new ByteArrayOutputStream();
        }
        if (p.syncId() != syncId || p.total() != total || p.part() != next) {
            reset();
            return new Failed("unexpected part " + p.part() + "/" + p.total() + " of sync " + p.syncId());
        }
        if (bytes.size() + p.data().length > DefinitionsSyncPacket.TOTAL_MAX) {
            reset();
            return new Failed("sync larger than " + DefinitionsSyncPacket.TOTAL_MAX + " B");
        }
        bytes.writeBytes(p.data());
        next++;
        if (next < total) {
            return new Waiting(next, total);
        }
        byte[] all = bytes.toByteArray();
        reset();
        Optional<SyncedDefinitions> defs = decode(all);
        return defs.<Step>map(Done::new).orElseGet(() -> new Failed("payload does not decode"));
    }

    /** Forgets a partial sync (the player left the server). */
    public void reset() {
        syncId = -1;
        total = 0;
        next = 0;
        bytes = new ByteArrayOutputStream();
    }

    /** Decodes a whole payload; empty on any error. */
    public static Optional<SyncedDefinitions> decode(byte[] all) {
        try {
            return Optional.of(SyncedDefinitions.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(all))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
