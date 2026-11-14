package io.github.slugblue.wildroots.client.journal.scene;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.journal.book.JournalAtlas;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The scene controls (journal v2 §3.13): 12 px sprite buttons of the art
 * and the step slider. They are screen widgets, so Tab, Enter and
 * narration work as for every other button of the book.
 */
@OnlyIn(Dist.CLIENT)
final class SceneWidgets {

    private SceneWidgets() {
    }

    static void focusFrame(GuiGraphics g, int x, int y, int w, int h) {
        JournalAtlas.nineSlice(g, JournalAtlas.FOCUS, JournalAtlas.FOCUS_BORDER, x, y, w, h);
    }

    /** A scene button: its sprite and tooltip may change (play ↔ pause), {@code ACTIVE} draws a toggle that is on. */
    static final class Button extends AbstractButton {

        private JournalAtlas.Button sprite;
        private final Runnable action;
        private final Component label;
        private BooleanSupplier on = () -> false;

        Button(int x, int y, JournalAtlas.Button sprite, Component tooltip, Component label, Runnable action) {
            super(x, y, 12 + (label == null ? 0 : 2 + Minecraft.getInstance().font.width(label)), 12, tooltip);
            this.sprite = sprite;
            this.action = action;
            this.label = label;
            setTooltip(Tooltip.create(tooltip));
        }

        Button toggle(BooleanSupplier on) {
            this.on = on;
            return this;
        }

        /** Changes sprite and tooltip (only when they differ, so the tooltip is not rebuilt per frame). */
        void set(JournalAtlas.Button sprite, Component tooltip) {
            if (this.sprite != sprite) {
                this.sprite = sprite;
                setMessage(tooltip);
                setTooltip(Tooltip.create(tooltip));
            }
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            JournalAtlas.State state = !active ? JournalAtlas.State.DISABLED
                    : on.getAsBoolean() ? JournalAtlas.State.ACTIVE
                    : isHoveredOrFocused() ? JournalAtlas.State.HOVERED : JournalAtlas.State.NORMAL;
            JournalAtlas.blit(g, sprite.sprite(state), getX(), getY());
            if (label != null) {
                g.drawString(Minecraft.getInstance().font, label, getX() + 14, getY() + 2,
                        active ? JournalColors.INK : JournalColors.INK_FADED, false);
            }
            if (active && isFocused()) {
                focusFrame(g, getX(), getY(), width, height);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** What the slider shows and moves. */
    interface SliderModel {

        int steps();

        int step();

        /** Position on the timeline, 0…1. */
        double fraction();

        double stepFraction(int n);

        void jump(int n);

        void seekFraction(double f);
    }

    /**
     * The step slider: a dot per step (past, current, ahead), the track
     * filled up to the position, a handle. Click a dot to jump, drag to
     * move through the timeline; ← → (when focused) step.
     */
    static final class Slider extends AbstractWidget {

        private final SliderModel model;

        Slider(int x, int y, int w, SliderModel model) {
            super(x, y, w, 12, Component.empty());
            this.model = model;
        }

        private int dotX(int n) {
            int n1 = Math.max(1, model.steps() - 1);
            int span = width - 7;
            return getX() + (model.steps() == 1 ? span / 2 : (int) Math.round(span * n / (double) n1));
        }

        private int dotAt(double mx) {
            for (int i = 0; i < model.steps(); i++) {
                int x = dotX(i);
                if (mx >= x - 1 && mx < x + 8) {
                    return i;
                }
            }
            return -1;
        }

        /** Pixel position of a timeline fraction: between the dots of its step. */
        private int xOf(double f) {
            int n = model.steps();
            for (int i = 0; i < n; i++) {
                double a = model.stepFraction(i);
                double b = i + 1 < n ? model.stepFraction(i + 1) : 1;
                if (f < b || i == n - 1) {
                    double k = b > a ? (f - a) / (b - a) : 0;
                    int xa = dotX(i) + 3;
                    int xb = i + 1 < n ? dotX(i + 1) + 3 : xa;
                    return (int) Math.round(xa + (xb - xa) * Math.max(0, Math.min(1, k)));
                }
            }
            return getX();
        }

        private double fractionAt(double mx) {
            int n = model.steps();
            for (int i = 0; i + 1 < n; i++) {
                int xa = dotX(i) + 3;
                int xb = dotX(i + 1) + 3;
                if (mx < xb || i + 2 == n) {
                    double k = xb > xa ? (mx - xa) / (double) (xb - xa) : 0;
                    double a = model.stepFraction(i);
                    double b = model.stepFraction(i + 1);
                    return a + (b - a) * Math.max(0, Math.min(1, k));
                }
            }
            return 0;
        }

        @Override
        public void onClick(double mx, double my) {
            int dot = dotAt(mx);
            if (dot >= 0) {
                model.jump(dot);
            } else {
                model.seekFraction(fractionAt(mx));
            }
        }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) {
            model.seekFraction(fractionAt(mx));
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            if (!isFocused()) {
                return false;
            }
            if (key == GLFW.GLFW_KEY_LEFT) {
                model.jump(model.step() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                model.jump(model.step() + 1);
                return true;
            }
            return false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int n = model.steps();
            if (n <= 0) {
                return;
            }
            int trackY = getY() + 5;
            int first = dotX(0) + 3;
            int last = dotX(n - 1) + 3;
            int pos = xOf(model.fraction());
            tile(g, JournalAtlas.SLIDER_TRACK, first, last, trackY);
            tile(g, JournalAtlas.SLIDER_TRACK_FILLED, first, Math.min(pos, last), trackY);
            int hover = isHovered() ? dotAt(mouseX) : -1;
            for (int i = 0; i < n; i++) {
                JournalAtlas.Sprite s = i == hover ? JournalAtlas.SLIDER_DOT_HOVER
                        : i < model.step() ? JournalAtlas.SLIDER_DOT_PAST
                        : i == model.step() ? JournalAtlas.SLIDER_DOT : JournalAtlas.SLIDER_DOT_FUTURE;
                JournalAtlas.blit(g, s, dotX(i), getY() + 2);
            }
            JournalAtlas.Sprite thumb = isHoveredOrFocused() ? JournalAtlas.SLIDER_THUMB.offset(6, 0)
                    : JournalAtlas.SLIDER_THUMB;
            JournalAtlas.blit(g, thumb, pos - 2, getY() + 1);
            if (isFocused()) {
                focusFrame(g, getX() - 1, getY(), width + 2, height);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(NarratedElementType.TITLE, Component.translatable("wildroots.journal.ui.step", model.step() + 1,
                    model.steps()));
        }

        /** Tiles a track sprite over {@code [from, to)}. */
        private static void tile(GuiGraphics g, JournalAtlas.Sprite s, int from, int to, int y) {
            for (int x = from; x < to; x += s.width()) {
                JournalAtlas.blit(g, s, x, y, Math.min(s.width(), to - x), s.height());
            }
        }
    }
}
