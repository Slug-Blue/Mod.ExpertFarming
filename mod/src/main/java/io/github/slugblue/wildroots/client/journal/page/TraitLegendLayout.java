package io.github.slugblue.wildroots.client.journal.page;

import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The layout of the trait legend (journal v2 §3.4; owner playtest 3: the
 * Russian words did not fit and were cut). Pure: strings and a
 * {@link TextFit.Measure} in, positioned items out, so every real string is
 * checked in JUnit with a fake font.
 *
 * <p>The content is a flow of blocks on half pages of {@value #WIDTH} ×
 * {@value #HEIGHT}: how to read (intro, the five steps of a scale with its
 * switch), how to use the lens, the grade of produce, the flag marks, and
 * the example of a species. A block never splits across pages, so a page
 * is added when the next block does not fit; English may need two pages,
 * Russian three. Nothing is cut, and nothing is wider than the page:
 * <ul>
 *   <li>text wraps, a word wider than the page is broken;</li>
 *   <li>a bar with its word: the word sits beside the bar when it fits in
 *       {@value #SIDE_WIDTH} px, else on its own line(s) above the bar;</li>
 *   <li>a flag mark: icon, then its text wrapped in the rest of the width.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class TraitLegendLayout {

    public static final int WIDTH = PageRect.HALF_WIDTH;
    public static final int HEIGHT = PageRect.HEIGHT;
    public static final int LINE = PageRect.LINE_HEIGHT;
    /** Width of a bar with its outline (5 segments of 6 px, gaps of 1, outline 1 each side). */
    public static final int BAR_WIDTH = 36;
    /** Space between a bar and a word beside it. */
    public static final int BAR_GAP = 4;
    /** Width left for a word beside its bar. */
    public static final int SIDE_WIDTH = WIDTH - BAR_WIDTH - BAR_GAP;
    /** Row height of a bar with a one-line word beside it. */
    public static final int SIDE_ROW = 10;
    /** Height of a bar under its word(s), added to the lines. */
    public static final int BAR_UNDER = 8;
    static final int GAP = 4;
    static final int FLAG_ICON = 9;
    static final int FLAG_TEXT_X = 11;
    /** Most lines of a heading or a word above a bar (longer ends with an ellipsis). */
    static final int MAX_LINES = 2;

    public enum Kind {
        INK, BOLD, FADED
    }

    /** A block of wrapped text at {@code y} (x = 0). */
    public record Text(int y, List<String> lines, Kind kind) {
    }

    /**
     * A bar of {@code step}: at {@code y}, x = 1. Beside: its word at x =
     * {@value #BAR_WIDTH} + {@value #BAR_GAP}; stacked: {@code label} lines
     * at {@code y}, the bar under them at {@code barY}. For the five steps
     * of a scale {@code label} is empty and {@code stepIndex} (0-4) says
     * which word of the scale goes there.
     */
    public record Bar(int y, int barY, int step, boolean stacked, List<String> label, int stepIndex) {
    }

    /** A flag mark: icon {@code flag} at x = 0, the text lines at x = {@value #FLAG_TEXT_X}. */
    public record Flag(int y, int flag, List<String> lines) {
    }

    /** The clickable scale line of the steps block, {@code h} high; its text depends on the scale. */
    public record ScaleLine(int y, int h) {
    }

    /** One half page: its items (Text, Bar, Flag, ScaleLine) and its height. */
    public record Page(List<Object> items, int height) {
    }

    /** All the strings of a legend, already translated; {@code words[scale][step]}, {@code scaleLines[scale]}. */
    public record Strings(String intro, String grade, String howTo, String speciesName, List<String> traitNames,
                          List<Integer> traitSteps, String[][] words, String[] scaleLines, String flagsTitle,
                          List<String> flagLabels, String example) {
    }

    private TraitLegendLayout() {
    }

    /** A block: its items at y relative to its top, and its height. */
    private record Block(List<Object> items, int height) {
    }

    private static Object at(Object item, int dy) {
        if (item instanceof Text t) {
            return new Text(t.y() + dy, t.lines(), t.kind());
        }
        if (item instanceof Bar b) {
            return new Bar(b.y() + dy, b.barY() + dy, b.step(), b.stacked(), b.label(), b.stepIndex());
        }
        if (item instanceof Flag f) {
            return new Flag(f.y() + dy, f.flag(), f.lines());
        }
        ScaleLine s = (ScaleLine) item;
        return new ScaleLine(s.y() + dy, s.h());
    }

    public static List<Page> layout(Strings s, TextFit.Measure m) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(howToRead(s, m));
        blocks.add(text(s.howTo(), Kind.INK, m));
        blocks.add(text(s.grade(), Kind.INK, m));
        blocks.add(flags(s, m));
        blocks.add(example(s, m));
        List<Page> pages = new ArrayList<>();
        List<Object> items = new ArrayList<>();
        int y = 0;
        for (Block b : blocks) {
            if (b.items().isEmpty()) {
                continue;
            }
            if (y > 0 && y + GAP + b.height() > HEIGHT) {
                pages.add(new Page(List.copyOf(items), y));
                items = new ArrayList<>();
                y = 0;
            }
            if (y > 0) {
                y += GAP;
            }
            for (Object it : b.items()) {
                items.add(at(it, y));
            }
            y += b.height();
        }
        if (!items.isEmpty()) {
            pages.add(new Page(List.copyOf(items), y));
        }
        return List.copyOf(pages);
    }

    private static Block text(String t, Kind kind, TextFit.Measure m) {
        List<String> lines = TextFit.wrap(t, WIDTH, kind == Kind.BOLD, m);
        return new Block(List.of(new Text(0, lines, kind)), lines.size() * LINE);
    }

    /** Intro, the five steps, the scale line. The row mode of a step is the worst over the 8 scales. */
    private static Block howToRead(Strings s, TextFit.Measure m) {
        List<Object> items = new ArrayList<>();
        List<String> intro = TextFit.wrap(s.intro(), WIDTH, false, m);
        items.add(new Text(0, intro, Kind.INK));
        int y = intro.size() * LINE + 3;
        for (int step = 0; step < 5; step++) {
            boolean stacked = false;
            int lines = 1;
            for (String[] scale : s.words()) {
                if (m.width(scale[step], false) > SIDE_WIDTH) {
                    stacked = true;
                    lines = Math.max(lines, TextFit.wrapMax(scale[step], WIDTH, MAX_LINES, false, m).size());
                }
            }
            items.add(new Bar(y, stacked ? y + lines * LINE : y, step + 1, stacked, List.of(), step));
            y += stacked ? lines * LINE + BAR_UNDER : SIDE_ROW;
        }
        int h = LINE;
        for (String line : s.scaleLines()) {
            h = Math.max(h, TextFit.wrapMax(line, WIDTH, MAX_LINES, false, m).size() * LINE);
        }
        items.add(new ScaleLine(y + 1, h));
        return new Block(items, y + 1 + h);
    }

    private static Block flags(Strings s, TextFit.Measure m) {
        List<Object> items = new ArrayList<>();
        List<String> head = TextFit.wrapMax(s.flagsTitle(), WIDTH, MAX_LINES, true, m);
        items.add(new Text(0, head, Kind.BOLD));
        int y = head.size() * LINE + 1;
        for (int i = 0; i < s.flagLabels().size(); i++) {
            List<String> lines = TextFit.wrap(s.flagLabels().get(i), WIDTH - FLAG_TEXT_X, false, m);
            items.add(new Flag(y, i, lines));
            y += Math.max(FLAG_ICON + 2, lines.size() * LINE + 2);
        }
        return new Block(items, y);
    }

    private static Block example(Strings s, TextFit.Measure m) {
        List<Object> items = new ArrayList<>();
        List<String> head = TextFit.wrapMax(s.speciesName(), WIDTH, MAX_LINES, true, m);
        items.add(new Text(0, head, Kind.BOLD));
        int y = head.size() * LINE + 1;
        List<String> note = TextFit.wrap(s.example(), WIDTH, false, m);
        items.add(new Text(y, note, Kind.FADED));
        y += note.size() * LINE + 3;
        for (int i = 0; i < s.traitNames().size() && i < s.traitSteps().size(); i++) {
            String name = s.traitNames().get(i);
            if (m.width(name, false) <= SIDE_WIDTH) {
                items.add(new Bar(y, y, s.traitSteps().get(i), false, List.of(name), -1));
                y += SIDE_ROW;
            } else {
                List<String> lines = TextFit.wrapMax(name, WIDTH, MAX_LINES, false, m);
                items.add(new Bar(y, y + lines.size() * LINE, s.traitSteps().get(i), true, lines, -1));
                y += lines.size() * LINE + BAR_UNDER;
            }
        }
        return new Block(items, y);
    }
}
