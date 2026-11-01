package io.github.slugblue.wildroots.genetics.testutil;

import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;

/** Uniformly random genomes over all allele values of a layout (tests only). */
public final class RandomGenomes {

    private RandomGenomes() {
    }

    public static Genome uniform(String species, GenomeLayout layout, SplitMix64 rng) {
        Genome.Builder b = Genome.builder(species, layout);
        for (Locus s : layout.sections()) {
            for (int l = 0; l < s.loci(); l++) {
                for (int w = 0; w < 2; w++) {
                    b.set(s, l, w, rng.nextInt(s.alleleCount()));
                }
            }
        }
        return b.build();
    }

    public static Genome carrot(SplitMix64 rng) {
        return uniform(Carrot.SPECIES.id(), Carrot.LAYOUT, rng);
    }
}
