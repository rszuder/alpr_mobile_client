package com.example.alpr_v1.pipeline;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Migawka tracku przekazywana do trwałej galerii wyników w UI. */
public final class PlateObservation {
    public final long trackId;
    public final Bitmap previewBitmap;
    public final String text;
    public final double plateConfidence;
    public final double recognitionConfidence;
    public final boolean confirmed;
    public final int observations;
    public final List<PlateCharacter> characters;
    public final long capturedAtMillis;
    public final long capturedElapsedNanos;
    public final float sharpness;
    public final CropInferenceTiming timing;

    public PlateObservation(
            long trackId,
            Bitmap previewBitmap,
            String text,
            double plateConfidence,
            double recognitionConfidence,
            boolean confirmed,
            int observations,
            List<PlateCharacter> characters,
            long capturedAtMillis,
            long capturedElapsedNanos,
            float sharpness,
            CropInferenceTiming timing
    ) {
        this.trackId = trackId;
        this.previewBitmap = previewBitmap;
        this.text = text == null ? "" : text;
        this.plateConfidence = plateConfidence;
        this.recognitionConfidence = recognitionConfidence;
        this.confirmed = confirmed;
        this.observations = Math.max(0, observations);
        this.characters = Collections.unmodifiableList(new ArrayList<>(characters));
        this.capturedAtMillis = capturedAtMillis;
        this.capturedElapsedNanos = capturedElapsedNanos;
        this.sharpness = Math.max(0f, Math.min(1f, sharpness));
        this.timing = timing;
    }

    void recyclePreview() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) previewBitmap.recycle();
    }
}
