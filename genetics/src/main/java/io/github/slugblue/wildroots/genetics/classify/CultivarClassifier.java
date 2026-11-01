package io.github.slugblue.wildroots.genetics.classify;

import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.Range;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.phenotype.PhenotypeCalculator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

/**
 * Cultivar recognition (GDD §6.1–6.2, docs/05-architecture.md §4.2).
 *
 * <ul>
 *   <li>Uses {@code G_eff} and the qualitative genotype, never the phenotype
 *   {@code P}: no "cultivar by luck".</li>
 *   <li>Several matching profiles: the highest tier, then the highest
 *   {@code priority}, then the smaller id (determinism).</li>
 *   <li>Offspring of a plant with status X keep X while every quantitative
 *   threshold of X holds within {@code hysteresis} (min − h, max + h);
 *   qualitative requirements have no tolerance. {@code hysteresis 0}
 *   disables the inheritance.</li>
 *   <li>A clone keeps the status of its source always ({@link #forClone}).</li>
 * </ul>
 */
public final class CultivarClassifier {

    private CultivarClassifier() {
    }

    public static Classification classify(CompiledSpecies cs, Genome g, List<CultivarDef> profiles,
                                          Optional<CultivarId> parentStatus) {
        double[] eff = PhenotypeCalculator.gEff(cs, g);
        CultivarDef best = null;
        boolean bestInherited = false;
        for (CultivarDef c : profiles) {
            if (!c.species().equals(cs.def().id())) {
                continue;
            }
            boolean strict = matches(cs, c, g, eff, 0);
            boolean inherited = false;
            if (!strict && parentStatus.isPresent() && parentStatus.get().equals(c.id()) && c.hysteresis() > 0) {
                inherited = matches(cs, c, g, eff, c.hysteresis());
            }
            if ((strict || inherited) && better(c, best)) {
                best = c;
                bestInherited = inherited;
            }
        }
        if (best == null) {
            return Classification.WILD;
        }
        return new Classification(Optional.of(best.id()), best.tier(), bestInherited);
    }

    /** Status of a clone: the source's status, unconditionally (GDD §6.2 p. 3). */
    public static Classification forClone(Classification source) {
        if (source.cultivar().isEmpty()) {
            return Classification.WILD;
        }
        return new Classification(source.cultivar(), source.tier(), true);
    }

    static boolean better(CultivarDef c, CultivarDef best) {
        if (best == null) {
            return true;
        }
        if (c.tier() != best.tier()) {
            return c.tier() > best.tier();
        }
        if (c.priority() != best.priority()) {
            return c.priority() > best.priority();
        }
        return c.id().compareTo(best.id()) < 0;
    }

    /** Whether {@code g} fits profile {@code c} with quantitative tolerance {@code tol}. */
    public static boolean matches(CompiledSpecies cs, CultivarDef c, Genome g, double[] eff, double tol) {
        for (Map.Entry<TraitId, Range> e : c.quantitative().entrySet()) {
            int i = cs.quantIndex(e.getKey());
            if (i < 0) {
                return false;
            }
            double v = eff[i];
            if (v < e.getValue().min() - tol || v > e.getValue().max() + tol) {
                return false;
            }
        }
        if (!qualitativeHolds(cs, c.qualitative(), g)) {
            return false;
        }
        if (c.anyOf().isEmpty()) {
            return true;
        }
        for (SortedMap<TraitId, QualReq> group : c.anyOf()) {
            if (qualitativeHolds(cs, group, g)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Each requirement compares the visible phenotype of the genotype: a
     * recessive trait as {@code aa}, a dominant one as {@code A_} (GDD §3.6).
     */
    static boolean qualitativeHolds(CompiledSpecies cs, Map<TraitId, QualReq> req, Genome g) {
        for (Map.Entry<TraitId, QualReq> e : req.entrySet()) {
            QualTrait q = cs.def().qual(e.getKey());
            Locus s = g.layout().section(e.getKey());
            if (q == null || s == null || q.alleles() != 2) {
                return false;
            }
            boolean domestic = q.domesticPhenotype(g.allele(s, 0, 0), g.allele(s, 0, 1));
            if (domestic != (e.getValue() == QualReq.DOMESTIC)) {
                return false;
            }
        }
        return true;
    }
}
