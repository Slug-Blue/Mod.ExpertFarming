package io.github.slugblue.wildroots.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.data.CarrotLineDataTestAccess;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Traits;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** WR-132: chance of a stage per random tick (economy §3.1, GDD §3.3, §12.1). */
class GrowthChanceTest {

    static final SpeciesDef CARROT = CarrotLineDataTestAccess.wildCarrot();

    /** Phenotype with P_growth and P_drought given, everything else 0.5. */
    static Phenotype phenotype(double growth, double drought) {
        List<TraitId> ids = CARROT.quantitative().stream().map(q -> q.id()).toList();
        double[] p = new double[ids.size()];
        for (int i = 0; i < p.length; i++) {
            p[i] = ids.get(i).equals(Traits.GROWTH_SPEED) ? growth
                    : ids.get(i).equals(Traits.DROUGHT_TOLERANCE) ? drought : 0.5;
        }
        return new Phenotype(ids, p, p, p, 1.0, List.of(), new boolean[0], false);
    }

    @Test
    void carrotStagesAre4_4_5_then_2point5_2point5() {
        double[] m = new double[6];
        for (int s = 1; s <= 5; s++) {
            m[s] = GrowthLogic.stageMinutes(CARROT.timing(), s);
        }
        assertEquals(4.0, m[1], 1e-9);
        assertEquals(4.0, m[2], 1e-9);
        assertEquals(5.0, m[3], 1e-9);
        assertEquals(2.5, m[4], 1e-9);
        assertEquals(2.5, m[5], 1e-9);
    }

    @Test
    void chanceFollowsTheEconomyTable() {
        Environment wet = new Environment(0.5, true, 15, 0);
        // m = 1 at P_growth = 0.5: 4 min → 0.285, 5 → 0.228, 2.5 → 0.455 (economy §3.1)
        Phenotype p = phenotype(0.5, 0);
        assertEquals(0.285, GrowthLogic.chance(CARROT, p, wet, 0, 0), 0.001);
        assertEquals(0.228, GrowthLogic.chance(CARROT, p, wet, 2, 0), 0.001);
        assertEquals(0.455, GrowthLogic.chance(CARROT, p, wet, 3, 0), 0.001);
        assertEquals(0, GrowthLogic.chance(CARROT, p, wet, 5, 0));
        // m = 0.7 + 0.6·P
        assertEquals(0.285 * 1.3, GrowthLogic.chance(CARROT, phenotype(1, 0), wet, 0, 0), 0.001);
        // dormancy adds minutes to stage 0 only
        assertTrue(GrowthLogic.chance(CARROT, p, wet, 0, 4) < GrowthLogic.chance(CARROT, p, wet, 0, 0));
        assertEquals(GrowthLogic.chance(CARROT, p, wet, 1, 4), GrowthLogic.chance(CARROT, p, wet, 1, 0));
    }

    @Test
    void dryFarmlandHalvesGrowthUnlessDroughtTolerant() {
        Environment wet = new Environment(0.5, true, 15, 0);
        Environment dry = new Environment(0.5, false, 15, 0);
        assertEquals(0.5, GrowthLogic.chance(CARROT, phenotype(0.5, 0), dry, 1, 0)
                / GrowthLogic.chance(CARROT, phenotype(0.5, 0), wet, 1, 0), 1e-9);
        assertEquals(0.75, GrowthLogic.chance(CARROT, phenotype(0.5, 0.5), dry, 1, 0)
                / GrowthLogic.chance(CARROT, phenotype(0.5, 0.5), wet, 1, 0), 1e-9);
    }

    @Test
    void chanceIsClamped() {
        Environment wet = new Environment(0.5, true, 15, 0);
        SpeciesDef fast = new SpeciesDef(CARROT.id(), CARROT.tempMin(), CARROT.tempMax(), CARROT.quantitative(),
                CARROT.tradeoffs(), CARROT.budget(), CARROT.wild(), CARROT.wildF(), CARROT.qualitative(),
                CARROT.starterFreq(), CARROT.load(), CARROT.mutation(), CARROT.pollination(),
                new io.github.slugblue.wildroots.genetics.api.Timing(0.1, 0.1), CARROT.qualityAxes());
        assertEquals(GrowthLogic.MAX_CHANCE, GrowthLogic.chance(fast, phenotype(1, 1), wet, 0, 0));
        assertTrue(GrowthLogic.chance(CARROT, phenotype(0, 0), new Environment(0.5, false, 0, 0), 0, 1e9)
                >= GrowthLogic.MIN_CHANCE);
    }
}
