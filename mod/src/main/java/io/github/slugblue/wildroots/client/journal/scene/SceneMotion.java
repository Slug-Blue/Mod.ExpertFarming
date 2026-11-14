package io.github.slugblue.wildroots.client.journal.scene;

import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Where the short effects are at a time (journal v2 §5.8), in structure
 * coordinates (a cell spans {@code [x, x + 1]}), pure. Every position
 * depends only on the step time, so pausing freezes and a jump replays the
 * same motion.
 */
@OnlyIn(Dist.CLIENT)
public final class SceneMotion {

    /** Ticks a popped item takes to arc up to where it hangs. */
    public static final int POP_TICKS = 10;

    private SceneMotion() {
    }

    /** A point {@code t} (0…1) along a path, evenly by length; a path point is a cell centre. */
    public static double[] along(List<SceneAction.Point> path, float t) {
        if (path.size() == 1 || t <= 0) {
            return centre(path.get(0));
        }
        if (t >= 1) {
            return centre(path.get(path.size() - 1));
        }
        double total = 0;
        for (int i = 1; i < path.size(); i++) {
            total += dist(path.get(i - 1), path.get(i));
        }
        if (total <= 1e-9) {
            return centre(path.get(0));
        }
        double want = t * total;
        for (int i = 1; i < path.size(); i++) {
            double d = dist(path.get(i - 1), path.get(i));
            if (want <= d || i == path.size() - 1) {
                double k = d <= 1e-9 ? 0 : Math.min(1, want / d);
                SceneAction.Point a = path.get(i - 1);
                SceneAction.Point b = path.get(i);
                return new double[] {a.x() + (b.x() - a.x()) * k + 0.5, a.y() + (b.y() - a.y()) * k + 0.5,
                        a.z() + (b.z() - a.z()) * k + 0.5};
            }
            want -= d;
        }
        return centre(path.get(path.size() - 1));
    }

    /** Direction of travel at {@code t} as a yaw in degrees (0 = +z), for the bee. */
    public static float heading(List<SceneAction.Point> path, float t) {
        double[] a = along(path, Math.max(0, t - 0.02f));
        double[] b = along(path, Math.min(1, t + 0.02f));
        double dx = b[0] - a[0];
        double dz = b[2] - a[2];
        if (dx * dx + dz * dz < 1e-8) {
            return 0;
        }
        return (float) Math.toDegrees(Math.atan2(dx, dz));
    }

    /**
     * A popped item: from the top of its cell, an arc up and aside to
     * where it hangs, then a slow bob. {@code index} of {@code count}
     * items of the same cell spreads them side by side.
     */
    public static double[] pop(SceneAction.Cell c, int index, int count, float dt) {
        double side = (index - (count - 1) / 2.0) * 0.55;
        double sx = c.x() + 0.5;
        double sy = c.y() + 0.6;
        double sz = c.z() + 0.5;
        double hy = c.y() + 1.6;
        if (dt < POP_TICKS) {
            double k = Math.max(0, dt) / POP_TICKS;
            double arc = 4 * k * (1 - k) * 0.5;
            return new double[] {sx + side * k, sy + (hy - sy) * k + arc, sz};
        }
        double bob = Math.sin((dt - POP_TICKS) * 0.15) * 0.06;
        return new double[] {sx + side, hy + bob, sz};
    }

    private static double[] centre(SceneAction.Point p) {
        return new double[] {p.x() + 0.5, p.y() + 0.5, p.z() + 0.5};
    }

    private static double dist(SceneAction.Point a, SceneAction.Point b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
