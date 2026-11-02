package io.github.slugblue.wildroots.data.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.data.DefinitionLoader;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.io.ByteArrayInputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** WR-128, SEC-04, SEC-31: what the client gets, the split and the client decoder. */
class DefinitionsSyncTest {

    static List<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    /** Only the left column of architecture §8.3; no frequencies, elite loci, tradeoffs, budget, load, mutation, thresholds. */
    @Test
    void serialisedFormHasOnlyTheAllowedFields() {
        assertEquals(List.of("lines", "species", "cultivars"), components(SyncedDefinitions.class));
        assertEquals(List.of("id", "archetype", "translationKey", "displayTraits", "color", "lifeCycle"),
                components(SyncedDefinitions.Species.class));
        assertEquals(List.of("id", "species", "tier", "produceItem", "helpKey", "foodReference"),
                components(SyncedDefinitions.Cultivar.class));
        assertEquals(List.of("nutrition", "saturation"), components(SyncedDefinitions.FoodReference.class));
        assertEquals(List.of("syncId", "part", "total", "data"), components(DefinitionsSyncPacket.class));
    }

    static SyncedDefinitions carrot() {
        String species = MiniJson.resourceText("/species/wild_carrot.json");
        String cultivar = MiniJson.resourceText("/cultivars/carrot.json");
        DefinitionLoader.LoadResult r = DefinitionLoader.load(
                Map.of(Ids.mod("crop_species/wild_carrot.json"),
                        () -> new ByteArrayInputStream(species.getBytes(StandardCharsets.UTF_8))),
                Map.of(Ids.mod("cultivars/carrot.json"),
                        () -> new ByteArrayInputStream(cultivar.getBytes(StandardCharsets.UTF_8))));
        return SyncedDefinitions.of(r.snapshot(), List.of("carrot"));
    }

    @Test
    void carrotRoundTripsAndCarriesItsClientData() {
        SyncedDefinitions d = carrot();
        byte[] bytes = DefinitionsSyncPacket.serialise(d);
        assertEquals(d, SyncAssembler.decode(bytes).orElseThrow());
        SyncedDefinitions.Species s = d.species().get(0);
        assertEquals("wildroots.species.wild_carrot", s.translationKey());
        assertEquals("field", s.archetype());
        assertEquals(Map.of("fruit_size", "root_size", "flavor", "sweetness"), s.displayTraits());
        SyncedDefinitions.Cultivar c = d.cultivars().get(0);
        assertEquals(2, c.tier());
        assertEquals(Optional.of(new SyncedDefinitions.FoodReference(3, 0.6f)), c.foodReference());
        assertEquals("wildroots.cultivar.carrot.note", c.helpKey());
        assertTrue(bytes.length < 1024, "carrot sync is " + bytes.length + " B");
        assertEquals(1, DefinitionsSyncPacket.split(1, bytes).size());
    }

    static SyncedDefinitions synthetic(int n, int traitChars, String namespace) {
        List<SyncedDefinitions.Species> species = new ArrayList<>();
        List<SyncedDefinitions.Cultivar> cultivars = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, String> traits = new LinkedHashMap<>();
            for (int t = 0; t < 4; t++) {
                traits.put("trait_" + t + "_" + "x".repeat(traitChars), "name_" + t);
            }
            var id = Ids.of(namespace, "species_" + i);
            species.add(new SyncedDefinitions.Species(id, "field", "wildroots.species." + namespace + ".species_" + i,
                    traits, i % 0xFFFFFF, "annual"));
            cultivars.add(new SyncedDefinitions.Cultivar(Ids.of(namespace, "cultivar_" + i), id, 1,
                    Optional.of(Ids.mod("generic_produce")), "wildroots.cultivar.x.note",
                    Optional.of(new SyncedDefinitions.FoodReference(3, 0.6f))));
        }
        return new SyncedDefinitions(List.of("carrot"), species, cultivars);
    }

    @Test
    void twoThousandSpeciesAreSplitAndReassembled() {
        SyncedDefinitions big = synthetic(2000, 50, "pack");
        DefinitionsSync.Plan plan = DefinitionsSync.plan(big, 7);
        assertTrue(plan.bytes() > DefinitionsSyncPacket.SINGLE_MAX, "test data too small: " + plan.bytes());
        assertTrue(plan.packets().size() > 1);
        assertTrue(plan.dropped().isEmpty());
        SyncAssembler assembler = new SyncAssembler();
        SyncAssembler.Step last = null;
        for (DefinitionsSyncPacket p : plan.packets()) {
            assertTrue(p.data().length <= DefinitionsSyncPacket.PART_MAX);
            // through the wire format, as the client receives it
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            p.encode(buf);
            assertTrue(buf.readableBytes() <= 1_048_576, "S→C payload limit");
            last = assembler.accept(DefinitionsSyncPacket.decode(buf));
        }
        assertEquals(big, assertInstanceOf(SyncAssembler.Done.class, last).definitions());
    }

    @Test
    void overTwoMebibytesDropsDataPackSpeciesOnly() {
        SyncedDefinitions builtIn = synthetic(10, 10, "wildroots");
        SyncedDefinitions packs = synthetic(3000, 120, "pack");
        List<SyncedDefinitions.Species> species = new ArrayList<>(builtIn.species());
        species.addAll(packs.species());
        List<SyncedDefinitions.Cultivar> cultivars = new ArrayList<>(builtIn.cultivars());
        cultivars.addAll(packs.cultivars());
        SyncedDefinitions all = new SyncedDefinitions(List.of("carrot"), species, cultivars);
        assertTrue(DefinitionsSyncPacket.serialise(all).length > DefinitionsSyncPacket.TOTAL_MAX);

        DefinitionsSync.Plan plan = DefinitionsSync.plan(all, 9);
        assertTrue(plan.bytes() <= DefinitionsSyncPacket.TOTAL_MAX);
        assertFalse(plan.dropped().isEmpty());
        assertTrue(plan.dropped().stream().noneMatch(id -> id.getNamespace().equals("wildroots")));
        assertTrue(plan.packets().size() <= DefinitionsSyncPacket.MAX_PARTS);
        SyncAssembler assembler = new SyncAssembler();
        SyncAssembler.Step step = null;
        for (DefinitionsSyncPacket p : plan.packets()) {
            step = assembler.accept(p);
        }
        SyncedDefinitions got = assertInstanceOf(SyncAssembler.Done.class, step).definitions();
        assertTrue(got.species().containsAll(builtIn.species()));
        assertTrue(got.cultivars().stream().noneMatch(c -> plan.dropped().contains(c.species())));
    }

    @Test
    void brokenSequencesFailWithoutExceptions() {
        List<DefinitionsSyncPacket> parts = DefinitionsSync.plan(synthetic(2000, 50, "pack"), 3).packets();
        SyncAssembler a = new SyncAssembler();
        a.accept(parts.get(0));
        assertInstanceOf(SyncAssembler.Failed.class, a.accept(parts.get(2)), "gap");
        assertInstanceOf(SyncAssembler.Failed.class, a.accept(parts.get(1)), "part without a start");
        byte[] junk = new byte[100];
        Arrays.fill(junk, (byte) 0x7F);
        assertInstanceOf(SyncAssembler.Failed.class,
                new SyncAssembler().accept(new DefinitionsSyncPacket(1, 0, 1, junk)), "junk payload");
        byte[] ok = DefinitionsSyncPacket.serialise(carrot());
        byte[] extra = Arrays.copyOf(ok, ok.length + 1);
        assertInstanceOf(SyncAssembler.Failed.class,
                new SyncAssembler().accept(new DefinitionsSyncPacket(1, 0, 1, extra)), "extra bytes");
    }

    @Test
    void packetDecoderRejectsBadHeaders() {
        for (int[] h : new int[][] {{1, 0, 0}, {1, 0, 9}, {1, 3, 2}, {1, -1, 2}}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarInt(h[0]);
            buf.writeVarInt(h[1]);
            buf.writeVarInt(h[2]);
            buf.writeByteArray(new byte[1]);
            assertThrows(DecoderException.class, () -> DefinitionsSyncPacket.decode(buf), Arrays.toString(h));
        }
        FriendlyByteBuf tooBig = new FriendlyByteBuf(Unpooled.buffer());
        tooBig.writeVarInt(1);
        tooBig.writeVarInt(0);
        tooBig.writeVarInt(2);
        tooBig.writeVarInt(DefinitionsSyncPacket.PART_MAX + 1); // claimed length, checked before allocation
        assertThrows(DecoderException.class, () -> DefinitionsSyncPacket.decode(tooBig));
    }
}
