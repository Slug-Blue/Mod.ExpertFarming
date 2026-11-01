package io.github.slugblue.wildroots.genetics.tempo;

import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.PatchPassport;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.breeding.Offspring;
import io.github.slugblue.wildroots.genetics.classify.CultivarClassifier;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.phenotype.PhenotypeCalculator;
import io.github.slugblue.wildroots.genetics.pollination.SelfIncompatibility;
import io.github.slugblue.wildroots.genetics.population.VillageTemplate;
import io.github.slugblue.wildroots.genetics.population.WildPopulation;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port of the canonical tempo model v2 (GDD §12.3–12.5) onto the real
 * engine: wild patches, meiosis, mutations, trade-offs, vitality and
 * classification are the production classes. The player is the scripted
 * "journal hint" player of §12.3. Only the seed-producing lines without
 * clones and brush are modelled (wave 1: carrot).
 *
 * <p>One run returns the generation of the first plant in the landrace
 * profile and of the first plant in the cultivar profile (by {@code G_eff}
 * and genotype), or {@code -1} if none appears by generation 30.
 */
public final class TempoModel {

    /**
     * Parameters of one line (carrot values in {@code TempoModelTest#carrot}).
     * {@code rareAlleleChance} is for lines without a landrace ("new patch with
     * the rare allele at 0.25"); every wave-1 line has one, so it is not used yet.
     */
    public record Params(SpeciesDef species, CultivarDef landrace, CultivarDef cultivar, VillageTemplate village,
                         TraitId keyRecessive, int population, int parents, int patches, int landraceGen,
                         int maxGen, double pOut, double freshOutcross, double visibleBonus,
                         double analyzerAlleleBonus, int analyzerGen, double stuntedPenalty,
                         double rareAlleleChance) {
    }

    private final Params p;
    private final CompiledSpecies cs;
    private final GenomeLayout layout;
    private final List<CultivarDef> profiles;

    public TempoModel(Params p) {
        this.p = p;
        this.cs = new CompiledSpecies(p.species());
        this.layout = cs.layout();
        this.profiles = List.of(p.landrace(), p.cultivar());
    }

    /** Result of one run. */
    public record Run(int landraceGen, int cultivarGen) {
    }

    private record Plant(Genome g, boolean fresh, double score) {
    }

    public Run run(long seed) {
        SplitMix64 rng = new SplitMix64(seed);
        long worldSeed = rng.nextLong();
        List<Genome> pop = new ArrayList<>();
        String[] cats = {"meadow", "plains", "forest_edge", "flower_fields", "taiga"};
        int perPatch = p.population() / p.patches();
        for (int k = 0; k < p.patches(); k++) {
            PatchPassport pp = new PatchPassport(p.species().id(), cats[k % cats.length], false, rng.nextLong(), false,
                    worldSeed);
            for (int i = 0; i < perPatch; i++) {
                pop.add(WildPopulation.wildPlant(p.species(), layout, pp, rng.nextLong()));
            }
        }
        int landraceGen = -1;
        double bestProfile = -1;
        int stagnant = 0;
        for (int gen = 1; gen <= p.maxGen(); gen++) {
            List<Genome> fresh = new ArrayList<>();
            if (gen == p.landraceGen()) {
                for (int i = 0; i < 4; i++) {
                    fresh.add(p.village().plant(p.species(), layout, Hash64.of(worldSeed, gen, i)));
                }
            } else if (gen >= 5 && gen % 3 == 0
                    && (!hasCarrier(pop, p.keyRecessive()) || stagnant >= 3 || lostRequiredRecessive(pop))) {
                for (int i = 0; i < 4; i++) {
                    fresh.add(p.village().plant(p.species(), layout, Hash64.of(worldSeed, gen, i)));
                }
            }
            List<Plant> plants = new ArrayList<>();
            for (Genome g : pop) {
                plants.add(new Plant(g, false, score(g, gen, rng)));
            }
            for (Genome g : fresh) {
                plants.add(new Plant(g, true, score(g, gen, rng)));
            }
            int bestTier = 0;
            for (Plant pl : plants) {
                Classification c = CultivarClassifier.classify(cs, pl.g(), profiles, Optional.empty());
                bestTier = Math.max(bestTier, c.tier());
            }
            if (bestTier >= 1 && landraceGen < 0) {
                landraceGen = gen;
            }
            if (bestTier >= 2) {
                return new Run(landraceGen, gen);
            }
            double profileBest = 0;
            for (Plant pl : plants) {
                profileBest = Math.max(profileBest, profileSum(pl.g()));
            }
            if (profileBest > bestProfile + 1e-9) {
                bestProfile = profileBest;
                stagnant = 0;
            } else {
                stagnant++;
            }
            List<Plant> parents = new ArrayList<>();
            plants.stream().filter(Plant::fresh).sorted(Comparator.comparingDouble(Plant::score).reversed()).limit(2)
                    .forEach(parents::add);
            plants.stream().filter(pl -> !parents.contains(pl))
                    .sorted(Comparator.comparingDouble(Plant::score).reversed())
                    .limit(p.parents() - parents.size()).forEach(parents::add);
            List<Genome> next = new ArrayList<>();
            for (int i = 0; i < p.population(); i++) {
                Plant mother = parents.get(i % parents.size());
                double out = mother.fresh() ? p.freshOutcross() : p.pOut();
                Genome father = mother.g();
                if (parents.size() > 1 && rng.chance(out)) {
                    List<Genome> others = new ArrayList<>();
                    for (Plant o : parents) {
                        if (o != mother && SelfIncompatibility.compatible(p.species(), mother.g(), o.g())) {
                            others.add(o.g());
                        }
                    }
                    if (!others.isEmpty()) {
                        father = others.get(rng.nextInt(others.size()));
                    }
                }
                next.add(Offspring.cross(mother.g(), father, p.species().mutation(), rng));
            }
            pop = next;
        }
        return new Run(landraceGen, -1);
    }

    /**
     * Player's score (GDD §12.5): {@code Σ w·P} over the cultivar's quality
     * axes, plus a bonus for visible required phenotypes (before the analyzer)
     * or for required alleles in the genotype (from the analyzer generation),
     * minus a penalty for stunted plants ({@code V < 0.85}).
     */
    double score(Genome g, int gen, SplitMix64 rng) {
        Phenotype ph = PhenotypeCalculator.compute(cs, g, Environment.neutral(
                (p.species().tempMin() + p.species().tempMax()) / 2), PhenotypeCalculator.rollNoise(cs, rng.nextLong()));
        double s = 0;
        for (Map.Entry<TraitId, Double> e : p.cultivar().qualityAxes().entrySet()) {
            s += e.getValue() * ph.pOr(e.getKey(), 0);
        }
        for (Map.Entry<TraitId, QualReq> e : p.cultivar().qualitative().entrySet()) {
            QualTrait q = p.species().qual(e.getKey());
            Locus loc = layout.section(e.getKey());
            int a = g.allele(loc, 0, 0);
            int b = g.allele(loc, 0, 1);
            int want = e.getValue() == QualReq.DOMESTIC ? 1 : 0;
            if (gen < p.analyzerGen()) {
                if (visible(g, e.getKey()) && q.domesticPhenotype(a, b) == (want == 1)) {
                    s += p.visibleBonus();
                }
            } else {
                s += p.analyzerAlleleBonus() * ((a == want ? 1 : 0) + (b == want ? 1 : 0));
            }
        }
        if (ph.vigor() < 0.85) {
            s -= p.stuntedPenalty();
        }
        return s;
    }

    /**
     * Visibility rule of GDD §3.6: bitterness is tasted; root colour is seen,
     * but {@code y2} shows only on a yellow-based root ({@code y1y1}, no
     * {@code P_}); {@code P} itself is always visible.
     */
    boolean visible(Genome g, TraitId t) {
        String id = t.value();
        if (id.equals("color_y2")) {
            return domestic(g, "color_y1") && !domestic(g, "color_p");
        }
        if (id.equals("color_y1")) {
            return !domestic(g, "color_p");
        }
        return true;
    }

    boolean domestic(Genome g, String trait) {
        TraitId t = TraitId.of(trait);
        QualTrait q = p.species().qual(t);
        Locus loc = layout.section(t);
        return q != null && q.domesticPhenotype(g.allele(loc, 0, 0), g.allele(loc, 0, 1));
    }

    double profileSum(Genome g) {
        double[] eff = PhenotypeCalculator.gEff(cs, g);
        double s = 0;
        for (TraitId t : p.cultivar().quantitative().keySet()) {
            s += eff[cs.quantIndex(t)];
        }
        return s;
    }

    boolean hasCarrier(List<Genome> pop, TraitId t) {
        Locus loc = layout.section(t);
        for (Genome g : pop) {
            if (g.allele(loc, 0, 0) == 1 || g.allele(loc, 0, 1) == 1) {
                return true;
            }
        }
        return false;
    }

    boolean lostRequiredRecessive(List<Genome> pop) {
        for (Map.Entry<TraitId, QualReq> e : p.cultivar().qualitative().entrySet()) {
            QualTrait q = p.species().qual(e.getKey());
            int want = e.getValue() == QualReq.DOMESTIC ? 1 : 0;
            boolean recessive = (want == 1) == (q.dominant() == io.github.slugblue.wildroots.genetics.api.Dominance.WILD);
            if (!recessive) {
                continue;
            }
            Locus loc = layout.section(e.getKey());
            boolean present = false;
            for (Genome g : pop) {
                if (g.allele(loc, 0, 0) == want || g.allele(loc, 0, 1) == want) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                return true;
            }
        }
        return false;
    }

    /** Median of non-negative results; failures (−1) count as +∞. */
    public static double median(int[] xs) {
        int[] s = xs.clone();
        for (int i = 0; i < s.length; i++) {
            if (s[i] < 0) {
                s[i] = Integer.MAX_VALUE;
            }
        }
        java.util.Arrays.sort(s);
        int n = s.length;
        return n % 2 == 1 ? s[n / 2] : (s[n / 2 - 1] / 2.0 + s[n / 2] / 2.0);
    }
}
