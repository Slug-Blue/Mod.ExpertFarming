package io.github.slugblue.wildroots.data.sync;

import io.github.slugblue.wildroots.network.BoundedReader;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S→C {@code DefinitionsSync} (docs/05-architecture.md §8.3, §14; SEC-04,
 * SEC-31): one part of the serialised {@link SyncedDefinitions}.
 *
 * <p>Forge does not split {@code SimpleChannel} packets and an S→C payload
 * is at most 1 048 576 B (S14), so the split is ours: up to
 * {@value #SINGLE_MAX} B go in one packet; above that the bytes travel in
 * parts of at most {@value #PART_MAX} B ({@code part}/{@code total}); above
 * {@value #TOTAL_MAX} B the server drops data-pack species until it fits and
 * tells operators. The client accepts the same limits and disconnects with a
 * message on anything else.
 */
public record DefinitionsSyncPacket(int syncId, int part, int total, byte[] data) {

    public static final int SINGLE_MAX = 512 * 1024;
    public static final int PART_MAX = 256 * 1024;
    public static final int TOTAL_MAX = 2 * 1024 * 1024;
    /** Largest number of parts: {@code TOTAL_MAX / PART_MAX}. */
    public static final int MAX_PARTS = TOTAL_MAX / PART_MAX;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(syncId);
        buf.writeVarInt(part);
        buf.writeVarInt(total);
        buf.writeByteArray(data);
    }

    public static DefinitionsSyncPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int part = buf.readVarInt();
        int total = buf.readVarInt();
        if (total < 1 || total > MAX_PARTS || part < 0 || part >= total) {
            throw new DecoderException("bad part " + part + "/" + total);
        }
        byte[] data = buf.readByteArray(total == 1 ? SINGLE_MAX : PART_MAX);
        BoundedReader.requireFullyRead(buf);
        return new DefinitionsSyncPacket(id, part, total, data);
    }

    /** Serialised form of {@code defs}. */
    public static byte[] serialise(SyncedDefinitions defs) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        defs.write(buf);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    /** Packets for {@code bytes} (≤ {@value #TOTAL_MAX}): one, or parts of ≤ {@value #PART_MAX}. */
    public static List<DefinitionsSyncPacket> split(int syncId, byte[] bytes) {
        if (bytes.length > TOTAL_MAX) {
            throw new IllegalArgumentException(bytes.length + " B > " + TOTAL_MAX);
        }
        if (bytes.length <= SINGLE_MAX) {
            return List.of(new DefinitionsSyncPacket(syncId, 0, 1, bytes));
        }
        int total = (bytes.length + PART_MAX - 1) / PART_MAX;
        List<DefinitionsSyncPacket> out = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            out.add(new DefinitionsSyncPacket(syncId, i, total,
                    Arrays.copyOfRange(bytes, i * PART_MAX, Math.min(bytes.length, (i + 1) * PART_MAX))));
        }
        return out;
    }
}
