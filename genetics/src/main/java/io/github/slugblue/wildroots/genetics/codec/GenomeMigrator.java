package io.github.slugblue.wildroots.genetics.codec;

import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitKind;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Migration of stored genomes (ADR-0002, docs/05-architecture.md §5.3).
 *
 * <p><b>Format axis</b> ({@code v}): a record from the future is inert and
 * kept raw; older versions pass the chain {@code vN → vN+1} (none exist for
 * v1 yet).
 *
 * <p><b>Data axis</b> ({@code lh}): when the hash differs from the current
 * layout, the old layout comes from the dictionary {@code (species, lh)} and
 * alleles are re-encoded by {@code (trait, locus, allele)}. New loci and
 * traits get background values from {@code Hash64(genomeKey, traitId, locus,
 * allele)} and the species' wild frequencies; removed traits and loci are
 * dropped with a warning; a narrower allele count or a changed trait kind
 * replaces out-of-range values with background values and a warning — the
 * only allowed loss. Unknown layouts regenerate the genome as wild with a
 * warning. Never throws on foreign data.
 */
public final class GenomeMigrator {

    public enum Status {
        /** {@code lh} is the current layout: decoded as is. */
        UNCHANGED,
        /** Old layout found in the dictionary, alleles re-encoded. */
        MIGRATED,
        /** Old layout unknown: genome regenerated as wild (warning). */
        REGENERATED_UNKNOWN_LAYOUT,
        /** Unusable now (future version, damaged data): raw data kept, object inert. */
        INERT
    }

    public record Result(Status status, Optional<Genome> genome, List<String> warnings,
                         Optional<GenomeLayout> storedLayout) {

        public Result {
            warnings = List.copyOf(warnings);
            storedLayout = storedLayout == null ? Optional.empty() : storedLayout;
        }

        public Result(Status status, Optional<Genome> genome, List<String> warnings) {
            this(status, genome, warnings, genome.filter(g -> status == Status.UNCHANGED).map(Genome::layout));
        }

        /** As a {@link DecodeResult} for the facade. */
        public DecodeResult toDecodeResult() {
            if (genome.isEmpty()) {
                return new DecodeResult.Inert(String.join("; ", warnings));
            }
            return new DecodeResult.Valid(genome.get(), status != Status.UNCHANGED, warnings, storedLayout);
        }
    }

    /** One step of the format chain {@code v → v + 1}. */
    @FunctionalInterface
    public interface FormatStep {
        GenomeRecord apply(GenomeRecord r);
    }

    /** Format chain: index {@code i} migrates {@code v = i + 1} to {@code i + 2}. Empty while CURRENT = 1. */
    static final List<FormatStep> FORMAT_CHAIN = List.of();

    private GenomeMigrator() {
    }

    /** Default genome key of a stored record: {@code Hash64(lh, a…)} ("key from the genome hash", §5.3). */
    public static long genomeKey(GenomeRecord r) {
        long[] a = r.a();
        long[] parts = new long[(a == null ? 0 : a.length) + 1];
        parts[0] = r.lh();
        if (a != null) {
            System.arraycopy(a, 0, parts, 1, a.length);
        }
        return Hash64.of(parts);
    }

    public static Result migrate(GenomeRecord r, SpeciesDef species, GenomeLayout current, LayoutDictionary dict) {
        return migrate(r, species, current, dict, r == null ? 0 : genomeKey(r));
    }

    public static Result migrate(GenomeRecord r, SpeciesDef species, GenomeLayout current, LayoutDictionary dict,
                                 long genomeKey) {
        List<String> warn = new ArrayList<>();
        try {
            if (r == null) {
                return inert("no genome", warn);
            }
            if (r.v() > GenomeCodec.CURRENT_VERSION) {
                return inert("format version " + r.v() + " is from a newer mod version: kept raw", warn);
            }
            GenomeRecord rec = r;
            while (rec.v() >= 1 && rec.v() < GenomeCodec.CURRENT_VERSION) {
                rec = FORMAT_CHAIN.get(rec.v() - 1).apply(rec);
            }
            String bad = GenomeCodec.precheck(rec, species.id());
            if (bad != null) {
                return inert(bad, warn);
            }
            if (rec.lh() == current.hash()) {
                DecodeResult d = GenomeCodec.decode(rec, species.id(), current);
                if (d instanceof DecodeResult.Inert i) {
                    return inert(i.reason(), warn);
                }
                return new Result(Status.UNCHANGED, Optional.of(d.genome()), warn);
            }
            Optional<GenomeLayout> old = dict.lookup(species.id(), rec.lh());
            if (old.isEmpty()) {
                warn.add("unknown layout " + Long.toHexString(rec.lh()) + " of " + species.id()
                        + ": genome regenerated as wild");
                return new Result(Status.REGENERATED_UNKNOWN_LAYOUT, Optional.of(wild(species, current, genomeKey)),
                        warn);
            }
            long[] raw = rec.a();
            String err = GenomeCodec.checkBits(raw, old.get());
            if (err != null) {
                return inert("old layout: " + err, warn);
            }
            return new Result(Status.MIGRATED,
                    Optional.of(reencode(raw, old.get(), species, current, genomeKey, warn)), warn, old);
        } catch (RuntimeException e) {
            return inert("migration failed: " + e, warn);
        }
    }

    private static Result inert(String reason, List<String> warn) {
        warn.add(reason);
        return new Result(Status.INERT, Optional.empty(), warn);
    }

    static Genome reencode(long[] raw, GenomeLayout old, SpeciesDef species, GenomeLayout current, long genomeKey,
                           List<String> warn) {
        Genome.Builder out = Genome.builder(species.id(), current);
        for (Locus s : current.sections()) {
            Locus o = old.section(s.trait());
            if (o != null && o.kind() != s.kind()) {
                warn.add("trait " + s.trait() + " changed kind " + o.kind() + " -> " + s.kind() + ": background");
                o = null;
            } else if (o == null) {
                warn.add("new trait " + s.trait() + ": background");
            }
            for (int l = 0; l < s.loci(); l++) {
                for (int w = 0; w < 2; w++) {
                    int v;
                    if (o != null && l < o.loci()) {
                        v = GenomeLayout.get(raw, o, l, w);
                        if (v >= s.alleleCount()) {
                            warn.add("trait " + s.trait() + " locus " + l + " allele " + v + " >= "
                                    + s.alleleCount() + ": background");
                            v = background(species, s, genomeKey, l, w);
                        }
                    } else {
                        v = background(species, s, genomeKey, l, w);
                    }
                    out.set(s, l, w, v);
                }
            }
            if (o != null && o.loci() > s.loci()) {
                warn.add("trait " + s.trait() + " lost loci " + s.loci() + ".." + (o.loci() - 1));
            } else if (o != null && o.loci() < s.loci()) {
                warn.add("trait " + s.trait() + " new loci " + o.loci() + ".." + (s.loci() - 1) + ": background");
            }
        }
        for (Locus o : old.sections()) {
            if (current.section(o.trait()) == null) {
                warn.add("trait " + o.trait() + " removed: dropped");
            }
        }
        return out.build();
    }

    /** Whole genome from background values (unknown layout). */
    public static Genome wild(SpeciesDef species, GenomeLayout layout, long genomeKey) {
        Genome.Builder b = Genome.builder(species.id(), layout);
        for (Locus s : layout.sections()) {
            for (int l = 0; l < s.loci(); l++) {
                for (int w = 0; w < 2; w++) {
                    b.set(s, l, w, background(species, s, genomeKey, l, w));
                }
            }
        }
        return b.build();
    }

    /** Background allele from {@code Hash64(genomeKey, traitId, locus, allele)} and the species' wild frequencies. */
    public static int background(SpeciesDef species, Locus s, long genomeKey, int locus, int allele) {
        long h = Hash64.of(genomeKey, Hash64.str(s.trait().value()), locus, allele);
        double u = (h >>> 11) * 0x1.0p-53;
        if (s.kind() == TraitKind.QUANTITATIVE) {
            double[] f = species.wild().background().asArray();
            double total = f[0] + f[1] + f[2];
            double x = u * (total > 0 ? total : 1);
            return x < f[0] ? 0 : x < f[0] + f[1] ? 1 : total > 0 ? 2 : 0;
        }
        if (s.kind() == TraitKind.LOAD) {
            return u < species.load().freq() ? 1 : 0;
        }
        QualTrait q = species.qual(s.trait());
        if (s.alleleCount() > 2 || (q != null && q.selfIncompat())) {
            return (int) Long.remainderUnsigned(Hash64.mix(h), s.alleleCount());
        }
        double p = q == null ? 0 : q.wildFreq();
        return u < p ? 1 : 0;
    }
}
