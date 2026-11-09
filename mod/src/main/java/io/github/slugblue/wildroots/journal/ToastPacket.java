package io.github.slugblue.wildroots.journal;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.network.BoundedReader;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S→C {@code Toast} (docs/05-architecture.md §14): a title and a body line,
 * each a translation key with arguments that the server wrote
 * ({@link DispCodec} rules, key arguments allowed). Used for "New journal
 * entry" and "Cultivar bred!" (narrative §9.6).
 */
public record ToastPacket(DispCodec.Line title, DispCodec.Line body) {

    public static final ToastPacket BROKEN = new ToastPacket(DispCodec.Line.of("wildroots.message.journal.new_entry"),
            DispCodec.Line.of("wildroots.journal.ui.empty"));

    public ToastPacket {
        if (!DispCodec.valid(title) || !DispCodec.valid(body)) {
            throw new IllegalArgumentException("invalid toast line");
        }
    }

    public void encode(FriendlyByteBuf buf) {
        write(buf, title);
        write(buf, body);
    }

    private static void write(FriendlyByteBuf buf, DispCodec.Line l) {
        buf.writeUtf(l.key(), 128);
        buf.writeVarInt(l.args().size());
        for (String a : l.args()) {
            buf.writeUtf(a, DispCodec.MAX_ARG_LENGTH * 2);
        }
    }

    private static DispCodec.Line read(FriendlyByteBuf buf) {
        String key = buf.readUtf(128);
        int n = BoundedReader.readSize(buf, DispCodec.MAX_ARGS);
        String[] args = new String[n];
        for (int i = 0; i < n; i++) {
            args[i] = BoundedReader.readText(buf, DispCodec.MAX_ARG_LENGTH);
        }
        DispCodec.Line l = DispCodec.Line.of(key, args);
        if (!DispCodec.valid(l)) {
            throw new DecoderException("bad toast line");
        }
        return l;
    }

    public static ToastPacket decode(FriendlyByteBuf buf) {
        ToastPacket p = new ToastPacket(read(buf), read(buf));
        BoundedReader.requireFullyRead(buf);
        return p;
    }

    public static ToastPacket clientDecode(FriendlyByteBuf buf) {
        try {
            return decode(buf);
        } catch (RuntimeException e) {
            return BROKEN;
        }
    }
}
