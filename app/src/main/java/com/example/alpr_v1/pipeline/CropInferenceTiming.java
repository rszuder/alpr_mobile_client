package com.example.alpr_v1.pipeline;

import org.json.JSONException;
import org.json.JSONObject;

/** Czasy przypisane do konkretnego cropu, a nie tylko do całej klatki. */
public final class CropInferenceTiming {
    public final long frameId;
    public final long cameraConversionNanos;
    public final long vehicleStagesNanos;
    public final long vehicleInferenceNanos;
    public final long platePreprocessNanos;
    public final long plateInferenceNanos;
    public final long platePostprocessNanos;
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
        this(
                frameId,
                cameraConversionNanos,
                vehicleStagesNanos,
                0L,
                plateStagesNanos,
                0L,
                rectificationNanos,
                characterPreprocessNanos,
                characterInferenceNanos,
                characterPostprocessNanos,
                pipelineToObservationNanos
        );
    }

    public CropInferenceTiming(
            long frameId,
            long cameraConversionNanos,
            long vehicleStagesNanos,
            long platePreprocessNanos,
            long plateInferenceNanos,
            long platePostprocessNanos,
            long rectificationNanos,
            long characterPreprocessNanos,
            long characterInferenceNanos,
            long characterPostprocessNanos,
            long pipelineToObservationNanos
    ) {
        this(frameId, cameraConversionNanos, vehicleStagesNanos, -1L,
                platePreprocessNanos, plateInferenceNanos, platePostprocessNanos,
                rectificationNanos, characterPreprocessNanos, characterInferenceNanos,
                characterPostprocessNanos, pipelineToObservationNanos);
    }

    public CropInferenceTiming(
            long frameId,
            long cameraConversionNanos,
            long vehicleStagesNanos,
            long vehicleInferenceNanos,
            long platePreprocessNanos,
            long plateInferenceNanos,
            long platePostprocessNanos,
            long rectificationNanos,
            long characterPreprocessNanos,
            long characterInferenceNanos,
            long characterPostprocessNanos,
            long pipelineToObservationNanos
    ) {
        this.frameId = frameId;
        this.cameraConversionNanos = nonNegative(cameraConversionNanos);
        this.vehicleStagesNanos = nonNegative(vehicleStagesNanos);
        this.vehicleInferenceNanos = vehicleInferenceNanos < 0L ? -1L : vehicleInferenceNanos;
        this.platePreprocessNanos = nonNegative(platePreprocessNanos);
        this.plateInferenceNanos = nonNegative(plateInferenceNanos);
        this.platePostprocessNanos = nonNegative(platePostprocessNanos);
        this.plateStagesNanos = this.platePreprocessNanos
                + this.plateInferenceNanos
                + this.platePostprocessNanos;
        this.rectificationNanos = nonNegative(rectificationNanos);
        this.characterPreprocessNanos = nonNegative(characterPreprocessNanos);
        this.characterInferenceNanos = nonNegative(characterInferenceNanos);
        this.characterPostprocessNanos = nonNegative(characterPostprocessNanos);
        this.pipelineToObservationNanos = nonNegative(pipelineToObservationNanos);
    }

    public double totalMilliseconds() { return pipelineToObservationNanos / 1_000_000.0; }
    public double vehicleInferenceMilliseconds() { return millis(vehicleInferenceNanos); }
    public double plateInferenceMilliseconds() { return millis(plateInferenceNanos); }
    public double characterInferenceMilliseconds() { return millis(characterInferenceNanos); }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("frame_id", frameId);
        json.put("camera_conversion_ms", millis(cameraConversionNanos));
        json.put("vehicle_stages_ms", millis(vehicleStagesNanos));
        if (vehicleInferenceNanos >= 0L) {
            json.put("vehicle_inference_ms", vehicleInferenceMilliseconds());
        }
        json.put("plate_preprocess_ms", millis(platePreprocessNanos));
        json.put("plate_inference_ms", millis(plateInferenceNanos));
        json.put("plate_postprocess_ms", millis(platePostprocessNanos));
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
