package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** WR-183: the five-step lens scale (narrative §9.2; GDD §10), boundaries in the upper step. */
class LensScaleTest {

    @Test
    void valueExactlyOnTheBoundaryGoesToTheUpperStep() {
        assertEquals(3, LensScale.step(0.35));
    }

    @ParameterizedTest
    @CsvSource({"0.0,1", "0.1999999,1", "0.20,2", "0.3499999,2", "0.35,3", "0.5499999,3", "0.55,4", "0.7499999,4",
        "0.75,5", "1.0,5", "-0.5,1", "NaN,1", "Infinity,5"})
    void steps(double p, int step) {
        assertEquals(step, LensScale.step(p));
    }

    @Test
    void displayTraitsChooseScaleOrName() {
        Map<String, String> carrot = Map.of("fruit_size", "root_size", "flavor", "sweetness");
        assertEquals("sweetness", LensScale.scale("flavor", carrot));
        assertEquals("size", LensScale.scale("fruit_size", carrot));
        assertEquals("tolerance", LensScale.scale("cold_tolerance", carrot));
        assertEquals("wildroots.tooltip.trait_name.fruit_size.wild_carrot", LensScale.nameKey("fruit_size", "wild_carrot", carrot));
        assertEquals("wildroots.tooltip.trait_name.flavor", LensScale.nameKey("flavor", "wild_carrot", carrot));
        assertEquals("wildroots.tooltip.scale.size.5", LensScale.wordKey("size", 9));
    }
}
