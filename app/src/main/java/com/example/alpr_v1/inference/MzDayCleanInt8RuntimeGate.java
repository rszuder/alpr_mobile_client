package com.example.alpr_v1.inference;

import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelRuntime;
import com.example.alpr_v1.model.ModelVariant;

import java.util.List;

/**
 * T2-I-9. Debug-only research gate for the one frozen MZ-DAY CLEAN signed INT8
 * model. It does NOT approve .alprmodel publication, release imports or F4.
 */
public final class MzDayCleanInt8RuntimeGate {
    public static final String MODEL_SHA256 =
            "a2f715399bdcb574da899e202388b5324d66f574b8519626cb611bf0e263e792";
    public static final int MODEL_BYTES = 10_255_456;
    private static final String OUTPUT_FORMAT = "raw_split_ltrb_scores_v1";
    private static final String DECODER = "ultralytics_detect_split_ltrb_scores_int8_v1";
    private static final float INPUT_QUANT_SCALE = 0.003921568859368563f;

    private MzDayCleanInt8RuntimeGate() {}

    public static void requirePinnedArtifact(
            String observedSha256, int byteCount, boolean debugBuild, boolean gpuRequested) {
        if (!debugBuild) throw new IllegalArgumentException("MZ_SPLIT_INT8_RELEASE_NOT_APPROVED");
        if (gpuRequested) throw new IllegalArgumentException("MZ_SPLIT_INT8_CPU_ONLY");
        if (byteCount != MODEL_BYTES || !MODEL_SHA256.equals(observedSha256)) {
            throw new IllegalArgumentException("MZ_SPLIT_INT8_FROZEN_ARTIFACT_SHA_MISMATCH");
        }
    }

    public static void requireClassOrder(List<String> labels) {
        if (labels == null || labels.size() != 36) {
            throw new IllegalArgumentException("MZ_SPLIT_INT8_LABEL_ORDER_MISMATCH");
        }
        for (int i = 0; i < 36; i++) {
            String expected = i < 10 ? Integer.toString(i)
                    : Character.toString((char) ('A' + i - 10));
            if (!expected.equals(labels.get(i))) {
                throw new IllegalArgumentException("MZ_SPLIT_INT8_LABEL_ORDER_MISMATCH");
            }
        }
    }

    public static void requireManifest(InstalledModel model, ModelVariant variant,
                                       ModelInputSpec input, ModelOutputSpec output) {
        if (model == null || variant == null || input == null || output == null
                || model.manifest().role() != ModelRole.CHARACTER
                || !"detect".equals(model.manifest().task())
                || variant.runtime() != ModelRuntime.TFLITE
                || !"int8".equals(variant.precision())
                || !MODEL_SHA256.equals(variant.sha256().get(variant.primaryFile()))
                || input.width() != 320 || input.height() != 320 || input.channels() != 3
                || !"NHWC".equals(input.layout()) || !"RGB".equals(input.colorSpace())
                || !"INT8".equals(input.dataType())
                || !Float.isFinite(input.scale()) || !Float.isFinite(input.offset())
                || Math.abs(input.scale() - (1f / 255f)) > 1e-6f || input.offset() != 0f
                || input.quantizationScale() == null || input.quantizationZeroPoint() == null
                || !Float.isFinite(input.quantizationScale())
                || Math.abs(input.quantizationScale() - INPUT_QUANT_SCALE) > 1e-8f
                || input.quantizationZeroPoint() != -128
                || !OUTPUT_FORMAT.equals(output.outputFormat()) || !DECODER.equals(output.decoder())
                || !"ltrb".equals(output.boxFormat()) || output.classCount() != 36
                || output.keypointCount() != 0 || output.keypointDimensions() != 0
                || output.hasObjectness() || !output.channelsFirst()
                || !output.normalizedCoordinates() || output.nmsInGraph() || output.nmsRequired()
                || !Float.isFinite(output.confidenceThreshold())
                || output.confidenceThreshold() < 0f || output.confidenceThreshold() > 1f
                || !Float.isFinite(output.iouThreshold()) || output.iouThreshold() < 0f
                || output.iouThreshold() > 1f) {
            throw new IllegalArgumentException("MZ_SPLIT_INT8_DEBUG_RESEARCH_MANIFEST_MISMATCH");
        }
        requireClassOrder(model.manifest().labels());
    }
}
