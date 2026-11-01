package io.github.slugblue.wildroots.genetics.phenotype;

/**
 * Layer 2 of GDD §3.10: {@code S = Σ wᵢ·G_pair(i)}; if {@code S > limit}
 * every budget trait is scaled by {@code limit/S}. Traits with weight 0 are
 * outside the budget and are not scaled. {@code S = 0} gives scale 1 (SEC-30).
 */
public final class ResourceBudget {

    private ResourceBudget() {
    }

    /** {@code S} for the given values and weights. */
    public static double sum(double[] gPair, double[] weights) {
        double s = 0;
        for (int i = 0; i < gPair.length; i++) {
            s += weights[i] * gPair[i];
        }
        return s;
    }

    /** Scale factor {@code min(1, limit/S)}. */
    public static double scale(double s, double limit) {
        return s > limit && s > 0 ? limit / s : 1.0;
    }

    /** {@code G_eff}: budget traits scaled, others unchanged. */
    public static double[] apply(double[] gPair, double[] weights, double limit) {
        double k = scale(sum(gPair, weights), limit);
        double[] out = gPair.clone();
        if (k < 1) {
            for (int i = 0; i < out.length; i++) {
                if (weights[i] > 0) {
                    out[i] *= k;
                }
            }
        }
        return out;
    }
}
