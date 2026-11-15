package io.github.slugblue.wildroots.client.lens;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.ui.TraitBar;
import io.github.slugblue.wildroots.lens.LensSheet;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
    static final int LINE = 10;
    static final int ICON = 9;
    static final int ICON_GAP = 3;
    static final int SECTION_GAP = 3;
    /** Extra height of a bar row that has a band strip. */
    static final int BAND_ROOM = 2;

    private LensSheetView() {
    }

    static boolean anyBand(LensSheet s) {
        return s.bars().stream().anyMatch(LensSheet.Bar::hasBand);
    }

    static int barRow(LensSheet s) {
        return TraitBar.ROW_HEIGHT + (anyBand(s) ? BAND_ROOM : 0);
    }

    /** Natural width: the widest element, at most {@code max}. */
    public static int width(Font font, LensSheet s, int max) {
        int w = font.width(s.title()) + (s.headIcon() == LensSheet.Icon.NONE ? 0 : ICON + ICON_GAP);
        for (Component c : s.header()) {
            w = Math.max(w, font.width(c));
        }
        if (s.barsTitle().isPresent()) {
            w = Math.max(w, font.width(s.barsTitle().get()));
        }
        for (LensSheet.Bar b : s.bars()) {
            w = Math.max(w, 1 + TraitBar.width(font, b.max(), b.label()));
        }
        for (LensSheet.Flag f : s.flags()) {
            w = Math.max(w, ICON + ICON_GAP + font.width(f.text()));
        }
        for (Component c : s.notes()) {
            w = Math.max(w, Math.min(max, font.width(c)));
        }
        return Math.min(max, w);
    }

    /** Height at {@code width}. */
    public static int height(Font font, LensSheet s, int width) {
        int h = lines(font, s.title(), width - (s.headIcon() == LensSheet.Icon.NONE ? 0 : ICON + ICON_GAP)) * LINE;
        for (Component c : s.header()) {
            h += lines(font, c, width) * LINE;
        }
        if (s.barsTitle().isPresent() || !s.bars().isEmpty()) {
            h += SECTION_GAP;
        }
        if (s.barsTitle().isPresent()) {
            h += LINE;
        }
        h += s.bars().size() * barRow(s);
        if (!s.flags().isEmpty()) {
            h += SECTION_GAP + s.flags().size() * LINE;
        }
        if (!s.notes().isEmpty()) {
            h += SECTION_GAP;
            for (Component c : s.notes()) {
                h += lines(font, c, width) * LINE;
            }
        }
        return h;
    }

    static int lines(Font font, Component c, int width) {
        return Math.max(1, font.split(c, Math.max(20, width)).size());
    }

    /** Draws the sheet with its top left at {@code x, y}; returns the height used. */
    public static int draw(GuiGraphics g, Font font, LensSheet s, int x, int y, int width) {
        int cy = y;
        int tx = x;
        if (s.headIcon() != LensSheet.Icon.NONE) {
            TraitBar.drawFlag(g, flag(s.headIcon()), x, cy);
            tx += ICON + ICON_GAP;
        }
        cy = text(g, font, s.title(), tx, cy, width - (tx - x), JournalColors.INK);
        for (Component c : s.header()) {
            cy = text(g, font, c, x, cy, width, JournalColors.INK_FADED);
        }
        if (s.barsTitle().isPresent() || !s.bars().isEmpty()) {
            cy += SECTION_GAP;
        }
        if (s.barsTitle().isPresent()) {
            g.drawString(font, s.barsTitle().get(), x, cy, JournalColors.INK, false);
            g.fill(x, cy + 9, x + Math.min(width, font.width(s.barsTitle().get())), cy + 10, JournalColors.INK_FADED);
            cy += LINE;
        }
        int row = barRow(s);
        for (LensSheet.Bar b : s.bars()) {
            int bx = x + 1;
            TraitBar.drawOnPaper(g, bx, cy, b.step(), b.max(), b.label());
            if (b.hasBand()) {
                int step = TraitBar.SEGMENT_WIDTH + TraitBar.GAP;
                int x0 = bx + (b.bandLo() - 1) * step - 1;
                int x1 = bx + (b.bandHi() - 1) * step + TraitBar.SEGMENT_WIDTH + 1;
                int top = cy + (TraitBar.ROW_HEIGHT - TraitBar.BAR_HEIGHT) / 2 - 1 + TraitBar.BAR_HEIGHT + 2;
                g.fill(x0, top, x1, top + BAND_ROOM, BAND);
            }
            cy += row;
        }
        if (!s.flags().isEmpty()) {
            cy += SECTION_GAP;
            for (LensSheet.Flag f : s.flags()) {
                if (f.icon() != LensSheet.Icon.NONE) {
                    TraitBar.drawFlag(g, flag(f.icon()), x, cy);
                }
                String cut = font.plainSubstrByWidth(f.text().getString(), Math.max(10, width - ICON - ICON_GAP));
                g.drawString(font, cut, x + ICON + ICON_GAP, cy + 1, JournalColors.INK, false);
                cy += LINE;
            }
        }
        if (!s.notes().isEmpty()) {
            cy += SECTION_GAP;
            for (Component c : s.notes()) {
                cy = text(g, font, c, x, cy, width, JournalColors.INK_FADED);
            }
        }
        return cy - y;
    }

    static TraitBar.Flag flag(LensSheet.Icon icon) {
        return TraitBar.Flag.values()[Math.min(icon.ordinal(), TraitBar.Flag.values().length - 1)];
    }

    static int text(GuiGraphics g, Font font, Component c, int x, int y, int width, int colour) {
        List<FormattedCharSequence> split = font.split(c, Math.max(20, width));
        if (split.isEmpty()) {
            return y + LINE;
        }
        for (FormattedCharSequence l : split) {
            g.drawString(font, l, x, y + 1, colour, false);
            y += LINE;
        }
        return y;
    }

    /** A sheet of paper: fill, an ink frame and a faded inner line (the journal's page edge). */
    public static void paper(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PAPER);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, JournalColors.INK);
        g.renderOutline(x0 + 2, y0 + 2, x1 - x0 - 4, y1 - y0 - 4, 0x408A7A62);
    }
}
