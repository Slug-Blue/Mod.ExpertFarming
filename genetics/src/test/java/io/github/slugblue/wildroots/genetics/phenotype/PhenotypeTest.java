package io.github.slugblue.wildroots.genetics.phenotype;

import static io.github.slugblue.wildroots.genetics.testutil.Carrot.COLD;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.DROUGHT;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.FLAVOR;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.FRUIT;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.GROWTH;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.HEAT;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.YIELD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** WR-104: G, trade-offs, budget, vitality and P (GDD §3.1, §3.5, §3.7, §3.10; architecture §4.2). */
class PhenotypeTest {

    static final CompiledSpecies CS = new CompiledSpecies(Carrot.SPECIES);
    static final Environment IDEAL = Environment.neutral(0.6);

    /** Genome with given allele sums per quantitative trait, no load, chosen qualitative alleles. */
    static Genome carrot(Map<TraitId, Integer> sums, int dd) {
        Genome.Builder b = Genome.builder(Carrot.SPECIES.id(), Carrot.LAYOUT);
        for (Locus s : Carrot.LAYOUT.quantitative()) {
            int left = sums.getOrDefault(s.trait(), 0);
            for (int l = 0; l < s.loci(); l++) {
                for (int w = 0; w < 2; w++) {
                    int v = Math.min(2, left);
                    b.set(s, l, w, v);
                    left -= v;
                }
            }
            assertEquals(0, left, "sum too big for " + s.trait());
        }
        Locus load = Carrot.LAYOUT.load();
        for (int l = 0; l < dd; l++) {
            b.set(load, l, 0, 1).set(load, l, 1, 1);
        }
        return b.build();
    }

    @Test
    void genotypicValueIsAlleleSumOverFourL() {
        Genome g = carrot(Map.of(YIELD, 13, FLAVOR, 5, COLD, 12), 0);
        Phenotype p = PhenotypeCalculator.compute(CS, g, IDEAL, NoiseVector.zero(7));
        assertEquals(13 / 24.0, p.g(YIELD), 1e-12);
        assertEquals(5 / 16.0, p.g(FLAVOR), 1e-12);
        assertEquals(1.0, p.g(COLD), 1e-12);
        assertEquals(0.0, p.g(HEAT), 1e-12);
    }

    // ---- GDD §3.10 worked examples, one test each

    @Test
    void layerOnePenaltyIsTenthOfCPerTenthAboveTheta() {
        // flavor ← fruit_size, c 0.20, θ 0.55: fruit 0.75 costs flavor 0.2·0.2 = 0.04
        Genome g = carrot(Map.of(FRUIT, 18, FLAVOR, 12), 0);
        Phenotype p = PhenotypeCalculator.compute(CS, g, IDEAL, NoiseVector.zero(7));
        assertEquals(0.75, p.g(FRUIT), 1e-12);
        assertEquals(0.75 - 0.04, p.gEff(FLAVOR), 1e-12);
    }

    @Test
    void layerOneUsesOriginalGInCycles() {
        double[] g = {0.9, 0.9};
        double[] out = Pleiotropy.apply(g, new int[] {0, 1}, new int[] {1, 0}, new double[] {0.5, 0.5},
                new double[] {0.3, 0.3});
        assertEquals(0.9 - 0.5 * 0.6, out[0], 1e-12);
        assertEquals(0.9 - 0.5 * 0.6, out[1], 1e-12);
        double[] floor = Pleiotropy.apply(new double[] {0.1, 1.0}, new int[] {0}, new int[] {1},
                new double[] {10}, new double[] {0});
        assertEquals(0, floor[0]);
    }

    /** Weights in budget order yield, fruit_size, growth_speed, cold, heat, drought. */
    private static final double[] W = {1.0, 1.0, 0.75, 0.5, 0.5, 0.5};

    @Test
    void wildPlantBudgetAboutOnePointFive() {
        // GDD §3.10 «дикое растение ≈ 1.5»: G ≈ 0.35 on every budget trait
        double s = ResourceBudget.sum(new double[] {0.35, 0.35, 0.35, 0.35, 0.35, 0.35}, W);
        assertEquals(1.5, s, 0.02);
        assertEquals(1.0, ResourceBudget.scale(s, 2.6));
    }

    @Test
    void modernCultivarFitsTheBudget() {
        // yield 0.65, fruit 0.75, speed 0.45, tolerances 0.35 → S ≈ 2.27 ≤ 2.6
        double s = ResourceBudget.sum(new double[] {0.65, 0.75, 0.45, 0.35, 0.35, 0.35}, W);
        assertEquals(2.27, s, 0.01);
        assertEquals(1.0, ResourceBudget.scale(s, 2.6));
    }

    @Test
    void everythingAtPointNineIsScaledBy068() {
        double s = ResourceBudget.sum(new double[] {0.9, 0.9, 0.9, 0.9, 0.9, 0.9}, W);
        assertEquals(3.8, s, 0.03);
        assertEquals(0.68, ResourceBudget.scale(s, 2.6), 0.005);
    }

    @Test
    void highYieldAndFruitExampleGivesFruit076() {
        double[] gPair = {0.9, 0.9, 0.7, 0.5, 0.5, 0.5};
        double s = ResourceBudget.sum(gPair, W);
        assertEquals(3.08, s, 0.01);
        assertEquals(0.84, ResourceBudget.scale(s, 2.6), 0.01); // GDD rounds 0.8455 down
        assertEquals(0.76, ResourceBudget.apply(gPair, W, 2.6)[1], 0.005);
    }

    @Test
    void budgetLeavesNonBudgetTraitsAndZeroSumAlone() {
        double[] out = ResourceBudget.apply(new double[] {1, 1, 1}, new double[] {2, 2, 0}, 1);
        assertEquals(0.25, out[0], 1e-12);
        assertEquals(1.0, out[2]);
        assertEquals(1.0, ResourceBudget.scale(0, 2.6));
    }

    // ---- vitality

    @Test
    void vigorIsStepPowerOfHomozygousDeleteriousLoci() {
        for (int dd = 0; dd <= 8; dd++) {
            Genome g = carrot(Map.of(YIELD, 12, FLAVOR, 8), dd);
            Phenotype p = PhenotypeCalculator.compute(CS, g, IDEAL, NoiseVector.zero(7));
            assertEquals(Math.pow(0.9, dd), p.vigor(), 1e-12);
        }
        // heterozygous Dd does not count
        Locus load = Carrot.LAYOUT.load();
        Genome het = carrot(Map.of(), 0).toBuilder().set(load, 0, 0, 1).build();
        assertEquals(1.0, Vigor.of(het, load, 0.9));
    }

    @Test
    void vigorAndEnvironmentApplyOnlyToVigorAffectedTraits() {
        Genome g = carrot(Map.of(YIELD, 12, FRUIT, 12, GROWTH, 8, FLAVOR, 8, COLD, 6, HEAT, 6, DROUGHT, 6), 3);
        Phenotype p = PhenotypeCalculator.compute(CS, g, IDEAL, NoiseVector.zero(7));
        double v = Math.pow(0.9, 3);
        assertEquals(p.gEff(YIELD) * v, p.p(YIELD), 1e-12);
        assertEquals(p.gEff(FRUIT) * v, p.p(FRUIT), 1e-12);
        assertEquals(p.gEff(GROWTH) * v, p.p(GROWTH), 1e-12);
        assertEquals(p.gEff(FLAVOR), p.p(FLAVOR), 1e-12);
        assertEquals(p.gEff(COLD), p.p(COLD), 1e-12);

        Environment dark = new Environment(0.6, true, 5, 0);
        Phenotype pd = PhenotypeCalculator.compute(CS, g, dark, NoiseVector.zero(7));
        assertEquals(p.p(YIELD) - 0.05, pd.p(YIELD), 1e-12);
        assertEquals(p.p(FLAVOR), pd.p(FLAVOR), 1e-12);
    }

    @Test
    void environmentFollowsGddTable() {
        Genome g = carrot(Map.of(YIELD, 12, COLD, 6, DROUGHT, 6), 0); // P_cold 0.5, P_drought 0.5
        double base = PhenotypeCalculator.compute(CS, g, IDEAL, NoiseVector.zero(7)).p(YIELD);
        // window [0.2, 1.0], cold widens the lower bound by 0.6·0.5 = 0.3 → −0.1; 0.05 below → −0.05
        double cold = PhenotypeCalculator.compute(CS, g, Environment.neutral(-0.15), NoiseVector.zero(7)).p(YIELD);
        assertEquals(base - 0.05, cold, 1e-9);
        double frozen = PhenotypeCalculator.compute(CS, g, Environment.neutral(-2), NoiseVector.zero(7)).p(YIELD);
        assertEquals(Math.max(0, base - 0.30), frozen, 1e-9);
        double dry = PhenotypeCalculator.compute(CS, g, new Environment(0.6, false, 15, 0), NoiseVector.zero(7))
                .p(YIELD);
        assertEquals(base - 0.05 * 0.5, dry, 1e-9);
        double compost = PhenotypeCalculator.compute(CS, g, new Environment(0.6, true, 15, 0.2),
                NoiseVector.zero(7)).p(YIELD);
        assertEquals(base + 0.05, compost, 1e-9);
    }

    @Test
    void noiseIsAddedAndPIsClamped() {
        Genome top = carrot(Map.of(YIELD, 24, FLAVOR, 16), 0);
        byte[] e = new byte[7];
        int yi = CS.quantIndex(YIELD);
        int fi = CS.quantIndex(FLAVOR);
        e[yi] = 127;
        e[fi] = -128;
        Phenotype p = PhenotypeCalculator.compute(CS, top, IDEAL, new NoiseVector(e));
        assertEquals(1.0, p.p(YIELD));
        assertEquals(Math.max(0, p.gEff(FLAVOR) - 0.5), p.p(FLAVOR), 1e-12);
        Phenotype zero = PhenotypeCalculator.compute(CS, carrot(Map.of(), 0), IDEAL, new NoiseVector(new byte[] {
                -128, -128, -128, -128, -128, -128, -128}));
        for (double x : zero.pArray()) {
            assertEquals(0.0, x);
        }
    }

    @Test
    void rolledNoiseIsDeterministicAndHasTheTraitSigma() {
        assertEquals(PhenotypeCalculator.rollNoise(CS, 77), PhenotypeCalculator.rollNoise(CS, 77));
        int yi = CS.quantIndex(YIELD);
        double sum = 0;
        double sq = 0;
        int n = 20_000;
        for (int k = 0; k < n; k++) {
            double x = PhenotypeCalculator.rollNoise(CS, k).eps(yi);
            sum += x;
            sq += x * x;
        }
        assertEquals(0, sum / n, 0.003);
        assertEquals(0.07, Math.sqrt(sq / n), 0.003);
    }

    @Test
    void bitterPhenotypeFollowsDominance() {
        TraitId bit = Carrot.BITTER;
        Locus s = Carrot.LAYOUT.section(bit);
        Genome g = carrot(Map.of(), 0);
        assertTrue(PhenotypeCalculator.compute(CS, g, IDEAL, NoiseVector.zero(7)).bitter());
        Genome het = g.toBuilder().set(s, 0, 0, 1).build();
        assertTrue(PhenotypeCalculator.compute(CS, het, IDEAL, NoiseVector.zero(7)).bitter(), "bitter is dominant");
        Genome hom = het.toBuilder().set(s, 0, 1, 1).build();
        assertFalse(PhenotypeCalculator.compute(CS, hom, IDEAL, NoiseVector.zero(7)).bitter());
        Locus p = Carrot.LAYOUT.section(Carrot.P);
        Genome purple = g.toBuilder().set(p, 0, 1, 1).build();
        assertTrue(PhenotypeCalculator.compute(CS, purple, IDEAL, NoiseVector.zero(7)).domestic(Carrot.P),
                "P_ is dominant");
    }

    /**
     * Why the next test is disabled: the carrot trade-offs of GDD §3.9 do not
     * tie yield to size or flavor, and with growth_speed and the tolerances at
     * 0 the budget never binds (S = 2.0 ≤ 2.6). So yield 1, size 1, flavor 1
     * give G_eff 1 / 1 / 0.91. Question to GD in the WR-104 report.
     */
    @Test
    void counterexampleToTheThreeAxisInvariantExists() {
        Genome g = carrot(Map.of(YIELD, 24, FRUIT, 24, FLAVOR, 16), 0);
        Phenotype p = PhenotypeCalculator.compute(CS, g, IDEAL, NoiseVector.zero(7));
        assertEquals(1.0, p.gEff(YIELD), 1e-12);
        assertEquals(1.0, p.gEff(FRUIT), 1e-12);
        assertEquals(1.0 - 0.2 * 0.45, p.gEff(FLAVOR), 1e-12);
        assertTrue(p.p(YIELD) >= 0.75 && p.p(FRUIT) >= 0.75 && p.p(FLAVOR) >= 0.75);
    }

    /**
     * GDD §3.10 success condition: on 1 000 000 random carrot genomes no plant
     * has P ≥ 0.75 on size, flavor and yield at once. Genomes are uniform over
     * all allele values, ε is rolled per plant, the cell is ideal.
     */
    @Test
    @org.junit.jupiter.api.Disabled("WR-104: fails with the GDD §3.9 carrot data (5 of 10^6 uniform genomes, seed 104);"
            + " the invariant needs a GD decision, see counterexampleToTheThreeAxisInvariantExists")
    void noRandomCarrotIsAbove075OnSizeFlavorAndYield() {
        SplitMix64 rng = new SplitMix64(104);
        int fi = CS.quantIndex(FRUIT);
        int vi = CS.quantIndex(FLAVOR);
        int yi = CS.quantIndex(YIELD);
        int hits = 0;
        String example = null;
        for (int i = 0; i < 1_000_000; i++) {
            Genome g = RandomGenomes.carrot(rng);
            Phenotype p = PhenotypeCalculator.compute(CS, g, IDEAL, PhenotypeCalculator.rollNoise(CS, rng.nextLong()));
            double[] pp = p.pArray();
            if (pp[fi] >= 0.75 && pp[vi] >= 0.75 && pp[yi] >= 0.75) {
                hits++;
                if (example == null) {
                    example = p.toString() + " gEff=" + java.util.Arrays.toString(p.gEffArray());
                }
            }
        }
        assertEquals(0, hits, "first: " + example);
    }
}
