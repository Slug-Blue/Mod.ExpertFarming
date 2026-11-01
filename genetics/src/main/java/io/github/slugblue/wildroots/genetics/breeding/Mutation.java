package io.github.slugblue.wildroots.genetics.breeding;

import io.github.slugblue.wildroots.genetics.api.MutationRates;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;

/**
 * Point mutations per allele and meiosis (GDD §3.8): quantitative alleles
 * step ±1 and are clipped to {@code [0, 2]}; qualitative alleles switch
 * (biallelic: 0 ↔ 1; S-locus: to another allele, uniformly). Load loci do
 * not mutate: GDD §3.7–3.8 give them no rate.
 */
public final class Mutation {

    private Mutation() {
    }

    /** Possibly mutated copy of {@code allele} of section {@code s}. */
    public static int apply(int allele, Locus s, MutationRates rates, SplitMix64 rng) {
        switch (s.kind()) {
            case QUANTITATIVE -> {
                if (rng.chance(rates.quantitative())) {
                    int v = allele + (rng.nextBoolean() ? 1 : -1);
                    return v < 0 ? 0 : Math.min(v, 2);
                }
                return allele;
            }
            case QUALITATIVE -> {
                if (rng.chance(rates.qualitative())) {
                    int n = s.alleleCount();
                    if (n == 2) {
                        return 1 - allele;
                    }
                    int other = rng.nextInt(n - 1);
                    return other >= allele ? other + 1 : other;
                }
                return allele;
            }
            default -> {
                return allele;
            }
        }
    }
}
