package io.github.slugblue.wildroots.client.journal.scene;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.slugblue.wildroots.client.journal.page.FakeFont;
import io.github.slugblue.wildroots.client.journal.page.LangFixture;
import io.github.slugblue.wildroots.client.journal.page.PageRect;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/**
 * Review m2 of the text-overflow fixes: the scene window is 264 x 102 (it
 * was 112). Over every step of both shipped scenes, at the camera the step
 * starts from, halfway and at its own camera, in both languages, the
 * flat overlays stay inside the window: every callout is placed (none is
 * dropped for lack of room, at most four at once), every highlight box lies
 * inside, and the hand, bee and billboard anchors are inside - so nothing is
 * clipped by the scissor, and nothing reaches the caption below.
 */
class SceneWindowFitTest {

    static final int W = PageRect.SCENE_WINDOW_W;
    static final int H = PageRect.SCENE_WINDOW_H;
    static final int LABEL_H = 13;

    /** Window pixels of a structure point under {@code cam}. */
    static float[] project(SceneDef<String> d, SceneCamera cam, double x, double y, double z) {
        PoseStack p = new PoseStack();
        SceneRenderer.pose(p, SceneRenderer.scaleFor(d.sizeX(), d.sizeY(), d.sizeZ(), cam.zoom(), W, H), cam.pitch(),
                cam.yaw(), W / 2f, H / 2f, d.sizeX(), d.sizeY(), d.sizeZ());
        Matrix4f m = p.last().pose();
        Vector4f v = m.transform(new Vector4f((float) x, (float) y, (float) z, 1));
        return new float[] {v.x(), v.y()};
    }

    /** The cameras a step shows: where it starts from, halfway, its own. */
    static List<SceneCamera> cameras(SceneDef<String> d, int step) {
        SceneCameraState st = new SceneCameraState(d.camera());
        SceneCamera to = st.cameraAt(d.steps(), step);
        SceneCamera from = step == 0 ? to : st.cameraAt(d.steps(), step - 1);
        return List.of(from, SceneCamera.lerp(from, to, 0.5f), to);
    }

    @Test
    void everyOverlayOfEveryStepStaysInsideTheWindow() throws IOException {
        List<String> bad = new ArrayList<>();
        for (String file : CarrotScenesTest.FILES) {
            SceneDef<String> d = CarrotScenesTest.scene(file);
            for (int s = 0; s < d.steps().size(); s++) {
                SceneStep step = d.steps().get(s);
                for (SceneCamera cam : cameras(d, s)) {
                    String where = file + " step " + (s + 1) + " (yaw " + cam.yaw() + ", zoom " + cam.zoom() + ")";
                    for (String lang : LangFixture.LANGS) {
                        callouts(bad, where + " " + lang, d, step, cam, LangFixture.lang(lang));
                    }
                    highlightsAndActors(bad, where, d, step, cam);
                }
            }
        }
        assertTrue(bad.isEmpty(), bad.size() + " overlays leave the window:\n" + String.join("\n", bad));
    }

    static void callouts(List<String> bad, String where, SceneDef<String> d, SceneStep step, SceneCamera cam,
                         Map<String, String> lang) {
        List<CalloutLayout.Want> wants = new ArrayList<>();
        for (SceneAction a : step.actions()) {
            if (a instanceof SceneAction.Label l) {
                float[] s = project(d, cam, l.pos().x() + 0.5, l.pos().y() + 0.3, l.pos().z() + 0.5);
                int w = FakeFont.INSTANCE.width(LangFixture.plain(lang.get(l.key())), false) + 6;
                wants.add(new CalloutLayout.Want(Math.round(s[0]), Math.round(s[1]), w, LABEL_H));
            }
        }
        if (wants.size() > CalloutLayout.MAX_SHOWN) {
            bad.add(where + ": " + wants.size() + " callouts at once, at most " + CalloutLayout.MAX_SHOWN);
        }
        List<CalloutLayout.Placed> placed = CalloutLayout.place(wants, 1, W - 1, 1);
        if (placed.size() < Math.min(wants.size(), CalloutLayout.MAX_SHOWN)) {
            bad.add(where + ": " + (wants.size() - placed.size()) + " of " + wants.size()
                    + " callouts do not find a place in the window");
        }
        for (CalloutLayout.Placed p : placed) {
            if (p.x() < 0 || p.x() + p.w() > W || p.y() < 0 || p.y() + p.h() > H) {
                bad.add(where + ": callout " + p + " leaves the " + W + " x " + H + " window");
            }
        }
    }

    static void highlightsAndActors(List<String> bad, String where, SceneDef<String> d, SceneStep step,
                                    SceneCamera cam) {
        for (SceneAction a : step.actions()) {
            if (a instanceof SceneAction.Highlight h) {
                // the whole cells, a little wider than the plant shapes the game frames
                float x0 = Float.MAX_VALUE;
                float y0 = Float.MAX_VALUE;
                float x1 = -Float.MAX_VALUE;
                float y1 = -Float.MAX_VALUE;
                for (int c = 0; c < 8; c++) {
                    float[] s = project(d, cam, (c & 1) == 0 ? h.from().x() : h.to().x() + 1,
                            (c & 2) == 0 ? h.from().y() : h.to().y() + 1, (c & 4) == 0 ? h.from().z() : h.to().z() + 1);
                    x0 = Math.min(x0, s[0]);
                    y0 = Math.min(y0, s[1]);
                    x1 = Math.max(x1, s[0]);
                    y1 = Math.max(y1, s[1]);
                }
                if (x0 - 2 < 0 || y0 - 2 < 0 || x1 + 2 > W || y1 + 2 > H) {
                    bad.add(where + ": highlight " + h.from() + ".." + h.to() + " is clipped: " + x0 + "," + y0 + " to "
                            + x1 + "," + y1);
                }
            } else if (a instanceof SceneAction.Icon i) {
                for (SceneAction.Point p : i.path()) {
                    inside(bad, where + ": " + i.icon() + " at " + p, project(d, cam, p.x() + 0.5, p.y(), p.z() + 0.5));
                }
            } else if (a instanceof SceneAction.Bee b) {
                for (SceneAction.Point p : b.path()) {
                    inside(bad, where + ": bee at " + p, project(d, cam, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5));
                }
            }
        }
    }

    /** A billboard is 16 px: its anchor must leave room for it on every side. */
    static void inside(List<String> bad, String what, float[] s) {
        if (s[0] < 8 || s[0] > W - 8 || s[1] < 16 || s[1] > H) {
            bad.add(what + " projects to " + s[0] + "," + s[1] + ", outside the window");
        }
    }
}
