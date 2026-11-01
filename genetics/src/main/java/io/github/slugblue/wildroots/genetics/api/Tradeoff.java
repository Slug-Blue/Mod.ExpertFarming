package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.TraitId;

/**
 * One pleiotropy pair of layer 1 (GDD §3.10): {@code trait} loses
 * {@code c·max(0, G(against) − threshold)}.
 */
public record Tradeoff(TraitId trait, TraitId against, double c, double threshold) {
}
