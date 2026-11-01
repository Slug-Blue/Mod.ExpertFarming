package io.github.slugblue.wildroots.genetics.phenotype;

import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;

/** {@code G = Σ alleles / (4·L)} over the loci of a quantitative trait (GDD §3.1). */
public final class GenotypicValue {

    private GenotypicValue() {
    }

    public static double of(Genome g, Locus s) {
        return g.alleleSum(s) / (4.0 * s.loci());
    }
}
