package io.github.slugblue.wildroots.network.c2s;

import io.github.slugblue.wildroots.network.BoundedReader;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S {@code JournalAction} (docs/05-architecture.md §14 table; SEC-04): mark
 * a journal entry as read or pin / unpin it. Only the entry id and the
 * action number.
 *
 * <p>Codec only: the handler and the registration come with the journal
 * task (WR-18x), through {@code ServerPacketGuard.register}.
 */
public record JournalActionPacket(ResourceLocation entry, int action) {

    public static final int MARK_READ = 0;
    public static final int PIN = 1;
    public static final int UNPIN = 2;
    public static final int MAX_ACTION = UNPIN;

    /** Marker returned by the guarded decoder for a broken buffer. */
    public static final JournalActionPacket MALFORMED = new JournalActionPacket(
            ResourceLocation.tryBuild("wildroots", "malformed"), -1);

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(entry.toString(), BoundedReader.MAX_ID_LENGTH);
        buf.writeVarInt(action);
    }

    public static JournalActionPacket decode(FriendlyByteBuf buf) {
        ResourceLocation entry = BoundedReader.readId(buf);
        int action = buf.readVarInt();
        if (action < 0 || action > MAX_ACTION) {
            throw new DecoderException("unknown journal action " + action);
        }
        BoundedReader.requireFullyRead(buf);
        return new JournalActionPacket(entry, action);
    }
}
