package com.example.alpr_v1.pipeline;

import org.json.JSONException;
import org.json.JSONObject;

/** Czasy przypisane do konkretnego cropu, a nie tylko do całej klatki. */
public final class CropInferenceTiming {
    public final long frameId;
    public final long cameraConversionNanos;
    public final long vehicleStagesNanos;
    public final long plateStagesNanos;
    public final long rectificationNanos;
    public final long characterPreprocessNanos;
    public final long characterInferenceNanos;
    public final long characterPostprocessNanos;
    public final long pipelineToObservationNanos;

    public CropInferenceTiming(
            long frameId,
            long cameraConversionNanos,
            long vehicleStagesNanos,
            long plateStagesNanos,
            long rectificationNanos,
            long characterPreprocessNanos,
            long characterInferenceNanos,
            long characterPostprocessNanos,
            long pipelineToObservationNanos
    ) {
        this.frameId = frameId;
        this.cameraConversionNanos = nonNegative(cameraConversionNanos);
        this.vehicleStagesNanos = nonNegative(vehicleStagesNanos);
        this.plateStagesNanos = nonNegative(plateStagesNanos);
        this.rectificationNanos = nonNegative(rectificationNanos);
        this.characterPreprocessNanos = nonNegative(characterPreprocessNanos);
        this.characterInferenceNanos = nonNegative(characterInferenceNanos);
        this.characterPostprocessNanos = nonNegative(characterPostprocessNanos);
        this.pipelineToObservationNanos = nonNegative(pipelineToObservationNanos);
    }

    public double totalMilliseconds() { return pipelineToObservationNanos / 1_000_000.0; }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("frame_id", frameId);
        json.put("camera_conversion_ms", millis(cameraConversionNanos));
        json.put("vehicle_stages_ms", millis(vehicleStagesNanos));
        json.put("plate_stages_ms", millis(plateStagesNanos));
        json.put("rectification_ms", millis(rectificationNanos));
        json.put("character_preprocess_ms", millis(characterPreprocessNanos));
        json.put("character_inference_ms", millis(characterInferenceNanos));
        json.put("character_postprocess_ms", millis(characterPostprocessNanos));
        json.put("pipeline_to_observation_ms", millis(pipelineToObservationNanos));
        return json;
    }

    private static long nonNegative(long value) { return Math.max(0L, value); }
    private static double millis(long value) { return value / 1_000_000.0; }
}
