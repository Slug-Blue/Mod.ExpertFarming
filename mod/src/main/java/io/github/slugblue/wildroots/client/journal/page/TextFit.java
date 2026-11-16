package io.github.slugblue.wildroots.client.journal.page;

import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Fitting text into a width (journal v2 §5.3; owner playtest 3: nothing may
 * leave its page). Pure: the width of a string comes from a
 * {@link Measure}, the game's font in play and a fake one in tests, so the
 * same decisions are checked over every real ru and en string.
 */
@OnlyIn(Dist.CLIENT)
public final class TextFit {

    /** The width of a string in GUI pixels. */
    @FunctionalInterface
    public interface Measure {

        int width(String s, boolean bold);

        default int width(String s) {
            return width(s, false);
        }
    }

    public static final String ELLIPSIS = "…";

    private TextFit() {
    }

    /**
     * Greedy word wrap to {@code width}. A single word wider than the
     * width is cut into pieces that fit, so no line is ever wider (a line of
     * one character wider than the width is the only exception).
     */
    public static List<String> wrap(String text, int width, boolean bold, Measure m) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            while (m.width(word, bold) > width && word.length() > 1) {
                if (!line.isEmpty()) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                int n = fitting(word, width, bold, m);
                out.add(word.substring(0, n));
                word = word.substring(n);
            }
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && m.width(next, bold) > width) {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    /** How many leading characters of {@code s} fit in {@code width} (at least 1). */
    static int fitting(String s, int width, boolean bold, Measure m) {
        int n = 1;
        while (n < s.length() && m.width(s.substring(0, n + 1), bold) <= width) {
            n++;
        }
        return n;
    }

    /** {@code s} as is if it fits, else cut with an ellipsis so that it does. */
    public static String ellipsize(String s, int width, boolean bold, Measure m) {
        if (m.width(s, bold) <= width) {
            return s;
        }
        int ell = m.width(ELLIPSIS, bold);
        int n = 0;
        while (n < s.length() && m.width(s.substring(0, n + 1), bold) + ell <= width) {
            n++;
        }
        return s.substring(0, n).stripTrailing() + ELLIPSIS;
    }

    /**
     * At most {@code maxLines} lines: the wrapped text, and if it needs more,
     * the last allowed line ends with an ellipsis (the full text belongs in
     * a tooltip).
     */
    public static List<String> wrapMax(String text, int width, int maxLines, boolean bold, Measure m) {
        List<String> lines = wrap(text, width, bold, m);
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<String> out = new ArrayList<>(lines.subList(0, maxLines - 1));
        StringBuilder rest = new StringBuilder();
        for (String l : lines.subList(maxLines - 1, lines.size())) {
            rest.append(rest.isEmpty() ? "" : " ").append(l);
        }
        out.add(ellipsize(rest.toString(), width, bold, m));
        return out;
    }

    /** Smallest scale a one-line title is shrunk to before it is cut with an ellipsis. */
    public static final float MIN_SHRINK = 0.7f;

    /**
     * The scale at which a line {@code width} px wide fits {@code avail}:
     * 1 if it fits, else {@code avail / width} but not below {@code min}
     * (below that the text is cut with an ellipsis instead).
     */
    public static float shrinkScale(int width, int avail, float min) {
        if (width <= avail || width <= 0) {
            return 1f;
        }
        return Math.max(min, (float) avail / width);
    }

    /** The widest line. */
    public static int widest(List<String> lines, boolean bold, Measure m) {
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, m.width(l, bold));
        }
        return w;
    }

    /** The measure of a real font; bold goes through the component style (the font widens bold glyphs). */
    public static Measure of(net.minecraft.client.gui.Font font) {
        return (s, bold) -> bold ? font.width(net.minecraft.network.chat.Component.literal(s)
                .withStyle(net.minecraft.ChatFormatting.BOLD)) : font.width(s);
    }
}
