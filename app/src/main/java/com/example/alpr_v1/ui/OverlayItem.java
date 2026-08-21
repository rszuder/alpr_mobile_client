package com.example.alpr_v1.ui;

import android.graphics.PointF;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OverlayItem {
    public final RectF normalizedBounds;
    public final List<PointF> normalizedKeypoints;
    public final String label;
    public final long trackId;
    public final boolean carriedPrediction;

    public OverlayItem(RectF normalizedBounds, List<PointF> normalizedKeypoints, String label) {
        this(normalizedBounds, normalizedKeypoints, label, 0L, false);
    }

    public OverlayItem(
            RectF normalizedBounds,
            List<PointF> normalizedKeypoints,
            String label,
            long trackId,
            boolean carriedPrediction
    ) {
        this.normalizedBounds = new RectF(normalizedBounds);
        this.normalizedKeypoints = Collections.unmodifiableList(new ArrayList<>(normalizedKeypoints));
        this.label = label == null ? "" : label;
        this.trackId = trackId;
        this.carriedPrediction = carriedPrediction;
    }
}
