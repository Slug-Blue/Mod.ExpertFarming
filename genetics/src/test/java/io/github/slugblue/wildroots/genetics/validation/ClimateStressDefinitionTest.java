package io.github.slugblue.wildroots.genetics.validation;

import static io.github.slugblue.wildroots.genetics.validation.DefinitionValidatorTest.has;
import static io.github.slugblue.wildroots.genetics.validation.DefinitionValidatorTest.speciesProblems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.ClimateStress;
import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.testutil.DefJson;
import io.github.slugblue.wildroots.genetics.testutil.MiniJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** WR-710: {@code climate_stress} of the species JSON is parsed with defaults and validated. */
class ClimateStressDefinitionTest {

    @SuppressWarnings("unchecked")
    static Map<String, Object> carrot() {
        return (Map<String, Object>) MiniJson.parse(MiniJson.resourceText("/species/wild_carrot.json"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> stress(Map<String, Object> json) {
        return (Map<String, Object>) json.get("climate_stress");
    }

    @Test
    void shippedNumbersAreValid() {
        List<Problem> p = speciesProblems(carrot());
        assertFalse(DefinitionValidator.hasErrors(p), p::toString);
    }

    @Test
    void aSpeciesWithoutTheBlockNeverWilts() {
        Map<String, Object> json = carrot();
        json.remove("climate_stress");
        List<Problem> p = speciesProblems(json);
        assertFalse(DefinitionValidator.hasErrors(p), p::toString);
        assertEquals(ClimateStress.NONE, DefJson.species(json, new ArrayList<>()).climateStress());
    }

    @Test
    void missingNumbersTakeTheDefaultsWithoutWarning() {
        Map<String, Object> json = carrot();
        json.put("climate_stress", new java.util.LinkedHashMap<String, Object>());
        List<Problem> p = speciesProblems(json);
        assertFalse(DefinitionValidator.hasErrors(p), p::toString);
        assertTrue(p.stream().noneMatch(x -> x.path().startsWith("climate_stress")), p::toString);
        assertEquals(ClimateStress.defaults(0), DefJson.species(json, new ArrayList<>()).climateStress());
        assertEquals(Defaults.WILT_MARGIN, ClimateStress.defaults(0).wiltMargin());
    }

    @Test
    void numbersOutOfBoundsAreErrors() {
        for (String[] bad : new String[][] {{"wilt_margin", "-0.1"}, {"wilt_margin", "3"}, {"humid_penalty", "2"},
                {"buffer_shelter", "0.95"}, {"buffer_irrigation", "-0.5"}, {"grace_ticks", "65"},
                {"grace_ticks", "-1"}, {"shelter_sky_light", "16"}}) {
            Map<String, Object> json = carrot();
            stress(json).put(bad[0], Double.parseDouble(bad[1]));
            List<Problem> p = speciesProblems(json);
            assertTrue(has(p, Problem.Severity.ERROR, "climate_stress." + bad[0]), bad[0] + "=" + bad[1] + " " + p);
        }
    }

    @Test
    void buffersTogetherStayBelowOne() {
        Map<String, Object> json = carrot();
        stress(json).put("buffer_shelter", 0.6);
        stress(json).put("buffer_irrigation", 0.5);
        assertTrue(has(speciesProblems(json), Problem.Severity.ERROR, "climate_stress.buffer_shelter"));
    }
}
