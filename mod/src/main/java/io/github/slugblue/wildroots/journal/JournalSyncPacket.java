package io.github.slugblue.wildroots.journal;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.network.BoundedReader;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S→C {@code JournalSync} (docs/05-architecture.md §14): the entries the
 * server unlocked, as ids, arguments and rows of translation keys. On login
 * and when the journal is opened it is {@code full} (the client replaces its
 * copy); otherwise it is a delta of changed entries. {@code open} asks the
 * client to open the journal screen (the item was used). The client never
 * computes what an entry says: it draws the keys it is sent (§15).
 *
 * <p>The decoder is bounded before allocation: ≤ {@value JournalData#MAX_ENTRIES}
 * entries, ≤ 4 arguments of ≤ 64 code points, ≤ {@value JournalData#MAX_ROWS}
 * rows; an invalid entry fails the whole packet (the client keeps its copy).
 */
public record JournalSyncPacket(boolean full, boolean open, String owner, List<JournalData.Entry> entries) {

    public static final int MAX_OWNER = 32;

    public JournalSyncPacket {
        entries = List.copyOf(entries);
    }

    /** Marker of a packet that did not decode. */
    public static final JournalSyncPacket BROKEN = new JournalSyncPacket(false, false, "", List.of());

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(full);
        buf.writeBoolean(open);
        buf.writeUtf(owner, MAX_OWNER * 2);
        buf.writeVarInt(entries.size());
        for (JournalData.Entry e : entries) {
            buf.writeUtf(e.id(), BoundedReader.MAX_ID_LENGTH);
            buf.writeVarInt(e.args().size());
            for (String a : e.args()) {
                buf.writeUtf(a, DispCodec.MAX_ARG_LENGTH * 2);
            }
            buf.writeVarInt(e.rows().size());
            for (DispCodec.Line l : e.rows()) {
                buf.writeUtf(l.key(), 128);
                buf.writeVarInt(l.args().size());
                for (String a : l.args()) {
                    buf.writeUtf(a, DispCodec.MAX_ARG_LENGTH * 2);
                }
            }
            buf.writeVarInt(e.order());
            buf.writeByte(e.flags());
        }
    }

    public static JournalSyncPacket decode(FriendlyByteBuf buf) {
        boolean full = buf.readBoolean();
        boolean open = buf.readBoolean();
        String owner = BoundedReader.readText(buf, MAX_OWNER);
        int n = BoundedReader.readSize(buf, JournalData.MAX_ENTRIES);
        List<JournalData.Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(BoundedReader.MAX_ID_LENGTH);
            List<String> args = readArgs(buf);
            int r = BoundedReader.readSize(buf, JournalData.MAX_ROWS);
            List<DispCodec.Line> rows = new ArrayList<>(r);
            for (int k = 0; k < r; k++) {
                DispCodec.Line l = new DispCodec.Line(buf.readUtf(128), readArgs(buf));
                if (!DispCodec.valid(l)) {
                    throw new DecoderException("bad row");
                }
                rows.add(l);
            }
            int order = buf.readVarInt();
            int flags = buf.readByte() & JournalData.ALL_FLAGS;
            if (!JournalData.validId(id) || !JournalData.validArgs(args)) {
                throw new DecoderException("bad entry");
            }
            entries.add(new JournalData.Entry(id, args, rows, order, flags));
        }
        BoundedReader.requireFullyRead(buf);
        return new JournalSyncPacket(full, open, owner, entries);
    }

    private static List<String> readArgs(FriendlyByteBuf buf) {
        int a = BoundedReader.readSize(buf, DispCodec.MAX_ARGS);
        List<String> args = new ArrayList<>(a);
        for (int k = 0; k < a; k++) {
            args.add(BoundedReader.readText(buf, DispCodec.MAX_ARG_LENGTH));
        }
        return args;
    }

    /** Client decoder: never throws, a broken buffer gives {@link #BROKEN}. */
    public static JournalSyncPacket clientDecode(FriendlyByteBuf buf) {
        try {
            return decode(buf);
        } catch (RuntimeException e) {
            return BROKEN;
        }
    }
}
