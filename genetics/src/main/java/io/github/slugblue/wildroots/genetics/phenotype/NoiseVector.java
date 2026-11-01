package io.github.slugblue.wildroots.genetics.phenotype;

import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.random.Gaussian;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import java.util.Arrays;
import java.util.List;

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

    /**
     * ε after a layout change (WR-111): values follow the trait id from the
     * old layout's quantitative order to the new one; a trait new to the
     * species gets a fresh ε from {@code Hash64(rngKey, traitId)} with its
     * σ_env (deterministic); removed traits are dropped.
     */
    public NoiseVector migrate(GenomeLayout oldLayout, CompiledSpecies current, long rngKey) {
        List<Locus> oldQuant = oldLayout.quantitative();
        List<TraitId> ids = current.quantIds();
        byte[] out = new byte[ids.size()];
        for (int i = 0; i < out.length; i++) {
            int j = -1;
            for (int k = 0; k < oldQuant.size(); k++) {
                if (oldQuant.get(k).trait().equals(ids.get(i))) {
                    j = k;
                    break;
                }
            }
            if (j >= 0 && j < q.length) {
                out[i] = q[j];
            } else {
                Gaussian g = new Gaussian(new SplitMix64(Hash64.of(rngKey, Hash64.str(ids.get(i).value()))));
                out[i] = quantise(g.next(0, current.sigma(i)));
            }
        }
        return new NoiseVector(out);
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
