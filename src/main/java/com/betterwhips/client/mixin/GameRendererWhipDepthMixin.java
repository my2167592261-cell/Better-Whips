package com.betterwhips.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.betterwhips.client.WhipFirstPersonDepth;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class GameRendererWhipDepthMixin {
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"
            ),
            require = 0
    )
    private void betterWhips$preserveWorldDepthForWhipHand(int mask, boolean getError) {
        if (WhipFirstPersonDepth.shouldPreserveWorldDepth() && (mask & 256) != 0) {
            int nonDepthMask = mask & ~256;
            if (nonDepthMask != 0) {
                RenderSystem.clear(nonDepthMask, getError);
            }
            return;
        }
        RenderSystem.clear(mask, getError);
    }
}
