package io.github.slugblue.wildroots.client.journal.page;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Where a page view draws, in GUI pixels of the screen (journal v2 §3.0,
 * §3.13): a half page is {@value #HALF_WIDTH} × {@value #HEIGHT} (16 lines
 * of 9 px), at the spread's (16, 20) or (156, 20); a page over the whole
 * spread is {@value #SPREAD_WIDTH} × {@value #SPREAD_HEIGHT} at the
 * spread's (12, 18). The header line above belongs to the book.
 *
 * <p>Inside a spread page the art (docs/design/journal-v2-art.md) puts the
 * scene window {@value #SCENE_WINDOW_W} × {@value #SCENE_WINDOW_H} at
 * {@code (x, y + }{@value #SCENE_WINDOW_Y}{@code )} (the frame is drawn on
 * the window grown by 4, so it covers the rect's top 4 px and sticks out 4
 * px left and right), the caption (two lines) at
 * {@code y + }{@value #CAPTION_Y} and the control row (12 px buttons) at
 * {@code y + }{@value #CONTROLS_Y}.
 */
@OnlyIn(Dist.CLIENT)
public record PageRect(int x, int y, int width, int height) {

    public static final int HALF_WIDTH = 116;
    public static final int SPREAD_WIDTH = 264;
    public static final int HEIGHT = 148;
    public static final int SPREAD_HEIGHT = 154;
    public static final int SCENE_WINDOW_W = 264;
    /** 102, not the spec's 112: the caption gets three lines (owner playtest 3: Russian captions need them). */
    public static final int SCENE_WINDOW_H = 102;
    public static final int SCENE_WINDOW_Y = 4;
    public static final int CAPTION_Y = 112;
    /** Lines of the caption under a scene window; a longer one is cut with an ellipsis and shown whole as a tooltip. */
    public static final int CAPTION_LINES = 3;
    public static final int CONTROLS_Y = 141;
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
