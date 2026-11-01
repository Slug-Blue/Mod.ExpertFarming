package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.Genome;

/**
 * One father recorded in a seed lot (GDD §4.1, §5.3).
 *
 * @param genome father genome
 * @param weight relative weight among the lot fathers
 * @param donor  {@code true} for pollen brought by bees or the brush
 */
public record FatherPick(Genome genome, double weight, boolean donor) {
}
