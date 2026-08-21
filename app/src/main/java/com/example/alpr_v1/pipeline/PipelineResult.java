package com.example.alpr_v1.pipeline;

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
        this.overlayItems = Collections.unmodifiableList(new ArrayList<>(overlayItems));
        this.plateObservations = Collections.emptyList();
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
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
        this.status = status;
        this.message = message;
        this.recognitions = Collections.unmodifiableList(new ArrayList<>(recognitions));
        StringBuilder combined = new StringBuilder();
        double minimum = 1.0;
        for (PlateRecognition recognition : recognitions) {
            if (recognition.text.isEmpty()) continue;
            if (combined.length() > 0) combined.append('\n');
            combined.append(recognition.text);
            minimum = Math.min(minimum, recognition.confidence);
        }
        this.recognizedText = combined.toString();
        this.confidence = combined.length() == 0 ? 0.0 : minimum;
        this.overlayItems = Collections.unmodifiableList(new ArrayList<>(overlayItems));
        this.plateObservations = Collections.unmodifiableList(
                new ArrayList<>(plateObservations)
        );
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
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
