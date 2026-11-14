package io.github.slugblue.wildroots.client.journal.scene;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A parsed and checked scene (journal v2 §5.8). Immutable. The block
 * states are a table ({@link #states()}, index 0 is air); the structure
 * and every {@code set_block} name an index of it, so the rewind
 * ({@link SceneTimeline}) works on plain {@code int} arrays without the
 * game.
 *
 * @param <S> the block state type: {@code BlockState} in the game, a string in tests
 */
@OnlyIn(Dist.CLIENT)
public final class SceneDef<S> {

    /** How the 2D frame lays out (§3.13): the current step's icons in a row, or one icon per step in a ring. */
    public enum FrameLayout {
        ROW, RING
    }

    private final String id;
    private final Optional<ResourceLocation> species;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<S> states;
    private final int[] cells;
    private final SceneCamera camera;
    private final List<SceneStep> steps;
    private final FrameLayout frameLayout;

    public SceneDef(String id, Optional<ResourceLocation> species, int sizeX, int sizeY, int sizeZ, List<S> states,
                    int[] cells, SceneCamera camera, List<SceneStep> steps, FrameLayout frameLayout) {
        if (cells.length != sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("cells " + cells.length + " for " + sizeX + "×" + sizeY + "×" + sizeZ);
        }
        this.id = id;
        this.species = species;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.states = List.copyOf(states);
        this.cells = cells.clone();
        this.camera = camera;
        this.steps = List.copyOf(steps);
        this.frameLayout = frameLayout;
    }

    public String id() {
        return id;
    }

    public Optional<ResourceLocation> species() {
        return species;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int volume() {
        return cells.length;
    }

    /** Block state table; index 0 is air. */
    public List<S> states() {
        return states;
    }

    /** A copy of the structure: a state index per cell, {@link #index} order. */
    public int[] cells() {
        return cells.clone();
    }

    public SceneCamera camera() {
        return camera;
    }

    public List<SceneStep> steps() {
        return steps;
    }

    public FrameLayout frameLayout() {
        return frameLayout;
    }

    /** Index of a cell in {@link #cells()}: x fastest, then z, then y. */
    public int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    public int index(SceneAction.Cell c) {
        return index(c.x(), c.y(), c.z());
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    /** The 2D frame data of every step, for a fallback drawn outside this package (WR-181d). */
    public FrameData frameData() {
        return new FrameData(id, frameLayout, steps.stream().map(s -> new FrameData.Step(s.caption(), s.frame(),
                s.unlock(), s.unless())).toList());
    }

    /**
     * What the 2D fallback frame needs (§3.13): per step its caption key
     * and frame icons, and the layout. No game state.
     */
    public record FrameData(String scene, FrameLayout layout, List<Step> steps) {

        public FrameData {
            steps = List.copyOf(steps);
        }

        public record Step(String caption, List<SceneStep.FrameIcon> icons, List<String> unlock, List<String> unless) {
        }
    }
}
