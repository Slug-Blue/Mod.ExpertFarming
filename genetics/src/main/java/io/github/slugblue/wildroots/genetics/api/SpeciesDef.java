package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.Immutable;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Species definition as the engine sees it (GDD §3.9, docs/05-architecture.md
 * §4.1). Immutable; trait lists are kept sorted by id. The constructor does
 * not validate: {@code DefinitionValidator} reports problems, and the engine
 * runs only on validated definitions (SEC-30).
 *
 * @param id           species id ({@code wildroots:wild_carrot})
 * @param tempMin      lower bound of the temperature window
 * @param tempMax      upper bound of the temperature window
 * @param quantitative quantitative traits
 * @param tradeoffs    pleiotropy pairs, layer 1
 * @param budget       resource budget, layer 2 ({@link Budget#NONE} if absent)
 * @param wild         wild allele frequencies
 * @param wildF        initial inbreeding of a wild patch
 * @param qualitative  qualitative traits
 * @param starterFreq  domestic-allele frequencies that replace {@code wildFreq} in the starter
 *                     patch (GDD §11.2, worldgen §6.4)
 * @param load         inbreeding load
 * @param mutation     mutation rates
 * @param pollination  pollination
 * @param timing       stage timing
 * @param qualityAxes  quality axes of the line for wild plants (GDD §6.4: like the main cultivar)
 * @param colours      colour rules and properties ({@link ColourRules#NONE} when the species has no colours)
 * @param climateStress wilting threshold, humid penalty and protection ({@link ClimateStress#NONE} = never wilts)
 */
public record SpeciesDef(String id, double tempMin, double tempMax, List<QuantTrait> quantitative,
                         List<Tradeoff> tradeoffs, Budget budget, WildFrequencies wild, double wildF,
                         List<QualTrait> qualitative, SortedMap<TraitId, Double> starterFreq, LoadDef load,
                         MutationRates mutation, Pollination pollination, Timing timing,
                         SortedMap<TraitId, Double> qualityAxes, ColourRules colours, ClimateStress climateStress) {

    /** Without climate stress: the species never wilts. */
    public SpeciesDef(String id, double tempMin, double tempMax, List<QuantTrait> quantitative,
                      List<Tradeoff> tradeoffs, Budget budget, WildFrequencies wild, double wildF,
                      List<QualTrait> qualitative, Map<TraitId, Double> starterFreq, LoadDef load,
                      MutationRates mutation, Pollination pollination, Timing timing,
                      Map<TraitId, Double> qualityAxes, ColourRules colours) {
        this(id, tempMin, tempMax, quantitative, tradeoffs, budget, wild, wildF, qualitative,
                Immutable.sorted(starterFreq), load, mutation, pollination, timing, Immutable.sorted(qualityAxes),
                colours, ClimateStress.NONE);
    }

    public SpeciesDef(String id, double tempMin, double tempMax, List<QuantTrait> quantitative,
                      List<Tradeoff> tradeoffs, Budget budget, WildFrequencies wild, double wildF,
                      List<QualTrait> qualitative, Map<TraitId, Double> starterFreq, LoadDef load,
                      MutationRates mutation, Pollination pollination, Timing timing,
                      Map<TraitId, Double> qualityAxes, ColourRules colours, ClimateStress climateStress) {
        this(id, tempMin, tempMax, quantitative, tradeoffs, budget, wild, wildF, qualitative,
                Immutable.sorted(starterFreq), load, mutation, pollination, timing, Immutable.sorted(qualityAxes),
                colours, climateStress);
    }

    /** A species without colours. */
    public SpeciesDef(String id, double tempMin, double tempMax, List<QuantTrait> quantitative,
                      List<Tradeoff> tradeoffs, Budget budget, WildFrequencies wild, double wildF,
                      List<QualTrait> qualitative, Map<TraitId, Double> starterFreq, LoadDef load,
                      MutationRates mutation, Pollination pollination, Timing timing,
                      Map<TraitId, Double> qualityAxes) {
        this(id, tempMin, tempMax, quantitative, tradeoffs, budget, wild, wildF, qualitative, starterFreq, load,
                mutation, pollination, timing, qualityAxes, ColourRules.NONE);
    }

    public SpeciesDef {
        quantitative = Immutable.list(Immutable.list(quantitative).stream()
                .sorted(Comparator.comparing(QuantTrait::id)).toList());
        qualitative = Immutable.list(Immutable.list(qualitative).stream()
                .sorted(Comparator.comparing(QualTrait::id)).toList());
        tradeoffs = Immutable.list(tradeoffs);
        budget = budget == null ? Budget.NONE : budget;
        starterFreq = Immutable.sorted(starterFreq);
        qualityAxes = Immutable.sorted(qualityAxes);
        colours = colours == null ? ColourRules.NONE : colours;
        climateStress = climateStress == null ? ClimateStress.NONE : climateStress;
    }

    /** Quantitative trait by id or {@code null}. */
    public QuantTrait quant(TraitId t) {
        for (QuantTrait q : quantitative) {
            if (q.id().equals(t)) {
                return q;
            }
        }
        return null;
    }

    /** Qualitative trait by id or {@code null}. */
    public QualTrait qual(TraitId t) {
        for (QualTrait q : qualitative) {
            if (q.id().equals(t)) {
                return q;
            }
        }
        return null;
    }

    /** The S-locus or {@code null}. */
    public QualTrait sLocus() {
        for (QualTrait q : qualitative) {
            if (q.selfIncompat()) {
                return q;
            }
        }
        return null;
    }
}
