package com.example.alpr_v1.capture;

import android.graphics.Bitmap;

import com.example.alpr_v1.pipeline.CropInferenceTiming;
import com.example.alpr_v1.pipeline.PlateCharacter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public List<PlateCharacter> characters;
    public CropInferenceTiming timing;
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
            List<PlateCharacter> characters,
            CropInferenceTiming timing,
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
        this.characters = immutableCharacters(characters);
        this.timing = timing;
        this.confirmed = confirmed;
        this.observations = observations;
        this.previewSharpness = previewSharpness;
        this.previewConfidence = confidence;
        this.previewCapturedAtMillis = capturedAtMillis;
        this.captureSource = captureSource;
    }

    void replacePreviewMetadata(
            List<PlateCharacter> characters,
            CropInferenceTiming timing
    ) {
        this.characters = immutableCharacters(characters);
        this.timing = timing;
    }

    private static List<PlateCharacter> immutableCharacters(List<PlateCharacter> characters) {
        return Collections.unmodifiableList(new ArrayList<>(
                characters == null ? Collections.emptyList() : characters
        ));
    }

    void recycle() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) previewBitmap.recycle();
        previewBitmap = null;
    }
}
