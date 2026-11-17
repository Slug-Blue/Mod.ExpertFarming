package io.github.slugblue.wildroots.genetics.phenotype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.ClimateStress;
import io.github.slugblue.wildroots.genetics.api.ColourProps;
import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import org.junit.jupiter.api.Test;

/**
 * WR-710: {@link ClimateStress}, stress {@code S} and the wilting threshold
 * (docs/design/carrot-colours-climate.md §3.1, §3.2, §4.2). The colour numbers are the ones of the shipped line
 * ({@code colours.properties}); the oracle below is the formula of the design written out from the table, not
 * a copy of the class.
 */
class ClimateStressTest {

    static final SpeciesDef DEF = Carrot.SPECIES;
    static final ClimateStress CS = DEF.climateStress();
    static final double W = 0.35;

    static ColourProps colour(int c) {
        return DEF.colours().props(c);
    }

    @Test
    void shippedCarrotDeclaresTheDesignNumbers() {
        assertTrue(CS.enabled());
        assertEquals(0.35, CS.wiltMargin());
        assertEquals(0.40, CS.humidPenalty());
        assertEquals(0.25, CS.shelterBuffer());
        assertEquals(0.10, CS.irrigationBuffer());
        assertEquals(3, CS.graceTicks());
        assertEquals(12, CS.shelterSkyLight());
        assertEquals(0.6, ClimateStress.mid(DEF), 1e-12);
    }

    // ------------------------------------------------------------------ T_eff and buffers (design §4.5 examples)

    @Test
    void effectiveTemperatureOfTheDesert() {
        double mid = ClimateStress.mid(DEF);
        assertEquals(2.00, ClimateStress.effectiveTemperature(mid, 2.0, 0, CS.buffer(false, false)), 1e-9);
        assertEquals(1.86, ClimateStress.effectiveTemperature(mid, 2.0, 0, CS.buffer(false, true)), 1e-9);
        assertEquals(1.65, ClimateStress.effectiveTemperature(mid, 2.0, 0, CS.buffer(true, false)), 1e-9);
        assertEquals(1.51, ClimateStress.effectiveTemperature(mid, 2.0, 0, CS.buffer(true, true)), 1e-9);
        // the snowy taiga: -0.50, -0.23 sheltered, -0.12 with both
        assertEquals(-0.50, ClimateStress.effectiveTemperature(mid, -0.5, 0, 0), 1e-9);
        assertEquals(-0.225, ClimateStress.effectiveTemperature(mid, -0.5, 0, CS.buffer(true, false)), 1e-9);
        assertEquals(-0.115, ClimateStress.effectiveTemperature(mid, -0.5, 0, CS.buffer(true, true)), 1e-9);
    }

    // ------------------------------------------------------------------ P* (design §3.2)

    /** Hot rows of the table of §3.2 for the five colours, from the shipped heat modifiers. */
    @Test
    void minimumHeatToleranceTable() {
        double[][] expect = { // T_eff of the desert: none, irrigated, sheltered, both
                // white, orange, red, yellow, purple (colour numbers 0, 2, 4, 1, 3)
                {1.083, 1.167, 0.950, 0.833, 0.667},
                {0.850, 0.933, 0.717, 0.600, 0.433},
                {0.500, 0.583, 0.367, 0.250, 0.083},
                {0.267, 0.350, 0.133, 0.017, -0.150}};
        boolean[][] prot = {{false, false}, {false, true}, {true, false}, {true, true}};
        int[] colours = {0, 2, 4, 1, 3};
        for (int r = 0; r < 4; r++) {
            double t = ClimateStress.effectiveTemperature(0.6, 2.0, 0, CS.buffer(prot[r][0], prot[r][1]));
            for (int c = 0; c < 5; c++) {
                assertEquals(expect[r][c], CS.requiredHeatTolerance(DEF, t, colour(colours[c]).heat()), 1.5e-3,
                        "row " + r + " colour " + colours[c]);
            }
        }
    }

    /** Cold rows: the numbers follow the shipped cold modifiers (owner decision 7b: orange has the best cold). */
    @Test
    void minimumColdToleranceTable() {
        double snowy = ClimateStress.effectiveTemperature(0.6, -0.5, 0, 0);
        assertEquals(0.50, CS.requiredColdTolerance(DEF, snowy, colour(0).cold()), 1e-9, "white");
        assertEquals(0.667, CS.requiredColdTolerance(DEF, snowy, colour(1).cold()), 1e-3, "yellow");
        assertEquals(0.75, CS.requiredColdTolerance(DEF, snowy, colour(3).cold()), 1e-9, "purple");
        assertEquals(0.75, CS.requiredColdTolerance(DEF, snowy, colour(4).cold()), 1e-9, "red");
        assertEquals(0.25, CS.requiredColdTolerance(DEF, snowy, colour(2).cold()), 1e-9, "orange");
        double peaks = ClimateStress.effectiveTemperature(0.6, -0.7, 0, 0);
        assertEquals(0.833, CS.requiredColdTolerance(DEF, peaks, colour(0).cold()), 1e-3);
    }

    /** P* is the threshold: a plant just above it survives, just below it wilts. */
    @Test
    void requiredToleranceIsTheBoundary() {
        for (int c = 0; c < 5; c++) {
            double t = ClimateStress.effectiveTemperature(0.6, 2.0, 0, 0);
            double pStar = CS.requiredHeatTolerance(DEF, t, colour(c).heat());
            for (double d : new double[] {-1e-6, 1e-6}) {
                double hot = ClimateStress.hotLimit(DEF, pStar + d, colour(c));
                double s = ClimateStress.stress(t, hot, ClimateStress.coldLimit(DEF, 0.3, colour(c)));
                assertEquals(d < 0, CS.wilts(s), "colour " + c + " d " + d);
            }
        }
    }

    @Test
    void thresholdIsInclusive() {
        assertTrue(CS.wilts(W));
        assertTrue(CS.wilts(W + 1e-9));
        assertFalse(CS.wilts(W - 1e-9));
        assertFalse(CS.wilts(0));
        assertFalse(CS.wilts(-1));
        assertFalse(ClimateStress.NONE.wilts(100), "a species without climate_stress never wilts");
        assertFalse(ClimateStress.NONE.enabled());
    }

    // ------------------------------------------------------------------ jungle (design §3.2 H1)

    @Test
    void jungleIsAPressureOfTheHarvestForTheWildWhite() {
        double[] limits = {ClimateStress.hotLimit(DEF, 0.32, colour(0)), ClimateStress.coldLimit(DEF, 0.32, colour(0))};
        double s = CS.stress(DEF, limits, 0.95, true, false, false);
        assertEquals(0.158, s, 1e-3, "S = 0.16 at P_heat 0.32 (E = -0.16)");
        assertFalse(CS.wilts(s), "pressure of the harvest, not of survival");
        assertEquals(CS.stress(DEF, limits, 1.35, false, false, false), s, 1e-12, "humid is plain addition without a buffer");
        // yellow and purple lose almost nothing, orange is worse
        double[] yellow = {ClimateStress.hotLimit(DEF, 0.32, colour(1)), ClimateStress.coldLimit(DEF, 0.32, colour(1))};
        double[] orange = {ClimateStress.hotLimit(DEF, 0.32, colour(2)), ClimateStress.coldLimit(DEF, 0.32, colour(2))};
        double[] purple = {ClimateStress.hotLimit(DEF, 0.32, colour(3)), ClimateStress.coldLimit(DEF, 0.32, colour(3))};
        assertTrue(CS.stress(DEF, yellow, 0.95, true, false, false) < s);
        assertTrue(CS.stress(DEF, purple, 0.95, true, false, false) < 0);
        assertTrue(CS.stress(DEF, orange, 0.95, true, false, false) > s);
        // a plain biome gets no humid penalty
        assertTrue(CS.stress(DEF, limits, 0.95, false, false, false) < s - 0.3);
        // a roof and irrigation take the jungle's pressure off entirely
        assertTrue(CS.stress(DEF, limits, 0.95, true, true, true) < 0);
    }

    // ------------------------------------------------------------------ the window invariance (risk table §4.7)

    /** S < 0 for every P ≥ 0, every colour, every protection, at 0.5 to 0.8 (the starter patch and the M1a gate). */
    @Test
    void insideTheClimateWindowNothingIsStressed() {
        double[] ps = {0, 0.001, 0.1, 0.32, 0.6, 1};
        for (int c = -1; c < 5; c++) { // -1: no colour at all
            ColourProps cp = c < 0 ? ColourProps.ZERO : colour(c);
            for (double ph : ps) {
                for (double pc : ps) {
                    double hot = ClimateStress.hotLimit(DEF, ph, cp);
                    double cold = ClimateStress.coldLimit(DEF, pc, cp);
                    for (double t = 0.5; t <= 0.8 + 1e-12; t += 0.01) {
                        for (int b = 0; b < 4; b++) {
                            double s = CS.stress(DEF, new double[] {hot, cold}, t, false, (b & 1) != 0, (b & 2) != 0);
                            assertTrue(s < 0, "S " + s + " at " + t);
                            assertFalse(CS.wilts(s));
                        }
                    }
                }
            }
        }
    }

    /** Same on real genomes: the limits of a phenotype of the carrot keep the window of 0.5–0.8 unstressed. */
    @Test
    void randomPhenotypesDoNotWiltInTheWindow() {
        SplitMix64 rng = new SplitMix64(710);
        CompiledSpecies cs = new CompiledSpecies(DEF);
        for (int i = 0; i < 2000; i++) {
            Phenotype p = PhenotypeCalculator.compute(cs, RandomGenomes.carrot(rng), Environment.neutral(0.6),
                    PhenotypeCalculator.rollNoise(cs, rng.nextLong()));
            double[] l = ClimateStress.limits(DEF, p);
            for (double t : new double[] {0.5, 0.65, 0.8}) {
                assertTrue(CS.stress(DEF, l, t, false, false, false) < 0);
            }
        }
    }

    // ------------------------------------------------------------------ monotony

    /** More protection never raises S, and P* falls with it; a better colour never raises P* (heat order of the design). */
    @Test
    void protectionAndColourOnlyHelp() {
        for (double cell : new double[] {2.0, 1.3, -0.5, -0.7}) {
            double last = Double.POSITIVE_INFINITY;
            double[] l = {ClimateStress.hotLimit(DEF, 0.3, colour(0)), ClimateStress.coldLimit(DEF, 0.3, colour(0))};
            for (int b = 0; b < 4; b++) {
                boolean shelter = b == 1 || b == 3;
                boolean irrigated = b >= 2;
                double s = CS.stress(DEF, l, cell, false, shelter, irrigated);
                if (b == 0 || b == 1 || b == 3) {
                    assertTrue(s <= last + 1e-12, "cell " + cell + " b " + b);
                    last = s;
                }
            }
        }
        // heat: purple < yellow < red < white < orange in P*
        double t = 2.0;
        double purple = CS.requiredHeatTolerance(DEF, t, colour(3).heat());
        double yellow = CS.requiredHeatTolerance(DEF, t, colour(1).heat());
        double red = CS.requiredHeatTolerance(DEF, t, colour(4).heat());
        double white = CS.requiredHeatTolerance(DEF, t, colour(0).heat());
        double orange = CS.requiredHeatTolerance(DEF, t, colour(2).heat());
        assertTrue(purple < yellow && yellow < red && red < white && white < orange);
        // protection lowers P*
        double none = CS.requiredHeatTolerance(DEF, ClimateStress.effectiveTemperature(0.6, 2, 0, 0), 0);
        double irrigated = CS.requiredHeatTolerance(DEF, ClimateStress.effectiveTemperature(0.6, 2, 0, 0.10), 0);
        double sheltered = CS.requiredHeatTolerance(DEF, ClimateStress.effectiveTemperature(0.6, 2, 0, 0.25), 0);
        double both = CS.requiredHeatTolerance(DEF, ClimateStress.effectiveTemperature(0.6, 2, 0, 0.35), 0);
        assertTrue(none > irrigated && irrigated > sheltered && sheltered > both);
    }

    /** The wild white wilts in the desert without protection, whatever its heat tolerance (P* above 1). */
    @Test
    void ancientCarrotAlwaysWiltsInTheDesertWithoutProtection() {
        double t = ClimateStress.effectiveTemperature(0.6, 2.0, 0, 0);
        assertTrue(CS.requiredHeatTolerance(DEF, t, colour(0).heat()) > 1);
        double[] l = {ClimateStress.hotLimit(DEF, 1.0, colour(0)), ClimateStress.coldLimit(DEF, 1.0, colour(0))};
        assertTrue(CS.wilts(CS.stress(DEF, l, 2.0, false, false, false)));
        // sheltered and irrigated: an average wild plant (0.32) survives (P* 0.267)
        double[] avg = {ClimateStress.hotLimit(DEF, 0.32, colour(0)), ClimateStress.coldLimit(DEF, 0.32, colour(0))};
        assertFalse(CS.wilts(CS.stress(DEF, avg, 2.0, false, true, true)));
        // a purple with 0.67 survives the bare desert
        double[] purple = {ClimateStress.hotLimit(DEF, 0.67, colour(3)), ClimateStress.coldLimit(DEF, 0.67, colour(3))};
        assertFalse(CS.wilts(CS.stress(DEF, purple, 2.0, false, false, false)));
    }

    // ------------------------------------------------------------------ same stress as E

    /** {@code E} of the calculator with {@code T_eff} is {@code −min(0.30, max(0, S))} of this class (design §3.1). */
    @Test
    void environmentPenaltyIsTheCappedStress() {
        SplitMix64 rng = new SplitMix64(711);
        CompiledSpecies cs = new CompiledSpecies(DEF);
        for (int i = 0; i < 400; i++) {
            var g = RandomGenomes.carrot(rng);
            var noise = PhenotypeCalculator.rollNoise(cs, rng.nextLong());
            Phenotype base = PhenotypeCalculator.compute(cs, g, Environment.neutral(0.6), noise);
            double[] l = ClimateStress.limits(DEF, base);
            double tEff = -1 + 4 * rng.nextDouble();
            Phenotype p = PhenotypeCalculator.compute(cs, g, Environment.neutral(tEff), noise);
            double e = -Math.min(Defaults.ENV_TEMP_CAP, Math.max(0, ClimateStress.stress(tEff, l[0], l[1])));
            for (int t = 0; t < p.traits().size(); t++) {
                if (cs.vigorAffected[t]) {
                    double expect = Math.max(0, Math.min(1, base.gEff(p.traits().get(t)) * base.vigor() + e + noise.eps(t)));
                    assertEquals(expect, p.p(p.traits().get(t)), 1e-9, "trait " + t);
                }
            }
        }
    }

}
