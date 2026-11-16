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

    /** The real font as the layout's measure. */
    static LensSheetLayout.Measure measure(Font font) {
        return new LensSheetLayout.Measure() {
            @Override
            public int width(Component c) {
                return font.width(c);
            }

            @Override
            public List<Integer> wrap(Component c, int maxWidth) {
                List<Integer> out = new java.util.ArrayList<>();
                for (FormattedCharSequence l : font.split(c, Math.max(1, maxWidth))) {
                    out.add(font.width(l));
                }
                if (out.isEmpty()) {
                    out.add(0);
                }
                return out;
            }
        };
    }

    /** The card's width for {@code s}: its widest element, at most {@code cap} (review: playtest 2 overflow). */
    public static int width(Font font, LensSheet s, int cap) {
        return LensSheetLayout.width(measure(font), s, cap);
    }

    /** Height at {@code width}: the real number of wrapped lines. */
    public static int height(Font font, LensSheet s, int width) {
        return LensSheetLayout.layout(measure(font), s, width).height();
    }

    /** Draws the sheet with its top left at {@code x, y} on a card {@code width} wide; returns the height used. */
    public static int draw(GuiGraphics g, Font font, LensSheet s, int x, int y, int width) {
        LensSheetLayout.Layout lay = LensSheetLayout.layout(measure(font), s, width);
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
                    int w = b.lines().stream().mapToInt(Integer::intValue).max().orElse(0);
                    g.fill(x, by + b.h() - 1, x + Math.min(width, w), by + b.h(), JournalColors.INK_FADED);
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
        for (FormattedCharSequence l : font.split(b.text(), Math.max(1, b.avail()))) {
            g.drawString(font, l, x + b.textX(), ly + 1, colour, false);
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
