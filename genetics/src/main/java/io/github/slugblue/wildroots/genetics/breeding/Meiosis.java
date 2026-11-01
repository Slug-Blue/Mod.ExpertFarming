package io.github.slugblue.wildroots.genetics.breeding;

import io.github.slugblue.wildroots.genetics.api.MutationRates;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;

/**
 * Meiosis with free recombination (GDD §3.8, no linkage): for every locus one
 * of the two parental alleles is taken with probability 1/2, then mutated.
 * A gamete is written straight into one allele slot of the child.
 */
public final class Meiosis {

    private Meiosis() {
    }

    /**
     * Writes a gamete of {@code parent} into slot {@code which} (0 maternal,
     * 1 paternal) of {@code child}.
     */
    public static void gameteInto(Genome parent, MutationRates rates, SplitMix64 rng, Genome.Builder child,
                                  int which) {
        for (Locus s : parent.layout().sections()) {
            for (int l = 0; l < s.loci(); l++) {
                int a = parent.allele(s, l, rng.nextBoolean() ? 1 : 0);
                child.set(s, l, which, Mutation.apply(a, s, rates, rng));
            }
        }
    }

    /** Gamete alleles of one section, for tests: {@code [locus]}. */
    public static int[] gamete(Genome parent, Locus s, MutationRates rates, SplitMix64 rng) {
        int[] out = new int[s.loci()];
        for (int l = 0; l < s.loci(); l++) {
            out[l] = Mutation.apply(parent.allele(s, l, rng.nextBoolean() ? 1 : 0), s, rates, rng);
        }
        return out;
    }
}
