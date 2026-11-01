package io.github.slugblue.wildroots.genetics.pollination;

import io.github.slugblue.wildroots.genetics.api.Bees;
import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;

/**
 * Effective outcrossing share {@code p_out} of a lot (GDD §4.1, §4.3, §4.4):
 * brush 0.90; a bee visit with matching pollen {@code bees.outcross}; else
 * {@code base_outcross · min(1, n / 3)} with {@code n} compatible flowering
 * neighbours. A lone plant (no neighbours, no donors) selfs 100 %.
 */
public final class Outcrossing {

    private Outcrossing() {
    }

    public static double pOut(SpeciesDef s, int compatibleNeighbours, boolean beeVisited, boolean brushed,
                              boolean hasDonors) {
        if (brushed) {
            return Defaults.BRUSH_OUTCROSS;
        }
        if (beeVisited && s.pollination().bees().isPresent()) {
            Bees b = s.pollination().bees().get();
            return clamp01(b.outcross());
        }
        if (compatibleNeighbours <= 0 && !hasDonors) {
            return 0;
        }
        double n = Math.max(0, compatibleNeighbours);
        return clamp01(s.pollination().outcross() * Math.min(1.0, n / Defaults.OUTCROSS_FULL_AT));
    }

    private static double clamp01(double x) {
        return x > 1 ? 1 : x >= 0 ? x : 0;
    }
}
