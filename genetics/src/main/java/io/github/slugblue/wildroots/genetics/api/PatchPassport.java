package io.github.slugblue.wildroots.genetics.api;

/**
 * Passport of a wild patch (worldgen §3.3): everything a wild plant stores
 * instead of a genome.
 *
 * @param species    species id
 * @param categoryId patch category ({@code meadow}), key of the elite loci (worldgen §2.1)
 * @param rich       rich patch (worldgen §5)
 * @param origin     packed origin position of the patch
 * @param starter    starter patch (worldgen §6.4)
 */
public record PatchPassport(String species, String categoryId, boolean rich, long origin, boolean starter) {
}
