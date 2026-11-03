package io.github.slugblue.wildroots.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.pollination.Brush;
import io.github.slugblue.wildroots.item.LotFathersNbt;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * Review ticket (batch 3): the save-failure fallback of
 * {@link PlantBlockEntity} ({@code essentialRoot}) must write every part of
 * the plant that can still be written, so a failure in one part (typically
 * the pending lot) loses only that part, never the cultivar status, the
 * line data or the plant flags ({@code MOTHER}, {@code MUTANT},
 * {@code rareClaimed}).
 */
class PlantEssentialRootTest {

    /** {@link PlantNbtTest#full()} with a pending lot whose write throws (a father without a genome). */
    static PlantState brokenPending() {
        PlantState s = PlantNbtTest.full();
        s.plantFlags = (byte) (PlantState.MOTHER | PlantState.MUTANT | PlantState.RARE_CLAIMED);
        s.pending = new PendingLot(List.of(new LotFathersNbt.Father(null, 1f, false)), 0.5f, Brush.NONE, false);
        return s;
    }

    @Test
    void theFullWriteReallyFailsOnTheBrokenPendingLot() {
        assertThrows(RuntimeException.class, () -> PlantNbt.write(brokenPending()));
    }

    @Test
    void onlyThePendingLotIsDroppedWhenItCannotBeWritten() {
        PlantState s = brokenPending();
        CompoundTag root = PlantBlockEntity.essentialRoot(s);
        PlantNbt.Ok ok = assertInstanceOf(PlantNbt.Ok.class, PlantNbt.read(root), () -> "root " + root);
        PlantState back = ok.state();
        assertEquals(s.species, back.species);
        assertEquals(s.genome, back.genome);
        assertTrue(java.util.Arrays.equals(s.noise, back.noise), "noise");
        assertEquals(s.passport, back.passport);
        assertEquals(s.planter(), back.planter());
        assertEquals(s.cultivar, back.cultivar, "cultivar id lost");
        assertEquals(s.tier, back.tier, "cultivar tier lost");
        assertEquals(s.inherited, back.inherited, "inherited status lost");
        assertEquals(s.lineName, back.lineName, "line name lost");
        assertEquals(s.generation, back.generation, "generation lost");
        assertEquals(s.lotFlags, back.lotFlags, "lot flags lost");
        assertEquals(s.plantFlags, back.plantFlags, "plant flags (MOTHER, MUTANT, rareClaimed) lost");
        assertNull(back.pending, "the broken pending lot came back");
    }

    @Test
    void aWritablePendingLotIsKeptToo() {
        PlantState s = PlantNbtTest.full();
        CompoundTag root = PlantBlockEntity.essentialRoot(s);
        assertEquals(PlantNbt.write(s), root, "with nothing broken the fallback equals the full write");
    }

    @Test
    void aBrokenScalarLosesOnlyItself() {
        PlantState s = PlantNbtTest.full();
        s.pending = null;
        s.passport = null; // the passport write throws (NPE), the rest must survive
        CompoundTag root = PlantBlockEntity.essentialRoot(s);
        assertFalse(root.contains("pp"));
        assertEquals("Golden Line", root.getString("ln"));
        assertEquals(PlantState.MOTHER, root.getByte("pf"));
        assertEquals("wildroots:eastern_carrot", root.getString("cv"));
        assertInstanceOf(PlantNbt.Ok.class, PlantNbt.read(root));
    }

    @Test
    void noStateNoRoot() {
        assertNull(PlantBlockEntity.essentialRoot(null));
    }
}
