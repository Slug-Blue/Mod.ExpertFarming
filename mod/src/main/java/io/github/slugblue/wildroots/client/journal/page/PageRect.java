package io.github.slugblue.wildroots.client.journal.page;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Where a page view draws, in GUI pixels of the screen (journal v2 §3.0,
 * §3.13): a half page is {@value #HALF_WIDTH} × {@value #HEIGHT} (16 lines
 * of 9 px), a page over the whole spread {@value #SPREAD_WIDTH} ×
 * {@value #HEIGHT}. The header line above it belongs to the book.
 */
@OnlyIn(Dist.CLIENT)
public record PageRect(int x, int y, int width, int height) {

    public static final int HALF_WIDTH = 116;
    public static final int SPREAD_WIDTH = 264;
    public static final int HEIGHT = 148;
    public static final int LINE_HEIGHT = 9;
    public static final int LINES = HEIGHT / LINE_HEIGHT;

    public boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + width && my < y + height;
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }
}
