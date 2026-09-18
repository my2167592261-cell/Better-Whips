package com.betterwhips.item;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LightningWhipTimeStop {

    public static final int FREEZE_TICKS = 2;

    public static final int HOP_TICKS = 4;
    private static final int SOFT_ENTRY_CAP = 2048;

    private static final ConcurrentMap<FreezeKey, List<FreezeWindow>> WINDOWS = new ConcurrentHashMap<>();

    private LightningWhipTimeStop() {}

    public static void freezeNow(Level level, LivingEntity target, long baseGameTime) {
        if (target != null) freezeNow(level, target.getUUID(), baseGameTime);
    }

    public static void freezeNow(Level level, UUID targetId, long baseGameTime) {
        if (level == null || targetId == null) return;

        addWindow(level, targetId, baseGameTime + 1L, baseGameTime + 1L + FREEZE_TICKS);
    }

    public static void scheduleRoute(Level level, List<UUID> route, long baseGameTime) {
        if (level == null || route == null || route.isEmpty()) return;
        for (int i = 0; i < route.size(); ++i) {
            UUID targetId = route.get(i);
            if (targetId == null) continue;
            long impactTick = baseGameTime + (long)i * HOP_TICKS;
            addWindow(level, targetId, impactTick + 1L, impactTick + 1L + FREEZE_TICKS);
        }
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity) || entity.isRemoved()) return;
        Level level = entity.level();
        FreezeKey key = new FreezeKey(level.dimension(), level.isClientSide, entity.getUUID());
        List<FreezeWindow> windows = WINDOWS.get(key);
        if (windows == null || windows.isEmpty()) return;

        long now = level.getGameTime();
        boolean freeze = false;
        List<FreezeWindow> keep = null;
        for (FreezeWindow window : windows) {
            if (window.endExclusive() <= now) continue;
            if (keep == null) keep = new ArrayList<>();
            keep.add(window);
            if (now >= window.startInclusive() && now < window.endExclusive()) freeze = true;
        }
        if (keep == null || keep.isEmpty()) WINDOWS.remove(key, windows);
        else if (keep.size() != windows.size()) WINDOWS.replace(key, windows, List.copyOf(keep));

        if (freeze) {

            event.setCanceled(true);
        }
    }

    private static void addWindow(Level level, UUID targetId, long start, long endExclusive) {
        if (endExclusive <= start) return;
        pruneOld(level.getGameTime());
        FreezeKey key = new FreezeKey(level.dimension(), level.isClientSide, targetId);
        WINDOWS.compute(key, (ignored, existing) -> mergeWindow(existing, new FreezeWindow(start, endExclusive)));
    }

    private static List<FreezeWindow> mergeWindow(List<FreezeWindow> existing, FreezeWindow added) {
        ArrayList<FreezeWindow> all = new ArrayList<>(existing == null ? 1 : existing.size() + 1);
        if (existing != null) all.addAll(existing);
        all.add(added);
        all.sort(Comparator.comparingLong(FreezeWindow::startInclusive));
        ArrayList<FreezeWindow> merged = new ArrayList<>(all.size());
        for (FreezeWindow current : all) {
            if (merged.isEmpty()) {
                merged.add(current);
                continue;
            }
            FreezeWindow previous = merged.get(merged.size() - 1);

            if (current.startInclusive() <= previous.endExclusive()) {
                merged.set(merged.size() - 1, new FreezeWindow(previous.startInclusive(),
                        Math.max(previous.endExclusive(), current.endExclusive())));
            } else {
                merged.add(current);
            }
        }
        return List.copyOf(merged);
    }

    private static void pruneOld(long now) {
        if (WINDOWS.size() < SOFT_ENTRY_CAP) return;
        WINDOWS.entrySet().removeIf(entry -> entry.getValue().stream()
                .allMatch(window -> window.endExclusive() + 40L < now));
    }

    private record FreezeKey(ResourceKey<Level> dimension, boolean clientSide, UUID uuid) {}
    private record FreezeWindow(long startInclusive, long endExclusive) {}
}
