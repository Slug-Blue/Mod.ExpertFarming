package io.github.slugblue.wildroots.genetics.phenotype;

import io.github.slugblue.wildroots.genetics.api.Defaults;
import java.util.Arrays;

/**
 * Planting noise ε of one plant (GDD §3.1): one value per quantitative trait
 * in layout order, quantised to steps of 1/256 within ±0.5 and stored as one
 * byte per trait ({@code "e": [B; …]}, docs/05-architecture.md §5.2).
 */
public final class NoiseVector {

    private final byte[] q;

    public NoiseVector(byte[] quantised) {
        this.q = quantised.clone();
    }

    /** No noise for {@code n} traits. */
    public static NoiseVector zero(int n) {
        return new NoiseVector(new byte[n]);
    }

    /** Quantises ε: clip to ±0.5, round to 1/256, fit a signed byte. */
    public static byte quantise(double eps) {
        double c = Math.max(-Defaults.NOISE_CLIP, Math.min(Defaults.NOISE_CLIP, eps));
        long r = Math.round(c * 256);
        return (byte) Math.max(-128, Math.min(127, r));
    }

    public int size() {
        return q.length;
    }

    /** ε of trait {@code i}; 0 if the vector is shorter (layout grew). */
    public double eps(int i) {
        return i < q.length ? q[i] / 256.0 : 0;
    }

    /** Stored bytes. */
    public byte[] bytes() {
        return q.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NoiseVector n && Arrays.equals(n.q, q);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(q);
    }
}
