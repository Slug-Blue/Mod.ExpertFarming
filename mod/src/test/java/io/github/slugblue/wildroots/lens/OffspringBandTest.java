package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.data.CarrotLineDataTestAccess;
import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Playtest 2, item 6: the offspring band is analytic, deterministic and reads σ_env from the species. */
class OffspringBandTest {

    static final double EPS = 1e-12;

    static int[][] homozygous(int loci, int allele) {
        int[][] m = new int[loci][2];
        for (int[] l : m) {
            l[0] = allele;
            l[1] = allele;
        }
        return m;
    }

    @Test
    void homozygousMotherSelfedGivesTheNoiseOnly() {
        double sd = OffspringBand.childSd(homozygous(4, 1), List.of(), new double[0], 0, 0.05, 1);
        assertEquals(0.05, sd, EPS);
        assertEquals(0.05, OffspringBand.noiseOnly(0.05), EPS);
    }

    @Test
    void oneHeterozygousLocusUnderSelfingGivesEffectSquaredHalf() {
        int loci = 4;
        int[][] m = homozygous(loci, 1);
        m[2][0] = 0;
        m[2][1] = 2;
        double sigma = 0.05;
        // allele effect (a − b)/(4L) = 2/16; its variance under selfing: effect² · 1/2
        double effect = 2.0 / (4 * loci);
        double expected = Math.sqrt(effect * effect / 2 + sigma * sigma);
        assertEquals(expected, OffspringBand.childSd(m, List.of(), new double[0], 0, sigma, 1), EPS);
        // a vigor below 1 shrinks the genetic part only
        assertEquals(Math.sqrt(0.25 * effect * effect / 2 + sigma * sigma),
                OffspringBand.childSd(m, List.of(), new double[0], 0, sigma, 0.5), EPS);
        // fathers with pOut 0 change nothing
        assertEquals(expected, OffspringBand.childSd(m, List.<int[][]>of(homozygous(loci, 2)), new double[] {1}, 0, sigma, 1),
                EPS);
    }

    static double sum1(int a, int b, int[][] fathers, double[] w, double pOut) {
        List<int[][]> f = new java.util.ArrayList<>();
        for (int[] c : fathers) {
            f.add(new int[][] {c});
        }
        return OffspringBand.sumVariance(new int[][] {{a, b}}, f, w, pOut);
    }

    @Test
    void fathersMixIntoThePollen() {
        // mother 1/1, a father 2/2 always: the pollen allele is always 2, no spread
        assertEquals(0, sum1(1, 1, new int[][] {{2, 2}}, new double[] {1}, 1), EPS);
        // half selfing (1), half the father (2): pollen variance 1/4
        assertEquals(0.25, sum1(1, 1, new int[][] {{2, 2}}, new double[] {1}, 0.5), EPS);
        // zero total weight: selfing only
        assertEquals(0, sum1(1, 1, new int[][] {{2, 2}}, new double[] {0}, 1), EPS);
        // heterozygous 0/2 selfed: 1 from the mother's gamete + 1 from the pollen
        assertEquals(2, sum1(0, 2, new int[0][], new double[0], 0.7), EPS);
    }

    /**
     * Review 1, M1: one father per seed for every locus. Mother 1/1 at 4
     * loci, father 2/2, pOut 0.5: the pollen sum is 4 or 8 at even odds,
     * variance 4 (not 4 × 0.25 = 1 locus by locus).
     */
    @Test
    void theFatherChoiceIsSharedByEveryLocus() {
        assertEquals(4, OffspringBand.sumVariance(homozygous(4, 1), List.<int[][]>of(homozygous(4, 2)),
                new double[] {1}, 0.5), EPS);
        assertEquals(Math.sqrt(4.0 / (16 * 16)), OffspringBand.childSd(homozygous(4, 1),
                List.<int[][]>of(homozygous(4, 2)), new double[] {1}, 0.5, 0, 1), EPS);
    }

    /** The formula against the real seed code ({@code Offspring.of}), fixed seed, Monte Carlo. */
    @Test
    void agreesWithTheRealOffspringCode() {
        SpeciesDef def = CarrotLineDataTestAccess.wildCarrot();
        GenomeLayout layout = GenomeLayout.of(def);
        java.util.Random r = new java.util.Random(0x0FF5_B12DL);
        Genome mother = random(def, layout, r);
        Genome f1 = random(def, layout, r);
        Genome f2 = random(def, layout, r);
        LotRecord lot = new LotRecord(mother, List.of(new FatherPick(f1, 3, false), new FatherPick(f2, 1, true)), 0.6);
        io.github.slugblue.wildroots.genetics.api.MutationRates none =
                new io.github.slugblue.wildroots.genetics.api.MutationRates(0, 0);
        int n = 40_000;
        List<Locus> quant = layout.quantitative();
        double[] sum = new double[quant.size()];
        double[] sq = new double[quant.size()];
        for (int k = 0; k < n; k++) {
            Genome child = io.github.slugblue.wildroots.genetics.breeding.Offspring.of(lot, none, 0x5EED_0000L + k);
            for (int t = 0; t < quant.size(); t++) {
                double g = child.alleleSum(quant.get(t));
                sum[t] += g;
                sq[t] += g * g;
            }
        }
        for (int t = 0; t < quant.size(); t++) {
            Locus s = quant.get(t);
            double mc = sq[t] / n - (sum[t] / n) * (sum[t] / n);
            double exact = OffspringBand.sumVariance(OffspringBand.alleles(mother, s),
                    List.of(OffspringBand.alleles(f1, s), OffspringBand.alleles(f2, s)), new double[] {3, 1}, 0.6);
            assertEquals(exact, mc, 0.05 * exact + 0.02, s.trait().value() + ": formula " + exact + ", seeds " + mc);
        }
    }

    static Genome random(SpeciesDef def, GenomeLayout layout, java.util.Random r) {
        Genome.Builder b = Genome.builder(def.id(), layout);
        for (Locus s : layout.quantitative()) {
            for (int l = 0; l < s.loci(); l++) {
                b.set(s, l, 0, r.nextInt(3));
                b.set(s, l, 1, r.nextInt(3));
            }
        }
        return b.build();
    }

    @Test
    void stepsOfTheBand() {
        assertArrayEquals(new int[] {3, 3}, OffspringBand.steps(0.45, 0.05));
        assertArrayEquals(new int[] {2, 4}, OffspringBand.steps(0.45, 0.15));
        assertArrayEquals(new int[] {1, 1}, OffspringBand.steps(Double.NaN, Double.NaN));
        assertArrayEquals(new int[] {5, 5}, OffspringBand.steps(1.3, 0.2));
        assertArrayEquals(new int[] {4, 5}, OffspringBand.steps(0.9, 0.2));
    }

    @Test
    void ofLotReadsSigmaEnvFromTheSpecies() {
        SpeciesDef def = CarrotLineDataTestAccess.wildCarrot();
        GenomeLayout layout = GenomeLayout.of(def);
        Genome.Builder b = Genome.builder(def.id(), layout);
        for (Locus s : layout.quantitative()) {
            for (int l = 0; l < s.loci(); l++) {
                b.set(s, l, 0, 1);
                b.set(s, l, 1, 1);
            }
        }
        Genome g = b.build();
        double[] sd = OffspringBand.ofLot(new LotRecord(g, List.of(), 0), def, 1);
        List<QuantTrait> q = def.quantitative();
        assertEquals(q.size(), sd.length);
        for (int i = 0; i < q.size(); i++) {
            assertEquals(q.get(i).sigmaEnv(), sd[i], EPS, q.get(i).id().value());
        }
        // the same homozygous genome as the only father: still no genetic spread
        double[] same = OffspringBand.ofLot(new LotRecord(g, List.of(new FatherPick(g, 1, false)), 1), def, 1);
        assertArrayEquals(sd, same, EPS);
        // one heterozygous locus of the first trait widens only that trait
        Locus first = layout.section(q.get(0).id());
        Genome het = g.toBuilder().set(first, 0, 0, 0).set(first, 0, 1, 2).build();
        double[] wide = OffspringBand.ofLot(new LotRecord(het, List.of(), 0), def, 1);
        double effect = 2.0 / (4 * first.loci());
        assertEquals(Math.sqrt(effect * effect / 2 + q.get(0).sigmaEnv() * q.get(0).sigmaEnv()), wide[0], EPS);
        for (int i = 1; i < q.size(); i++) {
            assertEquals(sd[i], wide[i], EPS);
        }
        assertTrue(OffspringBand.noiseOnly(def).length == q.size());
    }
}
