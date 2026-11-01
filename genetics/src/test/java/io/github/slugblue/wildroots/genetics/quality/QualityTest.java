package io.github.slugblue.wildroots.genetics.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** WR-109: harvest grades and processing (GDD §6.4, §6.6; economy §2, §4). */
class QualityTest {

    static Phenotype pheno(double fruit, double flavor) {
        List<TraitId> t = List.of(Carrot.FLAVOR, Carrot.FRUIT);
        double[] p = {flavor, fruit};
        return new Phenotype(t, p, p, p, 1, List.of(), new boolean[0], false);
    }

    @Test
    void qualityIndexIsWeightedSumOverCultivarAxes() {
        assertEquals(0.5 * 0.8 + 0.5 * 0.4, QualityIndex.of(pheno(0.8, 0.4), Carrot.CARROT.qualityAxes()), 1e-12);
        assertEquals(0.8 * 0.9 + 0.2 * 0.1, QualityIndex.of(pheno(0.9, 0.1),
                Map.of(Carrot.FRUIT, 0.8, Carrot.FLAVOR, 0.2)), 1e-12);
        // weights are normalised (validator warns about the sum separately)
        assertEquals(0.6, QualityIndex.of(pheno(0.6, 0.6), Map.of(Carrot.FRUIT, 2.0, Carrot.FLAVOR, 2.0)), 1e-12);
        assertEquals(0.0, QualityIndex.of(pheno(0.6, 0.6), Map.of()));
    }

    @ParameterizedTest(name = "Q={0} bitter={1} → {2}")
    @CsvSource({
        "0.00, false, 1", "0.3499, false, 1", "0.35, false, 2", "0.5499, false, 2", "0.55, false, 3",
        "0.7499, false, 3", "0.75, false, 4", "1.00, false, 4",
        "0.20, true, 1", "0.50, true, 2", "0.70, true, 2", "0.95, true, 2", "NaN, false, 1"})
    void gradeBoundsAndBitterCap(double q, boolean bitter, int grade) {
        assertEquals(grade, Grade.of(q, bitter));
    }

    /**
     * GDD §6.6 formula, one row per case. Inputs: "grade×count@tier" with
     * tier w(ild) / l(andrace) / c(ultivar), "b" for bitter; boosters count.
     */
    @ParameterizedTest(name = "{0} + {1} boosters → {2}")
    @CsvSource(delimiter = '|', value = {
        // bread from wild grain: grade 1, cap 1
        "1x3@w | 0 | 1",
        // wild grain with honey: one step above the cap
        "1x3@w | 1 | 2",
        // spelt (landrace, cap 3) of grade 3
        "3x3@l | 0 | 3",
        // spelt of grade 4 is still capped at 3
        "4x3@l | 0 | 3",
        // select wheat with honey: 4 + 1 stays 4
        "4x3@c | 1 | 4",
        // cultivar grade 3 with honey → 4
        "3x3@c | 1 | 4",
        // mixed grades: floor of the mean, one select root does not lift the pot
        "4x1@c,2x3@c | 0 | 2",
        "4x2@c,3x1@c | 0 | 3",
        // bitter input counts as 1
        "2x1@c:b,4x1@c | 0 | 2",
        // the lowest tier sets the cap
        "4x2@c,4x1@w | 0 | 1",
        "4x2@c,4x1@w | 1 | 2",
        "4x2@c,4x1@w | 3 | 2",
        // two boosters below the cap
        "1x2@c | 2 | 3",
        // landrace grade 2 with two boosters: 2 + 2 = 4 = cap + 1
        "2x2@l | 2 | 4",
        "3x2@l | 2 | 4",
        // no main ingredient: neutral 3
        " | 0 | 3"})
    void processingFormula(String mains, int boosters, int expected) {
        List<GradedInput> in = new ArrayList<>();
        if (mains != null) {
            for (String part : mains.split(",")) {
                String[] gc = part.trim().split("@");
                String[] g = gc[0].split("x");
                String tier = gc[1].split(":")[0];
                in.add(new GradedInput(Integer.parseInt(g[0]), switch (tier) {
                    case "w" -> 0;
                    case "l" -> 1;
                    default -> 2;
                }, Integer.parseInt(g[1]), gc[1].endsWith(":b")));
            }
        }
        List<Booster> b = new ArrayList<>();
        for (int i = 0; i < boosters; i++) {
            b.add(new Booster(true));
        }
        b.add(new Booster(false));
        assertEquals(expected, ProcessingQuality.process(in, b).grade());
    }

    /** GDD §6.6 table of technical products: output count by grade 1 / 2 / 3 / 4. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "paper            | 1,2,3,3",
        "sugar            | 0,1,1,2",
        "brown dye        | 1,1,1,1"})
    void technicalProductOutputs(String product, String byGrade) {
        int[] table = java.util.Arrays.stream(byGrade.split(",")).mapToInt(Integer::parseInt).toArray();
        for (int grade = 1; grade <= 4; grade++) {
            ProcessingResult r = ProcessingQuality.process(List.of(new GradedInput(grade, 2, 1, false)), List.of());
            assertEquals(grade, r.grade());
            assertEquals(table[grade - 1], r.output(table), product + " grade " + grade);
        }
    }

    @Test
    void foodExampleOfGddBread() {
        // GDD §6.6: bread from wild_grain = grade 1; from spelt (cap 3) up to 3; select wheat with honey 4
        assertEquals(1, ProcessingQuality.process(List.of(new GradedInput(2, 0, 3, false)), List.of()).grade());
        assertEquals(3, ProcessingQuality.process(List.of(new GradedInput(4, 1, 3, false)), List.of()).grade());
        assertEquals(4, ProcessingQuality.process(List.of(new GradedInput(4, 2, 3, false)),
                List.of(new Booster(true))).grade());
    }
}
