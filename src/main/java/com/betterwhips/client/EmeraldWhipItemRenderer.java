package com.betterwhips.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.betterwhips.BetterWhipsMod;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class EmeraldWhipItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID, "textures/item/emerald_whip.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);

    public EmeraldWhipItemRenderer(BlockEntityRenderDispatcher blockEntityRenderDispatcher,
                                      EntityModelSet entityModelSet) {
        super(blockEntityRenderDispatcher, entityModelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (context == ItemDisplayContext.GUI) {
            WhipGuiFastRenderer.render(
                    WhipGuiFastRenderer.Style.EMERALD, poseStack, bufferSource, packedOverlay);
            return;
        }
        if (context == ItemDisplayContext.GROUND) {
            WhipGuiFastRenderer.renderDropped(
                    WhipGuiFastRenderer.Style.EMERALD, poseStack, bufferSource, packedOverlay);
            return;
        }
        boolean held = context.firstPerson()
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        poseStack.pushPose();

        poseStack.translate(0.5F, 0.5F, 0.5F);
        applyDisplayTransform(context, poseStack);

        EmeraldWhipGeometry.applyAuthoredRootTransform(poseStack);

        EmeraldWhipPhysics.RenderContext renderContext =
                EmeraldWhipPhysics.currentRenderContext();
        boolean authoredStoredHeld = held
                && EmeraldWhipPhysics.shouldRenderAuthoredStoredLash(renderContext);
        if (held && renderContext != null) {

            EmeraldWhipPhysics.captureRenderedSocket(poseStack, renderContext);
        }

        VertexConsumer consumer = bufferSource.getBuffer(RENDER_TYPE);
        EmeraldWhipGeometry.INSTANCE.renderHandle(
                poseStack, consumer, packedLight, packedOverlay);
        if (context.firstPerson() && renderContext != null) {
            EmeraldWhipPhysics.renderFirstPersonHandLash(
                    poseStack, renderContext, consumer, packedLight, packedOverlay);
        }
        if (!held || (!context.firstPerson() && authoredStoredHeld)) {

            EmeraldWhipGeometry.INSTANCE.renderStoredCoil(
                    poseStack, consumer, packedLight, packedOverlay);
        }

        boolean renderFancy = WhipPerformanceTuning.allowItemFancy("emerald", context);
        if (renderFancy) {

            VertexConsumer spectralConsumer = bufferSource.getBuffer(
                    EmeraldWhipPhysics.spectralRenderType());
            if (context.firstPerson() && renderContext != null) {
                EmeraldWhipPhysics.renderFirstPersonHandSpectralLash(
                        poseStack, renderContext, spectralConsumer, packedOverlay);
            }
            if (!held || (!context.firstPerson() && authoredStoredHeld)) {
                EmeraldWhipGeometry.INSTANCE.renderAuthoredLashScaledPerPart(
                        poseStack, spectralConsumer, LightTexture.FULL_BRIGHT, packedOverlay, 1.20F,
                        112, 255, 170, 96);
            }
        }

        if (context.firstPerson() && renderContext != null) {
            VertexConsumer magicConsumer = bufferSource.getBuffer(
                    EmeraldWhipPhysics.bladeMagicRenderType());
            EmeraldWhipPhysics.renderFirstPersonBladeConnectors(
                    poseStack, renderContext, magicConsumer);
            EmeraldWhipPhysics.renderFirstPersonTipTrail(
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

            stack.translate(-0.375F, -0.30F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(24.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-34.0F));
            stack.scale(1.00F, 1.00F, 1.00F);
            return;
        }
        if (context == ItemDisplayContext.GROUND) {

            stack.translate(0.0F, -0.24F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(24.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-34.0F));
            stack.scale(1.00F, 1.00F, 1.00F);
            return;
        }
        if (context == ItemDisplayContext.FIXED) {
            stack.translate(0.0F, -0.34F, 0.0F);
            stack.mulPose(Axis.YP.rotationDegrees(18.0F));
            stack.mulPose(Axis.ZP.rotationDegrees(-28.0F));
            stack.scale(0.18F, 0.18F, 0.18F);
            return;
        }
        if (context == ItemDisplayContext.HEAD) {
            stack.translate(0.0F, -0.30F, 0.0F);
            stack.scale(0.18F, 0.18F, 0.18F);
            return;
        }
        stack.translate(0.0F, -0.32F, 0.0F);
        stack.mulPose(Axis.ZP.rotationDegrees(-30.0F));
        stack.scale(0.18F, 0.18F, 0.18F);
    }
}
