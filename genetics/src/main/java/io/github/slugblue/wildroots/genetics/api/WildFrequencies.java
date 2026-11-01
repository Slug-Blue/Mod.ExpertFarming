package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.Immutable;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.Map;
import java.util.SortedMap;

/**
 * Wild allele frequencies (GDD §3.2, worldgen §5.1).
 *
 * @param background    background loci, e.g. 0.60 / 0.35 / 0.05
 * @param elite         elite loci of a patch category, e.g. 0.30 / 0.40 / 0.30
 * @param eliteLoci     elite loci per trait
 * @param richElite     elite loci of a rich patch, e.g. 0.15 / 0.40 / 0.45
 * @param richExtraLoci extra elite loci of rich patches per trait (worldgen §5.1)
 */
public record WildFrequencies(AlleleFreq background, AlleleFreq elite, SortedMap<TraitId, Integer> eliteLoci,
                              AlleleFreq richElite, SortedMap<TraitId, Integer> richExtraLoci) {

    public WildFrequencies(AlleleFreq background, AlleleFreq elite, Map<TraitId, Integer> eliteLoci,
                           AlleleFreq richElite, Map<TraitId, Integer> richExtraLoci) {
        this(background, elite, Immutable.sorted(eliteLoci), richElite, Immutable.sorted(richExtraLoci));
    }

    public WildFrequencies {
        eliteLoci = Immutable.sorted(eliteLoci);
        richExtraLoci = Immutable.sorted(richExtraLoci);
    }
}
