package io.github.slugblue.wildroots.genetics.api;

/** Per-allele, per-meiosis mutation probabilities (GDD §3.8). */
public record MutationRates(double quantitative, double qualitative) {
}
