package io.github.slugblue.wildroots.lens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.genetics.api.Environment;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** WR-703: the colour lines of the lens window (docs/design/carrot-colours-climate.md 1.4). */
class LensColourTest {

    static List<String> keys(int colour) {
        Phenotype p = ColourClimateTest.pheno(ColourClimateTest.CARROT, colour, Environment.neutral(0.6));
        List<DispCodec.Line> lines = LensColour.lines(ColourClimateTest.CARROT, p);
        assertTrue(lines.size() <= LensColour.MAX_LINES);
        lines.forEach(l -> assertTrue(DispCodec.valid(l), l::toString));
        return lines.stream().map(DispCodec.Line::key).collect(Collectors.toList());
    }

    @Test
    void purpleListsHeatColdWaterAndSaturation() {
        assertEquals(List.of(LensColour.KEY, LensColour.KEY + ".heat.up", LensColour.KEY + ".cold.down",
                LensColour.KEY + ".water.less", LensColour.KEY + ".saturation"), keys(ColourClimateTest.PURPLE));
        Phenotype p = ColourClimateTest.pheno(ColourClimateTest.CARROT, ColourClimateTest.PURPLE, Environment.neutral(0.6));
        DispCodec.Line first = LensColour.lines(ColourClimateTest.CARROT, p).get(0);
        assertEquals(List.of(DispCodec.keyArg("wildroots.colour.purple")), first.args());
    }

    @Test
    void theOtherColoursListOnlyWhatIsNotNeutral() {
        assertEquals(List.of(LensColour.KEY, LensColour.KEY + ".heat.up", LensColour.KEY + ".cold.down",
                LensColour.KEY + ".water.less"), keys(ColourClimateTest.YELLOW));
        assertEquals(List.of(LensColour.KEY, LensColour.KEY + ".heat.up", LensColour.KEY + ".cold.down"),
                keys(ColourClimateTest.RED));
        assertEquals(List.of(LensColour.KEY, LensColour.KEY + ".cold.up"), keys(ColourClimateTest.WHITE));
        assertEquals(List.of(LensColour.KEY, LensColour.KEY + ".heat.down"), keys(ColourClimateTest.ORANGE));
    }

    @Test
    void aSpeciesWithoutColoursHasNoLines() {
        var none = ColourClimateTest.withColours(io.github.slugblue.wildroots.genetics.api.ColourRules.NONE);
        Phenotype p = ColourClimateTest.pheno(none, ColourClimateTest.PURPLE, Environment.neutral(0.6));
        assertEquals(List.of(), LensColour.lines(none, p));
    }
}
