package io.github.slugblue.wildroots.client.journal.scene;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Draws the 3D part of one scene in the GUI (journal v2 §5.8): the block
 * mesh and the bees in the scene pose, inside a scissor on the window,
 * above the page in z. The pose: to the window centre, scaled so that the
 * structure's bounding sphere fits the window at zoom 1, pitch, yaw.
 * Overlays (items, icons, labels, highlights) are drawn flat by the page
 * view at {@link #project projected} points.
 *
 * <p>One scene is live at a time: drawing a renderer closes the buffers
 * of any other ({@link #closeLive}).
 */
@OnlyIn(Dist.CLIENT)
final class SceneRenderer implements AutoCloseable {

    /** Depth of the scene's centre above the page (GUI z). */
    static final float Z = 400;

    private static SceneRenderer live;

    private final SceneDef<BlockState> def;
    private final BlockState[] scratch;
    private SceneMesh mesh;
    private long builtKey = Long.MIN_VALUE;
    /** GUI tick of the last mesh build: at most one rebuild a tick (§5.8), also while the slider is dragged. */
    private int builtTick = Integer.MIN_VALUE;
    private SceneBee bee;

    SceneRenderer(SceneDef<BlockState> def) {
        this.def = def;
        this.scratch = new BlockState[def.volume()];
    }

    /** Closes the live scene's GPU buffers (page turned, book closed, resources reloaded). */
    static void closeLive() {
        if (live != null) {
            live.close();
        }
    }

    static int liveBuffers() {
        return SceneMesh.liveBuffers();
    }

    static long liveBytes() {
        return SceneMesh.liveBytes();
    }

    SceneMesh mesh() {
        return mesh;
    }

    /** Pixels per block at {@code zoom} for a window of {@code w × h}. */
    float scale(float zoom, int w, int h) {
        double r = 0.5 * Math.sqrt(def.sizeX() * def.sizeX() + def.sizeY() * def.sizeY() + def.sizeZ() * def.sizeZ());
        return (float) (Math.min(w, h) / (2 * r) * zoom);
    }

    /** Applies the scene pose to {@code pose} (GUI pixels → structure coordinates). */
    void applyPose(PoseStack pose, SceneCamera cam, int wx, int wy, int ww, int wh) {
        float s = scale(cam.zoom(), ww, wh);
        pose.translate(wx + ww / 2f, wy + wh / 2f, Z);
        // y up on screen; the mirror on z keeps the determinant positive (face culling stays right)
        pose.scale(s, -s, -s);
        pose.mulPose(Axis.XP.rotationDegrees(-cam.pitch()));
        pose.mulPose(Axis.YP.rotationDegrees(cam.yaw() + 180));
        pose.translate(-def.sizeX() / 2f, -def.sizeY() / 2f, -def.sizeZ() / 2f);
    }

    /** Reused every frame (review m4): one pose and one matrix per renderer. */
    private final PoseStack matrixPose = new PoseStack();
    private final Matrix4f matrix = new Matrix4f();

    /** The scene pose as a matrix; the same object every call, valid until the next call. */
    Matrix4f matrix(SceneCamera cam, int wx, int wy, int ww, int wh) {
        matrixPose.last().pose().identity();
        matrixPose.last().normal().identity();
        applyPose(matrixPose, cam, wx, wy, ww, wh);
        return matrix.set(matrixPose.last().pose());
    }

    /** Render thread only: the scratch vector of {@link #project}. */
    private static final Vector4f PROJECT = new Vector4f();

    /** Where a structure point lands on the screen (GUI x, y), written into {@code out}; render thread only. */
    static float[] project(Matrix4f m, double x, double y, double z, float[] out) {
        m.transform(PROJECT.set((float) x, (float) y, (float) z, 1));
        out[0] = PROJECT.x();
        out[1] = PROJECT.y();
        return out;
    }

    /** A bee to draw: position in structure coordinates, heading, age. */
    record BeeAt(double x, double y, double z, float yaw, float age) {
    }

    /**
     * Rebuilds the mesh if {@code blocks} changed, then draws mesh and bees.
     * The caller holds the scissor; the depth inside it is cleared after,
     * so the flat overlays and the book's widgets draw on top.
     */
    void draw(GuiGraphics g, SceneTimeline timeline, SceneCamera cam, int wx, int wy, int ww, int wh,
              List<BeeAt> bees) {
        if (live != this) {
            closeLive();
            live = this;
        }
        long key = timeline.blocksKey();
        int tick = Minecraft.getInstance().gui.getGuiTicks();
        // a changed key waits for the next tick if this tick already built (review m3): the old mesh shows
        // for at most one tick while the slider is dragged at the frame rate
        if (mesh == null || key != builtKey && tick != builtTick) {
            int[] cells = timeline.blocks();
            List<BlockState> states = def.states();
            for (int i = 0; i < cells.length; i++) {
                scratch[i] = states.get(cells[i]);
            }
            if (mesh == null) {
                mesh = new SceneMesh();
            }
            mesh.build(new SceneBlockGetter(def.sizeX(), def.sizeY(), def.sizeZ(), scratch.clone()));
            builtKey = key;
            builtTick = tick;
        }
        g.flush();
        float fog = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            applyPose(pose, cam, wx, wy, ww, wh);
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
            RenderSystem.enableDepthTest();
            mesh.draw(modelView, RenderSystem.getProjectionMatrix());
            if (!bees.isEmpty()) {
                if (bee == null) {
                    bee = new SceneBee();
                }
                Lighting.setupForEntityInInventory();
                for (BeeAt b : bees) {
                    bee.render(pose, g.bufferSource(), b.x(), b.y(), b.z(), b.yaw(), b.age());
                }
                g.flush();
            }
        } finally {
            pose.popPose();
            Lighting.setupFor3DItems();
            RenderSystem.setShaderFogStart(fog);
        }
        clearDepth();
    }

    /** Clears depth inside the current scissor (GL clears respect the scissor test). */
    static void clearDepth() {
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    @Override
    public void close() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
        builtKey = Long.MIN_VALUE;
        builtTick = Integer.MIN_VALUE;
        if (live == this) {
            live = null;
        }
    }
}
