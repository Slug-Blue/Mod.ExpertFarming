package io.github.slugblue.wildroots.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.data.CarrotLineDataTestAccess;
import io.github.slugblue.wildroots.genetics.api.ClimateStress;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import org.junit.jupiter.api.Test;

/**
 * WR-711, WR-714: the pure rules around the wilting counter (carrot-colours-climate §4.2, §4.3, §4.6): the strain
 * of a counter value, the stages that can wilt, the block property, the flag of old plants. What needs a level is
 * in {@code ClimateStressGameTests}.
 */
class WiltingRulesTest {

    static final SpeciesDef CARROT = CarrotLineDataTestAccess.wildCarrot();
    static final int K = CARROT.climateStress().graceTicks();

    @Test
    void theShippedCarrotWiltsAfterThreeTicksAtTheDesignMargin() {
        ClimateStress cs = CARROT.climateStress();
        assertTrue(cs.enabled());
        assertEquals(3, K);
        assertEquals(0.35, cs.wiltMargin());
    }

    @Test
    void strainFollowsTheCounter() {
        assertEquals(0, Wilting.strainOf(0, K));
        assertEquals(0, Wilting.strainOf(-1, K));
        for (int t = 1; t < K; t++) {
            assertEquals(1, Wilting.strainOf(t, K), "drooping while the counter runs, tick " + t);
        }
        assertEquals(FieldPlantBlock.WILTED, Wilting.strainOf(K, K));
        assertEquals(FieldPlantBlock.WILTED, Wilting.strainOf(K + 5, K));
    }

    /** Only the seedling and the rosette (stages 1 and 2) wilt; a seed, a ripe root, flowers and seeds never. */
    @Test
    void onlyStagesOneAndTwoWilt() {
        for (int stage = 0; stage <= FieldPlantBlock.MAX_STAGE; stage++) {
            assertEquals(stage == 1 || stage == 2, Wilting.youngStage(stage), "stage " + stage);
        }
        assertEquals(3, FieldPlantBlock.HARVEST_STAGE, "the ripe root is the first stage that waits");
    }

    @Test
    void wiltedIsTheLastStrainAndTheOldPlantGraceIsTenMinutes() {
        assertEquals(2, FieldPlantBlock.WILTED);
        assertEquals(FieldPlantBlock.MAX_STRAIN, FieldPlantBlock.WILTED);
        assertEquals(10 * 60 * 20, Wilting.OLD_PLANT_GRACE_TICKS);
    }

    @Test
    void theClimateAwareFlagIsAFreeBitOfThePlantFlags() {
        int[] all = {PlantState.MOTHER, PlantState.MUTANT, PlantState.RARE_CLAIMED, PlantState.CLIMATE_AWARE};
        int seen = 0;
        for (int f : all) {
            assertEquals(1, Integer.bitCount(f));
            assertEquals(0, seen & f, "flag " + f + " is distinct");
            seen |= f;
        }
        assertFalse(seen > Byte.MAX_VALUE, "plant flags are one byte");
    }
}
