package com.example.alpr_v1.capture;

import android.graphics.Bitmap;
import android.net.Uri;

import com.example.alpr_v1.pipeline.CropInferenceTiming;
import com.example.alpr_v1.pipeline.PlateCharacter;
import com.example.alpr_v1.pipeline.PlateGeometry;
import com.example.alpr_v1.metrics.ImageDifficultyMetrics;

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
    public final float cameraZoomRatio;
    public final String captureSource;
    public final PlateGeometry plateGeometry;
    public final ImageDifficultyMetrics imageDifficulty;
    public final boolean trackConfirmed;
    public final boolean freshMzSuccessful;
    public final boolean cropSupportsConsensus;
    public final int consensusObservations;
    public final int mzAttemptIndex;
    public final String layout;
    public final List<Integer> rowCounts;
    public final String freshPrediction;
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
        this(
                captureId,
                sessionId,
                trackId,
                bitmap,
                text,
                plateConfidence,
                recognitionConfidence,
                confirmed,
                characters,
                capturedAtMillis,
                capturedElapsedNanos,
                sharpness,
                timing,
                1f,
                "normal"
        );
    }

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
            CropInferenceTiming timing,
            float cameraZoomRatio,
            String captureSource
    ) {
        this(
                captureId, sessionId, trackId, bitmap, text, plateConfidence,
                recognitionConfidence, confirmed, characters, capturedAtMillis,
                capturedElapsedNanos, sharpness, timing, cameraZoomRatio, captureSource,
                PlateGeometry.unavailable(), ImageDifficultyMetrics.measure(bitmap), confirmed,
                false, false, 0, 0, "unknown", Collections.emptyList(), ""
        );
    }

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
            CropInferenceTiming timing,
            float cameraZoomRatio,
            String captureSource,
            PlateGeometry plateGeometry,
            ImageDifficultyMetrics imageDifficulty,
            boolean trackConfirmed,
            boolean freshMzSuccessful,
            boolean cropSupportsConsensus,
            int consensusObservations,
            int mzAttemptIndex,
            String layout,
            List<Integer> rowCounts,
            String freshPrediction
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
        this.cameraZoomRatio = Math.max(1f, cameraZoomRatio);
        this.captureSource = captureSource == null ? "normal" : captureSource;
        this.plateGeometry = plateGeometry == null
                ? PlateGeometry.unavailable() : plateGeometry;
        this.imageDifficulty = imageDifficulty == null
                ? ImageDifficultyMetrics.unavailable() : imageDifficulty;
        this.trackConfirmed = trackConfirmed;
        this.freshMzSuccessful = freshMzSuccessful;
        this.cropSupportsConsensus = cropSupportsConsensus;
        this.consensusObservations = Math.max(0, consensusObservations);
        this.mzAttemptIndex = Math.max(0, mzAttemptIndex);
        this.layout = layout == null ? "unknown" : layout;
        this.rowCounts = Collections.unmodifiableList(new ArrayList<>(
                rowCounts == null ? Collections.emptyList() : rowCounts
        ));
        this.freshPrediction = freshPrediction == null ? "" : freshPrediction;
    }

    public boolean isProtectedFromEviction() {
        return saveState == SaveState.SAVING || exportProtected;
    }

    public void recycle() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }
}
