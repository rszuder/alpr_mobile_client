package com.example.alpr_v1.ui;

import android.graphics.PointF;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OverlayItem {

    public enum Kind {
        PLATE,
        VEHICLE,
        VEHICLE_ROI
    }

    public final Kind kind;
    public final RectF normalizedBounds;
    public final List<PointF> normalizedKeypoints;
    public final String label;
    public final long trackId;
    public final boolean carriedPrediction;

    /*
     * Konstruktor zgodnościowy.
     * Wszystkie dotychczasowe elementy overlay są traktowane jako PLATE.
     */
    public OverlayItem(
            RectF normalizedBounds,
            List<PointF> normalizedKeypoints,
            String label
    ) {
        this(
                Kind.PLATE,
                normalizedBounds,
                normalizedKeypoints,
                label,
                0L,
                false
        );
    }

    /*
     * Konstruktor używany obecnie przez tracker tablic.
     */
    public OverlayItem(
            RectF normalizedBounds,
            List<PointF> normalizedKeypoints,
            String label,
            long trackId,
            boolean carriedPrediction
    ) {
        this(
                Kind.PLATE,
                normalizedBounds,
                normalizedKeypoints,
                label,
                trackId,
                carriedPrediction
        );
    }

    /*
     * Konstruktor ogólny dla wszystkich elementów overlay.
     */
    public OverlayItem(
            Kind kind,
            RectF normalizedBounds,
            List<PointF> normalizedKeypoints,
            String label,
            long trackId,
            boolean carriedPrediction
    ) {
        this.kind = kind == null ? Kind.PLATE : kind;
        this.normalizedBounds = new RectF(normalizedBounds);
        this.normalizedKeypoints = Collections.unmodifiableList(
                new ArrayList<>(normalizedKeypoints)
        );
        this.label = label == null ? "" : label;
        this.trackId = trackId;
        this.carriedPrediction = carriedPrediction;
    }
}