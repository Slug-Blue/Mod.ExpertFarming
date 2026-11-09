package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Review 1, m1: a line name from NBT is checked as player text on read, and
 * a literal argument is never read as a key argument (the key marker is a
 * control character that no literal may hold).
 */
class LineNameKeyArgTest {

    static CompoundTag lotWithLine(String ln) {
        CompoundTag root = LotStacks.seedsTag(new LotStacks.Parent(LotNbtTest.SP, Optional.empty(), LotNbtTest.genome(1),
                Optional.of("Golden Line"), 1, 0), List.of(), 0, 0, 77L, LotStacks.RareClaim.NONE);
        root.putString("ln", ln);
        return root;
    }

    @ParameterizedTest
    @ValueSource(strings = {"@cultivar.carrot", "\u0001cultivar.carrot", "\u0001biome.minecraft.plains", "a\u0001b",
        "bad§cname", "  spaced", "x‮y"})
    void aLineNameThatIsNotCleanPlayerTextMakesTheLotDamaged(String ln) {
        assertInstanceOf(LotNbt.Inert.class, LotNbt.read(lotWithLine(ln)), ln);
    }

    @Test
    void aCleanLineNameStays() {
        assertEquals(Optional.of("Golden Line"), ((LotNbt.Lot) LotNbt.read(lotWithLine("Golden Line"))).lineName());
        assertEquals(Optional.of("Рыжик (2)"), ((LotNbt.Lot) LotNbt.read(lotWithLine("Рыжик (2)"))).lineName());
    }

    @Test
    void literalArgumentsAreNeverKeys() {
        assertNull(DispCodec.argKey("@cultivar.carrot"), "'@' is no longer a marker");
        assertTrue(DispCodec.valid(DispCodec.Line.of("wildroots.tooltip.lot.line_name", "@cultivar.carrot")));
        // a literal holding the marker anywhere but as a whole key argument is invalid
        assertFalse(DispCodec.valid(DispCodec.Line.of("wildroots.tooltip.lot.line_name", "x\u0001cultivar.carrot")));
        assertFalse(DispCodec.valid(DispCodec.Line.of("wildroots.tooltip.lot.line_name", "\u0001Not A Key")));
        assertTrue(DispCodec.valid(DispCodec.Line.of("wildroots.tooltip.lot.line_name",
                DispCodec.keyArg("wildroots.cultivar.carrot"))));
        assertEquals("wildroots.cultivar.carrot", DispCodec.argKey(DispCodec.keyArg("wildroots.cultivar.carrot")));
        assertEquals("biome.some-pack.hot.dunes", DispCodec.argKey(DispCodec.keyArg("biome.some-pack.hot.dunes")));
    }

    @Test
    void motherAndPlantRefuseADirtyLineName() {
        CompoundTag m = ItemRootBoundaryTest.fullMother();
        m.putString("ln", "@cultivar.carrot");
        assertTrue(MotherNbt.read(m).isEmpty(), "mother root with a key-like line name");
    }
}
