package io.github.slugblue.wildroots.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.network.BoundedReader;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * Review 1, test gaps: the released journal format v1 as an SNBT fixture
 * (docs/05-architecture.md §6.1, §5.4) loads and saves back unchanged; the
 * largest possible journal fits one client packet.
 */
class JournalFixtureTest {

    static CompoundTag fixture(String name) throws Exception {
        try (InputStream in = JournalFixtureTest.class.getResourceAsStream("/fixtures/journal/" + name)) {
            if (in == null) {
                throw new IOException("missing fixture " + name);
            }
            return TagParser.parseTag(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void journalV1FixtureLoadsAndSavesBackUnchanged() throws Exception {
        CompoundTag t = fixture("journal_v1.snbt");
        JournalData j = JournalData.load(t);
        assertFalse(j.inert(), j.inertReason());
        assertTrue(j.given());
        assertTrue(j.has("hint.welcome") && j.has("species.wild_carrot") && !j.has("cultivar.carrot"));
        JournalData.Entry w = j.entry("hint.welcome").orElseThrow();
        assertTrue(w.is(JournalData.READ) && w.is(JournalData.PINNED));
        assertEquals("wildroots.species.wild_carrot", DispCodec.argKey(w.args().get(0)));
        assertEquals(2, j.entry("species.wild_carrot").orElseThrow().rows().size());
        assertEquals(5, j.counter("sq.wild_carrot.n"));
        assertEquals(t, j.save(), "save changed the v1 journal");
    }

    @Test
    void theFullestJournalFitsOneClientPacket() {
        JournalData j = new JournalData();
        String arg = "\u6f22".repeat(DispCodec.MAX_ARG_LENGTH);
        List<DispCodec.Line> rows = new ArrayList<>();
        for (int i = 0; i < JournalData.MAX_ROWS; i++) {
            rows.add(DispCodec.Line.of("wildroots.journal.species.found_at", arg, arg, arg, arg));
        }
        int n = 0;
        while (j.unlock("note.big_" + n, List.of(arg, arg, arg, arg), rows) == JournalData.Unlock.NEW) {
            n++;
        }
        for (int i = 0; j.entries().size() < JournalData.MAX_ENTRIES && i < 10_000; i++) {
            j.unlock("note.s" + i, List.of(), List.of());
        }
        JournalSyncPacket p = new JournalSyncPacket(true, true, "x".repeat(JournalSyncPacket.MAX_OWNER), j.ordered());
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        p.encode(b);
        int size = b.readableBytes();
        assertTrue(size < BoundedReader.MAX_S2C_PAYLOAD, "full sync of " + size + " B");
        assertEquals(p, JournalSyncPacket.decode(b));
    }
}
