package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.ui.OverlayItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PipelineResult implements AutoCloseable {
    public final String status;
    public final String message;
    public final String recognizedText;
    public final double confidence;
    public final List<PlateRecognition> recognitions;
    public final List<OverlayItem> overlayItems;
    public final List<PlateObservation> plateObservations;
    public final int sourceWidth;
    public final int sourceHeight;
    public final long sceneGeneration;
    public final long visualEpoch;
    public final long cameraTransformGeneration;
    public final long sourceTimestampNanos;

    public final boolean sceneReset;

    public PipelineResult(
            String status,
            String message,
            String recognizedText,
            double confidence,
            List<OverlayItem> overlayItems
    ) {
        this(status, message, recognizedText, confidence, overlayItems, 0, 0);
    }

    public PipelineResult(
            String status,
            String message,
            String recognizedText,
            double confidence,
            List<OverlayItem> overlayItems,
            int sourceWidth,
            int sourceHeight
    ) {
        this(
                status,
                message,
                recognizedText,
                confidence,
                overlayItems,
                sourceWidth,
                sourceHeight,
                false
        );
    }

    public PipelineResult(
            String status,
            String message,
            String recognizedText,
            double confidence,
            List<OverlayItem> overlayItems,
            int sourceWidth,
            int sourceHeight,
            boolean sceneReset
    ) {
        this(
                status,
                message,
                recognizedText,
                confidence,
                overlayItems,
                sourceWidth,
                sourceHeight,
                sceneReset,
                ContinuityStamp.initial(0L)
        );
    }

    public PipelineResult(
            String status,
            String message,
            String recognizedText,
            double confidence,
            List<OverlayItem> overlayItems,
            int sourceWidth,
            int sourceHeight,
            boolean sceneReset,
            ContinuityStamp continuityStamp
    ) {
        ContinuityStamp safeStamp = continuityStamp == null
                ? ContinuityStamp.initial(0L) : continuityStamp;
        this.status = status;
        this.message = message;
        this.recognizedText = recognizedText == null ? "" : recognizedText;
        this.confidence = confidence;

        if (this.recognizedText.isEmpty()) {
            this.recognitions = Collections.emptyList();
        } else {
            this.recognitions = Collections.singletonList(
                    new PlateRecognition(this.recognizedText, confidence)
            );
        }

        this.overlayItems =
                Collections.unmodifiableList(
                        new ArrayList<>(overlayItems)
                );

        this.plateObservations = Collections.emptyList();
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.sceneReset = sceneReset;
        this.sceneGeneration = safeStamp.sceneGeneration;
        this.visualEpoch = safeStamp.visualEpoch;
        this.cameraTransformGeneration = safeStamp.cameraTransformGeneration;
        this.sourceTimestampNanos = safeStamp.sourceTimestampNanos;
    }

    public PipelineResult(
            String status,
            String message,
            List<PlateRecognition> recognitions,
            List<OverlayItem> overlayItems,
            int sourceWidth,
            int sourceHeight
    ) {
        this(status, message, recognitions, overlayItems, sourceWidth, sourceHeight,
                Collections.emptyList());
    }

    public PipelineResult(
            String status,
            String message,
            List<PlateRecognition> recognitions,
            List<OverlayItem> overlayItems,
            int sourceWidth,
            int sourceHeight,
            List<PlateObservation> plateObservations
    ) {
        this(
                status,
                message,
                recognitions,
                overlayItems,
                sourceWidth,
                sourceHeight,
                plateObservations,
                false
        );
    }

    public PipelineResult(
            String status,
            String message,
            List<PlateRecognition> recognitions,
            List<OverlayItem> overlayItems,
            int sourceWidth,
            int sourceHeight,
            List<PlateObservation> plateObservations,
            boolean sceneReset
    ) {
        this(
                status,
                message,
                recognitions,
                overlayItems,
                sourceWidth,
                sourceHeight,
                plateObservations,
                sceneReset,
                ContinuityStamp.initial(0L)
        );
    }

    public PipelineResult(
            String status,
            String message,
            List<PlateRecognition> recognitions,
            List<OverlayItem> overlayItems,
            int sourceWidth,
            int sourceHeight,
            List<PlateObservation> plateObservations,
            boolean sceneReset,
            ContinuityStamp continuityStamp
    ) {
        ContinuityStamp safeStamp = continuityStamp == null
                ? ContinuityStamp.initial(0L) : continuityStamp;
        this.status = status;
        this.message = message;

        this.recognitions =
                Collections.unmodifiableList(new ArrayList<>(recognitions));

        StringBuilder combined = new StringBuilder();
        double minimum = 1.0;

        for (PlateRecognition recognition : recognitions) {
            if (recognition.text.isEmpty()) continue;

            if (combined.length() > 0) {
                combined.append('\n');
            }

            combined.append(recognition.text);
            minimum = Math.min(minimum, recognition.confidence);
        }

        this.recognizedText = combined.toString();
        this.confidence =
                combined.length() == 0 ? 0.0 : minimum;

        this.overlayItems =
                Collections.unmodifiableList(new ArrayList<>(overlayItems));

        this.plateObservations =
                Collections.unmodifiableList(
                        new ArrayList<>(plateObservations)
                );

        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.sceneReset = sceneReset;
        this.sceneGeneration = safeStamp.sceneGeneration;
        this.visualEpoch = safeStamp.visualEpoch;
        this.cameraTransformGeneration = safeStamp.cameraTransformGeneration;
        this.sourceTimestampNanos = safeStamp.sourceTimestampNanos;
    }

    public ContinuityStamp continuityStamp() {
        return new ContinuityStamp(
                sceneGeneration,
                visualEpoch,
                cameraTransformGeneration,
                sourceTimestampNanos
        );
    }

    public static PipelineResult waitingForModels() {
        return new PipelineResult(
                "models_missing",
                "Zaimportuj model tablic i model znaków",
                "",
                0.0,
                Collections.emptyList()
        );
    }

    public static PipelineResult waitingForModels(ContinuityStamp stamp) {
        return waitingForModels().withContinuityStamp(stamp);
    }

    public PipelineResult withContinuityStamp(ContinuityStamp stamp) {
        List<PlateObservation> stampedObservations = new ArrayList<>(
                plateObservations.size()
        );
        for (PlateObservation observation : plateObservations) {
            stampedObservations.add(observation.withContinuityStamp(stamp));
        }
        return new PipelineResult(
                status,
                message,
                recognitions,
                overlayItems,
                sourceWidth,
                sourceHeight,
                stampedObservations,
                sceneReset,
                stamp
        );
    }

    public PipelineResult withoutGeometryAndFinalization() {
        List<PlateRecognition> safeRecognitions = new ArrayList<>(recognitions.size());
        for (PlateRecognition recognition : recognitions) {
            safeRecognitions.add(new PlateRecognition(
                    recognition.text,
                    recognition.confidence,
                    false,
                    recognition.observations
            ));
        }
        for (PlateObservation observation : plateObservations) {
            observation.recyclePreview();
        }
        return new PipelineResult(
                status,
                message,
                safeRecognitions,
                Collections.emptyList(),
                sourceWidth,
                sourceHeight,
                Collections.emptyList(),
                sceneReset,
                continuityStamp()
        );
    }

    public boolean hasConfirmedRecognition() {
        for (PlateRecognition recognition : recognitions) {
            if (recognition.confirmed) return true;
        }
        return false;
    }

    @Override
    public void close() {
        for (PlateObservation observation : plateObservations) observation.recyclePreview();
    }
}
