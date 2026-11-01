package io.github.slugblue.wildroots.genetics.codec;

import java.util.Arrays;
import java.util.Objects;

/**
 * Neutral stored form of a genome (ADR-0002, docs/05-architecture.md §5.2):
 * {@code {v, sp, lh, a}}. The mod layer maps it to NBT one to one: {@code v}
 * {@code IntTag}, {@code sp} {@code StringTag}, {@code lh} {@code LongTag}
 * (64-bit layout hash), {@code a} {@code LongArrayTag}. Arrays are copied in
 * and out; {@code a} may be {@code null} when the tag was missing.
 */
public final class GenomeRecord {

    private final int v;
    private final String species;
    private final long lh;
    private final long[] a;

    public GenomeRecord(int v, String species, long lh, long[] a) {
        this.v = v;
        this.species = species;
        this.lh = lh;
        this.a = a == null ? null : a.clone();
    }

    /** Format version of the code. */
    public int v() {
        return v;
    }

    /** Species id ({@code sp}); may be {@code null} in damaged data. */
    public String species() {
        return species;
    }

    /** 64-bit layout hash ({@code lh}, {@code LongTag}). */
    public long lh() {
        return lh;
    }

    /** Copy of the packed alleles, or {@code null}. */
    public long[] a() {
        return a == null ? null : a.clone();
    }

    int length() {
        return a == null ? -1 : a.length;
    }

    long word(int i) {
        return a[i];
    }

    /** Compact father form {@code {lh, a}} (ADR-0007 rev. 2, S10). */
    public FatherRecord toFather() {
        return new FatherRecord(lh, a);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GenomeRecord r && r.v == v && Objects.equals(r.species, species) && r.lh == lh
                && Arrays.equals(r.a, a);
    }

    @Override
    public int hashCode() {
        return Objects.hash(v, species, lh, Arrays.hashCode(a));
    }

    @Override
    public String toString() {
        return "GenomeRecord{v=" + v + ", sp=" + species + ", lh=" + Long.toHexString(lh) + ", a="
                + Arrays.toString(a) + "}";
    }
}
