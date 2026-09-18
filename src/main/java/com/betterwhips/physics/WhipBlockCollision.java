package com.betterwhips.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WhipBlockCollision {

    public static final double CONTACT_SKIN = 0.0125D;

    private static final double BROADPHASE_MARGIN = 0.22D;

    private static final long MAX_BROADPHASE_BLOCK_CELLS = 512L;
    private static final long MAX_BROADPHASE_AXIS_CELLS = 64L;

    private static final int MAX_TICK_CACHE_ENTRIES = 2048;
    private static final double[] MATERIAL_SAMPLES = {0.0D, 0.25D, 0.50D, 0.75D, 1.0D};

    private WhipBlockCollision() {}

    public static SegmentEnvironment[] buildBroadphase(Level level,
                                                       Vec3[] before,
                                                       Vec3[] predicted,
                                                       double[] radii,
                                                       double tipScale) {
        return buildBroadphase(level, before, predicted, radii, tipScale, null);
    }

    public static SegmentEnvironment[] buildBroadphase(Level level,
                                                       Vec3[] before,
                                                       Vec3[] predicted,
                                                       double[] radii,
                                                       double tipScale,
                                                       TickCache tickCache) {
        int segments = Math.min(radii.length, Math.min(before.length, predicted.length) - 1);
        SegmentEnvironment[] result = new SegmentEnvironment[segments];
        for (int i = 0; i < segments; ++i) {
            double radius = radii[i] * (i == segments - 1 ? Math.max(1.0D, tipScale) : 1.0D);
            AABB bounds = new AABB(before[i], before[i + 1])
                    .minmax(new AABB(predicted[i], predicted[i + 1]))
                    .inflate(radius + CONTACT_SKIN + BROADPHASE_MARGIN);
            result[i] = collect(level, bounds, tickCache);
        }
        return result;
    }

    private static SegmentEnvironment collect(Level level, AABB bounds, TickCache tickCache) {
        if (!finite(bounds.minX) || !finite(bounds.minY) || !finite(bounds.minZ)
                || !finite(bounds.maxX) || !finite(bounds.maxY) || !finite(bounds.maxZ)) {
            return SegmentEnvironment.EMPTY;
        }
        int minX = Mth.floor(bounds.minX);
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(bounds.minY));
        int minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.floor(bounds.maxX);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(bounds.maxY));
        int maxZ = Mth.floor(bounds.maxZ);
        if (maxY < minY) {
            return SegmentEnvironment.EMPTY;
        }

        long cellsX = (long)maxX - minX + 1L;
        long cellsY = (long)maxY - minY + 1L;
        long cellsZ = (long)maxZ - minZ + 1L;
        if (cellsX <= 0L || cellsY <= 0L || cellsZ <= 0L
                || cellsX > MAX_BROADPHASE_AXIS_CELLS
                || cellsY > MAX_BROADPHASE_AXIS_CELLS
                || cellsZ > MAX_BROADPHASE_AXIS_CELLS
                || cellsX > MAX_BROADPHASE_BLOCK_CELLS / cellsY
                || cellsX * cellsY > MAX_BROADPHASE_BLOCK_CELLS / cellsZ) {

            return SegmentEnvironment.EMPTY;
        }

        List<AABB> solids = null;
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            List<AABB> blockBoxes = tickCache != null
                    ? tickCache.collisionBoxes(level, pos)
                    : worldCollisionBoxes(level, pos);
            for (AABB world : blockBoxes) {
                if (!world.intersects(bounds)) {
                    continue;
                }
                if (solids == null) {
                    solids = new ArrayList<>();
                }
                solids.add(world);
            }
        }
        return solids == null
                ? SegmentEnvironment.EMPTY
                : new SegmentEnvironment(Collections.unmodifiableList(solids));
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static List<AABB> worldCollisionBoxes(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return List.of();
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return List.of();
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return List.of();
        }
        List<AABB> boxes = new ArrayList<>();
        for (AABB local : shape.toAabbs()) {
            boxes.add(local.move(pos.getX(), pos.getY(), pos.getZ()));
        }
        return boxes.isEmpty() ? List.of() : List.copyOf(boxes);
    }

    public static final class TickCache {
        private final Map<Long, List<AABB>> collisionBoxes = new HashMap<>();

        public List<AABB> collisionBoxes(Level level, BlockPos pos) {
            long key = pos.asLong();
            List<AABB> cached = collisionBoxes.get(key);
            if (cached != null) {
                return cached;
            }
            List<AABB> computed = WhipBlockCollision.worldCollisionBoxes(level, pos);
            if (collisionBoxes.size() < MAX_TICK_CACHE_ENTRIES) {
                collisionBoxes.put(key, computed);
            }
            return computed;
        }
    }

    public static SegmentContact findContact(SegmentEnvironment environment,
                                             Vec3 beforeStart, Vec3 beforeEnd,
                                             Vec3 afterStart, Vec3 afterEnd,
                                             double radius,
                                             double tangentRetention) {
        if (environment == null || environment.solids.isEmpty()) {
            return null;
        }

        Candidate best = null;
        double expandedBy = radius + CONTACT_SKIN;
        for (AABB solid : environment.solids) {
            AABB expanded = solid.inflate(expandedBy);
            for (double sample : MATERIAL_SAMPLES) {
                Vec3 from = beforeStart.lerp(beforeEnd, sample);
                Vec3 to = afterStart.lerp(afterEnd, sample);
                Entry entry = sweepPointAabb(from, to, expanded);
                if (entry == null) {
                    continue;
                }
                if (best == null || entry.toi < best.entry.toi
                        || (Math.abs(entry.toi - best.entry.toi) < 1.0E-8D
                        && entry.pushDistance > best.entry.pushDistance)) {
                    best = new Candidate(sample, from, to, solid, entry);
                }
            }
        }
        if (best == null) {
            return null;
        }

        Vec3 centerAtHit = best.from.lerp(best.to, best.entry.toi);
        if (best.entry.pushDistance > 0.0D) {
            centerAtHit = centerAtHit.add(best.entry.normal.scale(best.entry.pushDistance));
        }
        Vec3 remaining = best.to.subtract(centerAtHit);
        double inward = remaining.dot(best.entry.normal);
        if (inward < 0.0D) {
            remaining = remaining.subtract(best.entry.normal.scale(inward));
        }
        remaining = remaining.scale(tangentRetention);
        Vec3 desiredSample = centerAtHit
                .add(best.entry.normal.scale(CONTACT_SKIN))
                .add(remaining);
        Vec3 correction = desiredSample.subtract(best.to);

        Vec3 physicalSurface = centerAtHit.subtract(best.entry.normal.scale(radius));
        return new SegmentContact(best.sample, best.entry.toi, best.entry.normal,
                correction, physicalSurface);
    }

    private static Entry sweepPointAabb(Vec3 start, Vec3 end, AABB box) {
        Vec3 delta = end.subtract(start);
        if (box.contains(start)) {
            Exit exit = nearestExit(start, box);

            if (exit.distance <= CONTACT_SKIN * 1.5D
                    && delta.dot(exit.normal) >= -1.0E-9D) {
                return null;
            }
            return new Entry(0.0D, exit.normal, exit.distance);
        }

        double tEnter = 0.0D;
        double tExit = 1.0D;
        Vec3 enterNormal = Vec3.ZERO;

        AxisHit x = axis(start.x, delta.x, box.minX, box.maxX,
                new Vec3(-1.0D, 0.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D));
        if (x == null) return null;
        if (x.enter > tEnter) { tEnter = x.enter; enterNormal = x.enterNormal; }
        tExit = Math.min(tExit, x.exit);
        if (tEnter > tExit) return null;

        AxisHit y = axis(start.y, delta.y, box.minY, box.maxY,
                new Vec3(0.0D, -1.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D));
        if (y == null) return null;
        if (y.enter > tEnter) { tEnter = y.enter; enterNormal = y.enterNormal; }
        tExit = Math.min(tExit, y.exit);
        if (tEnter > tExit) return null;

        AxisHit z = axis(start.z, delta.z, box.minZ, box.maxZ,
                new Vec3(0.0D, 0.0D, -1.0D), new Vec3(0.0D, 0.0D, 1.0D));
        if (z == null) return null;
        if (z.enter > tEnter) { tEnter = z.enter; enterNormal = z.enterNormal; }
        tExit = Math.min(tExit, z.exit);
        if (tEnter > tExit || tEnter < 0.0D || tEnter > 1.0D) return null;

        if (enterNormal.lengthSqr() < 1.0E-12D) {
            Vec3 opposite = delta.lengthSqr() < 1.0E-12D
                    ? new Vec3(0.0D, 1.0D, 0.0D) : delta.normalize().scale(-1.0D);
            enterNormal = dominantAxis(opposite);
        }
        return new Entry(tEnter, enterNormal, 0.0D);
    }

    private static AxisHit axis(double start, double delta, double min, double max,
                                Vec3 minNormal, Vec3 maxNormal) {
        if (Math.abs(delta) < 1.0E-12D) {
            return start >= min && start <= max
                    ? new AxisHit(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Vec3.ZERO)
                    : null;
        }
        double a = (min - start) / delta;
        double b = (max - start) / delta;
        Vec3 enterNormal = minNormal;
        if (a > b) {
            double swap = a; a = b; b = swap;
            enterNormal = maxNormal;
        }
        return new AxisHit(a, b, enterNormal);
    }

    private static Exit nearestExit(Vec3 point, AABB box) {
        double dxMin = Math.abs(point.x - box.minX);
        double dxMax = Math.abs(box.maxX - point.x);
        double dyMin = Math.abs(point.y - box.minY);
        double dyMax = Math.abs(box.maxY - point.y);
        double dzMin = Math.abs(point.z - box.minZ);
        double dzMax = Math.abs(box.maxZ - point.z);

        double best = dxMin;
        Vec3 normal = new Vec3(-1.0D, 0.0D, 0.0D);
        if (dxMax < best) { best = dxMax; normal = new Vec3(1.0D, 0.0D, 0.0D); }
        if (dyMin < best) { best = dyMin; normal = new Vec3(0.0D, -1.0D, 0.0D); }
        if (dyMax < best) { best = dyMax; normal = new Vec3(0.0D, 1.0D, 0.0D); }
        if (dzMin < best) { best = dzMin; normal = new Vec3(0.0D, 0.0D, -1.0D); }
        if (dzMax < best) { best = dzMax; normal = new Vec3(0.0D, 0.0D, 1.0D); }
        return new Exit(normal, best);
    }

    private static Vec3 dominantAxis(Vec3 vector) {
        double ax = Math.abs(vector.x);
        double ay = Math.abs(vector.y);
        double az = Math.abs(vector.z);
        if (ax >= ay && ax >= az) {
            return new Vec3(Math.copySign(1.0D, vector.x), 0.0D, 0.0D);
        }
        if (ay >= az) {
            return new Vec3(0.0D, Math.copySign(1.0D, vector.y), 0.0D);
        }
        return new Vec3(0.0D, 0.0D, Math.copySign(1.0D, vector.z));
    }

    public static final class SegmentEnvironment {
        private static final SegmentEnvironment EMPTY = new SegmentEnvironment(List.of());
        private final List<AABB> solids;

        private SegmentEnvironment(List<AABB> solids) {
            this.solids = solids;
        }
    }

    public record SegmentContact(double sample,
                                 double toi,
                                 Vec3 normal,
                                 Vec3 correction,
                                 Vec3 surfacePoint) {}

    private record Candidate(double sample, Vec3 from, Vec3 to, AABB solid, Entry entry) {}
    private record Entry(double toi, Vec3 normal, double pushDistance) {}
    private record AxisHit(double enter, double exit, Vec3 enterNormal) {}
    private record Exit(Vec3 normal, double distance) {}
}
