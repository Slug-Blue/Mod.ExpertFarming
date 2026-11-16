package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.data.CarrotLineDataTestAccess;
import io.github.slugblue.wildroots.genetics.api.ColourProps;
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
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import io.github.slugblue.wildroots.plant.GrowthLogic;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * WR-703: the colour modifiers (heat, cold, water) reach the lens flags ({@link EnvModifiers}) and the growth
 * chance on dry farmland ({@link GrowthLogic#chance}), and with neutral modifiers nothing moves. The numbers are
 * the {@code [PLACEHOLDER]} values of the shipped species JSON; the tests read them from it.
 */
class ColourClimateTest {

    static final SpeciesDef CARROT = CarrotLineDataTestAccess.wildCarrot();
    static final GeneticsService G = CarrotLineDataTestAccess.snapshot().genetics();
    static final GenomeLayout LAYOUT = G.layout(CARROT).orElseThrow();
    static final int WHITE = 0;
    static final int YELLOW = 1;
    static final int ORANGE = 2;
    static final int PURPLE = 3;
    static final int RED = 4;

    /** Zero tolerances (window [0.2, 1.0]), the colour loci as given: copies of the domestic allele. */
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

    static final Map<Integer, int[]> OF = Map.of(WHITE, new int[] {0, 0, 0, 0}, YELLOW, new int[] {0, 2, 0, 0},
            ORANGE, new int[] {0, 2, 2, 0}, PURPLE, new int[] {1, 0, 0, 0}, RED, new int[] {0, 2, 0, 2});

    static Phenotype pheno(SpeciesDef def, int colour, Environment env) {
        int[] c = OF.get(colour);
        return G.phenotype(genome(c[0], c[1], c[2], c[3]), def, env, NoiseVector.zero(7)).orElseThrow();
    }

    static int flags(int colour, Environment env) {
        Phenotype p = pheno(CARROT, colour, env);
        assertEquals(colour, p.colour());
        return EnvModifiers.of(CARROT, env, p);
    }

    @Test
    void heatLimitMovesWithTheColour() {
        ColourRules c = CARROT.colours();
        Environment hot = Environment.neutral(1.2);
        // the window is [0.2, 1.0]; every tolerance is 0, so the limit is 1.0 + the colour's heat
        assertTrue((flags(WHITE, hot) & EnvModifiers.HOT) != 0, "white: limit 1.0");
        assertTrue((flags(ORANGE, hot) & EnvModifiers.HOT) != 0, "orange: limit " + (1.0 + c.props(ORANGE).heat()));
        assertFalse((flags(PURPLE, hot) & EnvModifiers.HOT) != 0, "purple: limit " + (1.0 + c.props(PURPLE).heat()));
        assertFalse((flags(YELLOW, Environment.neutral(1.1)) & EnvModifiers.HOT) != 0, "yellow stands 1.1");
        assertTrue((flags(WHITE, Environment.neutral(1.1)) & EnvModifiers.HOT) != 0, "white does not stand 1.1");
        // the order the owner asked for: purple stands the heat best, then yellow, then red, white, orange
        assertTrue(c.props(PURPLE).heat() > c.props(YELLOW).heat() && c.props(YELLOW).heat() > c.props(RED).heat()
                && c.props(RED).heat() > c.props(WHITE).heat() && c.props(WHITE).heat() > c.props(ORANGE).heat());
    }

    @Test
    void coldLimitMovesWithTheColour() {
        Environment cold = Environment.neutral(0.25);
        // lower limit 0.2 - cold property: purple 0.30, white 0.15
        assertTrue((flags(PURPLE, cold) & EnvModifiers.COLD) != 0, "purple does not stand 0.25");
        assertFalse((flags(WHITE, Environment.neutral(0.16)) & EnvModifiers.COLD) != 0, "white stands 0.16");
        assertTrue((flags(ORANGE, Environment.neutral(0.19)) & EnvModifiers.COLD) != 0, "orange: limit 0.2");
        assertFalse((flags(ORANGE, cold) & EnvModifiers.COLD) != 0);
    }

    @Test
    void waterBonusLeavesLessThirstOnDryFarmland() {
        ColourRules c = CARROT.colours();
        Environment dry = new Environment(0.6, false, 15, 0);
        // P_drought 0 plus 0.30 / 0.15 stays below 1: still flagged dry, but the growth chance differs
        assertTrue((flags(YELLOW, dry) & EnvModifiers.DRY) != 0);
        double white = GrowthLogic.chance(CARROT, pheno(CARROT, WHITE, dry), dry, 1, 0);
        double yellow = GrowthLogic.chance(CARROT, pheno(CARROT, YELLOW, dry), dry, 1, 0);
        double purple = GrowthLogic.chance(CARROT, pheno(CARROT, PURPLE, dry), dry, 1, 0);
        assertTrue(yellow > purple && purple > white, "yellow " + yellow + " purple " + purple + " white " + white);
        // dry = 1 - 0.5 (1 - P), P = 0 + bonus: the chance is in the ratio of the two
        double ratio = (1 - 0.5 * (1 - c.props(YELLOW).drought())) / 0.5;
        assertEquals(ratio, yellow / white, 1e-6);
        // on moist farmland the colours differ in nothing
        Environment wet = Environment.neutral(0.6);
        assertEquals(GrowthLogic.chance(CARROT, pheno(CARROT, WHITE, wet), wet, 1, 0),
                GrowthLogic.chance(CARROT, pheno(CARROT, YELLOW, wet), wet, 1, 0), 0);
        // a drought-hardy plant is capped at 1: a bonus cannot push the dry factor above it
        Genome.Builder b = genome(0, 2, 0, 0).toBuilder();
        Locus d = LAYOUT.section(TraitId.of("drought_tolerance"));
        for (int l = 0; l < d.loci(); l++) {
            b.set(d, l, 0, 2).set(d, l, 1, 2);
        }
        Phenotype hardy = G.phenotype(b.build(), CARROT, dry, NoiseVector.zero(7)).orElseThrow();
        assertEquals(1.0, hardy.p(TraitId.of("drought_tolerance")), 1e-9);
        assertEquals(0, EnvModifiers.of(CARROT, dry, hardy) & EnvModifiers.DRY, "P 1 + bonus is still 1: no dry flag");
        assertEquals(GrowthLogic.chance(CARROT, hardy, Environment.neutral(0.6), 1, 0),
                GrowthLogic.chance(CARROT, hardy, dry, 1, 0), 1e-12);
    }

    /** Every colour with every modifier at zero: the flags and the growth chance are those of a colourless species. */
    @Test
    void neutralModifiersChangeNothing() {
        ColourRules c = CARROT.colours();
        Map<String, ColourProps> zero = new TreeMap<>();
        c.ids().forEach(id -> zero.put(id, ColourProps.ZERO));
        SpeciesDef neutral = withColours(new ColourRules(c.ids(), c.rules(), zero));
        SpeciesDef none = withColours(ColourRules.NONE);
        SplitMix64 rng = new SplitMix64(7030);
        for (int i = 0; i < 3000; i++) {
            Genome g = RandomGenomes.uniform(CARROT.id(), LAYOUT, rng);
            Environment env = new Environment(-1 + 3.2 * rng.nextDouble(), rng.nextBoolean(), rng.nextInt(16),
                    rng.nextBoolean() ? 0 : 0.03);
            NoiseVector noise = G.rollNoise(CARROT, rng.nextLong()).orElseThrow();
            Phenotype a = G.phenotype(g, none, env, noise).orElseThrow();
            Phenotype b = G.phenotype(g, neutral, env, noise).orElseThrow();
            assertEquals(EnvModifiers.of(none, env, a), EnvModifiers.of(neutral, env, b), "flags " + i);
            int stage = 1 + rng.nextInt(4);
            assertEquals(Double.doubleToLongBits(GrowthLogic.chance(none, a, env, stage, 0)),
                    Double.doubleToLongBits(GrowthLogic.chance(neutral, b, env, stage, 0)), "chance " + i);
        }
    }

    static SpeciesDef withColours(ColourRules colours) {
        SpeciesDef s = CARROT;
        return new SpeciesDef(s.id(), s.tempMin(), s.tempMax(), s.quantitative(), s.tradeoffs(), s.budget(), s.wild(),
                s.wildF(), s.qualitative(), s.starterFreq(), s.load(), s.mutation(), s.pollination(), s.timing(),
                s.qualityAxes(), colours);
    }
}
