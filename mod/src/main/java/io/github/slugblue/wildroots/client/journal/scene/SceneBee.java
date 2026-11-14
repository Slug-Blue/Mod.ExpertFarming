package io.github.slugblue.wildroots.client.journal.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BeeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Bee;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The vanilla bee model without an entity (journal v2 §5.8): baked from
 * {@link ModelLayers#BEE} of the game's {@code EntityModelSet}, with the
 * vanilla texture, wings flapping as in {@code BeeModel#setupAnim} for a
 * flying bee. Nothing of {@code Bee} is created; the model's own
 * {@code setupAnim} is never called.
 */
@OnlyIn(Dist.CLIENT)
final class SceneBee {

    static final ResourceLocation TEXTURE = io.github.slugblue.wildroots.core.Ids.of("minecraft", "textures/entity/bee/bee.png");
    /** A bee in the scene, in blocks per model unit block: a little smaller than in the world. */
    private static final float SCALE = 0.55f;

    private final BeeModel<Bee> model;
    private final ModelPart rightWing;
    private final ModelPart leftWing;
    private final ModelPart[] legs;

    SceneBee() {
        ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.BEE);
        this.model = new BeeModel<>(root);
        ModelPart bone = root.getChild("bone");
        this.rightWing = bone.getChild("right_wing");
        this.leftWing = bone.getChild("left_wing");
        this.legs = new ModelPart[] {bone.getChild("front_legs"), bone.getChild("middle_legs"),
                bone.getChild("back_legs")};
    }

    /**
     * Draws one bee in scene coordinates at ({@code x, y, z}) heading
     * {@code yaw} (degrees, 0 = +z); {@code age} drives the wings.
     */
    void render(PoseStack pose, MultiBufferSource buffers, double x, double y, double z, float yaw, float age) {
        float flap = Mth.cos(age * 120.32113f * Mth.DEG_TO_RAD) * Mth.PI * 0.15f;
        rightWing.xRot = 0;
        rightWing.yRot = 0;
        rightWing.zRot = flap;
        leftWing.xRot = 0;
        leftWing.yRot = 0;
        leftWing.zRot = -flap;
        for (ModelPart leg : legs) {
            leg.xRot = Mth.PI / 4f;
        }
        pose.pushPose();
        pose.translate(x, y + Math.sin(age * 0.25f) * 0.05, z);
        // entity yaw is -heading; LivingEntityRenderer turns by 180 - entity yaw
        pose.mulPose(Axis.YP.rotationDegrees(180 + yaw));
        // as LivingEntityRenderer the model is y-down; its body centre sits about 1.2 below the origin
        pose.scale(-SCALE, -SCALE, SCALE);
        pose.translate(0, -1.2f, 0);
        VertexConsumer vc = buffers.getBuffer(model.renderType(TEXTURE));
        model.renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
    }
}
