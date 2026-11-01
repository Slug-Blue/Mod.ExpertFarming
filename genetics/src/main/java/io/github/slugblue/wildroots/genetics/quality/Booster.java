package io.github.slugblue.wildroots.genetics.quality;

/**
 * Optional special component of a quality recipe (GDD §6.6), e.g. honey in
 * bread. {@code qualifies} is decided by the mod layer from the recipe JSON
 * (for example "sugar of grade ≥ 3").
 */
public record Booster(boolean qualifies) {
}
