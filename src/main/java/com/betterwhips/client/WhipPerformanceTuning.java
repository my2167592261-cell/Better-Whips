package com.betterwhips.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class WhipPerformanceTuning {
    private static final double MAIN_RENDER_DISTANCE_SQR = 96.0D * 96.0D;
    private static final double FANCY_DISTANCE_SQR = 24.0D * 24.0D;
    private static final double CONNECTOR_DISTANCE_SQR = 48.0D * 48.0D;
    private static final double TRAIL_DISTANCE_SQR = 40.0D * 40.0D;
    private static final double DROPPED_FANCY_DISTANCE_SQR = 20.0D * 20.0D;

    private static final double REMOTE_DETAIL_LOSS_PER_BLOCK = 0.05D;

    private static final float MIN_REMOTE_DETAIL_QUALITY = 0.25F;

    private static final int MIN_REMOTE_VISUAL_SUBSTEPS = 4;

    private static final int MAX_FANCY_PER_FAMILY = 4;
    private static final int MAX_CONNECTOR_PER_FAMILY = 8;
    private static final int MAX_TRAIL_PER_FAMILY = 6;
    private static final int MAX_DROPPED_FANCY_PER_FAMILY = 4;

    private static long frameKey = Long.MIN_VALUE;
    private static final Map<String, Integer> fancyUsed = new HashMap<>();
    private static final Map<String, Integer> connectorUsed = new HashMap<>();
    private static final Map<String, Integer> trailUsed = new HashMap<>();
    private static final Map<String, Integer> droppedFancyUsed = new HashMap<>();

    private static final ThreadLocal<ArrayDeque<ItemEntity>> ITEM_RENDER_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static final ThreadLocal<ArrayDeque<Float>> GEOMETRY_QUALITY_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);

    private WhipPerformanceTuning() {}

    public static void pushItemRenderContext(ItemEntity entity) {
        if (entity != null) {
            ITEM_RENDER_CONTEXT.get().push(entity);
        }
    }

    public static void popItemRenderContext() {
        ArrayDeque<ItemEntity> stack = ITEM_RENDER_CONTEXT.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            ITEM_RENDER_CONTEXT.remove();
        }
    }

    public static float remoteRenderQuality(LivingEntity holder) {
        if (holder == null) {
            return 1.0F;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || holder == minecraft.player) {
            return 1.0F;
        }
        double distance = holder.position().distanceTo(minecraft.player.position());
        return (float)Math.max(MIN_REMOTE_DETAIL_QUALITY,
                Math.min(1.0D, 1.0D - distance * REMOTE_DETAIL_LOSS_PER_BLOCK));
    }

    public static void pushGeometryQuality(LivingEntity holder) {
        GEOMETRY_QUALITY_CONTEXT.get().push(remoteRenderQuality(holder));
    }

    public static void popGeometryQuality() {
        ArrayDeque<Float> stack = GEOMETRY_QUALITY_CONTEXT.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            GEOMETRY_QUALITY_CONTEXT.remove();
        }
    }

    public static boolean shouldRenderDetailElement(int index, int count) {
        if (count <= 1 || index < 0 || index >= count) {
            return index >= 0 && index < count;
        }
        ArrayDeque<Float> stack = GEOMETRY_QUALITY_CONTEXT.get();
        float quality = stack.isEmpty() ? 1.0F : stack.peek();
        if (quality >= 0.999F) {
            return true;
        }
        int target = Math.max(1, Math.min(count, (int)Math.ceil(count * quality)));
        if (target >= count) {
            return true;
        }
        if (target == 1) {
            return index == 0;
        }
        for (int sample = 0; sample < target; ++sample) {
            int selected = Math.round(sample * (count - 1.0F) / (target - 1.0F));
            if (index == selected) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldRenderMain(LivingEntity holder, Vec3 camera) {
        if (holder == null || camera == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (holder == minecraft.player) {
            return true;
        }
        return holder.position().distanceToSqr(camera) <= MAIN_RENDER_DISTANCE_SQR;
    }

    public static boolean allowFancyLayer(String family, LivingEntity holder, Vec3 camera) {
        if (holder == null || camera == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (holder == minecraft.player) {
            return true;
        }
        if (holder.position().distanceToSqr(camera) > FANCY_DISTANCE_SQR) {
            return false;
        }
        refreshFrameBudget();
        return takeBudget(fancyUsed, family, MAX_FANCY_PER_FAMILY);
    }

    public static boolean allowConnectors(String family, LivingEntity holder, Vec3 camera) {
        if (holder == null || camera == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (holder == minecraft.player) {
            return true;
        }
        if (holder.position().distanceToSqr(camera) > CONNECTOR_DISTANCE_SQR) {
            return false;
        }
        refreshFrameBudget();
        return takeBudget(connectorUsed, family, MAX_CONNECTOR_PER_FAMILY);
    }

    public static boolean allowTrail(String family, LivingEntity holder, Vec3 camera) {
        if (holder == null || camera == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (holder == minecraft.player) {
            return true;
        }
        if (holder.position().distanceToSqr(camera) > TRAIL_DISTANCE_SQR) {
            return false;
        }
        refreshFrameBudget();
        return takeBudget(trailUsed, family, MAX_TRAIL_PER_FAMILY);
    }

    public static int capRemoteSubsteps(LivingEntity holder, int requested) {
        if (holder == null || requested <= 1) {
            return requested;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (holder == minecraft.player || minecraft.player == null) {
            return requested;
        }

        int qualityCap = Math.max(MIN_REMOTE_VISUAL_SUBSTEPS,
                (int)Math.ceil(requested * remoteRenderQuality(holder)));

        double distanceSqr = holder.position().distanceToSqr(minecraft.player.position());
        int legacyCap = requested;
        if (distanceSqr > 48.0D * 48.0D) {
            legacyCap = Math.min(legacyCap, 6);
        } else if (distanceSqr > 24.0D * 24.0D) {
            legacyCap = Math.min(legacyCap, 10);
        } else if (distanceSqr > 12.0D * 12.0D) {
            legacyCap = Math.min(legacyCap, 16);
        }
        return Math.min(requested, Math.min(qualityCap, legacyCap));
    }

    public static boolean allowItemFancy(String family, ItemDisplayContext context) {
        if (context != ItemDisplayContext.GROUND) {
            return true;
        }
        ArrayDeque<ItemEntity> stack = ITEM_RENDER_CONTEXT.get();
        if (stack.isEmpty()) {
            return false;
        }
        ItemEntity entity = stack.peek();
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == null || minecraft.player == null
                || entity.position().distanceToSqr(minecraft.player.position()) > DROPPED_FANCY_DISTANCE_SQR) {
            return false;
        }
        refreshFrameBudget();
        return takeBudget(droppedFancyUsed, family, MAX_DROPPED_FANCY_PER_FAMILY);
    }

    private static boolean takeBudget(Map<String, Integer> usage, String family, int maximum) {
        String key = family == null ? "generic" : family;
        int used = usage.getOrDefault(key, 0);
        if (used >= maximum) {
            return false;
        }
        usage.put(key, used + 1);
        return true;
    }

    private static void refreshFrameBudget() {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        long key = (gameTime << 32) ^ (Float.floatToRawIntBits(partial) & 0xffffffffL);
        if (key == frameKey) {
            return;
        }
        frameKey = key;
        fancyUsed.clear();
        connectorUsed.clear();
        trailUsed.clear();
        droppedFancyUsed.clear();
    }
}
