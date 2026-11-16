package io.github.slugblue.wildroots.lens;

import io.github.slugblue.wildroots.journal.JournalTriggers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * What the lens window and the styled item panel draw (playtest 2, items 3
 * and 4), in the journal's paper style: a title, short header lines, the
 * trait bars (with the offspring band when the row has one), flag lines
 * with the journal legend's icons, and notes. Display only, built from a
 * {@link LensRow} and keys the server wrote; nothing here reads a genome.
 * Pure (JUnit).
 *
 * @param title     first line
 * @param header    short lines under the title (generation, origin)
 * @param headIcon  icon beside the title (pollination of a lot), or {@link Icon#NONE}
 * @param barsTitle a heading over the bars ("Mother plant"), if any
 * @param bars      the bars
 * @param flags     flag lines
 * @param notes     notes after the flags (environment, "seeds vary", "and more", the click hint)
 */
public record LensSheet(Component title, List<Component> header, Icon headIcon, Optional<Component> barsTitle,
                        List<Bar> bars, List<Flag> flags, List<Component> notes) {

    public static final String MOTHER = "wildroots.window.lens.mother";
    public static final String VARY = "wildroots.window.lens.vary";
    public static final String ENV_MINUS = "wildroots.window.lens.env.minus";
    public static final String ENV_PLUS = "wildroots.window.lens.env.plus";

    /**
     * Flag icons, in the order of the journal legend's sprites
     * ({@code TraitBar.Flag} ordinals); {@link #NONE} draws no icon.
     */
    public enum Icon {
        BITTER, POLLINATION_B, POLLINATION_H, POLLINATION_N, POLLINATION_S, AILING, LIKE_CULTIVAR, NONE
    }

    /**
     * One bar: label ("name: word"), {@code step} of {@code max} filled, and
     * the offspring band {@code bandLo..bandHi} (both 0: no band).
     */
    public record Bar(Component label, int step, int max, int bandLo, int bandHi) {

        public Bar {
            max = Math.max(1, max);
            step = Math.max(0, Math.min(max, step));
            if (bandLo < 1 || bandHi < bandLo || bandHi > max) {
                bandLo = 0;
                bandHi = 0;
            }
        }

        public boolean hasBand() {
            return bandLo > 0;
        }
    }

    /** A flag line: icon and text. */
    public record Flag(Icon icon, Component text) {
    }

    public LensSheet {
        header = List.copyOf(header);
        bars = List.copyOf(bars);
        flags = List.copyOf(flags);
        notes = List.copyOf(notes);
        barsTitle = barsTitle == null ? Optional.empty() : barsTitle;
        headIcon = headIcon == null ? Icon.NONE : headIcon;
    }

    /** The bars of a row of {@code species}: one per trait token, with its band if the row has one. */
    public static List<Bar> bars(LensRow row, String species) {
        List<Bar> out = new ArrayList<>();
        List<LensRow.Token> tokens = LensRow.tokens(row.traits());
        for (int i = 0; i < tokens.size(); i++) {
            LensRow.Token t = tokens.get(i);
            Component label = Component.translatable(LensReading.TRAIT,
                    Component.translatable(LensRow.traitNameKey(t, species)),
                    Component.translatable(LensScale.wordKey(t.scale(), t.step())));
            int[] band = row.band(i).orElse(new int[] {0, 0});
            out.add(new Bar(label, t.step(), LensPanel.STEPS, band[0], band[1]));
        }
        return out;
    }

    /** Icon of a pollination flag letter. */
    public static Icon pollinationIcon(char p) {
        return switch (p) {
            case 'B' -> Icon.POLLINATION_B;
            case 'H' -> Icon.POLLINATION_H;
            case 'N' -> Icon.POLLINATION_N;
            case 'S' -> Icon.POLLINATION_S;
            default -> Icon.NONE;
        };
    }

    /** The flag lines of a row: bitterness, pollination, inbreeding, looks like. */
    public static List<Flag> flags(LensRow row) {
        List<Flag> out = new ArrayList<>();
        if (row.bitter() != '-') {
            out.add(new Flag(row.bitter() == 'w' ? Icon.BITTER : Icon.NONE, Component.translatable(
                    "wildroots.tooltip.qual_state.bitterness." + (row.bitter() == 'd' ? "domestic" : "wild"))));
        }
        String poll = switch (row.pollination()) {
            case 'B' -> "bees";
            case 'H' -> "hand";
            case 'N' -> "neighbours";
            case 'S' -> "self";
            default -> null;
        };
        if (poll != null) {
            out.add(new Flag(pollinationIcon(row.pollination()),
                    Component.translatable("wildroots.tooltip.pollination." + poll)));
        }
        if (row.ailing()) {
            out.add(new Flag(Icon.AILING, Component.translatable(LensReading.AILING)));
        }
        row.looksLike().ifPresent(l -> out.add(new Flag(Icon.LIKE_CULTIVAR, Component.translatable(
                LensReading.LOOKS_LIKE, Component.translatable(
                        JournalTriggers.nameKey("cultivar", l.contains(":") ? l : "wildroots:" + l))))));
        return out;
    }

    /** One note per environment modifier: "− dry soil", "+ compost". */
    public static List<Component> environment(int bits) {
        List<Component> out = new ArrayList<>();
        for (String k : EnvModifiers.keys(bits)) {
            out.add(Component.translatable(EnvModifiers.negative(k) ? ENV_MINUS : ENV_PLUS, Component.translatable(k)));
        }
        return out;
    }

    /** The sheet of a plant reading: its bars, flags and environment. */
    public static LensSheet plant(Component title, LensRow row, String species, int env) {
        return plant(title, row, species, env, List.of());
    }

    /** The same with the colour lines of a plant of stage 3 or more ({@link LensColour}), before the environment. */
    public static LensSheet plant(Component title, LensRow row, String species, int env, List<Component> colour) {
        List<Component> notes = new ArrayList<>(colour);
        notes.addAll(environment(env));
        if (row.more()) {
            notes.add(Component.translatable(LensRow.MORE_KEY));
        }
        return new LensSheet(title, List.of(), Icon.NONE, Optional.empty(), bars(row, species), flags(row), notes);
    }

    /**
     * The sheet of a lot: the bars are the mother's, titled "Mother plant",
     * and a note says that the seeds vary (item 6).
     */
    public static LensSheet lot(Component title, List<Component> header, LensRow row, String species) {
        return lot(title, header, row, species, false);
    }

    /**
     * Same; a realised seedling lot (review 1, m4) is one young plant that
     * keeps its genome and noise: its bars are its own, with no band, no
     * "Mother plant" and no "seeds vary".
     */
    public static LensSheet lot(Component title, List<Component> header, LensRow row, String species,
                                boolean seedling) {
        List<Component> notes = new ArrayList<>();
        if (!seedling) {
            notes.add(Component.translatable(VARY));
        }
        if (row.more()) {
            notes.add(Component.translatable(LensRow.MORE_KEY));
        }
        LensRow shown = seedling ? row.withBands(null) : row;
        return new LensSheet(title, header, pollinationIcon(row.pollination()),
                seedling ? Optional.empty() : Optional.of(Component.translatable(MOTHER)), bars(shown, species),
                flags(row), notes);
    }
}
