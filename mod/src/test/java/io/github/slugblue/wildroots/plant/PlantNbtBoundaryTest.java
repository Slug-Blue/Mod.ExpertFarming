package io.github.slugblue.wildroots.plant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.core.safety.NbtShapes;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * WR-131, SEC-09 boundaries of the plant root (batch 3 test pass): exact
 * byte and depth limits, a wrong type and a missing value for every key,
 * the version, generation, tier and the 4 + 4 fathers of the pending lot.
 * The decoder never throws; a key it checks gives an inert root, a plain
 * numeric key falls back to its default (documented below as lenient).
 */
class PlantNbtBoundaryTest {

    static CompoundTag full() {
        return PlantNbt.write(PlantNbtTest.full());
    }

    static PlantNbt.Result read(Tag t) {
        return assertDoesNotThrow(() -> PlantNbt.read(t));
    }

    @Test
    void exactlyTheByteLimitIsAcceptedOneMoreIsInert() {
        CompoundTag at = NbtShapes.padTo(full(), NbtLimits.BLOCK_ENTITY_MAX_BYTES);
        assertInstanceOf(PlantNbt.Ok.class, read(at));
        CompoundTag over = NbtShapes.padTo(full(), NbtLimits.BLOCK_ENTITY_MAX_BYTES + 1);
        PlantNbt.Inert in = assertInstanceOf(PlantNbt.Inert.class, read(over));
        assertTrue(in.reason().startsWith("over limit"), in.reason());
        assertFalse(in.future());
    }

    @Test
    void depthSixIsAcceptedSevenIsInert() {
        assertInstanceOf(PlantNbt.Ok.class, read(NbtShapes.nestTo(full(), NbtLimits.BLOCK_ENTITY_MAX_DEPTH)));
        assertInstanceOf(PlantNbt.Inert.class, read(NbtShapes.nestTo(full(), NbtLimits.BLOCK_ENTITY_MAX_DEPTH + 1)));
    }

    @Test
    void versionZeroOneTwoAndNonInt() {
        CompoundTag r = full();
        r.putInt("v", 0);
        assertFalse(assertInstanceOf(PlantNbt.Inert.class, read(r)).future(), "v0");
        r.putInt("v", -1);
        assertFalse(assertInstanceOf(PlantNbt.Inert.class, read(r)).future(), "v-1");
        r.putInt("v", 1);
        assertInstanceOf(PlantNbt.Ok.class, read(r));
        r.putInt("v", 2);
        assertTrue(assertInstanceOf(PlantNbt.Inert.class, read(r)).future(), "v2 is from the future");
        r.putInt("v", Integer.MAX_VALUE);
        assertTrue(assertInstanceOf(PlantNbt.Inert.class, read(r)).future());
        r.put("v", ByteTag.valueOf((byte) 1));
        assertFalse(assertInstanceOf(PlantNbt.Inert.class, read(r)).future(), "byte v");
        r.putString("v", "1");
        assertFalse(assertInstanceOf(PlantNbt.Inert.class, read(r)).future(), "string v");
        r.remove("v");
        assertInstanceOf(PlantNbt.Inert.class, read(r), "missing v");
    }

    /** Keys whose wrong type makes the root inert; the others read their default (lenient numeric reads). */
    static final Set<String> STRICT_TOP = Set.of("v", "sp", "g", "e", "pp", "um", "ul", "cv", "ln", "pl");
    static final Set<String> LENIENT_TOP = Set.of("ct", "ci", "gen", "fl", "pf");

    @Test
    void wrongTypeOfEveryTopLevelKey() {
        CompoundTag base = full();
        assertEquals(base.getAllKeys(), union(STRICT_TOP, LENIENT_TOP), "fixture covers every key");
        for (String k : base.getAllKeys()) {
            CompoundTag r = base.copy();
            r.put(k, NbtShapes.wrongType(r.get(k)));
            PlantNbt.Result res = read(r);
            if (STRICT_TOP.contains(k)) {
                assertInstanceOf(PlantNbt.Inert.class, res, () -> "wrong-typed " + k + " accepted");
            } else {
                PlantNbt.Ok ok = assertInstanceOf(PlantNbt.Ok.class, res, () -> "lenient " + k);
                // the default is a valid plant that writes and reads back the same
                assertEquals(PlantNbt.write(ok.state()), PlantNbt.write(((PlantNbt.Ok) read(PlantNbt.write(ok.state())))
                        .state()), k);
            }
            Tag w = NbtShapes.otherWidth(base.get(k));
            if (w != null) {
                CompoundTag n = base.copy();
                n.put(k, w);
                read(n); // never throws, whatever it decides
            }
        }
    }

    @Test
    void wrongTypeOfEveryNestedKey() {
        Map<String, Set<String>> strict = Map.of(
                "g", Set.of("v", "sp", "lh", "a"),
                "pp", Set.of("c"),
                "pl", Set.of("f"));
        Map<String, Set<String>> lenient = Map.of(
                "g", Set.of(),
                "pp", Set.of("o", "at", "r"),
                "pl", Set.of("po", "br", "bee"));
        CompoundTag base = full();
        for (String parent : strict.keySet()) {
            CompoundTag p = base.getCompound(parent);
            assertEquals(p.getAllKeys(), union(strict.get(parent), lenient.get(parent)), parent + " fixture");
            for (String k : p.getAllKeys()) {
                CompoundTag r = base.copy();
                r.getCompound(parent).put(k, NbtShapes.wrongType(p.get(k)));
                PlantNbt.Result res = read(r);
                if (strict.get(parent).contains(k)) {
                    assertInstanceOf(PlantNbt.Inert.class, res, () -> parent + "." + k + " wrong type accepted");
                } else {
                    assertInstanceOf(PlantNbt.Ok.class, res, () -> parent + "." + k + " lenient");
                }
            }
        }
        // passport keys the fixture does not write: landrace and harvest count
        CompoundTag lr = base.copy();
        lr.getCompound("pp").putInt("lr", 1);
        assertInstanceOf(PlantNbt.Inert.class, read(lr), "pp.lr wrong type");
        CompoundTag hc = base.copy();
        hc.getCompound("pp").putInt("hc", -1);
        assertInstanceOf(PlantNbt.Inert.class, read(hc), "negative harvest count");
        hc.getCompound("pp").putInt("hc", Integer.MAX_VALUE);
        assertInstanceOf(PlantNbt.Ok.class, read(hc), "huge harvest count");
        CompoundTag origin = base.copy();
        origin.getCompound("pp").putByte("o", (byte) 3);
        assertInstanceOf(PlantNbt.Inert.class, read(origin), "unknown origin");
        origin.getCompound("pp").putByte("o", (byte) -1);
        assertInstanceOf(PlantNbt.Inert.class, read(origin), "negative origin");
    }

    @Test
    void missingKeys() {
        Set<String> inertWithout = Set.of("v", "sp", "g", "e", "um", "ul");
        CompoundTag base = full();
        for (String k : base.getAllKeys()) {
            CompoundTag r = base.copy();
            r.remove(k);
            PlantNbt.Result res = read(r);
            if (inertWithout.contains(k)) {
                assertInstanceOf(PlantNbt.Inert.class, res, () -> "root without " + k + " accepted");
            } else {
                assertInstanceOf(PlantNbt.Ok.class, res, () -> "root without optional " + k);
            }
        }
        CompoundTag noPassport = base.copy();
        noPassport.remove("pp");
        assertEquals(Passport.PLAYER, ((PlantNbt.Ok) read(noPassport)).state().passport, "absent passport = player");
        CompoundTag noPending = base.copy();
        noPending.remove("pl");
        assertNull(((PlantNbt.Ok) read(noPending)).state().pending);
        // a wild plant without genome is fine, a player plant without genome is not
        CompoundTag wild = base.copy();
        wild.remove("g");
        wild.remove("e");
        wild.remove("pl");
        assertInstanceOf(PlantNbt.Ok.class, read(wild), "untouched wild plant");
        wild.remove("pp");
        assertInstanceOf(PlantNbt.Inert.class, read(wild), "player plant without genome");
    }

    @Test
    void generationTierAndNoiseBounds() {
        CompoundTag r = full();
        r.putInt("gen", NbtLimits.LOT_MAX_GENERATION);
        assertInstanceOf(PlantNbt.Ok.class, read(r));
        r.putInt("gen", NbtLimits.LOT_MAX_GENERATION + 1);
        assertInstanceOf(PlantNbt.Inert.class, read(r));
        r.putInt("gen", -1);
        assertInstanceOf(PlantNbt.Inert.class, read(r));
        r.putInt("gen", Integer.MIN_VALUE);
        assertInstanceOf(PlantNbt.Inert.class, read(r));
        r = full();
        for (int t : new int[] {0, 1, 2}) {
            r.putByte("ct", (byte) t);
            assertInstanceOf(PlantNbt.Ok.class, read(r), "tier " + t);
        }
        for (int t : new int[] {-1, 3, 127}) {
            r.putByte("ct", (byte) t);
            assertInstanceOf(PlantNbt.Inert.class, read(r), "tier " + t);
        }
        r = full();
        r.getCompound("g").put("a", new net.minecraft.nbt.LongArrayTag(new long[NbtLimits.GENOME_MAX_LONGS]));
        assertInstanceOf(PlantNbt.Ok.class, read(r), "8 longs");
        r.getCompound("g").put("a", new net.minecraft.nbt.LongArrayTag(new long[NbtLimits.GENOME_MAX_LONGS + 1]));
        assertInstanceOf(PlantNbt.Inert.class, read(r), "9 longs");
        r.getCompound("g").put("a", new net.minecraft.nbt.LongArrayTag(new long[0]));
        assertInstanceOf(PlantNbt.Inert.class, read(r), "empty genome");
        r = full();
        r.putString("sp", "wildroots:" + "s".repeat(NbtLimits.GENOME_MAX_SPECIES_LENGTH - 10));
        r.getCompound("g").putString("sp", r.getString("sp"));
        assertInstanceOf(PlantNbt.Ok.class, read(r), "species id of 128 characters");
        r.putString("sp", "wildroots:" + "s".repeat(NbtLimits.GENOME_MAX_SPECIES_LENGTH - 9));
        r.getCompound("g").putString("sp", r.getString("sp"));
        assertInstanceOf(PlantNbt.Inert.class, read(r), "species id of 129 characters");
        r = full();
        r.putString("ln", "\uD835\uDC00".repeat(PlantNbt.MAX_LINE_NAME));
        assertInstanceOf(PlantNbt.Ok.class, read(r), "50 code points (100 chars)");
        r.putString("ln", "a".repeat(PlantNbt.MAX_LINE_NAME + 1));
        assertInstanceOf(PlantNbt.Inert.class, read(r), "51 code points");
    }

    static LotFathersNbt.Father father(int i, boolean donor) {
        return new LotFathersNbt.Father(new FatherRecord(PlantNbtTest.LH, new long[] {i, i + 1}), 1f, donor);
    }

    static CompoundTag withPending(int fathers, int donors) {
        PlantState s = PlantNbtTest.full();
        List<LotFathersNbt.Father> f = new ArrayList<>();
        for (int i = 0; i < fathers; i++) {
            f.add(father(i, false));
        }
        for (int i = 0; i < donors; i++) {
            f.add(father(100 + i, true));
        }
        s.pending = new PendingLot(f, 0.5f, Brush.NONE, false);
        return PlantNbt.write(s);
    }

    @Test
    void pendingLotFathersFourAndFiveDonorsFourAndFive() {
        assertInstanceOf(PlantNbt.Ok.class, read(withPending(4, 4)), "4 + 4");
        assertInstanceOf(PlantNbt.Ok.class, read(withPending(4, 0)), "4 + 0");
        assertInstanceOf(PlantNbt.Ok.class, read(withPending(0, 4)), "0 + 4");
        assertInstanceOf(PlantNbt.Inert.class, read(withPending(5, 0)), "5 fathers");
        assertInstanceOf(PlantNbt.Inert.class, read(withPending(0, 5)), "5 donors");
        assertInstanceOf(PlantNbt.Inert.class, read(withPending(5, 3)), "5 + 3 (8 entries, too many fathers)");
        assertInstanceOf(PlantNbt.Inert.class, read(withPending(3, 5)), "3 + 5 (8 entries, too many donors)");
        assertInstanceOf(PlantNbt.Inert.class, read(withPending(5, 4)), "9 entries");
        CompoundTag empty = withPending(0, 0);
        assertInstanceOf(PlantNbt.Ok.class, read(empty), "selfed pending lot");
    }

    @Test
    void pendingLotScalarBounds() {
        for (float po : new float[] {0f, 1f, -0f}) {
            CompoundTag r = withPending(1, 0);
            r.getCompound("pl").putFloat("po", po);
            assertInstanceOf(PlantNbt.Ok.class, read(r), "po " + po);
        }
        for (float po : new float[] {-0.0001f, 1.0001f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            CompoundTag r = withPending(1, 0);
            r.getCompound("pl").putFloat("po", po);
            assertInstanceOf(PlantNbt.Inert.class, read(r), "po " + po);
        }
        for (int br = 0; br < Brush.values().length; br++) {
            CompoundTag r = withPending(1, 0);
            r.getCompound("pl").putByte("br", (byte) br);
            assertInstanceOf(PlantNbt.Ok.class, read(r), "brush " + br);
        }
        for (int br : new int[] {-1, Brush.values().length, 127}) {
            CompoundTag r = withPending(1, 0);
            r.getCompound("pl").putByte("br", (byte) br);
            assertInstanceOf(PlantNbt.Inert.class, read(r), "brush " + br);
        }
        CompoundTag weight = withPending(1, 0);
        weight.getCompound("pl").getList("f", Tag.TAG_COMPOUND).getCompound(0).putFloat("w", NbtLimits.LOT_MAX_FATHER_WEIGHT);
        assertInstanceOf(PlantNbt.Ok.class, read(weight), "weight 16");
        weight.getCompound("pl").getList("f", Tag.TAG_COMPOUND).getCompound(0).putFloat("w", Math.nextUp(16f));
        assertInstanceOf(PlantNbt.Inert.class, read(weight), "weight over 16");
        weight.getCompound("pl").getList("f", Tag.TAG_COMPOUND).getCompound(0).putFloat("w", -1f);
        assertInstanceOf(PlantNbt.Inert.class, read(weight), "negative weight");
        // a pending lot without a genome is impossible
        CompoundTag noGenome = withPending(1, 0);
        noGenome.remove("g");
        noGenome.remove("e");
        assertInstanceOf(PlantNbt.Inert.class, read(noGenome));
        // a list of the wrong element type
        CompoundTag ints = withPending(1, 0);
        ListTag l = new ListTag();
        l.add(IntTag.valueOf(1));
        ints.getCompound("pl").put("f", l);
        assertInstanceOf(PlantNbt.Inert.class, read(ints));
        CompoundTag floats = withPending(1, 0);
        floats.getCompound("pl").put("po", FloatTag.valueOf(0.25f));
        assertEquals(0.25f, ((PlantNbt.Ok) read(floats)).state().pending.pOut());
    }

    @Test
    void lhAbsentAndPresentButEqualDecodeAlikeAndWriteNormalises() {
        CompoundTag absent = withPending(2, 1);
        CompoundTag equal = absent.copy();
        ListTag f = equal.getCompound("pl").getList("f", Tag.TAG_COMPOUND);
        for (int i = 0; i < f.size(); i++) {
            assertFalse(f.getCompound(i).contains("lh"), "the mother's lh is never written");
            f.getCompound(i).putLong("lh", PlantNbtTest.LH);
        }
        PlantState a = ((PlantNbt.Ok) read(absent)).state();
        PlantState e = ((PlantNbt.Ok) read(equal)).state();
        assertEquals(a.pending, e.pending, "present-and-equal lh decodes like an absent lh");
        assertEquals(absent, PlantNbt.write(e), "write drops the redundant lh");
        CompoundTag other = absent.copy();
        other.getCompound("pl").getList("f", Tag.TAG_COMPOUND).getCompound(0).putLong("lh", PlantNbtTest.LH + 1);
        PlantState o = ((PlantNbt.Ok) read(other)).state();
        assertEquals(PlantNbtTest.LH + 1, o.pending.fathers().get(0).genome().lh());
        assertEquals(other, PlantNbt.write(o), "a different lh is kept");
    }

    @Test
    void noiseWithoutGenomeAndGenomeOfAnotherSpecies() {
        CompoundTag r = full();
        r.remove("g");
        r.remove("pl");
        assertInstanceOf(PlantNbt.Inert.class, read(r), "noise without genome");
        CompoundTag s = full();
        s.put("e", StringTag.valueOf("x"));
        assertInstanceOf(PlantNbt.Inert.class, read(s));
    }

    static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> u = new java.util.HashSet<>(a);
        u.addAll(b);
        return u;
    }

    /** Review 1, m1: a stored line name must be clean player text. */
    @org.junit.jupiter.api.Test
    void dirtyLineNameMakesThePlantInert() {
        for (String ln : new String[] {"@cultivar.carrot", "\u0001cultivar.carrot", "a\u202Eb"}) {
            CompoundTag r = full();
            r.putString("ln", ln);
            assertInstanceOf(PlantNbt.Inert.class, read(r), ln);
        }
    }
}
