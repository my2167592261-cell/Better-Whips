package com.betterwhips.client;

import com.betterwhips.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;

public final class WhipFirstPersonDepth {
    private WhipFirstPersonDepth() {
    }

    public static boolean shouldPreserveWorldDepth() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !minecraft.options.getCameraType().isFirstPerson()) {
            return false;
        }
        return player.getMainHandItem().is(ModItems.LIGHTNING_WHIP.get()) || player.getOffhandItem().is(ModItems.LIGHTNING_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.ROYAL_SLIME_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.ROYAL_SLIME_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.LEATHER_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.LEATHER_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.TRAINER_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.TRAINER_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.UNTAMED_SEA_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.UNTAMED_SEA_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.CHAIN_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.CHAIN_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.GOLDEN_HEAVY_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.GOLDEN_HEAVY_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.DIAMOND_BLADE_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.DIAMOND_BLADE_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.AMETHYST_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.AMETHYST_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.EMERALD_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.EMERALD_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.DESTRUCTION_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.DESTRUCTION_WHIP.get()) || player.getMainHandItem().is((Item)ModItems.WINDWHISPERER_WHIP.get()) || player.getOffhandItem().is((Item)ModItems.WINDWHISPERER_WHIP.get());
    }
}
