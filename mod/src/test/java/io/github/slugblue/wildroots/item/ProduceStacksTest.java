package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** WR-136: {@code ProduceStacks} (docs/05-architecture.md §6.3; ADR-0007; GDD §6.4). */
class ProduceStacksTest {

    @Test
    void bitterAboveTwoIsTwoAndFalseIsNotWritten() {
        assertEquals(2, new CompoundTag().merge(ProduceStacks.tag(4, true)).getByte(ProduceStacks.GRADE));
        assertEquals(2, ProduceStacks.tag(3, true).getByte(ProduceStacks.GRADE));
        assertEquals(1, ProduceStacks.tag(1, true).getByte(ProduceStacks.GRADE));
        assertFalse(ProduceStacks.tag(3, false).contains(ProduceStacks.BITTER));
        assertTrue(ProduceStacks.tag(1, true).contains(ProduceStacks.BITTER));
    }

    @Test
    void noOtherKeysAndAtMostSixVariants() {
        Set<CompoundTag> variants = new HashSet<>();
        for (int g = -5; g <= 10; g++) {
            for (boolean b : new boolean[] {false, true}) {
                CompoundTag t = ProduceStacks.tag(g, b);
                assertTrue(Set.of(ProduceStacks.GRADE, ProduceStacks.BITTER).containsAll(t.getAllKeys()), t::toString);
                variants.add(t);
            }
        }
        assertEquals(6, variants.size(), variants::toString);
    }

    @Test
    void readBackNormalisesAndDefaultsToThree() {
        assertEquals(3, ProduceStacks.grade(null));
        assertEquals(3, ProduceStacks.grade(new CompoundTag()));
        CompoundTag forged = new CompoundTag();
        forged.putByte(ProduceStacks.GRADE, (byte) 4);
        forged.putByte(ProduceStacks.BITTER, (byte) 1);
        assertEquals(2, ProduceStacks.grade(forged), "a forged bitter grade 4 reads as 2");
    }

    @Test
    void carrotFoodTableOfGdd64() {
        int[] n = {0, 1, 2, 3, 4};
        float[] s = {0, 0.24f, 0.42f, 0.6f, 0.72f};
        for (int g = 1; g <= 4; g++) {
            assertEquals(n[g], ProduceStacks.nutrition(3, g));
            assertEquals(s[g], ProduceStacks.saturation(0.6, g, false), 1e-6);
            assertEquals(s[g] / 2, ProduceStacks.saturation(0.6, g, true), 1e-6);
        }
    }
}
