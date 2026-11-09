package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.data.DefinitionsSnapshot;
import io.github.slugblue.wildroots.data.SpeciesInfo;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.QuantTrait;
import io.github.slugblue.wildroots.genetics.api.Range;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.api.Traits;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.journal.JournalTriggers;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

/**
 * Tier-1 lines of the hand lens (narrative §7.2 rows 14–17, §9.2–9.4; GDD
 * §10): word scales of {@code P}, bitterness, pollination, signs of
 * inbreeding and "looks like a cultivar". Written by the server as
 * {@link DispCodec} lines with key arguments; the client only draws them.
 */
public final class LensReading {

    public static final String TRAIT = "wildroots.tooltip.trait";
    public static final String LOOKS_LIKE = "wildroots.tooltip.looks_like";
    public static final String AILING = "wildroots.tooltip.pollination.ailing";

    /** How the mother was pollinated (narrative §9.4 ids). */
    public enum Pollination {
        BEES, HAND, NEIGHBOURS, SELF, NONE
    }

    private LensReading() {
    }

    static String path(String id) {
        return JournalTriggers.speciesEntry(id).substring("species.".length());
    }

    /** One line per quantitative trait of the species: name and scale word of {@code P}. */
    public static List<DispCodec.Line> traits(SpeciesDef def, SpeciesInfo info, Phenotype p) {
        List<DispCodec.Line> out = new ArrayList<>();
        Map<String, String> dt = info.displayTraits();
        for (QuantTrait q : def.quantitative()) {
            String t = q.id().value();
            double v = p.pOr(q.id(), Double.NaN);
            String scale = LensScale.scale(t, dt);
            out.add(DispCodec.Line.of(TRAIT, DispCodec.keyArg(LensScale.nameKey(t, path(def.id()), dt)),
                    DispCodec.keyArg(LensScale.wordKey(scale, LensScale.step(v)))));
        }
        return out;
    }

    /** {@code Bitter: yes / no} when the species has the bitterness locus (narrative §9.2). */
    public static Optional<DispCodec.Line> bitterness(SpeciesDef def, Phenotype p) {
        if (def.qual(Traits.BITTERNESS) == null) {
            return Optional.empty();
        }
        return Optional.of(DispCodec.Line.of("wildroots.tooltip.qual_state.bitterness."
                + (p.domestic(Traits.BITTERNESS) ? "domestic" : "wild")));
    }

    public static Optional<DispCodec.Line> pollination(Pollination how) {
        return switch (how) {
            case BEES -> Optional.of(DispCodec.Line.of("wildroots.tooltip.pollination.bees"));
            case HAND -> Optional.of(DispCodec.Line.of("wildroots.tooltip.pollination.hand"));
            case NEIGHBOURS -> Optional.of(DispCodec.Line.of("wildroots.tooltip.pollination.neighbours"));
            case SELF -> Optional.of(DispCodec.Line.of("wildroots.tooltip.pollination.self"));
            case NONE -> Optional.empty();
        };
    }

    public static Optional<DispCodec.Line> ailing(Phenotype p) {
        return p.vigor() < GrowthLogic.AILING_BELOW ? Optional.of(DispCodec.Line.of(AILING)) : Optional.empty();
    }

    /**
     * "Looks like a cultivar" (GDD §6.2 item 5): the phenotype {@code P} and
     * the visible qualitative states pass a profile of the species that the
     * plant's status does not reach (the status comes from the genotype).
     * The highest tier wins, then the larger priority, then the smaller id.
     */
    public static Optional<CultivarDef> looksLike(DefinitionsSnapshot snap, SpeciesDef def, Phenotype p,
                                                  Optional<String> status, int statusTier) {
        return looksLike(snap.profiles(def.id()), p, status, statusTier);
    }

    static Optional<CultivarDef> looksLike(List<CultivarDef> profiles, Phenotype p, Optional<String> status,
                                           int statusTier) {
        return profiles.stream()
                .filter(c -> c.tier() > statusTier && !status.map(s -> s.equals(c.id().value())).orElse(false))
                .filter(c -> passes(c, p))
                .max(Comparator.comparingInt(CultivarDef::tier).thenComparingInt(CultivarDef::priority)
                        .thenComparing(c -> c.id().value(), Comparator.reverseOrder()));
    }

    static boolean passes(CultivarDef c, Phenotype p) {
        for (Map.Entry<TraitId, Range> e : c.quantitative().entrySet()) {
            double v = p.pOr(e.getKey(), Double.NaN);
            if (!(v >= e.getValue().min() && v <= e.getValue().max())) {
                return false;
            }
        }
        if (!qual(c.qualitative(), p)) {
            return false;
        }
        return c.anyOf().isEmpty() || c.anyOf().stream().anyMatch(g -> qual(g, p));
    }

    static boolean qual(SortedMap<TraitId, QualReq> req, Phenotype p) {
        for (Map.Entry<TraitId, QualReq> e : req.entrySet()) {
            if (!p.qualitativeTraits().contains(e.getKey())
                    || p.domestic(e.getKey()) != (e.getValue() == QualReq.DOMESTIC)) {
                return false;
            }
        }
        return true;
    }

    public static DispCodec.Line looksLikeLine(CultivarDef c) {
        return DispCodec.Line.of(LOOKS_LIKE, DispCodec.keyArg(JournalTriggers.nameKey("cultivar", c.id().value())));
    }

    /** Species with their own {@code harvest_ready} wording (narrative §9.7). */
    static final java.util.Set<String> HARVEST_READY_SPECIFIC = java.util.Set.of("wild_carrot", "sea_beet", "wild_onion",
            "wild_potato", "wild_cane", "wild_cacao", "wild_raspberry", "wild_apple");

    /** Stage key of a field plant (narrative §3.4) by stage and life cycle. */
    public static String stageKey(int stage, SpeciesInfo info, String speciesPath) {
        boolean biennial = "biennial".equals(info.lifeCycle());
        String id = switch (stage) {
            case 0 -> "seed";
            case 1 -> biennial ? "rosette" : "sprout";
            case 2 -> biennial ? "rosette" : "vegetative";
            case 3 -> biennial ? "harvest_ready" : "vegetative";
            case 4 -> "flowering";
            default -> "seed_ripening";
        };
        String key = "wildroots.tooltip.stage." + id;
        return id.equals("harvest_ready") && HARVEST_READY_SPECIFIC.contains(speciesPath) ? key + "." + speciesPath : key;
    }
}
