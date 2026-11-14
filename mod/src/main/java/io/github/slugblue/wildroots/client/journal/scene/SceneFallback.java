package io.github.slugblue.wildroots.client.journal.scene;

import io.github.slugblue.wildroots.client.journal.page.PageContext;
import io.github.slugblue.wildroots.client.journal.page.PageRect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The 2D frame a scene page draws in its window when it is not in 3D
 * (journal v2 §3.13): the reader pressed {@code 2D}, or drawing threw.
 * The scene page keeps its own caption, slider and buttons and only hands
 * the window over, keeping the frame on the same step.
 *
 * <p>Client setup may install a {@link Factory} (WR-181d's
 * {@code FallbackFrameView}); without one, or when it gives {@code null},
 * the scene page draws its own built-in frame from the parsed scene.
 */
@OnlyIn(Dist.CLIENT)
public interface SceneFallback {

    /** Keeps the frame on the scene's step (0-based, of the steps shown to this reader). */
    void setStep(int step);

    /** Draws the frame inside the scene window {@code (x, y, w, h)}; no caption, no controls. */
    void renderWindow(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick);

    /**
     * A click in the window last drawn by {@link #renderWindow}: a ring icon
     * picks its step, a frame item opens its entry. Whether it was taken.
     */
    default boolean mouseClicked(double mouseX, double mouseY) {
        return false;
    }

    /** The frame's step after a click (0-based, of the steps shown to this reader). */
    default int step() {
        return -1;
    }

    /** Creates the frame of one scene page; {@code null} = use the built-in frame. */
    @FunctionalInterface
    interface Factory {

        SceneFallback create(PageRect rect, String sceneId, PageContext ctx);
    }

    /** The installed factory; client setup only. */
    final class Install {

        private static volatile Factory factory = (rect, id, ctx) -> null;

        private Install() {
        }

        public static void set(Factory f) {
            factory = f == null ? (rect, id, ctx) -> null : f;
        }

        static SceneFallback create(PageRect rect, String sceneId, PageContext ctx) {
            try {
                return factory.create(rect, sceneId, ctx);
            } catch (RuntimeException e) {
                return null;
            }
        }
    }
}
