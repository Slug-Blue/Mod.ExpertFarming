package io.github.slugblue.wildroots.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** WR-180: JournalSync and Toast round trip; broken buffers give the marker, never an exception. */
class JournalSyncPacketTest {

    static FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void roundTrip() {
        JournalSyncPacket p = new JournalSyncPacket(true, true, "Steve", List.of(
                new JournalData.Entry("hint.welcome", List.of("@species.wild_carrot"), List.of(), 0, 8),
                new JournalData.Entry("species.wild_carrot", List.of(), List.of(
                        DispCodec.Line.of("wildroots.journal.species.found_at", "@biome.minecraft.plains", "1", "2")), 1, 2)));
        FriendlyByteBuf b = buf();
        p.encode(b);
        assertEquals(p, JournalSyncPacket.decode(b));
    }

    @Test
    void brokenAndOversizedBuffersGiveTheMarker() {
        FriendlyByteBuf b = buf();
        b.writeBoolean(true);
        b.writeBoolean(false);
        b.writeUtf("x");
        b.writeVarInt(JournalData.MAX_ENTRIES + 1);
        assertSame(JournalSyncPacket.BROKEN, JournalSyncPacket.clientDecode(b));
        FriendlyByteBuf c = buf();
        new JournalSyncPacket(false, false, "a", List.of()).encode(c);
        c.writeByte(1);
        assertSame(JournalSyncPacket.BROKEN, JournalSyncPacket.clientDecode(c));
        FriendlyByteBuf d = buf();
        d.writeBoolean(true);
        assertSame(JournalSyncPacket.BROKEN, JournalSyncPacket.clientDecode(d));
    }

    @Test
    void toastRoundTripAndBadKey() {
        ToastPacket t = new ToastPacket(
                DispCodec.Line.of("wildroots.message.journal.new_entry", "@journal.hint.bees.title"),
                DispCodec.Line.of("wildroots.message.toast.bred.body", "@cultivar.eastern_carrot"));
        FriendlyByteBuf b = buf();
        t.encode(b);
        assertEquals(t, ToastPacket.decode(b));
        FriendlyByteBuf c = buf();
        c.writeUtf("minecraft.bad");
        c.writeVarInt(0);
        assertSame(ToastPacket.BROKEN, ToastPacket.clientDecode(c));
    }

    @Test
    void keyArgumentsAreShortModKeysOrBiomeKeys() {
        assertEquals("@species.wild_carrot", DispCodec.keyArg("wildroots.species.wild_carrot"));
        assertEquals("wildroots.species.wild_carrot", DispCodec.argKey("@species.wild_carrot"));
        assertEquals("biome.minecraft.plains", DispCodec.argKey(DispCodec.keyArg("biome.minecraft.plains")));
        assertEquals(null, DispCodec.argKey("species.wild_carrot"));
        assertEquals(null, DispCodec.argKey("@Species"));
        assertEquals(null, DispCodec.argKey("@a b"));
        assertEquals(null, DispCodec.argKey("@"));
    }
}
