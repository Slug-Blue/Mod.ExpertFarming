package io.github.slugblue.wildroots.client.journal.page;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.journal.book.BookWidgets;
import io.github.slugblue.wildroots.client.journal.book.JournalAtlas;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/**
 * The 2D fallback frame of a scene over the spread (journal v2 §3.13;
 * WR-181d): in the scene window, the step's icons in a row or every step
 * around a ring with arrows and the current step highlighted; under it
 * the step's caption ("Step n of m. …") and the step slider with
 * ⏮ / ⏭. {@code [} {@code ]} step without focus, ← → with focus; a click on
 * a dot goes to that step. Steps whose {@code unlock} is closed are left
 * out. When no step has an icon, the window lists the captions as text.
 *
 * <p>The scene engine (WR-181k) shows this view when a scene is refused,
 * its renderer failed, {@code journalScenes = false} or the reader pressed
 * 2D; it can embed it as is ({@link #step()} / {@link #setStep(int)} keep
 * the two modes on the same step).
 */
@OnlyIn(Dist.CLIENT)
public final class FallbackFrameView implements PageView {

    static final int ICON_MAX = 32;
    static final int ARROW_GAP = 14;
    static final int SLIDER_X = 34;

    /** One shown icon: item stack or sprite. */
    record Shown(ItemStack stack, TextureAtlasSprite sprite) {
    }

    private final PageRect rect;
    private final SceneFrame.Layout layout;
    private final List<SceneFrame.Step> steps;
    private final List<List<Shown>> icons = new ArrayList<>();
    private final List<List<FormattedCharSequence>> captions = new ArrayList<>();
    private final boolean textOnly;
    private final List<FormattedCharSequence> captionList = new ArrayList<>();
    private final PageContext ctx;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private int step;

    /** Pages for a scene id: the frame of {@link SceneFrames}, or no page if the file did not load. */
    public static List<LaidOutPage> layout(String sceneId, PageContext ctx) {
        return SceneFrames.get(sceneId).map(f -> visibleSteps(f, ctx).isEmpty() ? List.<LaidOutPage>of()
                : List.of(LaidOutPage.spread(rect -> new FallbackFrameView(rect, f, ctx)))).orElse(List.of());
    }

    /** Steps shown to this reader, by the engine's rule: {@code unlock} open, and {@code unless} (if any) not. */
    static List<SceneFrame.Step> visibleSteps(SceneFrame f, PageContext ctx) {
        return visibleSteps(f, ctx.unlocks()::opened);
    }

    static List<SceneFrame.Step> visibleSteps(SceneFrame f, java.util.function.Predicate<List<String>> opened) {
        return f.steps().stream().filter(s -> opened.test(s.unlock())
                && (s.unless().isEmpty() || !opened.test(s.unless()))).toList();
    }

    public FallbackFrameView(PageRect rect, SceneFrame frame, PageContext ctx) {
        this.rect = rect;
        this.layout = frame.layout();
        this.ctx = ctx;
        this.steps = visibleSteps(frame, ctx);
        Font font = ctx.font();
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        boolean any = false;
        for (int i = 0; i < steps.size(); i++) {
            SceneFrame.Step s = steps.get(i);
            List<Shown> row = new ArrayList<>();
            for (SceneFrame.Icon ic : s.icons()) {
                if (ic.item().isPresent()) {
                    Item item = ForgeRegistries.ITEMS.getValue(ic.item().get());
                    if (item != null && item != Items.AIR) {
                        row.add(new Shown(new ItemStack(item), null));
                    }
                } else {
                    row.add(new Shown(ItemStack.EMPTY, atlas.apply(ic.sprite().orElseThrow())));
                }
            }
            any |= !row.isEmpty();
            icons.add(row);
            Component c = Component.translatable("wildroots.journal.ui.step", i + 1, steps.size()).append(" ")
                    .append(Component.translatable(s.caption()));
            List<FormattedCharSequence> lines = font.split(c, PageRect.SPREAD_WIDTH);
            captions.add(lines.subList(0, Math.min(2, lines.size())));
        }
        this.textOnly = !any;
        if (textOnly) {
            for (int i = 0; i < steps.size(); i++) {
                captionList.addAll(font.split(Component.literal((i + 1) + ". ")
                        .append(Component.translatable(steps.get(i).caption())), PageRect.SPREAD_WIDTH - 8));
            }
        }
        int cy = rect.y() + PageRect.CONTROLS_Y;
        widgets.add(new BookWidgets.SpriteButton(rect.x(), cy, JournalAtlas.Button.STEP_BACK,
                Component.translatable("wildroots.journal.ui.scene.prev"), true, () -> setStep(step - 1)));
        widgets.add(new BookWidgets.SpriteButton(rect.x() + 16, cy, JournalAtlas.Button.STEP_FORWARD,
                Component.translatable("wildroots.journal.ui.scene.next"), true, () -> setStep(step + 1)));
    }

    public int step() {
        return step;
    }

    public int stepCount() {
        return steps.size();
    }

    public void setStep(int s) {
        step = Math.max(0, Math.min(steps.size() - 1, s));
    }

    @Override
    public PageRect rect() {
        return rect;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return widgets;
    }

    int windowY() {
        return rect.y() + PageRect.SCENE_WINDOW_Y;
    }

    /** Centre x of the dot of step {@code i} on the slider. */
    int dotX(int i) {
        int x0 = rect.x() + SLIDER_X;
        int x1 = rect.right() - 6;
        return steps.size() <= 1 ? x0 : x0 + (x1 - x0) * i / (steps.size() - 1);
    }

    int dotY() {
        return rect.y() + PageRect.CONTROLS_Y + 6;
    }

    /** The window being drawn: the page's own, or the one a 3D scene hands over ({@link #renderWindow}). */
    private int winX;
    private int winY;
    private int winW = PageRect.SCENE_WINDOW_W;
    private int winH = PageRect.SCENE_WINDOW_H;

    private void ownWindow() {
        winX = rect.x();
        winY = windowY();
        winW = PageRect.SCENE_WINDOW_W;
        winH = PageRect.SCENE_WINDOW_H;
    }

    /** Where icon {@code i} of the current picture goes (top-left) and its size, in the current window. */
    int[] place(int i, int n) {
        if (layout == SceneFrame.Layout.RING) {
            int size = Math.max(8, Math.min(24, winH / 4));
            double a = -Math.PI / 2 + 2 * Math.PI * i / Math.max(1, n);
            int rx = Math.max(0, winW / 2 - size / 2 - 8);
            int ry = Math.max(0, winH / 2 - size / 2 - 8);
            int cx = winX + winW / 2 + (int) Math.round(Math.cos(a) * Math.min(100, rx));
            int cy = winY + winH / 2 + (int) Math.round(Math.sin(a) * Math.min(36, ry));
            return new int[] {cx - size / 2, cy - size / 2, size};
        }
        int size = Math.max(8, Math.min(Math.min(ICON_MAX, winH - 8),
                (winW - 16 - (n - 1) * ARROW_GAP) / Math.max(1, n)));
        int total = n * size + (n - 1) * ARROW_GAP;
        int x = winX + (winW - total) / 2 + i * (size + ARROW_GAP);
        return new int[] {x, winY + (winH - size) / 2, size};
    }

    /** Each step's first icon, for the ring; built once (review m4). */
    private List<Shown> ring;

    /** The icons drawn now: the current step's (row), or each step's first (ring). */
    List<Shown> picture() {
        if (layout == SceneFrame.Layout.RING) {
            if (ring == null) {
                List<Shown> out = new ArrayList<>();
                for (List<Shown> r : icons) {
                    out.add(r.isEmpty() ? new Shown(ItemStack.EMPTY, null) : r.get(0));
                }
                ring = List.copyOf(out);
            }
            return ring;
        }
        return steps.isEmpty() ? List.of() : icons.get(step);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        ownWindow();
        JournalAtlas.nineSlice(g, JournalAtlas.SCENE_FRAME, JournalAtlas.SCENE_FRAME_BORDER, rect.x() - 4, winY - 4,
                PageRect.SCENE_WINDOW_W + 8, PageRect.SCENE_WINDOW_H + 8);
        if (steps.isEmpty()) {
            return;
        }
        drawWindow(g, mouseX, mouseY);
        renderControls(g, font, mouseX, mouseY);
    }

    /**
     * Only the picture, inside a window {@code (x, y, w, h)} that someone
     * else frames: no caption, slider or buttons (the 3D scene page in 2D
     * mode keeps its own, WR-181k).
     */
    public void renderWindow(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        handed[0] = x;
        handed[1] = y;
        handed[2] = w;
        handed[3] = h;
        wasHanded = true;
        winX = x;
        winY = y;
        winW = w;
        winH = h;
        if (!steps.isEmpty()) {
            drawWindow(g, mouseX, mouseY);
        }
        ownWindow();
    }

    private void drawWindow(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int wy = winY;
        if (textOnly) {
            int y = wy + 4;
            for (FormattedCharSequence l : captionList) {
                if (y + PageRect.LINE_HEIGHT > wy + winH) {
                    break;
                }
                g.drawString(font, l, winX + 4, y, JournalColors.INK, false);
                y += PageRect.LINE_HEIGHT;
            }
        } else {
            List<Shown> pic = picture();
            int n = pic.size();
            for (int i = 0; i < n; i++) {
                int[] p = place(i, n);
                Shown s = pic.get(i);
                boolean current = layout == SceneFrame.Layout.RING && i == step;
                if (current) {
                    JournalAtlas.nineSlice(g, JournalAtlas.FRAME_HIGHLIGHT, 2, p[0] - 3, p[1] - 3, p[2] + 6, p[2] + 6);
                }
                draw(g, s, p[0], p[1], p[2]);
                if (!s.stack().isEmpty() && mouseX >= p[0] && mouseY >= p[1] && mouseX < p[0] + p[2]
                        && mouseY < p[1] + p[2]) {
                    ctx.itemTooltip(s.stack());
                }
                if (i + 1 < n || layout == SceneFrame.Layout.RING && n > 1) {
                    int[] q = place((i + 1) % n, n);
                    arrow(g, p, q);
                }
            }
            if (layout == SceneFrame.Layout.RING) {
                JournalAtlas.blit(g, JournalAtlas.Arrow.LOOP.sprite(), winX + winW / 2 - 4, wy + winH / 2 - 4);
            }
        }
    }

    private void renderControls(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int cy = rect.y() + PageRect.CAPTION_Y;
        for (FormattedCharSequence l : captions.get(step)) {
            g.drawString(font, l, rect.x(), cy, JournalColors.INK, false);
            cy += PageRect.LINE_HEIGHT;
        }
        // slider: the track passed / ahead, then the dots
        int x0 = dotX(0);
        int x1 = dotX(steps.size() - 1);
        int ty = dotY() - 1;
        for (int x = x0; x < x1; x += JournalAtlas.SLIDER_TRACK.width()) {
            int w = Math.min(JournalAtlas.SLIDER_TRACK.width(), x1 - x);
            JournalAtlas.blit(g, x < dotX(step) ? JournalAtlas.SLIDER_TRACK_FILLED : JournalAtlas.SLIDER_TRACK, x, ty, w,
                    JournalAtlas.SLIDER_TRACK.height());
        }
        for (int i = 0; i < steps.size(); i++) {
            JournalAtlas.Sprite d = i == step ? JournalAtlas.SLIDER_DOT
                    : hitDot(mouseX, mouseY) == i ? JournalAtlas.SLIDER_DOT_HOVER
                    : i < step ? JournalAtlas.SLIDER_DOT_PAST : JournalAtlas.SLIDER_DOT_FUTURE;
            JournalAtlas.blit(g, d, dotX(i) - 3, dotY() - 3);
        }
    }

    static void draw(GuiGraphics g, Shown s, int x, int y, int size) {
        if (!s.stack().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(size / 16f, size / 16f, 1f);
            g.renderItem(s.stack(), 0, 0);
            g.pose().popPose();
        } else if (s.sprite() != null) {
            g.blit(x, y, 0, size, size, s.sprite());
        }
    }

    /** An ink arrow between two icons, pointing the dominant way. */
    static void arrow(GuiGraphics g, int[] p, int[] q) {
        int ax = (p[0] + p[2] / 2 + q[0] + q[2] / 2) / 2;
        int ay = (p[1] + p[2] / 2 + q[1] + q[2] / 2) / 2;
        int dx = q[0] - p[0];
        int dy = q[1] - p[1];
        JournalAtlas.Arrow a = Math.abs(dx) >= Math.abs(dy) ? (dx >= 0 ? JournalAtlas.Arrow.RIGHT : JournalAtlas.Arrow.LEFT)
                : (dy >= 0 ? JournalAtlas.Arrow.DOWN : JournalAtlas.Arrow.UP);
        JournalAtlas.blit(g, a.sprite(), ax - 4, ay - 4);
    }

    int hitDot(double mx, double my) {
        for (int i = 0; i < steps.size(); i++) {
            if (Math.abs(mx - dotX(i)) <= 4 && Math.abs(my - dotY()) <= 5) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int d = hitDot(mx, my);
        if (d >= 0) {
            setStep(d);
            return true;
        }
        ownWindow();
        return clickPicture(mx, my);
    }

    /** The window a 3D page last handed over ({@link #renderWindow}), for its clicks. */
    private final int[] handed = new int[4];
    private boolean wasHanded;

    /**
     * A click inside the window a 3D scene page handed over (its 2D mode,
     * review m6): a ring icon goes to its step, a frame item opens its entry.
     * The page reads {@link #step()} afterwards to follow.
     */
    public boolean clickWindow(double mx, double my) {
        if (!wasHanded) {
            return false;
        }
        winX = handed[0];
        winY = handed[1];
        winW = handed[2];
        winH = handed[3];
        try {
            return clickPicture(mx, my);
        } finally {
            ownWindow();
        }
    }

    private boolean clickPicture(double mx, double my) {
        if (!textOnly) {
            List<Shown> pic = picture();
            for (int i = 0; i < pic.size(); i++) {
                int[] p = place(i, pic.size());
                if (mx >= p[0] && my >= p[1] && mx < p[0] + p[2] && my < p[1] + p[2]) {
                    if (layout == SceneFrame.Layout.RING) {
                        setStep(i);
                        return true;
                    }
                    ItemStack st = pic.get(i).stack();
                    if (!st.isEmpty()) {
                        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
                        return ctx.openItem(id);
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods, boolean focused) {
        if (key == GLFW.GLFW_KEY_LEFT_BRACKET || focused && key == GLFW.GLFW_KEY_LEFT) {
            setStep(step - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_BRACKET || focused && key == GLFW.GLFW_KEY_RIGHT) {
            setStep(step + 1);
            return true;
        }
        return false;
    }
}
