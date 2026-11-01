package io.github.slugblue.wildroots.genetics.api;

/** Bee pollination of a species (GDD §4.2, §4.3): outcrossing on a bee visit and carry range. */
public record Bees(double outcross, int carryRange) {
}
