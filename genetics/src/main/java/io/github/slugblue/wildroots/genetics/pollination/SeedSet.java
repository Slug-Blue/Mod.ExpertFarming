package io.github.slugblue.wildroots.genetics.pollination;

import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;

/**
 * Seeds of one mother (GDD §4.1):
 * {@code seeds = (3 + 9·P_yield) · seed_set · (shatters ? 0.35 : 1)}.
 * {@code seed_set}: 1 with a pollinator (bee visit or brush), else
 * {@code seed_set_no_pollinator}; a self-incompatible species without any
 * compatible pollen sets 0.10 (GDD §4.2, §12.5: those seeds are selfed),
 * and 0.5 under brush selfing (bud pollination, GDD §4.4).
 */
public final class SeedSet {

    private SeedSet() {
    }

    public static double seedSet(SpeciesDef s, boolean pollinator, boolean compatiblePollen, Brush brush) {
        if (!s.pollination().selfCompatible()) {
            if (brush == Brush.SELF) {
                return Defaults.BUD_POLLINATION_SEED_SET;
            }
            if (!compatiblePollen) {
                return Defaults.SI_SEED_SET_NO_COMPATIBLE;
            }
        }
        return pollinator || brush != Brush.NONE ? 1.0 : clamp01(s.pollination().seedSetNoPollinator());
    }

    /** Expected seed count (fractional). */
    public static double expected(SpeciesDef s, double pYield, boolean pollinator, boolean compatiblePollen,
                                  Brush brush, boolean shatters) {
        double p = clamp01(pYield);
        return (Defaults.SEEDS_BASE + Defaults.SEEDS_PER_YIELD * p) * seedSet(s, pollinator, compatiblePollen, brush)
                * (shatters ? Defaults.SHATTER_KEEP : 1.0);
    }

    /** Integer seed count by probabilistic rounding of {@link #expected}. */
    public static int realise(double expected, SplitMix64 rng) {
        double e = Double.isFinite(expected) ? Math.max(0, expected) : 0;
        int base = (int) Math.floor(e);
        return base + (rng.chance(e - base) ? 1 : 0);
    }

    private static double clamp01(double x) {
        return x > 1 ? 1 : x >= 0 ? x : 0;
    }
}
