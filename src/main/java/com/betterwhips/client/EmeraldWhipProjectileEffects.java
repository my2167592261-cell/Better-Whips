package com.betterwhips.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class EmeraldWhipProjectileEffects {
    private static final List<VisualBolt> ACTIVE = new ArrayList<>();
    private static final double SPEED_PER_TICK = 0.5D;
    private static final int MAX_LIFE_TICKS = 200;
    private static final int MAX_TRAIL_SAMPLES = 6;
    private static final double MAX_RENDER_DISTANCE_SQR = 128.0D * 128.0D;

    private EmeraldWhipProjectileEffects() {}

    public static void spawn(double x, double y, double z, int targetEntityId, long seed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 start = new Vec3(x, y, z);
        ACTIVE.add(new VisualBolt(start, targetEntityId, seed));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<VisualBolt> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            VisualBolt bolt = iterator.next();
            if (++bolt.lifeTicks > MAX_LIFE_TICKS) {
                iterator.remove();
                continue;
            }
            if (bolt.impactFade > 0) {
                if (--bolt.impactFade == 0) {
                    iterator.remove();
                }
                continue;
            }
            Entity target = minecraft.level.getEntity(bolt.targetEntityId);
            if (target == null || !target.isAlive()) {
                iterator.remove();
                continue;
            }
            Vec3 targetPoint = target.getBoundingBox().getCenter();
            Vec3 toTarget = targetPoint.subtract(bolt.position);
            double distance = toTarget.length();
            bolt.previous = bolt.position;
            bolt.position = distance <= SPEED_PER_TICK
                    ? targetPoint
                    : bolt.position.add(toTarget.scale(SPEED_PER_TICK / distance));
            bolt.trail.addLast(bolt.position);
            while (bolt.trail.size() > MAX_TRAIL_SAMPLES) {
                bolt.trail.removeFirst();
            }
            if (bolt.position.distanceToSqr(targetPoint) <= 0.16D) {
                bolt.impactFade = 3;
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
        RenderType type = EmeraldWhipPhysics.bladeMagicRenderType();
        VertexConsumer consumer = buffers.getBuffer(type);
        for (VisualBolt bolt : ACTIVE) {
            Vec3 current = bolt.previous.lerp(bolt.position, Mth.clamp(partialTick, 0.0F, 1.0F));
            if (current.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            renderTrail(poseStack.last(), consumer, camera, bolt, current);
            renderCrystal(poseStack.last(), consumer, camera, bolt, current);
        }
        buffers.endBatch(type);
    }

    private static void renderTrail(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                                    VisualBolt bolt, Vec3 current) {
        List<Vec3> samples = new ArrayList<>(bolt.trail);
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
                    EmeraldWhipPhysics.TIP_TRAIL_OUTER_HALF_WIDTH,
                    fade0, fade1, 48, 205, 104, 125);
            emitRibbon(pose, consumer, camera, start, end, side,
                    EmeraldWhipPhysics.TIP_TRAIL_CORE_HALF_WIDTH,
                    fade0, fade1, 190, 255, 214, 210);
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

    private static void renderCrystal(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
                                      VisualBolt bolt, Vec3 center) {
        Vec3 forward = center.subtract(bolt.previous);
        if (forward.lengthSqr() < 1.0E-8D) {
            forward = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            forward = forward.normalize();
        }
        Vec3 reference = Math.abs(forward.y) > 0.92D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = forward.cross(reference).normalize();
        Vec3 up = side.cross(forward).normalize();

        double halfLength = 0.145D;
        double radius = 0.090D;
        Vec3 front = center.add(forward.scale(halfLength));
        Vec3 back = center.add(forward.scale(-halfLength));
        Vec3[] ring = new Vec3[6];
        for (int i = 0; i < ring.length; ++i) {
            double angle = Math.PI / 6.0D + i * Math.PI / 3.0D;
            ring[i] = center
                    .add(side.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));
        }

        int[][] faceColors = {
                {88, 255, 154, 235},
                {34, 210, 104, 230},
                {122, 255, 184, 240},
                {25, 176, 84, 225},
                {72, 238, 132, 235},
                {42, 198, 96, 230}
        };
        for (int i = 0; i < ring.length; ++i) {
            Vec3 a = ring[i];
            Vec3 b = ring[(i + 1) % ring.length];
            int[] c = faceColors[i];
            triangle(pose, consumer, camera, front, a, b, c[0], c[1], c[2], c[3]);
            int backIndex = (i + 3) % faceColors.length;
            int[] cb = faceColors[backIndex];
            triangle(pose, consumer, camera, back, b, a, cb[0], cb[1], cb[2], cb[3]);
        }

        Vec3 coreFront = center.add(forward.scale(halfLength * 0.58D));
        Vec3 coreBack = center.add(forward.scale(-halfLength * 0.58D));
        double coreRadius = radius * 0.43D;
        Vec3[] coreRing = new Vec3[4];
        for (int i = 0; i < coreRing.length; ++i) {
            double angle = Math.PI / 4.0D + i * Math.PI / 2.0D;
            coreRing[i] = center
                    .add(side.scale(Math.cos(angle) * coreRadius))
                    .add(up.scale(Math.sin(angle) * coreRadius));
        }
        for (int i = 0; i < coreRing.length; ++i) {
            Vec3 a = coreRing[i];
            Vec3 b = coreRing[(i + 1) % coreRing.length];
            triangle(pose, consumer, camera, coreFront, a, b, 204, 255, 220, 245);
            triangle(pose, consumer, camera, coreBack, b, a, 156, 255, 190, 230);
        }
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

    private static final class VisualBolt {
        Vec3 previous;
        Vec3 position;
        final int targetEntityId;
        final long seed;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        int lifeTicks;
        int impactFade;

        VisualBolt(Vec3 start, int targetEntityId, long seed) {
            this.previous = start;
            this.position = start;
            this.targetEntityId = targetEntityId;
            this.seed = seed;
            this.trail.add(start);
        }
    }
}
