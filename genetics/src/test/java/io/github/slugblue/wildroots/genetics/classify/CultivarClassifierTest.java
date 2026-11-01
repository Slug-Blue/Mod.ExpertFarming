package io.github.slugblue.wildroots.genetics.classify;

import static io.github.slugblue.wildroots.genetics.testutil.Carrot.BITTER;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.FLAVOR;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.FRUIT;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.P;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.Y1;
import static io.github.slugblue.wildroots.genetics.testutil.Carrot.Y2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.genetics.api.Classification;
import io.github.slugblue.wildroots.genetics.api.CultivarDef;
import io.github.slugblue.wildroots.genetics.api.CultivarId;
import io.github.slugblue.wildroots.genetics.api.QualReq;
import io.github.slugblue.wildroots.genetics.api.Range;
import io.github.slugblue.wildroots.genetics.breeding.Cloning;
import io.github.slugblue.wildroots.genetics.model.Genome;
import io.github.slugblue.wildroots.genetics.model.Locus;
import io.github.slugblue.wildroots.genetics.model.TraitId;
import io.github.slugblue.wildroots.genetics.phenotype.CompiledSpecies;
import io.github.slugblue.wildroots.genetics.phenotype.PhenotypeCalculator;
import io.github.slugblue.wildroots.genetics.testutil.Carrot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** WR-107: cultivar classification (GDD §6.1–6.2, §7.3; architecture §4.2). */
class CultivarClassifierTest {

    static final CompiledSpecies CS = new CompiledSpecies(Carrot.SPECIES);

    /** Carrot genome: allele sums for fruit and flavor, qualitative genotypes as {trait: {a, b}}. */
    static Genome plant(int fruitSum, int flavorSum, Map<TraitId, int[]> qual) {
        Genome.Builder b = Genome.builder(Carrot.SPECIES.id(), Carrot.LAYOUT);
        fill(b, Carrot.LAYOUT.section(FRUIT), fruitSum);
        fill(b, Carrot.LAYOUT.section(FLAVOR), flavorSum);
        for (Map.Entry<TraitId, int[]> e : qual.entrySet()) {
            Locus s = Carrot.LAYOUT.section(e.getKey());
            b.set(s, 0, 0, e.getValue()[0]).set(s, 0, 1, e.getValue()[1]);
        }
        return b.build();
    }

    static void fill(Genome.Builder b, Locus s, int sum) {
        for (int l = 0; l < s.loci(); l++) {
            for (int w = 0; w < 2; w++) {
                int v = Math.min(2, sum);
                b.set(s, l, w, v);
                sum -= v;
            }
        }
    }

    static Classification classify(Genome g, List<CultivarDef> profiles, Optional<CultivarId> parent) {
        return CultivarClassifier.classify(CS, g, profiles, parent);
    }

    static final int[] DOM = {1, 1};
    static final int[] WILD = {0, 0};

    @Test
    void easternCarrotByGEffAndGenotype() {
        // G_eff on the allele grid: fruit 10/24 = 0.417 ≥ 0.40, flavor 5/16 = 0.3125 ≥ 0.30;
        // bitterness domestic (bb), y1y1
        Genome g = plant(10, 5, Map.of(BITTER, DOM, Y1, DOM));
        double[] eff = PhenotypeCalculator.gEff(CS, g);
        assertTrue(eff[CS.quantIndex(FRUIT)] >= 0.40 && eff[CS.quantIndex(FLAVOR)] >= 0.30);
        Classification c = classify(g, Carrot.PROFILES, Optional.empty());
        assertEquals(Optional.of(Carrot.EASTERN.id()), c.cultivar());
        assertEquals(1, c.tier());
        assertFalse(c.inherited());
        // P_ is the other alternative of GDD §7.3
        assertEquals(Optional.of(Carrot.EASTERN.id()),
                classify(plant(10, 5, Map.of(BITTER, DOM, P, new int[] {1, 0})), Carrot.PROFILES,
                        Optional.empty()).cultivar());
        // bitter (wild phenotype dominant) is not a landrace
        assertEquals(Classification.WILD, classify(plant(10, 5, Map.of(BITTER, new int[] {1, 0}, Y1, DOM)),
                Carrot.PROFILES, Optional.empty()));
    }

    @Test
    void carrotByGEffAndGenotype() {
        // fruit 18/24 = 0.75; flavor 11/16 = 0.6875 − 0.2·(0.75 − 0.55) = 0.6475 ≥ 0.60 (G_eff)
        Genome g = plant(18, 11, Map.of(BITTER, DOM, Y1, DOM, Y2, DOM, P, WILD));
        Classification c = classify(g, Carrot.PROFILES, Optional.empty());
        assertEquals(Optional.of(Carrot.CARROT.id()), c.cultivar());
        assertEquals(2, c.tier());
        // G = 0.625 passes on G but not on G_eff (0.585): recognition uses G_eff
        assertEquals(Optional.of(Carrot.EASTERN.id()),
                classify(plant(18, 10, Map.of(BITTER, DOM, Y1, DOM, Y2, DOM, P, WILD)), Carrot.PROFILES,
                        Optional.empty()).cultivar());
        // P_ is epistatic: purple root is not the orange carrot
        assertEquals(Optional.of(Carrot.EASTERN.id()),
                classify(plant(18, 11, Map.of(BITTER, DOM, Y1, DOM, Y2, DOM, P, new int[] {0, 1})),
                        Carrot.PROFILES, Optional.empty()).cultivar());
    }

    static CultivarDef profile(String id, int tier, int priority, double fruitMin, double hysteresis) {
        return new CultivarDef(CultivarId.of(id), Carrot.SPECIES.id(), tier, priority,
                Map.of(FRUIT, Range.atLeast(fruitMin)), Map.of(), List.of(), hysteresis,
                Map.of(FRUIT, 1.0));
    }

    @Test
    void highestTierThenPriorityWins() {
        Genome g = plant(18, 0, Map.of());
        CultivarDef low = profile("t:a", 1, 5, 0.5, 0.05);
        CultivarDef high = profile("t:b", 1, 9, 0.5, 0.05);
        CultivarDef tier2 = profile("t:c", 2, -100, 0.5, 0.05);
        assertEquals("t:b", classify(g, List.of(low, high), Optional.empty()).cultivar().get().value());
        assertEquals("t:b", classify(g, List.of(high, low), Optional.empty()).cultivar().get().value());
        assertEquals("t:c", classify(g, List.of(low, tier2, high), Optional.empty()).cultivar().get().value());
        CultivarDef tieA = profile("t:x", 1, 0, 0.5, 0.05);
        CultivarDef tieB = profile("t:y", 1, 0, 0.5, 0.05);
        assertEquals("t:x", classify(g, List.of(tieB, tieA), Optional.empty()).cultivar().get().value());
    }

    @Test
    void offspringKeepsStatusWithinHysteresis() {
        Genome g = plant(18, 0, Map.of()); // fruit G_eff = 0.75
        CultivarDef minus004 = profile("t:keep", 2, 0, 0.79, 0.05);
        CultivarDef minus006 = profile("t:lose", 2, 0, 0.81, 0.05);
        Classification keep = classify(g, List.of(minus004), Optional.of(minus004.id()));
        assertEquals(Optional.of(minus004.id()), keep.cultivar());
        assertTrue(keep.inherited());
        assertEquals(Classification.WILD, classify(g, List.of(minus006), Optional.of(minus006.id())));
        assertEquals(Classification.WILD, classify(g, List.of(minus004), Optional.empty()),
                "without the parent status the threshold is strict");
        CultivarDef apple = profile("t:apple", 2, 0, 0.79, 0);
        assertEquals(Classification.WILD, classify(g, List.of(apple), Optional.of(apple.id())),
                "hysteresis 0 disables inheritance");
    }

    @Test
    void hysteresisDoesNotRelaxQualitativeRequirements() {
        Genome orange = plant(18, 11, Map.of(BITTER, DOM, Y1, DOM, Y2, DOM, P, WILD));
        Genome carrier = plant(18, 11, Map.of(BITTER, DOM, Y1, DOM, Y2, new int[] {1, 0}, P, WILD));
        assertEquals(Optional.of(Carrot.CARROT.id()),
                classify(orange, Carrot.PROFILES, Optional.of(Carrot.CARROT.id())).cultivar());
        assertEquals(Optional.of(Carrot.EASTERN.id()),
                classify(carrier, Carrot.PROFILES, Optional.of(Carrot.CARROT.id())).cultivar());
    }

    @Test
    void cloneAlwaysKeepsStatus() {
        Genome weak = plant(0, 0, Map.of());
        Classification source = new Classification(Optional.of(Carrot.CARROT.id()), 2, false);
        Genome clone = Cloning.clone(weak);
        Classification c = CultivarClassifier.forClone(source);
        assertEquals(source.cultivar(), c.cultivar());
        assertEquals(2, c.tier());
        assertEquals(Classification.WILD, classify(clone, Carrot.PROFILES, Optional.of(Carrot.CARROT.id())),
                "a seed with that genome would lose it; the clone does not");
        assertEquals(Classification.WILD, CultivarClassifier.forClone(Classification.WILD));
    }

    @Test
    void maxThresholdBranches() {
        CultivarDef branch = new CultivarDef(CultivarId.of("t:mid"), Carrot.SPECIES.id(), 2, 0,
                Map.of(FRUIT, new Range(0.45, 0.65)), Map.of(), List.of(), 0.05, Map.of(FRUIT, 1.0));
        assertTrue(classify(plant(12, 0, Map.of()), List.of(branch), Optional.empty()).cultivar().isPresent());
        assertFalse(classify(plant(18, 0, Map.of()), List.of(branch), Optional.empty()).cultivar().isPresent());
        assertTrue(classify(plant(16, 0, Map.of()), List.of(branch), Optional.of(branch.id())).cultivar().isPresent(),
                "max + hysteresis: 0.667 ≤ 0.70");
    }

    @Test
    void profilesOfOtherSpeciesAreIgnored() {
        CultivarDef other = new CultivarDef(CultivarId.of("t:o"), "wildroots:other", 2, 0, Map.of(), Map.of(),
                List.of(), 0.05, Map.of());
        assertEquals(Classification.WILD, classify(plant(0, 0, Map.of()), List.of(other), Optional.empty()));
        assertEquals(QualReq.DOMESTIC, Carrot.CARROT.qualitative().get(Y2));
    }
}
