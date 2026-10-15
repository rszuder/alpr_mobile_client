package com.example.alpr_v1.vision;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Research-only YOLO26 MZ signed-INT8 dual-output decoder (F4/T2-I-1).
 *
 * <p>Consumes two separate output tensors: normalized post-DFL LTRB distances
 * [1,4,2100] and sigmoid class scores [1,36,2100], each channels-first.
 * It reconstructs the 40/20/10 P3/P4/P5 anchor grids and performs the two-stage
 * TopK selection (300 anchors, then 300 anchor/class pairs). The output is in
 * 320x320 input pixels, not coordinates of the original camera image.
 *
 * <p>THIS IS NOT wired to Android inference, manifest validation or packaging.
 * TopK equal-score ties follow deterministic NumPy-style stable order (first
 * stage: ascending anchor id; second stage: first-stage rank then class id).
 * torch.topk does not promise identical tie ordering. No Android runtime parity
 * or FP32 TFLite parity is claimed by these unit tests.
 */
public final class Yolo26SplitLtrbScoresInt8Decoder {
    public static final int MODEL_SIDE = 320;
    public static final int ANCHORS = 2100;
    public static final int CLASSES = 36;
    public static final int MAX_DETECTIONS = 300;
    public static final float GEOMETRY_SCALE = 0.006233580876141787f;
    public static final int GEOMETRY_ZERO_POINT = -126;
    public static final float SCORES_SCALE = 0.00390625f;
    public static final int SCORES_ZERO_POINT = -128;
    private static final int[] BOX_SHAPE = {1, 4, ANCHORS};
    private static final int[] SCORES_SHAPE = {1, CLASSES, ANCHORS};

    private Yolo26SplitLtrbScoresInt8Decoder() {}

    /**
     * @param geometry signed-INT8 output port 0; shape must be [1,4,2100].
     * @param scores signed-INT8 output port 1; shape must be [1,36,2100].
     * @param confidenceThreshold pass 0.25f for research reference or 0.10f
     *                            to preserve the legacy candidate-recall path.
     * @param clipCoordinates false for pre-NMS reference, true for ALPR usage.
     * @param classAwareNms false for reference; true for ALPR candidate policy.
     */
    public static List<Detection> decode(
            ByteBuffer geometry,
            int[] geometryShape,
            float geometryScale,
            int geometryZeroPoint,
            ByteBuffer scores,
            int[] scoresShape,
            float scoresScale,
            int scoresZeroPoint,
            float confidenceThreshold,
            boolean clipCoordinates,
            boolean classAwareNms,
            float nmsIouThreshold
    ) {
        requireShape(geometryShape, BOX_SHAPE, "LTRB");
        requireShape(scoresShape, SCORES_SHAPE, "SCORES");
        requireBuffer(geometry, 4 * ANCHORS, "LTRB");
        requireBuffer(scores, CLASSES * ANCHORS, "SCORES");
        requireQuantizer(geometryScale, geometryZeroPoint, GEOMETRY_SCALE, GEOMETRY_ZERO_POINT, "LTRB");
        requireQuantizer(scoresScale, scoresZeroPoint, SCORES_SCALE, SCORES_ZERO_POINT, "SCORES");
        if (!Float.isFinite(confidenceThreshold) || confidenceThreshold < 0f || confidenceThreshold > 1f) {
            throw new IllegalArgumentException("Invalid MZ confidence threshold");
        }
        if (!Float.isFinite(nmsIouThreshold) || nmsIouThreshold < 0f || nmsIouThreshold > 1f) {
            throw new IllegalArgumentException("Invalid MZ NMS IoU threshold");
        }
        ByteBuffer boxView = geometry.asReadOnlyBuffer();
        ByteBuffer scoreView = scores.asReadOnlyBuffer();
        int boxStart = boxView.position();
        int scoreStart = scoreView.position();

        // Stage 1: max score per anchor, then 300 anchors. Signed raw score
        // bytes can be compared directly because the quantizer is common.
        final int[] bestQuantized = new int[ANCHORS];
        Integer[] anchorOrder = new Integer[ANCHORS];
        for (int anchor = 0; anchor < ANCHORS; anchor++) {
            int best = Byte.MIN_VALUE;
            for (int cls = 0; cls < CLASSES; cls++) {
                int q = scoreView.get(scoreStart + cls * ANCHORS + anchor);
                if (q > best) best = q;
            }
            bestQuantized[anchor] = best;
            anchorOrder[anchor] = anchor;
        }
        Arrays.sort(anchorOrder, (left, right) -> {
            int byScore = Integer.compare(bestQuantized[right], bestQuantized[left]);
            return byScore != 0 ? byScore : Integer.compare(left, right);
        });

        // Stage 2: flatten [selected anchor rank, class], TopK of 300 pairs.
        List<Candidate> rankedPairs = new ArrayList<>(MAX_DETECTIONS * CLASSES);
        for (int rank = 0; rank < MAX_DETECTIONS; rank++) {
            int anchor = anchorOrder[rank];
            for (int cls = 0; cls < CLASSES; cls++) {
                int q = scoreView.get(scoreStart + cls * ANCHORS + anchor);
                rankedPairs.add(new Candidate(anchor, cls, rank, q));
            }
        }
        rankedPairs.sort(Comparator
                .comparingInt((Candidate item) -> item.quantizedScore).reversed()
                .thenComparingInt(item -> item.stage1Rank)
                .thenComparingInt(item -> item.classId));

        List<Detection> detections = new ArrayList<>();
        for (int index = 0; index < MAX_DETECTIONS; index++) {
            Candidate hit = rankedPairs.get(index);
            float confidence = (hit.quantizedScore - scoresZeroPoint) * scoresScale;
            if (confidence < confidenceThreshold) continue;

            int side;
            int local;
            if (hit.anchor < 1600) {
                side = 40;
                local = hit.anchor;
            } else if (hit.anchor < 2000) {
                side = 20;
                local = hit.anchor - 1600;
            } else {
                side = 10;
                local = hit.anchor - 2000;
            }
            float cx = ((local % side) + 0.5f) / side;
            float cy = ((local / side) + 0.5f) / side;
            float l = (boxView.get(boxStart + hit.anchor) - geometryZeroPoint) * geometryScale;
            float t = (boxView.get(boxStart + ANCHORS + hit.anchor) - geometryZeroPoint) * geometryScale;
            float r = (boxView.get(boxStart + 2 * ANCHORS + hit.anchor) - geometryZeroPoint) * geometryScale;
            float b = (boxView.get(boxStart + 3 * ANCHORS + hit.anchor) - geometryZeroPoint) * geometryScale;
            float x1 = cx - l, y1 = cy - t, x2 = cx + r, y2 = cy + b;
            if (clipCoordinates) {
                x1 = clamp01(x1); y1 = clamp01(y1);
                x2 = clamp01(x2); y2 = clamp01(y2);
            }
            detections.add(new Detection(
                    hit.classId, confidence,
                    x1 * MODEL_SIDE, y1 * MODEL_SIDE, x2 * MODEL_SIDE, y2 * MODEL_SIDE,
                    Collections.emptyList()
            ));
        }
        List<Detection> selected = classAwareNms
                ? NonMaxSuppression.apply(detections, nmsIouThreshold, true)
                : detections;
        return Collections.unmodifiableList(new ArrayList<>(selected));
    }

    private static void requireShape(int[] found, int[] expected, String role) {
        if (found == null || !Arrays.equals(found, expected)) {
            throw new IllegalArgumentException(role + " expected shape " + Arrays.toString(expected)
                    + ", got " + Arrays.toString(found));
        }
    }

    private static void requireBuffer(ByteBuffer buffer, int length, String role) {
        if (buffer == null || buffer.remaining() != length) {
            throw new IllegalArgumentException(role + " expected " + length + " signed INT8 bytes");
        }
    }

    private static void requireQuantizer(
            float scale, int zeroPoint, float expectedScale, int expectedZeroPoint, String role
    ) {
        float tolerance = Math.max(1e-8f, Math.abs(expectedScale) * 1e-5f);
        if (!Float.isFinite(scale) || scale <= 0f || Math.abs(scale - expectedScale) > tolerance
                || zeroPoint != expectedZeroPoint) {
            throw new IllegalArgumentException(role + " quantizer does not match frozen research artifact");
        }
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static final class Candidate {
        final int anchor;
        final int classId;
        final int stage1Rank;
        final int quantizedScore;

        Candidate(int anchor, int classId, int stage1Rank, int quantizedScore) {
            this.anchor = anchor;
            this.classId = classId;
            this.stage1Rank = stage1Rank;
            this.quantizedScore = quantizedScore;
        }
    }
}
