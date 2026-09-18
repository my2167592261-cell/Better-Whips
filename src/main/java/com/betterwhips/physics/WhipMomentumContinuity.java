package com.betterwhips.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

public final class WhipMomentumContinuity {
    private static final double MIN_LATERAL_SPEED = 1.50D;
    private static final double MIN_DIRECTION_COHERENCE = 0.55D;

    private WhipMomentumContinuity() {}

    public static Continuation analyze(Vec3[] points, Vec3[] previous, double substepSeconds,
                                       Vec3 attackDirection, HumanoidArm arm) {
        if (points == null || previous == null || points.length != previous.length
                || points.length < 5 || !Double.isFinite(substepSeconds)
                || substepSeconds <= 1.0E-6D || attackDirection == null || arm == null) {
            return Continuation.NONE;
        }

        Vec3 forward = new Vec3(attackDirection.x, 0.0D, attackDirection.z);
        if (forward.lengthSqr() < 1.0E-10D) {
            return Continuation.NONE;
        }
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        int last = points.length - 2;
        int first = Math.max(2, (int)Math.floor((points.length - 1) * 0.24D));
        if (first > last) {
            return Continuation.NONE;
        }

        Vec3 rootStep = points[0].subtract(previous[0]);
        double signed = 0.0D;
        double absolute = 0.0D;
        double weightSum = 0.0D;
        for (int i = first; i <= last; ++i) {
            Vec3 point = points[i];
            Vec3 old = previous[i];
            if (!finite(point) || !finite(old)) {
                return Continuation.NONE;
            }
            double fraction = i / (double)(points.length - 1);
            double t = Mth.clamp((fraction - 0.24D) / 0.70D, 0.0D, 1.0D);

            double weight = 0.35D + 0.65D * (t * t * (3.0D - 2.0D * t));

            double lateral = point.subtract(old).subtract(rootStep).dot(right) / substepSeconds;
            signed += lateral * weight;
            absolute += Math.abs(lateral) * weight;
            weightSum += weight;
        }
        if (weightSum <= 1.0E-9D || absolute <= 1.0E-9D) {
            return Continuation.NONE;
        }

        double lateralSpeed = signed / weightSum;
        double coherence = Math.abs(signed) / absolute;
        if (Math.abs(lateralSpeed) < MIN_LATERAL_SPEED || coherence < MIN_DIRECTION_COHERENCE) {
            return new Continuation(false, 1.0D, lateralSpeed, coherence);
        }

        double momentumSide = lateralSpeed >= 0.0D ? 1.0D : -1.0D;
        double armSide = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        double swingSign = -armSide * momentumSide;
        return new Continuation(true, swingSign, lateralSpeed, coherence);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public record Continuation(boolean active, double swingSign,
                               double lateralSpeed, double coherence) {
        public static final Continuation NONE = new Continuation(false, 1.0D, 0.0D, 0.0D);
    }
}
