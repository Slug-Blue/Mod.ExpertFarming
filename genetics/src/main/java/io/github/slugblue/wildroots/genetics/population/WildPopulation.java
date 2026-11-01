package io.github.slugblue.wildroots.genetics.population;

import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.WildFrequencies;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;

/**
 * Wild patches (GDD §3.2, worldgen §3.3, §5, §6.4).
 *
 * <ul>
 *   <li>Elite loci of a trait: {@code k} of {@code L} loci chosen by
 *   {@code Hash64(worldSeed, species, category)} mixed with the trait id.
 *   Same for every patch of the category, different between categories.</li>
 *   <li>Rich patches add extra elite loci of {@code yield} and
 *   {@code fruit_size} from {@code Hash64(worldSeed, species, category, "rich")}
 *   and use the rich frequencies on all elite loci (worldgen §5.1).</li>
 *   <li>Alleles are drawn by the background or elite frequencies with the
 *   species' initial inbreeding {@code wild_F}; load alleles with
 *   {@code load.freq}; qualitative with {@code wild_freq}, replaced by
 *   {@code starter_freq} in the starter patch; the S-locus takes 6 of its
 *   alleles per patch, from {@code Hash64(worldSeed, origin, species)}.</li>
 * </ul>
 * The plant key is {@code Hash64(worldSeed, patchOrigin, plantPos)}
 * (docs/05-architecture.md §4.3); the genome is lazy and deterministic.
 */
public final class WildPopulation {

    private WildPopulation() {
    }

    /** Key of the elite loci of a patch category: {@code Hash64(worldSeed, species, category)}. */
    public static long categoryKey(long worldSeed, String species, String category) {
        return Hash64.of(worldSeed, Hash64.str(species), Hash64.str(category));
    }

    /** Elite flags per locus of {@code trait}: {@code count} of {@code loci} loci. */
    public static boolean[] eliteLoci(long categoryKey, TraitId trait, int loci, int count) {
        return chooseLoci(Hash64.of(categoryKey, Hash64.str(trait.value())), loci, count, new boolean[loci]);
    }

    /** Elite flags of a rich patch: the category's loci plus the extra rich loci. */
    public static boolean[] richLoci(long categoryKey, TraitId trait, int loci, int count, int extra) {
        boolean[] base = eliteLoci(categoryKey, trait, loci, count);
        long richKey = Hash64.of(categoryKey, Hash64.str("rich"), Hash64.str(trait.value()));
        return chooseLoci(richKey, loci, count + extra, base);
    }

    /** Marks loci until {@code total} are marked, keeping marks already set (partial Fisher–Yates). */
    static boolean[] chooseLoci(long key, int loci, int total, boolean[] marked) {
        boolean[] out = marked.clone();
        int have = 0;
        int[] free = new int[loci];
        int nFree = 0;
        for (int i = 0; i < loci; i++) {
            if (out[i]) {
                have++;
            } else {
                free[nFree++] = i;
            }
        }
        SplitMix64 rng = new SplitMix64(key);
        int want = Math.max(0, Math.min(total, loci)) - have;
        for (int k = 0; k < want && nFree > 0; k++) {
            int j = rng.nextInt(nFree);
            out[free[j]] = true;
            free[j] = free[--nFree];
        }
        return out;
    }

    /** S-alleles present in a patch: {@code n} of the species' alleles. */
    public static int[] patchSAlleles(PatchPassport p, QualTrait s) {
        int total = s.alleles();
        int n = Math.max(1, Math.min(total, s.patchAlleles()));
        int[] pool = new int[total];
        for (int i = 0; i < total; i++) {
            pool[i] = i;
        }
        SplitMix64 rng = new SplitMix64(Hash64.of(p.worldSeed(), p.origin(), Hash64.str(p.species()),
                Hash64.str("s_alleles")));
        for (int i = 0; i < n; i++) {
            int j = i + rng.nextInt(total - i);
            int t = pool[i];
            pool[i] = pool[j];
            pool[j] = t;
        }
        return java.util.Arrays.copyOf(pool, n);
    }

    /** Lazy wild genome of one plant of the patch. */
    public static Genome wildPlant(SpeciesDef s, GenomeLayout layout, PatchPassport p, long plantKey) {
        SplitMix64 rng = new SplitMix64(plantKey);
        WildFrequencies wf = s.wild();
        long catKey = categoryKey(p.worldSeed(), s.id(), p.categoryId());
        double f = s.wildF();
        Genome.Builder b = Genome.builder(s.id(), layout);
        double[] bg = wf.background().asArray();
        double[] elite = (p.rich() ? wf.richElite() : wf.elite()).asArray();
        for (Locus q : layout.quantitative()) {
            int count = wf.eliteLoci().getOrDefault(q.trait(), 0);
            boolean[] el = p.rich()
                    ? richLoci(catKey, q.trait(), q.loci(), count, wf.richExtraLoci().getOrDefault(q.trait(), 0))
                    : eliteLoci(catKey, q.trait(), q.loci(), count);
            for (int l = 0; l < q.loci(); l++) {
                Alleles.drawLocus(b, q, l, el[l] ? elite : bg, f, rng);
            }
        }
        Locus load = layout.load();
        if (load != null) {
            double[] lf = Alleles.biallelic(s.load().freq());
            for (int l = 0; l < load.loci(); l++) {
                Alleles.drawLocus(b, load, l, lf, f, rng);
            }
        }
        for (Locus c : layout.qualitative()) {
            QualTrait def = s.qual(c.trait());
            if (def.alleles() > 2) {
                int[] present = patchSAlleles(p, def);
                double[] w = new double[def.alleles()];
                for (int a : present) {
                    w[a] = 1;
                }
                Alleles.drawLocus(b, c, 0, w, f, rng);
            } else {
                Double starter = p.starter() ? s.starterFreq().get(c.trait()) : null;
                Alleles.drawLocus(b, c, 0, Alleles.biallelic(starter != null ? starter : def.wildFreq()), f, rng);
            }
        }
        return b.build();
    }
}
