package io.github.slugblue.wildroots.client.journal.scene;

import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.client.journal.book.JournalAtlas;
import io.github.slugblue.wildroots.client.journal.page.PageContext;
import io.github.slugblue.wildroots.client.journal.page.PageRect;
import io.github.slugblue.wildroots.client.journal.page.PageView;
import io.github.slugblue.wildroots.client.journal.page.ScenePage;
import io.github.slugblue.wildroots.client.journal.page.TextFit;
import io.github.slugblue.wildroots.item.ProduceStacks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * A scene page over the whole spread (journal v2 §3.13): the scene window
 * 264 × 102, the caption of the step (up to three lines, then an
 * ellipsis and the whole text as a tooltip), the control row; the
 * view of {@code {"type": "scene"}} pages, created once per layout.
 *
 * <p><b>Modes.</b> 3D while the scene loaded, {@code journalScenes} is on,
 * the reader has not pressed {@code 2D} and its rendering never threw;
 * otherwise the 2D frame of the steps' {@code frame} icons: the installed
 * {@link SceneFallback} (WR-181d), else a built-in frame; a scene that did not load
 * shows its id only. A throw while drawing → one log line, the 2D frame
 * for the rest of the session.
 *
 * <p><b>Resources.</b> The GPU buffers are made on the first 3D frame
 * after {@link #shown()} and closed in {@link #hidden()}; one scene is
 * live at a time.
 */
@OnlyIn(Dist.CLIENT)
public final class ScenePageView implements PageView {

    private static final int WINDOW_W = PageRect.SCENE_WINDOW_W;
    private static final int WINDOW_H = PageRect.SCENE_WINDOW_H;
    /** Ticks of {@code Shift+[ ]} (1 s). */
    private static final int SEEK = 20;
    private static final float ZOOM_STEP = 1.1f;
    private static final long DOUBLE_CLICK_MS = 300;
    /** Overlay item size (GUI px): never under 8 (§3.13). */
    private static final int ITEM_MIN = 10;
    private static final int ITEM_MAX = 20;

    private final PageRect rect;
    private final ScenePage page;
    private final PageContext ctx;
    private final SceneDef<BlockState> def;
    private final SceneTimeline timeline;
    private final SceneCameraState camera;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final Map<SceneAction.PopItem, ItemStack> popStacks = new IdentityHashMap<>();
    private final Map<ResourceLocation, ItemStack> frameStacks = new HashMap<>();
    /** Reused every frame (review m4): no per-frame lists, maps, label text or tooltip wrapping. */
    private final List<SceneRenderer.BeeAt> beesNow = new ArrayList<>(SceneLimits.MAX_BEES);
    private final Map<SceneAction.Cell, Integer> popsPerCell = new HashMap<>();
    private final Map<SceneAction.Cell, Integer> popsSeen = new HashMap<>();
    private final float[] projected = new float[2];
    private final Map<String, Component> labelText = new HashMap<>();
    private final Map<String, Integer> labelWidth = new HashMap<>();
    private final Map<String, List<Component>> tooltipLines = new HashMap<>();
    private Boolean playShown;
    private SceneWidgets.Button play;
    private SceneWidgets.Button reset;

    private SceneRenderer renderer;
    /** The installed 2D frame (WR-181d), made on first use; null = the built-in frame. */
    private SceneFallback fallback;
    private boolean fallbackMade;
    private boolean flat;
    private boolean dragging;
    private long lastClick;
    private int captionStep = -1;
    private List<FormattedCharSequence> caption = List.of();
    private final List<Hit> hits = new ArrayList<>();
    private double frameNanos;

    /** Something under the pointer in the last frame: an item (tooltip, click → entry) or a label tooltip. */
    private record Hit(int x, int y, int w, int h, ItemStack stack, ResourceLocation item, String tooltip) {

        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    public ScenePageView(PageRect rect, ScenePage page, PageContext ctx) {
        this.rect = rect;
        this.page = page;
        this.ctx = ctx;
        SceneDef<BlockState> d = SceneRegistry.INSTANCE.get(page.scene()).orElse(null);
        SceneTimeline t = d == null ? null : new SceneTimeline(d, ids -> ctx.unlocks().opened(ids));
        if (t != null && t.isEmpty()) {
            d = null;
            t = null;
        }
        this.def = d;
        this.timeline = t;
        this.camera = new SceneCameraState(d == null ? SceneCamera.DEFAULT : d.camera());
        if (timeline != null) {
            camera.jumped(timeline.steps(), 0);
            stacks();
            controls();
        }
    }

    // ------------------------------------------------------------------ setup

    private void stacks() {
        for (SceneStep s : timeline.steps()) {
            for (SceneAction a : s.actions()) {
                if (a instanceof SceneAction.PopItem p) {
                    popStacks.put(p, stack(p.item(), p.variant().orElse(null)));
                }
            }
            for (SceneStep.FrameIcon f : s.frame()) {
                if (f instanceof SceneStep.FrameItem fi) {
                    frameStacks.computeIfAbsent(fi.item(), rl -> stack(rl, null));
                }
            }
        }
    }

    /** A display stack, built on the client and never sent (§3.1); a variant only from the client table. */
    private static ItemStack stack(ResourceLocation id, String variant) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        if ("colour=purple".equals(variant)) {
            return ProduceStacks.create(item, ProduceStacks.DEFAULT_GRADE, false, ProduceStacks.PURPLE, 1);
        }
        if ("colour=red".equals(variant)) {
            return ProduceStacks.create(item, ProduceStacks.DEFAULT_GRADE, false, ProduceStacks.RED, 1);
        }
        if ("colour=orange".equals(variant)) {
            return ProduceStacks.create(item, ProduceStacks.DEFAULT_GRADE, false, ProduceStacks.ORANGE, 1);
        }
        if ("colour=yellow".equals(variant)) {
            return ProduceStacks.create(item, ProduceStacks.DEFAULT_GRADE, false, ProduceStacks.YELLOW, 1);
        }
        return new ItemStack(item);
    }

    private int windowX() {
        return rect.x();
    }

    private int windowY() {
        return rect.y() + PageRect.SCENE_WINDOW_Y;
    }

    private void controls() {
        Font font = Minecraft.getInstance().font;
        int y = rect.y() + PageRect.CONTROLS_Y;
        int x = rect.x();
        widgets.add(new SceneWidgets.Button(x, y, JournalAtlas.Button.STEP_BACK,
                Component.translatable("wildroots.journal.ui.scene.prev"), null, () -> jump(timeline.step() - 1)));
        play = new SceneWidgets.Button(x + 14, y, JournalAtlas.Button.PAUSE,
                Component.translatable("wildroots.journal.ui.scene.pause"), null, this::togglePlay);
        widgets.add(play);
        widgets.add(new SceneWidgets.Button(x + 28, y, JournalAtlas.Button.STEP_FORWARD,
                Component.translatable("wildroots.journal.ui.scene.next"), null, () -> jump(timeline.step() + 1)));
        Component again = Component.translatable("wildroots.journal.ui.scene.restart");
        int restartX = rect.x() + WINDOW_W - 14 - font.width(again);
        int sliderX = x + 46;
        widgets.add(new SceneWidgets.Slider(sliderX, y, Math.max(24, restartX - 6 - sliderX), sliderModel()));
        widgets.add(new SceneWidgets.Button(restartX, y, JournalAtlas.Button.RESTART, again, again, this::restart));
        int by = windowY() + WINDOW_H - 16;
        reset = new SceneWidgets.Button(windowX() + WINDOW_W - 30, by, JournalAtlas.Button.RESET_VIEW,
                Component.translatable("wildroots.journal.ui.scene.reset_view"), null, this::resetView);
        widgets.add(reset);
        widgets.add(new SceneWidgets.Button(windowX() + WINDOW_W - 16, by, JournalAtlas.Button.FLAT,
                Component.translatable("wildroots.journal.ui.scene.flat"), null, this::toggleFlat)
                .toggle(() -> !threeD()));
    }

    private SceneWidgets.SliderModel sliderModel() {
        return new SceneWidgets.SliderModel() {
            @Override
            public int steps() {
                return timeline.steps().size();
            }

            @Override
            public int step() {
                return timeline.step();
            }

            @Override
            public double fraction() {
                return timeline.fraction();
            }

            @Override
            public double stepFraction(int n) {
                return timeline.stepFraction(n);
            }

            @Override
            public void jump(int n) {
                ScenePageView.this.jump(n);
            }

            @Override
            public void seekFraction(double f) {
                int before = timeline.step();
                timeline.seekFraction(f);
                if (timeline.step() != before) {
                    camera.jumped(timeline.steps(), timeline.step());
                }
            }
        };
    }

    // ------------------------------------------------------------------ actions

    private void jump(int n) {
        timeline.jump(n);
        camera.jumped(timeline.steps(), timeline.step());
    }

    private void togglePlay() {
        boolean restart = !timeline.playing() && timeline.finished();
        timeline.toggle();
        if (restart) {
            camera.jumped(timeline.steps(), 0);
        }
    }

    private void restart() {
        timeline.restart();
        camera.jumped(timeline.steps(), 0);
    }

    private void resetView() {
        camera.reset(timeline.steps(), timeline.step());
    }

    private void toggleFlat() {
        flat = !flat;
        if (flat) {
            closeRenderer();
        }
    }

    /** Whether the 3D scene is drawn (else the 2D frame). */
    private boolean threeD() {
        return def != null && !flat && ctx.scenesEnabled() && !SceneRegistry.INSTANCE.failed(def.id());
    }

    private void closeRenderer() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        dragging = false;
    }

    // ------------------------------------------------------------------ PageView

    @Override
    public PageRect rect() {
        return rect;
    }

    @Override
    public List<? extends AbstractWidget> widgets() {
        return widgets;
    }

    @Override
    public void shown() {
    }

    @Override
    public void hidden() {
        closeRenderer();
    }

    @Override
    public void tick() {
        if (timeline == null) {
            return;
        }
        int before = timeline.step();
        timeline.advance();
        if (timeline.step() != before) {
            camera.stepReached(timeline.current());
        }
        camera.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        if (timeline == null) {
            g.drawString(font, Component.translatable("wildroots.journal.ui.scene.unavailable"), rect.x(), windowY(),
                    JournalColors.INK_FADED, false);
            return;
        }
        if (playShown == null || playShown != timeline.playing()) {
            playShown = timeline.playing();
            play.set(playShown ? JournalAtlas.Button.PAUSE : JournalAtlas.Button.PLAY,
                    Component.translatable(playShown ? "wildroots.journal.ui.scene.pause"
                            : "wildroots.journal.ui.scene.play"));
        }
        reset.active = threeD();
        int wx = windowX();
        int wy = windowY();
        hits.clear();
        g.blit(JournalAtlas.TEXTURE, wx, wy, WINDOW_W, WINDOW_H, JournalAtlas.SCENE_BACKDROP.u(),
                JournalAtlas.SCENE_BACKDROP.v(), JournalAtlas.SCENE_BACKDROP.width(),
                JournalAtlas.SCENE_BACKDROP.height(), JournalAtlas.SIZE, JournalAtlas.SIZE);
        boolean drawn3d = false;
        if (threeD()) {
            long t0 = System.nanoTime();
            g.enableScissor(wx, wy, wx + WINDOW_W, wy + WINDOW_H);
            try {
                render3d(g, partialTick);
                drawn3d = true;
            } catch (RuntimeException | LinkageError e) {
                SceneRegistry.INSTANCE.markFailed(def.id(), e);
                closeRenderer();
            } finally {
                g.disableScissor();
            }
            frameNanos = frameNanos == 0 ? System.nanoTime() - t0 : frameNanos * 0.95 + (System.nanoTime() - t0) * 0.05;
        }
        if (!drawn3d) {
            SceneFallback f = fallback();
            if (f != null) {
                f.setStep(timeline.step());
                f.renderWindow(g, wx, wy, WINDOW_W, WINDOW_H, mouseX, mouseY, partialTick);
            } else {
                renderFlat(g);
            }
        }
        JournalAtlas.nineSlice(g, JournalAtlas.SCENE_FRAME, JournalAtlas.SCENE_FRAME_BORDER, wx - 4, wy - 4,
                WINDOW_W + 8, WINDOW_H + 8);
        renderCaption(g, font, mouseX, mouseY);
        if (ctx.debug()) {
            renderDebug(g, font);
        }
        hover(mouseX, mouseY);
    }

    // ------------------------------------------------------------------ 3D

    private void render3d(GuiGraphics g, float partialTick) {
        if (renderer == null) {
            renderer = new SceneRenderer(def);
        }
        int wx = windowX();
        int wy = windowY();
        SceneCamera cam = camera.current(partialTick);
        SceneStep step = timeline.current();
        float time = timeline.time(partialTick);
        List<SceneRenderer.BeeAt> bees = beesNow;
        bees.clear();
        for (SceneAction a : step.actions()) {
            if (a instanceof SceneAction.Bee b && time >= b.at() && time < b.at() + b.ticks()) {
                float k = (time - b.at()) / b.ticks();
                double[] p = SceneMotion.along(b.path(), k);
                bees.add(new SceneRenderer.BeeAt(p[0], p[1], p[2], SceneMotion.heading(b.path(), k), time));
            }
        }
        renderer.draw(g, timeline, cam, wx, wy, WINDOW_W, WINDOW_H, bees);
        Matrix4f m = renderer.matrix(cam, wx, wy, WINDOW_W, WINDOW_H);
        float scale = renderer.scale(cam.zoom(), WINDOW_W, WINDOW_H);
        overlays(g, step, time, m, scale);
        g.flush();
        SceneRenderer.clearDepth();
    }

    /**
     * Highlights, labels, icons, popped items: flat, over the scene, at
     * projected points (owner playtest 2):
     * <ul>
     *   <li>a highlight frames only the real blocks of its cells at this
     *       moment, tightly around their shapes; once they are gone (picked)
     *       it is not drawn;</li>
     *   <li>callouts never overlap ({@link CalloutLayout}): a moved one gets
     *       a leader line to its anchor; one that belongs to a highlight is
     *       outlined and led in the highlight's green;</li>
     *   <li>a hand never covers a callout, and a dotted green line leads from
     *       the moving hand to the cell it goes to.</li>
     * </ul>
     */
    private void overlays(GuiGraphics g, SceneStep step, float time, Matrix4f m, float scale) {
        Font font = Minecraft.getInstance().font;
        int wx = windowX();
        int wy = windowY();
        // highlights: tight boxes around the blocks that are there now
        boxes.clear();
        boxCells.clear();
        int[] cells = timeline.blocks();
        for (SceneAction a : step.actions()) {
            if (a instanceof SceneAction.Highlight h && time >= h.at()) {
                int[] box = highlightBox(m, h, cells);
                if (box != null) {
                    boxes.add(box);
                    boxCells.add(h);
                    g.renderOutline(box[0], box[1], box[2], box[3], JournalColors.NEW);
                }
            }
        }
        popsPerCell.clear();
        for (SceneAction a : step.actions()) {
            if (a instanceof SceneAction.PopItem p && time >= p.at()) {
                popsPerCell.merge(p.pos(), 1, Integer::sum);
            }
        }
        // callouts: laid out together, then drawn
        wants.clear();
        wantLabels.clear();
        for (SceneAction a : step.actions()) {
            if (a instanceof SceneAction.Label l && time >= l.at()) {
                float[] s = SceneRenderer.project(m, l.pos().x() + 0.5, l.pos().y() + 0.3, l.pos().z() + 0.5, projected);
                Component text = labelText.computeIfAbsent(l.key(), Component::translatable);
                int w = labelWidth.computeIfAbsent(l.key(), k -> font.width(text)) + 6;
                wants.add(new CalloutLayout.Want(Math.round(s[0]), Math.round(s[1]), w, LABEL_H));
                wantLabels.add(l);
            }
        }
        List<CalloutLayout.Placed> placed = CalloutLayout.place(wants, wx + 1, wx + WINDOW_W - 1, wy + 1);
        // leaders first, then the callouts over them: a leader never crosses a callout's text (review 1, m8)
        for (CalloutLayout.Placed c : placed) {
            if (c.moved()) {
                leader(g, placed, c, wants.get(c.index()), linked(wantLabels.get(c.index())));
            }
        }
        for (CalloutLayout.Placed c : placed) {
            SceneAction.Label l = wantLabels.get(c.index());
            CalloutLayout.Want w = wants.get(c.index());
            label(g, font, c, w, l, linked(l));
        }
        // billboards: beside the callouts, with a line to where the hand goes
        for (SceneAction a : step.actions()) {
            if (a instanceof SceneAction.Icon i && time >= i.at() && time < i.at() + i.ticks()) {
                double[] p = SceneMotion.along(i.path(), (time - i.at()) / i.ticks());
                float[] s = SceneRenderer.project(m, p[0], p[1], p[2], projected);
                int ax = Math.round(s[0]);
                int ay = Math.round(s[1]);
                int[] at = CalloutLayout.beside(placed, ax - 8, ay - (i.icon().bottom ? 15 : 8), 16, wx + 1,
                        wx + WINDOW_W - 1);
                if (i.icon().bottom && i.path().size() > 1) {
                    SceneAction.Point end = i.path().get(i.path().size() - 1);
                    float[] e = SceneRenderer.project(m, end.x() + 0.5, end.y(), end.z() + 0.5, projected);
                    dotted(g, at[0] + 8, at[1] + 15, Math.round(e[0]), Math.round(e[1]), JournalColors.NEW);
                }
                g.blit(JournalAtlas.SCENE, at[0], at[1], i.icon().u, i.icon().v, 16, 16, JournalAtlas.SCENE_W,
                        JournalAtlas.SCENE_H);
            }
        }
        int size = Mth.clamp(Math.round(scale * 0.75f), ITEM_MIN, ITEM_MAX);
        Map<SceneAction.Cell, Integer> seen = popsSeen;
        seen.clear();
        for (SceneAction a : step.actions()) {
            if (a instanceof SceneAction.PopItem p && time >= p.at()) {
                ItemStack stack = popStacks.getOrDefault(p, ItemStack.EMPTY);
                if (stack.isEmpty()) {
                    continue;
                }
                int index = seen.merge(p.pos(), 1, Integer::sum) - 1;
                double[] w = SceneMotion.pop(p.pos(), index, popsPerCell.get(p.pos()), time - p.at());
                float[] s = SceneRenderer.project(m, w[0], w[1], w[2], projected);
                int x = Math.round(s[0] - size / 2f);
                int y = Math.round(s[1] - size / 2f);
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(size / 16f, size / 16f, 1);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
                hits.add(new Hit(x, y, size, size, stack, p.item(), null));
            }
        }
    }

    private static final int LABEL_H = 13;
    /** Reused per frame: highlight boxes and the cells they frame, wanted callouts and their labels. */
    private final List<int[]> boxes = new ArrayList<>();
    private final List<SceneAction.Highlight> boxCells = new ArrayList<>();
    private final List<CalloutLayout.Want> wants = new ArrayList<>();
    private final List<SceneAction.Label> wantLabels = new ArrayList<>();

    /** Whether a callout names a framed cell: its anchor cell, or the cell under it, is in a drawn highlight. */
    private boolean linked(SceneAction.Label l) {
        int x = (int) Math.floor(l.pos().x());
        int y = (int) Math.floor(l.pos().y());
        int z = (int) Math.floor(l.pos().z());
        for (SceneAction.Highlight h : boxCells) {
            boolean inXz = x >= h.from().x() && x <= h.to().x() && z >= h.from().z() && z <= h.to().z();
            if (inXz && (y >= h.from().y() && y <= h.to().y() || y - 1 >= h.from().y() && y - 1 <= h.to().y())) {
                return true;
            }
        }
        return false;
    }

    /** Render thread only: scratch of {@link #highlightBox}. */
    private static final float[] CORNER = new float[2];

    /**
     * The screen box (x, y, w, h) around the shapes of the blocks in the
     * highlight's cells as they are now, or {@code null} when every cell is
     * empty (5.png: a frame over a plant already picked).
     */
    private int[] highlightBox(Matrix4f m, SceneAction.Highlight h, int[] cells) {
        float x0 = Float.MAX_VALUE;
        float y0 = Float.MAX_VALUE;
        float x1 = -Float.MAX_VALUE;
        float y1 = -Float.MAX_VALUE;
        boolean any = false;
        List<BlockState> states = def.states();
        for (int cx = h.from().x(); cx <= h.to().x(); cx++) {
            for (int cy = h.from().y(); cy <= h.to().y(); cy++) {
                for (int cz = h.from().z(); cz <= h.to().z(); cz++) {
                    if (cx < 0 || cy < 0 || cz < 0 || cx >= def.sizeX() || cy >= def.sizeY() || cz >= def.sizeZ()) {
                        continue;
                    }
                    BlockState st = states.get(cells[def.index(cx, cy, cz)]);
                    if (st.isAir()) {
                        continue;
                    }
                    net.minecraft.world.phys.shapes.VoxelShape shape = st.getShape(
                            net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
                    net.minecraft.world.phys.AABB b = shape.isEmpty() ? new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1)
                            : shape.bounds();
                    for (int c = 0; c < 8; c++) {
                        float[] s = SceneRenderer.project(m, cx + ((c & 1) == 0 ? b.minX : b.maxX),
                                cy + ((c & 2) == 0 ? b.minY : b.maxY), cz + ((c & 4) == 0 ? b.minZ : b.maxZ), CORNER);
                        x0 = Math.min(x0, s[0]);
                        y0 = Math.min(y0, s[1]);
                        x1 = Math.max(x1, s[0]);
                        y1 = Math.max(y1, s[1]);
                    }
                    any = true;
                }
            }
        }
        if (!any) {
            return null;
        }
        return new int[] {Math.round(x0) - 2, Math.round(y0) - 2, Math.round(x1 - x0) + 4, Math.round(y1 - y0) + 4};
    }

    /** A dotted 1 px line (every other 2 px), for the hand's way to its cell. */
    private static void dotted(GuiGraphics g, int xa, int ya, int xb, int yb, int color) {
        int n = Math.max(Math.abs(xb - xa), Math.abs(yb - ya));
        for (int i = 0; i <= n; i += 4) {
            int x = xa + (xb - xa) * i / Math.max(1, n);
            int y = ya + (yb - ya) * i / Math.max(1, n);
            g.fill(x, y, x + 2, y + 1, color);
        }
    }

    /**
     * A callout (9-slice, border 3, text at +3, +2, 13 tall) where the layout
     * put it. Straight above its anchor it has its tail; moved, a leader line
     * runs down to the anchor. One that names a framed cell is outlined and
     * led in the frame's green.
     */
    private void label(GuiGraphics g, Font font, CalloutLayout.Placed c, CalloutLayout.Want w, SceneAction.Label l,
                       boolean linked) {
        Component text = labelText.computeIfAbsent(l.key(), Component::translatable);
        sceneNineSlice(g, 16, 16, 12, 12, 3, c.x(), c.y(), c.w(), c.h());
        if (!c.moved()) {
            g.blit(JournalAtlas.SCENE, w.anchorX() - 3, c.y() + c.h() - 1, 30, 16, 7, 4, JournalAtlas.SCENE_W,
                    JournalAtlas.SCENE_H);
        }
        if (linked) {
            g.renderOutline(c.x() - 1, c.y() - 1, c.w() + 2, c.h() + 2, JournalColors.NEW);
        }
        g.drawString(font, text, c.x() + 3, c.y() + 2, JournalColors.INK, false);
        if (l.tooltip().isPresent()) {
            hits.add(new Hit(c.x(), c.y(), c.w(), c.h() + 3, ItemStack.EMPTY, null, l.tooltip().get()));
        }
    }

    /** The L-shaped leader of a moved callout down to its anchor, and the anchor dot ({@link CalloutLayout#leader}). */
    private static void leader(GuiGraphics g, List<CalloutLayout.Placed> placed, CalloutLayout.Placed c,
                               CalloutLayout.Want w, boolean linked) {
        int lead = linked ? JournalColors.NEW : JournalColors.INK;
        int[] l = CalloutLayout.leader(placed, c, w);
        g.fill(l[0], Math.min(l[1], l[2]), l[0] + 1, Math.max(l[1], l[2]) + 1, lead);
        g.fill(Math.min(l[0], l[3]), l[2], Math.max(l[0], l[3]) + 1, l[2] + 1, lead);
        g.fill(w.anchorX() - 1, w.anchorY() - 1, w.anchorX() + 2, w.anchorY() + 2, lead);
    }

    /** 9-slice of {@code journal_scene.png} (64 × 32). */
    private static void sceneNineSlice(GuiGraphics g, int u, int v, int sw, int sh, int b, int x, int y, int w,
                                       int h) {
        int iw = Math.max(0, w - 2 * b);
        int ih = Math.max(0, h - 2 * b);
        int[] dx = {x, x + b, x + w - b};
        int[] dw = {b, iw, b};
        int[] su = {u, u + b, u + sw - b};
        int[] uw = {b, sw - 2 * b, b};
        int[] dy = {y, y + b, y + h - b};
        int[] dh = {b, ih, b};
        int[] sv = {v, v + b, v + sh - b};
        int[] vh = {b, sh - 2 * b, b};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (dw[c] > 0 && dh[r] > 0) {
                    g.blit(JournalAtlas.SCENE, dx[c], dy[r], dw[c], dh[r], su[c], sv[r], uw[c], vh[r],
                            JournalAtlas.SCENE_W, JournalAtlas.SCENE_H);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 2D frame (placeholder until WR-181d)

    /**
     * The 2D frame (§3.13): {@code row} — the current step's icons in a
     * row with arrows; {@code ring} — every step's first icon on a ring,
     * the current one framed, the loop arrow in the middle. A step without
     * icons shows only its caption.
     */
    private void renderFlat(GuiGraphics g) {
        int cx = windowX() + WINDOW_W / 2;
        int cy = windowY() + WINDOW_H / 2;
        List<SceneStep> steps = timeline.steps();
        if (def.frameLayout() == SceneDef.FrameLayout.RING) {
            int n = steps.size();
            for (int i = 0; i < n; i++) {
                double a = -Math.PI / 2 + 2 * Math.PI * i / n;
                int x = cx + (int) Math.round(Math.cos(a) * 92) - 8;
                int y = cy + (int) Math.round(Math.sin(a) * 36) - 8;
                if (i == timeline.step()) {
                    JournalAtlas.nineSlice(g, JournalAtlas.FRAME_HIGHLIGHT, 2, x - 3, y - 3, 22, 22);
                }
                if (!steps.get(i).frame().isEmpty()) {
                    icon(g, steps.get(i).frame().get(0), x, y);
                }
            }
            JournalAtlas.blit(g, JournalAtlas.Arrow.LOOP.sprite(), cx - 4, cy - 4);
            return;
        }
        List<SceneStep.FrameIcon> icons = timeline.current().frame();
        int n = icons.size();
        if (n == 0) {
            return;
        }
        int gap = 12;
        int w = n * 16 + (n - 1) * gap;
        int x = cx - w / 2;
        JournalAtlas.nineSlice(g, JournalAtlas.FRAME_HIGHLIGHT, 2, x - 4, cy - 12, w + 8, 24);
        for (int i = 0; i < n; i++) {
            icon(g, icons.get(i), x, cy - 8);
            if (i + 1 < n) {
                JournalAtlas.blit(g, JournalAtlas.Arrow.RIGHT.sprite(), x + 16 + 2, cy - 4);
            }
            x += 16 + gap;
        }
    }

    private SceneFallback fallback() {
        if (!fallbackMade) {
            fallbackMade = true;
            fallback = SceneFallback.Install.create(rect, page.scene(), ctx);
        }
        return fallback;
    }

    private void icon(GuiGraphics g, SceneStep.FrameIcon f, int x, int y) {
        if (f instanceof SceneStep.FrameItem fi) {
            ItemStack s = frameStacks.getOrDefault(fi.item(), ItemStack.EMPTY);
            if (!s.isEmpty()) {
                g.renderItem(s, x, y);
                hits.add(new Hit(x, y, 16, 16, s, fi.item(), null));
            }
        } else if (f instanceof SceneStep.FrameSprite fs) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(fs.sprite());
            g.blit(x, y, 0, 16, 16, sprite);
        }
    }

    // ------------------------------------------------------------------ caption, debug, hover

    private void renderCaption(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (captionStep != timeline.step()) {
            captionStep = timeline.step();
            String text = Component.translatable("wildroots.journal.ui.step", captionStep + 1,
                    timeline.steps().size()).getString() + ". "
                    + Component.translatable(timeline.current().caption()).getString();
            TextFit.Measure m = TextFit.of(font);
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (String l : TextFit.wrapMax(text, WINDOW_W, PageRect.CAPTION_LINES, false, m)) {
                lines.add(Component.literal(l).getVisualOrderText());
            }
            caption = lines;
            captionTip = TextFit.wrap(text, WINDOW_W, false, m).size() > PageRect.CAPTION_LINES
                    ? TextFit.wrap(text, 180, false, m).stream().<Component>map(Component::literal).toList() : List.of();
        }
        int y = rect.y() + PageRect.CAPTION_Y;
        for (FormattedCharSequence line : caption) {
            g.drawString(font, line, rect.x(), y, JournalColors.INK, false);
            y += PageRect.LINE_HEIGHT;
        }
        if (!captionTip.isEmpty() && mouseX >= rect.x() && mouseX < rect.x() + WINDOW_W
                && mouseY >= rect.y() + PageRect.CAPTION_Y && mouseY < y) {
            ctx.tooltip(captionTip);
        }
    }

    /** The whole caption as tooltip lines when it was cut to {@link PageRect#CAPTION_LINES}; else empty. */
    private List<Component> captionTip = List.of();

    private void renderDebug(GuiGraphics g, Font font) {
        SceneMesh mesh = renderer == null ? null : renderer.mesh();
        String a = String.format("rebuilds %d, last %.2f ms, frame %.2f ms", mesh == null ? 0 : mesh.builds(),
                mesh == null ? 0 : mesh.lastBuildNanos() / 1e6, frameNanos / 1e6);
        String b = String.format("buffers %d, %.1f KiB GPU, load %.2f ms", SceneRenderer.liveBuffers(),
                SceneRenderer.liveBytes() / 1024.0, SceneRegistry.INSTANCE.lastLoadNanos() / 1e6);
        g.drawString(font, a, windowX() + 2, windowY() + 2, 0xFFFFFFFF, true);
        g.drawString(font, b, windowX() + 2, windowY() + 11, 0xFFFFFFFF, true);
    }

    private void hover(int mx, int my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(mx, my)) {
                continue;
            }
            if (!h.stack().isEmpty()) {
                ctx.itemTooltip(h.stack());
            } else if (h.tooltip() != null) {
                ctx.tooltip(tooltipLines.computeIfAbsent(h.tooltip(), key -> {
                    Font font = Minecraft.getInstance().font;
                    List<Component> lines = new ArrayList<>();
                    for (FormattedText t : font.getSplitter().splitLines(Component.translatable(key), 180,
                            Style.EMPTY)) {
                        lines.add(Component.literal(t.getString()));
                    }
                    return List.copyOf(lines);
                }));
            }
            return;
        }
    }

    // ------------------------------------------------------------------ input

    private boolean inWindow(double mx, double my) {
        return mx >= windowX() && my >= windowY() && mx < windowX() + WINDOW_W && my < windowY() + WINDOW_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (timeline == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !inWindow(mx, my)) {
            return false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.item() != null && h.contains(mx, my) && ctx.hasEntryFor(h.item())) {
                ctx.openItem(h.item());
                return true;
            }
        }
        if (!threeD()) {
            // 2D mode: ring icons and frame items of the handed-over frame take the click (review m6)
            SceneFallback f = fallback();
            if (f != null && f.mouseClicked(mx, my)) {
                if (f.step() >= 0 && f.step() != timeline.step()) {
                    jump(f.step());
                }
            }
            return true;
        }
        long now = Util.getMillis();
        if (now - lastClick < DOUBLE_CLICK_MS) {
            resetView();
            lastClick = 0;
            return true;
        }
        lastClick = now;
        dragging = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging || !threeD()) {
            return false;
        }
        camera.turn((float) dx, (float) dy);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (timeline == null || !inWindow(mx, my)) {
            return false;
        }
        if (threeD() && delta != 0) {
            camera.zoom(delta > 0 ? ZOOM_STEP : 1 / ZOOM_STEP);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods, boolean focused) {
        if (timeline == null || (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
            return false;
        }
        if (!focused && Minecraft.getInstance().screen != null
                && Minecraft.getInstance().screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox) {
            // a text field (search) is typing: its letters are not scene keys
            return false;
        }
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_SPACE -> togglePlay();
            case GLFW.GLFW_KEY_LEFT_BRACKET -> {
                if (shift) {
                    timeline.seek(-SEEK);
                } else {
                    jump(timeline.step() - 1);
                }
            }
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> {
                if (shift) {
                    timeline.seek(SEEK);
                } else {
                    jump(timeline.step() + 1);
                }
            }
            case GLFW.GLFW_KEY_R -> resetView();
            case GLFW.GLFW_KEY_V -> toggleFlat();
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> camera.zoom(ZOOM_STEP);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> camera.zoom(1 / ZOOM_STEP);
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                if (!focused || !threeD()) {
                    return false;
                }
                camera.turn(key == GLFW.GLFW_KEY_LEFT ? -15 : key == GLFW.GLFW_KEY_RIGHT ? 15 : 0,
                        key == GLFW.GLFW_KEY_UP ? 10 : key == GLFW.GLFW_KEY_DOWN ? -10 : 0);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** What a new layout of this page keeps (review m7): the scene, its visible steps, playback, camera, 2D. */
    private record Saved(String scene, List<SceneStep> steps, SceneTimeline.State timeline,
                         SceneCameraState.State camera, boolean flat) {
    }

    @Override
    public Object saveState() {
        return timeline == null ? null
                : new Saved(page.scene(), timeline.steps(), timeline.state(), camera.state(), flat);
    }

    @Override
    public void restoreState(Object state) {
        // the same scene with the same visible steps (an unlock may have added the brush step): else start over
        if (timeline != null && state instanceof Saved s && s.scene().equals(page.scene())
                && s.steps().equals(timeline.steps())) {
            timeline.restore(s.timeline());
            camera.restore(s.camera());
            flat = s.flat();
            captionStep = -1;
        }
    }

    /** The 2D frame data of this page's scene, for WR-181d's frame (null when the scene did not load). */
    public SceneDef.FrameData frameData() {
        return def == null ? null : def.frameData();
    }
}
