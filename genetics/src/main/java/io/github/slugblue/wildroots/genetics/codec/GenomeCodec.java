package io.github.slugblue.wildroots.genetics.codec;

import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;

/**
 * Encoder and type-safe decoder of {@link GenomeRecord} (ADR-0002 rev. 1–2,
 * docs/05-architecture.md §5.2, SEC-09, SEC-10). Decoding never throws: any
 * damage gives {@link DecodeResult.Inert}.
 */
public final class GenomeCodec {

    /** Current format version of the genome root. */
    public static final int CURRENT_VERSION = 1;
    /** Longest accepted allele array (512 bits). */
    public static final int MAX_LONGS = GenomeLayout.MAX_LONGS;

    private GenomeCodec() {
    }

    public static GenomeRecord encode(Genome g) {
        return new GenomeRecord(CURRENT_VERSION, g.species(), g.layout().hash(), g.bits());
    }

    /** Father in the compact lot form {@code {lh, a}}. */
    public static FatherRecord encodeFather(Genome g) {
        return new FatherRecord(g.layout().hash(), g.bits());
    }

    /**
     * Checks that do not need a layout: version, species, array present and
     * not longer than 8 longs. Returns the reason, or {@code null} if fine.
     */
    public static String precheck(GenomeRecord r, String expectedSpecies) {
        if (r == null) {
            return "no genome";
        }
        if (r.v() < 1 || r.v() > CURRENT_VERSION) {
            return "format version " + r.v() + " outside 1.." + CURRENT_VERSION;
        }
        if (r.species() == null || !r.species().equals(expectedSpecies)) {
            return "species " + r.species() + " != " + expectedSpecies;
        }
        if (r.length() < 0) {
            return "no allele array";
        }
        if (r.length() > MAX_LONGS) {
            return "allele array of " + r.length() + " longs > " + MAX_LONGS;
        }
        return null;
    }

    /**
     * Decodes a record written with exactly {@code layout} ({@code lh} equal
     * to {@code layout.hash()}). A different {@code lh} is inert here; the
     * facade first passes such records through {@code GenomeMigrator}.
     */
    public static DecodeResult decode(GenomeRecord r, String species, GenomeLayout layout) {
        String bad = precheck(r, species);
        if (bad != null) {
            return new DecodeResult.Inert(bad);
        }
        if (r.lh() != layout.hash()) {
            return new DecodeResult.Inert("layout " + Long.toHexString(r.lh()) + " is not the current "
                    + Long.toHexString(layout.hash()));
        }
        long[] a = r.a();
        String err = checkBits(a, layout);
        if (err != null) {
            return new DecodeResult.Inert(err);
        }
        return new DecodeResult.Valid(new Genome(species, layout, a));
    }

    /** Father of a lot, decoded with the lot's species and version (S10). */
    public static DecodeResult decodeFather(FatherRecord f, int lotVersion, String lotSpecies, GenomeLayout layout) {
        if (f == null) {
            return new DecodeResult.Inert("no father genome");
        }
        return decode(f.withLot(lotVersion, lotSpecies), lotSpecies, layout);
    }

    /**
     * Validates packed bits against a layout: exact length, every allele in
     * range, unused tail bits zero. Returns the reason or {@code null}.
     */
    public static String checkBits(long[] a, GenomeLayout layout) {
        return layout.checkBits(a);
    }
}
