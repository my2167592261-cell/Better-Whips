package com.betterwhips.physics;

public final class WindwhispererWhipDimensions {
    public static final int SEGMENT_COUNT = 50;
    public static final int POINT_COUNT = SEGMENT_COUNT + 1;
    public static final double MODEL_UNIT = 1.0D / 16.0D;

    public static final double DEPLOYED_REST_SCALE = 1.00D;
    public static final double STORED_REST_SCALE = 1.00D;

    private static final double[] AUTHORED_PIVOT_Z = buildPivots();
    private static final double[] REST_LENGTHS = buildRestLengths();

    private WindwhispererWhipDimensions() {}

    public static double pivotZ(int index) { return AUTHORED_PIVOT_Z[index]; }
    public static double restLength(int segmentIndex) { return REST_LENGTHS[segmentIndex]; }
    public static double[] restLengthsCopy() { return REST_LENGTHS.clone(); }

    public static float[] authoredPivotZFloatCopy() {
        float[] result = new float[AUTHORED_PIVOT_Z.length];
        for (int i = 0; i < result.length; ++i) result[i] = (float)AUTHORED_PIVOT_Z[i];
        return result;
    }

    public static double deployedLength() {
        double total = 0.0D;
        for (double rest : REST_LENGTHS) total += rest * DEPLOYED_REST_SCALE;
        return total;
    }

    private static double[] buildPivots() {
        double[] result = new double[POINT_COUNT];
        for (int i = 0; i < result.length; ++i) result[i] = -2.8D * i;
        return result;
    }

    private static double[] buildRestLengths() {
        double[] result = new double[SEGMENT_COUNT];
        for (int i = 0; i < SEGMENT_COUNT; ++i) {
            result[i] = Math.abs(AUTHORED_PIVOT_Z[i + 1] - AUTHORED_PIVOT_Z[i]) * MODEL_UNIT;
        }
        return result;
    }
}
