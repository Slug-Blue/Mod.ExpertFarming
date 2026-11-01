package io.github.slugblue.wildroots.genetics.quality;

/**
 * One main ingredient of a quality recipe (GDD §6.6).
 *
 * @param grade  grade 1–4 (an item without the field counts as 3 — mod layer)
 * @param tier   tier of the raw material: 0 wild, 1 landrace, 2 cultivar
 * @param count  how many of it are in the grid
 * @param bitter bitter input counts as grade 1
 */
public record GradedInput(int grade, int tier, int count, boolean bitter) {
}
