package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.Map;

/**
 * Passport of a wild patch (worldgen §3.3): everything a wild plant needs
 * instead of a stored genome.
 *
 * @param species    species id
 * @param categoryId patch category ({@code meadow}), key of the elite loci (worldgen §2.1)
 * @param rich       rich patch (worldgen §5)
 * @param origin     packed origin position of the patch
 * @param starter    starter patch (worldgen §6.4)
 * @param worldSeed  world seed; not stored in the block entity, the mod layer adds it
 *                   (elite loci are {@code Hash64(worldSeed, species, category)}, GDD §3.2)
 * @param qualitativeFrequencies habitat-specific domestic-allele frequencies, overriding even starter frequencies;
 *                               resolved by the mod layer, not stored in a plant
 */
public record PatchPassport(String species, String categoryId, boolean rich, long origin, boolean starter,
                            long worldSeed, Map<TraitId, Double> qualitativeFrequencies) {
    /** Callers without habitat-specific frequencies retain the species defaults. */
    public PatchPassport(String species, String categoryId, boolean rich, long origin, boolean starter, long worldSeed) {
        this(species, categoryId, rich, origin, starter, worldSeed, Map.of());
    }

    public PatchPassport {
        qualitativeFrequencies = Map.copyOf(qualitativeFrequencies);
        if (qualitativeFrequencies.values().stream().anyMatch(p -> !Double.isFinite(p) || p < 0 || p > 1)) {
            throw new IllegalArgumentException("invalid habitat allele frequency");
        }
    }
}
