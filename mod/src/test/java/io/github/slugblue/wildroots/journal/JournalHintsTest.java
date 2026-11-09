package io.github.slugblue.wildroots.journal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** WR-182: hints of later lines and features never fire in a wave-1 build; per-line rules. */
class JournalHintsTest {

    private Predicate<String> old;

    @BeforeEach
    void wave1() {
        old = JournalHints.setLineCheck(Set.of("carrot")::contains);
    }

    @AfterEach
    void restore() {
        JournalHints.setLineCheck(old);
    }

    @Test
    void wave1HintsAreEnabledLaterOnesAreNot() {
        for (String id : new String[] {"welcome", "bees", "inbreeding", "stagnation", "looks_like", "first_landrace",
            "first_cultivar", "seed_pouch", "yellow_root", "orange_root", "purple_root", "mother_plant"}) {
            assertTrue(JournalHints.enabled("hint." + id), id);
        }
        for (String id : new String[] {"shattering", "brush_for_wheat", "teosinte_case", "cabbage_roads",
            "toxic_tomato", "hand_pollination", "forced_self", "genotype_lab", "clone", "graft", "red_flesh",
            "village_seeds", "breeder_met", "no_such_hint"}) {
            assertFalse(JournalHints.enabled("hint." + id), id);
        }
        assertTrue(JournalHints.enabled("species.wild_carrot"), "non-hint entries are not gated");
    }

    @Test
    void perLineSuffixOnlyForPerLineHints() {
        assertTrue(JournalHints.enabled("hint.stagnation.l0123456789abcdef"));
        assertTrue(JournalHints.enabled("hint.missing_allele.l0123456789abcdef"));
        assertFalse(JournalHints.enabled("hint.bees.l0123456789abcdef"));
    }

    @Test
    void stagnationAfterThreeStalledGenerationsAndRepeatNotBeforeThree() {
        JournalData j = new JournalData();
        String l = LineProgress.lineKey("Рыжик");
        // gen 1..3 improve, then gens 4, 5, 6 do not beat the best of gen 3
        assertFalse(LineProgress.observe(j, l, 1, 0.30));
        assertFalse(LineProgress.observe(j, l, 2, 0.35));
        assertFalse(LineProgress.observe(j, l, 2, 0.36));
        assertFalse(LineProgress.observe(j, l, 3, 0.40));
        assertFalse(LineProgress.observe(j, l, 4, 0.40)); // closes gen 3: improved
        assertFalse(LineProgress.observe(j, l, 5, 0.401)); // closes gen 4: stall 1
        assertFalse(LineProgress.observe(j, l, 6, 0.395)); // closes gen 5: stall 2
        assertTrue(LineProgress.observe(j, l, 7, 0.39)); // closes gen 6: stall 3 -> stagnation
        assertFalse(LineProgress.observe(j, l, 3, 0.10), "an older generation is ignored");
        assertFalse(LineProgress.observe(j, l, 8, 0.50)); // gen 7 stalled once more (count restarted)

        assertTrue(LineProgress.mayFire(j, "stagnation", l, 7));
        assertFalse(LineProgress.mayFire(j, "stagnation", l, 8));
        assertFalse(LineProgress.mayFire(j, "stagnation", l, 9));
        assertTrue(LineProgress.mayFire(j, "stagnation", l, 10));
        assertTrue(LineProgress.mayFire(j, "stagnation", LineProgress.lineKey("other"), 8), "lines are separate");
    }

    @Test
    void lineKeyIsACounterSafeId() {
        String k = LineProgress.lineKey("Line «Рыжик» (2)");
        assertTrue(JournalData.validId("hint.stagnation." + k), k);
        assertTrue(JournalData.COUNTER.matcher("lp." + k + ".n").matches());
    }
}
