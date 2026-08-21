package com.example.alpr_v1.capture;

import android.graphics.Bitmap;
import android.net.Uri;

import com.example.alpr_v1.pipeline.CropInferenceTiming;
import com.example.alpr_v1.pipeline.PlateCharacter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Element sesji zbierania cropów i źródło danych karty galerii. */
public final class CapturedPlateItem {
    public enum SaveState { NOT_SAVED, SAVING, SAVED, ERROR }
    public enum VerificationStatus {
        NOT_REVIEWED("not_reviewed"),
        ACCEPTED("accepted"),
        REJECTED("rejected"),
        CORRECTED("corrected");

        private final String wireName;

        VerificationStatus(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
    }

    public final String captureId;
    public final String sessionId;
    public final long trackId;
    public final Bitmap bitmap;
    public final String text;
    public final double plateConfidence;
    public final double recognitionConfidence;
    public final boolean confirmed;
    public final List<PlateCharacter> characters;
    public final long capturedAtMillis;
    public final long capturedElapsedNanos;
    public final float sharpness;
    public final CropInferenceTiming timing;
    public volatile SaveState saveState = SaveState.NOT_SAVED;
    public volatile boolean selectedForSave;
    public volatile Uri savedImageUri;
    public volatile Uri savedReportUri;
    public volatile String miniReportJson = "";
    public volatile VerificationStatus verificationStatus = VerificationStatus.NOT_REVIEWED;
    public volatile String groundTruthText = "";
    public volatile long verifiedAtMillis;
    public volatile int verificationRevision;
    public volatile boolean exportProtected;

    public CapturedPlateItem(
            String captureId,
            String sessionId,
            long trackId,
            Bitmap bitmap,
            String text,
            double plateConfidence,
            double recognitionConfidence,
            boolean confirmed,
            List<PlateCharacter> characters,
            long capturedAtMillis,
            long capturedElapsedNanos,
            float sharpness,
            CropInferenceTiming timing
    ) {
        this.captureId = captureId;
        this.sessionId = sessionId;
        this.trackId = trackId;
        this.bitmap = bitmap;
        this.text = text == null ? "" : text;
        this.plateConfidence = plateConfidence;
        this.recognitionConfidence = recognitionConfidence;
        this.confirmed = confirmed;
        this.characters = Collections.unmodifiableList(new ArrayList<>(characters));
        this.capturedAtMillis = capturedAtMillis;
        this.capturedElapsedNanos = capturedElapsedNanos;
        this.sharpness = sharpness;
        this.timing = timing;
    }

    public boolean isProtectedFromEviction() {
        return saveState == SaveState.SAVING || exportProtected;
    }

    public void recycle() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }
}
