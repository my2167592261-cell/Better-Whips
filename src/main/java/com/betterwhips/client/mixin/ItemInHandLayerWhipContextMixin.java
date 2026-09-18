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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ItemInHandLayer.class})
public abstract class ItemInHandLayerWhipContextMixin {
    @Inject(method={"renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at={@At(value="HEAD")})
    private void betterWhips$pushWhipRenderContext(LivingEntity holder, ItemStack stack, ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
        RoyalSlimeWhipPhysics.pushRenderContext(holder, arm, false);
        LeatherWhipPhysics.pushRenderContext(holder, arm, false);
        LightningWhipPhysics.pushRenderContext(holder, arm, false);
        TrainerWhipPhysics.pushRenderContext(holder, arm, false);
        SeaRippleWhipPhysics.pushRenderContext(holder, arm, false);
        GoldenHeavyWhipPhysics.pushRenderContext(holder, arm, false);
        ChainWhipPhysics.pushRenderContext(holder, arm, false);
        DiamondBladeWhipPhysics.pushRenderContext(holder, arm, false);
        EmeraldWhipPhysics.pushRenderContext(holder, arm, false);
        AmethystWhipPhysics.pushRenderContext(holder, arm, false);
        DestructionWhipPhysics.pushRenderContext(holder, arm, false);
        WindwhispererWhipPhysics.pushRenderContext(holder, arm, false);
    }

    @Inject(method={"renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"}, at={@At(value="RETURN")})
    private void betterWhips$popWhipRenderContext(LivingEntity holder, ItemStack stack, ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo callbackInfo) {
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
