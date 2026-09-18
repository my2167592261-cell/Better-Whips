package com.betterwhips.physics;

public final class TrainerWhipDimensions {
    public static final int SEGMENT_COUNT = 50;
    public static final int POINT_COUNT = SEGMENT_COUNT + 1;
    public static final double MODEL_UNIT = 1.0D / 16.0D;
    public static final double DEPLOYED_REST_SCALE = 1.0D;
    public static final double STORED_REST_SCALE = 1.0D;

    private static final double[] AUTHORED_PIVOT_Z = buildPivots();
    private static final double[] REST_LENGTHS = buildRestLengths();

    private TrainerWhipDimensions() {}

    private static double[] buildPivots() {
        double[] result = new double[SEGMENT_COUNT + 1];
        for (int i = 0; i <= SEGMENT_COUNT; ++i) {
            result[i] = -2.6D * i;
        }
        return result;
    }

    private static double[] buildRestLengths() {
        double[] result = new double[SEGMENT_COUNT];
        for (int i = 0; i < SEGMENT_COUNT; ++i) {
            result[i] = Math.abs(AUTHORED_PIVOT_Z[i + 1] - AUTHORED_PIVOT_Z[i]) * MODEL_UNIT;
        }
        return result;
    }

    public static double pivotZ(int index) { return AUTHORED_PIVOT_Z[index]; }
    public static double restLength(int index) { return REST_LENGTHS[index]; }
    public static double[] restLengthsCopy() { return REST_LENGTHS.clone(); }
    public static float[] authoredPivotZFloatCopy() {
        float[] out = new float[AUTHORED_PIVOT_Z.length];
        for (int i = 0; i < out.length; ++i) out[i] = (float)AUTHORED_PIVOT_Z[i];
        return out;
    }
    public static double deployedLength() {
        double total = 0.0D;
        for (double v : REST_LENGTHS) total += v;
        return total;
    }
}
