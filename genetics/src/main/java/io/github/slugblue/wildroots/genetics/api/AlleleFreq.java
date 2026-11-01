package io.github.slugblue.wildroots.genetics.api;

/** Frequencies of quantitative alleles 0 / 1 / 2 (GDD §3.2). */
public record AlleleFreq(double p0, double p1, double p2) {

    public double[] asArray() {
        return new double[] {p0, p1, p2};
    }

    /** Mean allele value. */
    public double mean() {
        return p1 + 2 * p2;
    }
}
