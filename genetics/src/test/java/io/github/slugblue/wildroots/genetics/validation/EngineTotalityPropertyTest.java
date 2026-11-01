package io.github.slugblue.wildroots.genetics.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.AlleleFreq;
import io.github.slugblue.wildroots.genetics.api.Bees;
import io.github.slugblue.wildroots.genetics.api.Budget;
import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.Dominance;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.LoadDef;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.MutationRates;
import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.Pollination;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.Range;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Timing;
import io.github.slugblue.wildroots.genetics.api.Tradeoff;
import io.github.slugblue.wildroots.genetics.api.WildFrequencies;
import io.github.slugblue.wildroots.genetics.breeding.Offspring;
import io.github.slugblue.wildroots.genetics.classify.CultivarClassifier;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.phenotype.PhenotypeCalculator;
import io.github.slugblue.wildroots.genetics.pollination.Candidate;
import io.github.slugblue.wildroots.genetics.pollination.Donor;
import io.github.slugblue.wildroots.genetics.pollination.FatherSelector;
import io.github.slugblue.wildroots.genetics.population.WildPopulation;
import io.github.slugblue.wildroots.genetics.quality.Booster;
import io.github.slugblue.wildroots.genetics.quality.Grade;
import io.github.slugblue.wildroots.genetics.quality.GradedInput;
import io.github.slugblue.wildroots.genetics.quality.ProcessingQuality;
import io.github.slugblue.wildroots.genetics.quality.ProcessingResult;
import io.github.slugblue.wildroots.genetics.quality.QualityIndex;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * WR-110 / SEC-30 property test: on 10 000 random valid definitions and
 * genomes the engine functions do not throw and give finite results in
 * {@code [0, 1]} (grades in 1–4).
 */
class EngineTotalityPropertyTest {

    @Test
    void engineIsTotalOnRandomValidDefinitions() {
        SplitMix64 rng = new SplitMix64(110);
        for (int n = 0; n < 10_000; n++) {
            SpeciesDef s = randomSpecies(rng, n);
            List<Problem> problems = DefinitionValidator.validate(s);
            assertTrue(!DefinitionValidator.hasErrors(problems), () -> "generator made an invalid species: " + problems);
            List<CultivarDef> profiles = List.of(randomCultivar(rng, s, "a"), randomCultivar(rng, s, "b"));
            for (CultivarDef c : profiles) {
                assertTrue(!DefinitionValidator.hasErrors(DefinitionValidator.validate(c, s)));
            }
            exercise(s, profiles, rng, n);
        }
    }

    static void exercise(SpeciesDef s, List<CultivarDef> profiles, SplitMix64 rng, int n) {
        CompiledSpecies cs = new CompiledSpecies(s);
        GenomeLayout l = cs.layout();
        Genome wild = WildPopulation.wildPlant(s, l, new PatchPassport(s.id(), "cat" + n % 5, rng.nextBoolean(),
                rng.nextLong(), rng.nextBoolean(), rng.nextLong()), rng.nextLong());
        Genome any = RandomGenomes.uniform(s.id(), l, rng);
        for (Genome g : List.of(wild, any)) {
            Environment env = new Environment(rng.nextDouble() * 8 - 4, rng.nextBoolean(), rng.nextInt(16),
                    rng.nextDouble() * 0.05);
            Phenotype p = PhenotypeCalculator.compute(cs, g, env, PhenotypeCalculator.rollNoise(cs, rng.nextLong()));
            unit(p.pArray());
            unit(p.gArray());
            unit(p.gEffArray());
            assertTrue(p.vigor() > 0 && p.vigor() <= 1);
            for (CultivarDef c : profiles) {
                double q = QualityIndex.of(p, c.qualityAxes());
                unit(new double[] {q});
                int grade = Grade.of(q, p.bitter());
                assertTrue(grade >= 1 && grade <= 4);
            }
            Classification cl = CultivarClassifier.classify(cs, g, profiles,
                    rng.nextBoolean() ? Optional.of(profiles.get(0).id()) : Optional.empty());
            assertTrue(cl.tier() >= 0 && cl.tier() <= 2);
        }
        List<Candidate> cands = new ArrayList<>();
        int k = rng.nextInt(100);
        for (int i = 0; i < k; i++) {
            cands.add(new Candidate(RandomGenomes.uniform(s.id(), l, rng), rng.nextInt(33) - 16, rng.nextInt(7) - 3,
                    rng.nextInt(33) - 16));
        }
        List<Donor> donors = new ArrayList<>();
        for (int i = 0; i < rng.nextInt(6); i++) {
            donors.add(new Donor(RandomGenomes.uniform(s.id(), l, rng), rng.nextDouble() * 3, rng.nextBoolean()));
        }
        FatherSelector.Selection sel = FatherSelector.choose(s, wild, cands, donors, rng.nextLong());
        assertTrue(sel.examined() <= 64 && sel.fathers().size() <= 8);
        for (FatherPick f : sel.fathers()) {
            assertTrue(Double.isFinite(f.weight()) && f.weight() >= 0);
        }
        Genome child = Offspring.of(new LotRecord(wild, sel.fathers(), rng.nextDouble()), s.mutation(), rng.nextLong());
        assertEquals(l, child.layout());
        List<GradedInput> in = new ArrayList<>();
        for (int i = 0; i < 1 + rng.nextInt(9); i++) {
            in.add(new GradedInput(1 + rng.nextInt(4), rng.nextInt(3), 1 + rng.nextInt(64), rng.nextBoolean()));
        }
        List<Booster> b = new ArrayList<>();
        for (int i = 0; i < rng.nextInt(9); i++) {
            b.add(new Booster(rng.nextBoolean()));
        }
        ProcessingResult r = ProcessingQuality.process(in, b);
        assertTrue(r.grade() >= 1 && r.grade() <= 4);
    }

    static void unit(double[] xs) {
        for (double x : xs) {
            assertTrue(Double.isFinite(x) && x >= 0 && x <= 1, "value " + x);
        }
    }

    static double u(SplitMix64 rng, double lo, double hi) {
        return lo + (hi - lo) * rng.nextDouble();
    }

    static AlleleFreq freq(SplitMix64 rng) {
        double a = rng.nextDouble();
        double b = rng.nextDouble();
        double c = rng.nextDouble() + 1e-9;
        double sum = a + b + c;
        double p0 = a / sum;
        double p1 = b / sum;
        return new AlleleFreq(p0, p1, 1 - p0 - p1);
    }

    static SpeciesDef randomSpecies(SplitMix64 rng, int n) {
        int nq = 1 + rng.nextInt(8);
        List<QuantTrait> quant = new ArrayList<>();
        Map<TraitId, Integer> elite = new TreeMap<>();
        int bits = 0;
        for (int i = 0; i < nq; i++) {
            int loci = 1 + rng.nextInt(16);
            if (bits + loci * 4 > 400) {
                loci = 1;
            }
            bits += loci * 4;
            TraitId id = TraitId.of(i == 0 ? "yield" : i == 1 ? "fruit_size" : "t" + i);
            quant.add(new QuantTrait(id, loci, u(rng, 0, 0.5), rng.nextBoolean()));
            elite.put(id, rng.nextInt(loci + 1) / 2);
        }
        List<QualTrait> qual = new ArrayList<>();
        int nc = rng.nextInt(5);
        for (int i = 0; i < nc; i++) {
            qual.add(new QualTrait(TraitId.of(i == 0 ? "bitterness" : "c" + i),
                    rng.nextBoolean() ? Dominance.WILD : Dominance.DOMESTIC, rng.nextDouble(), rng.nextBoolean(), 2,
                    false, 2));
        }
        boolean si = rng.nextInt(4) == 0;
        if (si) {
            int alleles = 2 + rng.nextInt(15);
            qual.add(new QualTrait(TraitId.of("self_incompat"), Dominance.WILD, 0, false, alleles, true,
                    1 + rng.nextInt(alleles)));
        }
        List<Tradeoff> trade = new ArrayList<>();
        for (int i = 0; i < rng.nextInt(10); i++) {
            trade.add(new Tradeoff(quant.get(rng.nextInt(nq)).id(), quant.get(rng.nextInt(nq)).id(), u(rng, 0, 10),
                    rng.nextDouble()));
        }
        Budget budget = Budget.NONE;
        if (rng.nextBoolean()) {
            Map<TraitId, Double> w = new TreeMap<>();
            for (QuantTrait q : quant) {
                if (rng.nextBoolean()) {
                    w.put(q.id(), u(rng, 0, 10));
                }
            }
            budget = new Budget(u(rng, 0.01, 32), w);
        }
        Map<TraitId, Double> axes = new TreeMap<>();
        axes.put(quant.get(0).id(), 0.5);
        axes.put(quant.get(nq - 1).id(), nq == 1 ? 0.5 : 0.5);
        return new SpeciesDef("test:s" + n, u(rng, -2, 0), u(rng, 0, 2), quant, trade, budget,
                new WildFrequencies(freq(rng), freq(rng), elite, freq(rng), Map.of()), rng.nextDouble(), qual,
                Map.of(), new LoadDef(rng.nextInt(17), rng.nextDouble(), u(rng, 0.01, 1)),
                new MutationRates(u(rng, 0, 0.05), u(rng, 0, 0.05)),
                new Pollination("insect", !si, rng.nextInt(17), rng.nextDouble(),
                        rng.nextBoolean() ? Optional.of(new Bees(rng.nextDouble(), rng.nextInt(65))) : Optional.empty(),
                        rng.nextDouble()),
                new Timing(u(rng, 0.1, 1440), 0), axes);
    }

    static CultivarDef randomCultivar(SplitMix64 rng, SpeciesDef s, String suffix) {
        Map<TraitId, Range> q = new TreeMap<>();
        for (QuantTrait t : s.quantitative()) {
            if (rng.nextBoolean()) {
                double a = rng.nextDouble();
                double b = rng.nextDouble();
                q.put(t.id(), new Range(Math.min(a, b), Math.max(a, b)));
            }
        }
        Map<TraitId, QualReq> qual = new TreeMap<>();
        for (QualTrait t : s.qualitative()) {
            if (t.alleles() == 2 && rng.nextBoolean()) {
                qual.put(t.id(), rng.nextBoolean() ? QualReq.DOMESTIC : QualReq.WILD);
            }
        }
        return new CultivarDef(CultivarId.of("test:" + suffix), s.id(), 1 + rng.nextInt(2), rng.nextInt(2001) - 1000,
                q, qual, List.of(), u(rng, 0, 0.5), Map.of(s.quantitative().get(0).id(), 1.0));
    }
}
