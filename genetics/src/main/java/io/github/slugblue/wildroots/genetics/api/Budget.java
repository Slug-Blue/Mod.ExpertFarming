package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.Immutable;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.Map;
import java.util.SortedMap;

/**
 * Resource budget, layer 2 of GDD §3.10: {@code S = Σ wᵢ·G_pair(i)}; when
 * {@code S > limit} every budget trait is scaled by {@code limit/S}.
 */
public record Budget(double limit, SortedMap<TraitId, Double> weights) {

    /** No budget: the species has no {@code budget} block (GDD §3.10 edge cases). */
    public static final Budget NONE = new Budget(Double.MAX_VALUE, Map.of());

    public Budget(double limit, Map<TraitId, Double> weights) {
        this(limit, Immutable.sorted(weights));
    }

    public Budget {
        weights = Immutable.sorted(weights);
    }

    public boolean isNone() {
        return weights.isEmpty();
    }
}
