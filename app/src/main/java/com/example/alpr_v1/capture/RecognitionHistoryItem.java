package com.example.alpr_v1.capture;

import android.graphics.Bitmap;

import com.example.alpr_v1.pipeline.CropInferenceTiming;
import com.example.alpr_v1.pipeline.PlateCharacter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecognitionHistoryItem implements AutoCloseable {
    public final String historyId;
    public final long sceneGeneration;
    public final long entityId;
    public long vehicleTrackId;
    public long plateTrackId;
    public long trackId;
    public String text;
    public double confidence;
    public double plateConfidence;
    public long capturedAtMillis;
    public long lastObservationAtMillis;
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
        this.lastObservationAtMillis = capturedAtMillis;
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

    public RecognitionHistoryItem snapshot() {
        if (previewBitmap == null || previewBitmap.isRecycled()) return null;
        Bitmap copy = previewBitmap.copy(Bitmap.Config.ARGB_8888, false);
        if (copy == null) return null;
        RecognitionHistoryItem snapshot = new RecognitionHistoryItem(historyId, sceneGeneration,
                entityId, vehicleTrackId, plateTrackId, trackId, text, confidence,
                plateConfidence, capturedAtMillis, copy, characters, timing, confirmed,
                observations, previewSharpness, captureSource);
        snapshot.lastObservationAtMillis = lastObservationAtMillis;
        return snapshot;
    }

    /** Transfers the retained crop to a proven entity without copying or recycling it. */
    RecognitionHistoryItem withIdentity(String id, long ownerId, long ownerTrackId) {
        RecognitionHistoryItem moved = new RecognitionHistoryItem(id, sceneGeneration,
                ownerId, ownerTrackId, plateTrackId, trackId, text, confidence,
                plateConfidence, capturedAtMillis, previewBitmap, characters, timing,
                confirmed, observations, previewSharpness, captureSource);
        moved.previewConfidence = previewConfidence;
        moved.previewCapturedAtMillis = previewCapturedAtMillis;
        moved.lastObservationAtMillis = lastObservationAtMillis;
        previewBitmap = null;
        return moved;
    }

    @Override
    public void close() { recycle(); }

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
