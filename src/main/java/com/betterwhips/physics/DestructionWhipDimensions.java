package com.betterwhips.physics;

public final class DestructionWhipDimensions {
    public static final int SEGMENT_COUNT = 24;
    public static final int POINT_COUNT = SEGMENT_COUNT + 1;
    public static final double MODEL_UNIT = 1.0D / 16.0D;

    public static final double DEPLOYED_REST_SCALE = 22.0D / 9.0D;
    public static final double STORED_REST_SCALE = 1.00D;

    private static final double[] AUTHORED_PIVOT_Z = {
            0.0D, -3.0D, -6.0D, -9.0D, -12.0D, -15.0D,
            -18.0D, -21.0D, -24.0D, -27.0D, -30.0D, -33.0D,
            -36.0D, -39.0D, -42.0D, -45.0D, -48.0D, -51.0D,
            -54.0D, -57.0D, -60.0D, -63.0D, -66.0D, -69.0D,
            -72.0D
    };

    private static final double[] REST_LENGTHS = buildRestLengths();

    private DestructionWhipDimensions() {}

    public static double pivotZ(int index) {
        return AUTHORED_PIVOT_Z[index];
    }

    public static double restLength(int segmentIndex) {
        return REST_LENGTHS[segmentIndex];
    }

    public static double[] restLengthsCopy() {
        return REST_LENGTHS.clone();
    }

    public static float[] authoredPivotZFloatCopy() {
        float[] result = new float[AUTHORED_PIVOT_Z.length];
        for (int i = 0; i < AUTHORED_PIVOT_Z.length; ++i) {
            result[i] = (float)AUTHORED_PIVOT_Z[i];
        }
        return result;
    }

    public static double deployedLength() {
        double total = 0.0D;
        for (double rest : REST_LENGTHS) {
            total += rest * DEPLOYED_REST_SCALE;
        }
        return total;
    }

    private static double[] buildRestLengths() {
        double[] result = new double[SEGMENT_COUNT];
        for (int i = 0; i < SEGMENT_COUNT; ++i) {
            result[i] = Math.abs(AUTHORED_PIVOT_Z[i + 1] - AUTHORED_PIVOT_Z[i]) * MODEL_UNIT;
        }
        return result;
    }
}
