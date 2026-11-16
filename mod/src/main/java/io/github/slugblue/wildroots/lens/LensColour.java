package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.genetics.api.ColourProps;
import io.github.slugblue.wildroots.genetics.api.ColourRules;
import io.github.slugblue.wildroots.genetics.api.Phenotype;
import io.github.slugblue.wildroots.genetics.api.SpeciesDef;
import java.util.ArrayList;
import java.util.List;

/**
 * The colour of a plant for the lens window (WR-703, docs/design/carrot-colours-climate.md §1.4, §2.1): one
 * line with the colour name and one line per property that is not neutral (stands heat, suffers in cold, needs
 * less water, saturation). The server builds the lines from the species' colour rules (the client does not have
 * them) as translation keys, like every other line of the window. The window shows them only from stage 3, when
 * the root's shoulder shows the colour on the block as well. Pure (JUnit).
 */
public final class LensColour {

    public static final String KEY = "wildroots.window.lens.colour";
    /** Name of colour {@code id}: {@code wildroots.colour.<id>}. */
    public static final String NAME_PREFIX = "wildroots.colour.";
    /** The name line and heat, cold, water and saturation. */
    public static final int MAX_LINES = 5;

    private LensColour() {
    }

    /** The lines of the plant's colour, empty if the species has no colours or no rule matched. */
    public static List<DispCodec.Line> lines(SpeciesDef def, Phenotype p) {
        ColourRules rules = def.colours();
        int colour = p.colour();
        if (rules.isNone() || colour < 0 || colour >= rules.ids().size()) {
            return List.of();
        }
        List<DispCodec.Line> out = new ArrayList<>(MAX_LINES);
        out.add(DispCodec.Line.of(KEY, DispCodec.keyArg(NAME_PREFIX + rules.ids().get(colour))));
        ColourProps c = p.colourProps();
        sign(out, c.heat(), "heat.up", "heat.down");
        sign(out, c.cold(), "cold.up", "cold.down");
        sign(out, c.drought(), "water.less", "water.more");
        sign(out, c.saturationMult() - 1, "saturation", "saturation_less");
        return List.copyOf(out);
    }

    private static void sign(List<DispCodec.Line> out, double v, String up, String down) {
        if (v > 0) {
            out.add(DispCodec.Line.of(KEY + "." + up));
        } else if (v < 0) {
            out.add(DispCodec.Line.of(KEY + "." + down));
        }
    }
}
