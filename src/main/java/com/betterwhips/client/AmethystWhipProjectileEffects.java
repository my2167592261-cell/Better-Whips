package com.betterwhips.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class AmethystWhipProjectileEffects {
    private static final List<VisualShard> ACTIVE = new ArrayList<>();
    private static final int MAX_LIFE_TICKS = 60;
    private static final double GRAVITY_PER_TICK = 0.04D;
    private static final double DRAG = 0.98D;
    private static final int MAX_TRAIL_SAMPLES = 6;
    private static final double MAX_RENDER_DISTANCE_SQR = 128.0D * 128.0D;

    private AmethystWhipProjectileEffects() {}

    public static void spawn(double x, double y, double z,
                             double vx, double vy, double vz,
                             int ignoredTargetEntityId, long seed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 start = new Vec3(x, y, z);
        ACTIVE.add(new VisualShard(start, new Vec3(vx, vy, vz), ignoredTargetEntityId, seed));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<VisualShard> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            VisualShard shard = iterator.next();
            if (++shard.lifeTicks > MAX_LIFE_TICKS) {
                iterator.remove();
                continue;
            }
            if (shard.impactFade > 0) {
                if (--shard.impactFade == 0) {
                    iterator.remove();
                }
                continue;
            }
            shard.previous = shard.position;
            shard.velocity = shard.velocity.multiply(DRAG, DRAG, DRAG)
                    .add(0.0D, -GRAVITY_PER_TICK, 0.0D);
            Vec3 intendedNext = shard.position.add(shard.velocity);
            if (minecraft.player != null) {
                BlockHitResult blockHit = minecraft.level.clip(new ClipContext(
                        shard.position, intendedNext, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, minecraft.player));
                if (blockHit.getType() != HitResult.Type.MISS) {
                    shard.position = blockHit.getLocation();
                    shard.velocity = Vec3.ZERO;
                    shard.trail.addLast(shard.position);
                    while (shard.trail.size() > MAX_TRAIL_SAMPLES) {
                        shard.trail.removeFirst();
                    }
                    shard.impactFade = 2;
                    continue;
                }
            }
            shard.position = intendedNext;
            shard.trail.addLast(shard.position);
            while (shard.trail.size() > MAX_TRAIL_SAMPLES) {
                shard.trail.removeFirst();
            }
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType type = AmethystWhipPhysics.bladeMagicRenderType();
        VertexConsumer consumer = buffers.getBuffer(type);
        for (VisualShard shard : ACTIVE) {
            Vec3 current = shard.previous.lerp(shard.position, Mth.clamp(partialTick, 0.0F, 1.0F));
            if (current.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            renderTrail(poseStack.last(), consumer, camera, shard, current);
            renderTriangleShard(poseStack.last(), consumer, camera, shard, current);
        }
        buffers.endBatch(type);
    }

    private static void renderTrail(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                                    VisualShard shard, Vec3 current) {
        List<Vec3> samples = new ArrayList<>(shard.trail);
        if (samples.isEmpty()) {
            return;
        }
        samples.set(samples.size() - 1, current);
        for (int i = 0; i < samples.size() - 1; ++i) {
            Vec3 start = samples.get(i);
            Vec3 end = samples.get(i + 1);
            Vec3 tangent = end.subtract(start);
            if (tangent.lengthSqr() < 1.0E-10D) {
                continue;
            }
            Vec3 midpoint = start.lerp(end, 0.5D);
            Vec3 side = tangent.cross(camera.subtract(midpoint));
            if (side.lengthSqr() < 1.0E-10D) {
                side = tangent.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }
            if (side.lengthSqr() < 1.0E-10D) {
                continue;
            }
            side = side.normalize();
            float fade0 = (i + 1.0F) / samples.size();
            float fade1 = (i + 2.0F) / samples.size();
            emitRibbon(pose, consumer, camera, start, end, side,
                    AmethystWhipPhysics.TIP_TRAIL_OUTER_HALF_WIDTH,
                    fade0, fade1, 148, 96, 255, 128);
            emitRibbon(pose, consumer, camera, start, end, side,
                    AmethystWhipPhysics.TIP_TRAIL_CORE_HALF_WIDTH,
                    fade0, fade1, 228, 212, 255, 220);
        }
    }

    private static void emitRibbon(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                                   Vec3 start, Vec3 end, Vec3 side, float halfWidth,
                                   float fadeStart, float fadeEnd,
                                   int r, int g, int b, int alpha) {
        Vec3 s0 = start.add(side.scale(halfWidth));
        Vec3 s1 = start.add(side.scale(-halfWidth));
        Vec3 e1 = end.add(side.scale(-halfWidth));
        Vec3 e0 = end.add(side.scale(halfWidth));
        vertex(pose, consumer, camera, s0, r, g, b, Math.round(alpha * fadeStart));
        vertex(pose, consumer, camera, s1, r, g, b, Math.round(alpha * fadeStart));
        vertex(pose, consumer, camera, e1, r, g, b, Math.round(alpha * fadeEnd));
        vertex(pose, consumer, camera, e0, r, g, b, Math.round(alpha * fadeEnd));
    }

    private static void renderTriangleShard(PoseStack.Pose pose, VertexConsumer consumer,
                                            Vec3 camera, VisualShard shard, Vec3 center) {
        Vec3 forward = shard.velocity;
        if (forward.lengthSqr() < 1.0E-8D) {
            forward = shard.position.subtract(shard.previous);
        }
        if (forward.lengthSqr() < 1.0E-8D) {
            forward = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            forward = forward.normalize();
        }

        Vec3 toCamera = camera.subtract(center);
        Vec3 side = forward.cross(toCamera);
        if (side.lengthSqr() < 1.0E-10D) {
            side = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        }
        if (side.lengthSqr() < 1.0E-10D) {
            side = forward.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();

        double halfWidth = 0.078D;
        Vec3 tip = center.add(forward.scale(0.145D));
        Vec3 baseCenter = center.add(forward.scale(-0.095D));
        Vec3 left = baseCenter.add(side.scale(halfWidth));
        Vec3 right = baseCenter.add(side.scale(-halfWidth));

        triangle(pose, consumer, camera, tip, left, right, 174, 112, 255, 238);

        Vec3 innerTip = center.add(forward.scale(0.105D));
        Vec3 innerBase = center.add(forward.scale(-0.058D));
        Vec3 innerLeft = innerBase.add(side.scale(halfWidth * 0.48D));
        Vec3 innerRight = innerBase.add(side.scale(-halfWidth * 0.48D));
        triangle(pose, consumer, camera, innerTip, innerLeft, innerRight,
                235, 218, 255, 248);
    }

    private static void triangle(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                                 Vec3 a, Vec3 b, Vec3 c, int r, int g, int blue, int alpha) {
        vertex(pose, consumer, camera, a, r, g, blue, alpha);
        vertex(pose, consumer, camera, b, r, g, blue, alpha);
        vertex(pose, consumer, camera, c, r, g, blue, alpha);
        vertex(pose, consumer, camera, c, r, g, blue, alpha);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                               Vec3 point, int r, int g, int b, int a) {
        consumer.addVertex(pose.pose(),
                        (float) (point.x - camera.x),
                        (float) (point.y - camera.y),
                        (float) (point.z - camera.z))
                .setColor(r, g, b, Mth.clamp(a, 0, 255));
    }

    private static final class VisualShard {
        Vec3 previous;
        Vec3 position;
        Vec3 velocity;
        final int ignoredTargetEntityId;
        final long seed;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        int lifeTicks;
        int impactFade;

        VisualShard(Vec3 start, Vec3 velocity, int ignoredTargetEntityId, long seed) {
            this.previous = start;
            this.position = start;
            this.velocity = velocity;
            this.ignoredTargetEntityId = ignoredTargetEntityId;
            this.seed = seed;
            this.trail.add(start);
        }
    }
}
