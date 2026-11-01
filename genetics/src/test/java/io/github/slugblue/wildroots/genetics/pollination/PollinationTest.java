package io.github.slugblue.wildroots.genetics.pollination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.Dominance;
import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.Pollination;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.ChiSquare;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** WR-106: fathers, outcrossing and seed set (GDD §4.1, architecture §4.2, ADR-0008). */
class PollinationTest {

    static final SpeciesDef CARROT = Carrot.SPECIES;
    static final SplitMix64 GEN = new SplitMix64(106);
    static final Genome MOTHER = RandomGenomes.carrot(GEN);

    /** Carrot with a bigger radius so that 200 neighbours fit around one plant. */
    static SpeciesDef carrotRadius(int radius) {
        Pollination p = CARROT.pollination();
        return new SpeciesDef(CARROT.id(), CARROT.tempMin(), CARROT.tempMax(), CARROT.quantitative(),
                CARROT.tradeoffs(), CARROT.budget(), CARROT.wild(), CARROT.wildF(), CARROT.qualitative(),
                CARROT.starterFreq(), CARROT.load(), CARROT.mutation(), new Pollination(p.mode(), p.selfCompatible(),
                radius, p.outcross(), p.bees(), p.seedSetNoPollinator()), CARROT.timing(), CARROT.qualityAxes());
    }

    @Test
    void atMost64ExaminedAndAtMost4FathersOf200() {
        SpeciesDef s = carrotRadius(16);
        List<Candidate> many = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            many.add(new Candidate(RandomGenomes.carrot(GEN), i % 15 - 7, 0, i / 15 - 7));
        }
        for (int run = 0; run < 200; run++) {
            FatherSelector.Selection sel = FatherSelector.choose(s, MOTHER, many, List.of(), Hash64.of(1061, run));
            assertEquals(64, sel.examined());
            assertTrue(sel.fathers().size() <= 4 && !sel.fathers().isEmpty());
            assertEquals(4.0, sel.fathers().stream().mapToDouble(FatherPick::weight).sum());
        }
    }

    @Test
    void reservoirSamplesEveryCandidateEqually() {
        SpeciesDef s = carrotRadius(16);
        List<Candidate> many = new ArrayList<>();
        Map<Genome, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < 128; i++) {
            Genome g = RandomGenomes.carrot(GEN);
            index.put(g, i);
            many.add(new Candidate(g, 0, 0, 0)); // equal weights: picks follow the reservoir
        }
        long[] counts = new long[128];
        for (int run = 0; run < 20_000; run++) {
            for (FatherPick f : FatherSelector.choose(s, MOTHER, many, List.of(), Hash64.of(1062, run)).fathers()) {
                counts[index.get(f.genome())] += (long) f.weight();
            }
        }
        double[] eq = new double[128];
        java.util.Arrays.fill(eq, 1);
        assertTrue(ChiSquare.pValue(counts, eq) > 0.01);
    }

    @Test
    void choiceFrequenciesFollowInverseSquareWeights() {
        SpeciesDef s = carrotRadius(8);
        List<Candidate> near = new ArrayList<>();
        Map<Genome, Integer> index = new IdentityHashMap<>();
        double[] expected = new double[8];
        for (int i = 0; i < 8; i++) {
            Genome g = RandomGenomes.carrot(GEN);
            index.put(g, i);
            near.add(new Candidate(g, i, 0, 0));
            expected[i] = 1.0 / (1 + i * i);
        }
        long[] counts = new long[8];
        for (int run = 0; run < 100_000; run++) {
            for (FatherPick f : FatherSelector.choose(s, MOTHER, near, List.of(), Hash64.of(1063, run)).fathers()) {
                counts[index.get(f.genome())] += (long) f.weight();
            }
        }
        double p = ChiSquare.pValue(counts, expected);
        assertTrue(p > 0.01, "p=" + p + " " + java.util.Arrays.toString(counts));
    }

    @Test
    void neighboursOutsideRadiusOrHeightAreIgnored() {
        List<Candidate> c = List.of(new Candidate(RandomGenomes.carrot(GEN), 2, 0, 1),
                new Candidate(RandomGenomes.carrot(GEN), 1, 3, 0), new Candidate(RandomGenomes.carrot(GEN), 2, 2, 0));
        FatherSelector.Selection sel = FatherSelector.choose(CARROT, MOTHER, c, List.of(), 1);
        assertEquals(1, sel.examined());
        assertSame(c.get(2).genome(), sel.fathers().get(0).genome());
    }

    @Test
    void donorsAreAtMostFourAndTheFifthPushesOutTheOldest() {
        List<Genome> g = new ArrayList<>();
        DonorBuffer b = DonorBuffer.EMPTY;
        for (int i = 0; i < 5; i++) {
            g.add(RandomGenomes.carrot(GEN));
            b = b.add(Donor.bee(g.get(i)));
        }
        assertEquals(4, b.size());
        assertSame(g.get(1), b.donors().get(0).genome());
        assertSame(g.get(4), b.donors().get(3).genome());
        FatherSelector.Selection sel = FatherSelector.choose(CARROT, MOTHER, List.of(), b.donors(), 1);
        assertEquals(4, sel.fathers().size());
        assertTrue(sel.fathers().stream().allMatch(FatherPick::donor));
        assertEquals(2.0, sel.fathers().get(0).weight());
    }

    @Test
    void pOutFollowsNeighbourCountBeesAndBrush() {
        assertEquals(0.60 * 1 / 3.0, Outcrossing.pOut(CARROT, 1, false, false, false), 1e-12);
        assertEquals(0.60 * 2 / 3.0, Outcrossing.pOut(CARROT, 2, false, false, false), 1e-12);
        assertEquals(0.60, Outcrossing.pOut(CARROT, 3, false, false, false), 1e-12);
        assertEquals(0.60, Outcrossing.pOut(CARROT, 30, false, false, false), 1e-12);
        assertEquals(0.95, Outcrossing.pOut(CARROT, 0, true, false, true), 1e-12);
        assertEquals(0.90, Outcrossing.pOut(CARROT, 5, true, true, true), 1e-12);
        assertEquals(0.0, Outcrossing.pOut(CARROT, 0, false, false, false), "lone plant selfs 100 %");
    }

    @Test
    void lonePlantOffspringIsAlwaysSelfed() {
        FatherSelector.Selection sel = FatherSelector.choose(CARROT, MOTHER, List.of(), List.of(), 5);
        assertTrue(sel.fathers().isEmpty());
        assertEquals(0, sel.compatibleNeighbours());
    }

    @Test
    void seedFormulaAndCarrotWithoutPollinator() {
        assertEquals(3 + 9 * 0.5, SeedSet.expected(CARROT, 0.5, true, false), 1e-12);
        assertEquals((3 + 9 * 0.5) * 0.5, SeedSet.expected(CARROT, 0.5, false, false), 1e-12);
        assertEquals(0.5, SeedSet.seedSet(CARROT, false));
        assertEquals((3 + 9 * 1.0) * 0.35, SeedSet.expected(CARROT, 1.0, true, true), 1e-12);
        SplitMix64 rng = new SplitMix64(1);
        long sum = 0;
        for (int i = 0; i < 100_000; i++) {
            sum += SeedSet.realise(5.7, rng);
        }
        assertEquals(5.7, sum / 100_000.0, 0.01);
    }

    @Test
    void sharedSAlleleRejectsPollen() {
        TraitId sid = TraitId.of("self_incompat");
        List<QualTrait> qual = new ArrayList<>(CARROT.qualitative());
        qual.add(new QualTrait(sid, Dominance.WILD, 0, false, 10, true, 6));
        Pollination p = CARROT.pollination();
        SpeciesDef si = new SpeciesDef("t:si", 0, 1, CARROT.quantitative(), CARROT.tradeoffs(), CARROT.budget(),
                CARROT.wild(), 0, qual, Map.of(), CARROT.load(), CARROT.mutation(),
                new Pollination("insect", false, 2, 1.0, Optional.empty(), 0.5), CARROT.timing(), Map.of());
        GenomeLayout l = GenomeLayout.of(si);
        Locus s = l.section(sid);
        Genome m = Genome.builder("t:si", l).set(s, 0, 0, 1).set(s, 0, 1, 2).build();
        Genome shared = Genome.builder("t:si", l).set(s, 0, 0, 2).set(s, 0, 1, 7).build();
        Genome other = Genome.builder("t:si", l).set(s, 0, 0, 3).set(s, 0, 1, 7).build();
        assertFalse(SelfIncompatibility.compatible(si, m, shared));
        assertFalse(SelfIncompatibility.compatible(si, m, m));
        assertTrue(SelfIncompatibility.compatible(si, m, other));
        FatherSelector.Selection sel = FatherSelector.choose(si, m,
                List.of(new Candidate(shared, 1, 0, 0), new Candidate(other, 1, 0, 0)), List.of(Donor.bee(shared)), 9);
        assertEquals(1, sel.compatibleNeighbours());
        assertTrue(sel.fathers().stream().allMatch(f -> f.genome() == other));
    }
}
