package com.example.alpr_v1.capture;

import androidx.lifecycle.ViewModel;

import com.example.alpr_v1.metrics.MetricsCollector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Przechowuje ciężki stan galerii podczas odtwarzania aktywności, np. po obrocie ekranu.
 * Bitmap nie zapisujemy w Bundle, ponieważ łatwo przekroczyłyby limit transakcji Androida.
 */
public final class CaptureGalleryViewModel extends ViewModel {
    private final List<CapturedPlateItem> capturedCrops = new ArrayList<>();
    private final Map<Long, CropSamplingPolicy.Previous> lastCaptureByTrack = new HashMap<>();
    private final MetricsCollector metricsCollector = new MetricsCollector();

    private boolean collectionActive;
    private String collectionSessionId = "";
    private long collectionSessionStartedElapsedNanos;
    private int collectionSequence;
    private boolean galleryExpanded;
    private boolean galleryMaximized;

    public List<CapturedPlateItem> capturedCrops() { return capturedCrops; }

    public Map<Long, CropSamplingPolicy.Previous> lastCaptureByTrack() {
        return lastCaptureByTrack;
    }

    public MetricsCollector metricsCollector() { return metricsCollector; }

    public boolean collectionActive() { return collectionActive; }
    public String collectionSessionId() { return collectionSessionId; }
    public long collectionSessionStartedElapsedNanos() {
        return collectionSessionStartedElapsedNanos;
    }
    public int collectionSequence() { return collectionSequence; }

    public void retainSession(
            boolean active,
            String sessionId,
            long startedElapsedNanos,
            int sequence
    ) {
        collectionActive = active;
        collectionSessionId = sessionId == null ? "" : sessionId;
        collectionSessionStartedElapsedNanos = startedElapsedNanos;
        collectionSequence = Math.max(0, sequence);
    }

    public boolean galleryExpanded() { return galleryExpanded; }
    public void setGalleryExpanded(boolean expanded) {
        galleryExpanded = expanded;
        if (!expanded) galleryMaximized = false;
    }
    public boolean galleryMaximized() { return galleryMaximized; }
    public void setGalleryMaximized(boolean maximized) {
        galleryMaximized = maximized;
        if (maximized) galleryExpanded = true;
    }

    @Override
    protected void onCleared() {
        for (CapturedPlateItem item : capturedCrops) item.recycle();
        capturedCrops.clear();
        lastCaptureByTrack.clear();
        metricsCollector.clearCropSession();
    }
}
