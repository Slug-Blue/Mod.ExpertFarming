package io.github.slugblue.wildroots.genetics.pollination;

import io.github.slugblue.wildroots.genetics.model.Genome;

/**
 * Pollen brought to the plant by a bee or the brush (GDD §4.1, §4.3, §4.4).
 *
 * @param genome pollen parent
 * @param weight weight in the lot (bee 2.0, GDD §4.3)
 * @param brush  brought by the brush
 */
public record Donor(Genome genome, double weight, boolean brush) {

    /** Bee donor weight (GDD §4.3) {@code [PLACEHOLDER]}. */
    public static final double BEE_WEIGHT = 2.0;

    public static Donor bee(Genome g) {
        return new Donor(g, BEE_WEIGHT, false);
    }
}
