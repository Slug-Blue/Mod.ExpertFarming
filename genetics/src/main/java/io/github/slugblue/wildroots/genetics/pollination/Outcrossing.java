package io.github.slugblue.wildroots.genetics.pollination;

import io.github.slugblue.wildroots.genetics.api.Bees;
import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;

/**
 * Effective outcrossing share {@code p_out} of a lot (GDD §4.1–4.4):
 * <ul>
 *   <li>brush on itself: {@code 1 − 0.90} (forced selfing);</li>
 *   <li>no compatible pollen at all (neighbours + donors = 0) and no brush: 0, the
 *   plant selfs (for a self-incompatible species only the leaky {@code seed_set}
 *   0.10 of {@link SeedSet} is left);</li>
 *   <li>self-incompatible species with compatible pollen: 1.0 ("1.0 среди совместимых",
 *   §4.2) — also with the brush: its own pollen cannot set seed anyway;</li>
 *   <li>brush with other pollen: 0.90;</li>
 *   <li>bee visit: {@code bees.outcross}, but only if a bee brought compatible pollen
 *   ({@code compatibleDonors > 0}); a visit without a donor changes nothing;</li>
 *   <li>otherwise {@code base_outcross · min(1, n / 3)}, {@code n} = compatible
 *   flowering neighbours plus compatible donors.</li>
 * </ul>
 */
public final class Outcrossing {

    private Outcrossing() {
    }

    public static double pOut(SpeciesDef s, int compatibleNeighbours, int compatibleDonors, boolean beeVisited,
                              Brush brush) {
        int n = Math.max(0, compatibleNeighbours) + Math.max(0, compatibleDonors);
        if (brush == Brush.SELF) {
            return n > 0 ? 1 - Defaults.BRUSH_OUTCROSS : 0;
        }
        if (n == 0 && (brush != Brush.CROSS || !s.pollination().selfCompatible())) {
            return 0;
        }
        if (!s.pollination().selfCompatible() && n > 0) {
            return 1.0;
        }
        if (brush == Brush.CROSS) {
            return Defaults.BRUSH_OUTCROSS;
        }
        if (beeVisited && compatibleDonors > 0 && s.pollination().bees().isPresent()) {
            Bees b = s.pollination().bees().get();
            return clamp01(b.outcross());
        }
        return clamp01(s.pollination().outcross() * Math.min(1.0, n / (double) Defaults.OUTCROSS_FULL_AT));
    }

    private static double clamp01(double x) {
        return x > 1 ? 1 : x >= 0 ? x : 0;
    }
}
