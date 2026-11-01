package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Immutable;
import java.util.List;

/**
 * Genetic part of a seed lot (GDD §5.3): mother, up to 4 + 4 fathers and the
 * outcrossing share. Display fields of the lot live in the mod layer.
 *
 * @param mother  mother genome
 * @param fathers fathers with weights (≤ 8, SEC-30)
 * @param pOut    probability that a seed has an outside father; otherwise selfing
 */
public record LotRecord(Genome mother, List<FatherPick> fathers, double pOut) {

    public LotRecord {
        fathers = Immutable.list(fathers);
    }

    public String species() {
        return mother.species();
    }
}
