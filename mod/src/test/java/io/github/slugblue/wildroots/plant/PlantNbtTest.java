package io.github.slugblue.wildroots.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.NbtLimits;
import io.github.slugblue.wildroots.genetics.codec.FatherRecord;
import io.github.slugblue.wildroots.genetics.codec.GenomeRecord;
import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/** WR-131: NBT of the plant block entity (docs/05-architecture.md §6.1; SEC-09). */
class PlantNbtTest {

    static final String SP = "wildroots:wild_carrot";
    static final long LH = 0x0123_4567_89AB_CDEFL;

    static PlantState full() {
        PlantState s = new PlantState();
        s.species = SP;
        s.genome = new GenomeRecord(1, SP, LH, new long[] {1, 2, 3});
        s.noise = new byte[] {1, -2, 3, -4, 5, -6, 7};
        s.passport = Passport.wild(42L, "meadow", true, false);
        UUID u = new UUID(7, 9);
        s.planterMost = u.getMostSignificantBits();
        s.planterLeast = u.getLeastSignificantBits();
        s.cultivar = "wildroots:eastern_carrot";
        s.tier = 1;
        s.inherited = true;
        s.lineName = "Golden Line";
        s.generation = 12;
        s.lotFlags = 1;
        s.plantFlags = PlantState.MOTHER;
        List<LotFathersNbt.Father> f = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            f.add(new LotFathersNbt.Father(new FatherRecord(LH, new long[] {i, i + 1, i + 2}), 1 + i, false));
        }
        for (int i = 0; i < 4; i++) {
            f.add(new LotFathersNbt.Father(new FatherRecord(LH + i, new long[] {i, i, i}), 2f, true));
        }
        s.pending = new PendingLot(f, 0.6f, Brush.CROSS, true);
        return s;
    }

    @Test
    void roundTripKeepsEveryField() {
        PlantState s = full();
        CompoundTag root = PlantNbt.write(s);
        PlantNbt.Ok ok = assertInstanceOf(PlantNbt.Ok.class, PlantNbt.read(root));
        assertEquals(root, PlantNbt.write(ok.state()), "write(read(x)) == x");
        assertEquals(new UUID(7, 9), ok.state().planter());
        assertEquals(8, ok.state().pending().fathers().size());
        assertTrue(NbtLimits.blockEntity(root).ok(), () -> NbtLimits.blockEntity(root).toString());
        assertTrue(NbtLimits.blockEntity(root).bytes() <= 2048);
    }

    @Test
    void noiseLimitIs48Bytes() {
        CompoundTag root = PlantNbt.write(full());
        root.put("e", new ByteArrayTag(new byte[48]));
        assertInstanceOf(PlantNbt.Ok.class, PlantNbt.read(root));
        root.put("e", new ByteArrayTag(new byte[49]));
        assertInstanceOf(PlantNbt.Inert.class, PlantNbt.read(root));
    }

    @Test
    void wildPlantKeepsOnlyThePassport() {
        PlantState s = new PlantState();
        s.species = SP;
        s.passport = Passport.wild(42L, "meadow", false, true);
        CompoundTag root = PlantNbt.write(s);
        assertEquals(java.util.Set.of("v", "sp", "pp"), root.getAllKeys());
        assertTrue(NbtLimits.blockEntity(root).bytes() <= 96, () -> "passport root " + NbtLimits.blockEntity(root));
        PlantNbt.Ok ok = assertInstanceOf(PlantNbt.Ok.class, PlantNbt.read(root));
        assertEquals(s.passport, ok.state().passport());
    }

    @Test
    void futureVersionIsInertAndFlagged() {
        CompoundTag root = PlantNbt.write(full());
        root.putInt("v", PlantNbt.CURRENT + 1);
        PlantNbt.Inert in = assertInstanceOf(PlantNbt.Inert.class, PlantNbt.read(root));
        assertTrue(in.future());
    }

    @Test
    void oversizedOrDamagedRootsAreInert() {
        CompoundTag big = PlantNbt.write(full());
        big.putString("junk", "x".repeat(4000));
        assertFalse(((PlantNbt.Inert) PlantNbt.read(big)).future());

        CompoundTag badSp = PlantNbt.write(full());
        badSp.putString("sp", "Not An Id");
        assertInstanceOf(PlantNbt.Inert.class, PlantNbt.read(badSp));

        CompoundTag mismatch = PlantNbt.write(full());
        mismatch.getCompound("g").putString("sp", "wildroots:other");
        assertInstanceOf(PlantNbt.Inert.class, PlantNbt.read(mismatch));

        CompoundTag noGenome = PlantNbt.write(full());
        noGenome.remove("g");
        assertInstanceOf(PlantNbt.Inert.class, PlantNbt.read(noGenome));

        CompoundTag wrongType = PlantNbt.write(full());
        wrongType.put("pl", StringTag.valueOf("x"));
        assertInstanceOf(PlantNbt.Inert.class, PlantNbt.read(wrongType));

        CompoundTag badPo = PlantNbt.write(full());
        badPo.getCompound("pl").putFloat("po", Float.NaN);
        assertInstanceOf(PlantNbt.Inert.class, PlantNbt.read(badPo));

        assertInstanceOf(PlantNbt.Inert.class, PlantNbt.read(null));
        assertInstanceOf(PlantNbt.Inert.class, PlantNbt.read(new CompoundTag()));
    }

    @Test
    void updateTagOfABuiltInSpeciesIsEmpty() {
        assertTrue(PlantNbt.updateTag(true, SP).isEmpty());
        assertEquals(SP, PlantNbt.updateTag(false, SP).getString("sp"));
        assertEquals(1, PlantNbt.updateTag(false, SP).size());
    }
}
