package io.github.slugblue.wildroots.genetics.phenotype;

import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Tradeoff;
import io.github.slugblue.wildroots.genetics.api.Traits;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.ArrayList;
import java.util.List;

/**
 * A validated {@link SpeciesDef} turned into index arrays for the hot path
 * (phenotype of every plant). Built once per species and cached by the facade.
 */
public final class CompiledSpecies {

    final SpeciesDef def;
    final GenomeLayout layout;
    final List<TraitId> quantIds;
    final Locus[] quant;
    final double[] sigma;
    final boolean[] vigorAffected;
    final int[] pairTarget;
    final int[] pairAgainst;
    final double[] pairC;
    final double[] pairTheta;
    final double[] budgetWeight;
    final double budgetLimit;
    final List<TraitId> qualIds;
    final Locus[] qual;
    final QualTrait[] qualDefs;
    final int bitterIndex;
    final int coldIndex;
    final int heatIndex;
    final int droughtIndex;

    public CompiledSpecies(SpeciesDef def) {
        this(def, GenomeLayout.of(def));
    }

    public CompiledSpecies(SpeciesDef def, GenomeLayout layout) {
        this.def = def;
        this.layout = layout;
        int n = layout.quantitative().size();
        quant = layout.quantitative().toArray(new Locus[0]);
        List<TraitId> ids = new ArrayList<>();
        sigma = new double[n];
        vigorAffected = new boolean[n];
        budgetWeight = new double[n];
        for (int i = 0; i < n; i++) {
            TraitId t = quant[i].trait();
            ids.add(t);
            QuantTrait q = def.quant(t);
            sigma[i] = q.sigmaEnv();
            vigorAffected[i] = q.vigorAffected();
            Double w = def.budget().weights().get(t);
            budgetWeight[i] = w == null ? 0 : w;
        }
        quantIds = List.copyOf(ids);
        budgetLimit = def.budget().isNone() ? Double.MAX_VALUE : def.budget().limit();
        List<Tradeoff> pairs = def.tradeoffs();
        pairTarget = new int[pairs.size()];
        pairAgainst = new int[pairs.size()];
        pairC = new double[pairs.size()];
        pairTheta = new double[pairs.size()];
        for (int k = 0; k < pairs.size(); k++) {
            Tradeoff t = pairs.get(k);
            pairTarget[k] = quantIds.indexOf(t.trait());
            pairAgainst[k] = quantIds.indexOf(t.against());
            pairC[k] = t.c();
            pairTheta[k] = t.threshold();
        }
        qual = layout.qualitative().toArray(new Locus[0]);
        qualDefs = new QualTrait[qual.length];
        List<TraitId> qids = new ArrayList<>();
        int bitter = -1;
        for (int i = 0; i < qual.length; i++) {
            qualDefs[i] = def.qual(qual[i].trait());
            qids.add(qual[i].trait());
            if (qual[i].trait().equals(Traits.BITTERNESS)) {
                bitter = i;
            }
        }
        qualIds = List.copyOf(qids);
        bitterIndex = bitter;
        coldIndex = quantIds.indexOf(Traits.COLD_TOLERANCE);
        heatIndex = quantIds.indexOf(Traits.HEAT_TOLERANCE);
        droughtIndex = quantIds.indexOf(Traits.DROUGHT_TOLERANCE);
    }

    public SpeciesDef def() {
        return def;
    }

    public GenomeLayout layout() {
        return layout;
    }

    /** Quantitative traits in layout order (index of {@link NoiseVector} entries). */
    public List<TraitId> quantIds() {
        return quantIds;
    }

    public int quantIndex(TraitId t) {
        return quantIds.indexOf(t);
    }

    /** {@code G_eff} from genotypic values {@code G} in layout order (layers 1 and 2 of GDD §3.10). */
    public double[] gEffOf(double[] g) {
        double[] pair = Pleiotropy.apply(g, pairTarget, pairAgainst, pairC, pairTheta);
        return ResourceBudget.apply(pair, budgetWeight, budgetLimit);
    }

    public double sigma(int i) {
        return sigma[i];
    }
}
