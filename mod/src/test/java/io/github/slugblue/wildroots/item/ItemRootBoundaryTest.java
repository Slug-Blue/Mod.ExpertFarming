package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.core.safety.NbtShapes;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * WR-135, WR-137, WR-138, SEC-09 boundaries (batch 3 test pass) of the lot,
 * mother-root and pouch roots: exact byte and depth limits, a wrong type
 * and a missing value for every key, versions 0/1/2/non-int, negative and
 * huge numbers, 4/5 fathers and donors, {@code lh} absent vs present and
 * equal. No decoder throws.
 */
class ItemRootBoundaryTest {

    static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> u = new HashSet<>(a);
        u.addAll(b);
        return u;
    }

    // ------------------------------------------------------------------ lot

    static LotNbt.Result lot(Tag t) {
        return assertDoesNotThrow(() -> LotNbt.read(t));
    }

    /** {@link LotNbtTest#typical()} that also carries {@code rs} (a mutant's first lot). */
    static CompoundTag rareLot() {
        LotStacks.Parent mother = new LotStacks.Parent(LotNbtTest.SP, Optional.of("wildroots:eastern_carrot"),
                LotNbtTest.genome(3), Optional.of("Golden Line"), 11, LotFlags.FROM_WILD);
        return LotStacks.seedsTag(mother, LotNbtTest.fathers(4), 0.35f, LotFlags.BEES, 0x1234L, () -> true);
    }

    static CompoundTag seedlingLot() {
        LotStacks.Parent p = new LotStacks.Parent(LotNbtTest.SP, Optional.empty(), LotNbtTest.genome(4),
                Optional.of("Line"), 2, LotFlags.FROM_WILD);
        return LotStacks.seedlingTag(p, new byte[] {1, 2, 3}, 0x55L);
    }

    @Test
    void lotExactlyAtTheByteLimitAndOneOver() {
        assertInstanceOf(LotNbt.Lot.class, lot(NbtShapes.padTo(rareLot(), NbtLimits.LOT_MAX_BYTES)));
        LotNbt.Inert in = assertInstanceOf(LotNbt.Inert.class, lot(NbtShapes.padTo(rareLot(), NbtLimits.LOT_MAX_BYTES + 1)));
        assertTrue(in.reason().startsWith("over limit"), in.reason());
        CompoundTag lazy = LotStacks.lazyTag(LotNbtTest.SP, "meadow", 1L, 2L, LotNbt.RareSource.NONE);
        assertInstanceOf(LotNbt.Lazy.class, lot(NbtShapes.padTo(lazy, NbtLimits.LOT_MAX_BYTES)));
        assertInstanceOf(LotNbt.Inert.class, lot(NbtShapes.padTo(lazy, NbtLimits.LOT_MAX_BYTES + 1)));
    }

    @Test
    void lotDepthFiveAndSix() {
        assertInstanceOf(LotNbt.Lot.class, lot(NbtShapes.nestTo(rareLot(), NbtLimits.LOT_MAX_DEPTH)));
        assertInstanceOf(LotNbt.Inert.class, lot(NbtShapes.nestTo(rareLot(), NbtLimits.LOT_MAX_DEPTH + 1)));
    }

    @Test
    void lotVersions() {
        for (CompoundTag base : List.of(rareLot(), LotStacks.lazyTag(LotNbtTest.SP, "meadow", 1L, 2L,
                LotNbt.RareSource.NONE))) {
            CompoundTag r = base.copy();
            r.putInt("v", 0);
            assertInstanceOf(LotNbt.Inert.class, lot(r), "v0");
            r.putInt("v", 1);
            assertFalse(lot(r) instanceof LotNbt.Inert, "v1");
            r.putInt("v", 2);
            LotNbt.Inert future = assertInstanceOf(LotNbt.Inert.class, lot(r), "v2");
            assertTrue(future.reason().contains("future"), future.reason());
            r.putInt("v", -5);
            assertInstanceOf(LotNbt.Inert.class, lot(r), "v-5");
            r.put("v", ByteTag.valueOf((byte) 1));
            assertInstanceOf(LotNbt.Inert.class, lot(r), "byte v");
            r.putString("v", "1");
            assertInstanceOf(LotNbt.Inert.class, lot(r), "string v");
            r.remove("v");
            assertInstanceOf(LotNbt.Inert.class, lot(r), "missing v");
        }
    }

    static final Set<String> LOT_STRICT = Set.of("v", "id", "var", "g", "f", "ln");
    /** Plain numeric or display keys: a wrong type reads as the default (rs → none, disp → nothing shown). */
    static final Set<String> LOT_LENIENT = Set.of("po", "gen", "fl", "rs", "disp");

    @Test
    void lotWrongTypeOfEveryKey() {
        CompoundTag base = rareLot();
        assertEquals(union(LOT_STRICT, LOT_LENIENT), base.getAllKeys(), "fixture covers every key");
        for (String k : base.getAllKeys()) {
            CompoundTag r = base.copy();
            r.put(k, NbtShapes.wrongType(r.get(k)));
            LotNbt.Result res = lot(r);
            if (LOT_STRICT.contains(k)) {
                assertInstanceOf(LotNbt.Inert.class, res, () -> "wrong-typed " + k + " accepted");
            } else {
                LotNbt.Lot l = assertInstanceOf(LotNbt.Lot.class, res, () -> "lenient " + k);
                if (k.equals("rs")) {
                    assertEquals(LotNbt.RareSource.NONE, l.rare(), "a wrong-typed rs never grants a flag");
                }
            }
            Tag w = NbtShapes.otherWidth(base.get(k));
            if (w != null) {
                CompoundTag n = base.copy();
                n.put(k, w);
                lot(n);
            }
        }
        // nested: the mother genome and a father
        for (String k : base.getCompound("g").getAllKeys()) {
            CompoundTag r = base.copy();
            r.getCompound("g").put(k, NbtShapes.wrongType(r.getCompound("g").get(k)));
            assertInstanceOf(LotNbt.Inert.class, lot(r), () -> "g." + k);
        }
        CompoundTag f0 = base.getList("f", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(Set.of("a", "w"), f0.getAllKeys(), "father fixture (lh of the mother is implied)");
        for (String k : List.of("a", "w", "lh")) {
            CompoundTag r = base.copy();
            CompoundTag f = r.getList("f", Tag.TAG_COMPOUND).getCompound(0);
            f.put(k, NbtShapes.wrongType(f.contains(k) ? f.get(k) : IntTag.valueOf(0)));
            assertInstanceOf(LotNbt.Inert.class, lot(r), () -> "f[0]." + k);
        }
        CompoundTag d = base.copy();
        d.getList("f", Tag.TAG_COMPOUND).getCompound(0).putString("d", "x");
        assertFalse(((LotNbt.Lot) lot(d)).fathers().get(0).donor(), "wrong-typed d is not a donor");
    }

    @Test
    void seedlingAndLazyWrongTypes() {
        CompoundTag s = seedlingLot();
        assertTrue(((LotNbt.Lot) lot(s)).seedling());
        CompoundTag e = s.copy();
        e.putString("e", "x");
        assertInstanceOf(LotNbt.Inert.class, lot(e), "seedling with wrong-typed noise");
        CompoundTag bigNoise = s.copy();
        bigNoise.putByteArray("e", new byte[NbtLimits.BLOCK_ENTITY_MAX_NOISE]);
        assertInstanceOf(LotNbt.Lot.class, lot(bigNoise), "48 noise bytes");
        bigNoise.putByteArray("e", new byte[NbtLimits.BLOCK_ENTITY_MAX_NOISE + 1]);
        assertInstanceOf(LotNbt.Inert.class, lot(bigNoise), "49 noise bytes");
        CompoundTag fx = s.copy();
        fx.putString("fx", "x");
        // lenient: a damaged fx reads as an ordinary lot (rolls offspring); it never plants an arbitrary genome
        assertFalse(((LotNbt.Lot) lot(fx)).seedling());

        CompoundTag lazy = LotStacks.lazyTag(LotNbtTest.SP, "meadow", 1L, 2L, LotNbt.RareSource.TRADE);
        for (String k : lazy.getCompound("tpl").getAllKeys()) {
            CompoundTag r = lazy.copy();
            r.getCompound("tpl").put(k, NbtShapes.wrongType(r.getCompound("tpl").get(k)));
            assertInstanceOf(LotNbt.Inert.class, lot(r), () -> "tpl." + k);
            CompoundTag m = lazy.copy();
            m.getCompound("tpl").remove(k);
            assertInstanceOf(LotNbt.Inert.class, lot(m), () -> "tpl without " + k);
        }
        CompoundTag t = lazy.copy();
        t.putString("tpl", "x");
        assertInstanceOf(LotNbt.Inert.class, lot(t));
        CompoundTag both = lazy.copy();
        both.put("g", rareLot().getCompound("g"));
        assertInstanceOf(LotNbt.Inert.class, lot(both), "template and genome at once");
        CompoundTag cat = lazy.copy();
        cat.getCompound("tpl").putString("cat", "c".repeat(LotNbt.MAX_CATEGORY));
        assertInstanceOf(LotNbt.Lazy.class, lot(cat));
        cat.getCompound("tpl").putString("cat", "c".repeat(LotNbt.MAX_CATEGORY + 1));
        assertInstanceOf(LotNbt.Inert.class, lot(cat));
    }

    @Test
    void lotMissingKeys() {
        Set<String> required = Set.of("v", "id", "g");
        CompoundTag base = rareLot();
        for (String k : base.getAllKeys()) {
            CompoundTag r = base.copy();
            r.remove(k);
            if (required.contains(k)) {
                assertInstanceOf(LotNbt.Inert.class, lot(r), () -> "lot without " + k);
            } else {
                assertInstanceOf(LotNbt.Lot.class, lot(r), () -> "lot without optional " + k);
            }
        }
        for (String k : base.getCompound("g").getAllKeys()) {
            CompoundTag r = base.copy();
            r.getCompound("g").remove(k);
            assertInstanceOf(LotNbt.Inert.class, lot(r), () -> "g without " + k);
        }
        CompoundTag noFathers = base.copy();
        noFathers.remove("f");
        assertTrue(((LotNbt.Lot) lot(noFathers)).fathers().isEmpty(), "absent f = self-pollinated");
    }

    @Test
    void lotNumbersNegativeAndHuge() {
        CompoundTag r = rareLot();
        r.putInt("gen", NbtLimits.LOT_MAX_GENERATION);
        assertInstanceOf(LotNbt.Lot.class, lot(r));
        for (int g : new int[] {NbtLimits.LOT_MAX_GENERATION + 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            r.putInt("gen", g);
            assertInstanceOf(LotNbt.Inert.class, lot(r), "gen " + g);
        }
        for (int rs = 0; rs < LotNbt.RareSource.values().length; rs++) {
            CompoundTag x = rareLot();
            x.putByte("rs", (byte) rs);
            assertEquals(LotNbt.RareSource.values()[rs], ((LotNbt.Lot) lot(x)).rare());
        }
        for (int rs : new int[] {-1, LotNbt.RareSource.values().length, 127}) {
            CompoundTag x = rareLot();
            x.putByte("rs", (byte) rs);
            assertInstanceOf(LotNbt.Inert.class, lot(x), "rs " + rs);
        }
        for (float po : new float[] {-0.0001f, 1.0001f, Float.NaN, Float.NEGATIVE_INFINITY}) {
            CompoundTag x = rareLot();
            x.putFloat("po", po);
            assertInstanceOf(LotNbt.Inert.class, lot(x), "po " + po);
        }
        for (long id : new long[] {0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            CompoundTag x = rareLot();
            x.putLong("id", id);
            assertEquals(id, ((LotNbt.Lot) lot(x)).lotId(), "any lotId value is an id");
        }
        CompoundTag a = rareLot();
        a.getCompound("g").put("a", new LongArrayTag(new long[NbtLimits.GENOME_MAX_LONGS]));
        assertInstanceOf(LotNbt.Lot.class, lot(a));
        a.getCompound("g").put("a", new LongArrayTag(new long[NbtLimits.GENOME_MAX_LONGS + 1]));
        assertInstanceOf(LotNbt.Inert.class, lot(a));
        a.getCompound("g").put("a", new LongArrayTag(new long[0]));
        assertInstanceOf(LotNbt.Inert.class, lot(a));
        CompoundTag ln = rareLot();
        ln.putString("ln", "\uD835\uDC00".repeat(LotNbt.MAX_LINE_NAME));
        assertInstanceOf(LotNbt.Lot.class, lot(ln), "50 code points");
        ln.putString("ln", "a".repeat(LotNbt.MAX_LINE_NAME + 1));
        assertInstanceOf(LotNbt.Inert.class, lot(ln), "51 code points");
    }

    static LotFathersNbt.Father father(int i, boolean donor) {
        return new LotFathersNbt.Father(new FatherRecord(LotNbtTest.LH, new long[] {i, ~i}), 1f, donor);
    }

    static CompoundTag lotWith(int fathers, int donors) {
        List<LotFathersNbt.Father> f = new ArrayList<>();
        for (int i = 0; i < fathers; i++) {
            f.add(father(i, false));
        }
        for (int i = 0; i < donors; i++) {
            f.add(father(50 + i, true));
        }
        CompoundTag r = rareLot();
        r.put("f", LotFathersNbt.encode(f, LotNbtTest.LH));
        return r;
    }

    @Test
    void lotFathersFourFiveDonorsFourFive() {
        assertEquals(8, ((LotNbt.Lot) lot(lotWith(4, 4))).fathers().size(), "4 + 4");
        assertInstanceOf(LotNbt.Inert.class, lot(lotWith(5, 0)), "5 fathers");
        assertInstanceOf(LotNbt.Inert.class, lot(lotWith(0, 5)), "5 donors");
        assertInstanceOf(LotNbt.Inert.class, lot(lotWith(5, 3)), "5 + 3");
        assertInstanceOf(LotNbt.Inert.class, lot(lotWith(3, 5)), "3 + 5");
        assertInstanceOf(LotNbt.Inert.class, lot(lotWith(5, 4)), "5 + 4");
        assertInstanceOf(LotNbt.Inert.class, lot(lotWith(4, 5)), "4 + 5");
        assertTrue(NbtLimits.lot(lotWith(4, 4)).ok(), "4 + 4 fits the lot budget");
    }

    @Test
    void lotLhAbsentVersusPresentAndEqual() {
        CompoundTag absent = lotWith(3, 1);
        CompoundTag equal = absent.copy();
        ListTag f = equal.getList("f", Tag.TAG_COMPOUND);
        for (int i = 0; i < f.size(); i++) {
            assertFalse(f.getCompound(i).contains("lh"));
            f.getCompound(i).putLong("lh", LotNbtTest.LH);
        }
        LotNbt.Lot a = (LotNbt.Lot) lot(absent);
        LotNbt.Lot e = (LotNbt.Lot) lot(equal);
        assertEquals(a.fathers(), e.fathers(), "present-and-equal lh = absent lh");
        assertEquals(absent.get("f"), LotFathersNbt.encode(e.fathers(), LotNbtTest.LH), "re-encoding drops it");
        CompoundTag other = absent.copy();
        other.getList("f", Tag.TAG_COMPOUND).getCompound(1).putLong("lh", 99L);
        assertEquals(99L, ((LotNbt.Lot) lot(other)).fathers().get(1).genome().lh());
    }

    // ------------------------------------------------------------------ mother root

    static Optional<MotherNbt.Mother> mother(Tag t) {
        return assertDoesNotThrow(() -> MotherNbt.read(t));
    }

    static CompoundTag fullMother() {
        return MotherNbt.write(new MotherNbt.Mother(LotNbtTest.genome(9), new byte[] {4, -4, 9},
                Optional.of("wildroots:eastern_carrot"), 1, true, Optional.of("Golden Line"), 7, LotFlags.FROM_WILD, 2,
                true));
    }

    @Test
    void motherAtTheByteLimitAndDepth() {
        assertTrue(mother(NbtShapes.padTo(fullMother(), NbtLimits.MOTHER_MAX_BYTES)).isPresent(), "512 B");
        assertTrue(mother(NbtShapes.padTo(fullMother(), NbtLimits.MOTHER_MAX_BYTES + 1)).isEmpty(), "513 B");
        assertTrue(mother(NbtShapes.nestTo(fullMother(), NbtLimits.MOTHER_MAX_DEPTH)).isPresent(), "depth 4");
        assertTrue(mother(NbtShapes.nestTo(fullMother(), NbtLimits.MOTHER_MAX_DEPTH + 1)).isEmpty(), "depth 5");
    }

    @Test
    void motherVersions() {
        CompoundTag r = fullMother();
        r.putInt("v", 0);
        assertTrue(mother(r).isEmpty(), "v0");
        r.putInt("v", 1);
        assertTrue(mother(r).isPresent(), "v1");
        r.putInt("v", 2);
        assertTrue(mother(r).isEmpty(), "v2");
        r.putInt("v", -1);
        assertTrue(mother(r).isEmpty(), "v-1");
        r.putString("v", "1");
        assertTrue(mother(r).isEmpty(), "string v");
        // lenient, unlike lot, plant and pouch: a numeric v of another width reads by its value
        r.put("v", ByteTag.valueOf((byte) 1));
        assertTrue(mother(r).isPresent(), "byte v 1");
        r.put("v", ByteTag.valueOf((byte) 2));
        assertTrue(mother(r).isEmpty(), "byte v 2");
        r.remove("v");
        assertTrue(mother(r).isEmpty(), "missing v");
    }

    static final Set<String> MOTHER_STRICT = Set.of("v", "g", "e", "var", "ln", "gr");
    static final Set<String> MOTHER_LENIENT = Set.of("ct", "ci", "gen", "fl", "bt");

    @Test
    void motherWrongTypeAndMissingKeys() {
        CompoundTag base = fullMother();
        assertEquals(union(MOTHER_STRICT, MOTHER_LENIENT), base.getAllKeys(), "fixture covers every key");
        for (String k : base.getAllKeys()) {
            CompoundTag r = base.copy();
            r.put(k, NbtShapes.wrongType(r.get(k)));
            if (MOTHER_STRICT.contains(k)) {
                assertTrue(mother(r).isEmpty(), () -> "wrong-typed " + k + " accepted");
            } else {
                assertTrue(mother(r).isPresent(), () -> "lenient " + k);
            }
            Tag w = NbtShapes.otherWidth(base.get(k));
            if (w != null) {
                CompoundTag n = base.copy();
                n.put(k, w);
                mother(n);
            }
        }
        for (String k : base.getCompound("g").getAllKeys()) {
            CompoundTag r = base.copy();
            r.getCompound("g").put(k, NbtShapes.wrongType(r.getCompound("g").get(k)));
            assertTrue(mother(r).isEmpty(), () -> "g." + k);
        }
        Set<String> required = Set.of("v", "g", "e", "gr");
        for (String k : base.getAllKeys()) {
            CompoundTag r = base.copy();
            r.remove(k);
            assertEquals(!required.contains(k), mother(r).isPresent(), () -> "mother without " + k);
        }
        CompoundTag noLn = base.copy();
        noLn.remove("ln");
        assertTrue(mother(noLn).orElseThrow().lineName().isEmpty());
    }

    @Test
    void motherNumbers() {
        for (int gr = 1; gr <= 4; gr++) {
            CompoundTag r = fullMother();
            r.putByte("gr", (byte) gr);
            r.remove("bt");
            assertEquals(gr, mother(r).orElseThrow().grade());
        }
        for (int gr : new int[] {0, 5, -1}) {
            CompoundTag r = fullMother();
            r.putByte("gr", (byte) gr);
            assertTrue(mother(r).isEmpty(), "grade " + gr);
        }
        for (int t : new int[] {-1, 3}) {
            CompoundTag r = fullMother();
            r.putByte("ct", (byte) t);
            assertTrue(mother(r).isEmpty(), "tier " + t);
        }
        for (int g : new int[] {-1, NbtLimits.LOT_MAX_GENERATION + 1}) {
            CompoundTag r = fullMother();
            r.putInt("gen", g);
            assertTrue(mother(r).isEmpty(), "gen " + g);
        }
        CompoundTag e = fullMother();
        e.putByteArray("e", new byte[NbtLimits.BLOCK_ENTITY_MAX_NOISE]);
        assertTrue(mother(e).isPresent(), "48 noise bytes");
        e.putByteArray("e", new byte[NbtLimits.BLOCK_ENTITY_MAX_NOISE + 1]);
        assertTrue(mother(e).isEmpty(), "49 noise bytes");
        CompoundTag ln = fullMother();
        ln.putString("ln", "a".repeat(LotNbt.MAX_LINE_NAME + 1));
        assertTrue(mother(ln).isEmpty(), "51 code points");
        // a bitter mother above grade 2 is normalised on write and read back as written
        CompoundTag bitter = MotherNbt.write(new MotherNbt.Mother(LotNbtTest.genome(9), new byte[0], Optional.empty(), 0,
                false, Optional.empty(), 0, 0, 4, true));
        assertEquals(ProduceStacks.BITTER_MAX_GRADE, mother(bitter).orElseThrow().grade());
    }

    // ------------------------------------------------------------------ pouch

    static Optional<PouchContents> pouch(Tag t) {
        return assertDoesNotThrow(() -> PouchContents.read(t));
    }

    static CompoundTag entry(Tag l) {
        CompoundTag e = new CompoundTag();
        e.put("l", l);
        e.putByte("c", (byte) 1);
        return e;
    }

    static CompoundTag pouchOf(int entries) {
        ListTag e = new ListTag();
        for (int i = 0; i < entries; i++) {
            CompoundTag l = new CompoundTag();
            l.putLong("id", i);
            e.add(entry(l));
        }
        CompoundTag c = new CompoundTag();
        c.putInt("v", 1);
        c.put("e", e);
        return c;
    }

    @Test
    void pouchSixteenEntriesAndSeventeen() {
        assertEquals(16, pouch(pouchOf(16)).orElseThrow().used());
        assertTrue(pouch(pouchOf(17)).isEmpty(), "17 entries read");
        assertTrue(pouch(pouchOf(1000)).isEmpty(), "1000 entries read");
        assertEquals(0, pouch(pouchOf(16)).orElseThrow().free());
        assertTrue(pouch(pouchOf(16)).orElseThrow().insert(LotNbtTest.typical(), 1).isEmpty());
    }

    @Test
    void pouchAtTheByteLimitAndDepth() {
        assertTrue(pouch(NbtShapes.padTo(pouchOf(3), NbtLimits.POUCH_MAX_BYTES)).isPresent(), "17 KiB");
        assertTrue(pouch(NbtShapes.padTo(pouchOf(3), NbtLimits.POUCH_MAX_BYTES + 1)).isEmpty(), "17 KiB + 1");
        assertTrue(pouch(NbtShapes.nestTo(pouchOf(3), NbtLimits.POUCH_MAX_DEPTH)).isPresent(), "depth 7");
        assertTrue(pouch(NbtShapes.nestTo(pouchOf(3), NbtLimits.POUCH_MAX_DEPTH + 1)).isEmpty(), "depth 8");
        // a pouch exactly at the limit refuses any insertion but still gives its entries back
        PouchContents full = pouch(NbtShapes.padTo(pouchOf(3), NbtLimits.POUCH_MAX_BYTES)).orElseThrow();
        assertTrue(full.insert(LotNbtTest.typical(), 1).isEmpty());
        assertEquals(2L, full.removeLast().orElseThrow().lot().getLong("id"));
    }

    @Test
    void pouchVersionsAndWrongTypes() {
        CompoundTag r = pouchOf(2);
        Map<Tag, Boolean> versions = Map.of(IntTag.valueOf(0), false, IntTag.valueOf(1), true, IntTag.valueOf(2), false,
                IntTag.valueOf(-1), false, ByteTag.valueOf((byte) 1), false, StringTag.valueOf("1"), false);
        versions.forEach((v, ok) -> {
            CompoundTag x = r.copy();
            x.put("v", v);
            assertEquals(ok, pouch(x).isPresent(), () -> "v " + v);
        });
        CompoundTag noV = r.copy();
        noV.remove("v");
        assertEquals(2, pouch(noV).orElseThrow().used(), "a pouch without v is a v1 pouch");
        CompoundTag noE = r.copy();
        noE.remove("e");
        assertEquals(0, pouch(noE).orElseThrow().used());
        CompoundTag e = r.copy();
        e.putString("e", "x");
        assertTrue(pouch(e).isEmpty(), "string e");
        ListTag ints = new ListTag();
        ints.add(IntTag.valueOf(1));
        e.put("e", ints);
        assertTrue(pouch(e).isEmpty(), "list of ints");
        e.put("e", new ListTag());
        assertEquals(0, pouch(e).orElseThrow().used(), "empty list");
        CompoundTag l = r.copy();
        l.getList("e", Tag.TAG_COMPOUND).getCompound(0).putString("l", "x");
        assertTrue(pouch(l).isEmpty(), "string lot root");
        for (Tag c : List.of(StringTag.valueOf("x"), ByteTag.valueOf((byte) 2), ByteTag.valueOf((byte) 0))) {
            CompoundTag x = r.copy();
            x.getList("e", Tag.TAG_COMPOUND).getCompound(0).put("c", c);
            assertTrue(pouch(x).isEmpty(), () -> "c " + c);
        }
        CompoundTag noC = r.copy();
        noC.getList("e", Tag.TAG_COMPOUND).getCompound(0).remove("c");
        assertTrue(pouch(noC).isEmpty(), "entry without c");
        CompoundTag noL = r.copy();
        noL.getList("e", Tag.TAG_COMPOUND).getCompound(0).remove("l");
        assertTrue(pouch(noL).isEmpty(), "entry without l");
        assertTrue(pouch(StringTag.valueOf("x")).isEmpty(), "not a compound");
        assertEquals(0, pouch(null).orElseThrow().used(), "no tag = empty pouch");
    }

    @Test
    void pouchInsertCounts() {
        CompoundTag l = LotNbtTest.typical();
        assertTrue(PouchContents.EMPTY.insert(l, 0).isEmpty(), "0 seeds");
        assertTrue(PouchContents.EMPTY.insert(l, -5).isEmpty(), "negative count");
        assertTrue(PouchContents.EMPTY.insert(l, Integer.MIN_VALUE).isEmpty(), "min int");
        assertTrue(PouchContents.EMPTY.insert(null, 3).isEmpty(), "no lot");
        assertEquals(16, PouchContents.EMPTY.insert(l, Integer.MAX_VALUE).orElseThrow().moved(), "huge count");
        assertEquals(16, PouchContents.EMPTY.insert(l, 127).orElseThrow().moved(), "127 (byte count of a stack)");
        PouchContents fifteen = PouchContents.EMPTY.insert(l, 15).orElseThrow().pouch();
        assertEquals(1, fifteen.insert(l, 64).orElseThrow().moved());
        assertTrue(PouchContents.EMPTY.removeLast().isEmpty());
    }
}
