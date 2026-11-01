package io.github.slugblue.wildroots.genetics.breeding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.MutationRates;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.model.TraitKind;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.ChiSquare;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import java.util.List;
import org.junit.jupiter.api.Test;

/** WR-105: meiosis, mutations, offspring and clones (GDD §3.6, §3.8; architecture §4.2). */
class BreedingTest {

    /** Mendel tests run without mutation so that they test segregation only. */
    static final MutationRates NONE = new MutationRates(0, 0);
    static final MutationRates CARROT = Carrot.SPECIES.mutation();
    static final Locus BITTER = Carrot.LAYOUT.section(Carrot.BITTER);

    static Genome withQual(Locus s, int a, int b) {
        return Genome.builder(Carrot.SPECIES.id(), Carrot.LAYOUT).set(s, 0, 0, a).set(s, 0, 1, b).build();
    }

    static LotRecord cross(Genome mother, Genome father) {
        return new LotRecord(mother, List.of(new FatherPick(father, 1, false)), 1.0);
    }

    @Test
    void homozygousCrossGivesOnlyHeterozygotes() {
        LotRecord lot = cross(withQual(BITTER, 1, 1), withQual(BITTER, 0, 0));
        for (int i = 0; i < 1000; i++) {
            Genome c = Offspring.of(lot, NONE, Hash64.of(105, i));
            assertEquals(1, c.allele(BITTER, 0, 0) + c.allele(BITTER, 0, 1), "seed " + i);
        }
    }

    @Test
    void heterozygousCrossSegregatesOneTwoOne() {
        Genome aa = withQual(BITTER, 1, 0);
        LotRecord lot = cross(aa, withQual(BITTER, 0, 1));
        long[] genotypes = new long[3];
        int children = 50_000; // 100 000 gametes
        for (int i = 0; i < children; i++) {
            Genome c = Offspring.of(lot, NONE, Hash64.of(1052, i));
            genotypes[c.allele(BITTER, 0, 0) + c.allele(BITTER, 0, 1)]++;
        }
        double p = ChiSquare.pValue(genotypes, new double[] {1, 2, 1});
        assertTrue(p > 0.01, "1:2:1 p=" + p + " counts " + java.util.Arrays.toString(genotypes));
    }

    @Test
    void heterozygousCrossShowsDominantPhenotypeThreeToOne() {
        // bitterness: wild allele dominant → bitter phenotype unless both alleles are domestic
        QualTrait def = Carrot.SPECIES.qual(Carrot.BITTER);
        LotRecord lot = cross(withQual(BITTER, 1, 0), withQual(BITTER, 0, 1));
        long[] pheno = new long[2];
        for (int i = 0; i < 2000; i++) {
            Genome c = Offspring.of(lot, NONE, Hash64.of(1053, i));
            boolean domestic = def.domesticPhenotype(c.allele(BITTER, 0, 0), c.allele(BITTER, 0, 1));
            pheno[domestic ? 1 : 0]++;
        }
        double p = ChiSquare.pValue(pheno, new double[] {3, 1});
        assertTrue(p > 0.01, "3:1 p=" + p + " counts " + java.util.Arrays.toString(pheno));
    }

    @Test
    void quantitativeMutationRateWithinThreeSigma() {
        // all quantitative alleles 1: every mutation changes the value
        Genome.Builder b = Genome.builder(Carrot.SPECIES.id(), Carrot.LAYOUT);
        for (Locus s : Carrot.LAYOUT.quantitative()) {
            for (int l = 0; l < s.loci(); l++) {
                b.set(s, l, 0, 1).set(s, l, 1, 1);
            }
        }
        Genome mid = b.build();
        SplitMix64 rng = new SplitMix64(1054);
        long loci = 0;
        long mutated = 0;
        while (loci < 1_000_000) {
            for (Locus s : Carrot.LAYOUT.quantitative()) {
                for (int v : Meiosis.gamete(mid, s, CARROT, rng)) {
                    loci++;
                    if (v != 1) {
                        mutated++;
                    }
                }
            }
        }
        assertWithin3Sigma(mutated, loci, CARROT.quantitative());
    }

    @Test
    void qualitativeMutationRateWithinThreeSigma() {
        Genome zero = Genome.builder(Carrot.SPECIES.id(), Carrot.LAYOUT).build();
        SplitMix64 rng = new SplitMix64(1055);
        long loci = 0;
        long mutated = 0;
        while (loci < 1_000_000) {
            for (Locus s : Carrot.LAYOUT.qualitative()) {
                int v = Meiosis.gamete(zero, s, CARROT, rng)[0];
                loci++;
                if (v != 0) {
                    assertEquals(1, v);
                    mutated++;
                }
            }
        }
        assertWithin3Sigma(mutated, loci, CARROT.qualitative());
    }

    static void assertWithin3Sigma(long k, long n, double p) {
        double mean = n * p;
        double sigma = Math.sqrt(n * p * (1 - p));
        assertTrue(Math.abs(k - mean) <= 3 * sigma, k + " mutations of " + n + ", expected " + mean + " ± " + 3 * sigma);
    }

    @Test
    void quantitativeStepIsPlusMinusOneClippedToZeroTwo() {
        Locus q = Carrot.LAYOUT.section(Carrot.YIELD);
        MutationRates always = new MutationRates(1, 1);
        SplitMix64 rng = new SplitMix64(1056);
        long[] fromOne = new long[3];
        for (int i = 0; i < 10_000; i++) {
            assertTrue(Mutation.apply(0, q, always, rng) <= 1);
            assertTrue(Mutation.apply(2, q, always, rng) >= 1);
            fromOne[Mutation.apply(1, q, always, rng)]++;
        }
        assertEquals(0, fromOne[1], "from 1 the step always moves");
        assertTrue(ChiSquare.pValue(new long[] {fromOne[0], fromOne[2]}, new double[] {1, 1}) > 0.01);
        Locus c = Carrot.LAYOUT.section(Carrot.Y1);
        assertEquals(1, Mutation.apply(0, c, always, rng));
        assertEquals(0, Mutation.apply(1, c, always, rng));
        Locus load = Carrot.LAYOUT.load();
        assertEquals(1, Mutation.apply(1, load, always, rng), "load loci do not mutate");
    }

    @Test
    void sLocusMutationSwitchesToAnotherAllele() {
        GenomeLayout l = GenomeLayout.of(List.of(new GenomeLayout.SectionSpec(TraitId.of("s"),
                TraitKind.QUALITATIVE, 1, 10)));
        Locus s = l.section(TraitId.of("s"));
        SplitMix64 rng = new SplitMix64(1057);
        long[] hist = new long[10];
        for (int i = 0; i < 9000; i++) {
            int v = Mutation.apply(4, s, new MutationRates(0, 1), rng);
            hist[v]++;
        }
        assertEquals(0, hist[4]);
        long[] others = new long[9];
        for (int i = 0, j = 0; i < 10; i++) {
            if (i != 4) {
                others[j++] = hist[i];
            }
        }
        assertTrue(ChiSquare.pValue(others, new double[] {1, 1, 1, 1, 1, 1, 1, 1, 1}) > 0.01);
    }

    @Test
    void cloneIsBitwiseEqual() {
        SplitMix64 rng = new SplitMix64(1058);
        for (int i = 0; i < 1000; i++) {
            Genome g = RandomGenomes.carrot(rng);
            Genome c = Cloning.clone(g);
            assertNotSame(g, c);
            assertArrayEquals(g.bits(), c.bits());
            assertEquals(g, c);
        }
    }

    @Test
    void offspringIsDeterministicAndSelfsWithoutFathers() {
        Genome m = RandomGenomes.carrot(new SplitMix64(1));
        Genome f = RandomGenomes.carrot(new SplitMix64(2));
        LotRecord lot = new LotRecord(m, List.of(new FatherPick(f, 1, false)), 0.6);
        assertEquals(Offspring.of(lot, CARROT, 42), Offspring.of(lot, CARROT, 42));
        LotRecord selfed = new LotRecord(withQual(BITTER, 1, 1), List.of(), 1.0);
        for (int i = 0; i < 200; i++) {
            Genome c = Offspring.of(selfed, NONE, i);
            assertEquals(2, c.allele(BITTER, 0, 0) + c.allele(BITTER, 0, 1));
        }
    }

    @Test
    void outcrossShareFollowsPOut() {
        Genome m = withQual(BITTER, 1, 1);
        Genome f = withQual(BITTER, 0, 0);
        LotRecord lot = new LotRecord(m, List.of(new FatherPick(f, 1, false)), 0.6);
        long[] counts = new long[2];
        for (int i = 0; i < 10_000; i++) {
            Genome c = Offspring.of(lot, NONE, Hash64.of(1059, i));
            counts[c.allele(BITTER, 0, 1) == 0 ? 1 : 0]++;
        }
        assertTrue(ChiSquare.pValue(counts, new double[] {0.4, 0.6}) > 0.01, java.util.Arrays.toString(counts));
    }
}
