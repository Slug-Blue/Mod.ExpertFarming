package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** D2: the lens panel shows exactly the tier-1 steps of the row, nothing more. */
class LensPanelTest {

    @Test
    void barsFollowTheRowTokens() {
        List<LensPanel.Bar> bars = LensPanel.bars(LensRowTest.FULL, "wildroots:wild_carrot");
        List<LensRow.Token> tokens = LensRow.tokens(LensRowTest.FULL.traits());
        assertEquals(tokens.size(), bars.size());
        for (int i = 0; i < bars.size(); i++) {
            LensPanel.Bar b = bars.get(i);
            LensRow.Token t = tokens.get(i);
            assertEquals(t.step(), b.step(), "step of " + t.trait());
            assertEquals(LensPanel.STEPS, b.max());
            assertEquals(LensScale.wordKey(t.scale(), t.step()), b.wordKey(), "word of " + t.trait());
            assertEquals(LensRow.traitNameKey(t, "wildroots:wild_carrot"), b.labelKey());
        }
        assertEquals("wildroots.tooltip.trait_name.fruit_size.wild_carrot", bars.get(0).labelKey());
        assertEquals("wildroots.tooltip.trait_name.yield", bars.get(1).labelKey());
    }

    @Test
    void stepsMatchTheLensScaleBounds() {
        double[] p = {0.0, 0.199, 0.20, 0.349, 0.35, 0.549, 0.55, 0.749, 0.75, 1.0};
        int[] step = {1, 1, 2, 2, 3, 3, 4, 4, 5, 5};
        for (int i = 0; i < p.length; i++) {
            String row = "s" + LensScale.step(p[i]);
            List<LensPanel.Bar> bars = LensPanel.bars(new LensRow(row, '-', '-', false, Optional.empty(), false),
                    "wildroots:wild_carrot");
            assertEquals(step[i], bars.get(0).step(), "P " + p[i]);
        }
    }

    @Test
    void gradeBarIsOneToFourOfFour() {
        for (int g = 1; g <= 4; g++) {
            LensPanel.Bar b = LensPanel.gradeBar(g);
            assertEquals(g, b.step());
            assertEquals(4, b.max());
            assertEquals("wildroots.panel.grade." + g, b.wordKey());
        }
        assertEquals(1, LensPanel.gradeBar(0).step());
        assertEquals(4, LensPanel.gradeBar(9).step());
    }

    @Test
    void barsClampAndRowsAreFoundOnlyAsLensRows() {
        assertEquals(5, new LensPanel.Bar("k", 9, 5, "w").step());
        assertEquals(0, new LensPanel.Bar("k", -1, 5, "w").step());
        assertTrue(LensPanel.row(List.of(DispCodec.Line.of("wildroots.tooltip.lot.from_wild"))).isEmpty());
        assertEquals(Optional.of(LensRowTest.FULL),
                LensPanel.row(List.of(DispCodec.Line.of("wildroots.tooltip.lot.from_wild"), LensRowTest.FULL.line())));
        assertTrue(LensPanel.bars(new LensRow("", '-', '-', false, Optional.empty(), true), "x:y").isEmpty());
    }
}
