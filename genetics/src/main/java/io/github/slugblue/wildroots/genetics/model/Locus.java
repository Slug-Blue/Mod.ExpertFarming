package io.github.slugblue.wildroots.genetics.model;

/**
 * One section of a {@link GenomeLayout}: all loci of one trait
 * (docs/05-architecture.md §5.1). Inside a section the layout is locus-major,
 * two alleles per locus (mother's, then father's).
 *
 * @param trait       trait id ({@link TraitId#LOAD} for the load section)
 * @param kind        section kind
 * @param loci        number of loci
 * @param alleleCount number of allele values: 3 quantitative, 2 load, 2..16 qualitative
 * @param bitOffset   first bit of the section in the packed array
 */
public record Locus(TraitId trait, TraitKind kind, int loci, int alleleCount, int bitOffset) {

    /** Bits per allele: 2 / 1 / ceil(log2 n). */
    public int bitsPerAllele() {
        return switch (kind) {
            case QUANTITATIVE -> 2;
            case LOAD -> 1;
            case QUALITATIVE -> Math.max(1, 32 - Integer.numberOfLeadingZeros(alleleCount - 1));
        };
    }

    /** Total bits of the section. */
    public int bits() {
        return loci * 2 * bitsPerAllele();
    }

    /** Bit position of allele {@code which} (0 mother, 1 father) at {@code locus}. */
    public int bitOf(int locus, int which) {
        return bitOffset + (locus * 2 + which) * bitsPerAllele();
    }

    /** Canonical fragment: {@code q:id:L}, {@code l:L}, {@code c:id:L:n}. */
    public String canonical() {
        return switch (kind) {
            case QUANTITATIVE -> "q:" + trait.value() + ":" + loci;
            case LOAD -> "l:" + loci;
            case QUALITATIVE -> "c:" + trait.value() + ":" + loci + ":" + alleleCount;
        };
    }
}
