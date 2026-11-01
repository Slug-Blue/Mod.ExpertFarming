package io.github.slugblue.wildroots.genetics.phenotype;

/**
 * Layer 1 of GDD §3.10: {@code G_pair(A) = max(0, G(A) − Σ c·max(0, G(B) − θ))}.
 * The penalty uses the original {@code G(B)}, so cycles A ← B ← A are fine
 * and the cost is one pass over the pairs (SEC-29).
 */
public final class Pleiotropy {

    private Pleiotropy() {
    }

    /**
     * @param g       G per trait
     * @param target  trait index A of each pair
     * @param against trait index B of each pair
     * @param c       cost of each pair
     * @param theta   threshold of each pair
     */
    public static double[] apply(double[] g, int[] target, int[] against, double[] c, double[] theta) {
        double[] out = g.clone();
        for (int k = 0; k < target.length; k++) {
            out[target[k]] -= c[k] * Math.max(0, g[against[k]] - theta[k]);
        }
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.max(0, out[i]);
        }
        return out;
    }
}
