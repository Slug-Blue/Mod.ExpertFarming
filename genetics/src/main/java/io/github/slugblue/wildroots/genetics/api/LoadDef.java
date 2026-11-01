package io.github.slugblue.wildroots.genetics.api;

/**
 * Inbreeding load (GDD §3.7): {@code loci} hidden loci, wild frequency
 * {@code freq} of the deleterious allele {@code d}, vitality
 * {@code V = step^(number of dd)}.
 */
public record LoadDef(int loci, double freq, double step) {
}
