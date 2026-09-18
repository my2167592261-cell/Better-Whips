package com.betterwhips.client;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

final class WhipCoilPose {
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);

    private WhipCoilPose() {}

    static void build(Vec3 root, Player player, HumanoidArm arm, Vec3 handleAxis,
                      float[] restLengths, double restScale, int requestedLeadSegments,
                      double requestedTurns, double layerPitch, Vec3[] output) {
        if (output.length != restLengths.length + 1) {
            throw new IllegalArgumentException("Coil output size must match whip point count");
        }

        Vec3 forward = bodyForward(player);
        Vec3 bodyRight = new Vec3(-forward.z, 0.0D, forward.x);
        double side = arm == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        Vec3 outward = bodyRight.scale(side);
        buildOriented(root, forward, outward, handleAxis, restLengths, restScale,
                requestedLeadSegments, requestedTurns, layerPitch, output);
    }

    static void buildStoredItem(Vec3 root, float[] restLengths, double restScale,
                                int requestedLeadSegments, double requestedTurns,
                                double layerPitch, Vec3[] output) {
        buildOriented(root, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D),
                new Vec3(0.0D, 1.0D, 0.0D), restLengths, restScale, requestedLeadSegments,
                requestedTurns, layerPitch, output);
    }

    private static void buildOriented(Vec3 root, Vec3 forward, Vec3 outward, Vec3 handleAxis,
                                      float[] restLengths, double restScale,
                                      int requestedLeadSegments, double requestedTurns,
                                      double layerPitch, Vec3[] output) {
        if (output.length != restLengths.length + 1) {
            throw new IllegalArgumentException("Coil output size must match whip point count");
        }
        output[0] = root;

        Vec3 incoming = finiteDirection(handleAxis) ? handleAxis.normalize() : forward;
        Vec3 loopTangent = forward;
        int leadSegments = Math.max(1, Math.min(requestedLeadSegments, restLengths.length - 1));

        Vec3 cursor = root;
        double remainingLength = 0.0D;
        double maximumRemainingSegment = 0.0D;
        for (int i = 0; i < restLengths.length; ++i) {
            double length = restLengths[i] * restScale;
            if (i < leadSegments) {
                double t = (i + 1.0D) / leadSegments;
                double blend = t * t * (3.0D - 2.0D * t);
                Vec3 direction = incoming.scale(1.0D - blend).add(loopTangent.scale(blend));
                if (direction.lengthSqr() < 1.0E-10D) {
                    direction = loopTangent;
                } else {
                    direction = direction.normalize();
                }
                cursor = cursor.add(direction.scale(length));
                output[i + 1] = cursor;
            } else {
                remainingLength += length;
                maximumRemainingSegment = Math.max(maximumRemainingSegment, length);
            }
        }

        if (remainingLength <= 1.0E-8D) {
            return;
        }

        double turns = Math.max(3.0D, requestedTurns);
        double verticalScale = 1.18D;
        double circumferenceScale = 1.09D;
        double radius = remainingLength
                / (Math.PI * 2.0D * turns * circumferenceScale);
        radius = Math.max(radius, maximumRemainingSegment * 0.46D);
        radius = Math.max(0.085D, radius);
        double verticalRadius = radius * verticalScale;
        double gravityDroop = radius * 0.34D;
        double k = layerPitch / (Math.PI * 2.0D);

        double theta = 0.0D;
        Vec3 previousPoint = cursor;
        for (int i = leadSegments; i < restLengths.length; ++i) {
            double length = restLengths[i] * restScale;
            theta = advanceTheta(previousPoint, cursor, forward, outward, radius,
                    verticalRadius, gravityDroop, k, theta, length);
            Vec3 point = sample(cursor, forward, outward, radius,
                    verticalRadius, gravityDroop, k, theta);
            output[i + 1] = point;
            previousPoint = point;
        }
    }

    private static double advanceTheta(Vec3 previousPoint, Vec3 top, Vec3 loopHorizontal, Vec3 layerAxis,
                                       double radius, double verticalRadius, double gravityDroop,
                                       double k, double theta, double chordLength) {
        double low = 0.0D;
        double high = Math.PI * 0.98D;
        double highDistance = sample(top, loopHorizontal, layerAxis, radius, verticalRadius,
                gravityDroop, k, theta + high).distanceTo(previousPoint);

        if (highDistance + 1.0E-7D < chordLength) {
            return theta + high;
        }

        for (int iteration = 0; iteration < 12; ++iteration) {
            double mid = (low + high) * 0.5D;
            double distance = sample(top, loopHorizontal, layerAxis, radius, verticalRadius,
                    gravityDroop, k, theta + mid).distanceTo(previousPoint);
            if (distance < chordLength) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return theta + (low + high) * 0.5D;
    }

    private static Vec3 sample(Vec3 top, Vec3 loopHorizontal, Vec3 layerAxis,
                               double radius, double verticalRadius, double gravityDroop,
                               double k, double theta) {
        double sin = Math.sin(theta);
        double cos = Math.cos(theta);
        double lowerHalfSag = gravityDroop * 0.5D * (1.0D - cos);
        double vertical = verticalRadius * (cos - 1.0D) - lowerHalfSag;
        return top
                .add(loopHorizontal.scale(sin * radius))
                .add(WORLD_UP.scale(vertical))
                .add(layerAxis.scale(k * theta));
    }

    private static Vec3 bodyForward(Player player) {
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        if (forward.lengthSqr() < 1.0E-8D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return forward.normalize();
    }

    private static boolean finiteDirection(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z) && value.lengthSqr() > 1.0E-10D;
    }
}
