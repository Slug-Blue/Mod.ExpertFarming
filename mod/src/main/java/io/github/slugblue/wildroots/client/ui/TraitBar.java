package io.github.slugblue.wildroots.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A trait bar in the style of the experience bar (D2, 2026-11-11; shared
 * with the journal v2): {@value #SEGMENTS} segments, the step shown by how
 * many are filled (never by colour), then the label to the right. The lens
 * steps are the word scale of {@code lens.LensScale} (bounds 0.20 / 0.35 /
 * 0.55 / 0.75). Draws only what it is given.
 */
public final class TraitBar {

    public static final int SEGMENTS = 5;
    public static final int SEGMENT_WIDTH = 6;
    public static final int GAP = 1;
    public static final int BAR_HEIGHT = 5;
    /** Height of one row: the label line. */
    public static final int ROW_HEIGHT = 10;
    /** Space between the bar and its label. */
    public static final int LABEL_GAP = 4;

    static final int FILLED = 0xFF80FF20;
    static final int EMPTY = 0xFF2A2A2A;
    static final int FRAME = 0xFF000000;
    static final int TEXT = 0xFFE0E0E0;

    private TraitBar() {
    }

    /** Width of a bar of {@code segments} segments, without the label. */
    public static int barWidth(int segments) {
        int n = Math.max(1, segments);
        return n * SEGMENT_WIDTH + (n - 1) * GAP;
    }

    /** Width of a whole row: bar, gap and label. */
    public static int width(Font font, int segments, Component label) {
        return barWidth(segments) + LABEL_GAP + font.width(label);
    }

    /** A {@value #SEGMENTS}-segment bar with {@code filled} segments filled and the label after it. */
    public static void draw(GuiGraphics g, int x, int y, int filled, Component label) {
        draw(g, x, y, filled, SEGMENTS, label);
    }

    /** Same with another number of segments (the 4 grades of produce). */
    public static void draw(GuiGraphics g, int x, int y, int filled, int segments, Component label) {
        int n = Math.max(1, segments);
        int f = Math.max(0, Math.min(n, filled));
        int top = y + (ROW_HEIGHT - BAR_HEIGHT) / 2 - 1;
        g.fill(x - 1, top - 1, x + barWidth(n) + 1, top + BAR_HEIGHT + 1, FRAME);
        for (int i = 0; i < n; i++) {
            int sx = x + i * (SEGMENT_WIDTH + GAP);
            g.fill(sx, top, sx + SEGMENT_WIDTH, top + BAR_HEIGHT, i < f ? FILLED : EMPTY);
        }
        Font font = Minecraft.getInstance().font;
        g.drawString(font, label, x + barWidth(n) + LABEL_GAP, y, TEXT);
    }
}
