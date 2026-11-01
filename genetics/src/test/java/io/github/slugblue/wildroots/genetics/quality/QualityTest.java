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

    /** {@code output_by_grade} of the GDD §6.6 technical products, as their recipe JSON will carry it. */
    static final int[] PAPER = {1, 2, 3, 3};
    static final int[] SUGAR = {0, 1, 1, 2};
    static final int[] BROWN_DYE = {1, 1, 1, 1};

    static int craft(int[] outputByGrade, int boosters, GradedInput... mains) {
        List<Booster> b = new ArrayList<>();
        for (int i = 0; i < boosters; i++) {
            b.add(new Booster(true));
        }
        return ProcessingQuality.process(List.of(mains), b).output(outputByGrade);
    }

    /**
     * GDD §6.6 technical products from real ingredient sets: the grade comes
     * from the formula (mean, tier cap, bitterness) and selects the output.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        // paper: 3 cane stalks of any tier; grade 1 → 1 «coarse» paper
        "paper, 3 wild stalks grade 1            | paper | 1x3@w       | 1",
        "paper, 3 wild stalks grade 4 (cap 1)    | paper | 4x3@w       | 1",
        "paper, landrace 3 3 3                   | paper | 3x3@l       | 3",
        "paper, cultivar 4 4 2 → floor 3.33 = 3  | paper | 4x2@c,2x1@c | 3",
        "paper, cultivar 4 4 4                   | paper | 4x3@c       | 3",
        "paper, cultivar 2 2 2                   | paper | 2x3@c       | 2",
        // sugar: landrace stalk or better; grade 1 gives nothing
        "sugar, wild beet grade 4 (cap 1)        | sugar | 4x1@w       | 0",
        "sugar, landrace grade 4 (cap 3)         | sugar | 4x1@l       | 1",
        "sugar, cultivar grade 4                 | sugar | 4x1@c       | 2",
        "sugar, bitter cultivar input            | sugar | 4x1@c:b     | 0",
        "sugar, cultivar grade 2                 | sugar | 2x1@c       | 1",
        // brown dye: grade does not matter
        "dye, wild grade 1                       | dye   | 1x1@w       | 1",
        "dye, cultivar grade 4                   | dye   | 4x1@c       | 1"})
    void technicalProductOutputs(String name, String product, String mains, int expected) {
        int[] table = switch (product) {
            case "paper" -> PAPER;
            case "sugar" -> SUGAR;
            default -> BROWN_DYE;
        };
        List<GradedInput> in = new ArrayList<>();
        for (String part : mains.split(",")) {
            String[] gc = part.trim().split("@");
            String[] g = gc[0].split("x");
            String tier = gc[1].split(":")[0];
            in.add(new GradedInput(Integer.parseInt(g[0]), tier.equals("w") ? 0 : tier.equals("l") ? 1 : 2,
                    Integer.parseInt(g[1]), gc[1].endsWith(":b")));
        }
        assertEquals(expected, craft(table, 0, in.toArray(new GradedInput[0])), name);
    }

    @Test
    void outputTableIsReadSafely() {
        ProcessingResult r = ProcessingQuality.process(List.of(new GradedInput(4, 2, 1, false)), List.of());
        assertEquals(0, r.output(null));
        assertEquals(0, r.output(new int[] {1, 2}));
        assertEquals(0, r.output(new int[] {1, 2, 3, -5}), "negative outputs clamp to 0");
    }

    @Test
    void atMostEightBoosterSlotsCount() {
        List<Booster> many = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            many.add(new Booster(false));
        }
        many.add(new Booster(true)); // ninth slot: ignored (SEC-28 MAX_BOOSTERS)
        assertEquals(2, ProcessingQuality.process(List.of(new GradedInput(2, 2, 1, false)), many).grade());
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
