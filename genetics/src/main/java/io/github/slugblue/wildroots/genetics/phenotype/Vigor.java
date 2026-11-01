package io.github.slugblue.wildroots.genetics.phenotype;

import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;

/** Vitality {@code V = step^(number of dd)} over the load loci (GDD §3.7). Allele 1 is {@code d}. */
public final class Vigor {

    private Vigor() {
    }

    public static int homozygousDeleterious(Genome g, Locus load) {
        if (load == null) {
            return 0;
        }
        int n = 0;
        for (int l = 0; l < load.loci(); l++) {
            if (g.allele(load, l, 0) == 1 && g.allele(load, l, 1) == 1) {
                n++;
            }
        }
        return n;
    }

    /** {@code step^n} by repeated multiplication (bit-exact on every JVM). */
    public static double of(int dd, double step) {
        double v = 1;
        for (int i = 0; i < dd; i++) {
            v *= step;
        }
        return v;
    }

    public static double of(Genome g, Locus load, double step) {
        return of(homozygousDeleterious(g, load), step);
    }
}
