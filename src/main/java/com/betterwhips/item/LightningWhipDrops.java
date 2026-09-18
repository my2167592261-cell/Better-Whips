package com.betterwhips.item;

import com.betterwhips.registry.ModItems;
import com.betterwhips.client.mixin.LightningBoltAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class LightningWhipDrops {
    private static final float NATURAL_LIGHTNING_DROP_CHANCE = 0.005F;
    private static final ArrayDeque<PendingDrop> PENDING = new ArrayDeque<>();

    private LightningWhipDrops() {}

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if(event.loadedFromDisk() || event.getLevel().isClientSide()
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof LightningBolt bolt))return;

        if(bolt.getCause()!=null || ((LightningBoltAccessor)(Object)bolt).betterwhips$isVisualOnly() || !level.isThundering())return;
        if(level.getRandom().nextFloat()>=NATURAL_LIGHTNING_DROP_CHANCE)return;

        PENDING.addLast(new PendingDrop(level,bolt.position()));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if(PENDING.isEmpty())return;
        int count=PENDING.size();
        while(count-->0) {
            PendingDrop pending=PENDING.removeFirst();
            if(pending.level.getServer()!=event.getServer()) {
                PENDING.addLast(pending);
                continue;
            }
            ItemEntity drop=new ItemEntity(pending.level,pending.pos.x,pending.pos.y+0.15D,pending.pos.z,
                    new ItemStack(ModItems.LIGHTNING_WHIP.get()));

            drop.setGlowingTag(true);
            pending.level.addFreshEntity(drop);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        Iterator<PendingDrop> iterator=PENDING.iterator();
        while(iterator.hasNext())if(iterator.next().level.getServer()==event.getServer())iterator.remove();
    }

    private record PendingDrop(ServerLevel level,Vec3 pos) {}
}
