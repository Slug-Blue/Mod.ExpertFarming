package io.github.slugblue.wildroots.oldsave;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.data.CarrotLineDataTestAccess;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.codec.DecodeResult;
import io.github.slugblue.wildroots.genetics.codec.GenomeCodec;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.item.LotFlags;
import io.github.slugblue.wildroots.item.LotNbt;
import io.github.slugblue.wildroots.item.MotherNbt;
import io.github.slugblue.wildroots.item.PouchContents;
import io.github.slugblue.wildroots.plant.Passport;
import io.github.slugblue.wildroots.plant.PlantNbt;
import io.github.slugblue.wildroots.plant.PlantState;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

/**
 * WR-196, docs/05-architecture.md §5.4 item 2: the released v1 format of the
 * lot, the pouch, the plant block entity root and the mother root, frozen as
 * SNBT fixtures ({@code fixtures/v1/*.snbt}), passes the mod's NBT codecs and
 * equals its golden values; genomes decode with the shipped wild carrot
 * layout. The journal v1 fixture is {@code JournalFixtureTest}. These files
 * and the GameTest template {@code old_save_v1.nbt} built from them are never
 * regenerated or deleted: a later format adds {@code fixtures/v2}, and v1
 * must keep loading (migrating) forever.
 */
class OldSaveV1Test {

    static final String SP = "wildroots:wild_carrot";
    static final String EASTERN = "wildroots:eastern_carrot";
    /** Layout hash of the shipped wild carrot when v1 was frozen. */
    static final long LH_V1 = -3539622408563365149L;

    /** The committed GameTest template (relative to the {@code :mod} project directory). */
    static final Path TEMPLATE = Path.of("src/gametest/resources/data/wildroots/structures/gametest/old_save_v1.nbt");
    /** sha256 of {@link #TEMPLATE}: the template is frozen, any change or deletion fails here. */
    static final String TEMPLATE_SHA256 = "e4b9e83ead5338f252b1762c07797e01b3dcc06b35bf48a53e7fdc5fd685121e";

    static CompoundTag fixture(String name) throws Exception {
        try (InputStream in = OldSaveV1Test.class.getResourceAsStream("/fixtures/v1/" + name)) {
            if (in == null) {
                throw new IOException("missing fixture " + name);
            }
            return TagParser.parseTag(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static GenomeLayout layout() {
        SpeciesDef def = CarrotLineDataTestAccess.wildCarrot();
        return CarrotLineDataTestAccess.snapshot().genetics().layout(def).orElseThrow();
    }

    /** Decodes {@code r} with the shipped layout and checks it against the golden bits. */
    static void assertGenome(GenomeRecord r, long... golden) {
        assertEquals(1, r.v());
        assertEquals(SP, r.species());
        assertEquals(LH_V1, r.lh());
        assertArrayEquals(golden, r.a());
        DecodeResult d = GenomeCodec.decode(r, SP, layout());
        DecodeResult.Valid v = assertInstanceOf(DecodeResult.Valid.class, d, d::toString);
        assertArrayEquals(golden, v.genome().bits(), "decoded genome lost bits");
    }

    @Test
    void lotV1() throws Exception {
        CompoundTag t = fixture("lot_v1.snbt");
        LotNbt.Lot lot = assertInstanceOf(LotNbt.Lot.class, LotNbt.read(t), () -> LotNbt.read(t).toString());
        assertEquals(0x1234_5678_9ABCL, lot.lotId());
        assertEquals(SP, lot.species());
        assertEquals(Optional.of(EASTERN), lot.cultivar());
        assertEquals(12, lot.generation());
        assertEquals(Optional.of("Golden Line"), lot.lineName());
        assertEquals(LotFlags.FROM_WILD | LotFlags.BEES, lot.flags());
        assertEquals(0.35f, lot.pOut());
        assertEquals(LotNbt.RareSource.NONE, lot.rare());
        assertGenome(lot.mother(), 76588965013356677L, 2954455936510461254L, 562L);
        assertEquals(4, lot.fathers().size());
        long[][] fathers = {
            {4684113345127665744L, 960781300351898696L, 3076L},
            {74753682486722901L, 2883588361817227338L, 536L},
            {4611844348120223765L, 874020486378422357L, 266L},
            {1538139252045649025L, 1342072691658596368L, 4145L},
        };
        for (int i = 0; i < 4; i++) {
            var f = lot.fathers().get(i);
            assertEquals(1f + i, f.weight());
            assertEquals(i == 3, f.donor(), "donor flag of father " + i);
            assertEquals(LH_V1, f.genome().lh(), "father lh comes from the mother");
            assertArrayEquals(fathers[i], f.genome().a());
            DecodeResult d = GenomeCodec.decodeFather(f.genome(), 1, SP, layout());
            assertInstanceOf(DecodeResult.Valid.class, d, d::toString);
        }
        assertEquals(5, LotNbt.disp(t).size());
    }

    @Test
    void pouchV1GivesItsEntriesLastInFirstOut() throws Exception {
        CompoundTag t = fixture("pouch_v1.snbt");
        PouchContents p = PouchContents.read(t).orElseThrow();
        assertEquals(6, p.used());
        assertEquals(t, p.write(), "save changed the v1 pouch");
        List<Long> out = new ArrayList<>();
        while (p.used() > 0) {
            PouchContents.Extracted e = p.removeLast().orElseThrow();
            assertInstanceOf(LotNbt.Lot.class, LotNbt.read(e.lot()));
            out.add(e.lot().getLong("id"));
            p = e.pouch();
        }
        assertEquals(List.of(1003L, 1003L, 1003L, 1002L, 1002L, 1001L), out);
    }

    @Test
    void plantBlockEntityV1() throws Exception {
        CompoundTag t = fixture("plant_v1.snbt");
        PlantNbt.Ok ok = assertInstanceOf(PlantNbt.Ok.class, PlantNbt.read(t), () -> PlantNbt.read(t).toString());
        PlantState s = ok.state();
        assertEquals(SP, s.species());
        assertGenome(s.genome(), 4992702343843299669L, -4236302851338009576L, 24L);
        assertArrayEquals(new byte[] {-7, -6, -5, -5, 11, -4, -50}, s.noise());
        assertEquals(new UUID(1, 2), s.planter());
        assertEquals(Passport.PLAYER, s.passport());
        assertEquals(EASTERN, s.cultivar());
        assertEquals(1, s.tier());
        assertFalse(s.inherited());
        assertEquals("Golden Line", s.lineName());
        assertEquals(13, s.generation());
        assertEquals(LotFlags.FROM_WILD, s.lotFlags());
        assertTrue(s.has(PlantState.MOTHER));
        assertEquals(2, s.pending().fathers().size());
        assertEquals(0.5f, s.pending().pOut());
        assertTrue(s.pending().bees());
        assertEquals(t, PlantNbt.write(s), "save changed the v1 plant");
    }

    @Test
    void lazyWildPlantV1KeepsOnlyItsPassport() throws Exception {
        CompoundTag t = fixture("plant_wild_v1.snbt");
        PlantState s = assertInstanceOf(PlantNbt.Ok.class, PlantNbt.read(t)).state();
        assertNull(s.genome(), "the wild genome is realised on first access, not stored");
        assertEquals(Passport.Origin.WILD, s.passport().origin());
        assertEquals("meadow", s.passport().category());
        assertEquals(123456789L, s.passport().patchOrigin());
        assertEquals(t, PlantNbt.write(s), "save changed the v1 wild plant");
    }

    @Test
    void motherRootV1() throws Exception {
        CompoundTag t = fixture("mother_v1.snbt");
        MotherNbt.Mother m = MotherNbt.read(t).orElseThrow();
        assertGenome(m.genome(), 5843509598464786513L, -8930638058306006902L, 49L);
        assertArrayEquals(new byte[] {-10, 0, 6, 21, -20, 9, -36}, m.noise());
        assertEquals(Optional.of(EASTERN), m.cultivar());
        assertEquals(1, m.tier());
        assertEquals(Optional.of("Golden Line"), m.lineName());
        assertEquals(12, m.generation());
        assertEquals(LotFlags.FROM_WILD, m.flags());
        assertEquals(3, m.grade());
        assertFalse(m.bitter());
        assertEquals(t, MotherNbt.write(m), "save changed the v1 mother root");
    }

    /** The frozen template is still there and byte for byte the one committed with WR-196. */
    @Test
    void oldSaveTemplateIsFrozen() throws Exception {
        assertTrue(Files.isRegularFile(TEMPLATE), "old_save_v1.nbt was deleted; templates of released versions stay forever");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(TEMPLATE)));
        assertEquals(TEMPLATE_SHA256, sha, "old_save_v1.nbt changed; a released template is never regenerated");
    }
}
