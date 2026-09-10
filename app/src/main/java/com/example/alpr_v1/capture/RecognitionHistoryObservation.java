package com.example.alpr_v1.capture;

import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.CropInferenceTiming;

/** Evidence metadata only: observations share the gallery entry's single representative bitmap. */
public final class RecognitionHistoryObservation {
    public final long sceneGeneration, visualEpoch, cameraTransformGeneration, frameId,
            entityId, vehicleTrackId, plateTrackId, capturedAtMillis, capturedElapsedNanos;
    public final String text, associationReason, captureSource;
    public final String rawPrediction, registrationKey;
    public final double confidence, plateConfidence;
    public final boolean confirmed;
    public final int sourceWidth, sourceHeight;
    public final CropInferenceTiming timing;
    public final ObservationTelemetry telemetry;

    public RecognitionHistoryObservation(PlateObservation observation, String captureSource,
            ObservationTelemetry telemetry) {
        sceneGeneration = observation.sceneGeneration; visualEpoch = observation.visualEpoch;
        cameraTransformGeneration = observation.cameraTransformGeneration; frameId = observation.frameId;
        entityId = observation.entityId; vehicleTrackId = observation.vehicleTrackId;
        plateTrackId = observation.plateTrackId; capturedAtMillis = observation.capturedAtMillis;
        capturedElapsedNanos = observation.capturedElapsedNanos;
        text = observation.freshMzAttempted ? observation.freshPrediction : observation.text;
        rawPrediction = text;
        registrationKey = com.example.alpr_v1.domain.RegistrationTextNormalizer.registrationKey(text);
        confidence = observation.freshRecognitionConfidence(); plateConfidence = observation.plateConfidence;
        confirmed = observation.confirmed;
        associationReason = observation.associationReason; this.captureSource = captureSource;
        sourceWidth = observation.geometry.sourceWidthPx; sourceHeight = observation.geometry.sourceHeightPx;
        timing = observation.timing; this.telemetry = telemetry;
    }

    public String key() { return sceneGeneration + ":" + visualEpoch + ":" + cameraTransformGeneration
            + ":" + frameId + ":" + plateTrackId + ":" + capturedAtMillis; }
}
