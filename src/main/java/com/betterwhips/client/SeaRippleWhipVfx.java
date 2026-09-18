package com.betterwhips.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;

public final class SeaRippleWhipVfx extends RenderType {
    private static ShaderInstance water,glow;
    public static final RenderType WATER=create("better_whips_sea_water",DefaultVertexFormat.POSITION_TEX_COLOR,
        VertexFormat.Mode.QUADS,262144,false,true,CompositeState.builder()
            .setShaderState(new ShaderStateShard(()->water)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
            .setOutputState(MAIN_TARGET).createCompositeState(false));
    public static final RenderType GLOW=create("better_whips_sea_foam",DefaultVertexFormat.POSITION_TEX_COLOR,
        VertexFormat.Mode.QUADS,262144,false,false,CompositeState.builder()
            .setShaderState(new ShaderStateShard(()->glow)).setTransparencyState(LIGHTNING_TRANSPARENCY)
            .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
            .setOutputState(MAIN_TARGET).createCompositeState(false));
    private SeaRippleWhipVfx(String name,VertexFormat format,VertexFormat.Mode mode,int size,boolean crumbling,
            boolean sort,Runnable setup,Runnable clear) { super(name,format,mode,size,crumbling,sort,setup,clear); }
    public static boolean ready() { return water!=null && glow!=null; }
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("better_whips","sea_water"),DefaultVertexFormat.POSITION_TEX_COLOR),s->water=s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("better_whips","sea_foam"),DefaultVertexFormat.POSITION_TEX_COLOR),s->glow=s);
        } catch(IOException e) { throw new IllegalStateException("Could not load Untamed Sea water shaders",e); }
    }
}
