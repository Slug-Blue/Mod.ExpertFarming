package io.github.slugblue.wildroots.genetics.api;

/**
 * Required qualitative phenotype in a cultivar profile (GDD §6.3): a
 * recessive requirement is checked as {@code aa}, a dominant one as
 * {@code A_} (GDD §3.6).
 */
public enum QualReq {
    WILD,
    DOMESTIC
}
