package io.github.slugblue.wildroots.genetics.api;

/** Which allele of a biallelic qualitative trait is dominant (GDD §3.6). */
public enum Dominance {
    /** Wild allele (0) dominant: the domestic phenotype needs two domestic alleles. */
    WILD,
    /** Domestic allele (1) dominant: one domestic allele shows the domestic phenotype. */
    DOMESTIC
}
