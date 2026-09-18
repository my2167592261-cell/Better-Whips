package com.betterwhips.client;

import com.betterwhips.BetterWhipsMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

public final class RoyalSlimeWhipHitRenderTypes extends RenderType {
    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterWhipsMod.MOD_ID,
            "textures/misc/vfx_white.png"
    );

    private static final ShaderStateShard VANILLA_POSITION_TEX_COLOR_SHADER =
            new ShaderStateShard(GameRenderer::getPositionTexColorShader);

    private static final TextureStateShard WHITE_TEXTURE_STATE =
            new TextureStateShard(WHITE_TEXTURE, false, false);

    public static final RenderType TRANSLUCENT = create(
            BetterWhipsMod.MOD_ID + "_combat_vfx_translucent_compat",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            16384,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(VANILLA_POSITION_TEX_COLOR_SHADER)
                    .setTextureState(WHITE_TEXTURE_STATE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)
    );

    public static final RenderType GLOW = create(
            BetterWhipsMod.MOD_ID + "_combat_vfx_glow_compat",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            16384,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(VANILLA_POSITION_TEX_COLOR_SHADER)
                    .setTextureState(WHITE_TEXTURE_STATE)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)
    );

    public static final RenderType TRANSLUCENT_THROUGH_WALLS = create(
            BetterWhipsMod.MOD_ID + "_combat_vfx_translucent_through_walls_compat",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            16384,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(VANILLA_POSITION_TEX_COLOR_SHADER)
                    .setTextureState(WHITE_TEXTURE_STATE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)
    );

    public static final RenderType GLOW_THROUGH_WALLS = create(
            BetterWhipsMod.MOD_ID + "_combat_vfx_glow_through_walls_compat",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            16384,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(VANILLA_POSITION_TEX_COLOR_SHADER)
                    .setTextureState(WHITE_TEXTURE_STATE)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)
    );

    private RoyalSlimeWhipHitRenderTypes(
            String name,
            VertexFormat format,
            VertexFormat.Mode mode,
            int bufferSize,
            boolean affectsCrumbling,
            boolean sortOnUpload,
            Runnable setupState,
            Runnable clearState
    ) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        throw new UnsupportedOperationException("Utility holder only");
    }

    public static void registerShaders(RegisterShadersEvent event) throws IOException {

    }

    public static boolean ready() {

        return true;
    }
}
