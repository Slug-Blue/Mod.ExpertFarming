package io.github.slugblue.wildroots.client.lens;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.slugblue.wildroots.client.journal.JournalColors;
import io.github.slugblue.wildroots.core.safety.DispCodec;
import io.github.slugblue.wildroots.core.safety.Guarded;
import io.github.slugblue.wildroots.lens.LensRow;
import io.github.slugblue.wildroots.lens.LensSheet;
import io.github.slugblue.wildroots.lens.LensWindowPacket;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The lens window (playtest 2, item 3): the tier-1 reading of a plant or of
 * a lot on a sheet of the journal's paper, with one bar per trait in the
 * legend's words ({@link LensSheetView}), the flags, the environment that
 * holds the plant back and, for a plant on farmland, a small model of it
 * that the mouse turns. It does not pause the game; Esc or the inventory
 * key closes it. It draws only what the server sent
 * ({@link LensWindowPacket}).
 */
@OnlyIn(Dist.CLIENT)
public final class LensWindowScreen extends Screen {

    static final int SHEET_WIDTH = 170;
    static final int MODEL = 84;
    static final int PAD = 8;
    static final float MODEL_SCALE = 30;

    private final LensSheet sheet;
    private final Optional<BlockState> plant;
    private float yaw = 200;
    private float pitch = 25;
    private int left;
    private int top;
    private int sheetHeight;

    LensWindowScreen(LensSheet sheet, Optional<BlockState> plant) {
        super(sheet.title());
        this.sheet = sheet;
        this.plant = plant;
    }

    /** The sheet of a packet: the plant's or the lot's; empty for a broken one. */
    static Optional<LensSheet> sheet(LensWindowPacket p) {
        if (p == LensWindowPacket.BROKEN) {
            return Optional.empty();
        }
        Component title = DispCodec.toComponent(p.header());
        Optional<LensRow> row = LensRow.parse(p.row());
        if (row.isEmpty()) {
            java.util.List<Component> notes = new java.util.ArrayList<>(colour(p));
            notes.addAll(LensSheet.environment(p.env()));
            return Optional.of(new LensSheet(title, java.util.List.of(), LensSheet.Icon.NONE, Optional.empty(),
                    java.util.List.of(), java.util.List.of(), notes));
        }
        return Optional.of(p.kind() == LensWindowPacket.Kind.LOT ? LensSheet.lot(title, java.util.List.of(), row.get(),
                p.species(), io.github.slugblue.wildroots.lens.Lens.SEEDLING_HEADER.equals(p.header().key())) : LensSheet.plant(title, row.get(), p.species(), p.env(), colour(p)));
    }

    /** The colour lines of a packet as components (a plant of stage 3 or more). */
    static java.util.List<Component> colour(LensWindowPacket p) {
        return p.colour().stream().map(DispCodec::toComponent).toList();
    }

    /** The plant's block state for the model, only a known non-air state. */
    static Optional<BlockState> model(LensWindowPacket p) {
        if (p.state() == LensWindowPacket.NO_MODEL) {
            return Optional.empty();
        }
        BlockState s = Block.stateById(p.state());
        return s.isAir() ? Optional.empty() : Optional.of(s);
    }

    /** Opens the window for a packet (client thread). */
    public static void open(LensWindowPacket p) {
        sheet(p).ifPresent(s -> Minecraft.getInstance().setScreen(new LensWindowScreen(s, model(p))));
    }

    /**
     * Opens the window only when no other screen is open (review 1, m1: a
     * container, the chat or another screen is never replaced, so no menu
     * is left open on the server and no chat draft is lost). Otherwise the
     * reading goes to the local chat as lines, the old form; returns whether
     * the window opened.
     */
    public static boolean openOrChat(LensWindowPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            open(p);
            return true;
        }
        mc.gui.getChat().addMessage(DispCodec.toComponent(p.header()));
        LensRow.parse(p.row()).ifPresent(r -> r.expand(p.species()).forEach(mc.gui.getChat()::addMessage));
        colour(p).forEach(mc.gui.getChat()::addMessage);
        LensSheet.environment(p.env()).forEach(mc.gui.getChat()::addMessage);
        return false;
    }

    @Override
    protected void init() {
        sheetHeight = LensSheetView.height(font, sheet, SHEET_WIDTH);
        int w = SHEET_WIDTH + 2 * PAD + (plant.isPresent() ? MODEL + PAD : 0);
        int h = Math.max(sheetHeight, plant.isPresent() ? MODEL : 0) + 2 * PAD;
        left = (width - w) / 2;
        top = Math.max(4, (height - h) / 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(key, scan))) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        int mx = left + PAD + SHEET_WIDTH + PAD;
        int my = top + PAD;
        if (plant.isPresent() && x >= mx && x < mx + MODEL && y >= my && y < my + MODEL) {
            yaw += (float) dx * 2;
            pitch = Math.max(-10, Math.min(80, pitch + (float) dy));
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Guarded.run("lens window render", "client", () -> {
            int w = SHEET_WIDTH + 2 * PAD + (plant.isPresent() ? MODEL + PAD : 0);
            int h = Math.max(sheetHeight, plant.isPresent() ? MODEL : 0) + 2 * PAD;
            LensSheetView.paper(g, left, top, left + w, top + h);
            LensSheetView.draw(g, font, sheet, left + PAD, top + PAD, SHEET_WIDTH);
            plant.ifPresent(p -> {
                int mx = left + PAD + SHEET_WIDTH + PAD;
                int my = top + PAD;
                g.fill(mx, my, mx + MODEL, my + MODEL, 0x183A2A1A);
                g.renderOutline(mx, my, MODEL, MODEL, JournalColors.INK_FADED);
                g.enableScissor(mx + 1, my + 1, mx + MODEL - 1, my + MODEL - 1);
                renderModel(g, p, mx + MODEL / 2, my + MODEL / 2 + 8);
                g.disableScissor();
            });
        });
        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * The plant on a farmland block, through the block render dispatcher
     * with a GUI pose (the journal's scene engine is package-private and is
     * not changed here).
     */
    private void renderModel(GuiGraphics g, BlockState p, int cx, int cy) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 150);
        ps.scale(MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(-0.5, -0.5, -0.5);
        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var buffers = g.bufferSource();
        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        dispatcher.renderSingleBlock(farmland, ps, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        ps.translate(0, 1, 0);
        dispatcher.renderSingleBlock(p, ps, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        g.flush();
        Lighting.setupFor3DItems();
        ps.popPose();
    }
}
