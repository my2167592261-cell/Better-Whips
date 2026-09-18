package com.betterwhips.client;

import com.betterwhips.BetterWhipsMod;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

final class WhipGuiFastRenderer {
    enum Style {
        LEATHER,
        TRAINER,
        CHAIN,
        GOLDEN,
        ROYAL_SLIME,
        DIAMOND,
        EMERALD,
        AMETHYST,
        VINE,
        DESTRUCTION
    }

    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID, "textures/misc/vfx_white.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(WHITE_TEXTURE);

    private WhipGuiFastRenderer() {}

    static void render(Style style, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedOverlay) {
        poseStack.pushPose();

        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.translate(0.0F, -0.015F, 0.0F);
        renderStyle(style, poseStack, bufferSource, packedOverlay);
        poseStack.popPose();
    }

    static void renderDropped(Style style, PoseStack poseStack, MultiBufferSource bufferSource,
                              int packedOverlay) {
        poseStack.pushPose();

        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.translate(0.0F, -0.19F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(24.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-34.0F));
        poseStack.scale(0.78F, 0.78F, 0.78F);
        renderStyle(style, poseStack, bufferSource, packedOverlay);
        poseStack.popPose();
    }

    private static void renderStyle(Style style, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int packedOverlay) {
        VertexConsumer consumer = bufferSource.getBuffer(RENDER_TYPE);
        switch (style) {
            case LEATHER -> renderCoiledWhip(poseStack, consumer, packedOverlay,
                    92, 49, 27, 255,
                    144, 82, 44, 255,
                    0.064F, 2.30F);
            case TRAINER -> renderCoiledWhip(poseStack, consumer, packedOverlay,
                    72, 46, 32, 255,
                    178, 146, 103, 255,
                    0.068F, 2.35F);
            case CHAIN -> renderCoiledWhip(poseStack, consumer, packedOverlay,
                    63, 67, 74, 255,
                    154, 165, 177, 255,
                    0.057F, 2.55F);
            case GOLDEN -> renderCoiledWhip(poseStack, consumer, packedOverlay,
                    126, 79, 15, 255,
                    247, 194, 57, 255,
                    0.073F, 2.20F);
            case ROYAL_SLIME -> renderRoyalSlime(poseStack, consumer, packedOverlay);
            case DIAMOND -> renderDiamond(poseStack, consumer, packedOverlay);
            case EMERALD -> renderEmerald(poseStack, consumer, packedOverlay);
            case AMETHYST -> renderAmethyst(poseStack, consumer, packedOverlay);
            case VINE -> renderCoiledWhip(poseStack, consumer, packedOverlay,
                    31, 83, 35, 255,
                    95, 164, 70, 255,
                    0.060F, 2.45F);
            case DESTRUCTION -> renderDestruction(poseStack, consumer, packedOverlay);
        }
    }

    private static void renderCoiledWhip(PoseStack poseStack, VertexConsumer consumer, int overlay,
                                         int darkR, int darkG, int darkB, int darkA,
                                         int brightR, int brightG, int brightB, int brightA,
                                         float ropeWidth, float turns) {

        line(poseStack, consumer, overlay, -0.39F, -0.31F, -0.14F, -0.07F,
                0.115F, darkR, darkG, darkB, darkA, 0.000F);
        line(poseStack, consumer, overlay, -0.34F, -0.26F, -0.17F, -0.10F,
                0.035F, brightR, brightG, brightB, brightA, 0.003F);

        final int samples = 24;
        float prevX = -0.10F;
        float prevY = -0.02F;
        for (int i = 1; i <= samples; ++i) {
            float t = i / (float) samples;
            float angle = (float) (Math.PI * 2.0D * turns * t + Math.PI * 0.25D);
            float radius = 0.34F - 0.21F * t;
            float x = 0.045F + (float) Math.cos(angle) * radius;
            float y = 0.055F + (float) Math.sin(angle) * radius * 0.82F;
            line(poseStack, consumer, overlay, prevX, prevY, x, y,
                    ropeWidth, darkR, darkG, darkB, darkA, 0.000F);
            if ((i & 1) == 0) {
                line(poseStack, consumer, overlay, prevX, prevY, x, y,
                        ropeWidth * 0.30F, brightR, brightG, brightB, brightA, 0.004F);
            }
            prevX = x;
            prevY = y;
        }
    }

    private static void renderRoyalSlime(PoseStack poseStack, VertexConsumer consumer, int overlay) {
        renderCoiledWhip(poseStack, consumer, overlay,
                27, 103, 58, 235,
                91, 239, 135, 245,
                0.073F, 2.20F);
        disc(poseStack, consumer, overlay, 0.11F, 0.08F, 0.055F,
                161, 255, 182, 190, 0.007F);
        disc(poseStack, consumer, overlay, 0.25F, -0.04F, 0.040F,
                161, 255, 182, 155, 0.007F);
    }

    private static void renderDiamond(PoseStack poseStack, VertexConsumer consumer, int overlay) {

        line(poseStack, consumer, overlay, -0.39F, -0.34F, -0.18F, -0.13F,
                0.135F, 25, 50, 62, 255, 0.000F);
        line(poseStack, consumer, overlay, -0.34F, -0.29F, -0.20F, -0.15F,
                0.042F, 84, 192, 212, 255, 0.004F);

        float startX = -0.14F;
        float startY = -0.09F;
        float endX = 0.38F;
        float endY = 0.39F;
        final int pieces = 9;
        for (int i = 0; i < pieces; ++i) {
            float t0 = i / (float) pieces;
            float t1 = (i + 0.72F) / pieces;
            float x0 = lerp(startX, endX, t0);
            float y0 = lerp(startY, endY, t0);
            float x1 = lerp(startX, endX, Math.min(1.0F, t1));
            float y1 = lerp(startY, endY, Math.min(1.0F, t1));
            float width = 0.118F - i * 0.0053F;
            line(poseStack, consumer, overlay, x0, y0, x1, y1,
                    width, 39, 188, 211, 255, 0.001F);
            line(poseStack, consumer, overlay, x0, y0, x1, y1,
                    width * 0.39F, 177, 248, 255, 255, 0.006F);
        }
        line(poseStack, consumer, overlay, 0.34F, 0.35F, 0.43F, 0.44F,
                0.048F, 207, 255, 255, 255, 0.007F);
    }

    private static void renderEmerald(PoseStack poseStack, VertexConsumer consumer, int overlay) {
        line(poseStack, consumer, overlay, -0.39F, -0.34F, -0.18F, -0.13F,
                0.135F, 28, 54, 37, 255, 0.000F);
        line(poseStack, consumer, overlay, -0.34F, -0.29F, -0.20F, -0.15F,
                0.042F, 55, 184, 103, 255, 0.004F);
        float startX = -0.14F, startY = -0.09F, endX = 0.38F, endY = 0.39F;
        final int pieces = 9;
        for (int i = 0; i < pieces; ++i) {
            float t0 = i / (float) pieces;
            float t1 = (i + 0.72F) / pieces;
            float x0 = lerp(startX, endX, t0), y0 = lerp(startY, endY, t0);
            float x1 = lerp(startX, endX, Math.min(1.0F, t1));
            float y1 = lerp(startY, endY, Math.min(1.0F, t1));
            float width = 0.110F - i * 0.0048F;
            line(poseStack, consumer, overlay, x0, y0, x1, y1,
                    width, 39, 181, 92, 255, 0.001F);
            line(poseStack, consumer, overlay, x0, y0, x1, y1,
                    width * 0.39F, 180, 255, 205, 255, 0.006F);
        }
        line(poseStack, consumer, overlay, 0.34F, 0.35F, 0.43F, 0.44F,
                0.046F, 193, 255, 211, 255, 0.007F);
    }

    private static void renderAmethyst(PoseStack poseStack, VertexConsumer consumer, int overlay) {
        line(poseStack, consumer, overlay, -0.39F, -0.34F, -0.18F, -0.13F,
                0.135F, 54, 52, 66, 255, 0.000F);
        line(poseStack, consumer, overlay, -0.34F, -0.29F, -0.20F, -0.15F,
                0.042F, 137, 98, 216, 255, 0.004F);
        float startX = -0.14F, startY = -0.09F, endX = 0.38F, endY = 0.39F;
        final int pieces = 9;
        for (int i = 0; i < pieces; ++i) {
            float t0 = i / (float) pieces;
            float t1 = (i + 0.72F) / pieces;
            float x0 = lerp(startX, endX, t0), y0 = lerp(startY, endY, t0);
            float x1 = lerp(startX, endX, Math.min(1.0F, t1));
            float y1 = lerp(startY, endY, Math.min(1.0F, t1));
            float width = 0.110F - i * 0.0048F;
            line(poseStack, consumer, overlay, x0, y0, x1, y1,
                    width, 132, 92, 224, 255, 0.001F);
            line(poseStack, consumer, overlay, x0, y0, x1, y1,
                    width * 0.39F, 236, 220, 255, 255, 0.006F);
        }
        line(poseStack, consumer, overlay, 0.34F, 0.35F, 0.43F, 0.44F,
                0.046F, 226, 208, 255, 255, 0.007F);
    }

    private static void renderDestruction(PoseStack poseStack, VertexConsumer consumer, int overlay) {

        line(poseStack, consumer, overlay, -0.40F, -0.34F, -0.17F, -0.12F,
                0.145F, 31, 29, 34, 255, 0.000F);
        line(poseStack, consumer, overlay, -0.34F, -0.28F, -0.19F, -0.14F,
                0.043F, 180, 42, 35, 255, 0.005F);

        float startX = -0.14F;
        float startY = -0.08F;
        float endX = 0.39F;
        float endY = 0.39F;
        final int pieces = 10;
        for (int i = 0; i < pieces; ++i) {
            float t0 = i / (float) pieces;
            float t1 = (i + 0.73F) / pieces;
            float x0 = lerp(startX, endX, t0);
            float y0 = lerp(startY, endY, t0);
            float x1 = lerp(startX, endX, Math.min(1.0F, t1));
            float y1 = lerp(startY, endY, Math.min(1.0F, t1));
            float width = 0.124F - i * 0.0050F;
            line(poseStack, consumer, overlay, x0, y0, x1, y1,
                    width, 48, 43, 49, 255, 0.001F);
            line(poseStack, consumer, overlay, x0, y0, x1, y1,
                    width * 0.31F, 224, 55, 43, 255, 0.006F);
        }
        disc(poseStack, consumer, overlay, 0.39F, 0.39F, 0.043F,
                255, 95, 75, 255, 0.008F);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static void line(PoseStack stack, VertexConsumer consumer, int overlay,
                             float x0, float y0, float x1, float y1, float width,
                             int red, int green, int blue, int alpha, float z) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-5F) {
            return;
        }
        float px = -dy / length * width * 0.5F;
        float py = dx / length * width * 0.5F;
        quad(stack, consumer, overlay,
                x0 + px, y0 + py,
                x0 - px, y0 - py,
                x1 - px, y1 - py,
                x1 + px, y1 + py,
                red, green, blue, alpha, z);
    }

    private static void disc(PoseStack stack, VertexConsumer consumer, int overlay,
                             float cx, float cy, float radius,
                             int red, int green, int blue, int alpha, float z) {

        quad(stack, consumer, overlay,
                cx - radius, cy + radius,
                cx - radius, cy - radius,
                cx + radius, cy - radius,
                cx + radius, cy + radius,
                red, green, blue, alpha, z);
    }

    private static void quad(PoseStack stack, VertexConsumer consumer, int overlay,
                             float x0, float y0,
                             float x1, float y1,
                             float x2, float y2,
                             float x3, float y3,
                             int red, int green, int blue, int alpha, float z) {
        PoseStack.Pose pose = stack.last();
        vertex(consumer, pose, x0, y0, z, 0.0F, 0.0F, red, green, blue, alpha, overlay);
        vertex(consumer, pose, x1, y1, z, 0.0F, 1.0F, red, green, blue, alpha, overlay);
        vertex(consumer, pose, x2, y2, z, 1.0F, 1.0F, red, green, blue, alpha, overlay);
        vertex(consumer, pose, x3, y3, z, 1.0F, 0.0F, red, green, blue, alpha, overlay);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               int red, int green, int blue, int alpha, int overlay) {
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
