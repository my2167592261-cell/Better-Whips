package com.betterwhips.item;

import com.betterwhips.client.LightningWhipItemRenderer;
import com.betterwhips.client.LightningWhipPhysics;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

public final class LightningWhipItem
extends Item {
    public static final int RIGHT_CHARGE_TICKS = 60;

    public static final int ATTACK_PERIOD_TICKS = 5;
    private static final double ATTACK_SPEED_MODIFIER = 0.0;
    private static final int ENCHANTMENT_VALUE = 12;

    public LightningWhipItem(Item.Properties properties) {
        super(properties);
    }

    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, -1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED_MODIFIER, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
    }

    public static int attackPeriodTicks(Player player) {

        return ATTACK_PERIOD_TICKS;
    }

    public static float damageForSpeed(double speedBlocksPerSecond) {
        double speed = Math.max(0.0, speedBlocksPerSecond);
        return (float)(Math.floor(speed / 10.0) * 0.5);
    }

    public int getEnchantmentValue(ItemStack stack) {
        return 12;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return false;
    }

    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return itemAbility == ItemAbilities.SWORD_SWEEP || super.canPerformAction(stack, itemAbility);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.better_whips.lightning_whip.left").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("tooltip.better_whips.lightning_whip.damage").withStyle(ChatFormatting.WHITE));
        tooltipComponents.add(Component.translatable("tooltip.better_whips.lightning_whip.passive_streamflow").withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.translatable("tooltip.better_whips.lightning_whip.obtain").withStyle(ChatFormatting.DARK_AQUA));
    }

    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        Minecraft minecraft = Minecraft.getInstance();
        final LightningWhipItemRenderer renderer = new LightningWhipItemRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        consumer.accept(new IClientItemExtensions(){

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand, float partialTick, float equipProcess, float swingProcess) {
                LightningWhipPhysics.applyFirstPersonItemTransform(poseStack, player, arm, partialTick, equipProcess);
                return true;
            }
        });
    }
}
