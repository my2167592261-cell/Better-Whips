package com.betterwhips.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.betterwhips.BetterWhipsMod;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class DestructionWhipItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID, "textures/item/destruction_whip.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);

    public DestructionWhipItemRenderer(BlockEntityRenderDispatcher blockEntityRenderDispatcher,
                                      EntityModelSet entityModelSet) {
        super(blockEntityRenderDispatcher, entityModelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (context == ItemDisplayContext.GUI) {
            WhipGuiFastRenderer.render(
                    WhipGuiFastRenderer.Style.DESTRUCTION, poseStack, bufferSource, packedOverlay);
            return;
        }
        if (context == ItemDisplayContext.GROUND) {
            WhipGuiFastRenderer.renderDropped(
                    WhipGuiFastRenderer.Style.DESTRUCTION, poseStack, bufferSource, packedOverlay);
            return;
        }
        boolean held = context.firstPerson()
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        poseStack.pushPose();

        poseStack.translate(0.5F, 0.5F, 0.5F);
        applyDisplayTransform(context, poseStack);

        DestructionWhipGeometry.applyAuthoredRootTransform(poseStack);

        DestructionWhipPhysics.RenderContext renderContext =
                DestructionWhipPhysics.currentRenderContext();
        if (held && renderContext != null) {

            poseStack.mulPose(Axis.ZP.rotationDegrees(
                    DestructionWhipPhysics.currentTangentialHorizontalDegrees(renderContext)));
            poseStack.mulPose(Axis.XP.rotationDegrees(
                    -DestructionWhipPhysics.currentTangentialVerticalDegrees(renderContext)));

            DestructionWhipPhysics.captureRenderedSocket(poseStack, renderContext);
        }

        VertexConsumer consumer = bufferSource.getBuffer(RENDER_TYPE);
        DestructionWhipGeometry.INSTANCE.renderHandle(
                poseStack, consumer, packedLight, packedOverlay);
        if (context.firstPerson() && renderContext != null) {
            DestructionWhipPhysics.renderFirstPersonHandLash(
                    poseStack, renderContext, consumer, packedLight, packedOverlay);
        }
        if (!held) {
            DestructionWhipGeometry.INSTANCE.renderStoredCoil(
                    poseStack, consumer, packedLight, packedOverlay);
        }

        if (held && renderContext != null) {
            int pulseFrame = DestructionWhipPhysics.currentPulseFrame(renderContext);
            VertexConsumer pulseConsumer = bufferSource.getBuffer(
                    DestructionWhipPhysics.pulseRenderType());
            DestructionWhipGeometry.INSTANCE.renderHandleEnergyGradient(
                    poseStack, pulseConsumer, LightTexture.FULL_BRIGHT, packedOverlay,
                    DestructionWhipPhysics.REDSTONE_BASE_LEFT_RED,
                    DestructionWhipPhysics.REDSTONE_BASE_LEFT_GREEN,
                    DestructionWhipPhysics.REDSTONE_BASE_LEFT_BLUE,
                    DestructionWhipPhysics.REDSTONE_BASE_RIGHT_RED,
                    DestructionWhipPhysics.REDSTONE_BASE_RIGHT_GREEN,
                    DestructionWhipPhysics.REDSTONE_BASE_RIGHT_BLUE,
                    DestructionWhipPhysics.REDSTONE_BASE_ALPHA);
            int orangeAlpha = DestructionWhipPhysics.currentHandleOrangePulseAlpha(pulseFrame);
            if (orangeAlpha > 0) {
                DestructionWhipGeometry.INSTANCE.renderHandleEnergy(
                        poseStack, pulseConsumer, LightTexture.FULL_BRIGHT, packedOverlay,
                        255, 116, 12, orangeAlpha);
            }
            int yellowAlpha = DestructionWhipPhysics.currentHandleYellowPulseAlpha(pulseFrame);
            if (yellowAlpha > 0) {
                DestructionWhipGeometry.INSTANCE.renderHandleEnergy(
                        poseStack, pulseConsumer, LightTexture.FULL_BRIGHT, packedOverlay,
                        255, 238, 72, yellowAlpha);
            }
            if (context.firstPerson()) {
                DestructionWhipPhysics.renderFirstPersonHandPulseLash(
                        poseStack, renderContext, pulseConsumer, packedOverlay, pulseFrame);
            }
        }

        if (context.firstPerson() && renderContext != null) {
            VertexConsumer magicConsumer = bufferSource.getBuffer(
                    DestructionWhipPhysics.bladeMagicRenderType());
            DestructionWhipPhysics.renderFirstPersonBladeConnectors(
                    poseStack, renderContext, magicConsumer);
            DestructionWhipPhysics.renderFirstPersonTipTrail(
                    poseStack, renderContext, magicConsumer);
        }
        poseStack.popPose();
    }

    private static void applyDisplayTransform(ItemDisplayContext context, PoseStack stack) {
        if (context.firstPerson()) {
            stack.translate(0.0F, -0.18F, 0.0F);
            stack.scale(1.0F, 1.0F, 1.0F);
            return;
        }
        if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            stack.translate(0.0F, -0.17F, 0.0F);
            stack.scale(1.0F, 1.0F, 1.0F);
            return;
        }
        if (context == ItemDisplayContext.GUI) {

            stack.translate(-0.06F, -0.08F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(24.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-34.0F));
            stack.scale(0.30F, 0.30F, 0.30F);
            return;
        }
        if (context == ItemDisplayContext.GROUND) {

            stack.translate(0.0F, -0.10F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(24.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-34.0F));
            stack.scale(0.34F, 0.34F, 0.34F);
            return;
        }
        if (context == ItemDisplayContext.FIXED) {
            stack.translate(0.0F, -0.34F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(18.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-28.0F));
            stack.scale(0.10F, 0.10F, 0.10F);
            return;
        }
        if (context == ItemDisplayContext.HEAD) {
            stack.translate(0.0F, -0.30F, 0.0F);
            stack.scale(0.10F, 0.10F, 0.10F);
            return;
        }
        stack.translate(0.0F, -0.32F, 0.0F);
        stack.mulPose(Axis.ZP.rotationDegrees(-30.0F));
        stack.scale(0.10F, 0.10F, 0.10F);
    }
}
