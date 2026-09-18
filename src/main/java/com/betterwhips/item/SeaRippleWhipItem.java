package com.betterwhips.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.betterwhips.client.SeaRippleWhipItemRenderer;
import com.betterwhips.client.SeaRippleWhipPhysics;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import com.betterwhips.network.SeaRippleWhipNetwork;
import net.minecraft.client.Minecraft;
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

import java.util.List;
import java.util.function.Consumer;

public final class SeaRippleWhipItem extends Item {

    public static final int RIGHT_CHARGE_TICKS = 20 * 3;
    private static final double ATTACK_SPEED_MODIFIER = -2.3D;
    private static final int ENCHANTMENT_VALUE = 12;

    public SeaRippleWhipItem(Properties properties) { super(properties); }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 4.0D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED_MODIFIER,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public static int attackPeriodTicks(Player player) {
        return Math.max(1, (int)Math.ceil(player.getCurrentItemAttackStrengthDelay()));
    }

    public static float damageForSpeed(double speedBlocksPerSecond) {
        return (float)(Math.floor(Math.max(0.0D, speedBlocksPerSecond) / 10.0D) * 5.0D);
    }

    @Override public int getEnchantmentValue(ItemStack stack) { return ENCHANTMENT_VALUE; }
    @Override public boolean isEnchantable(ItemStack stack) { return stack.getCount() == 1; }
    @Override public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) { return false; }
    @Override public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return ability == ItemAbilities.SWORD_SWEEP || super.canPerformAction(stack, ability);
    }
    @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) { return true; }
    @Override public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {}

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.better_whips.untamed_sea_whip.attack").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.better_whips.untamed_sea_whip.blade").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.better_whips.untamed_sea_whip.rain_speed").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.better_whips.untamed_sea_whip.protection").withStyle(ChatFormatting.WHITE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(player.getItemInHand(hand));
        if (player instanceof ServerPlayer serverPlayer) SeaRippleWhipCombat.request(serverPlayer, SeaRippleWhipNetwork.BLADE);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockEntityWithoutLevelRenderer renderer = new SeaRippleWhipItemRenderer(
                minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        consumer.accept(new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack,
                                                   net.minecraft.client.player.LocalPlayer player,
                                                   HumanoidArm arm, ItemStack itemInHand,
                                                   float partialTick, float equipProcess,
                                                   float swingProcess) {
                SeaRippleWhipPhysics.applyFirstPersonItemTransform(
                        poseStack, player, arm, partialTick, equipProcess);
                return true;
            }
        });
    }
}
