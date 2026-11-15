package io.github.slugblue.wildroots.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.slugblue.wildroots.data.CarrotLineDataTestAccess;
import io.github.slugblue.wildroots.genetics.api.ColourRules;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.GeneticsService;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.GenomeLayout;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.phenotype.NoiseVector;
import org.junit.jupiter.api.Test;

/**
 * WR-702: the block's {@code variant} is the colour of the species' rules ({@code Phenotype#colour}); a species
 * without colour rules keeps the M1a rule, and for every genotype that is not red the two agree (so a plant
 * saved before WR-701 shows the same colour).
 */
class GrowthVariantTest {

    static final SpeciesDef CARROT = CarrotLineDataTestAccess.wildCarrot();
    static final GeneticsService G = CarrotLineDataTestAccess.snapshot().genetics();
    static final GenomeLayout LAYOUT = G.layout(CARROT).orElseThrow();
    /** The same species with no colour rules: the code of M1a decides the variant. */
    static final SpeciesDef COLOURLESS = new SpeciesDef(CARROT.id(), CARROT.tempMin(), CARROT.tempMax(),
            CARROT.quantitative(), CARROT.tradeoffs(), CARROT.budget(), CARROT.wild(), CARROT.wildF(),
            CARROT.qualitative(), CARROT.starterFreq(), CARROT.load(), CARROT.mutation(), CARROT.pollination(),
            CARROT.timing(), CARROT.qualityAxes(), ColourRules.NONE);

    static Genome genome(int p, int y1, int y2, int r) {
        Genome.Builder b = Genome.builder(CARROT.id(), LAYOUT);
        int[] copies = {p, y1, y2, r};
        String[] traits = {"color_p", "color_y1", "color_y2", "color_r"};
        for (int i = 0; i < 4; i++) {
            Locus s = LAYOUT.section(TraitId.of(traits[i]));
            b.set(s, 0, 0, copies[i] >= 1 ? 1 : 0).set(s, 0, 1, copies[i] >= 2 ? 1 : 0);
        }
        return b.build();
    }

    static Phenotype pheno(SpeciesDef def, Genome g) {
        return G.phenotype(g, def, Environment.neutral(0.6), NoiseVector.zero(7)).orElseThrow();
    }

    /** P_ purple 3; y1y1 rr red 4; y1y1 y2y2 orange 2; y1y1 yellow 1; else white 0 (docs/design/carrot-colours-climate.md 1.1). */
    static int oracle(int p, int y1, int y2, int r) {
        if (p >= 1) {
            return 3;
        }
        if (y1 == 2 && r == 2) {
            return 4;
        }
        return y1 == 2 ? (y2 == 2 ? 2 : 1) : 0;
    }

    @Test
    void variantIsTheColourOfTheRulesForAll81Genotypes() {
        int n = 0;
        for (int p = 0; p < 3; p++) {
            for (int y1 = 0; y1 < 3; y1++) {
                for (int y2 = 0; y2 < 3; y2++) {
                    for (int r = 0; r < 3; r++) {
                        int v = GrowthLogic.variant(CARROT, pheno(CARROT, genome(p, y1, y2, r)));
                        assertEquals(oracle(p, y1, y2, r), v, "P" + p + " y1 " + y1 + " y2 " + y2 + " r " + r);
                        assertEquals(true, v >= 0 && v <= FieldPlantBlock.MAX_VARIANT);
                        n++;
                    }
                }
            }
        }
        assertEquals(81, n);
    }

    /** What WR-701 promises old saves: every genotype that is not red has the variant the M1a code gave it. */
    @Test
    void theRulesAgreeWithTheM1aCodeExceptForRed() {
        for (int p = 0; p < 3; p++) {
            for (int y1 = 0; y1 < 3; y1++) {
                for (int y2 = 0; y2 < 3; y2++) {
                    for (int r = 0; r < 3; r++) {
                        Genome g = genome(p, y1, y2, r);
                        int rules = GrowthLogic.variant(CARROT, pheno(CARROT, g));
                        int old = GrowthLogic.variant(COLOURLESS, pheno(COLOURLESS, g));
                        boolean red = rules == 4;
                        assertEquals(red ? old >= 1 && old <= 2 : true, true, "red covers yellow or orange only");
                        if (!red) {
                            assertEquals(old, rules, "P" + p + " y1 " + y1 + " y2 " + y2 + " r " + r);
                        }
                    }
                }
            }
        }
    }

    @Test
    void aSpeciesWithoutColourRulesWorksAsBefore() {
        assertEquals(3, GrowthLogic.variant(COLOURLESS, pheno(COLOURLESS, genome(1, 2, 2, 2))));
        assertEquals(2, GrowthLogic.variant(COLOURLESS, pheno(COLOURLESS, genome(0, 2, 2, 2))));
        assertEquals(1, GrowthLogic.variant(COLOURLESS, pheno(COLOURLESS, genome(0, 2, 0, 2))));
        assertEquals(0, GrowthLogic.variant(COLOURLESS, pheno(COLOURLESS, genome(0, 1, 2, 2))));
    }
}
