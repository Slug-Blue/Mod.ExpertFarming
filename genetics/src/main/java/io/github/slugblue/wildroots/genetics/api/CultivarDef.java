package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.Immutable;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

/**
 * Cultivar or landrace profile (GDD §6.1–6.3). Immutable; validated by
 * {@code DefinitionValidator}.
 *
 * @param id           cultivar id
 * @param species      species id
 * @param tier         1 landrace, 2 cultivar
 * @param priority     tie-break between profiles of one tier (higher wins)
 * @param quantitative thresholds on {@code G_eff}
 * @param qualitative  required qualitative phenotypes (all of them)
 * @param anyOf        alternatives: at least one group must hold completely (GDD §7.3:
 *                     {@code y1y1} or {@code P_}); empty means no alternative
 * @param hysteresis   tolerance for offspring of a plant with this status (GDD §6.2)
 * @param qualityAxes  quality axes {@code Q = Σ w·P} (GDD §6.4)
 * @param parent       optional parent cultivar (branches; reference graph, SEC-29)
 */
public record CultivarDef(CultivarId id, String species, int tier, int priority,
                          SortedMap<TraitId, Range> quantitative, SortedMap<TraitId, QualReq> qualitative,
                          List<SortedMap<TraitId, QualReq>> anyOf, double hysteresis,
                          SortedMap<TraitId, Double> qualityAxes, Optional<CultivarId> parent) {

    public CultivarDef(CultivarId id, String species, int tier, int priority, Map<TraitId, Range> quantitative,
                       Map<TraitId, QualReq> qualitative, List<Map<TraitId, QualReq>> anyOf, double hysteresis,
                       Map<TraitId, Double> qualityAxes) {
        this(id, species, tier, priority, Immutable.sorted(quantitative), Immutable.sorted(qualitative),
                anyOf == null ? List.of() : anyOf.stream().map(Immutable::sorted).toList(), hysteresis,
                Immutable.sorted(qualityAxes), Optional.empty());
    }

    public CultivarDef {
        quantitative = Immutable.sorted(quantitative);
        qualitative = Immutable.sorted(qualitative);
        anyOf = anyOf == null ? List.of() : Immutable.list(anyOf.stream().map(Immutable::sorted).toList());
        qualityAxes = Immutable.sorted(qualityAxes);
        parent = parent == null ? Optional.empty() : parent;
    }
}
