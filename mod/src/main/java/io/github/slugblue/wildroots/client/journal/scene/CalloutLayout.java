package io.github.slugblue.wildroots.client.journal.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Places the callouts of a scene step so that none overlaps another
 * (owner playtest 2, 6.png): each wants to sit centred above its anchor;
 * one that would overlap an already placed callout moves up a row, and is
 * drawn with a leader line down to its anchor. At most {@link #MAX_SHOWN}
 * callouts at once; billboards that would cover a callout move beside it.
 * Pure (GUI pixels in, rectangles out), so it is tested without a game.
 */
@OnlyIn(Dist.CLIENT)
public final class CalloutLayout {

    /** Most callouts shown at once; the rest wait (§5.8 allows 8 in the file, the window holds about 4). */
    public static final int MAX_SHOWN = 4;
    /** Space kept between callouts and between a callout and the billboard moved beside it. */
    public static final int GAP = 2;
    /** Rows a callout may climb before it gives up and is not shown. */
    static final int MAX_ROWS = 6;

    /** A wanted callout: its anchor (the tail's tip) and its size. */
    public record Want(int anchorX, int anchorY, int w, int h) {
    }

    /** A placed callout; {@code moved}: not straight above its anchor, so it needs a leader line. */
    public record Placed(int index, int x, int y, int w, int h, boolean moved) {

        public boolean overlaps(int ox, int oy, int ow, int oh) {
            return x < ox + ow + GAP && ox < x + w + GAP && y < oy + oh + GAP && oy < y + h + GAP;
        }
    }

    private CalloutLayout() {
    }

    /**
     * Places up to {@link #MAX_SHOWN} callouts (in order of {@code wants},
     * the step's order), left to right, kept inside {@code [minX, maxX)}.
     */
    public static List<Placed> place(List<Want> wants, int minX, int maxX, int minY) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < wants.size() && order.size() < MAX_SHOWN; i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(i -> wants.get(i).anchorX()));
        List<Placed> out = new ArrayList<>();
        for (int i : order) {
            Want w = wants.get(i);
            int x = Math.max(minX, Math.min(maxX - w.w(), w.anchorX() - w.w() / 2));
            int y = w.anchorY() - 3 - w.h();
            int rows = 0;
            while (overlapsAny(out, x, y, w.w(), w.h()) && rows < MAX_ROWS) {
                y -= w.h() + GAP;
                rows++;
            }
            if (y < minY || overlapsAny(out, x, y, w.w(), w.h())) {
                continue;
            }
            out.add(new Placed(i, x, y, w.w(), w.h(), rows > 0));
        }
        return out;
    }

    static boolean overlapsAny(List<Placed> placed, int x, int y, int w, int h) {
        for (Placed p : placed) {
            if (p.overlaps(x, y, w, h)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where a {@code size}-px billboard at {@code (x, y)} goes so it covers
     * no callout: unchanged if it covers none, else just right of the
     * callout it covers (or left, if right leaves the window).
     */
    public static int[] beside(List<Placed> placed, int x, int y, int size, int maxX) {
        for (Placed p : placed) {
            if (p.overlaps(x, y, size, size)) {
                int right = p.x() + p.w() + GAP;
                int nx = right + size <= maxX ? right : p.x() - GAP - size;
                return new int[] {nx, y};
            }
        }
        return new int[] {x, y};
    }
}
