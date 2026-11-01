package io.github.slugblue.wildroots.genetics.population;

import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.Range;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Immutable;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Genome template of a village landrace (GDD §9.5): "in the middle".
 *
 * <ul>
 *   <li>Every quantitative trait of the landrace profile gets exactly
 *   {@code G = threshold + 0.05}, rounded up to the allele grid; the alleles
 *   are spread over random loci, so crosses still segregate.</li>
 *   <li>Other quantitative traits: background frequencies with
 *   {@code wild_F = 0.3}; load {@code q = 0.15}.</li>
 *   <li>Qualitative traits: domestic-allele frequency from the template
 *   (required phenotypes fixed, "key to the final" such as {@code y2} 0.30),
 *   otherwise the species' {@code wild_freq}.</li>
 * </ul>
 * The key is {@code Hash64(worldSeed, villagePos, landraceId, harvestCount)} (§4.3).
 *
 * @param landrace landrace profile id
 * @param targets  exact {@code G} per quantitative trait
 * @param qualFreq domestic-allele frequency per qualitative trait
 * @param wildF    inbreeding of the drawn traits
 * @param loadFreq frequency of {@code d}
 */
public record VillageTemplate(CultivarId landrace, SortedMap<TraitId, Double> targets,
                              SortedMap<TraitId, Double> qualFreq, double wildF, double loadFreq) {

    public VillageTemplate(CultivarId landrace, Map<TraitId, Double> targets, Map<TraitId, Double> qualFreq,
                           double wildF, double loadFreq) {
        this(landrace, Immutable.sorted(targets), Immutable.sorted(qualFreq), wildF, loadFreq);
    }

    public VillageTemplate {
        targets = Immutable.sorted(targets);
        qualFreq = Immutable.sorted(qualFreq);
    }

    /**
     * Template from a landrace profile (GDD §9.5): targets = profile minimum +
     * 0.05; required qualitative phenotypes fixed (the first {@code any_of}
     * group is taken); {@code extra} adds or overrides frequencies and targets
     * (e.g. {@code color_y2} 0.30, corn {@code yield} 0.40).
     */
    public static VillageTemplate fromLandrace(SpeciesDef s, CultivarDef landrace, Map<TraitId, Double> keyFreqs,
                                               Map<TraitId, Double> extraTargets) {
        Map<TraitId, Double> targets = new TreeMap<>();
        for (Map.Entry<TraitId, Range> e : landrace.quantitative().entrySet()) {
            targets.put(e.getKey(), Math.min(1.0, e.getValue().min() + Defaults.VILLAGE_MARGIN));
        }
        targets.putAll(extraTargets);
        Map<TraitId, Double> freq = new TreeMap<>();
        Map<TraitId, QualReq> req = new TreeMap<>(landrace.qualitative());
        if (!landrace.anyOf().isEmpty()) {
            req.putAll(landrace.anyOf().get(0));
        }
        for (Map.Entry<TraitId, QualReq> e : req.entrySet()) {
            QualTrait q = s.qual(e.getKey());
            if (q == null) {
                continue;
            }
            boolean wantDomestic = e.getValue() == QualReq.DOMESTIC;
            // fixing the allele that shows the wanted phenotype in every genotype
            freq.put(e.getKey(), wantDomestic ? 1.0 : 0.0);
        }
        freq.putAll(keyFreqs);
        return new VillageTemplate(landrace.id(), targets, freq, Defaults.VILLAGE_WILD_F, Defaults.VILLAGE_LOAD_FREQ);
    }

    /** Genome of a village plant. */
    public Genome plant(SpeciesDef s, GenomeLayout layout, long key) {
        return plant(new CompiledSpecies(s, layout), key);
    }

    /**
     * Genome of a village plant. After the targets are laid out on the allele
     * grid, trade-offs and the budget may pull a target trait's {@code G_eff}
     * below its target (e.g. {@code flavor ← yield} when the drawn yield is
     * high). Then the plant is corrected towards the target by {@code G_eff}:
     * one allele unit is added to the short trait, or, if it is full, removed
     * from a drawn (non-target) trait, until every target holds (bounded loop).
     */
    public Genome plant(CompiledSpecies cs, long key) {
        SpeciesDef s = cs.def();
        GenomeLayout layout = cs.layout();
        SplitMix64 rng = new SplitMix64(key);
        Genome.Builder b = Genome.builder(s.id(), layout);
        double[] bg = s.wild().background().asArray();
        for (Locus q : layout.quantitative()) {
            Double t = targets.get(q.trait());
            if (t != null) {
                spread(b, q, (int) Math.ceil(Math.max(0, Math.min(1, t)) * 4 * q.loci() - 1e-9), rng);
            } else {
                for (int l = 0; l < q.loci(); l++) {
                    Alleles.drawLocus(b, q, l, bg, wildF, rng);
                }
            }
        }
        Locus load = layout.load();
        if (load != null) {
            for (int l = 0; l < load.loci(); l++) {
                Alleles.drawLocus(b, load, l, Alleles.biallelic(loadFreq), wildF, rng);
            }
        }
        for (Locus c : layout.qualitative()) {
            QualTrait def = s.qual(c.trait());
            if (def.alleles() > 2) {
                double[] w = new double[def.alleles()];
                java.util.Arrays.fill(w, 1);
                Alleles.drawLocus(b, c, 0, w, 0, rng);
            } else {
                Double fq = qualFreq.get(c.trait());
                Alleles.drawLocus(b, c, 0, Alleles.biallelic(fq != null ? fq : def.wildFreq()), wildF, rng);
            }
        }
        correctByGEff(cs, b, rng);
        return b.build();
    }

    void correctByGEff(CompiledSpecies cs, Genome.Builder b, SplitMix64 rng) {
        List<Locus> quant = cs.layout().quantitative();
        for (int step = 0; step < 256; step++) {
            double[] g = new double[quant.size()];
            for (int i = 0; i < g.length; i++) {
                g[i] = sum(b, quant.get(i)) / (4.0 * quant.get(i).loci());
            }
            double[] eff = cs.gEffOf(g);
            Locus shortTrait = null;
            for (int i = 0; i < g.length; i++) {
                Double t = targets.get(quant.get(i).trait());
                if (t != null && eff[i] < Math.min(1, t) - 1e-9) {
                    shortTrait = quant.get(i);
                    break;
                }
            }
            if (shortTrait == null) {
                return;
            }
            if (!bump(b, shortTrait, +1, rng)) {
                boolean lowered = false;
                for (Locus q : quant) {
                    if (!targets.containsKey(q.trait()) && bump(b, q, -1, rng)) {
                        lowered = true;
                        break;
                    }
                }
                if (!lowered) {
                    return;
                }
            }
        }
    }

    static int sum(Genome.Builder b, Locus q) {
        int s = 0;
        for (int l = 0; l < q.loci(); l++) {
            s += b.get(q, l, 0) + b.get(q, l, 1);
        }
        return s;
    }

    /** Adds ({@code +1}) or removes ({@code −1}) one allele unit at a random slot that allows it. */
    static boolean bump(Genome.Builder b, Locus q, int delta, SplitMix64 rng) {
        int slots = q.loci() * 2;
        int start = rng.nextInt(slots);
        for (int k = 0; k < slots; k++) {
            int i = (start + k) % slots;
            int v = b.get(q, i / 2, i % 2) + delta;
            if (v >= 0 && v <= 2) {
                b.set(q, i / 2, i % 2, v);
                return true;
            }
        }
        return false;
    }

    /** Puts {@code sum} allele units on random allele slots (each slot 0..2). */
    static void spread(Genome.Builder b, Locus q, int sum, SplitMix64 rng) {
        int slots = q.loci() * 2;
        int[] v = new int[slots];
        int left = Math.min(sum, slots * 2);
        while (left > 0) {
            int i = rng.nextInt(slots);
            if (v[i] < 2) {
                v[i]++;
                left--;
            }
        }
        for (int i = 0; i < slots; i++) {
            b.set(q, i / 2, i % 2, v[i]);
        }
    }
}
