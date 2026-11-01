package io.github.slugblue.wildroots.genetics.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable packed genome of one plant (ADR-0002, docs/05-architecture.md
 * §5.1): species id, its layout and the allele bits. Arrays are copied in
 * and out.
 */
public final class Genome {

    private final String species;
    private final GenomeLayout layout;
    private final long[] bits;

    /**
     * Genome from packed bits; throws {@link IllegalArgumentException} unless
     * {@link GenomeLayout#checkBits} accepts them. Stored data goes through the
     * codec instead, which returns {@code Inert} and never throws.
     */
    public Genome(String species, GenomeLayout layout, long[] bits) {
        this.species = Objects.requireNonNull(species, "species");
        this.layout = Objects.requireNonNull(layout, "layout");
        long[] copy = bits.clone();
        String bad = layout.checkBits(copy);
        if (bad != null) {
            throw new IllegalArgumentException("invalid genome bits: " + bad);
        }
        this.bits = copy;
    }

    /** Wraps an array the caller promises not to touch again (engine-internal builders). */
    static Genome adopt(String species, GenomeLayout layout, long[] bits) {
        return new Genome(species, layout, bits, true);
    }

    private Genome(String species, GenomeLayout layout, long[] bits, boolean adopt) {
        this.species = species;
        this.layout = layout;
        this.bits = bits;
    }

    public String species() {
        return species;
    }

    public GenomeLayout layout() {
        return layout;
    }

    /** Copy of the packed bits. */
    public long[] bits() {
        return bits.clone();
    }

    /** Allele {@code which} (0 mother, 1 father) at {@code locus} of section {@code s}. */
    public int allele(Locus s, int locus, int which) {
        return GenomeLayout.get(bits, s, locus, which);
    }

    /** Allele of a trait by id. */
    public int allele(TraitId trait, int locus, int which) {
        Locus s = layout.section(trait);
        if (s == null) {
            throw new IllegalArgumentException("no trait " + trait);
        }
        return allele(s, locus, which);
    }

    /** Sum of both alleles over all loci of a section. */
    public int alleleSum(Locus s) {
        int sum = 0;
        for (int l = 0; l < s.loci(); l++) {
            sum += allele(s, l, 0) + allele(s, l, 1);
        }
        return sum;
    }

    /** Mutable builder over a fresh zero array. */
    public static Builder builder(String species, GenomeLayout layout) {
        return new Builder(species, layout, new long[layout.longCount()]);
    }

    /** Mutable builder starting from this genome. */
    public Builder toBuilder() {
        return new Builder(species, layout, bits.clone());
    }

    /** Fills a packed array; {@link #build()} hands it over without copying. */
    public static final class Builder {
        private final String species;
        private final GenomeLayout layout;
        private long[] bits;

        private Builder(String species, GenomeLayout layout, long[] bits) {
            this.species = species;
            this.layout = layout;
            this.bits = bits;
        }

        public Builder set(Locus s, int locus, int which, int value) {
            GenomeLayout.set(bits, s, locus, which, value);
            return this;
        }

        public int get(Locus s, int locus, int which) {
            return GenomeLayout.get(bits, s, locus, which);
        }

        public GenomeLayout layout() {
            return layout;
        }

        public Genome build() {
            long[] b = bits;
            bits = b.clone();
            return adopt(species, layout, b);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Genome g && g.species.equals(species) && g.layout.equals(layout)
                && Arrays.equals(g.bits, bits);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bits);
    }

    @Override
    public String toString() {
        return "Genome[" + species + ", " + Long.toHexString(layout.hash()) + ", " + Arrays.toString(bits) + "]";
    }
}
