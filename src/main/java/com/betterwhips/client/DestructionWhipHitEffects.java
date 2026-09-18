package com.betterwhips.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class DestructionWhipHitEffects {
    private static final List<KickVfx> ACTIVE = new ArrayList<>();
    private static final List<LaserVfx> LASERS = new ArrayList<>();
    private static final double MAX_RENDER_DISTANCE_SQR = 80.0D * 80.0D;
    private static final float LASER_LIFETIME_TICKS = 6.0F;
    private static final int TAKEOFF = 0;
    private static final int TRAIL = 1;
    private static final int EXPLOSION = 2;
    private static final float HIT_SIZE_SCALE = 0.32F;
    private static final float HIT_ALPHA_SCALE = 1.00F;

    private DestructionWhipHitEffects() {
    }

    public static void spawnBurst(double x, double y, double z, long seed) {
        spawnBurst(x, y, z, 0.0D, 0.0D, 0.0D, false, seed);
    }

    public static void spawnBurst(double x, double y, double z,
                                  double dx, double dy, double dz,
                                  boolean magicMirror, long seed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 center = new Vec3(x, y, z);
        Vec3 direction = new Vec3(dx, dy, dz);
        if (direction.lengthSqr() <= 1.0E-7D) {
            direction = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            direction = direction.normalize();
        }
        ACTIVE.add(new KickVfx(
                EXPLOSION,
                center,
                center.add(direction),
                seed,
                minecraft.level.getGameTime(),
                magicMirror
        ));
    }

    public static void spawnLaser(int ownerEntityId, int sourceSegment, int targetEntityId,
                                  double fallbackFromX, double fallbackFromY, double fallbackFromZ,
                                  double fallbackToX, double fallbackToY, double fallbackToZ,
                                  float fullWidth, long seed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 fallbackFrom = new Vec3(fallbackFromX, fallbackFromY, fallbackFromZ);
        Vec3 fallbackTo = new Vec3(fallbackToX, fallbackToY, fallbackToZ);
        LASERS.add(new LaserVfx(ownerEntityId, sourceSegment, targetEntityId,
                fallbackFrom, fallbackTo, Math.max(0.001F, fullWidth),
                seed, minecraft.level.getGameTime()));
    }

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.clear();
            LASERS.clear();
            return;
        }
        long now = minecraft.level.getGameTime();
        Iterator<KickVfx> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            KickVfx event = iterator.next();
            long max = switch (event.kind) {
                case EXPLOSION -> 5L;
                case TAKEOFF -> 7L;
                default -> 5L;
            };
            if (now - event.spawnTick > max) {
                iterator.remove();
            }
        }
        Iterator<LaserVfx> laserIterator = LASERS.iterator();
        while (laserIterator.hasNext()) {
            LaserVfx laser = laserIterator.next();
            if (now - laser.spawnTick > (long)Math.ceil(LASER_LIFETIME_TICKS) + 1L) {
                laserIterator.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
        LASERS.clear();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        clientTick();
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        render(event.getPoseStack(), event.getCamera().getPosition(), partialTick);
    }

    public static void render(PoseStack poseStack, Vec3 cameraPos, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || (ACTIVE.isEmpty() && LASERS.isEmpty())
                || !RoyalSlimeWhipHitRenderTypes.ready()) {
            return;
        }
        float now = minecraft.level.getGameTime() + partialTick;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack.Pose pose = poseStack.last();

        VertexConsumer translucent = buffers.getBuffer(RoyalSlimeWhipHitRenderTypes.TRANSLUCENT_THROUGH_WALLS);
        for (KickVfx event : ACTIVE) {
            if (event.from.distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR
                    && event.to.distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            drawEvent(translucent, pose, cameraPos, event, now, false);
        }
        for (LaserVfx laser : LASERS) {
            LaserEndpoints endpoints = resolveLaserEndpoints(laser, partialTick);
            if (endpoints.from.distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR
                    && endpoints.to.distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            drawLaser(translucent, pose, cameraPos, laser, endpoints, now, false);
        }
        buffers.endBatch(RoyalSlimeWhipHitRenderTypes.TRANSLUCENT_THROUGH_WALLS);

        VertexConsumer glow = buffers.getBuffer(RoyalSlimeWhipHitRenderTypes.GLOW_THROUGH_WALLS);
        for (KickVfx event : ACTIVE) {
            if (event.from.distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR
                    && event.to.distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            drawEvent(glow, pose, cameraPos, event, now, true);
        }
        for (LaserVfx laser : LASERS) {
            LaserEndpoints endpoints = resolveLaserEndpoints(laser, partialTick);
            if (endpoints.from.distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR
                    && endpoints.to.distanceToSqr(cameraPos) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            drawLaser(glow, pose, cameraPos, laser, endpoints, now, true);
        }
        buffers.endBatch(RoyalSlimeWhipHitRenderTypes.GLOW_THROUGH_WALLS);
    }

    private static LaserEndpoints resolveLaserEndpoints(LaserVfx laser, float partialTick) {
        Vec3 from = DestructionWhipPhysics.currentSegmentMidpoint(
                laser.ownerEntityId, laser.sourceSegment, partialTick);
        if (from == null) {
            from = laser.fallbackFrom;
        }
        Vec3 to = currentEntityCenter(laser.targetEntityId);
        if (to == null) {
            to = laser.fallbackTo;
        }
        return new LaserEndpoints(from, to);
    }

    private static Vec3 currentEntityCenter(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity == null) {
            return null;
        }

        return entity.getBoundingBox().getCenter();
    }

    private static void drawLaser(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            LaserVfx laser,
            LaserEndpoints endpoints,
            float now,
            boolean glowPass
    ) {
        float age = now - laser.spawnTick;
        float life = Mth.clamp(age / LASER_LIFETIME_TICKS, 0.0F, 1.0F);

        float shrink = 1.0F - smoothstep(0.0F, 1.0F, life);
        if (shrink <= 0.001F) {
            return;
        }
        float halfWidth = laser.fullWidth * 0.5F * shrink;
        Vec3 direction = normalized(endpoints.to.subtract(endpoints.from));
        Vec3 side = horizontalSide(direction);
        Vec3 up = normalized(direction.cross(side));
        int alpha = Math.round((glowPass ? 118.0F : 242.0F)
                * (0.72F + 0.28F * shrink));
        int red = 255;
        int green = glowPass ? 48 : 24;
        int blue = glowPass ? 30 : 18;

        drawRibbon(vertices, pose, cameraPos, endpoints.from, endpoints.to, side,
                halfWidth, red, green, blue, alpha);
        drawRibbon(vertices, pose, cameraPos, endpoints.from, endpoints.to, up,
                halfWidth, red, green, blue, alpha);
    }

    private static void drawEvent(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            KickVfx event,
            float now,
            boolean glowPass
    ) {
        switch (event.kind) {
            case TAKEOFF -> drawTakeoff(vertices, pose, cameraPos, event, now, glowPass);
            case TRAIL -> drawTrail(vertices, pose, cameraPos, event, now, glowPass);
            case EXPLOSION -> drawExplosion(vertices, pose, cameraPos, event, now, glowPass);
            default -> {
            }
        }
    }

    private static void drawTakeoff(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            KickVfx event,
            float now,
            boolean glowPass
    ) {
        float age = now - event.spawnTick;
        float life = Mth.clamp(age / 5.6F, 0.0F, 1.0F);
        float fade = 1.0F - smoothstep(0.26F, 1.0F, life);
        Vec3 direction = normalized(event.to.subtract(event.from));
        Vec3 side = horizontalSide(direction);
        Vec3 up = normalized(direction.cross(side));

        drawTaperedBeam(vertices, pose, cameraPos,
                event.from.subtract(direction.scale(0.12D)),
                event.from.add(direction.scale(0.84D)),
                side,
                up,
                glowPass ? 0.16F : 0.07F,
                glowPass ? 0.06F : 0.026F,
                255,
                glowPass ? 184 : 235,
                glowPass ? 104 : 186,
                Math.round((glowPass ? 72.0F : 210.0F) * fade));

        drawCircularRing(vertices, pose, cameraPos,
                event.from.add(0.0D, 0.16D, 0.0D),
                new Vec3(0.0D, 1.0D, 0.0D),
                0.24F + life * 0.36F,
                glowPass ? 0.10F : 0.038F,
                255,
                glowPass ? 194 : 228,
                glowPass ? 126 : 188,
                Math.round((glowPass ? 58.0F : 170.0F) * fade),
                10);

        for (int i = 0; i < 2; i++) {
            float radius = 0.28F + i * 0.08F + life * 0.08F;
            float alpha = (glowPass ? 42.0F : 132.0F) * fade * (1.0F - i * 0.18F);
            drawArcRibbon(vertices, pose, cameraPos,
                    event.from.add(direction.scale(0.12D)).add(up.scale(0.10D - i * 0.12D)),
                    side, up,
                    radius,
                    glowPass ? 0.07F : 0.026F,
                    -1.40F + i * 0.22F,
                    0.55F + i * 0.24F,
                    255,
                    glowPass ? 170 : 220,
                    glowPass ? 116 : 176,
                    Math.round(alpha));
        }
    }

    private static void drawTrail(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            KickVfx event,
            float now,
            boolean glowPass
    ) {
        float age = now - event.spawnTick;
        float life = Mth.clamp(age / 3.8F, 0.0F, 1.0F);
        float fade = 1.0F - smoothstep(0.12F, 1.0F, life);
        Vec3 direction = normalized(event.to.subtract(event.from));
        Vec3 side = horizontalSide(direction);
        Vec3 up = normalized(direction.cross(side));

        drawTaperedBeam(vertices, pose, cameraPos,
                event.from.subtract(direction.scale(0.18D)),
                event.to.add(direction.scale(0.14D)),
                side,
                up,
                glowPass ? 0.14F : 0.055F,
                glowPass ? 0.08F : 0.024F,
                255,
                glowPass ? 160 : 226,
                glowPass ? 88 : 176,
                Math.round((glowPass ? 52.0F : 180.0F) * fade));

        for (int lane = 0; lane < 2; lane++) {
            float phase = life * 1.35F + lane * (float) Math.PI;
            Vec3 offsetA = side.scale(Math.cos(phase) * 0.17D).add(up.scale(Math.sin(phase) * 0.10D));
            Vec3 offsetB = side.scale(Math.cos(phase + 0.9F) * 0.11D).add(up.scale(Math.sin(phase + 0.9F) * 0.08D));
            drawRibbon(vertices, pose, cameraPos,
                    event.from.add(offsetA),
                    event.to.add(offsetB),
                    normalized(offsetA.cross(direction)),
                    glowPass ? 0.038F : 0.012F,
                    255,
                    glowPass ? 214 : 180,
                    glowPass ? 255 : 255,
                    Math.round((glowPass ? 34.0F : 132.0F) * fade));
        }
    }

    private static void drawExplosion(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            KickVfx event,
            float now,
            boolean glowPass
    ) {
        float age = now - event.spawnTick;
        float life = Mth.clamp(age / 3.15F, 0.0F, 1.0F);
        float appear = smoothstep(0.0F, 0.055F, life);
        float fade = 1.0F - smoothstep(0.18F, 1.0F, life);
        float energy = appear * fade;
        if (energy <= 0.0F) return;

        Vec3 center = event.from;
        Vec3 direction = normalized(event.to.subtract(event.from));
        Vec3 side = horizontalSide(direction);
        Vec3 up = normalized(direction.cross(side));

        drawTaperedBeam(vertices, pose, cameraPos,
                center.subtract(direction.scale(0.50D)),
                center.add(direction.scale(0.78D)),
                side, up,
                glowPass ? 0.105F : 0.043F,
                glowPass ? 0.025F : 0.010F,
                255, glowPass ? 70 : 34, glowPass ? 52 : 26,
                Math.round((glowPass ? 116.0F : 248.0F) * energy));

        drawTaperedBeam(vertices, pose, cameraPos,
                center.subtract(direction.scale(0.24D)),
                center.add(direction.scale(0.54D)),
                side, up,
                glowPass ? 0.042F : 0.018F,
                glowPass ? 0.012F : 0.004F,
                255, 214, 192,
                Math.round((glowPass ? 132.0F : 255.0F) * energy));

        BillboardBasis basis = trajectoryBasis(center, cameraPos, direction);
        float cutLength = (glowPass ? 1.18F : 0.92F) * (0.94F + life * 0.08F);
        float cutWidth = glowPass ? 0.050F : 0.026F;
        drawPointedSlash(vertices, pose, cameraPos, center, basis,
                cutLength, 0.08F, cutWidth,
                255, glowPass ? 52 : 22, glowPass ? 42 : 18,
                Math.round((glowPass ? 96.0F : 235.0F) * energy));

        drawCircularRing(vertices, pose, cameraPos, center, direction,
                0.12F + life * 0.18F, glowPass ? 0.030F : 0.014F,
                255, glowPass ? 78 : 36, glowPass ? 58 : 24,
                Math.round((glowPass ? 74.0F : 188.0F) * energy), 8);
    }

    private static void drawPointedSlash(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            Vec3 center,
            BillboardBasis basis,
            float length,
            float slant,
            float halfWidth,
            int r,
            int g,
            int b,
            int alpha
    ) {
        if (alpha <= 0) {
            return;
        }
        int steps = 12;
        Vec3[] centers = new Vec3[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float along = (t - 0.5F) * length;
            float offset = along * slant;
            centers[i] = center
                    .add(basis.right().scale(along))
                    .add(basis.up().scale(offset));
        }

        Vec3 prevLeft = null;
        Vec3 prevRight = null;
        float prevAlpha = 0.0F;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            Vec3 tangent;
            if (i == 0) {
                tangent = centers[1].subtract(centers[0]);
            } else if (i == steps) {
                tangent = centers[steps].subtract(centers[steps - 1]);
            } else {
                tangent = centers[i + 1].subtract(centers[i - 1]);
            }
            double tx = tangent.dot(basis.right());
            double ty = tangent.dot(basis.up());
            Vec3 normal = basis.right().scale(-ty).add(basis.up().scale(tx));
            if (normal.lengthSqr() < 1.0E-10D) {
                normal = basis.up();
            } else {
                normal = normal.normalize();
            }

            float widthFactor = (float) Math.pow(Mth.sin(t * Mth.PI), 1.85D);
            float localWidth = halfWidth * widthFactor;
            float localAlpha = alpha * (0.18F + 0.82F * widthFactor);
            Vec3 left = centers[i].subtract(normal.scale(localWidth));
            Vec3 right = centers[i].add(normal.scale(localWidth));

            if (prevLeft != null && prevRight != null) {
                add(vertices, pose, prevLeft.subtract(cameraPos), 0.0F, 0.0F, r, g, b, Math.round(prevAlpha));
                add(vertices, pose, prevRight.subtract(cameraPos), 1.0F, 0.0F, r, g, b, Math.round(prevAlpha));
                add(vertices, pose, right.subtract(cameraPos), 1.0F, 1.0F, r, g, b, Math.round(localAlpha));
                add(vertices, pose, left.subtract(cameraPos), 0.0F, 1.0F, r, g, b, Math.round(localAlpha));
            }
            prevLeft = left;
            prevRight = right;
            prevAlpha = localAlpha;
        }
    }

    private static void drawComicBurstRing(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            Vec3 center,
            BillboardBasis basis,
            float radius,
            float thickness,
            long seed,
            int r,
            int g,
            int b,
            int alpha
    ) {
        if (alpha <= 0) return;
        Random random = new Random(seed ^ 0xBB67AE8584CAA73BL);
        int points = 24;
        float[] outer = new float[points];
        float[] inner = new float[points];
        for (int i = 0; i < points; i++) {
            boolean spike = (i & 1) == 0;
            float jitter = 0.90F + random.nextFloat() * 0.20F;
            outer[i] = radius * (spike ? (1.20F + random.nextFloat() * 0.40F) : (0.78F + random.nextFloat() * 0.16F)) * jitter;
            inner[i] = Math.max(0.14F, outer[i] - thickness * (spike ? 1.15F : 0.72F));
        }
        for (int i = 0; i < points; i++) {
            int j = (i + 1) % points;
            double a0 = Math.PI * 2.0D * i / points;
            double a1 = Math.PI * 2.0D * j / points;
            Vec3 o0 = billboardPoint(center, basis, a0, outer[i]);
            Vec3 o1 = billboardPoint(center, basis, a1, outer[j]);
            Vec3 i1 = billboardPoint(center, basis, a1, inner[j]);
            Vec3 i0 = billboardPoint(center, basis, a0, inner[i]);
            drawQuad(vertices, pose, cameraPos, o0, o1, i1, i0, r, g, b, alpha, 0.36F);
        }
    }

    private static void drawComicNeedle(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            Vec3 center,
            BillboardBasis basis,
            float angle,
            float startRadius,
            float length,
            float halfWidth,
            int r,
            int g,
            int b,
            int alpha
    ) {
        if (alpha <= 0) return;
        Vec3 radial = basis.right().scale(Math.cos(angle)).add(basis.up().scale(Math.sin(angle))).normalize();
        Vec3 tangent = basis.right().scale(-Math.sin(angle)).add(basis.up().scale(Math.cos(angle))).normalize();
        Vec3 start = center.add(radial.scale(startRadius));
        Vec3 end = start.add(radial.scale(length));
        Vec3 w0 = tangent.scale(halfWidth);
        Vec3 w1 = tangent.scale(halfWidth * 0.06D);
        Vec3 p0 = start.subtract(w0).subtract(cameraPos);
        Vec3 p1 = start.add(w0).subtract(cameraPos);
        Vec3 p2 = end.add(w1).subtract(cameraPos);
        Vec3 p3 = end.subtract(w1).subtract(cameraPos);
        add(vertices, pose, p0, 0, 0, r, g, b, alpha);
        add(vertices, pose, p1, 0, 1, r, g, b, alpha);
        add(vertices, pose, p2, 1, 1, r, g, b, 0);
        add(vertices, pose, p3, 1, 0, r, g, b, 0);
    }

    private static BillboardBasis trajectoryBasis(Vec3 center, Vec3 cameraPos, Vec3 trajectory) {
        Vec3 viewNormal = center.subtract(cameraPos);
        if (viewNormal.lengthSqr() <= 1.0E-7D) {
            viewNormal = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            viewNormal = viewNormal.normalize();
        }

        Vec3 realTrajectory = trajectory.lengthSqr() <= 1.0E-7D
                ? new Vec3(1.0D, 0.0D, 0.0D) : trajectory.normalize();

        Vec3 screenTrajectory = realTrajectory.subtract(
                viewNormal.scale(realTrajectory.dot(viewNormal)));
        if (screenTrajectory.lengthSqr() <= 1.0E-7D) {
            Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
            screenTrajectory = worldUp.cross(viewNormal);
            if (screenTrajectory.lengthSqr() <= 1.0E-7D) {
                screenTrajectory = new Vec3(1.0D, 0.0D, 0.0D);
            }
        }
        Vec3 right = screenTrajectory.normalize();
        Vec3 up = viewNormal.cross(right);
        if (up.lengthSqr() <= 1.0E-7D) {
            up = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            up = up.normalize();
        }
        return new BillboardBasis(right, up);
    }

    private static BillboardBasis billboardBasis(Vec3 center, Vec3 cameraPos, float rotation) {
        Vec3 view = center.subtract(cameraPos);
        Vec3 right = new Vec3(view.z, 0.0D, -view.x);
        if (right.lengthSqr() <= 1.0E-7D) right = new Vec3(1.0D, 0.0D, 0.0D);
        else right = right.normalize();
        Vec3 up = view.lengthSqr() <= 1.0E-7D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : right.cross(view.normalize()).normalize();
        double c = Math.cos(rotation);
        double s = Math.sin(rotation);
        return new BillboardBasis(
                right.scale(c).add(up.scale(s)),
                up.scale(c).subtract(right.scale(s))
        );
    }

    private static Vec3 billboardPoint(Vec3 center, BillboardBasis basis, double angle, float radius) {
        return center
                .add(basis.right().scale(Math.cos(angle) * radius))
                .add(basis.up().scale(Math.sin(angle) * radius));
    }

    private static void drawTaperedBeam(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            Vec3 from,
            Vec3 to,
            Vec3 side,
            Vec3 up,
            float startRadius,
            float endRadius,
            int r,
            int g,
            int b,
            int alpha
    ) {
        if (alpha <= 0) {
            return;
        }
        Vec3 aSide = side.normalize().scale(startRadius);
        Vec3 aUp = up.normalize().scale(startRadius * 0.62D);
        Vec3 bSide = side.normalize().scale(endRadius);
        Vec3 bUp = up.normalize().scale(endRadius * 0.62D);

        drawQuad(vertices, pose, cameraPos,
                from.subtract(aSide), from.add(aSide), to.add(bSide), to.subtract(bSide),
                r, g, b, alpha, 0.0F);
        drawQuad(vertices, pose, cameraPos,
                from.subtract(aUp), from.add(aUp), to.add(bUp), to.subtract(bUp),
                r, g, b, Math.round(alpha * 0.82F), 0.0F);
    }

    private static void drawArcRibbon(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            Vec3 center,
            Vec3 axisX,
            Vec3 axisY,
            float radius,
            float halfWidth,
            float startAngle,
            float endAngle,
            int r,
            int g,
            int b,
            int alpha
    ) {
        if (alpha <= 0) {
            return;
        }
        Vec3 x = axisX.normalize();
        Vec3 y = axisY.normalize();
        int steps = 7;
        Vec3 prevL = null;
        Vec3 prevR = null;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / (float) steps;
            float angle = Mth.lerp(t, startAngle, endAngle);
            Vec3 dir = x.scale(Math.cos(angle)).add(y.scale(Math.sin(angle)));
            Vec3 p = center.add(dir.scale(radius));
            Vec3 normal = normalized(dir.cross(new Vec3(0.0D, 1.0D, 0.0D)));
            Vec3 left = p.subtract(normal.scale(halfWidth));
            Vec3 right = p.add(normal.scale(halfWidth));
            if (prevL != null) {
                drawQuad(vertices, pose, cameraPos, prevL, prevR, right, left,
                        r, g, b, Math.round(alpha * (1.0F - t * 0.25F)), 0.28F);
            }
            prevL = left;
            prevR = right;
        }
    }

    private static void drawCircularRing(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            Vec3 center,
            Vec3 normal,
            float radius,
            float thickness,
            int r,
            int g,
            int b,
            int alpha,
            int segments
    ) {
        if (alpha <= 0) {
            return;
        }
        Vec3 n = normalized(normal);
        Vec3 tangent = normalized(Math.abs(n.y) > 0.92D ? new Vec3(1.0D, 0.0D, 0.0D).cross(n) : new Vec3(0.0D, 1.0D, 0.0D).cross(n));
        Vec3 bitangent = normalized(n.cross(tangent));
        float inner = Math.max(0.02F, radius - thickness);

        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 outer0 = center.add(tangent.scale(Math.cos(a0) * radius)).add(bitangent.scale(Math.sin(a0) * radius));
            Vec3 outer1 = center.add(tangent.scale(Math.cos(a1) * radius)).add(bitangent.scale(Math.sin(a1) * radius));
            Vec3 inner1 = center.add(tangent.scale(Math.cos(a1) * inner)).add(bitangent.scale(Math.sin(a1) * inner));
            Vec3 inner0 = center.add(tangent.scale(Math.cos(a0) * inner)).add(bitangent.scale(Math.sin(a0) * inner));
            drawQuad(vertices, pose, cameraPos, outer0, outer1, inner1, inner0,
                    r, g, b, alpha, 0.32F);
        }
    }

    private static void drawSpike(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            Vec3 center,
            Vec3 direction,
            float length,
            float halfWidth,
            int r,
            int g,
            int b,
            int alpha
    ) {
        Vec3 start = center.add(direction.scale(0.16D));
        Vec3 end = center.add(direction.scale(length));
        Vec3 axis = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (axis.lengthSqr() <= 1.0E-7D) {
            axis = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            axis = axis.normalize();
        }
        Vec3 w0 = axis.scale(halfWidth);
        Vec3 w1 = axis.scale(halfWidth * 0.10D);
        Vec3 a = start.subtract(w0).subtract(cameraPos);
        Vec3 b0 = start.add(w0).subtract(cameraPos);
        Vec3 c = end.add(w1).subtract(cameraPos);
        Vec3 d = end.subtract(w1).subtract(cameraPos);
        add(vertices, pose, a, 0.0F, 0.0F, r, g, b, alpha);
        add(vertices, pose, b0, 0.0F, 1.0F, r, g, b, alpha);
        add(vertices, pose, c, 1.0F, 1.0F, r, g, b, 0);
        add(vertices, pose, d, 1.0F, 0.0F, r, g, b, 0);
    }

    private static void drawQuad(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            Vec3 p0,
            Vec3 p1,
            Vec3 p2,
            Vec3 p3,
            int r,
            int g,
            int blue,
            int alpha,
            float farFade
    ) {
        Vec3 a = p0.subtract(cameraPos);
        Vec3 b = p1.subtract(cameraPos);
        Vec3 c = p2.subtract(cameraPos);
        Vec3 d = p3.subtract(cameraPos);
        int tailAlpha = Math.round(alpha * farFade);
        add(vertices, pose, a, 0, 0, r, g, blue, alpha);
        add(vertices, pose, b, 1, 0, r, g, blue, alpha);
        add(vertices, pose, c, 1, 1, r, g, blue, tailAlpha);
        add(vertices, pose, d, 0, 1, r, g, blue, tailAlpha);
    }

    private static void drawRibbon(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            Vec3 a,
            Vec3 b,
            Vec3 axis,
            float halfWidth,
            int r,
            int g,
            int blue,
            int alpha
    ) {
        if (alpha <= 0) {
            return;
        }
        Vec3 w = axis.normalize().scale(halfWidth);
        drawQuad(vertices, pose, cameraPos, a.subtract(w), a.add(w), b.add(w), b.subtract(w), r, g, blue, alpha, 0.0F);
    }

    private static void drawBillboardDiamond(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 cameraPos,
            Vec3 center,
            float radius,
            int red,
            int green,
            int blue,
            int alpha,
            float rotation
    ) {
        if (alpha <= 0) {
            return;
        }
        Vec3 view = center.subtract(cameraPos);
        Vec3 right = new Vec3(view.z, 0.0D, -view.x);
        if (right.lengthSqr() <= 1.0E-7D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = view.lengthSqr() <= 1.0E-7D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : right.cross(view.normalize()).normalize();
        double c = Math.cos(rotation);
        double s = Math.sin(rotation);
        Vec3 rx = right.scale(c).add(up.scale(s));
        Vec3 uy = up.scale(c).subtract(right.scale(s));
        Vec3 top = center.add(uy.scale(radius)).subtract(cameraPos);
        Vec3 rightP = center.add(rx.scale(radius)).subtract(cameraPos);
        Vec3 bottom = center.subtract(uy.scale(radius)).subtract(cameraPos);
        Vec3 leftP = center.subtract(rx.scale(radius)).subtract(cameraPos);
        add(vertices, pose, top, 0.5F, 0, red, green, blue, alpha);
        add(vertices, pose, rightP, 1, 0.5F, red, green, blue, alpha);
        add(vertices, pose, bottom, 0.5F, 1, red, green, blue, alpha);
        add(vertices, pose, leftP, 0, 0.5F, red, green, blue, alpha);
    }

    private static Vec3 normalized(Vec3 value) {
        return value.lengthSqr() <= 1.0E-7D ? new Vec3(0.0D, 0.0D, 1.0D) : value.normalize();
    }

    private static Vec3 horizontalSide(Vec3 direction) {
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        return side.lengthSqr() <= 1.0E-7D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
    }

    private static void add(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 position,
            float u,
            float v,
            int r,
            int g,
            int b,
            int a
    ) {
        vertices.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setUv(u, v)
                .setColor(r, g, b, Mth.clamp(a, 0, 255));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / Math.max(0.0001F, edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record BillboardBasis(Vec3 right, Vec3 up) {
    }

    private record KickVfx(int kind, Vec3 from, Vec3 to, long seed, long spawnTick,
                           boolean magicMirror) {
    }

    private record LaserVfx(int ownerEntityId, int sourceSegment, int targetEntityId,
                            Vec3 fallbackFrom, Vec3 fallbackTo,
                            float fullWidth, long seed, long spawnTick) {
    }

    private record LaserEndpoints(Vec3 from, Vec3 to) {
    }
}
