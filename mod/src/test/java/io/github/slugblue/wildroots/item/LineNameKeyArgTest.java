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

    /**
     * T2: a stored line name that is not clean player text is cosmetic
     * damage only. It is read as absent: the lot stays live, has no line
     * name, and the display rebuilt from it carries no line-name row (so the
     * dirty text is never shown, translated or not).
     */
    @ParameterizedTest
    @ValueSource(strings = {"@cultivar.carrot", "cultivar.carrot", "biome.minecraft.plains", "ab",
        "bad§cname", "  spaced", "x‮y"})
    void aLineNameThatIsNotCleanPlayerTextIsDropped(String ln) {
        LotNbt.Lot lot = assertInstanceOf(LotNbt.Lot.class, LotNbt.read(lotWithLine(ln)), ln);
        assertEquals(Optional.empty(), lot.lineName(), ln);
        assertTrue(LotStacks.disp(lot).stream().noneMatch(l -> l.key().equals("wildroots.tooltip.lot.line_name")),
                "no line-name row for " + ln);
        assertTrue(LotStacks.disp(lot).stream().flatMap(l -> l.args().stream()).noneMatch(a -> a.contains(ln)),
                "dirty text in no argument");
        assertFalse(LotNbt.write(lot, LotStacks.disp(lot)).contains("ln"), "written back without the name");
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

    @ParameterizedTest
    @ValueSource(strings = {"@cultivar.carrot", "cultivar.carrot", "a‮b", "  spaced"})
    void aMotherRootWithADirtyLineNameStaysReadableWithoutIt(String ln) {
        CompoundTag m = ItemRootBoundaryTest.fullMother();
        m.putString("ln", ln);
        MotherNbt.Mother mother = MotherNbt.read(m).orElseThrow(() -> new AssertionError("unreadable: " + ln));
        assertEquals(Optional.empty(), mother.lineName(), ln);
        assertFalse(MotherNbt.write(mother).contains("ln"), "written back without the name");
        m.putString("ln", "Golden Line");
        assertEquals(Optional.of("Golden Line"), MotherNbt.read(m).orElseThrow().lineName());
    }
}
