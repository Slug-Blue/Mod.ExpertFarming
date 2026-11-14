package io.github.slugblue.wildroots.client.journal.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Playback of one scene (journal v2 §3.13), pure: no game, no rendering.
 *
 * <p><b>Deterministic rewind.</b> The blocks at the start of step N are the
 * structure plus every {@code set_block} of steps 1…N−1 applied at once
 * (precomputed); inside step N, the {@code set_block}s with
 * {@code at ≤ tick} are applied on top. So jumping to any step and tick
 * gives the same blocks as playing up to it, and short effects
 * (pop items, bees, icons) belong only to the current step.
 *
 * <p><b>Playing.</b> Starts at step 1 and plays once: at the end of the
 * last step it stops and {@link #finished()} turns true (no loop). Any
 * jump or seek pauses.
 */
@OnlyIn(Dist.CLIENT)
public final class SceneTimeline {

    private final SceneDef<?> def;
    private final List<SceneStep> steps;
    private final int[][] stepStart;
    private int step;
    private int tick;
    private boolean playing = true;
    private boolean finished;
    private int[] blocks;
    private long blocksKey = Long.MIN_VALUE;

    /**
     * @param opened the opening rule of {@code unlock} lists (§2.3): steps
     *               whose {@code unlock} is closed or whose {@code unless}
     *               is open are left out
     */
    public SceneTimeline(SceneDef<?> def, Predicate<List<String>> opened) {
        this.def = def;
        List<SceneStep> visible = new ArrayList<>();
        for (SceneStep s : def.steps()) {
            if (opened.test(s.unlock()) && (s.unless().isEmpty() || !opened.test(s.unless()))) {
                visible.add(s);
            }
        }
        this.steps = List.copyOf(visible);
        this.stepStart = new int[steps.size()][];
        int[] cur = def.cells();
        for (int i = 0; i < steps.size(); i++) {
            stepStart[i] = cur.clone();
            applySetBlocks(cur, steps.get(i), Integer.MAX_VALUE);
        }
        this.finished = steps.isEmpty();
        this.playing = !steps.isEmpty();
    }

    private void applySetBlocks(int[] cells, SceneStep s, int upTo) {
        for (SceneAction a : s.actions()) {
            if (a.at() > upTo) {
                break;
            }
            if (a instanceof SceneAction.SetBlock b) {
                cells[def.index(b.pos())] = b.state();
            }
        }
    }

    public SceneDef<?> def() {
        return def;
    }

    /** The steps shown to this reader. */
    public List<SceneStep> steps() {
        return steps;
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** Current step, 0-based. */
    public int step() {
        return step;
    }

    public SceneStep current() {
        return steps.get(step);
    }

    /** Ticks into the current step, 0…duration. */
    public int tick() {
        return tick;
    }

    public boolean playing() {
        return playing;
    }

    public boolean finished() {
        return finished;
    }

    /** Effect time in the step: the tick, plus {@code partialTick} while playing. */
    public float time(float partialTick) {
        return playing ? tick + partialTick : tick;
    }

    /** One screen tick. */
    public void advance() {
        if (!playing || steps.isEmpty()) {
            return;
        }
        tick++;
        if (tick >= current().duration()) {
            if (step + 1 < steps.size()) {
                step++;
                tick = 0;
            } else {
                tick = current().duration();
                playing = false;
                finished = true;
            }
        }
    }

    public void play() {
        if (steps.isEmpty()) {
            return;
        }
        if (finished) {
            restart();
            return;
        }
        playing = true;
    }

    public void pause() {
        playing = false;
    }

    public void toggle() {
        if (playing) {
            pause();
        } else {
            play();
        }
    }

    /** From step 1, playing. */
    public void restart() {
        if (steps.isEmpty()) {
            return;
        }
        step = 0;
        tick = 0;
        finished = false;
        playing = true;
    }

    /** To the start of step {@code n} (clamped), paused. */
    public void jump(int n) {
        if (steps.isEmpty()) {
            return;
        }
        step = Math.max(0, Math.min(steps.size() - 1, n));
        tick = 0;
        playing = false;
        finished = false;
    }

    public void next() {
        jump(step + 1);
    }

    public void previous() {
        jump(step - 1);
    }

    /** Moves inside the current step by {@code dt} ticks (clamped), paused. */
    public void seek(int dt) {
        if (steps.isEmpty()) {
            return;
        }
        tick = Math.max(0, Math.min(current().duration(), tick + dt));
        playing = false;
        finished = false;
    }

    /** To a fraction 0…1 of the whole visible timeline, paused (slider drag). */
    public void seekFraction(double f) {
        if (steps.isEmpty()) {
            return;
        }
        int total = totalTicks();
        int t = (int) Math.round(Math.max(0, Math.min(1, f)) * total);
        for (int i = 0; i < steps.size(); i++) {
            int d = steps.get(i).duration();
            if (t < d || i == steps.size() - 1) {
                step = i;
                tick = Math.min(t, d);
                break;
            }
            t -= d;
        }
        playing = false;
        finished = false;
    }

    /** Position in the whole visible timeline, 0…1. */
    public double fraction() {
        if (steps.isEmpty()) {
            return 0;
        }
        int before = 0;
        for (int i = 0; i < step; i++) {
            before += steps.get(i).duration();
        }
        return (before + tick) / (double) totalTicks();
    }

    public int totalTicks() {
        int t = 0;
        for (SceneStep s : steps) {
            t += s.duration();
        }
        return Math.max(1, t);
    }

    /** Start of a step as a fraction of the timeline (slider dots). */
    public double stepFraction(int n) {
        int before = 0;
        for (int i = 0; i < n && i < steps.size(); i++) {
            before += steps.get(i).duration();
        }
        return before / (double) totalTicks();
    }

    /** Blocks at the start of step {@code n}: the structure and steps 1…n−1 at once. */
    public int[] blocksAtStart(int n) {
        return stepStart[n].clone();
    }

    /** Blocks at step {@code n}, tick {@code t}: {@link #blocksAtStart} plus this step's set_block with at ≤ t. */
    public int[] blocksAt(int n, int t) {
        int[] b = stepStart[n].clone();
        applySetBlocks(b, steps.get(n), t);
        return b;
    }

    /**
     * Changes whenever the current blocks change (a new step, or another
     * set_block of this step reached): the mesh rebuilds only then.
     */
    public long blocksKey() {
        if (steps.isEmpty()) {
            return 0;
        }
        int applied = 0;
        for (SceneAction a : current().actions()) {
            if (a.at() > tick) {
                break;
            }
            if (a instanceof SceneAction.SetBlock) {
                applied++;
            }
        }
        return ((long) step << 32) | applied;
    }

    /** The current blocks (cached until {@link #blocksKey()} changes); do not modify. */
    public int[] blocks() {
        if (steps.isEmpty()) {
            return def.cells();
        }
        long key = blocksKey();
        if (blocks == null || key != blocksKey) {
            blocks = blocksAt(step, tick);
            blocksKey = key;
        }
        return blocks;
    }
}
