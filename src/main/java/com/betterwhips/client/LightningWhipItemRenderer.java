package com.betterwhips.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

public final class LightningWhipItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final RenderType HILT = RenderType.entityTranslucent(ResourceLocation.fromNamespaceAndPath("better_whips", "textures/item/lightning_whip.png"));
    public LightningWhipItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) { super(dispatcher, models); }

    @Override
    public void renderByItem(ItemStack item, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        boolean held = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        pose.pushPose();
        try {
            pose.translate(.5,.5,.5);
            if (context.firstPerson()) pose.translate(0,-.18,0);
            else if (held) pose.translate(0,-.17,0);
            else if (context == ItemDisplayContext.GUI) {
                pose.translate(-.10,-.13,0);
                pose.mulPose(Axis.YP.rotationDegrees(-20));
                pose.mulPose(Axis.ZP.rotationDegrees(-38));
                pose.scale(.68f,.68f,.68f);
                light = LightTexture.FULL_BRIGHT;
            } else if (context == ItemDisplayContext.GROUND) {
                pose.translate(0,-.27,0);
                pose.mulPose(Axis.ZP.rotationDegrees(-75));
                pose.scale(.55f,.55f,.55f);
            } else {
                pose.mulPose(Axis.ZP.rotationDegrees(-30));
                pose.scale(.6f,.6f,.6f);
            }
            LightningWhipPhysics.RenderContext rc = LightningWhipPhysics.currentRenderContext();
            if (held && rc != null) LightningWhipPhysics.captureRenderedSocket(pose, rc);
            LightningWhipGeometry.INSTANCE.renderHandle(pose, ItemRenderer.getFoilBufferDirect(buffers, HILT, true, item.hasFoil()), light, overlay);
            if (context.firstPerson() && rc != null && LightningWhipVfx.ready()) {
                LightningWhipPhysics.renderFirstPersonHandLash(pose, rc, buffers.getBuffer(LightningWhipVfx.RENDER_TYPE), overlay);
            } else if (!held && LightningWhipVfx.ready() && context != ItemDisplayContext.GROUND) {

                Vec3[] arc = new Vec3[17];
                for (int i=0;i<arc.length;i++) {
                    double t=i/16.0;
                    arc[i]=new Vec3(.18*Math.sin(t*4.8), LightningWhipGeometry.CHAIN_PIVOT_Y+.30*t, -.02);
                }
                LightningWhipVfx.renderArc(pose,buffers.getBuffer(LightningWhipVfx.RENDER_TYPE),arc,7,1f,.45f,LightningWhipVfx.Style.LINK);
            }
        } finally { pose.popPose(); }
    }
}
