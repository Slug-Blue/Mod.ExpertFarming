package io.github.slugblue.wildroots.data.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.Ids;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * WR-128, SEC-31 (TAE): the split and the 2 MiB cut at the exact byte
 * boundaries (512 KiB, 512 KiB + 1, 2 MiB, 2 MiB + 1), through the wire
 * format and the client assembler.
 */
class DefinitionsSyncBoundaryTest {

    static final SyncedDefinitions.Species PACK_SPECIES = new SyncedDefinitions.Species(Ids.of("pack", "turnip"),
            "field", "pack.species.turnip", Map.of("fruit_size", "root_size"), 0x00FF00, "annual");
    static final SyncedDefinitions.Cultivar PACK_CULTIVAR = new SyncedDefinitions.Cultivar(Ids.of("pack", "white"),
            PACK_SPECIES.id(), 1, Optional.empty(), "pack.cultivar.white.note", Optional.empty());

    static int size(List<String> lines, boolean withPack) {
        return DefinitionsSyncPacket.serialise(defs(lines, withPack)).length;
    }

    static SyncedDefinitions defs(List<String> lines, boolean withPack) {
        return withPack ? new SyncedDefinitions(lines, List.of(PACK_SPECIES), List.of(PACK_CULTIVAR))
                : new SyncedDefinitions(lines, List.of(), List.of());
    }

    /** Definitions whose serialised form is exactly {@code target} bytes (padding in the line list). */
    static SyncedDefinitions ofSize(int target, boolean withPack) {
        List<String> lines = new ArrayList<>();
        String full = "x".repeat(SyncedDefinitions.MAX_STRING); // 2 + 256 B
        // grow by full lines in big steps, then one by one
        while (target - size(lines, withPack) > 300) {
            int steps = Math.max(1, (target - size(lines, withPack) - 300) / 258);
            for (int i = 0; i < steps; i++) {
                lines.add(full);
            }
        }
        int r = target - size(lines, withPack);
        if (r == 129 || r > 258) {
            lines.add("y".repeat(100)); // 101 B
            r = target - size(lines, withPack);
        }
        lines.add("z".repeat(r <= 128 ? r - 1 : r - 2));
        SyncedDefinitions d = defs(lines, withPack);
        assertEquals(target, DefinitionsSyncPacket.serialise(d).length, "padding helper is off");
        return d;
    }

    static SyncAssembler.Step throughTheWire(List<DefinitionsSyncPacket> packets) {
        SyncAssembler a = new SyncAssembler();
        SyncAssembler.Step step = null;
        for (DefinitionsSyncPacket p : packets) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            p.encode(buf);
            assertTrue(buf.readableBytes() <= 1_048_576, "S→C payload " + buf.readableBytes());
            step = a.accept(DefinitionsSyncPacket.decode(buf));
        }
        return step;
    }

    @Test
    void singlePacketUpToHalfAMebibyte() {
        SyncedDefinitions d = ofSize(DefinitionsSyncPacket.SINGLE_MAX, false);
        DefinitionsSync.Plan plan = DefinitionsSync.plan(d, 1);
        assertEquals(1, plan.packets().size());
        assertEquals(d, assertInstanceOf(SyncAssembler.Done.class, throughTheWire(plan.packets())).definitions());

        SyncedDefinitions over = ofSize(DefinitionsSyncPacket.SINGLE_MAX + 1, false);
        DefinitionsSync.Plan parts = DefinitionsSync.plan(over, 2);
        assertEquals(3, parts.packets().size(), "256 KiB + 256 KiB + 1 B");
        assertEquals(1, parts.packets().get(2).data().length);
        assertEquals(over, assertInstanceOf(SyncAssembler.Done.class, throughTheWire(parts.packets())).definitions());
    }

    @Test
    void exactlyTwoMebibytesIsSentWhole() {
        SyncedDefinitions d = ofSize(DefinitionsSyncPacket.TOTAL_MAX, true);
        DefinitionsSync.Plan plan = DefinitionsSync.plan(d, 3);
        assertEquals(List.of(), plan.dropped());
        assertEquals(DefinitionsSyncPacket.MAX_PARTS, plan.packets().size());
        assertTrue(plan.packets().stream().allMatch(p -> p.data().length == DefinitionsSyncPacket.PART_MAX));
        assertEquals(d, assertInstanceOf(SyncAssembler.Done.class, throughTheWire(plan.packets())).definitions());
    }

    @Test
    void oneByteOverTwoMebibytesDropsTheDataPackSpecies() {
        SyncedDefinitions d = ofSize(DefinitionsSyncPacket.TOTAL_MAX + 1, true);
        DefinitionsSync.Plan plan = DefinitionsSync.plan(d, 4);
        assertEquals(List.of(PACK_SPECIES.id()), plan.dropped());
        assertTrue(plan.bytes() <= DefinitionsSyncPacket.TOTAL_MAX);
        SyncedDefinitions got = assertInstanceOf(SyncAssembler.Done.class, throughTheWire(plan.packets())).definitions();
        assertEquals(List.of(), got.species());
        assertEquals(List.of(), got.cultivars(), "the cultivars of a dropped species go with it");
        assertEquals(d.lines(), got.lines());
        assertThrows(IllegalArgumentException.class,
                () -> DefinitionsSyncPacket.split(1, new byte[DefinitionsSyncPacket.TOTAL_MAX + 1]));
        assertThrows(IllegalStateException.class, () -> DefinitionsSync.plan(ofSize(DefinitionsSyncPacket.TOTAL_MAX + 1,
                false), 5), "built-in data alone over the limit cannot be cut");
    }

    @Test
    void clientDecoderLimitsAtTheBoundary() {
        // one packet: SINGLE_MAX is the most it reads, a part: PART_MAX
        FriendlyByteBuf single = new FriendlyByteBuf(Unpooled.buffer());
        new DefinitionsSyncPacket(1, 0, 1, new byte[DefinitionsSyncPacket.SINGLE_MAX]).encode(single);
        assertEquals(DefinitionsSyncPacket.SINGLE_MAX, DefinitionsSyncPacket.decode(single).data().length);
        FriendlyByteBuf singleOver = new FriendlyByteBuf(Unpooled.buffer());
        new DefinitionsSyncPacket(1, 0, 1, new byte[DefinitionsSyncPacket.SINGLE_MAX + 1]).encode(singleOver);
        assertThrows(DecoderException.class, () -> DefinitionsSyncPacket.decode(singleOver));
        FriendlyByteBuf part = new FriendlyByteBuf(Unpooled.buffer());
        new DefinitionsSyncPacket(1, 7, 8, new byte[DefinitionsSyncPacket.PART_MAX]).encode(part);
        assertEquals(7, DefinitionsSyncPacket.decode(part).part());
        // a new sync id in the middle of a sync is a failure, not a mix of two syncs
        List<DefinitionsSyncPacket> a = DefinitionsSync.plan(ofSize(DefinitionsSyncPacket.SINGLE_MAX + 1, false), 10).packets();
        List<DefinitionsSyncPacket> b = DefinitionsSync.plan(ofSize(DefinitionsSyncPacket.SINGLE_MAX + 1, false), 11).packets();
        SyncAssembler asm = new SyncAssembler();
        asm.accept(a.get(0));
        assertInstanceOf(SyncAssembler.Failed.class, asm.accept(b.get(1)));
    }
}
