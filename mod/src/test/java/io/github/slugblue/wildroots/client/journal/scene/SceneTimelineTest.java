package io.github.slugblue.wildroots.client.journal.scene;

import static io.github.slugblue.wildroots.client.journal.scene.SceneFixtures.scene;
import static io.github.slugblue.wildroots.client.journal.scene.SceneFixtures.setBlock;
import static io.github.slugblue.wildroots.client.journal.scene.SceneFixtures.step;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * WR-181k: deterministic rewind (journal v2 §3.13) on the block array
 * without rendering: the blocks after playing up to step N, tick t equal
 * the blocks after jumping there, for every N and t; playing runs once.
 */
class SceneTimelineTest {

    static final Predicate<List<String>> ALL_OPEN = ids -> true;

    static SceneDef<String> threeSteps() {
        return SceneFixtures.load(scene(
                step(20, setBlock(5, 1, 1, 1, "wildroots:wild_carrot[stage=1,size=1]") + ", "
                        + setBlock(12, 0, 1, 0, "wildroots:wild_carrot[stage=0,size=1]"))
                        + ", " + step(15, setBlock(0, 1, 1, 1, "wildroots:wild_carrot[stage=3,size=2]") + ", "
                        + setBlock(14, 0, 1, 0, "minecraft:air"))
                        + ", " + step(10, setBlock(3, 2, 1, 2, "wildroots:wild_carrot[stage=5,size=2]")))).only();
    }

    /** Plays a fresh timeline up to (n, t) tick by tick. */
    static SceneTimeline playedTo(SceneDef<?> def, Predicate<List<String>> opened, int n, int t) {
        SceneTimeline tl = new SceneTimeline(def, opened);
        while (tl.step() != n || tl.tick() != t) {
            assertTrue(tl.playing(), "stopped before " + n + "/" + t);
            tl.advance();
        }
        return tl;
    }

    static void assertDeterministic(SceneDef<?> def, Predicate<List<String>> opened) {
        SceneTimeline probe = new SceneTimeline(def, opened);
        for (int n = 0; n < probe.steps().size(); n++) {
            for (int t = 0; t < probe.steps().get(n).duration(); t++) {
                SceneTimeline played = playedTo(def, opened, n, t);
                SceneTimeline jumped = new SceneTimeline(def, opened);
                jumped.jump(probe.steps().size() - 1 - n); // somewhere else first
                jumped.jump(n);
                jumped.seek(t);
                assertArrayEquals(played.blocks(), jumped.blocks(), "step " + (n + 1) + " tick " + t);
                assertEquals(played.blocksKey(), jumped.blocksKey(), "key at step " + (n + 1) + " tick " + t);
                assertArrayEquals(played.blocks(), probe.blocksAt(n, t));
            }
            if (n > 0) {
                // the start of step n is the end of step n − 1 with all its set_block (at 0 of step n not yet)
                assertArrayEquals(probe.blocksAt(n - 1, Integer.MAX_VALUE), probe.blocksAtStart(n),
                        "start of " + (n + 1));
            }
        }
    }

    @Test
    void playingEqualsJumping() {
        assertDeterministic(threeSteps(), ALL_OPEN);
    }

    @Test
    void startOfStepIsStructurePlusEarlierSetBlocks() {
        SceneDef<String> d = threeSteps();
        SceneTimeline tl = new SceneTimeline(d, ALL_OPEN);
        assertArrayEquals(d.cells(), tl.blocksAtStart(0));
        int[] s2 = tl.blocksAtStart(2);
        assertEquals("wildroots:wild_carrot[stage=3,size=2]", d.states().get(s2[d.index(1, 1, 1)]));
        assertEquals("minecraft:air", d.states().get(s2[d.index(0, 1, 0)]));
    }

    @Test
    void playsOnceAndStops() {
        SceneTimeline tl = new SceneTimeline(threeSteps(), ALL_OPEN);
        assertTrue(tl.playing());
        for (int i = 0; i < 1000; i++) {
            tl.advance();
        }
        assertFalse(tl.playing());
        assertTrue(tl.finished());
        assertEquals(2, tl.step());
        assertEquals(10, tl.tick());
        tl.play();
        assertTrue(tl.playing());
        assertEquals(0, tl.step(), "play after the end starts again");
    }

    @Test
    void touchingPauses() {
        SceneTimeline tl = new SceneTimeline(threeSteps(), ALL_OPEN);
        tl.jump(1);
        assertFalse(tl.playing());
        tl.play();
        tl.seek(3);
        assertFalse(tl.playing());
        tl.play();
        tl.seekFraction(0.5);
        assertFalse(tl.playing());
        tl.jump(99);
        assertEquals(2, tl.step(), "jump clamps");
        tl.jump(-5);
        assertEquals(0, tl.step());
    }

    @Test
    void keyChangesOnlyWithTheBlocks() {
        SceneTimeline tl = new SceneTimeline(threeSteps(), ALL_OPEN);
        long k0 = tl.blocksKey();
        for (int i = 0; i < 4; i++) {
            tl.advance();
        }
        assertEquals(k0, tl.blocksKey(), "no set_block yet");
        tl.advance();
        assertNotEquals(k0, tl.blocksKey(), "set_block at 5");
    }

    @Test
    void seekFractionCoversTheTimeline() {
        SceneTimeline tl = new SceneTimeline(threeSteps(), ALL_OPEN);
        tl.seekFraction(0);
        assertEquals(0, tl.step());
        assertEquals(0, tl.tick());
        tl.seekFraction(1);
        assertEquals(2, tl.step());
        tl.seekFraction(25 / 45.0);
        assertEquals(1, tl.step());
        assertEquals(5, tl.tick());
        assertEquals(25 / 45.0, tl.fraction(), 1e-9);
    }

    @Test
    void unlockAndUnlessPickTheVariant() {
        String json = scene(step(POP_ONE) + ", "
                + step(POP_ONE).replace("\"actions\"", "\"unless\": [\"tool.pollination_brush\"], \"actions\"") + ", "
                + step(POP_ONE).replace("\"actions\"", "\"unlock\": [\"tool.pollination_brush\"], \"actions\""));
        SceneDef<String> d = SceneFixtures.load(json).only();
        Set<String> none = Set.of();
        Set<String> brush = Set.of("tool.pollination_brush");
        SceneTimeline without = new SceneTimeline(d, ids -> ids.isEmpty() || ids.stream().anyMatch(none::contains));
        SceneTimeline with = new SceneTimeline(d, ids -> ids.isEmpty() || ids.stream().anyMatch(brush::contains));
        assertEquals(2, without.steps().size());
        assertTrue(without.steps().get(1).unless().contains("tool.pollination_brush"));
        assertEquals(2, with.steps().size());
        assertTrue(with.steps().get(1).unlock().contains("tool.pollination_brush"));
    }

    static final String POP_ONE = SceneFixtures.POP;

    @Test
    void cameraJumpIsTheSameWhateverTheWay() {
        String json = scene(step(POP_ONE) + ", "
                + step(POP_ONE).replace("\"actions\"", "\"camera\": {\"yaw\": 90, \"pitch\": 50, \"ease\": 10}, \"actions\"")
                + ", " + step(POP_ONE));
        SceneDef<String> d = SceneFixtures.load(json).only();
        SceneCameraState a = new SceneCameraState(d.camera());
        SceneCameraState b = new SceneCameraState(d.camera());
        a.jumped(d.steps(), 2);
        b.jumped(d.steps(), 0);
        b.turn(33, 12);
        b.jumped(d.steps(), 2);
        assertEquals(a.current(0), b.current(0));
        assertEquals(90f, a.current(0).yaw(), 1e-4);
        assertEquals(50f, a.current(0).pitch(), 1e-4);
        assertEquals(d.camera().zoom(), a.current(0).zoom(), 1e-4);
    }

    @Test
    void cameraEasesAndTheReaderTakesOver() {
        SceneCameraState c = new SceneCameraState(new SceneCamera(0, 30, 1));
        c.stepReached(new SceneStep("k", 40, List.of(), List.of(),
                java.util.Optional.of(new SceneStep.Move(new SceneCamera(90, 30, 1), 10)), List.of(), List.of()));
        assertEquals(0f, c.current(0).yaw(), 1e-4);
        for (int i = 0; i < 5; i++) {
            c.tick();
        }
        float mid = c.current(0).yaw();
        assertTrue(mid > 0 && mid < 90, "mid " + mid);
        c.turn(10, 100);
        assertTrue(c.userOwned());
        assertEquals(SceneLimits.MAX_PITCH, c.current(0).pitch(), 1e-4, "pitch clamps");
        for (int i = 0; i < 20; i++) {
            c.tick();
        }
        assertEquals(mid + 10, c.current(0).yaw(), 1e-3, "the reader's camera stays");
    }

    @Test
    void motionIsAFunctionOfTime() {
        List<SceneAction.Point> path = List.of(new SceneAction.Point(0, 0, 0), new SceneAction.Point(2, 0, 0));
        assertArrayEquals(new double[] {0.5, 0.5, 0.5}, SceneMotion.along(path, 0), 1e-9);
        assertArrayEquals(new double[] {1.5, 0.5, 0.5}, SceneMotion.along(path, 0.5f), 1e-6);
        assertArrayEquals(new double[] {2.5, 0.5, 0.5}, SceneMotion.along(path, 1), 1e-9);
        SceneAction.Cell c = new SceneAction.Cell(1, 1, 1);
        assertArrayEquals(SceneMotion.pop(c, 0, 2, 17.5f), SceneMotion.pop(c, 0, 2, 17.5f));
    }

    /** Review m7: a new layout of the same page takes back the step, tick and play state. */
    @Test
    void aSavedStateComesBack() {
        SceneTimeline t = new SceneTimeline(threeSteps(), ALL_OPEN);
        t.jump(1);
        t.seek(7);
        t.play();
        SceneTimeline.State s = t.state();
        SceneTimeline again = new SceneTimeline(threeSteps(), ALL_OPEN);
        assertEquals(0, again.step());
        again.restore(s);
        assertEquals(s, again.state());
        assertArrayEquals(t.blocks(), again.blocks(), "the same blocks after the restore");
        again.restore(new SceneTimeline.State(99, 9999, false, false));
        assertEquals(again.steps().size() - 1, again.step(), "a step past the end is clamped");
        assertTrue(again.tick() <= again.current().duration());
    }
}
