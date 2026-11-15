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

    @Test
    void fathersMixIntoThePollen() {
        // mother 1/1, a father 2/2 always: the pollen allele is always 2, no spread
        assertEquals(0, OffspringBand.locusVariance(1, 1, new int[][] {{2, 2}}, new double[] {1}, 1), EPS);
        // half selfing (1), half the father (2): pollen variance 1/4
        assertEquals(0.25, OffspringBand.locusVariance(1, 1, new int[][] {{2, 2}}, new double[] {1}, 0.5), EPS);
        // zero total weight: selfing only
        assertEquals(0, OffspringBand.locusVariance(1, 1, new int[][] {{2, 2}}, new double[] {0}, 1), EPS);
        // heterozygous 0/2 selfed: 1 from the mother's gamete + 1 from the pollen
        assertEquals(2, OffspringBand.locusVariance(0, 2, new int[0][], new double[0], 0.7), EPS);
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
