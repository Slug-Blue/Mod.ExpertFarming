package io.github.slugblue.wildroots.client.lens;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.ui.TraitBar;
import io.github.slugblue.wildroots.lens.LensSheet;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import io.github.slugblue.wildroots.lens.LensSheetLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws a {@link LensSheet} on the journal's paper (playtest 2, items 3 and
 * 4): ink text without shadow, {@link TraitBar#drawOnPaper} bars with the
 * five marks and the legend's words, the offspring band as a faint strip
 * under the marks it spans, and the legend's flag icons. Shared by the lens
 * window and the styled item panel, so both read the same way as the
 * journal's "Reading the Lens" page.
 */
@OnlyIn(Dist.CLIENT)
public final class LensSheetView {

    /** The journal's paper. */
    public static final int PAPER = 0xFFF4EEDE;
    /** The band: faded ink, half transparent. */
    static final int BAND = 0x808A7A62;

    private LensSheetView() {
    }

    /** The real font as the layout's measure; one per font, so the cache key stays the same between frames. */
    private static Font measureFont;
    private static LensSheetLayout.Measure measure;

    static LensSheetLayout.Measure measure(Font font) {
        if (font != measureFont) {
            measureFont = font;
            measure = new LensSheetLayout.Measure() {
                @Override
                public int width(Component c) {
                    return font.width(c);
                }

                @Override
                public List<LensSheetLayout.Wrapped> wrap(Component c, int maxWidth) {
                    List<FormattedCharSequence> split = font.split(c, Math.max(1, maxWidth));
                    List<LensSheetLayout.Wrapped> out = new java.util.ArrayList<>(Math.max(1, split.size()));
                    for (FormattedCharSequence l : split) {
                        out.add(new LensSheetLayout.Wrapped(font.width(l), l));
                    }
                    if (out.isEmpty()) {
                        out.add(new LensSheetLayout.Wrapped(0, FormattedCharSequence.EMPTY));
                    }
                    return out;
                }
            };
        }
        return measure;
    }

    private static final LensSheetLayout.Cache CACHE = new LensSheetLayout.Cache();

    /**
     * The sheet laid out once for its content width, at most {@code cap}
     * (playtest 2 overflow; review M1): the same sheet, cap and font return
     * the stored layout and its wrapped lines, so width, height and drawing
     * in every frame measure and wrap nothing. Render thread only.
     */
    public static LensSheetLayout.Prepared prepare(Font font, LensSheet s, int cap) {
        return CACHE.get(measure(font), s, cap);
    }

    /** Draws a prepared sheet with its top left at {@code x, y}; returns the height used. */
    public static int draw(GuiGraphics g, Font font, LensSheet s, LensSheetLayout.Prepared prepared, int x, int y) {
        LensSheetLayout.Layout lay = prepared.layout();
        int width = prepared.width();
        if (s.headIcon() != LensSheet.Icon.NONE) {
            TraitBar.drawFlag(g, flag(s.headIcon()), x, y);
        }
        for (LensSheetLayout.Block b : lay.blocks()) {
            int by = y + b.y();
            switch (b.kind()) {
                case TITLE -> lines(g, font, b, x, by, JournalColors.INK);
                case HEADER, NOTE -> lines(g, font, b, x, by, JournalColors.INK_FADED);
                case BARS_TITLE -> {
                    lines(g, font, b, x, by, JournalColors.INK);
                    g.fill(x, by + b.h() - 1, x + Math.min(width, b.textWidth()), by + b.h(), JournalColors.INK_FADED);
                }
                case BAR -> {
                    LensSheet.Bar bar = s.bars().get(b.index());
                    int bx = x + b.x();
                    // the bar alone; its label is drawn wrapped inside the card below
                    TraitBar.drawOnPaper(g, bx, by, bar.step(), bar.max(), Component.empty());
                    lines(g, font, b, x, by, JournalColors.INK);
                    if (bar.hasBand()) {
                        int step = TraitBar.SEGMENT_WIDTH + TraitBar.GAP;
                        int x0 = bx + (bar.bandLo() - 1) * step - 1;
                        int x1 = bx + (bar.bandHi() - 1) * step + TraitBar.SEGMENT_WIDTH + 1;
                        int top = by + (TraitBar.ROW_HEIGHT - TraitBar.BAR_HEIGHT) / 2 - 1 + TraitBar.BAR_HEIGHT + 2;
                        g.fill(x0, top, x1, top + LensSheetLayout.BAND_ROOM, BAND);
                    }
                }
                case FLAG -> {
                    LensSheet.Flag f = s.flags().get(b.index());
                    if (f.icon() != LensSheet.Icon.NONE) {
                        TraitBar.drawFlag(g, flag(f.icon()), x, by);
                    }
                    lines(g, font, b, x, by, JournalColors.INK);
                }
            }
        }
        return lay.height();
    }

    /** The wrapped lines of a block, ink without shadow, inside {@code avail}. */
    private static void lines(GuiGraphics g, Font font, LensSheetLayout.Block b, int x, int y, int colour) {
        int ly = y;
        for (LensSheetLayout.Wrapped l : b.lines()) {
            g.drawString(font, (FormattedCharSequence) l.text(), x + b.textX(), ly + 1, colour, false);
            ly += LensSheetLayout.LINE;
        }
    }

    /** The journal legend's sprite of an icon ({@link LensSheet.Icon#NONE} has none and is never drawn). */
    static TraitBar.Flag flag(LensSheet.Icon icon) {
        return switch (icon) {
            case BITTER -> TraitBar.Flag.BITTER;
            case POLLINATION_B -> TraitBar.Flag.POLLINATION_B;
            case POLLINATION_H -> TraitBar.Flag.POLLINATION_H;
            case POLLINATION_N -> TraitBar.Flag.POLLINATION_N;
            case POLLINATION_S -> TraitBar.Flag.POLLINATION_S;
            case AILING -> TraitBar.Flag.AILING;
            case LIKE_CULTIVAR, NONE -> TraitBar.Flag.LIKE_CULTIVAR;
        };
    }

    /** A sheet of paper: fill, an ink frame and a faded inner line (the journal's page edge). */
    public static void paper(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PAPER);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, JournalColors.INK);
        g.renderOutline(x0 + 2, y0 + 2, x1 - x0 - 4, y1 - y0 - 4, 0x408A7A62);
    }
}
