package com.betterwhips.client.mixin;

import com.betterwhips.client.AmethystWhipPhysics;
import com.betterwhips.client.ChainWhipPhysics;
import com.betterwhips.client.DiamondBladeWhipPhysics;
import com.betterwhips.client.EmeraldWhipPhysics;
import com.betterwhips.client.GoldenHeavyWhipPhysics;
import com.betterwhips.client.LeatherWhipPhysics;
import com.betterwhips.client.LightningWhipPhysics;
import com.betterwhips.client.RoyalSlimeWhipPhysics;
import com.betterwhips.client.SeaRippleWhipPhysics;
import com.betterwhips.client.TrainerWhipPhysics;
import com.betterwhips.client.WindwhispererWhipPhysics;
import com.betterwhips.registry.ModItems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PlayerModel.class})
public abstract class PlayerModelWhipMixin {
    @Inject(method={"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"}, at={@At(value="TAIL")})
    private void betterWhips$applyWhipArmPhysics(LivingEntity living, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callbackInfo) {
        if (!(living instanceof Player)) {
            return;
        }
        Player player = (Player)living;
        PlayerModel model = (PlayerModel)((Object)this);
        float partialTick = Mth.clamp(ageInTicks - (float)living.tickCount, 0.0f, 1.0f);
        PlayerModelWhipMixin.applyArm(player, HumanoidArm.RIGHT, model.rightArm, model.rightSleeve, partialTick);
        PlayerModelWhipMixin.applyArm(player, HumanoidArm.LEFT, model.leftArm, model.leftSleeve, partialTick);
    }

    private static void applyArm(Player player, HumanoidArm arm, ModelPart armPart, ModelPart sleevePart, float partialTick) {
        Record pose;
        InteractionHand hand;
        InteractionHand interactionHand = hand = player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (player.getItemInHand(hand).is(ModItems.LIGHTNING_WHIP.get())) {
            LightningWhipPhysics.ArmPose electricPose = LightningWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = electricPose.pitch();
            armPart.yRot = electricPose.yaw();
            armPart.zRot = electricPose.roll();
            sleevePart.copyFrom(armPart);
            return;
        }
        if (player.getItemInHand(hand).is((Item)ModItems.ROYAL_SLIME_WHIP.get())) {
            RoyalSlimeWhipPhysics.ArmPose pose2 = RoyalSlimeWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = pose2.pitch();
            armPart.yRot = pose2.yaw();
            armPart.zRot = pose2.roll();
            sleevePart.copyFrom(armPart);
            return;
        }
        if (player.getItemInHand(hand).is((Item)ModItems.LEATHER_WHIP.get())) {
            pose = LeatherWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = ((LeatherWhipPhysics.ArmPose)pose).pitch();
            armPart.yRot = ((LeatherWhipPhysics.ArmPose)pose).yaw();
            armPart.zRot = ((LeatherWhipPhysics.ArmPose)pose).roll();
            sleevePart.copyFrom(armPart);
        }
        if (player.getItemInHand(hand).is((Item)ModItems.TRAINER_WHIP.get())) {
            pose = TrainerWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = ((TrainerWhipPhysics.ArmPose)pose).pitch();
            armPart.yRot = ((TrainerWhipPhysics.ArmPose)pose).yaw();
            armPart.zRot = ((TrainerWhipPhysics.ArmPose)pose).roll();
            sleevePart.copyFrom(armPart);
            return;
        }
        if (player.getItemInHand(hand).is((Item)ModItems.UNTAMED_SEA_WHIP.get())) {
            pose = SeaRippleWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = ((SeaRippleWhipPhysics.ArmPose)pose).pitch();
            armPart.yRot = ((SeaRippleWhipPhysics.ArmPose)pose).yaw();
            armPart.zRot = ((SeaRippleWhipPhysics.ArmPose)pose).roll();
            sleevePart.copyFrom(armPart);
            return;
        }
        if (player.getItemInHand(hand).is((Item)ModItems.CHAIN_WHIP.get())) {
            pose = ChainWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = ((ChainWhipPhysics.ArmPose)pose).pitch();
            armPart.yRot = ((ChainWhipPhysics.ArmPose)pose).yaw();
            armPart.zRot = ((ChainWhipPhysics.ArmPose)pose).roll();
            sleevePart.copyFrom(armPart);
            return;
        }
        if (player.getItemInHand(hand).is((Item)ModItems.GOLDEN_HEAVY_WHIP.get())) {
            pose = GoldenHeavyWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = ((GoldenHeavyWhipPhysics.ArmPose)pose).pitch();
            armPart.yRot = ((GoldenHeavyWhipPhysics.ArmPose)pose).yaw();
            armPart.zRot = ((GoldenHeavyWhipPhysics.ArmPose)pose).roll();
            sleevePart.copyFrom(armPart);
            return;
        }
        if (player.getItemInHand(hand).is((Item)ModItems.DIAMOND_BLADE_WHIP.get())) {
            pose = DiamondBladeWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = ((DiamondBladeWhipPhysics.ArmPose)pose).pitch();
            armPart.yRot = ((DiamondBladeWhipPhysics.ArmPose)pose).yaw();
            armPart.zRot = ((DiamondBladeWhipPhysics.ArmPose)pose).roll();
            sleevePart.copyFrom(armPart);
            return;
        }
        if (player.getItemInHand(hand).is((Item)ModItems.EMERALD_WHIP.get())) {
            pose = EmeraldWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = ((EmeraldWhipPhysics.ArmPose)pose).pitch();
            armPart.yRot = ((EmeraldWhipPhysics.ArmPose)pose).yaw();
            armPart.zRot = ((EmeraldWhipPhysics.ArmPose)pose).roll();
            sleevePart.copyFrom(armPart);
            return;
        }
        if (player.getItemInHand(hand).is((Item)ModItems.AMETHYST_WHIP.get())) {
            pose = AmethystWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = ((AmethystWhipPhysics.ArmPose)pose).pitch();
            armPart.yRot = ((AmethystWhipPhysics.ArmPose)pose).yaw();
            armPart.zRot = ((AmethystWhipPhysics.ArmPose)pose).roll();
            sleevePart.copyFrom(armPart);
            return;
        }
        if (player.getItemInHand(hand).is((Item)ModItems.WINDWHISPERER_WHIP.get())) {
            pose = WindwhispererWhipPhysics.getThirdPersonArmPose(player, arm, partialTick);
            armPart.xRot = ((WindwhispererWhipPhysics.ArmPose)pose).pitch();
            armPart.yRot = ((WindwhispererWhipPhysics.ArmPose)pose).yaw();
            armPart.zRot = ((WindwhispererWhipPhysics.ArmPose)pose).roll();
            sleevePart.copyFrom(armPart);
            return;
        }
    }
}
