package io.github.slugblue.wildroots.genetics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.breeding.Offspring;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import io.github.slugblue.wildroots.genetics.phenotype.PhenotypeCalculator;
import io.github.slugblue.wildroots.genetics.pollination.Candidate;
import io.github.slugblue.wildroots.genetics.pollination.FatherSelector;
import io.github.slugblue.wildroots.genetics.population.VillageTemplate;
import io.github.slugblue.wildroots.genetics.population.WildPopulation;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TAE, WR-101/ADR-0001: "a seed gives the same plant everywhere". Digests of
 * the keyed engine outputs (wild plants, village plants, fathers, offspring,
 * ε and P) are pinned. A same-JVM determinism check cannot see an order that
 * depends on the per-JVM salt of {@code Map.of}/{@code Set.of} iteration or on
 * identity hash codes; a pinned digest fails on the run where that order
 * changes, so it surfaces as a red build instead of a silent world drift.
 *
 * <p>A deliberate change of the generation algorithm changes these digests:
 * update them in the same commit and say so (old worlds regenerate wild
 * patches differently).
 */
class DeterminismGoldenTest {

    static final long SEED = 20261101L;
    static final CompiledSpecies CS = new CompiledSpecies(Carrot.SPECIES);

    static long fold(long acc, Genome g) {
        return Hash64.of(acc, Hash64.of(g.bits()));
    }

    static long fold(long acc, double[] xs) {
        for (double x : xs) {
            acc = Hash64.of(acc, Double.doubleToLongBits(x));
        }
        return acc;
    }

    static List<Genome> wild(int n) {
        PatchPassport meadow = new PatchPassport(Carrot.SPECIES.id(), "meadow", false, 12345L, false, SEED);
        List<Genome> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(WildPopulation.wildPlant(Carrot.SPECIES, Carrot.LAYOUT, meadow, Hash64.of(SEED, 12345L, i)));
        }
        return out;
    }

    @Test
    void wildAndVillagePlantsArePinned() {
        long acc = 0;
        for (Genome g : wild(200)) {
            acc = fold(acc, g);
        }
        PatchPassport rich = new PatchPassport(Carrot.SPECIES.id(), "taiga", true, 77L, true, SEED);
        for (int i = 0; i < 100; i++) {
            acc = fold(acc, WildPopulation.wildPlant(Carrot.SPECIES, Carrot.LAYOUT, rich, Hash64.of(SEED, 77L, i)));
        }
        VillageTemplate t = VillageTemplate.fromLandrace(Carrot.SPECIES, Carrot.EASTERN, Map.of(Carrot.Y2, 0.30),
                Map.of());
        for (int i = 0; i < 100; i++) {
            acc = fold(acc, t.plant(Carrot.SPECIES, Carrot.LAYOUT, Hash64.of(SEED, 42L, i)));
        }
        assertEquals(Long.toHexString(WILD_AND_VILLAGE), Long.toHexString(acc), "wild/village digest");
    }

    @Test
    void fathersOffspringNoiseAndPhenotypeArePinned() {
        List<Genome> pop = wild(64);
        long acc = 0;
        for (int m = 0; m < 16; m++) {
            Genome mother = pop.get(m);
            List<Candidate> nearby = new ArrayList<>();
            for (int k = 16; k < 64; k++) {
                nearby.add(new Candidate(pop.get(k), (k % 5) - 2, 0, (k / 5) % 5 - 2));
            }
            FatherSelector.Selection sel = FatherSelector.choose(Carrot.SPECIES, mother, nearby, List.of(),
                    Hash64.of(SEED, m, 1));
            for (var f : sel.fathers()) {
                acc = Hash64.of(fold(acc, f.genome()), Double.doubleToLongBits(f.weight()));
            }
            LotRecord lot = new LotRecord(mother, sel.fathers(), 0.6);
            for (int i = 0; i < 8; i++) {
                Genome child = Offspring.of(lot, Carrot.SPECIES.mutation(), Hash64.of(SEED, m, i));
                acc = fold(acc, child);
                NoiseVector n = PhenotypeCalculator.rollNoise(CS, Hash64.of(SEED, m, i, 7));
                acc = fold(acc, PhenotypeCalculator.compute(CS, child, Environment.neutral(0.6), n).pArray());
            }
        }
        assertEquals(Long.toHexString(BREEDING), Long.toHexString(acc), "breeding digest");
    }

    static final long WILD_AND_VILLAGE = 0xf80c5a5e7920e2a1L;
    static final long BREEDING = 0xb87f9988b3c94868L;
}
