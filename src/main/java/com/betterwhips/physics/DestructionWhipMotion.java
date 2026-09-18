package com.betterwhips.physics;

import net.minecraft.util.Mth;

public final class DestructionWhipMotion {

    public static final int ATTACK_TICKS = 10;

    public static final int PRECISION_WINDUP_TICKS = 5;
    public static final int PRECISION_STROKE_TICKS = 5;
    public static final double PRECISION_RELEASE_RAW = 0.50D;

    private static final double MIN_SWEEP_ANGLE = Math.toRadians(5.5D);
    private static final double MAX_SWEEP_ANGLE = Math.toRadians(9.5D);

    private DestructionWhipMotion() {}

    public static double attackTime(double rawProgress) {
        return Mth.clamp(rawProgress, 0.0D, 1.0D);
    }

    public static double radialExtension(double rawProgress, long seed) {
        double t = attackTime(rawProgress);
        double extendEnd = 0.27D + 0.04D * randomUnit(seed, 11L);
        double retractStart = 0.69D + 0.04D * randomUnit(seed, 12L);
        double plateauDip = 0.018D + 0.032D * randomUnit(seed, 13L);

        if (t < extendEnd) {
            return smoothstep(t / Math.max(1.0E-6D, extendEnd));
        }
        if (t <= retractStart) {

            double halfSpan = Math.max(0.12D, Math.max(0.5D - extendEnd, retractStart - 0.5D));
            double centerDistance = Math.min(1.0D, Math.abs(t - 0.5D) / halfSpan);
            return Mth.clamp(1.0D - plateauDip * centerDistance * centerDistance, 0.0D, 1.0D);
        }

        double startHalfSpan = Math.max(0.12D, Math.max(0.5D - extendEnd, retractStart - 0.5D));
        double startCenterDistance = Math.min(1.0D, Math.abs(retractStart - 0.5D) / startHalfSpan);
        double startExtension = 1.0D - plateauDip * startCenterDistance * startCenterDistance;
        double retract = (t - retractStart) / Math.max(1.0E-6D, 1.0D - retractStart);
        return Mth.clamp(startExtension * (1.0D - smoothstep(retract)), 0.0D, 1.0D);
    }

    public static double sweepAngle(double rawProgress, long seed) {
        double t = attackTime(rawProgress);
        double amplitude = Mth.lerp(randomUnit(seed, 21L), MIN_SWEEP_ANGLE, MAX_SWEEP_ANGLE);
        double sign = randomUnit(seed, 22L) < 0.5D ? -1.0D : 1.0D;
        return sign * amplitude * (1.0D - 2.0D * smoothstep(t));
    }

    public static double sweepPlaneAngle(long seed) {
        return randomUnit(seed, 23L) * Math.PI * 2.0D;
    }

    public static double[] tangentialAngles(double rawProgress, long seed) {
        double sweep = sweepAngle(rawProgress, seed);
        double plane = sweepPlaneAngle(seed);
        return new double[]{sweep * Math.cos(plane), sweep * Math.sin(plane)};
    }

    private static double smoothstep(double x) {
        double t = Mth.clamp(x, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static long mixSeed(long seed, long salt) {
        long z = seed + salt * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double randomUnit(long seed, long salt) {
        return (mixSeed(seed, salt) >>> 11) * 0x1.0p-53;
    }
}
