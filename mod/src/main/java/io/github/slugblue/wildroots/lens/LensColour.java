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
        for (String key : propertyKeys(p.colourProps())) {
            out.add(DispCodec.Line.of(key));
        }
        return List.copyOf(out);
    }

    /**
     * The lang keys of what a colour does, in the order heat, cold, water, saturation; one per property that is
     * not neutral, chosen by the <b>sign</b> of the modifier, never by its size (the numbers are placeholders of
     * the balance pass, WR-704). The window and the item tooltip share them, so no text claims more than the
     * data does.
     */
    public static List<String> propertyKeys(ColourProps c) {
        List<String> out = new ArrayList<>(MAX_LINES - 1);
        sign(out, c.heat(), "heat.up", "heat.down");
        sign(out, c.cold(), "cold.up", "cold.down");
        sign(out, c.drought(), "water.less", "water.more");
        sign(out, c.saturationMult() - 1, "saturation", "saturation_less");
        return out;
    }

    private static void sign(List<String> out, double v, String up, String down) {
        if (v > 0) {
            out.add(KEY + "." + up);
        } else if (v < 0) {
            out.add(KEY + "." + down);
        }
    }
}
