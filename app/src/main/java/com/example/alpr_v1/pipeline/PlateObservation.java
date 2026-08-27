package com.example.alpr_v1.pipeline;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Migawka tracku przekazywana do trwałej galerii wyników w UI. */
public final class PlateObservation {
    public final long entityId;
    public final long vehicleTrackId;
    public final long plateTrackId;
    public final VehicleAssociationStatus associationStatus;
    public final float associationConfidence;
    public final String associationReason;
    public final MtWorkKind sourceRoiKind;
    public final MtReason sourceMtReason;
    public final long trackId;
    public final long frameId;
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
    public final float[] appearanceDescriptor;
    public final CropInferenceTiming timing;
    public final PlateGeometry geometry;
    public final boolean freshMzAttempted;
    public final boolean freshMzSuccessful;
    public final String freshPrediction;
    public final boolean cropSupportsConsensus;
    public final int mzAttemptIndex;
    public final String layout;
    public final List<Integer> rowCounts;
    public final String predictionBefore;
    public final String predictionAfter;

    public PlateObservation(
            long trackId,
            PlateVehicleAssociation association,
            MtWorkKind sourceRoiKind,
            MtReason sourceMtReason,
            long frameId,
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
            float[] appearanceDescriptor,
            CropInferenceTiming timing,
            PlateGeometry geometry,
            boolean freshMzAttempted,
            boolean freshMzSuccessful,
            String freshPrediction,
            boolean cropSupportsConsensus,
            int mzAttemptIndex,
            String layout,
            List<Integer> rowCounts,
            String predictionBefore,
            String predictionAfter
    ) {
        PlateVehicleAssociation safeAssociation = association == null
                ? PlateVehicleAssociation.unassigned("missing_association") : association;
        this.entityId = safeAssociation.entityId;
        this.vehicleTrackId = safeAssociation.vehicleTrackId;
        this.plateTrackId = trackId;
        this.associationStatus = safeAssociation.status;
        this.associationConfidence = safeAssociation.confidence;
        this.associationReason = safeAssociation.reason;
        this.sourceRoiKind = sourceRoiKind == null ? MtWorkKind.FULL_FRAME : sourceRoiKind;
        this.sourceMtReason = sourceMtReason == null ? MtReason.UNKNOWN : sourceMtReason;
        this.trackId = trackId;
        this.frameId = frameId;
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
        this.appearanceDescriptor = appearanceDescriptor == null
                ? null : appearanceDescriptor.clone();
        this.timing = timing;
        this.geometry = geometry == null ? PlateGeometry.unavailable() : geometry;
        this.freshMzAttempted = freshMzAttempted;
        this.freshMzSuccessful = freshMzSuccessful;
        this.freshPrediction = freshPrediction == null ? "" : freshPrediction;
        this.cropSupportsConsensus = cropSupportsConsensus;
        this.mzAttemptIndex = Math.max(0, mzAttemptIndex);
        this.layout = layout == null ? TemporalCharacterAggregator.LAYOUT_UNKNOWN : layout;
        this.rowCounts = Collections.unmodifiableList(new ArrayList<>(
                rowCounts == null ? Collections.emptyList() : rowCounts
        ));
        this.predictionBefore = predictionBefore == null ? "" : predictionBefore;
        this.predictionAfter = predictionAfter == null ? "" : predictionAfter;
    }

    void recyclePreview() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) previewBitmap.recycle();
    }
}
