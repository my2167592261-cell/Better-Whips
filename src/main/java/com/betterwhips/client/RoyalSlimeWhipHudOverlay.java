package com.betterwhips.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.betterwhips.BetterWhipsMod;
import com.betterwhips.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;

import java.io.IOException;

public final class RoyalSlimeWhipHudOverlay {
    private static final int MAX_STACKS = 5;
    private static final int DIAMOND_RADIUS = 2;
    private static final float MARKER_SPACING = 8.0F;
    private static final float HUD_Y_OFFSET = 10.0F;
    private static final long REARRANGE_NANOS = 135_000_000L;

    private static final ResourceLocation GLOW_SHADER_ID = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID, "royal_whip_hud_glow");

    private static volatile ShaderInstance glowShader;
    private static volatile int currentStacks;
    private static volatile int animationFromStacks;
    private static volatile long animationStartNanos;

    private RoyalSlimeWhipHudOverlay() {}

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            ShaderInstance instance = new ShaderInstance(
                    event.getResourceProvider(), GLOW_SHADER_ID, DefaultVertexFormat.POSITION_TEX_COLOR);
            event.registerShader(instance, loaded -> glowShader = loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load Royal Slime Whip HUD glow shader", exception);
        }
    }

    public static void setStacks(int stacks) {
        int clamped = Mth.clamp(stacks, 0, MAX_STACKS);
        int old = currentStacks;
        if (clamped == old) {
            return;
        }
        currentStacks = clamped;
        if (clamped > old) {
            animationFromStacks = old;
            animationStartNanos = System.nanoTime();
        } else {

            animationFromStacks = clamped;
            animationStartNanos = 0L;
        }
    }

    public static int getStacks() {
        return currentStacks;
    }

    public static boolean hasFullStacks() {
        return currentStacks >= MAX_STACKS;
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        currentStacks = 0;
        animationFromStacks = 0;
        animationStartNanos = 0L;
    }

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (minecraft.options.hideGui || player == null || minecraft.level == null
                || currentStacks <= 0
                || !player.getMainHandItem().is(ModItems.ROYAL_SLIME_WHIP.get())) {
            return;
        }

        GuiGraphics gui = event.getGuiGraphics();
        float centerX = minecraft.getWindow().getGuiScaledWidth() * 0.5F;
        float centerY = minecraft.getWindow().getGuiScaledHeight() * 0.5F + HUD_Y_OFFSET;
        boolean full = hasFullStacks();

        float transition = transitionProgress();
        float eased = easeOutCubic(transition);
        float breath = full
                ? 0.5F + 0.5F * Mth.sin((float) (System.nanoTime() * 1.0E-9D * 2.15D))
                : 0.0F;

        gui.flush();
        drawShaderGlows(gui, centerX, centerY, transition, eased, full, breath);

        for (int i = 0; i < currentStacks; ++i) {
            MarkerPose pose = markerPose(i, centerX, eased);
            float appear = markerAppear(i, transition);
            if (appear <= 0.01F) {
                continue;
            }
            drawDiamondCore(gui, Math.round(pose.x), Math.round(centerY), full, breath, appear);
        }
    }

    private static void drawShaderGlows(GuiGraphics gui, float centerX, float centerY,
                                        float transition, float eased, boolean full, float breath) {
        ShaderInstance shader = glowShader;
        if (shader == null) {
            return;
        }

        Matrix4f matrix = gui.pose().last().pose();
        RenderSystem.enableBlend();

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        BufferBuilder buffer = null;
        for (int i = 0; i < currentStacks; ++i) {
            MarkerPose pose = markerPose(i, centerX, eased);
            float appear = markerAppear(i, transition);
            if (appear <= 0.01F) {
                continue;
            }

            if (buffer == null) {
                buffer = Tesselator.getInstance().begin(
                        VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            }

            float glowRadius = full
                    ? Mth.lerp(breath, 6.9F, 8.7F)
                    : 5.7F;
            float alpha = (full
                    ? Mth.lerp(breath, 0.62F, 0.88F)
                    : 0.30F) * appear;
            int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
            int r = full ? 126 : 80;
            int g = 255;
            int b = full ? 126 : 72;
            addGlowQuad(buffer, matrix, pose.x, centerY, glowRadius, r, g, b, a);
        }
        if (buffer != null) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void addGlowQuad(BufferBuilder buffer, Matrix4f matrix,
                                    float cx, float cy, float radius,
                                    int r, int g, int b, int a) {
        float left = cx - radius;
        float right = cx + radius;
        float top = cy - radius;
        float bottom = cy + radius;
        float z = 0.0F;
        buffer.addVertex(matrix, left, bottom, z).setUv(0.0F, 1.0F).setColor(r, g, b, a);
        buffer.addVertex(matrix, right, bottom, z).setUv(1.0F, 1.0F).setColor(r, g, b, a);
        buffer.addVertex(matrix, right, top, z).setUv(1.0F, 0.0F).setColor(r, g, b, a);
        buffer.addVertex(matrix, left, top, z).setUv(0.0F, 0.0F).setColor(r, g, b, a);
    }

    private static MarkerPose markerPose(int index, float centerX, float eased) {
        int fromCount = animationFromStacks;
        int toCount = currentStacks;
        float targetX = centredMarkerX(index, toCount, centerX);
        if (animationStartNanos == 0L || fromCount >= toCount) {
            return new MarkerPose(targetX);
        }

        if (index < fromCount) {

            float oldX = centredMarkerX(index, fromCount, centerX);
            return new MarkerPose(Mth.lerp(eased, oldX, targetX));
        }
        return new MarkerPose(targetX);
    }

    private static float markerAppear(int index, float transition) {
        if (animationStartNanos == 0L || animationFromStacks >= currentStacks
                || index < animationFromStacks) {
            return 1.0F;
        }

        float local = Mth.clamp((transition - 0.12F) / 0.88F, 0.0F, 1.0F);
        return local * local * (3.0F - 2.0F * local);
    }

    private static float centredMarkerX(int index, int count, float centerX) {
        if (count <= 0) {
            return centerX;
        }
        return centerX + (index - (count - 1) * 0.5F) * MARKER_SPACING;
    }

    private static float transitionProgress() {
        long start = animationStartNanos;
        if (start == 0L) {
            return 1.0F;
        }
        float progress = Mth.clamp((System.nanoTime() - start) / (float) REARRANGE_NANOS, 0.0F, 1.0F);
        if (progress >= 1.0F) {
            animationFromStacks = currentStacks;
            animationStartNanos = 0L;
        }
        return progress;
    }

    private static float easeOutCubic(float t) {
        float inv = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv;
    }

    private static void drawDiamondCore(GuiGraphics gui, int centerX, int centerY,
                                        boolean full, float breath, float appear) {
        int outer = full
                ? lerpColor(0xFF5CDE10, 0xFFA8FF70, breath)
                : 0xFF54C414;
        int inner = full
                ? lerpColor(0xFFC8FF72, 0xFFF1FFD1, breath)
                : 0xFFB4EF4A;
        int core = full
                ? lerpColor(0xFFF1FFD6, 0xFFFFFFFF, breath)
                : 0xFFD8FF94;
        outer = multiplyAlpha(outer, appear);
        inner = multiplyAlpha(inner, appear);
        core = multiplyAlpha(core, appear);

        for (int dy = -DIAMOND_RADIUS; dy <= DIAMOND_RADIUS; ++dy) {
            int row = DIAMOND_RADIUS - Math.abs(dy);
            int left = centerX - row;
            int right = centerX + row + 1;
            gui.fill(left, centerY + dy, right, centerY + dy + 1, outer);
            if (row > 0) {
                gui.fill(left + 1, centerY + dy, right - 1, centerY + dy + 1, inner);
            }
            if (row > 1) {
                gui.fill(left + 2, centerY + dy, right - 2, centerY + dy + 1, core);
            }
        }
    }

    private static int multiplyAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        alpha = Mth.clamp(Math.round(alpha * Mth.clamp(factor, 0.0F, 1.0F)), 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int lerpColor(int from, int to, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int a = lerp8(from >>> 24, to >>> 24, t);
        int r = lerp8((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = lerp8((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = lerp8(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp8(int a, int b, float t) {
        return Mth.clamp(Math.round(a + (b - a) * t), 0, 255);
    }

    private record MarkerPose(float x) {}
}
