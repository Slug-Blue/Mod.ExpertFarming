package io.github.slugblue.wildroots.genetics.model;

import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Packed layout of a species genome (ADR-0002, docs/05-architecture.md §5.1).
 *
 * <p>Sections go in canonical order: quantitative by trait id, then load,
 * then qualitative by trait id; inside a section locus by locus, two alleles
 * per locus. Bits per allele: quantitative 2, load 1, qualitative
 * {@code ceil(log2 n)}. The layout hash {@code lh} is FNV-1a 64 of the
 * canonical string {@code q:flavor:4|…|l:8|c:bitterness:1:2|…}; the string
 * carries the allele count of each qualitative trait (S10).
 */
public final class GenomeLayout {

    /** Hard limit: 8 longs, 512 bits (SEC-09, SEC-28). */
    public static final int MAX_LONGS = 8;
    /** Hard limit of the canonical string (SEC-28). */
    public static final int MAX_CANONICAL_LENGTH = 4096;

    /** Section before offsets are assigned. */
    public record SectionSpec(TraitId trait, TraitKind kind, int loci, int alleleCount) {
    }

    private final List<Locus> sections;
    private final Map<TraitId, Locus> byTrait;
    private final List<Locus> quantitative;
    private final List<Locus> qualitative;
    private final Locus load;
    private final String canonical;
    private final long hash;
    private final int totalBits;

    private GenomeLayout(List<SectionSpec> specs) {
        List<SectionSpec> sorted = new ArrayList<>(specs);
        sorted.sort(Comparator.comparing(SectionSpec::kind).thenComparing(SectionSpec::trait));
        List<Locus> out = new ArrayList<>();
        Map<TraitId, Locus> map = new TreeMap<>();
        List<Locus> quant = new ArrayList<>();
        List<Locus> qual = new ArrayList<>();
        Locus loadSection = null;
        StringBuilder sb = new StringBuilder();
        int offset = 0;
        for (SectionSpec spec : sorted) {
            int alleles = switch (spec.kind()) {
                case QUANTITATIVE -> 3;
                case LOAD -> 2;
                case QUALITATIVE -> spec.alleleCount();
            };
            if (spec.loci() < 1 || spec.loci() > 64 || alleles < 2 || alleles > 16) {
                throw new IllegalArgumentException("bad section " + spec);
            }
            Locus s = new Locus(spec.trait(), spec.kind(), spec.loci(), alleles, offset);
            if (map.put(s.trait(), s) != null) {
                throw new IllegalArgumentException("duplicate trait " + s.trait());
            }
            out.add(s);
            switch (s.kind()) {
                case QUANTITATIVE -> quant.add(s);
                case QUALITATIVE -> qual.add(s);
                case LOAD -> loadSection = s;
            }
            offset += s.bits();
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(s.canonical());
        }
        this.totalBits = offset;
        if (longCount() > MAX_LONGS) {
            throw new IllegalArgumentException("layout has " + totalBits + " bits > " + (MAX_LONGS * 64));
        }
        this.sections = Collections.unmodifiableList(out);
        this.byTrait = Collections.unmodifiableMap(map);
        this.quantitative = Collections.unmodifiableList(quant);
        this.qualitative = Collections.unmodifiableList(qual);
        this.load = loadSection;
        this.canonical = sb.toString();
        this.hash = Hash64.fnv1a64(canonical);
    }

    /** Layout from section specs in any order. Throws on invalid sections or &gt; 512 bits. */
    public static GenomeLayout of(List<SectionSpec> specs) {
        return new GenomeLayout(specs);
    }

    /** Layout of a validated species. */
    public static GenomeLayout of(SpeciesDef s) {
        return new GenomeLayout(specsOf(s));
    }

    /** Section specs of a species, unsorted. */
    public static List<SectionSpec> specsOf(SpeciesDef s) {
        List<SectionSpec> specs = new ArrayList<>();
        for (QuantTrait q : s.quantitative()) {
            specs.add(new SectionSpec(q.id(), TraitKind.QUANTITATIVE, q.loci(), 3));
        }
        if (s.load().loci() > 0) {
            specs.add(new SectionSpec(TraitId.LOAD, TraitKind.LOAD, s.load().loci(), 2));
        }
        for (QualTrait q : s.qualitative()) {
            specs.add(new SectionSpec(q.id(), TraitKind.QUALITATIVE, 1, q.alleles()));
        }
        return specs;
    }

    /** Total bits a species would need, without building the layout (validation). */
    public static long bitsOf(SpeciesDef s) {
        long bits = 0;
        for (QuantTrait q : s.quantitative()) {
            bits += (long) q.loci() * 4;
        }
        bits += (long) Math.max(0, s.load().loci()) * 2;
        for (QualTrait q : s.qualitative()) {
            int n = Math.max(2, q.alleles());
            bits += 2L * (32 - Integer.numberOfLeadingZeros(n - 1));
        }
        return bits;
    }

    /**
     * Parses a canonical string from the layout dictionary. Never throws:
     * malformed or oversized input gives an empty result.
     */
    public static Optional<GenomeLayout> parse(String canonical) {
        if (canonical == null || canonical.isEmpty() || canonical.length() > MAX_CANONICAL_LENGTH) {
            return Optional.empty();
        }
        try {
            List<SectionSpec> specs = new ArrayList<>();
            for (String part : canonical.split("\\|", -1)) {
                String[] f = part.split(":", -1);
                TraitKind kind = f[0].length() == 1 ? TraitKind.ofCode(f[0].charAt(0)) : null;
                if (kind == null) {
                    return Optional.empty();
                }
                switch (kind) {
                    case QUANTITATIVE -> {
                        if (f.length != 3) {
                            return Optional.empty();
                        }
                        specs.add(new SectionSpec(new TraitId(f[1]), kind, Integer.parseInt(f[2]), 3));
                    }
                    case LOAD -> {
                        if (f.length != 2) {
                            return Optional.empty();
                        }
                        specs.add(new SectionSpec(TraitId.LOAD, kind, Integer.parseInt(f[1]), 2));
                    }
                    case QUALITATIVE -> {
                        if (f.length != 4) {
                            return Optional.empty();
                        }
                        specs.add(new SectionSpec(new TraitId(f[1]), kind, Integer.parseInt(f[2]),
                                Integer.parseInt(f[3])));
                    }
                }
            }
            GenomeLayout layout = new GenomeLayout(specs);
            return layout.canonical.equals(canonical) ? Optional.of(layout) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public List<Locus> sections() {
        return sections;
    }

    public List<Locus> quantitative() {
        return quantitative;
    }

    public List<Locus> qualitative() {
        return qualitative;
    }

    /** Load section or {@code null} when the species has no load loci. */
    public Locus load() {
        return load;
    }

    /** Section of a trait or {@code null}. */
    public Locus section(TraitId trait) {
        return byTrait.get(trait);
    }

    public String canonical() {
        return canonical;
    }

    /** 64-bit layout hash {@code lh} (FNV-1a 64 of {@link #canonical()}). */
    public long hash() {
        return hash;
    }

    public int totalBits() {
        return totalBits;
    }

    /** Length of the packed {@code long[]}. */
    public int longCount() {
        return (totalBits + 63) >>> 6;
    }

    /**
     * Validates packed bits against this layout: exact length, every allele
     * in range, unused tail bits zero. Returns the reason, or {@code null}.
     */
    public String checkBits(long[] a) {
        if (a == null) {
            return "no allele array";
        }
        if (a.length != longCount()) {
            return "allele array of " + a.length + " longs != layout " + longCount();
        }
        for (Locus s : sections) {
            for (int l = 0; l < s.loci(); l++) {
                for (int w = 0; w < 2; w++) {
                    int v = get(a, s, l, w);
                    if (v >= s.alleleCount()) {
                        return "allele " + v + " out of range in " + s.trait() + " locus " + l;
                    }
                }
            }
        }
        int used = totalBits & 63;
        if (used != 0 && (a[a.length - 1] >>> used) != 0) {
            return "non-zero bits after the last allele";
        }
        return null;
    }

    /** Reads allele {@code which} (0 mother, 1 father) at {@code locus} of section {@code s}. */
    public static int get(long[] bits, Locus s, int locus, int which) {
        int off = s.bitOf(locus, which);
        int n = s.bitsPerAllele();
        int word = off >>> 6;
        int shift = off & 63;
        long v = bits[word] >>> shift;
        if (shift + n > 64) {
            v |= bits[word + 1] << (64 - shift);
        }
        return (int) (v & ((1L << n) - 1));
    }

    /** Writes an allele; the value must fit the section ({@code 0 ≤ value < alleleCount}). */
    public static void set(long[] bits, Locus s, int locus, int which, int value) {
        if (value < 0 || value >= s.alleleCount()) {
            throw new IllegalArgumentException(s.trait() + " allele " + value);
        }
        int off = s.bitOf(locus, which);
        int n = s.bitsPerAllele();
        for (int i = 0; i < n; i++) {
            int bit = off + i;
            long m = 1L << (bit & 63);
            if ((value & (1 << i)) != 0) {
                bits[bit >>> 6] |= m;
            } else {
                bits[bit >>> 6] &= ~m;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GenomeLayout g && g.canonical.equals(canonical);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hash);
    }

    @Override
    public String toString() {
        return canonical + " #" + Long.toHexString(hash) + " (" + totalBits + " bits)";
    }
}
