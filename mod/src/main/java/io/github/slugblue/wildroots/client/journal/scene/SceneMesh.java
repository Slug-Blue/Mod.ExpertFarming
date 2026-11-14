package io.github.slugblue.wildroots.client.journal.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * The block mesh of a scene (journal v2 §5.8): one {@link VertexBuffer}
 * per chunk render type that has quads (solid, cutout_mipped, cutout,
 * translucent: at most 4), built with
 * {@link BlockRenderDispatcher#renderBatched} over a
 * {@link SceneBlockGetter}. Rebuilt only when the blocks change; closed
 * when the scene is hidden or the resources reload. Render thread only.
 */
@OnlyIn(Dist.CLIENT)
final class SceneMesh implements AutoCloseable {

    /** One builder for every scene mesh, reused (vanilla keeps one per render type the same way). */
    private static BufferBuilder builder;
    /** Open buffers of every mesh: 0 once no scene is visible (debug counter, §5.8). */
    private static int liveBuffers;
    private static long liveBytes;

    private final Map<RenderType, VertexBuffer> buffers = new LinkedHashMap<>();
    private final Map<RenderType, Integer> bytes = new LinkedHashMap<>();
    private long lastBuildNanos;
    private int builds;

    static int liveBuffers() {
        return liveBuffers;
    }

    static long liveBytes() {
        return liveBytes;
    }

    long lastBuildNanos() {
        return lastBuildNanos;
    }

    int builds() {
        return builds;
    }

    long bytes() {
        long b = 0;
        for (int v : bytes.values()) {
            b += v;
        }
        return b;
    }

    /** Builds (or rebuilds) the mesh of {@code level}; on a throw the shared builder is left clean. */
    void build(SceneBlockGetter level) {
        try {
            buildLayers(level);
        } catch (RuntimeException e) {
            if (builder != null && builder.building()) {
                BufferBuilder.RenderedBuffer left = builder.endOrDiscardIfEmpty();
                if (left != null) {
                    left.release();
                }
            }
            throw e;
        }
    }

    private void buildLayers(SceneBlockGetter level) {
        long t0 = System.nanoTime();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        if (builder == null) {
            builder = new BufferBuilder(64 * 1024);
        }
        RandomSource random = RandomSource.create();
        PoseStack pose = new PoseStack();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (RenderType type : RenderType.chunkBufferLayers()) {
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (int y = 0; y < level.sizeY(); y++) {
                for (int z = 0; z < level.sizeZ(); z++) {
                    for (int x = 0; x < level.sizeX(); x++) {
                        pos.set(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
                            continue;
                        }
                        BakedModel model = dispatcher.getBlockModel(state);
                        long seed = state.getSeed(pos);
                        random.setSeed(seed);
                        if (!model.getRenderTypes(state, random, ModelData.EMPTY).contains(type)) {
                            continue;
                        }
                        random.setSeed(seed);
                        pose.pushPose();
                        pose.translate(x, y, z);
                        dispatcher.renderBatched(state, pos, level, pose, builder, true, random, ModelData.EMPTY,
                                type);
                        pose.popPose();
                    }
                }
            }
            BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
            VertexBuffer vb = buffers.get(type);
            if (rendered == null) {
                if (vb != null) {
                    closeOne(type, vb);
                }
                continue;
            }
            int size = rendered.drawState().vertexBufferSize();
            if (vb == null) {
                vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffers.put(type, vb);
                liveBuffers++;
            } else {
                liveBytes -= bytes.getOrDefault(type, 0);
            }
            vb.bind();
            vb.upload(rendered);
            VertexBuffer.unbind();
            bytes.put(type, size);
            liveBytes += size;
        }
        builds++;
        lastBuildNanos = System.nanoTime() - t0;
    }

    /** Draws the mesh with {@code modelView} (screen view times the scene pose). */
    void draw(Matrix4f modelView, Matrix4f projection) {
        for (Map.Entry<RenderType, VertexBuffer> e : buffers.entrySet()) {
            RenderType type = e.getKey();
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                if (shader.CHUNK_OFFSET != null) {
                    shader.CHUNK_OFFSET.set(0f, 0f, 0f);
                }
                VertexBuffer vb = e.getValue();
                vb.bind();
                vb.drawWithShader(modelView, projection, shader);
                VertexBuffer.unbind();
            }
            type.clearRenderState();
        }
    }

    boolean isEmpty() {
        return buffers.isEmpty();
    }

    private void closeOne(RenderType type, VertexBuffer vb) {
        vb.close();
        buffers.remove(type);
        liveBuffers--;
        liveBytes -= bytes.getOrDefault(type, 0);
        bytes.remove(type);
    }

    @Override
    public void close() {
        for (Map.Entry<RenderType, VertexBuffer> e : Map.copyOf(buffers).entrySet()) {
            closeOne(e.getKey(), e.getValue());
        }
    }
}
