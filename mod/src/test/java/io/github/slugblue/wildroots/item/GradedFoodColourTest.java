package io.github.slugblue.wildroots.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.data.CultivarInfo;
import net.minecraft.world.food.FoodProperties;
import org.junit.jupiter.api.Test;

/** WR-703: the saturation multiplier of the purple root (anthocyanins) touches the saturation only. */
class GradedFoodColourTest {

    static final CultivarInfo.FoodReference REF = new CultivarInfo.FoodReference(3, 0.6);

    @Test
    void saturationIsMultipliedAndNothingElse() {
        for (int g = 1; g <= 4; g++) {
            assertEquals(ProduceStacks.saturation(0.6, g, false) * 1.15f, ProduceStacks.saturation(0.6, g, false, 1.15), 1e-6);
            assertEquals(ProduceStacks.saturation(0.6, g, false), ProduceStacks.saturation(0.6, g, false, 1), 0);
            FoodProperties plain = GradedFood.of(REF, g, false, false);
            FoodProperties purple = GradedFood.of(REF, g, false, false, 1.15);
            assertEquals(plain.getNutrition(), purple.getNutrition());
            assertEquals(plain.getSaturationModifier() * 1.15f, purple.getSaturationModifier(), 1e-6);
            assertTrue(purple.getEffects().isEmpty());
            assertSame(purple, GradedFood.of(REF, g, false, false, 1.15), "cached");
            assertNotSame(plain, purple);
        }
        assertEquals(0.69f, GradedFood.of(REF, 3, false, false, 1.15).getSaturationModifier(), 1e-6);
        // a broken multiplier from a data pack is no change
        assertEquals(0.6f, ProduceStacks.saturation(0.6, 3, false, Double.NaN), 1e-6);
        assertEquals(0.6f, ProduceStacks.saturation(0.6, 3, false, -2), 1e-6);
        assertEquals(0.6f, ProduceStacks.saturation(0.6, 3, false, 0), 1e-6);
    }
}
