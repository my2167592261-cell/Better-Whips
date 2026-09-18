package com.betterwhips.item;

import com.betterwhips.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class WindwhispererWhipDrops {
    private static final float DROP_CHANCE = 0.001F;
    private WindwhispererWhipDrops() {}

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getState().is(BlockTags.LEAVES)) return;
        if (level.random.nextFloat() < DROP_CHANCE) {
            Block.popResource(level, event.getPos(), new ItemStack(ModItems.WINDWHISPERER_WHIP.get()));
        }
    }
}
