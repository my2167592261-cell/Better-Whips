package com.betterwhips.physics;

public final class AmethystWhipDimensions {
    public static final int SEGMENT_COUNT = 24;
    public static final int POINT_COUNT = SEGMENT_COUNT + 1;
    public static final double MODEL_UNIT = 1.0D / 16.0D;
    public static final double DEPLOYED_REST_SCALE = 6.40D;
    public static final double STORED_REST_SCALE = 1.00D;

    private static final double[] AUTHORED_PIVOT_Z = {
            0.0000D, -1.0417D, -2.0833D, -3.1250D, -4.1667D, -5.2083D,
            -6.2500D, -7.2917D, -8.3333D, -9.3750D, -10.4167D, -11.4583D,
            -12.5000D, -13.5417D, -14.5833D, -15.6250D, -16.6667D, -17.7083D,
            -18.7500D, -19.7917D, -20.8333D, -21.8750D, -22.9167D, -23.9583D,
            -25.0000D
    };

    private static final double[] REST_LENGTHS = buildRestLengths();

    private AmethystWhipDimensions() {}

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
            result[i] = (float) AUTHORED_PIVOT_Z[i];
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
