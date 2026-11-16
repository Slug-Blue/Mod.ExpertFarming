package io.github.slugblue.wildroots.genetics.api;

import io.github.slugblue.wildroots.genetics.model.Immutable;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Colour phenotype of a species as data (docs/design/carrot-colours-climate.md
 * §1.3): the ordered colour ids (the position is the {@code variant} of the
 * block), the "first match" rules over the qualitative traits and the
 * properties per colour. A species without colours has {@link #NONE}.
 *
 * @param ids        colour ids; the index of an id is the colour number
 * @param rules      first match wins; an empty condition matches everything
 * @param properties properties by colour id; a missing colour has {@link ColourProps#ZERO}
 */
public record ColourRules(List<String> ids, List<Rule> rules, SortedMap<String, ColourProps> properties) {

    /** One rule: the colour {@code id} when every trait in {@code when} shows the required phenotype. */
    public record Rule(String id, SortedMap<TraitId, QualReq> when) {

        public Rule {
            when = Immutable.sorted(when);
        }

        public Rule(String id, Map<TraitId, QualReq> when) {
            this(id, Immutable.sorted(when));
        }
    }

    /** The species has no colours (the colour is then a matter of the mod's own code). */
    public static final ColourRules NONE = new ColourRules(List.of(), List.of(), Map.<String, ColourProps>of());

    public ColourRules(List<String> ids, List<Rule> rules, Map<String, ColourProps> properties) {
        this(ids, rules, Immutable.sorted(properties));
    }

    public ColourRules {
        ids = Immutable.list(ids);
        rules = Immutable.list(rules);
        properties = Immutable.sorted(properties);
    }

    public boolean isNone() {
        return ids.isEmpty();
    }

    /** Number of the colour {@code id}, or −1. */
    public int indexOf(String id) {
        return ids.indexOf(id);
    }

    /** Properties of the colour number {@code colour}; {@link ColourProps#ZERO} for −1 or an unknown colour. */
    public ColourProps props(int colour) {
        if (colour < 0 || colour >= ids.size()) {
            return ColourProps.ZERO;
        }
        return properties.getOrDefault(ids.get(colour), ColourProps.ZERO);
    }
}
