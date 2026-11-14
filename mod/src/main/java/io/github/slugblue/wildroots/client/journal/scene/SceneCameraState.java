package io.github.slugblue.wildroots.client.journal.scene;

import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The camera of a playing scene (journal v2 §3.13, §5.8), pure. A step
 * with a {@code camera} eases to it over {@code ease} ticks when playback
 * reaches the step; a jump puts the camera of that step (the last one set
 * at or before it, else the scene's) at once, so a jump always gives the
 * same picture. Once the reader turns or zooms, the camera is theirs until
 * the step changes.
 */
@OnlyIn(Dist.CLIENT)
public final class SceneCameraState {

    private final SceneCamera base;
    private SceneCamera from;
    private SceneCamera to;
    private int ease;
    private int easeTick;
    private boolean userOwned;

    public SceneCameraState(SceneCamera base) {
        this.base = base;
        this.from = base;
        this.to = base;
    }

    /** The camera that holds at the start of step {@code n} after a jump. */
    public SceneCamera cameraAt(List<SceneStep> steps, int n) {
        for (int i = Math.min(n, steps.size() - 1); i >= 0; i--) {
            if (steps.get(i).camera().isPresent()) {
                return steps.get(i).camera().get().target();
            }
        }
        return base;
    }

    /** Playback reached a new step: ease to its camera, if it has one. */
    public void stepReached(SceneStep step) {
        userOwned = false;
        if (step.camera().isPresent()) {
            from = current(0);
            to = step.camera().get().target();
            ease = step.camera().get().ease();
            easeTick = 0;
        }
    }

    /** A jump to step {@code n}: its camera at once. */
    public void jumped(List<SceneStep> steps, int n) {
        userOwned = false;
        set(cameraAt(steps, n));
    }

    private void set(SceneCamera c) {
        from = c;
        to = c;
        ease = 0;
        easeTick = 0;
    }

    public void tick() {
        if (easeTick < ease) {
            easeTick++;
        }
    }

    /** The camera to draw now. */
    public SceneCamera current(float partialTick) {
        if (ease <= 0 || easeTick >= ease) {
            return to;
        }
        float t = Math.min(1, (easeTick + partialTick) / ease);
        float smooth = t * t * (3 - 2 * t);
        return SceneCamera.lerp(from, to, smooth);
    }

    public void turn(float dYaw, float dPitch) {
        userOwned = true;
        set(current(0).turned(dYaw, dPitch));
    }

    public void zoom(float factor) {
        userOwned = true;
        set(current(0).zoomed(factor));
    }

    /** Reset view (⟲, R, double click): the current step's camera. */
    public void reset(List<SceneStep> steps, int n) {
        jumped(steps, n);
    }

    public boolean userOwned() {
        return userOwned;
    }
}
