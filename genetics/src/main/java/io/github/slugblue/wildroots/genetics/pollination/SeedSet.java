package io.github.slugblue.wildroots.genetics.pollination;

import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;

/**
 * Seeds of one mother (GDD §4.1):
 * {@code seeds = (3 + 9·P_yield) · seed_set · (shatters ? 0.35 : 1)}, where
 * {@code seed_set = 1} with a pollinator (bee visit or brush) and
 * {@code seed_set_no_pollinator} of the species without.
 */
public final class SeedSet {

    private SeedSet() {
    }

    public static double seedSet(SpeciesDef s, boolean pollinator) {
        return pollinator ? 1.0 : clamp01(s.pollination().seedSetNoPollinator());
    }

    /** Expected seed count (fractional). */
    public static double expected(SpeciesDef s, double pYield, boolean pollinator, boolean shatters) {
        double p = clamp01(pYield);
        return (Defaults.SEEDS_BASE + Defaults.SEEDS_PER_YIELD * p) * seedSet(s, pollinator)
                * (shatters ? Defaults.SHATTER_KEEP : 1.0);
    }

    /** Integer seed count by probabilistic rounding of {@link #expected}. */
    public static int realise(double expected, SplitMix64 rng) {
        double e = Math.max(0, expected);
        int base = (int) Math.floor(e);
        return base + (rng.chance(e - base) ? 1 : 0);
    }

    private static double clamp01(double x) {
        return x > 1 ? 1 : x >= 0 ? x : 0;
    }
}
