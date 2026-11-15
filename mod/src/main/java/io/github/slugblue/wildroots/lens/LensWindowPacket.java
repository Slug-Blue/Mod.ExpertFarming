package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.network.BoundedReader;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/**
 * S→C {@code LensWindow} (playtest 2, item 3): the tier-1 reading the
 * server computed when the hand lens was used on a plant or on the seeds in
 * the other hand; the client opens the lens window with it instead of chat
 * lines. Everything is display data the server wrote: a header line (name ·
 * stage, or the lot's name), the compact {@link LensRow} line, the active
 * environment modifiers ({@link EnvModifiers} bits) and, for a plant on
 * farmland, its block state id for the small model. At most
 * {@value #MAX_BYTES} bytes; the decoder checks every length before it
 * reads and refuses extra bytes.
 *
 * @param kind     a plant or a lot
 * @param species  species id ({@code wildroots:wild_carrot}), for trait names
 * @param header   header line
 * @param row      the lens row line ({@link LensRow#KEY})
 * @param env      {@link EnvModifiers} bits (a plant only)
 * @param state    block state id of the plant on farmland, or {@value #NO_MODEL}
 */
public record LensWindowPacket(Kind kind, String species, DispCodec.Line header, DispCodec.Line row, int env,
                               int state) {

    /** Largest encoded packet. */
    public static final int MAX_BYTES = 1024;
    public static final int MAX_SPECIES = BoundedReader.MAX_ID_LENGTH;
    static final int MAX_KEY = 128;
    public static final int NO_MODEL = -1;

    /** A broken packet: the client ignores it. */
    public static final LensWindowPacket BROKEN = new LensWindowPacket(Kind.PLANT, "wildroots:broken",
            DispCodec.Line.of("wildroots.message.lens.nothing"), DispCodec.Line.of(LensRow.KEY, "", "---"), 0, NO_MODEL);

    /** What was looked at. */
    public enum Kind {
        PLANT, LOT
    }

    public LensWindowPacket {
        if (kind == null || species == null || species.length() > MAX_SPECIES || !DispCodec.valid(header)
                || !DispCodec.valid(row) || header.key().length() > MAX_KEY || row.key().length() > MAX_KEY
                || (env & ~EnvModifiers.ALL) != 0 || state < NO_MODEL) {
            throw new IllegalArgumentException("invalid lens window");
        }
    }

    public void encode(FriendlyByteBuf buf) {
        int start = buf.writerIndex();
        buf.writeByte(kind.ordinal());
        buf.writeUtf(species, MAX_SPECIES);
        write(buf, header);
        write(buf, row);
        buf.writeVarInt(env);
        buf.writeVarInt(state);
        if (buf.writerIndex() - start > MAX_BYTES) {
            throw new IllegalStateException("lens window of " + (buf.writerIndex() - start) + " bytes");
        }
    }

    /** Encoded size (server checks before sending). */
    public int size() {
        FriendlyByteBuf b = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            int start = b.writerIndex();
            b.writeByte(kind.ordinal());
            b.writeUtf(species, MAX_SPECIES);
            write(b, header);
            write(b, row);
            b.writeVarInt(env);
            b.writeVarInt(state);
            return b.writerIndex() - start;
        } finally {
            b.release();
        }
    }

    private static void write(FriendlyByteBuf buf, DispCodec.Line l) {
        buf.writeUtf(l.key(), MAX_KEY);
        buf.writeVarInt(l.args().size());
        for (String a : l.args()) {
            buf.writeUtf(a, DispCodec.MAX_ARG_LENGTH * 2);
        }
    }

    private static DispCodec.Line read(FriendlyByteBuf buf) {
        String key = buf.readUtf(MAX_KEY);
        int n = BoundedReader.readSize(buf, DispCodec.MAX_ARGS);
        String[] args = new String[n];
        for (int i = 0; i < n; i++) {
            args[i] = BoundedReader.readText(buf, DispCodec.MAX_ARG_LENGTH);
        }
        DispCodec.Line l = DispCodec.Line.of(key, args);
        if (!DispCodec.valid(l)) {
            throw new DecoderException("bad lens line");
        }
        return l;
    }

    public static LensWindowPacket decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() > MAX_BYTES) {
            throw new DecoderException("lens window of " + buf.readableBytes() + " bytes");
        }
        int k = buf.readByte();
        if (k < 0 || k >= Kind.values().length) {
            throw new DecoderException("bad lens window kind " + k);
        }
        String species = buf.readUtf(MAX_SPECIES);
        DispCodec.Line header = read(buf);
        DispCodec.Line row = read(buf);
        int env = buf.readVarInt();
        int state = buf.readVarInt();
        BoundedReader.requireFullyRead(buf);
        try {
            return new LensWindowPacket(Kind.values()[k], species, header, row, env, state);
        } catch (IllegalArgumentException e) {
            throw new DecoderException(e.getMessage());
        }
    }

    public static LensWindowPacket clientDecode(FriendlyByteBuf buf) {
        try {
            return decode(buf);
        } catch (RuntimeException e) {
            return BROKEN;
        }
    }
}
