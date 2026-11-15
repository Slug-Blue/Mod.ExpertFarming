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
            // a callout wider than the window starts at its left edge (review 1, m8)
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
     * The leader of a moved callout (review 1, m8), as an L that ends on the
     * anchor even when the callout was pushed to the window's edge: down
     * from the callout's lower edge at {@code lx}, then across to the anchor.
     * {@code lx} is the column inside the callout nearest the anchor whose
     * vertical run passes no other callout, if there is one. Returns
     * {@code {lx, top, anchorY, anchorX}}: the vertical runs from
     * {@code (lx, top)} to {@code (lx, anchorY)}, the horizontal from there
     * to {@code (anchorX, anchorY)}. Leaders are drawn before the callouts,
     * so one that cannot avoid a callout runs under it, never over its text.
     */
    public static int[] leader(List<Placed> placed, Placed c, Want w) {
        int lo = c.x() + 3;
        int hi = Math.max(lo, c.x() + c.w() - 4);
        int best = Math.max(lo, Math.min(hi, w.anchorX()));
        int top = c.y() + c.h();
        int bottom = w.anchorY();
        for (int d = 0; d <= hi - lo; d++) {
            for (int lx : new int[] {best - d, best + d}) {
                if (lx >= lo && lx <= hi && !crossesAny(placed, c, lx, top, bottom)) {
                    return new int[] {lx, top, bottom, w.anchorX()};
                }
            }
        }
        return new int[] {best, top, bottom, w.anchorX()};
    }

    /** Whether the vertical {@code x, y0..y1} passes through a callout other than {@code self}. */
    static boolean crossesAny(List<Placed> placed, Placed self, int x, int y0, int y1) {
        int a = Math.min(y0, y1);
        int b = Math.max(y0, y1);
        for (Placed p : placed) {
            if (p != self && x >= p.x() && x < p.x() + p.w() && a < p.y() + p.h() && p.y() < b) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where a {@code size}-px billboard at {@code (x, y)} goes so it covers
     * no callout and stays in {@code [minX, maxX)}: unchanged if it covers
     * none, else the first free spot of right of, left of, above and below
     * the callout it covers; every candidate is checked against all
     * callouts and both edges (review 1, m8). If none is free it stays.
     */
    public static int[] beside(List<Placed> placed, int x, int y, int size, int minX, int maxX) {
        for (Placed p : placed) {
            if (p.overlaps(x, y, size, size)) {
                int[][] candidates = {
                    {p.x() + p.w() + GAP, y},
                    {p.x() - GAP - size, y},
                    {x, p.y() - GAP - size},
                    {x, p.y() + p.h() + GAP},
                };
                for (int[] c : candidates) {
                    if (c[0] >= minX && c[0] + size <= maxX && !overlapsAny(placed, c[0], c[1], size, size)) {
                        return c;
                    }
                }
                return new int[] {x, y};
            }
        }
        return new int[] {x, y};
    }
}
