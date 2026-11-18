package io.github.slugblue.wildroots.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.slugblue.wildroots.WildRoots;
import io.github.slugblue.wildroots.butterfly.ButterflyEntity;
import io.github.slugblue.wildroots.core.Ids;
import io.github.slugblue.wildroots.core.registry.ModEntities;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Four scalloped wings, a slim body and antennae, using palette-coloured voxel geometry. */
@Mod.EventBusSubscriber(modid = WildRoots.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ButterflyRendering {
    private static final ModelLayerLocation LAYER = new ModelLayerLocation(Ids.mod("butterfly"), "main");
    // A neutral vanilla surface lets each mesh region have its own palette without a blurry UV atlas.
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
    private ButterflyRendering() { }

    @SubscribeEvent
    public static void butterflyLayers(EntityRenderersEvent.RegisterLayerDefinitions event) { event.registerLayerDefinition(LAYER, Model::layer); }

    @SubscribeEvent
    public static void butterflyRenderer(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(ModEntities.BUTTERFLY.get(), Renderer::new); }

    private static final class Renderer extends MobRenderer<ButterflyEntity, Model> {
        Renderer(EntityRendererProvider.Context context) { super(context, new Model(context.bakeLayer(LAYER)), .10f); }
        @Override public ResourceLocation getTextureLocation(ButterflyEntity entity) { return TEXTURE; }
        @Override protected void setupRotations(ButterflyEntity entity, PoseStack pose, float age, float yaw, float partialTick) {
            super.setupRotations(entity, pose, age, yaw, partialTick);
            if (entity.hanging()) {
                pose.translate(0, entity.getBbHeight(), 0);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
            }
        }
    }

    private static final class Model extends EntityModel<ButterflyEntity> {
        private static final String[] SILHOUETTE = {
                "       ####", "      #####", "     ######", "    #######", "   ########",
                "  #########", " ##########", "###########", " #######   ",
                "  #######  ", " ########  ", " ########  ", "  ######   ", "   ####    "
        };
        // Forest, meadow and cabbage palettes: edge, main wing, veins/highlights, spots.
        private static final int[][] PALETTES = {
                {0x281B16, 0xF8790B, 0xFFAA25, 0xFFF2CB},
                {0x174654, 0x78D3DF, 0xA8EDF0, 0xF0FFFF},
                {0xD5D99B, 0xFAFCEB, 0xEDF09D, 0x4B354B}
        };
        private final ModelPart body, left, right;
        private int variant;
        Model(ModelPart root) { body = root.getChild("body"); left = root.getChild("left"); right = root.getChild("right"); }

        private static boolean pixel(int x, int z) {
            return z >= 0 && z < SILHOUETTE.length && x >= 0 && x < 11 && SILHOUETTE[z].charAt(x) == '#';
        }

        private static int region(int x, int z) {
            if (!pixel(x - 1, z) || !pixel(x + 1, z) || !pixel(x, z - 1) || !pixel(x, z + 1)) return 0;
            if (z == 3 && x == 8 || z == 11 && x == 4) return 3;
            if (x == 10 - z || z == 7 || z > 9 && x == 3) return 2;
            return 1;
        }

        private static void wing(PartDefinition root, String name, boolean mirror) {
            var wing = root.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(mirror ? -.4f : .4f, 23.25f, 0));
            for (int region = 0; region < 4; region++) {
                var cubes = CubeListBuilder.create().texOffs(0, 0);
                for (int z = 0; z < SILHOUETTE.length; z++) for (int x = 0; x < 11; x++) {
                    if (!pixel(x, z) || region(x, z) != region) continue;
                    int end = x + 1;
                    while (end < 11 && pixel(end, z) && region(end, z) == region) end++;
                    float size = (end - x) * .48f;
                    cubes.addBox(mirror ? -end * .48f : x * .48f, 0, (z - 7) * .48f, size, .12f, .48f);
                    x = end - 1;
                }
                wing.addOrReplaceChild("colour" + region, cubes, PartPose.ZERO);
            }
        }

        static LayerDefinition layer() {
            MeshDefinition mesh = new MeshDefinition();
            var root = mesh.getRoot();
            var body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-.34f, -.45f, -1.4f, .68f, .7f, 2.8f)
                    .addBox(-.48f, -.6f, -1.7f, .96f, .8f, .8f), PartPose.offset(0, 23.55f, 0));
            body.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(0, 0).addBox(-.09f, -1.3f, -.09f, .18f, 1.4f, .18f),
                    PartPose.offsetAndRotation(-.3f, -.5f, -1.5f, .5f, 0, -.5f));
            body.addOrReplaceChild("antenna_right", CubeListBuilder.create().texOffs(0, 0).addBox(-.09f, -1.3f, -.09f, .18f, 1.4f, .18f),
                    PartPose.offsetAndRotation(.3f, -.5f, -1.5f, .5f, 0, .5f));
            wing(root, "left", false); wing(root, "right", true);
            return LayerDefinition.create(mesh, 16, 16);
        }

        @Override public void setupAnim(ButterflyEntity entity, float limbSwing, float amount, float age, float yaw, float pitch) {
            variant = entity.variant();
            float fold = (entity.resting() || entity.feeding()) ? 1.28f + (float) Math.sin(age * .07) * .025f
                    : entity.flying() ? .48f + (float) Math.sin(age * 1.25 + entity.getId() * .7) * .7f : 1.05f;
            left.zRot = -fold; right.zRot = fold;
            // The feet/body stay at ground level when perched, without a model-space hover offset.
            body.xRot = entity.flying() ? (float) Math.sin(age * .18) * .045f : 0;
        }

        private static void coloured(ModelPart part, int colour, PoseStack pose, VertexConsumer vertices,
                                      int light, int overlay, float red, float green, float blue, float alpha) {
            part.render(pose, vertices, light, overlay, red * ((colour >> 16) & 255) / 255f,
                    green * ((colour >> 8) & 255) / 255f, blue * (colour & 255) / 255f, alpha);
        }

        @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay,
                                              float red, float green, float blue, float alpha) {
            coloured(body, 0x302725, pose, vertices, light, overlay, red, green, blue, alpha);
            int[] palette = PALETTES[Math.max(0, Math.min(2, variant))];
            for (ModelPart wing : new ModelPart[]{left, right}) {
                pose.pushPose(); wing.translateAndRotate(pose);
                for (int i = 0; i < 4; i++) coloured(wing.getChild("colour" + i), palette[i], pose, vertices, light, overlay, red, green, blue, alpha);
                pose.popPose();
            }
        }
    }
}
