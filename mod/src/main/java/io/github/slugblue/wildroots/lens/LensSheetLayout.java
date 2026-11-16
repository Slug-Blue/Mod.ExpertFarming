package io.github.slugblue.wildroots.lens;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * The layout of a {@link LensSheet} on its paper card (playtest 2, panel
 * overflow): where every block of text goes, how many lines it wraps to
 * and how wide and tall the card must be. Pure: the real font is reached
 * through {@link Measure}, so JUnit checks the layout with a fake font and
 * the real lang strings.
 *
 * <p>The card is as wide as its widest element measured in real pixels
 * (a bar and its label side by side, a flag icon and its text, a header
 * line), but at most {@code cap} (the screen's width minus a margin). A
 * text that does not fit its space is wrapped, never drawn past the card,
 * and the height follows the real number of lines. {@link Block#right()}
 * of every block is at most the card width; that is the invariant of the
 * tests, and the drawing code draws exactly these blocks.
 */
public final class LensSheetLayout {

    public static final int LINE = 10;
    public static final int ICON = 9;
    public static final int ICON_GAP = 3;
    public static final int SECTION_GAP = 3;
    /** Extra height of a bar row that has a band strip. */
    public static final int BAND_ROOM = 2;
    /** The bar (journal trait bar): segments of 6 px with a 1 px gap, 4 px between the bar and its label. */
    public static final int SEGMENT_WIDTH = 6;
    public static final int SEGMENT_GAP = 1;
    public static final int LABEL_GAP = 4;
    public static final int ROW_HEIGHT = 10;
    /** The bar starts 1 px in from the card's left edge (its outline reaches the edge). */
    public static final int BAR_X = 1;
    /** Narrowest text column: smaller spaces are not worth wrapping into. */
    public static final int MIN_TEXT = 24;

    private LensSheetLayout() {
    }

    /** What the layout needs of a font. */
    public interface Measure {

        /** Pixel width of {@code c} on one line. */
        int width(Component c);

        /** The lines {@code c} wraps to at {@code maxWidth}; at least one. */
        List<Wrapped> wrap(Component c, int maxWidth);
    }

    /**
     * One wrapped line: its pixel width and what the drawing code needs to
     * draw it ({@code text}: the real font's {@code FormattedCharSequence},
     * kept so the line is split once, not again each frame; null in tests).
     */
    public record Wrapped(int width, Object text) {
    }

    /** What a block is. */
    public enum Kind {
        TITLE, HEADER, BARS_TITLE, BAR, FLAG, NOTE
    }

    /**
     * One block of the card. {@code x} is its left edge in the card,
     * {@code textX} where its text starts (after an icon or a bar),
     * {@code avail} the width the text may use, {@code lines} the pixel
     * width of each wrapped line, {@code h} its height.
     */
    public record Block(Kind kind, Component text, int index, int x, int textX, int y, int avail, List<Wrapped> lines,
                        int h) {

        /** Width of the widest line. */
        public int textWidth() {
            int w = 0;
            for (Wrapped l : lines) {
                w = Math.max(w, l.width());
            }
            return w;
        }

        /** Right edge of the widest line (or of the bar, if wider). */
        public int right() {
            return textX + textWidth();
        }
    }

    /** A sheet laid out once: the card's width and every block with its wrapped lines. */
    public record Prepared(int width, Layout layout) {
    }

    /** Lays {@code s} out at the width its content wants, at most {@code cap}: one layout, wrapped lines kept. */
    public static Prepared prepare(Measure m, LensSheet s, int cap) {
        int w = width(m, s, cap);
        return new Prepared(w, layout(m, s, w));
    }

    /**
     * A one-entry cache of {@link #prepare}: while the same sheet, cap and
     * measure come again (every frame a tooltip or window is drawn, and for
     * its width and height calls) the stored layout is returned and nothing
     * is measured or wrapped. A different key lays out again.
     */
    public static final class Cache {

        private Measure measure;
        private LensSheet sheet;
        private int cap = -1;
        private Prepared prepared;

        public Prepared get(Measure m, LensSheet s, int c) {
            if (prepared == null || m != measure || s != sheet || c != cap) {
                prepared = prepare(m, s, c);
                measure = m;
                sheet = s;
                cap = c;
            }
            return prepared;
        }
    }

    /** The layout at one width. */
    public record Layout(int width, int height, List<Block> blocks) {
    }

    /** Width of a bar of {@code segments} segments. */
    public static int barWidth(int segments) {
        int n = Math.max(1, segments);
        return n * SEGMENT_WIDTH + (n - 1) * SEGMENT_GAP;
    }

    static boolean noTitle(LensSheet s) {
        return s.headIcon() == LensSheet.Icon.NONE && s.title().getString().isEmpty();
    }

    static int iconIndent(boolean icon) {
        return icon ? ICON + ICON_GAP : 0;
    }

    /**
     * The card's width: the widest element on one line, at most {@code cap}
     * (and at least what the narrowest column needs). Notes and the title
     * may wrap, so they count at most {@code cap}.
     */
    public static int width(Measure m, LensSheet s, int cap) {
        int c = Math.max(cap, minWidth(s));
        int w = 0;
        if (!noTitle(s)) {
            w = Math.max(w, iconIndent(s.headIcon() != LensSheet.Icon.NONE) + m.width(s.title()));
        }
        for (Component h : s.header()) {
            w = Math.max(w, m.width(h));
        }
        if (s.barsTitle().isPresent()) {
            w = Math.max(w, m.width(s.barsTitle().get()));
        }
        for (LensSheet.Bar b : s.bars()) {
            w = Math.max(w, labelX(b) + m.width(b.label()));
        }
        for (LensSheet.Flag f : s.flags()) {
            w = Math.max(w, ICON + ICON_GAP + m.width(f.text()));
        }
        for (Component n : s.notes()) {
            w = Math.max(w, m.width(n));
        }
        return Math.min(c, Math.max(w, minWidth(s)));
    }

    /** Where a bar's label starts: the bar, then the gap. */
    static int labelX(LensSheet.Bar b) {
        return BAR_X + barWidth(b.max()) + LABEL_GAP;
    }

    /** The width below which the widest fixed part (a bar and a minimal label) would not fit. */
    static int minWidth(LensSheet s) {
        int w = ICON + ICON_GAP + MIN_TEXT;
        for (LensSheet.Bar b : s.bars()) {
            w = Math.max(w, labelX(b) + MIN_TEXT);
        }
        return w;
    }

    /** The card's blocks at {@code width}, with their wrapped lines and the total height. */
    public static Layout layout(Measure m, LensSheet s, int width) {
        int w = Math.max(width, minWidth(s));
        List<Block> out = new ArrayList<>();
        int y = 0;
        boolean icon = s.headIcon() != LensSheet.Icon.NONE;
        if (!noTitle(s)) {
            int x0 = iconIndent(icon);
            List<Wrapped> lines = m.wrap(s.title(), Math.max(MIN_TEXT, w - x0));
            int h = Math.max(lines.size(), 1) * LINE;
            out.add(new Block(Kind.TITLE, s.title(), 0, 0, x0, y, w - x0, lines, h));
            y += h;
        }
        for (int i = 0; i < s.header().size(); i++) {
            Component c = s.header().get(i);
            List<Wrapped> lines = m.wrap(c, Math.max(MIN_TEXT, w));
            int h = Math.max(lines.size(), 1) * LINE;
            out.add(new Block(Kind.HEADER, c, i, 0, 0, y, w, lines, h));
            y += h;
        }
        if (s.barsTitle().isPresent() || !s.bars().isEmpty()) {
            y += SECTION_GAP;
        }
        if (s.barsTitle().isPresent()) {
            Component c = s.barsTitle().get();
            List<Wrapped> lines = m.wrap(c, Math.max(MIN_TEXT, w));
            int h = Math.max(lines.size(), 1) * LINE;
            out.add(new Block(Kind.BARS_TITLE, c, 0, 0, 0, y, w, lines, h));
            y += h;
        }
        int band = s.bars().stream().anyMatch(LensSheet.Bar::hasBand) ? BAND_ROOM : 0;
        for (int i = 0; i < s.bars().size(); i++) {
            LensSheet.Bar b = s.bars().get(i);
            int lx = labelX(b);
            int avail = Math.max(MIN_TEXT, w - lx);
            List<Wrapped> lines = m.wrap(b.label(), avail);
            int h = Math.max(ROW_HEIGHT, Math.max(lines.size(), 1) * LINE) + band;
            out.add(new Block(Kind.BAR, b.label(), i, BAR_X, lx, y, avail, lines, h));
            y += h;
        }
        if (!s.flags().isEmpty()) {
            y += SECTION_GAP;
            for (int i = 0; i < s.flags().size(); i++) {
                Component c = s.flags().get(i).text();
                int x0 = ICON + ICON_GAP;
                List<Wrapped> lines = m.wrap(c, Math.max(MIN_TEXT, w - x0));
                int h = Math.max(lines.size(), 1) * LINE;
                out.add(new Block(Kind.FLAG, c, i, 0, x0, y, w - x0, lines, h));
                y += h;
            }
        }
        if (!s.notes().isEmpty()) {
            y += SECTION_GAP;
            for (int i = 0; i < s.notes().size(); i++) {
                Component c = s.notes().get(i);
                List<Wrapped> lines = m.wrap(c, Math.max(MIN_TEXT, w));
                int h = Math.max(lines.size(), 1) * LINE;
                out.add(new Block(Kind.NOTE, c, i, 0, 0, y, w, lines, h));
                y += h;
            }
        }
        return new Layout(w, y, out);
    }
}
