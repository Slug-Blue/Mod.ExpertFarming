package io.github.slugblue.wildroots.genetics.api;

/**
 * Environment of one cell (docs/05-architecture.md §4.1, GDD §3.5), computed
 * by {@code ClimateService} in the mod layer.
 *
 * @param temperature  biome temperature with the height adjustment
 * @param moisturized  farmland is moist
 * @param light        light level 0–15
 * @param compostBonus compost bonus, up to +0.05
 */
public record Environment(double temperature, boolean moisturized, int light, double compostBonus) {

    /** Ideal cell inside any window: moist, bright, no compost. */
    public static Environment neutral(double temperature) {
        return new Environment(temperature, true, 15, 0);
    }
}
