package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.TraitId;

/**
 * Quantitative trait of a species (GDD §3.3, §3.9 {@code quantitative}).
 *
 * @param id            trait id
 * @param loci          number of loci (1–16)
 * @param sigmaEnv      σ of the planting noise ε
 * @param vigorAffected whether vitality {@code V} and environment {@code E} apply
 */
public record QuantTrait(TraitId id, int loci, double sigmaEnv, boolean vigorAffected) {
}
