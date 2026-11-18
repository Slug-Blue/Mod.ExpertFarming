package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.*;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class LensClimateTest {
    static List<String> keys(LensClimate c) {
        return c.lines().stream().map(DispCodec.Line::key).toList();
    }

    @Test
    void stressBoundariesAndReserveAreQualitative() {
        assertTrue(keys(LensClimate.of(0, .35, true, null, 4, false, false, false))
                .contains("wildroots.lens.climate.comfortable"));
        assertTrue(keys(LensClimate.of(.199, .35, true, null, 4, false, false, false))
                .contains("wildroots.lens.climate.heat.1"));
        assertTrue(keys(LensClimate.of(.2, .35, false, null, 4, false, false, false))
                .contains("wildroots.lens.climate.cold.2"));
        LensClimate risk = LensClimate.of(.35, .35, false, null, 2, false, false, true);
        assertEquals(List.of("wildroots.lens.climate.cold.3", "wildroots.lens.climate.reserve.cold.0",
                "wildroots.lens.climate.time", "wildroots.lens.climate.protection.water"), keys(risk));
        assertEquals(List.of("2"), risk.lines().get(2).args());
    }

    @Test
    void exemptionsAndWiltedPlantsNeverPromiseADeathCountdown() {
        for (String exemption : List.of("config", "origin", "mother", "stage", "dimension", "starter", "old_save")) {
            LensClimate c = LensClimate.of(1, .35, true, exemption, 4, false, true, true);
            assertTrue(keys(c).contains("wildroots.lens.climate.exempt." + exemption));
            assertFalse(keys(c).contains("wildroots.lens.climate.time"));
        }
        LensClimate dead = LensClimate.of(-1, .35, true, null, 0, true, true, true);
        assertEquals("wildroots.lens.climate.wilted", dead.lines().get(0).key());
        assertFalse(keys(dead).contains("wildroots.lens.climate.time"));
        assertTrue(keys(LensClimate.of(1, .35, true, null, 0, false, false, false))
                .contains("wildroots.lens.climate.paused"));
        assertEquals(List.of(), LensClimate.NONE.lines());
    }

    @Test
    void packetRoundTripAddsExactlyFourBytesAndRejectsForgedClimate() {
        LensWindowPacket base = LensWindowPacketTest.plant();
        LensClimate climate = LensClimate.of(.4, .35, true, null, 3, false, true, false);
        LensWindowPacket packet = new LensWindowPacket(base.kind(), base.species(), base.header(), base.row(),
                base.env(), base.state(), base.colour(), climate);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buf);
            assertEquals(packet.size(), buf.readableBytes());
            assertEquals(packet, LensWindowPacket.decode(buf));
            buf.clear();
            packet.encode(buf);
            assertEquals(climate.bits(), buf.getInt(buf.writerIndex() - 4));
            buf.setInt(buf.writerIndex() - 4, -1);
            assertSame(LensWindowPacket.BROKEN, LensWindowPacket.clientDecode(buf));
            assertTrue(packet.size() <= LensWindowPacket.MAX_BYTES);
            assertEquals(base.size(), packet.size()); // the fixed climate field is present for lots too
        } finally {
            buf.release();
        }
        assertThrows(IllegalArgumentException.class, () -> new LensClimate(1));
        assertThrows(IllegalArgumentException.class, () -> new LensClimate((1 << 20) | (3 << 3)));
        assertThrows(IllegalArgumentException.class, () -> new LensClimate((1 << 20) | (8 << 5)));
    }
}
