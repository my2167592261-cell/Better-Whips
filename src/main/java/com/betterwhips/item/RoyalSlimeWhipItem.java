package com.betterwhips.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.betterwhips.client.RoyalSlimeWhipItemRenderer;
import com.betterwhips.client.RoyalSlimeWhipPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;
import java.util.function.Consumer;

public final class RoyalSlimeWhipItem extends Item {
    public static final float BASE_DAMAGE = 1.0F;
    public static final int RIGHT_CHARGE_TICKS = 20 * 3;

    private static final int RIGHT_MAX_USE_TICKS = 72_000;

    private static final double ATTACK_SPEED_MODIFIER = -3.0D;

    private static final double ATTACK_DAMAGE_MODIFIER = 0.0D;

    private static final int ENCHANTMENT_VALUE = 15;
    private static final int MIN_ATTACK_PERIOD_TICKS = 1;

    public RoyalSlimeWhipItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                                ATTACK_DAMAGE_MODIFIER,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID,
                                ATTACK_SPEED_MODIFIER,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public static int attackPeriodTicks(Player player) {
        float vanillaDelay = player.getCurrentItemAttackStrengthDelay();
        return Math.max(MIN_ATTACK_PERIOD_TICKS, (int)Math.ceil(vanillaDelay));
    }

    public static float damageForSpeed(double speedBlocksPerSecond) {
        double speed = Math.max(0.0D, speedBlocksPerSecond);
        return BASE_DAMAGE + (float)(Math.floor(speed / 10.0D) * 1.5D);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return ENCHANTMENT_VALUE;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return false;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return itemAbility == ItemAbilities.SWORD_SWEEP
                || super.canPerformAction(stack, itemAbility);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "tooltip.better_whips.royal_slime_whip.left").withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(Component.translatable(
                "tooltip.better_whips.royal_slime_whip.right").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable(
                "tooltip.better_whips.royal_slime_whip.items").withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.translatable(
                "tooltip.better_whips.whip.multi_hit").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        if (level.isClientSide) {
            RoyalSlimeWhipPhysics.requestChargedSlam(player, hand);
        } else if (level instanceof ServerLevel serverLevel) {
            RoyalSlimeWhipCombat.beginChargedSlam(serverLevel, player, hand);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return RIGHT_MAX_USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {

        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity,
                             int timeCharged) {

        if (livingEntity instanceof Player player) {
            int usedTicks = getUseDuration(stack, livingEntity) - timeCharged;
            if (usedTicks < RIGHT_CHARGE_TICKS) {
                if (level.isClientSide) {
                    RoyalSlimeWhipPhysics.cancelChargedSlam(player);
                } else {
                    RoyalSlimeWhipCombat.cancelChargedSlam(player);
                }
            }

        }
    }

    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockEntityWithoutLevelRenderer renderer = new RoyalSlimeWhipItemRenderer(
                minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack,
                                                   net.minecraft.client.player.LocalPlayer player,
                                                   HumanoidArm arm,
                                                   ItemStack itemInHand,
                                                   float partialTick,
                                                   float equipProcess,
                                                   float swingProcess) {
                RoyalSlimeWhipPhysics.applyFirstPersonItemTransform(
                        poseStack, player, arm, partialTick, equipProcess);
                return true;
            }
        });
    }
}
