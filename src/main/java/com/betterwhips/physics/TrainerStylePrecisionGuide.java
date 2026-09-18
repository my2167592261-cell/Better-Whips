package com.betterwhips.physics;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class TrainerStylePrecisionGuide {
    private static final double FOLLOW_TOTAL_DELAY_SECONDS = 0.24D;
    private static final double FOLLOW_POSITION_ACCEL = 640.0D;
    private static final double FOLLOW_VELOCITY_ACCEL = 28.0D;
    private static final double FOLLOW_RADIAL_ACCEL = 430.0D;
    private static final double FOLLOW_MAX_ACCEL = 4400.0D;
    private static final double FOLLOW_TIP_GAIN = 1.18D;
    private static final double CROSSHAIR_SOURCE_PROGRESS = 0.30D;
    private static final double CROSSHAIR_SWEEP_HALF_SPAN_PROGRESS = 0.34D;
    private static final double CROSSHAIR_SWEEP_HALF_ANGLE_RADIANS = Math.toRadians(68.0D);
    private static final double ATTACK_SECONDS = 0.50D;
    private static final double CROSSHAIR_GATE_WIDTH_PROGRESS = 0.095D;
    private static final double CROSSHAIR_GATE_ACCEL = 5200.0D;

    private TrainerStylePrecisionGuide() {}

    public static final class State {
        private final Vec3[] directions;
        private final Vec3[] directionDelta;
        private boolean initialized;
        private double lastRawProgress = Double.NaN;
        private long lastGameTime = Long.MIN_VALUE;

        public State(int pointCount) {
            if (pointCount < 2) {
                throw new IllegalArgumentException("pointCount must be >= 2");
            }
            directions = new Vec3[pointCount];
            directionDelta = new Vec3[pointCount];
            for (int i = 0; i < pointCount; ++i) {
                directions[i] = Vec3.ZERO;
                directionDelta[i] = Vec3.ZERO;
            }
        }

        public void reset() {
            initialized = false;
            lastRawProgress = Double.NaN;
            lastGameTime = Long.MIN_VALUE;
        }
    }

    public static void apply(State state, Player player, Vec3[] points, Vec3[] previous,
                             Vec3 attackDirection, double rawProgress, double swingSign,
                             boolean momentumCarry, double substepSeconds,
                             double substepSecondsSqr, double[] restLengths,
                             double physicalRestScale) {
        applyInternal(state, player, points, previous, attackDirection, rawProgress, swingSign,
                momentumCarry, substepSeconds, substepSecondsSqr, restLengths, null,
                physicalRestScale);
    }

    public static void apply(State state, Player player, Vec3[] points, Vec3[] previous,
                             Vec3 attackDirection, double rawProgress, double swingSign,
                             boolean momentumCarry, double substepSeconds,
                             double substepSecondsSqr, float[] restLengths,
                             double physicalRestScale) {
        applyInternal(state, player, points, previous, attackDirection, rawProgress, swingSign,
                momentumCarry, substepSeconds, substepSecondsSqr, null, restLengths,
                physicalRestScale);
    }

    private static void applyInternal(State state, Player player, Vec3[] points, Vec3[] previous,
                                      Vec3 attackDirection, double rawProgress, double swingSign,
                                      boolean momentumCarry, double substepSeconds,
                                      double substepSecondsSqr, double[] restLengthsD,
                                      float[] restLengthsF, double physicalRestScale) {
        int restLengthCount = restLengthsD != null
                ? restLengthsD.length
                : (restLengthsF != null ? restLengthsF.length : 0);
        if (state == null || player == null || points == null || previous == null
                || restLengthCount < points.length - 1 || points.length < 2
                || previous.length != points.length) {
            return;
        }

        long gameTime = player.level().getGameTime();
        if (!Double.isFinite(state.lastRawProgress)
                || rawProgress + 1.0E-4D < state.lastRawProgress
                || state.lastGameTime == Long.MIN_VALUE
                || gameTime - state.lastGameTime > 2L) {
            state.initialized = false;
        }
        state.lastRawProgress = rawProgress;
        state.lastGameTime = gameTime;

        Vec3 aim = attackDirection != null && attackDirection.lengthSqr() > 1.0E-10D
                ? attackDirection.normalize() : player.getViewVector(1.0F).normalize();
        Vec3 center = player.getEyePosition();
        Vec3 sourceDirection = crosshairSweepDirection(aim, rawProgress, swingSign);

        if (!state.initialized) {
            initialize(state, center, sourceDirection, points);
        }

        double dt = Math.max(1.0E-5D, substepSeconds);
        double perLinkTau = FOLLOW_TOTAL_DELAY_SECONDS / Math.max(1.0D, points.length - 1.0D);
        double blend = 1.0D - Math.exp(-dt / perLinkTau);
        blend = Mth.clamp(blend, 0.0D, 1.0D);

        for (int i = points.length - 1; i >= 1; --i) {
            Vec3 oldDirection = state.directions[i];
            Vec3 leaderDirection = state.directions[i - 1];
            Vec3 mixed = oldDirection.lerp(leaderDirection, blend);
            if (mixed.lengthSqr() < 1.0E-10D) {
                mixed = leaderDirection;
            }
            Vec3 nextDirection = mixed.normalize();
            state.directionDelta[i] = nextDirection.subtract(oldDirection);
            state.directions[i] = nextDirection;
        }
        state.directionDelta[0] = sourceDirection.subtract(state.directions[0]);
        state.directions[0] = sourceDirection;

        double rootRadius = Mth.clamp(points[0].distanceTo(center), 0.45D, 1.15D);
        double accumulatedLength = 0.0D;
        double carryScale = momentumCarry
                ? Mth.lerp(smoothstep(Mth.clamp(rawProgress / 0.18D, 0.0D, 1.0D)), 0.72D, 1.0D)
                : 1.0D;

        Vec3 rootTarget = center.add(sourceDirection.scale(rootRadius));
        Vec3 rootAcceleration = rootTarget.subtract(points[0])
                .scale(FOLLOW_POSITION_ACCEL * 0.42D);
        double rootAccelLength = rootAcceleration.length();
        if (rootAccelLength > FOLLOW_MAX_ACCEL * 0.55D) {
            rootAcceleration = rootAcceleration.scale(
                    (FOLLOW_MAX_ACCEL * 0.55D) / rootAccelLength);
        }
        points[0] = points[0].add(rootAcceleration.scale(substepSecondsSqr));

        for (int i = 1; i < points.length; ++i) {
            accumulatedLength += (restLengthsD != null ? restLengthsD[i - 1] : restLengthsF[i - 1]) * physicalRestScale;
            double taper = i / (double)(points.length - 1);
            double targetRadius = rootRadius + accumulatedLength;
            double gate = crosshairGate(rawProgress, taper);

            Vec3 followedDirection = state.directions[i];
            Vec3 desiredDirection = followedDirection.lerp(aim, gate);
            if (desiredDirection.lengthSqr() < 1.0E-10D) {
                desiredDirection = aim;
            } else {
                desiredDirection = desiredDirection.normalize();
            }

            Vec3 targetPoint = center.add(desiredDirection.scale(targetRadius));
            Vec3 pathError = targetPoint.subtract(points[i]);
            Vec3 relative = points[i].subtract(center);
            double radialError = targetRadius - relative.length();
            Vec3 desiredAngularVelocity = state.directionDelta[i].scale(targetRadius / dt);
            Vec3 currentVelocity = points[i].subtract(previous[i]).scale(1.0D / dt);
            Vec3 velocityError = desiredAngularVelocity.subtract(currentVelocity);

            Vec3 fromEye = points[i].subtract(center);
            double rayDistance = Math.max(0.35D, fromEye.dot(aim));
            Vec3 crosshairPoint = center.add(aim.scale(rayDistance));
            Vec3 crosshairError = crosshairPoint.subtract(points[i]);

            double tipGain = Mth.lerp(taper, 1.0D, FOLLOW_TIP_GAIN);
            Vec3 acceleration = pathError.scale(FOLLOW_POSITION_ACCEL)
                    .add(desiredDirection.scale(radialError * FOLLOW_RADIAL_ACCEL))
                    .add(velocityError.scale(FOLLOW_VELOCITY_ACCEL))
                    .add(crosshairError.scale(CROSSHAIR_GATE_ACCEL * gate))
                    .scale(carryScale * tipGain);
            double accelLength = acceleration.length();
            if (accelLength > FOLLOW_MAX_ACCEL) {
                acceleration = acceleration.scale(FOLLOW_MAX_ACCEL / accelLength);
            }
            points[i] = points[i].add(acceleration.scale(substepSecondsSqr));
        }

    }

    private static void initialize(State state, Vec3 center, Vec3 fallbackDirection, Vec3[] points) {
        Vec3 last = fallbackDirection;
        for (int i = 0; i < points.length; ++i) {
            Vec3 relative = points[i].subtract(center);
            Vec3 direction = relative.lengthSqr() > 1.0E-10D ? relative.normalize() : last;
            state.directions[i] = direction;
            state.directionDelta[i] = Vec3.ZERO;
            last = direction;
        }
        state.initialized = true;
    }

    private static Vec3 crosshairSweepDirection(Vec3 aim, double rawProgress, double swingSign) {
        Vec3 referenceUp = Math.abs(aim.y) < 0.94D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 side = referenceUp.cross(aim);
        if (side.lengthSqr() < 1.0E-10D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        double phase = Mth.clamp(
                (rawProgress - CROSSHAIR_SOURCE_PROGRESS)
                        / CROSSHAIR_SWEEP_HALF_SPAN_PROGRESS,
                -1.0D, 1.0D);
        double signedAngle = phase * CROSSHAIR_SWEEP_HALF_ANGLE_RADIANS
                * (swingSign >= 0.0D ? 1.0D : -1.0D);
        Vec3 direction = aim.scale(Math.cos(signedAngle)).add(side.scale(Math.sin(signedAngle)));
        return direction.lengthSqr() > 1.0E-10D ? direction.normalize() : aim;
    }

    private static double crosshairGate(double rawProgress, double chainTaper) {
        double delayProgress = (FOLLOW_TOTAL_DELAY_SECONDS / ATTACK_SECONDS)
                * Mth.clamp(chainTaper, 0.0D, 1.0D);
        double crossingProgress = CROSSHAIR_SOURCE_PROGRESS + delayProgress;
        double distance = (rawProgress - crossingProgress) / CROSSHAIR_GATE_WIDTH_PROGRESS;
        return Math.exp(-distance * distance);
    }

    private static double smoothstep(double x) {
        double c = Mth.clamp(x, 0.0D, 1.0D);
        return c * c * (3.0D - 2.0D * c);
    }
}
