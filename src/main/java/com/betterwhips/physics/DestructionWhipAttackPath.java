package com.betterwhips.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DestructionWhipAttackPath {
    private DestructionWhipAttackPath() {}

    public static double attackTime(double rawProgress, double releaseRaw) {
        double raw = Mth.clamp(rawProgress, 0.0D, 1.0D);
        if (raw <= releaseRaw) {
            return 0.5D * raw / Math.max(1.0E-6D, releaseRaw);
        }
        return 0.5D + 0.5D * (raw - releaseRaw)
                / Math.max(1.0E-6D, 1.0D - releaseRaw);
    }

    public static double outwardProgress(double rawProgress, double releaseRaw) {
        double t = attackTime(rawProgress, releaseRaw);
        return t <= 0.5D ? t * 2.0D : (1.0D - t) * 2.0D;
    }

    public static Vec3 crosshairEndpoint(Vec3 eye, Vec3 root, Vec3 viewDirection, double reach) {
        Vec3 view = safeDirection(viewDirection, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 eyeFromRoot = eye.subtract(root);
        double along = eyeFromRoot.dot(view);
        double discriminant = along * along
                - (eyeFromRoot.lengthSqr() - reach * reach);
        if (discriminant >= 0.0D && Double.isFinite(discriminant)) {
            double rayDistance = -along + Math.sqrt(discriminant);
            if (rayDistance > 0.0D && Double.isFinite(rayDistance)) {
                return eye.add(view.scale(rayDistance));
            }
        }
        return root.add(view.scale(reach));
    }

    public static double targetProgress(Vec3 root, Vec3 targetPoint, Vec3 finalPoint) {
        double reach = Math.max(1.0E-6D, root.distanceTo(finalPoint));
        double targetDistance = root.distanceTo(targetPoint);
        return Mth.clamp(targetDistance / reach, 0.18D, 0.965D);
    }

    public static Vec3 point(Vec3 root, Vec3 targetPoint, Vec3 finalPoint,
                             boolean hasTarget, double outwardProgress, long motionSeed) {
        double u = Mth.clamp(outwardProgress, 0.0D, 1.0D);
        if (!finite(root) || !finite(finalPoint)) {
            return finite(finalPoint) ? finalPoint : root;
        }
        PathVariant variant = variant(root, targetPoint, finalPoint, motionSeed);
        if (!hasTarget || targetPoint == null || !finite(targetPoint)) {
            return cubic(root, variant.c1(), variant.c2(), finalPoint, u);
        }

        double hitU = targetProgress(root, targetPoint, finalPoint);
        if (u <= hitU) {
            double s = u / Math.max(1.0E-6D, hitU);
            return cubic(root, variant.c1(), variant.c2a(), targetPoint, s);
        }
        double s = (u - hitU) / Math.max(1.0E-6D, 1.0D - hitU);
        return cubic(targetPoint, variant.c1b(), variant.c2(), finalPoint, s);
    }

    private static Vec3 point(Vec3 root, Vec3 targetPoint, Vec3 finalPoint,
                              boolean hasTarget, double outwardProgress, long motionSeed,
                              Vec3 launchDirection) {
        double u = Mth.clamp(outwardProgress, 0.0D, 1.0D);
        if (!finite(root) || !finite(finalPoint)) {
            return finite(finalPoint) ? finalPoint : root;
        }
        PathVariant variant = variant(root, targetPoint, finalPoint, motionSeed, launchDirection);
        if (!hasTarget || targetPoint == null || !finite(targetPoint)) {
            return cubic(root, variant.c1(), variant.c2(), finalPoint, u);
        }
        double hitU = targetProgress(root, targetPoint, finalPoint);
        if (u <= hitU) {
            double s = u / Math.max(1.0E-6D, hitU);
            return cubic(root, variant.c1(), variant.c2a(), targetPoint, s);
        }
        double s = (u - hitU) / Math.max(1.0E-6D, 1.0D - hitU);
        return cubic(targetPoint, variant.c1b(), variant.c2(), finalPoint, s);
    }

    public static SampledPath sample(Vec3 root, Vec3 targetPoint, Vec3 finalPoint,
                                     boolean hasTarget, long motionSeed) {
        return sample(root, targetPoint, finalPoint, hasTarget, motionSeed, null);
    }

    public static SampledPath sample(Vec3 root, Vec3 targetPoint, Vec3 finalPoint,
                                     boolean hasTarget, long motionSeed, Vec3 launchDirection) {
        final int samples = 72;
        Vec3[] positions = new Vec3[samples + 1];
        double[] cumulative = new double[samples + 1];
        positions[0] = point(root, targetPoint, finalPoint, hasTarget,
                0.0D, motionSeed, launchDirection);
        cumulative[0] = 0.0D;
        for (int i = 1; i <= samples; ++i) {
            double u = i / (double)samples;
            positions[i] = point(root, targetPoint, finalPoint, hasTarget,
                    u, motionSeed, launchDirection);
            cumulative[i] = cumulative[i - 1] + positions[i - 1].distanceTo(positions[i]);
        }
        return new SampledPath(positions, cumulative);
    }

    public static SampledPath prependPolyline(Vec3[] prefix, SampledPath continuation) {
        if (prefix == null || prefix.length == 0) {
            return continuation;
        }
        int continuationCount = continuation == null || continuation.positions().length == 0
                ? 0 : continuation.positions().length - 1;
        Vec3[] positions = new Vec3[prefix.length + continuationCount];
        for (int i = 0; i < prefix.length; ++i) {
            positions[i] = prefix[i];
        }
        if (continuationCount > 0) {
            System.arraycopy(continuation.positions(), 1, positions, prefix.length, continuationCount);
        }
        double[] cumulative = new double[positions.length];
        for (int i = 1; i < positions.length; ++i) {
            cumulative[i] = cumulative[i - 1] + positions[i - 1].distanceTo(positions[i]);
        }
        return new SampledPath(positions, cumulative);
    }

    public record SampledPath(Vec3[] positions, double[] cumulative) {
        public double totalLength() {
            return cumulative.length == 0 ? 0.0D : cumulative[cumulative.length - 1];
        }

        public double lengthAtProgress(double progress) {
            if (positions.length <= 1) return 0.0D;
            double scaled = Mth.clamp(progress, 0.0D, 1.0D) * (positions.length - 1);
            int low = Math.min(positions.length - 2, Math.max(0, (int)Math.floor(scaled)));
            double local = scaled - low;
            return Mth.lerp(local, cumulative[low], cumulative[low + 1]);
        }

        public Vec3 pointAtLength(double distance) {
            if (positions.length == 0) return Vec3.ZERO;
            if (positions.length == 1 || distance <= 0.0D) return positions[0];
            double clamped = Mth.clamp(distance, 0.0D, totalLength());
            int lo = 0;
            int hi = cumulative.length - 1;
            while (lo + 1 < hi) {
                int mid = (lo + hi) >>> 1;
                if (cumulative[mid] < clamped) lo = mid; else hi = mid;
            }
            double span = cumulative[hi] - cumulative[lo];
            if (span <= 1.0E-9D) return positions[hi];
            double local = (clamped - cumulative[lo]) / span;
            return positions[lo].lerp(positions[hi], local);
        }

        public Vec3 tangentAtLength(double distance) {
            if (positions.length <= 1) return new Vec3(0.0D, 0.0D, 1.0D);
            double epsilon = Math.max(0.025D, totalLength() / 320.0D);
            Vec3 before = pointAtLength(distance - epsilon);
            Vec3 after = pointAtLength(distance + epsilon);
            return safeDirection(after.subtract(before),
                    positions[positions.length - 1].subtract(positions[0]));
        }
    }

    public static Vec3 tangent(Vec3 root, Vec3 targetPoint, Vec3 finalPoint,
                               boolean hasTarget, double outwardProgress, long motionSeed) {
        double u = Mth.clamp(outwardProgress, 0.0D, 1.0D);
        double epsilon = 1.0E-3D;
        Vec3 before = point(root, targetPoint, finalPoint, hasTarget,
                Math.max(0.0D, u - epsilon), motionSeed);
        Vec3 after = point(root, targetPoint, finalPoint, hasTarget,
                Math.min(1.0D, u + epsilon), motionSeed);
        return safeDirection(after.subtract(before), finalPoint.subtract(root));
    }

    public static Vec3 bodyPoint(Vec3 root, Vec3 tip, Vec3 tipTangent,
                                 double taper, double outwardProgress, long motionSeed) {
        double t = Mth.clamp(taper, 0.0D, 1.0D);
        if (t <= 0.0D || !finite(root) || !finite(tip)) {
            return root;
        }
        if (t >= 1.0D) {
            return tip;
        }
        Vec3 axis = safeDirection(tip.subtract(root), tipTangent);
        Basis basis = basis(axis, tipTangent);
        double distance = root.distanceTo(tip);
        double envelope = Math.sin(Math.PI * Mth.clamp(outwardProgress, 0.0D, 1.0D));
        double signedA = unitSigned(motionSeed, 21);
        double signedB = unitSigned(motionSeed, 33);
        Vec3 bendDir = safeDirection(basis.right().scale(signedA).add(basis.up().scale(signedB)),
                basis.right());
        double curve = distance * (0.14D + 0.14D * unit01(motionSeed, 7)) * envelope;
        double c1Len = distance * (0.22D + 0.07D * unit01(motionSeed, 3));
        double c2Len = distance * (0.20D + 0.10D * unit01(motionSeed, 11));
        Vec3 c1 = root.add(axis.scale(c1Len)).add(bendDir.scale(curve));
        Vec3 backDir = safeDirection(tipTangent.scale(-1.0D), axis.scale(-1.0D));
        Vec3 c2 = tip.add(backDir.scale(c2Len)).add(bendDir.scale(curve * 0.48D));
        return cubic(root, c1, c2, tip, t);
    }

    public static Vec3 bodyTangent(Vec3 root, Vec3 tip, Vec3 tipTangent,
                                   double taper, double outwardProgress, long motionSeed) {
        double t = Mth.clamp(taper, 0.0D, 1.0D);
        double epsilon = 1.0E-3D;
        Vec3 before = bodyPoint(root, tip, tipTangent, Math.max(0.0D, t - epsilon),
                outwardProgress, motionSeed);
        Vec3 after = bodyPoint(root, tip, tipTangent, Math.min(1.0D, t + epsilon),
                outwardProgress, motionSeed);
        return safeDirection(after.subtract(before), tip.subtract(root));
    }

    private static PathVariant variant(Vec3 root, Vec3 targetPoint, Vec3 finalPoint, long motionSeed) {
        return variant(root, targetPoint, finalPoint, motionSeed, null);
    }

    private static PathVariant variant(Vec3 root, Vec3 targetPoint, Vec3 finalPoint,
                                       long motionSeed, Vec3 launchDirection) {
        Vec3 mainAxis = safeDirection(finalPoint.subtract(root),
                targetPoint != null ? targetPoint.subtract(root) : new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 tangentRef = targetPoint != null ? targetPoint.subtract(root) : finalPoint.subtract(root);
        Basis basis = basis(mainAxis, tangentRef);
        double reach = Math.max(1.0E-6D, root.distanceTo(finalPoint));
        double signedA = unitSigned(motionSeed, 2);
        double signedB = unitSigned(motionSeed, 5);
        Vec3 bendDir = safeDirection(basis.right().scale(signedA).add(basis.up().scale(signedB)),
                basis.right());
        double primaryCurve = reach * (0.040D + 0.060D * unit01(motionSeed, 8));
        double lead = Math.min(reach * (0.26D + 0.07D * unit01(motionSeed, 17)), 3.6D);
        double recovery = Math.min(reach * (0.18D + 0.08D * unit01(motionSeed, 19)), 2.8D);

        Vec3 launchAxis = safeDirection(launchDirection, mainAxis);
        if (launchAxis.dot(mainAxis) < 0.15D) {
            Vec3 blendedLaunch = launchAxis.scale(0.45D).add(mainAxis.scale(0.55D));
            launchAxis = safeDirection(blendedLaunch, mainAxis);
        }

        Vec3 c1 = root.add(launchAxis.scale(lead)).add(bendDir.scale(primaryCurve * 0.30D));

        Vec3 c2 = finalPoint.subtract(mainAxis.scale(recovery));

        if (targetPoint == null || !finite(targetPoint)) {
            return new PathVariant(c1, c2, c1, c2);
        }

        Vec3 toTarget = safeDirection(targetPoint.subtract(root), mainAxis);
        double targetDistance = root.distanceTo(targetPoint);
        double remaining = targetPoint.distanceTo(finalPoint);

        Vec3 stabAxis = safeDirection(finalPoint.subtract(root), toTarget);
        Vec3 c2a = targetPoint.subtract(stabAxis.scale(Math.min(targetDistance * 0.24D, 2.1D)));
        Vec3 c1b = targetPoint.add(stabAxis.scale(Math.min(remaining * 0.20D, 1.9D)));
        return new PathVariant(c1, c2, c2a, c1b);
    }

    private static Vec3 quadratic(Vec3 a, Vec3 control, Vec3 b, double progress) {
        double t = Mth.clamp(progress, 0.0D, 1.0D);
        double one = 1.0D - t;
        return a.scale(one * one)
                .add(control.scale(2.0D * one * t))
                .add(b.scale(t * t));
    }

    private static Vec3 cubic(Vec3 a, Vec3 c1, Vec3 c2, Vec3 b, double progress) {
        double t = Mth.clamp(progress, 0.0D, 1.0D);
        double one = 1.0D - t;
        return a.scale(one * one * one)
                .add(c1.scale(3.0D * one * one * t))
                .add(c2.scale(3.0D * one * t * t))
                .add(b.scale(t * t * t));
    }

    private static Basis basis(Vec3 axis, Vec3 tangentReference) {
        Vec3 forward = safeDirection(axis, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 reference = finite(tangentReference) ? tangentReference : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(reference);
        if (right.lengthSqr() < 1.0E-10D) {
            right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        }
        if (right.lengthSqr() < 1.0E-10D) {
            right = forward.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        right = safeDirection(right, new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 up = safeDirection(right.cross(forward), new Vec3(0.0D, 1.0D, 0.0D));
        return new Basis(right, up);
    }

    private static long mix(long seed, int salt) {
        long z = seed + 0x9E3779B97F4A7C15L * (salt + 1L);
        z ^= (z >>> 30);
        z *= 0xBF58476D1CE4E5B9L;
        z ^= (z >>> 27);
        z *= 0x94D049BB133111EBL;
        z ^= (z >>> 31);
        return z;
    }

    private static double unit01(long seed, int salt) {
        long bits = mix(seed, salt) >>> 11;
        return bits * (1.0D / (1L << 53));
    }

    private static double unitSigned(long seed, int salt) {
        return unit01(seed, salt) * 2.0D - 1.0D;
    }

    private record Basis(Vec3 right, Vec3 up) {}

    private record PathVariant(Vec3 c1, Vec3 c2, Vec3 c2a, Vec3 c1b) {}

    private static Vec3 safeDirection(Vec3 value, Vec3 fallback) {
        if (value != null && finite(value) && value.lengthSqr() > 1.0E-12D) {
            return value.normalize();
        }
        if (fallback != null && finite(fallback) && fallback.lengthSqr() > 1.0E-12D) {
            return fallback.normalize();
        }
        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
