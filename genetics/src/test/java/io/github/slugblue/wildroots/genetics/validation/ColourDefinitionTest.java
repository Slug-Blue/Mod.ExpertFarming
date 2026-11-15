package io.github.slugblue.wildroots.genetics.validation;

import static io.github.slugblue.wildroots.genetics.validation.DefinitionValidatorTest.has;
import static io.github.slugblue.wildroots.genetics.validation.DefinitionValidatorTest.speciesProblems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.testutil.DefJson;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** WR-701: {@code colours} of the species JSON are validated (docs/design/carrot-colours-climate.md §1.3). */
class ColourDefinitionTest {

    @SuppressWarnings("unchecked")
    static Map<String, Object> colours(Object json) {
        return (Map<String, Object>) ((Map<String, Object>) json).get("colours");
    }

    static Object carrot() {
        return MiniJson.parse(MiniJson.resourceText("/species/wild_carrot.json"));
    }

    @Test
    void shippedColoursAreValidAndParsed() {
        List<Problem> p = speciesProblems(carrot());
        assertFalse(DefinitionValidator.hasErrors(p), p::toString);
        SpeciesDef s = DefJson.species("/species/wild_carrot.json");
        assertEquals(5, s.colours().ids().size());
        assertEquals(5, s.colours().rules().size());
    }

    @Test
    void aSpeciesWithoutColoursIsValidAsBefore() {
        Object json = carrot();
        ((Map<String, Object>) json).remove("colours");
        List<Problem> p = speciesProblems(json);
        assertFalse(DefinitionValidator.hasErrors(p), p::toString);
        assertTrue(DefJson.species(json, new ArrayList<>()).colours().isNone());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownLocusInAConditionIsAnError() {
        Object json = carrot();
        List<Object> rules = (List<Object>) colours(json).get("rules");
        ((Map<String, Object>) ((Map<String, Object>) rules.get(1)).get("when")).put("color_zz", "domestic");
        List<Problem> p = speciesProblems(json);
        assertTrue(has(p, Problem.Severity.ERROR, "colours.rules[1].when.color_zz"), p::toString);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aQuantitativeTraitInAConditionIsAnError() {
        Object json = carrot();
        List<Object> rules = (List<Object>) colours(json).get("rules");
        ((Map<String, Object>) ((Map<String, Object>) rules.get(0)).get("when")).put("yield", "domestic");
        assertTrue(has(speciesProblems(json), Problem.Severity.ERROR, "colours.rules[0].when.yield"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void moreThanEightColoursAreAnError() {
        Object json = carrot();
        List<Object> ids = (List<Object>) colours(json).get("ids");
        for (int i = 0; i < 4; i++) {
            ids.add("extra" + i);
        }
        assertEquals(9, ids.size());
        List<Problem> p = speciesProblems(json);
        assertTrue(has(p, Problem.Severity.ERROR, "colours.ids"), p::toString);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRuleForAnUndeclaredColourAndADuplicateIdAreErrors() {
        Object json = carrot();
        List<Object> rules = (List<Object>) colours(json).get("rules");
        ((Map<String, Object>) rules.get(4)).put("id", "green");
        ((List<Object>) colours(json).get("ids")).set(1, "white");
        List<Problem> p = speciesProblems(json);
        assertTrue(has(p, Problem.Severity.ERROR, "colours.rules[4].id"), p::toString);
        assertTrue(has(p, Problem.Severity.ERROR, "colours.ids[1]"), p::toString);
    }

    @Test
    @SuppressWarnings("unchecked")
    void propertiesOfAnUnknownColourOrOutOfRangeAreErrors() {
        Object json = carrot();
        Map<String, Object> props = (Map<String, Object>) colours(json).get("properties");
        props.put("blue", Map.of("heat", 0.1));
        ((Map<String, Object>) props.get("purple")).put("heat", 7.0);
        ((Map<String, Object>) props.get("purple")).put("saturation_mult", -1.0);
        List<Problem> p = speciesProblems(json);
        assertTrue(has(p, Problem.Severity.ERROR, "colours.properties.blue"), p::toString);
        assertTrue(has(p, Problem.Severity.ERROR, "colours.properties.purple.heat"), p::toString);
        assertTrue(has(p, Problem.Severity.ERROR, "colours.properties.purple.saturation_mult"), p::toString);
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyDefaultOnTheSLocusIsAnError() {
        Object json = MiniJson.parse(MiniJson.resourceText("/species/wild_carrot_s6.json"));
        Map<String, Object> qual = (Map<String, Object>) ((Map<String, Object>) json).get("qualitative");
        for (Map.Entry<String, Object> e : qual.entrySet()) {
            Map<String, Object> q = (Map<String, Object>) e.getValue();
            if (Boolean.TRUE.equals(q.get("self_incompat"))) {
                q.put("legacy_default", "wild");
                List<Problem> p = speciesProblems(json);
                assertTrue(has(p, Problem.Severity.ERROR, "qualitative." + e.getKey() + ".legacy_default"), p::toString);
                return;
            }
        }
        throw new AssertionError("no S-locus in the fixture");
    }
}
