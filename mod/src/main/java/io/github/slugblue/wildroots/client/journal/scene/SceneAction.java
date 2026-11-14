package io.github.slugblue.wildroots.client.journal.scene;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One action of a scene step (journal v2 §5.8), {@code at} ticks into the
 * step. Only {@link SetBlock} is permanent and part of the rewind; the
 * others are short effects that play in their own step only.
 */
@OnlyIn(Dist.CLIENT)
public sealed interface SceneAction {

    /** Ticks into the step, {@code 0 ≤ at < duration}. */
    int at();

    /** A cell of the structure. */
    record Cell(int x, int y, int z) {
    }

    /** A point of a path in cell units: (x, y, z) is the centre of that cell; may lie 1 cell outside. */
    record Point(double x, double y, double z) {
    }

    /** Puts a block state (an index of {@link SceneDef#states()}) into a cell; permanent. */
    record SetBlock(int at, Cell pos, int state) implements SceneAction {
    }

    /** An item jumps out of a cell and hangs, bobbing, until the end of the step. */
    record PopItem(int at, Cell pos, ResourceLocation item, Optional<String> variant) implements SceneAction {
    }

    /** A billboard of {@code journal_scene.png} that moves along a path for {@code ticks}. */
    record Icon(int at, IconKind icon, List<Point> path, int ticks) implements SceneAction {

        public Icon {
            path = List.copyOf(path);
        }
    }

    /** The vanilla bee model, without an entity, flying along a path for {@code ticks}. */
    record Bee(int at, List<Point> path, int ticks) implements SceneAction {

        public Bee {
            path = List.copyOf(path);
        }
    }

    /** A callout above a cell with a lang key, and a tooltip key on hover; until the end of the step. */
    record Label(int at, Point pos, String key, Optional<String> tooltip) implements SceneAction {
    }

    /** A frame around the cells {@code from…to}; until the end of the step. */
    record Highlight(int at, Cell from, Cell to) implements SceneAction {
    }

    /** Billboards of {@code journal_scene.png} (art doc): 16 × 16 at (u, v); hands anchor bottom-centre. */
    enum IconKind {
        HAND("hand", 0, 0, true), HAND_LOT("hand_lot", 16, 0, true), HAND_BRUSH("hand_brush", 32, 0, true),
        CLOCK("clock", 48, 0, false), ARROW_LOOP("arrow_loop", 0, 16, false);

        public final String id;
        public final int u;
        public final int v;
        /** Anchored at (+8, +15) instead of the centre. */
        public final boolean bottom;

        IconKind(String id, int u, int v, boolean bottom) {
            this.id = id;
            this.u = u;
            this.v = v;
            this.bottom = bottom;
        }

        public static Optional<IconKind> byId(String id) {
            for (IconKind k : values()) {
                if (k.id.equals(id)) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }
    }
}
