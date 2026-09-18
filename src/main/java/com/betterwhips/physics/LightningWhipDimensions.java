package com.betterwhips.physics;

public final class LightningWhipDimensions {
    public static final int SEGMENTS = 56;
    public static final double LENGTH_BLOCKS = 10.0;

    public static final float SOCKET_Y = 10.4f / 16.0f;
    private LightningWhipDimensions() {}

    public static float[] restLengths() {
        float[] lengths = new float[SEGMENTS];
        double used = 0.0;
        for (int i = 0; i < SEGMENTS - 1; i++) {
            lengths[i] = (float)(LENGTH_BLOCKS / SEGMENTS);
            used += lengths[i];
        }
        lengths[SEGMENTS - 1] = (float)(LENGTH_BLOCKS - used);
        return lengths;
    }

    public static double[] restLengthsDouble() {
        float[] source = restLengths();
        double[] lengths = new double[source.length];
        for (int i = 0; i < source.length; i++) lengths[i] = source[i];
        return lengths;
    }

    public static double[] colliderRadii() {
        double[] radii = new double[SEGMENTS];
        java.util.Arrays.fill(radii, 0.04);
        return radii;
    }
}
