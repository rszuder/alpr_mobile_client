package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.ui.OverlayItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PipelineResult {
    public final String status;
    public final String message;
    public final String recognizedText;
    public final double confidence;
    public final List<OverlayItem> overlayItems;
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
        this.overlayItems = Collections.unmodifiableList(new ArrayList<>(overlayItems));
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
}
