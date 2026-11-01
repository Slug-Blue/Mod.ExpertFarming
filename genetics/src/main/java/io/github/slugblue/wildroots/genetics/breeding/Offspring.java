package io.github.slugblue.wildroots.genetics.breeding;

import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.MutationRates;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import java.util.List;

/**
 * Seed of a lot realised at planting (GDD §3.8, §5.3): mother meiosis ×
 * meiosis of the father. With probability {@code pOut} the father is drawn
 * from the lot fathers by weight, otherwise the seed is selfed. The RNG key
 * comes from the mod layer: {@code Hash64(secret, lotId, remainingCount,
 * packedPos)} (docs/05-architecture.md §4.3).
 */
public final class Offspring {

    private Offspring() {
    }

    /** Father chosen for one seed: the mother itself when selfed. */
    public static Genome chooseFather(LotRecord lot, SplitMix64 rng) {
        List<FatherPick> fathers = lot.fathers();
        if (fathers.isEmpty() || !rng.chance(lot.pOut())) {
            return lot.mother();
        }
        double[] w = new double[fathers.size()];
        double total = 0;
        for (int i = 0; i < w.length; i++) {
            w[i] = Math.max(0, fathers.get(i).weight());
            total += w[i];
        }
        if (!(total > 0)) {
            return lot.mother();
        }
        return fathers.get(rng.pick(w)).genome();
    }

    public static Genome of(LotRecord lot, MutationRates rates, long rngKey) {
        SplitMix64 rng = new SplitMix64(rngKey);
        Genome mother = lot.mother();
        Genome father = chooseFather(lot, rng);
        if (!father.layout().equals(mother.layout())) {
            father = mother; // a father in another layout is never mixed in (migrated before use)
        }
        return cross(mother, father, rates, rng);
    }

    /** Child of two parents of one layout. */
    public static Genome cross(Genome mother, Genome father, MutationRates rates, SplitMix64 rng) {
        Genome.Builder child = Genome.builder(mother.species(), mother.layout());
        Meiosis.gameteInto(mother, rates, rng, child, 0);
        Meiosis.gameteInto(father, rates, rng, child, 1);
        return child.build();
    }
}
