package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Playtest 2, item 3: the lens window packet round-trips, is bounded and refuses forged input. */
class LensWindowPacketTest {

    static final LensRow ROW = new LensRow("s4*y3g2f3Wc3h2d2", 'w', 'S', false, Optional.empty(), false)
            .withBands(new int[][] {{3, 5}, {2, 3}, {2, 2}, {3, 3}, {2, 4}, {1, 2}, {2, 2}});

    static LensWindowPacket plant() {
        return new LensWindowPacket(LensWindowPacket.Kind.PLANT, "wildroots:wild_carrot",
                DispCodec.Line.of(Lens.HEADER, DispCodec.keyArg("wildroots.species.wild_carrot"),
                        DispCodec.keyArg("wildroots.tooltip.stage.sprout")),
                ROW.line(), EnvModifiers.DRY | EnvModifiers.DARK, 1234);
    }

    static FriendlyByteBuf encoded(LensWindowPacket p) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        p.encode(b);
        return b;
    }

    @Test
    void roundTrip() {
        LensWindowPacket p = plant();
        FriendlyByteBuf b = encoded(p);
        assertEquals(p.size(), b.readableBytes());
        assertTrue(p.size() < 200, "plant window of " + p.size() + " bytes");
        LensWindowPacket back = LensWindowPacket.decode(b);
        assertEquals(p, back);
        assertEquals(Optional.of(ROW), LensRow.parse(back.row()));
        LensWindowPacket lot = new LensWindowPacket(LensWindowPacket.Kind.LOT, "wildroots:wild_carrot",
                DispCodec.Line.of(Lens.LOT_HEADER, DispCodec.keyArg("wildroots.species.wild_carrot")), ROW.line(), 0,
                LensWindowPacket.NO_MODEL);
        assertEquals(lot, LensWindowPacket.decode(encoded(lot)));
    }

    @Test
    void sizeLimitIsEnforcedBothWays() {
        String big = "Ж".repeat(DispCodec.MAX_ARG_LENGTH);
        LensWindowPacket huge = new LensWindowPacket(LensWindowPacket.Kind.PLANT, "w:" + "x".repeat(120),
                DispCodec.Line.of("wildroots." + "k".repeat(96), big, big, big, big),
                DispCodec.Line.of("wildroots." + "r".repeat(96), big, big, big, big), 0, 0);
        assertTrue(huge.size() > LensWindowPacket.MAX_BYTES, "not over the limit: " + huge.size());
        assertThrows(IllegalStateException.class, () -> encoded(huge));
        FriendlyByteBuf over = new FriendlyByteBuf(Unpooled.buffer());
        over.writeBytes(new byte[LensWindowPacket.MAX_BYTES + 1]);
        assertThrows(DecoderException.class, () -> LensWindowPacket.decode(over));
        FriendlyByteBuf over2 = new FriendlyByteBuf(Unpooled.buffer());
        over2.writeBytes(new byte[LensWindowPacket.MAX_BYTES + 1]);
        assertSame(LensWindowPacket.BROKEN, LensWindowPacket.clientDecode(over2));
    }

    static void writeLine(FriendlyByteBuf b, DispCodec.Line l) {
        b.writeUtf(l.key());
        b.writeVarInt(l.args().size());
        l.args().forEach(b::writeUtf);
    }

    @Test
    void forgedPacketsAreRefused() {
        FriendlyByteBuf extra = encoded(plant());
        extra.writeByte(0);
        assertThrows(DecoderException.class, () -> LensWindowPacket.decode(extra));

        FriendlyByteBuf kind = new FriendlyByteBuf(Unpooled.buffer());
        kind.writeByte(7);
        assertThrows(DecoderException.class, () -> LensWindowPacket.decode(kind));

        LensWindowPacket p = plant();
        FriendlyByteBuf env = new FriendlyByteBuf(Unpooled.buffer());
        env.writeByte(0);
        env.writeUtf(p.species());
        writeLine(env, p.header());
        writeLine(env, p.row());
        env.writeVarInt(1 << 9);
        env.writeVarInt(0);
        env.writeVarInt(0); // colour lines (protocol 3)
        assertThrows(DecoderException.class, () -> LensWindowPacket.decode(env));

        FriendlyByteBuf args = new FriendlyByteBuf(Unpooled.buffer());
        args.writeByte(0);
        args.writeUtf(p.species());
        args.writeUtf(Lens.HEADER);
        args.writeVarInt(DispCodec.MAX_ARGS + 1);
        assertThrows(DecoderException.class, () -> LensWindowPacket.decode(args));

        FriendlyByteBuf key = new FriendlyByteBuf(Unpooled.buffer());
        key.writeByte(0);
        key.writeUtf(p.species());
        writeLine(key, DispCodec.Line.of("wildroots.ok"));
        key.writeUtf("minecraft.not_ours");
        key.writeVarInt(0);
        key.writeVarInt(0);
        key.writeVarInt(0);
        assertThrows(DecoderException.class, () -> LensWindowPacket.decode(key));

        assertThrows(IllegalArgumentException.class, () -> new LensWindowPacket(LensWindowPacket.Kind.PLANT, "x",
                DispCodec.Line.of("not.a.key"), ROW.line(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LensWindowPacket(LensWindowPacket.Kind.PLANT, "x",
                ROW.line(), ROW.line(), 0, -2));
    }

    /** Review 1, m2: a line that is not valid gives no packet, never an exception. */
    @Test
    void ofIsEmptyForInvalidLinesOrOversize() {
        assertTrue(LensWindowPacket.of(LensWindowPacket.Kind.PLANT, "x", DispCodec.Line.of(Lens.HEADER,
                DispCodec.keyArg("wildroots.cultivar." + "a".repeat(200))), ROW.line(), 0, 0).isEmpty());
        assertTrue(LensWindowPacket.of(LensWindowPacket.Kind.PLANT, "x", DispCodec.Line.of("bad key"), ROW.line(), 0,
                0).isEmpty());
        String big = "Ж".repeat(DispCodec.MAX_ARG_LENGTH);
        assertTrue(LensWindowPacket.of(LensWindowPacket.Kind.PLANT, "w:" + "x".repeat(120),
                DispCodec.Line.of("wildroots." + "k".repeat(96), big, big, big, big),
                DispCodec.Line.of("wildroots." + "r".repeat(96), big, big, big, big), 0, 0).isEmpty());
        assertEquals(Optional.of(plant()), LensWindowPacket.of(plant().kind(), plant().species(), plant().header(),
                plant().row(), plant().env(), plant().state()));
    }

    @Test
    void environmentKeys() {
        assertEquals(List.of("wildroots.window.lens.env.dry", "wildroots.window.lens.env.dark"),
                EnvModifiers.keys(EnvModifiers.DRY | EnvModifiers.DARK | (1 << 20)));
        assertTrue(EnvModifiers.negative("wildroots.window.lens.env.cold"));
        assertFalse(EnvModifiers.negative("wildroots.window.lens.env.compost"));
        assertEquals(2, LensSheet.environment(EnvModifiers.DRY | EnvModifiers.COMPOST).size());
    }

    /** WR-703: the colour lines of a plant of stage 3 or more round-trip, are bounded and refuse forged input. */
    @Test
    void colourLinesRoundTripAndAreBounded() {
        List<DispCodec.Line> colour = List.of(DispCodec.Line.of(LensColour.KEY, DispCodec.keyArg("wildroots.colour.purple")),
                DispCodec.Line.of(LensColour.KEY + ".heat.up"), DispCodec.Line.of(LensColour.KEY + ".cold.down"),
                DispCodec.Line.of(LensColour.KEY + ".water.less"), DispCodec.Line.of(LensColour.KEY + ".saturation"));
        LensWindowPacket p = new LensWindowPacket(LensWindowPacket.Kind.PLANT, "wildroots:wild_carrot",
                plant().header(), ROW.line(), EnvModifiers.DRY, 1234, colour);
        FriendlyByteBuf b = encoded(p);
        assertEquals(p.size(), b.readableBytes());
        assertTrue(p.size() <= LensWindowPacket.MAX_BYTES, "window of " + p.size() + " bytes");
        assertEquals(p, LensWindowPacket.decode(b));
        assertTrue(p.size() - plant().size() < 400, "the colour adds " + (p.size() - plant().size()) + " bytes");
        // a plant without colour lines is the old packet plus one byte (the count 0)
        assertEquals(plant().size(), encoded(plant()).readableBytes());
        assertEquals(LensWindowPacket.of(p.kind(), p.species(), p.header(), p.row(), p.env(), p.state(), colour),
                Optional.of(p));
        // more lines than the colour has, or an invalid line: refused
        List<DispCodec.Line> six = new java.util.ArrayList<>(colour);
        six.add(DispCodec.Line.of(LensColour.KEY + ".extra"));
        assertThrows(IllegalArgumentException.class, () -> new LensWindowPacket(LensWindowPacket.Kind.PLANT, "x",
                plant().header(), ROW.line(), 0, 0, six));
        assertThrows(IllegalArgumentException.class, () -> new LensWindowPacket(LensWindowPacket.Kind.PLANT, "x",
                plant().header(), ROW.line(), 0, 0, List.of(DispCodec.Line.of("bad key"))));
        assertEquals(Optional.empty(), LensWindowPacket.of(p.kind(), "x", p.header(), p.row(), 0, 0, six));
        // a forged count above the bound makes the decoder throw before it reads the lines
        FriendlyByteBuf forged = new FriendlyByteBuf(Unpooled.buffer());
        forged.writeByte(0);
        forged.writeUtf("x");
        forged.writeUtf(plant().header().key());
        forged.writeVarInt(plant().header().args().size());
        plant().header().args().forEach(a -> forged.writeUtf(a));
        forged.writeUtf(ROW.line().key());
        forged.writeVarInt(ROW.line().args().size());
        ROW.line().args().forEach(a -> forged.writeUtf(a));
        forged.writeVarInt(0);
        forged.writeVarInt(0);
        forged.writeVarInt(1000);
        assertThrows(DecoderException.class, () -> LensWindowPacket.decode(forged));
    }
}
