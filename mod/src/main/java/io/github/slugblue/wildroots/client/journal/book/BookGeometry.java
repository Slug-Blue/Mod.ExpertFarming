package io.github.slugblue.wildroots.client.journal.book;

import io.github.slugblue.wildroots.client.journal.page.PageRect;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Where everything of the book sits on the screen (journal v2 §3.0,
 * §5.3): the spread {@value #SPREAD_W} × {@value #SPREAD_H}, the tabs
 * sticking out {@value #TABS_OUT} px on the left, the navigation strip
 * {@value #NAV_H} px under it; {@value #TOTAL_W} × {@value #TOTAL_H}
 * in all, centred. That fits the smallest window Minecraft allows at any
 * GUI scale (320 × 240), so there is no narrow mode.
 *
 * @param x left edge of the spread
 * @param y top edge of the spread
 */
@OnlyIn(Dist.CLIENT)
public record BookGeometry(int x, int y) {

    public static final int SPREAD_W = 288;
    public static final int SPREAD_H = 180;
    public static final int TABS_OUT = 12;
    public static final int NAV_H = 16;
    public static final int TOTAL_W = SPREAD_W + TABS_OUT;
    public static final int TOTAL_H = SPREAD_H + NAV_H + 4;
    /** Half of the spread, spine included. */
    public static final int HALF_W = SPREAD_W / 2;
    /** Text inset of a page from its half's edge. */
    public static final int INSET = 14;
    /** Inset of a page over the whole spread. */
    public static final int SPREAD_INSET = 12;
    public static final int HEADER_Y = 7;
    public static final int CONTENT_Y = 20;
    public static final int TAB_H = 20;
    public static final int MAX_TABS = 8;
    public static final int BUTTON = 16;

    public static BookGeometry center(int screenWidth, int screenHeight) {
        int left = Math.max(0, (screenWidth - TOTAL_W) / 2);
        int top = Math.max(0, (screenHeight - TOTAL_H) / 2);
        return new BookGeometry(left + TABS_OUT, top);
    }

    public PageRect left() {
        return new PageRect(x + INSET, y + CONTENT_Y, PageRect.HALF_WIDTH, PageRect.HEIGHT);
    }

    public PageRect right() {
        return new PageRect(x + HALF_W + INSET, y + CONTENT_Y, PageRect.HALF_WIDTH, PageRect.HEIGHT);
    }

    public PageRect spread() {
        return new PageRect(x + SPREAD_INSET, y + CONTENT_Y, PageRect.SPREAD_WIDTH, PageRect.HEIGHT);
    }

    /** Top of the navigation strip. */
    public int navY() {
        return y + SPREAD_H;
    }

    public int headerY() {
        return y + HEADER_Y;
    }

    /** Top of tab {@code i} (0-based). */
    public int tabY(int i) {
        return y + 8 + i * TAB_H;
    }

    public int tabX() {
        return x - TABS_OUT;
    }

    public boolean overBook(double mx, double my) {
        return mx >= x && my >= y && mx < x + SPREAD_W && my < y + SPREAD_H;
    }
}
