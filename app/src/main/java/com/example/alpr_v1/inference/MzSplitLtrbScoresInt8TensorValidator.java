package com.example.alpr_v1.inference;

import java.util.Arrays;
import java.util.Map;

/**
 * T2-I-2: fail-closed research tensor contract for the existing MZ DAY
 * signed-INT8 dual-output TFLite, not yet connected to the production path.
 *
 * <p>IMPORTANT: TensorInfo.index is the LiteRT tensor PORT, not the flatbuffer
 * internal tensor IDs (646/634). Outputs must be addressed by map keys 0/1,
 * never by values().iterator().next().
 *
 * <p>Verifies runtime I/O descriptor metadata only. A valid descriptor does NOT
 * prove static whole-graph integer execution, model SHA, Android invoke,
 * FPS/latency or results versus ground truth.
 */
public final class MzSplitLtrbScoresInt8TensorValidator {
    public static final int INPUT_SIDE = 320;
    public static final int ANCHORS = 2100;
    public static final int CLASSES = 36; // keep Q=26 in model; only 35 legal PL plate classes
    private static final float INPUT_SCALE = 0.003921568859368563f;
    private static final int INPUT_ZP = -128;
    private static final float GEOMETRY_SCALE = 0.006233580876141787f;
    private static final int GEOMETRY_ZP = -126;
    private static final float SCORES_SCALE = 0.00390625f;
    private static final int SCORES_ZP = -128;
    private static final int[] INPUT_SHAPE = {1, INPUT_SIDE, INPUT_SIDE, 3};
    private static final int[] GEOMETRY_SHAPE = {1, 4, ANCHORS};
    private static final int[] SCORES_SHAPE = {1, CLASSES, ANCHORS};
    private static final int INPUT_BYTES = 1 * INPUT_SIDE * INPUT_SIDE * 3;
    private static final int GEOMETRY_BYTES = 4 * ANCHORS;
    private static final int SCORES_BYTES = CLASSES * ANCHORS;

    private MzSplitLtrbScoresInt8TensorValidator() {}

    /**
     * Require exact signed-INT8 input and exactly two output ports.
     * Port 0 = post-DFL LTRB distances [1,4,2100].
     * Port 1 = sigmoid class probabilities [1,36,2100].
     * No fallback, no graph tensor-index assumptions, no manifest changes.
     */
    public static void validate(TensorInfo input, Map<Integer, TensorInfo> outputs) {
        requireTensor("INPUT_PORT_0", input, 0, INPUT_SHAPE, INPUT_BYTES, INPUT_SCALE, INPUT_ZP);
        if (outputs == null || outputs.size() != 2 || !outputs.containsKey(0) || !outputs.containsKey(1)) {
            throw new IllegalArgumentException("MZ output ports must be exactly 0=LTRB and 1=SCORES");
        }
        requireTensor("OUTPUT_PORT_0_LTRB", outputs.get(0), 0, GEOMETRY_SHAPE,
                GEOMETRY_BYTES, GEOMETRY_SCALE, GEOMETRY_ZP);
        requireTensor("OUTPUT_PORT_1_SCORES", outputs.get(1), 1, SCORES_SHAPE,
                SCORES_BYTES, SCORES_SCALE, SCORES_ZP);
    }

    private static void requireTensor(String role, TensorInfo tensor, int requiredPort,
                                      int[] requiredShape, int requiredBytes,
                                      float requiredScale, int requiredZeroPoint) {
        if (tensor == null) {
            throw new IllegalArgumentException(role + " tensor missing");
        }
        if (tensor.index != requiredPort) {
            throw new IllegalArgumentException(role + " tensor port mismatch: " + tensor.index);
        }
        if (!"INT8".equals(tensor.dataType)) {
            throw new IllegalArgumentException(role + " requires signed INT8 storage, got " + tensor.dataType);
        }
        if (tensor.shape == null || !Arrays.equals(tensor.shape, requiredShape)) {
            throw new IllegalArgumentException(role + " shape mismatch: " + Arrays.toString(tensor.shape));
        }
        if (tensor.byteSize != requiredBytes) {
            throw new IllegalArgumentException(role + " byte count mismatch: " + tensor.byteSize);
        }
        float tolerance = Math.max(1e-8f, Math.abs(requiredScale) * 1e-5f);
        if (!Float.isFinite(tensor.quantizationScale) || tensor.quantizationScale <= 0f
                || Math.abs(tensor.quantizationScale - requiredScale) > tolerance
                || tensor.quantizationZeroPoint != requiredZeroPoint) {
            throw new IllegalArgumentException(role + " quantizer mismatch (scale or zero_point)");
        }
    }
}
