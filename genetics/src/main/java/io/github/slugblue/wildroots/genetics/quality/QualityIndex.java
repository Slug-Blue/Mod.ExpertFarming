package io.github.slugblue.wildroots.genetics.quality;

import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.Map;

/**
 * Quality index of a plant {@code Q = Σ w·P} over the cultivar's
 * {@code quality_axes} (GDD §6.4). Weights are normalised by their sum
 * (the validator warns when the sum is not 1); a missing trait counts as
 * {@code P = 0}.
 */
public final class QualityIndex {

    private QualityIndex() {
    }

    public static double of(Phenotype p, Map<TraitId, Double> axes) {
        double sum = 0;
        double wsum = 0;
        for (Map.Entry<TraitId, Double> e : axes.entrySet()) {
            double w = e.getValue() == null || !(e.getValue() > 0) ? 0 : e.getValue();
            sum += w * p.pOr(e.getKey(), 0);
            wsum += w;
        }
        if (!(wsum > 0)) {
            return 0;
        }
        double q = sum / wsum;
        return q > 1 ? 1 : q >= 0 ? q : 0;
    }
}
