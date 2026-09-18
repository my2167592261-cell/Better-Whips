package com.betterwhips.registry;

import com.betterwhips.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "better_whips");
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_TABS.register("main", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.better_whips.main")).icon(() -> new ItemStack((ItemLike)ModItems.ROYAL_SLIME_WHIP.get())).displayItems((p, o) -> {
        o.accept((ItemLike)ModItems.ROYAL_SLIME_WHIP.get());
        o.accept((ItemLike)ModItems.LEATHER_WHIP.get());
        o.accept((ItemLike)ModItems.LIGHTNING_WHIP.get());
        o.accept((ItemLike)ModItems.CHAIN_WHIP.get());
        o.accept((ItemLike)ModItems.GOLDEN_HEAVY_WHIP.get());
        o.accept((ItemLike)ModItems.DIAMOND_BLADE_WHIP.get());
        o.accept((ItemLike)ModItems.EMERALD_WHIP.get());
        o.accept((ItemLike)ModItems.AMETHYST_WHIP.get());
        o.accept((ItemLike)ModItems.TRAINER_WHIP.get());
        o.accept((ItemLike)ModItems.UNTAMED_SEA_WHIP.get());
        o.accept((ItemLike)ModItems.DESTRUCTION_WHIP.get());
        o.accept((ItemLike)ModItems.WINDWHISPERER_WHIP.get());
    }).build());

    private ModCreativeTabs() {
    }
}
