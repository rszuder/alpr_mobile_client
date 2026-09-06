package com.example.alpr_v1.capture;

import android.graphics.Bitmap;

public final class RecognitionHistoryItem {
    public final String historyId;
    public final long sceneGeneration;
    public final long entityId;
    public final long vehicleTrackId;
    public final long plateTrackId;
    public final long trackId;
    public String text;
    public double confidence;
    public double plateConfidence;
    public long capturedAtMillis;
    public Bitmap previewBitmap;
    public boolean confirmed;
    public int observations;
    public float previewSharpness;
    public double previewConfidence;
    public long previewCapturedAtMillis;
    public String captureSource;

    RecognitionHistoryItem(
            String historyId,
            long sceneGeneration,
            long entityId,
            long vehicleTrackId,
            long plateTrackId,
            long trackId,
            String text,
            double confidence,
            double plateConfidence,
            long capturedAtMillis,
            Bitmap previewBitmap,
            boolean confirmed,
            int observations,
            float previewSharpness,
            String captureSource
    ) {
        this.historyId = historyId;
        this.sceneGeneration = sceneGeneration;
        this.entityId = entityId;
        this.vehicleTrackId = vehicleTrackId;
        this.plateTrackId = plateTrackId;
        this.trackId = trackId;
        this.text = text;
        this.confidence = confidence;
        this.plateConfidence = plateConfidence;
        this.capturedAtMillis = capturedAtMillis;
        this.previewBitmap = previewBitmap;
        this.confirmed = confirmed;
        this.observations = observations;
        this.previewSharpness = previewSharpness;
        this.previewConfidence = confidence;
        this.previewCapturedAtMillis = capturedAtMillis;
        this.captureSource = captureSource;
    }

    void recycle() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) previewBitmap.recycle();
        previewBitmap = null;
    }
}
