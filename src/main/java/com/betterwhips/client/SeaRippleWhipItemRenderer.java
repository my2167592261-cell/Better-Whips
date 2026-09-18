package com.betterwhips.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.betterwhips.BetterWhipsMod;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class SeaRippleWhipItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID, "textures/item/untamed_sea_whip.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);

    public SeaRippleWhipItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        boolean held = context.firstPerson()
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        applyDisplayTransform(context, poseStack);

        SeaRippleWhipPhysics.RenderContext renderContext = SeaRippleWhipPhysics.currentRenderContext();
        if (context.firstPerson() && renderContext != null) {

            SeaRippleWhipPhysics.alignFirstPersonGripToAttackVfx(poseStack, renderContext);
        }
        if (held && renderContext != null) {
            SeaRippleWhipPhysics.captureRenderedSocket(poseStack, renderContext);
        }

        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        SeaRippleWhipGeometry.INSTANCE.renderHandle(poseStack, consumer, packedLight, packedOverlay);
        if (context.firstPerson() && renderContext != null) {
            SeaRippleWhipPhysics.renderFirstPersonHandLash(
                    poseStack, renderContext, consumer, packedOverlay);
        } else if (!held) {
            SeaRippleWhipGeometry.INSTANCE.renderAuthoredLash(
                    poseStack, consumer, packedLight, packedOverlay);
        }
        if (context.firstPerson() && renderContext != null) {

            if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(RENDER_TYPE);
            SeaRippleWhipPhysics.renderFirstPersonWater(poseStack, renderContext, buffers);
        }
        poseStack.popPose();
    }

    private static void applyDisplayTransform(ItemDisplayContext context, PoseStack stack) {
        if (context.firstPerson()) {
            stack.translate(0.0F, -0.18F, 0.0F);

            stack.mulPose(Axis.YP.rotationDegrees(90.0F));
            return;
        }
        if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            stack.translate(0.0F, -0.17F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(90.0F));
            return;
        }
        if (context == ItemDisplayContext.GUI) {
            stack.translate(0.02F, -0.18F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(24.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-35.0F));
            stack.scale(0.17F, 0.17F, 0.17F);
            return;
        }
        if (context == ItemDisplayContext.GROUND) {
            stack.translate(0.0F, -0.20F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(24.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-28.0F));
            stack.scale(0.23F, 0.23F, 0.23F);
            return;
        }
        if (context == ItemDisplayContext.FIXED) {
            stack.translate(0.0F, -0.28F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(18.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-28.0F));
            stack.scale(0.16F, 0.16F, 0.16F);
            return;
        }
        stack.scale(0.16F, 0.16F, 0.16F);
    }
}
