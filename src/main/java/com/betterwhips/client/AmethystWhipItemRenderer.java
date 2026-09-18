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

public final class AmethystWhipItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID, "textures/item/amethyst_whip.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);

    public AmethystWhipItemRenderer(BlockEntityRenderDispatcher blockEntityRenderDispatcher,
                                      EntityModelSet entityModelSet) {
        super(blockEntityRenderDispatcher, entityModelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (context == ItemDisplayContext.GUI) {
            WhipGuiFastRenderer.render(
                    WhipGuiFastRenderer.Style.AMETHYST, poseStack, bufferSource, packedOverlay);
            return;
        }
        if (context == ItemDisplayContext.GROUND) {
            WhipGuiFastRenderer.renderDropped(
                    WhipGuiFastRenderer.Style.AMETHYST, poseStack, bufferSource, packedOverlay);
            return;
        }
        boolean held = context.firstPerson()
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        poseStack.pushPose();

        poseStack.translate(0.5F, 0.5F, 0.5F);
        applyDisplayTransform(context, poseStack);

        AmethystWhipGeometry.applyAuthoredRootTransform(poseStack);

        AmethystWhipPhysics.RenderContext renderContext =
                AmethystWhipPhysics.currentRenderContext();
        boolean authoredStoredHeld = held
                && AmethystWhipPhysics.shouldRenderAuthoredStoredLash(renderContext);
        if (held && renderContext != null) {

            AmethystWhipPhysics.captureRenderedSocket(poseStack, renderContext);
        }

        VertexConsumer consumer = bufferSource.getBuffer(RENDER_TYPE);
        AmethystWhipGeometry.INSTANCE.renderHandle(
                poseStack, consumer, packedLight, packedOverlay);
        if (context.firstPerson() && renderContext != null) {
            AmethystWhipPhysics.renderFirstPersonHandLash(
                    poseStack, renderContext, consumer, packedLight, packedOverlay);
        }
        if (!held || (!context.firstPerson() && authoredStoredHeld)) {

            AmethystWhipGeometry.INSTANCE.renderStoredCoil(
                    poseStack, consumer, packedLight, packedOverlay);
        }

        boolean renderFancy = WhipPerformanceTuning.allowItemFancy("amethyst", context);
        if (renderFancy) {

            VertexConsumer spectralConsumer = bufferSource.getBuffer(
                    AmethystWhipPhysics.spectralRenderType());
            AmethystWhipGeometry.INSTANCE.renderHandleCrystalSpectral(
                    poseStack, spectralConsumer, LightTexture.FULL_BRIGHT, packedOverlay, 1.20F,
                    178, 132, 255, 96);
            if (context.firstPerson() && renderContext != null) {
                AmethystWhipPhysics.renderFirstPersonHandSpectralLash(
                        poseStack, renderContext, spectralConsumer, packedOverlay);
            }
            if (!held || (!context.firstPerson() && authoredStoredHeld)) {
                AmethystWhipGeometry.INSTANCE.renderAuthoredLashScaledPerPart(
                        poseStack, spectralConsumer, LightTexture.FULL_BRIGHT, packedOverlay, 1.20F,
                        178, 132, 255, 96);
            }
        }

        if (context.firstPerson() && renderContext != null) {
            VertexConsumer magicConsumer = bufferSource.getBuffer(
                    AmethystWhipPhysics.bladeMagicRenderType());
            AmethystWhipPhysics.renderFirstPersonBladeConnectors(
                    poseStack, renderContext, magicConsumer);
            AmethystWhipPhysics.renderFirstPersonTipTrail(
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
