package com.betterwhips.client.mixin;

import com.betterwhips.client.AmethystWhipPhysics;
import com.betterwhips.client.ChainWhipPhysics;
import com.betterwhips.client.DestructionWhipPhysics;
import com.betterwhips.client.DiamondBladeWhipPhysics;
import com.betterwhips.client.EmeraldWhipPhysics;
import com.betterwhips.client.GoldenHeavyWhipPhysics;
import com.betterwhips.client.LeatherWhipPhysics;
import com.betterwhips.client.LightningWhipPhysics;
import com.betterwhips.client.RoyalSlimeWhipPhysics;
import com.betterwhips.client.SeaRippleWhipPhysics;
import com.betterwhips.client.TrainerWhipPhysics;
import com.betterwhips.client.WindwhispererWhipPhysics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ItemInHandRenderer.class})
public abstract class ItemInHandRendererWhipContextMixin {
    @Inject(method={"renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at={@At(value="HEAD")})
    private void betterWhips$pushFirstPersonWhipContext(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        RoyalSlimeWhipPhysics.pushRenderContext(player, arm, true);
        LeatherWhipPhysics.pushRenderContext(player, arm, true);
        LightningWhipPhysics.pushRenderContext(player, arm, true);
        TrainerWhipPhysics.pushRenderContext(player, arm, true);
        SeaRippleWhipPhysics.pushRenderContext(player, arm, true);
        GoldenHeavyWhipPhysics.pushRenderContext(player, arm, true);
        ChainWhipPhysics.pushRenderContext(player, arm, true);
        DiamondBladeWhipPhysics.pushRenderContext(player, arm, true);
        EmeraldWhipPhysics.pushRenderContext(player, arm, true);
        AmethystWhipPhysics.pushRenderContext(player, arm, true);
        DestructionWhipPhysics.pushRenderContext(player, arm, true);
        WindwhispererWhipPhysics.pushRenderContext(player, arm, true);
    }

    @Inject(method={"renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at={@At(value="RETURN")})
    private void betterWhips$popFirstPersonWhipContext(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
        WindwhispererWhipPhysics.popRenderContext();
        DestructionWhipPhysics.popRenderContext();
        AmethystWhipPhysics.popRenderContext();
        EmeraldWhipPhysics.popRenderContext();
        DiamondBladeWhipPhysics.popRenderContext();
        ChainWhipPhysics.popRenderContext();
        GoldenHeavyWhipPhysics.popRenderContext();
        SeaRippleWhipPhysics.popRenderContext();
        TrainerWhipPhysics.popRenderContext();
        LeatherWhipPhysics.popRenderContext();
        LightningWhipPhysics.popRenderContext();
        RoyalSlimeWhipPhysics.popRenderContext();
    }
}
