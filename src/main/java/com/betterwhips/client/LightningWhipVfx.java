package com.betterwhips.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;

public final class LightningWhipVfx extends RenderType {
    private static volatile ShaderInstance shader;
    public enum Style { LASH, LINK, WRAP }
    public static final RenderType RENDER_TYPE = create("better_whips_lightning_3d",
        DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 262144, false, false,
        CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader))
            .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
            .setOutputState(MAIN_TARGET).createCompositeState(false));

    private LightningWhipVfx(String name, VertexFormat format, VertexFormat.Mode mode, int size,
            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
    public static boolean ready() { return shader != null; }
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("better_whips", "lightning_whip"),
                DefaultVertexFormat.NEW_ENTITY), loaded -> shader = loaded);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load lightning whip shader", e);
        }
    }
    static void render(PoseStack stack, VertexConsumer out, Vec3[] points, int seed) {
        renderArc(stack, out, points, seed, 1f, 1f, Style.LASH);
    }
    static void renderFirstPerson(PoseStack stack, VertexConsumer out, Vec3[] points, int seed,
            float[] pointVisibility) {
        if (ready()) LightningArcMesh.emit(stack, out, points, seed, 1f, 1f, Style.LASH, pointVisibility);
    }
    static void renderArc(PoseStack stack, VertexConsumer out, Vec3[] points, int seed,
            float intensity, float thickness, Style style) {
        if (ready()) LightningArcMesh.emit(stack,out,points,seed,intensity,thickness,style);
    }
    static void renderArc(PoseStack stack, VertexConsumer out, Vec3[] points, int seed,
            float intensity, float thickness, Style style, float[] pointVisibility) {
        if (ready()) LightningArcMesh.emit(stack, out, points, seed, intensity, thickness, style, pointVisibility);
    }
}
