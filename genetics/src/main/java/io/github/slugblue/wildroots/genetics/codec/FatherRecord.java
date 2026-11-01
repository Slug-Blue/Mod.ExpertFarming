package io.github.slugblue.wildroots.genetics.codec;

import java.util.Arrays;

/**
 * Father genome inside a seed lot, stored compactly as {@code {lh, a}}: the
 * species and the format version come from the lot (ADR-0002 rev. 2,
 * ADR-0007, S10).
 */
public final class FatherRecord {

    private final long lh;
    private final long[] a;

    public FatherRecord(long lh, long[] a) {
        this.lh = lh;
        this.a = a == null ? null : a.clone();
    }

    public long lh() {
        return lh;
    }

    public long[] a() {
        return a == null ? null : a.clone();
    }

    /** Full record with the lot's species and version. */
    public GenomeRecord withLot(int v, String species) {
        return new GenomeRecord(v, species, lh, a);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FatherRecord r && r.lh == lh && Arrays.equals(r.a, a);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(lh) * 31 + Arrays.hashCode(a);
    }
}
