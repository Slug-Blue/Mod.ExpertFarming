package io.github.slugblue.wildroots.genetics.population;

import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;

/** Drawing one diploid locus with inbreeding {@code F} (identity by descent). */
final class Alleles {

    private Alleles() {
    }

    /** With probability {@code f} both alleles are one draw, otherwise two independent draws. */
    static void drawLocus(Genome.Builder b, Locus s, int locus, double[] freq, double f, SplitMix64 rng) {
        int a = rng.pick(freq);
        int c = rng.chance(f) ? a : rng.pick(freq);
        b.set(s, locus, 0, a).set(s, locus, 1, c);
    }

    static double[] biallelic(double domesticFreq) {
        double p = domesticFreq > 1 ? 1 : domesticFreq >= 0 ? domesticFreq : 0;
        return new double[] {1 - p, p};
    }
}
