package io.github.slugblue.wildroots.genetics.model;

/**
 * Kind of a layout section; the declaration order is the canonical section
 * order {@code quantitative → load → qualitative} (docs/05-architecture.md §5.1).
 */
public enum TraitKind {
    /** Additive allele 0, 1 or 2; 2 bits per allele (GDD §3.1). */
    QUANTITATIVE('q'),
    /** Load allele D = 0 / d = 1; 1 bit per allele (GDD §3.7). */
    LOAD('l'),
    /** Mendelian allele 0 = wild, 1 = domestic, or an S-allele number; ceil(log2 n) bits (GDD §3.6). */
    QUALITATIVE('c');

    private final char code;

    TraitKind(char code) {
        this.code = code;
    }

    /** One-letter code in the canonical layout string. */
    public char code() {
        return code;
    }

    /** Kind for a canonical code, or {@code null}. */
    public static TraitKind ofCode(char c) {
        for (TraitKind k : values()) {
            if (k.code == c) {
                return k;
            }
        }
        return null;
    }
}
