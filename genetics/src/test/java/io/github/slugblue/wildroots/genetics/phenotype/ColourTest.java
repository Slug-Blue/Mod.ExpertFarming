package io.github.slugblue.wildroots.genetics.phenotype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.ColourProps;
import io.github.slugblue.wildroots.genetics.api.ColourRules;
import io.github.slugblue.wildroots.genetics.api.Defaults;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.FatherPick;
import io.github.slugblue.wildroots.genetics.api.LotRecord;
import io.github.slugblue.wildroots.genetics.api.MutationRates;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.QualTrait;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import io.github.slugblue.wildroots.genetics.breeding.Offspring;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.random.Hash64;
import io.github.slugblue.wildroots.genetics.random.SplitMix64;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import io.github.slugblue.wildroots.genetics.testutil.ChiSquare;
import io.github.slugblue.wildroots.genetics.testutil.RandomGenomes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * WR-701 / WR-703: the colour phenotype of the carrot as data ({@code colours} of the species JSON,
 * docs/design/carrot-colours-climate.md §1.1, §2) and its modifiers of {@code E}. The properties file
 * is the one of the shipped line, so a change of a {@code [PLACEHOLDER]} number there is a change of
 * these tests' inputs only where they say so.
 */
class ColourTest {

    static final int WHITE = 0;
    static final int YELLOW = 1;
    static final int ORANGE = 2;
    static final int PURPLE = 3;
    static final int RED = 4;

    static final CompiledSpecies CS = new CompiledSpecies(Carrot.SPECIES);
    static final TraitId R = TraitId.of("color_r");
    static final Locus LP = Carrot.LAYOUT.section(Carrot.P);
    static final Locus LR = Carrot.LAYOUT.section(R);
    static final Locus LY1 = Carrot.LAYOUT.section(Carrot.Y1);
    static final Locus LY2 = Carrot.LAYOUT.section(Carrot.Y2);

    /** The four colour loci as allele sums 0, 1, 2 (copies of the domestic allele, 1 = the lower-case letter). */
    static Genome genome(int p, int y1, int y2, int r) {
        Genome.Builder b = Genome.builder(Carrot.SPECIES.id(), Carrot.LAYOUT);
        set(b, LP, p);
        set(b, LY1, y1);
        set(b, LY2, y2);
        set(b, LR, r);
        return b.build();
    }

    static void set(Genome.Builder b, Locus s, int copies) {
        b.set(s, 0, 0, copies >= 1 ? 1 : 0).set(s, 0, 1, copies >= 2 ? 1 : 0);
    }

    static Phenotype pheno(Genome g) {
        return PhenotypeCalculator.compute(CS, g, Environment.neutral(0.6), NoiseVector.zero(7));
    }

    /** The table of §1.1 written out as an oracle, from the letters: P_ purple; y1y1 rr red; y1y1 y2y2 R_ orange; y1y1 yellow; else white. */
    static int oracle(int p, int y1, int y2, int r) {
        boolean purple = p >= 1; // P is dominant: the domestic allele
        boolean y1y1 = y1 == 2; // recessive: needs both copies
        boolean y2y2 = y2 == 2;
        boolean rr = r == 2;
        if (purple) {
            return PURPLE;
        }
        if (y1y1 && rr) {
            return RED;
        }
        if (y1y1 && y2y2) {
            return ORANGE;
        }
        return y1y1 ? YELLOW : WHITE;
    }

    @Test
    void speciesHasTheFiveColoursInTheBlockOrder() {
        ColourRules c = Carrot.SPECIES.colours();
        assertEquals(List.of("white", "yellow", "orange", "purple", "red"), c.ids());
        assertEquals(5, c.rules().size());
        assertFalse(c.isNone());
        // GenomeLayout: color_r is a biallelic qualitative trait that is recessive in the wild sense
        QualTrait q = Carrot.SPECIES.qual(R);
        assertEquals(0.15, q.wildFreq());
        assertEquals(0, q.legacyAllele(), "legacy_default wild");
    }

    /** All 81 genotypes over the four loci (§1.1, WR-701 acceptance): the engine gives the colour of the table. */
    @Test
    void all81GenotypesGiveTheColourOfTheTable() {
        int n = 0;
        for (int p = 0; p < 3; p++) {
            for (int y1 = 0; y1 < 3; y1++) {
                for (int y2 = 0; y2 < 3; y2++) {
                    for (int r = 0; r < 3; r++) {
                        Phenotype ph = pheno(genome(p, y1, y2, r));
                        assertEquals(oracle(p, y1, y2, r), ph.colour(), "P" + p + " y1 " + y1 + " y2 " + y2 + " r " + r);
                        n++;
                    }
                }
            }
        }
        assertEquals(81, n);
    }

    /** The genotype tables of the doc: red hides behind purple, red replaces yellow and orange. */
    @Test
    void epistasisOfPurpleAndRedOverOrange() {
        assertEquals(PURPLE, pheno(genome(1, 2, 2, 2)).colour(), "Pp y1y1 y2y2 rr is purple");
        assertEquals(RED, pheno(genome(0, 2, 2, 2)).colour(), "red covers orange");
        assertEquals(RED, pheno(genome(0, 2, 0, 2)).colour(), "red covers yellow");
        assertEquals(ORANGE, pheno(genome(0, 2, 2, 1)).colour(), "Rr is not red");
        assertEquals(WHITE, pheno(genome(0, 1, 2, 2)).colour(), "Y1y1 is white even with rr");
    }

    /** Offspring colour of two parents without mutation: counts per colour after 10 000 crosses. */
    static long[] cross(Genome mother, Genome father, int children, long seed, int... order) {
        LotRecord lot = new LotRecord(mother, List.of(new FatherPick(father, 1, false)), 1.0);
        long[] counts = new long[order.length];
        for (int i = 0; i < children; i++) {
            Genome c = Offspring.of(lot, new MutationRates(0, 0), Hash64.of(seed, i));
            int colour = pheno(c).colour();
            for (int k = 0; k < order.length; k++) {
                if (order[k] == colour) {
                    counts[k]++;
                }
            }
        }
        return counts;
    }

    @Test
    void purpleIsDominant_threeToOne() {
        Genome pp = genome(1, 0, 0, 0);
        long[] c = cross(pp, pp, 10_000, 7011, PURPLE, WHITE);
        assertEquals(10_000, c[0] + c[1]);
        assertTrue(ChiSquare.pValue(c, new double[] {3, 1}) > 0.01, "3:1 " + c[0] + ":" + c[1]);
    }

    @Test
    void carrierWhitesGiveColouredOnesThreeToOne() {
        // Y1y1 × Y1y1 on pp, R_ : 3 white : 1 coloured (y2 and r do not segregate: Y2Y2, RR give yellow)
        Genome carrier = genome(0, 1, 0, 0);
        long[] c = cross(carrier, carrier, 10_000, 7012, WHITE, YELLOW);
        assertEquals(10_000, c[0] + c[1]);
        assertTrue(ChiSquare.pValue(c, new double[] {3, 1}) > 0.01, "3:1 " + c[0] + ":" + c[1]);
    }

    @Test
    void redIsARecessiveOnAColouredRoot_threeToOne() {
        // pp y1y1 Y2Y2 Rr selfed: 3 yellow : 1 red
        Genome g = genome(0, 2, 0, 1);
        long[] c = cross(g, g, 10_000, 7013, YELLOW, RED);
        assertEquals(10_000, c[0] + c[1]);
        assertTrue(ChiSquare.pValue(c, new double[] {3, 1}) > 0.01, "3:1 " + c[0] + ":" + c[1]);
    }

    @Test
    void selfOfAFourFoldHeterozygote_192_48_9_3_4() {
        Genome g = genome(1, 1, 1, 1); // Pp Y1y1 Y2y2 Rr
        long[] c = cross(g, g, 10_000, 7014, PURPLE, WHITE, YELLOW, ORANGE, RED);
        assertEquals(10_000, c[0] + c[1] + c[2] + c[3] + c[4]);
        double p = ChiSquare.pValue(c, new double[] {192, 48, 9, 3, 4});
        assertTrue(p > 0.01, "192:48:9:3:4 " + java.util.Arrays.toString(c) + " p " + p);
    }

    // ------------------------------------------------------------------ modifiers of E

    static SpeciesDef withColours(ColourRules colours) {
        SpeciesDef s = Carrot.SPECIES;
        return new SpeciesDef(s.id(), s.tempMin(), s.tempMax(), s.quantitative(), s.tradeoffs(), s.budget(),
                s.wild(), s.wildF(), s.qualitative(), s.starterFreq(), s.load(), s.mutation(), s.pollination(),
                s.timing(), s.qualityAxes(), colours);
    }

    /** The colour rules of the shipped species with every modifier neutral. */
    static ColourRules neutral() {
        ColourRules c = Carrot.SPECIES.colours();
        Map<String, ColourProps> zero = new java.util.TreeMap<>();
        for (String id : c.ids()) {
            zero.put(id, ColourProps.ZERO);
        }
        return new ColourRules(c.ids(), c.rules(), zero);
    }

    /** E of the carrot before WR-703 (GDD §3.5), the formula copied from {@code PhenotypeCalculator} at 77deb86. */
    static double oldE(SpeciesDef def, Environment env, double cold, double heat, double drought) {
        double lo = def.tempMin() - Defaults.TOLERANCE_WIDENING * cold;
        double hi = def.tempMax() + Defaults.TOLERANCE_WIDENING * heat;
        double t = env.temperature();
        double out = t < lo ? lo - t : t > hi ? t - hi : 0;
        double e = -Math.min(Defaults.ENV_TEMP_CAP, Defaults.ENV_TEMP_SLOPE * out);
        if (!env.moisturized()) {
            e -= Defaults.ENV_DRY * (1 - drought);
        }
        if (env.light() < Defaults.ENV_DARK_LIGHT) {
            e -= Defaults.ENV_DARK;
        }
        e += Math.max(0, Math.min(Defaults.ENV_COMPOST_CAP, env.compostBonus()));
        return Double.isFinite(e) ? e : 0;
    }

    static void assertBitIdentical(Phenotype a, Phenotype b, String what) {
        for (TraitId t : a.traits()) {
            assertEquals(Double.doubleToLongBits(a.p(t)), Double.doubleToLongBits(b.p(t)), what + " P(" + t + ")");
            assertEquals(Double.doubleToLongBits(a.gEff(t)), Double.doubleToLongBits(b.gEff(t)), what + " G_eff " + t);
        }
        assertEquals(Double.doubleToLongBits(a.vigor()), Double.doubleToLongBits(b.vigor()), what + " V");
    }

    static Environment randomEnv(SplitMix64 rng, double tLo, double tHi, boolean anyMoisture) {
        return new Environment(tLo + (tHi - tLo) * rng.nextDouble(), !anyMoisture || rng.nextBoolean(),
                rng.nextInt(16), rng.nextBoolean() ? 0 : rng.nextDouble() * 0.1);
    }

    /**
     * WR-703 acceptance, the gate property: with every colour modifier at zero, {@code P} and {@code E} are
     * bit for bit those of the colourless species (and of the formula before the change) on 5000 random
     * genomes and cells, in the whole temperature range.
     */
    @Test
    void zeroModifiersGiveBitIdenticalPAndE() {
        CompiledSpecies none = new CompiledSpecies(withColours(ColourRules.NONE));
        CompiledSpecies zero = new CompiledSpecies(withColours(neutral()));
        SplitMix64 rng = new SplitMix64(703);
        for (int i = 0; i < 5000; i++) {
            Genome g = RandomGenomes.carrot(rng);
            NoiseVector noise = PhenotypeCalculator.rollNoise(CS, rng.nextLong());
            Environment env = randomEnv(rng, -1.0, 2.2, true);
            Phenotype a = PhenotypeCalculator.compute(none, g, env, noise);
            Phenotype b = PhenotypeCalculator.compute(zero, g, env, noise);
            assertBitIdentical(a, b, "genome " + i);
            // and the formula of M1a, written out
            double e = oldE(Carrot.SPECIES, env, a.p(Carrot.COLD), a.p(Carrot.HEAT), a.p(Carrot.DROUGHT));
            for (TraitId t : List.of(Carrot.YIELD, Carrot.FRUIT, Carrot.GROWTH)) {
                double expected = Math.max(0, Math.min(1, a.gEff(t) * a.vigor() + e + noise.eps(a.traits().indexOf(t))));
                assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(a.p(t)), "old E " + t + " #" + i);
            }
        }
    }

    /**
     * The shipped modifiers do not touch a moist cell in the gate range 0.5-0.8 (the window [0.2, 1.0] with the
     * smallest shift is wider than that): P and E are still those of the colourless species there.
     */
    @Test
    void shippedModifiersLeaveTheGateRangeOnMoistLandAlone() {
        CompiledSpecies none = new CompiledSpecies(withColours(ColourRules.NONE));
        SplitMix64 rng = new SplitMix64(7031);
        int coloured = 0;
        for (int i = 0; i < 5000; i++) {
            Genome g = RandomGenomes.carrot(rng);
            NoiseVector noise = PhenotypeCalculator.rollNoise(CS, rng.nextLong());
            Environment env = randomEnv(rng, 0.5, 0.8, false);
            Phenotype a = PhenotypeCalculator.compute(none, g, env, noise);
            Phenotype b = PhenotypeCalculator.compute(CS, g, env, noise);
            assertBitIdentical(a, b, "genome " + i);
            coloured += b.colour() > 0 ? 1 : 0;
        }
        assertTrue(coloured > 1000, "the sample must contain coloured plants: " + coloured);
    }

    /** Yield alleles all 2, everything else 0: P_yield is 1 + E, every tolerance 0 (window [0.2, 1.0]). */
    static Genome yieldOnly() {
        Genome.Builder b = Genome.builder(Carrot.SPECIES.id(), Carrot.LAYOUT);
        Locus y = Carrot.LAYOUT.section(Carrot.YIELD);
        for (int l = 0; l < y.loci(); l++) {
            b.set(y, l, 0, 2).set(y, l, 1, 2);
        }
        return b.build();
    }

    static Phenotype inCell(Genome colourLoci, Environment env) {
        return PhenotypeCalculator.compute(CS, withLoci(yieldOnly(), colourLoci), env, NoiseVector.zero(7));
    }

    @Test
    void colourShiftsTheWindowAndTheDroughtTerm() {
        Environment hot = Environment.neutral(1.2);
        Phenotype purple = inCell(genome(1, 0, 0, 0), hot);
        Phenotype white = inCell(genome(0, 0, 0, 0), hot);
        assertEquals(PURPLE, purple.colour());
        assertEquals(WHITE, white.colour());
        // purple: upper limit 1.0 + 0.25 = 1.25, so 1.2 is inside; white is 0.2 over its limit of 1.0
        assertEquals(1.0, purple.p(Carrot.YIELD), 1e-9);
        assertEquals(0.8, white.p(Carrot.YIELD), 1e-9);
        // orange is the most heat-sensitive: limit 0.95, 0.25 over
        assertEquals(0.75, inCell(genome(0, 2, 2, 0), hot).p(Carrot.YIELD), 1e-9);

        Environment cold = Environment.neutral(0.1);
        // white widens the window down by 0.05 (lower limit 0.15): 0.05 under; purple narrows it (0.30): 0.20 under
        assertEquals(0.95, inCell(genome(0, 0, 0, 0), cold).p(Carrot.YIELD), 1e-9);
        assertEquals(0.80, inCell(genome(1, 0, 0, 0), cold).p(Carrot.YIELD), 1e-9);

        // dry farmland: yellow adds 0.30 to P_drought, purple 0.15, white nothing
        Environment dry = new Environment(0.6, false, 15, 0);
        assertEquals(1 - 0.05, inCell(genome(0, 0, 0, 0), dry).p(Carrot.YIELD), 1e-9);
        assertEquals(1 - 0.05 * 0.70, inCell(genome(0, 2, 0, 0), dry).p(Carrot.YIELD), 1e-9);
        assertEquals(1 - 0.05 * 0.85, inCell(genome(1, 0, 0, 0), dry).p(Carrot.YIELD), 1e-9);
    }

    /** {@code base} with the four colour loci of {@code colours}. */
    static Genome withLoci(Genome base, Genome colours) {
        Genome.Builder b = Genome.builder(Carrot.SPECIES.id(), Carrot.LAYOUT);
        for (Locus s : Carrot.LAYOUT.sections()) {
            Genome from = s == LP || s == LR || s == LY1 || s == LY2 ? colours : base;
            for (int l = 0; l < s.loci(); l++) {
                b.set(s, l, 0, from.allele(s, l, 0)).set(s, l, 1, from.allele(s, l, 1));
            }
        }
        return b.build();
    }

    @Test
    void purpleHasTheSaturationBonusAndOnlyPurple() {
        ColourRules c = Carrot.SPECIES.colours();
        List<String> withBonus = new ArrayList<>();
        for (String id : c.ids()) {
            if (c.props(c.indexOf(id)).saturationMult() != 1) {
                withBonus.add(id);
            }
        }
        assertEquals(List.of("purple"), withBonus);
        assertEquals(1.15, c.props(PURPLE).saturationMult());
    }

    @Test
    void aSpeciesWithoutColoursHasNoColourAndZeroProperties() {
        Phenotype ph = PhenotypeCalculator.compute(new CompiledSpecies(withColours(ColourRules.NONE)),
                genome(1, 2, 2, 2), Environment.neutral(0.6), NoiseVector.zero(7));
        assertEquals(Phenotype.NO_COLOUR, ph.colour());
        assertEquals(ColourProps.ZERO, ph.colourProps());
    }
}
