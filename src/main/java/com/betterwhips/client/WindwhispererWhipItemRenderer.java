package com.betterwhips.client;

import com.betterwhips.BetterWhipsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class WindwhispererWhipItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID, "textures/item/windwhisperer_whip.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);

    public WindwhispererWhipItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (context == ItemDisplayContext.GUI) {
            WhipGuiFastRenderer.render(WhipGuiFastRenderer.Style.VINE, poseStack, bufferSource, packedOverlay);
            return;
        }
        if (context == ItemDisplayContext.GROUND) {
            WhipGuiFastRenderer.renderDropped(WhipGuiFastRenderer.Style.VINE, poseStack, bufferSource, packedOverlay);
            return;
        }
        boolean held = context.firstPerson()
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        applyDisplayTransform(context, poseStack);
        WindwhispererWhipGeometry.applyAuthoredRootTransform(poseStack);

        WindwhispererWhipPhysics.RenderContext renderContext =
                WindwhispererWhipPhysics.currentRenderContext();
        if (held && renderContext != null) {
            WindwhispererWhipPhysics.captureRenderedSocket(poseStack, renderContext);
        }

        VertexConsumer consumer = bufferSource.getBuffer(RENDER_TYPE);
        WindwhispererWhipGeometry.INSTANCE.renderHandle(
                poseStack, consumer, packedLight, packedOverlay);
        if (context.firstPerson() && renderContext != null) {
            WindwhispererWhipPhysics.renderFirstPersonHandLash(
                    poseStack, renderContext, consumer, packedOverlay);
        }
        if (!held) {
            WindwhispererWhipGeometry.INSTANCE.renderStoredCoil(
                    poseStack, consumer, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private static void applyDisplayTransform(ItemDisplayContext context, PoseStack stack) {
        if (context.firstPerson()) {
            stack.translate(0.0F, -0.18F, 0.0F);
            return;
        }
        if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            stack.translate(0.0F, -0.17F, 0.0F);
            return;
        }
        if (context == ItemDisplayContext.GUI) {
            stack.translate(-0.05F, -0.16F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(22.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-28.0F));
            stack.scale(0.72F, 0.72F, 0.72F);
            return;
        }
        if (context == ItemDisplayContext.GROUND) {
            stack.translate(0.0F, -0.22F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(20.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-25.0F));
            stack.scale(0.72F, 0.72F, 0.72F);
            return;
        }
        if (context == ItemDisplayContext.FIXED) {
            stack.translate(0.0F, -0.24F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(18.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-24.0F));
            stack.scale(0.62F, 0.62F, 0.62F);
            return;
        }
        if (context == ItemDisplayContext.HEAD) {
            stack.translate(0.0F, -0.28F, 0.0F);
            stack.scale(0.55F, 0.55F, 0.55F);
            return;
        }
        stack.translate(0.0F, -0.26F, 0.0F);
        stack.mulPose(Axis.ZP.rotationDegrees(-26.0F));
        stack.scale(0.62F, 0.62F, 0.62F);
    }
}
