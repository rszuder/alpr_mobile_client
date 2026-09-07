package com.example.alpr_v1.capture;

import android.graphics.Bitmap;

import com.example.alpr_v1.pipeline.CropInferenceTiming;
import com.example.alpr_v1.pipeline.PlateCharacter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecognitionHistoryStore {
    public static final int DEFAULT_CAPACITY = 40;

    private final int capacity;
    private final LinkedHashMap<String, RecognitionHistoryItem> items = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> plateOwners = new LinkedHashMap<>();

    public RecognitionHistoryStore() {
        this(DEFAULT_CAPACITY);
    }

    RecognitionHistoryStore(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized boolean upsert(
            long sceneGeneration,
            long entityId,
            long vehicleTrackId,
            long plateTrackId,
            long trackId,
            String text,
            double confidence,
            double plateConfidence,
            long capturedAtMillis,
            Bitmap sourcePreview,
            boolean confirmed,
            int observations,
            float sharpness,
            String captureSource
    ) {
        return upsert(
                sceneGeneration,
                entityId,
                vehicleTrackId,
                plateTrackId,
                trackId,
                text,
                confidence,
                plateConfidence,
                capturedAtMillis,
                sourcePreview,
                Collections.emptyList(),
                null,
                confirmed,
                observations,
                sharpness,
                captureSource
        );
    }

    public synchronized boolean upsert(
            long sceneGeneration,
            long entityId,
            long vehicleTrackId,
            long plateTrackId,
            long trackId,
            String text,
            double confidence,
            double plateConfidence,
            long capturedAtMillis,
            Bitmap sourcePreview,
            List<PlateCharacter> characters,
            CropInferenceTiming timing,
            boolean confirmed,
            int observations,
            float sharpness,
            String captureSource
    ) {
        return upsert(sceneGeneration, entityId, vehicleTrackId, plateTrackId, trackId,
                text, confidence, plateConfidence, capturedAtMillis, sourcePreview,
                characters, timing, confirmed, observations, sharpness, captureSource, 0L);
    }

    public synchronized boolean upsert(
            long sceneGeneration, long entityId, long vehicleTrackId, long plateTrackId,
            long trackId, String text, double confidence, double plateConfidence,
            long capturedAtMillis, Bitmap sourcePreview, List<PlateCharacter> characters,
            CropInferenceTiming timing, boolean confirmed, int observations, float sharpness,
            String captureSource, long visualEpoch
    ) {
        String normalizedText = text == null ? "" : text.trim();
        // Plate track ids may restart within a scene when the visual epoch changes.
        String plateKey = historyId(sceneGeneration, 0L, plateTrackId, trackId)
                + ":v" + visualEpoch;
        String historyId = entityId > 0L
                ? historyId(sceneGeneration, entityId, plateTrackId, trackId) : plateKey;
        boolean promoted = false;
        if (entityId > 0L) {
            plateOwners.put(plateKey, historyId);
            RecognitionHistoryItem provisional = items.remove(plateKey);
            if (provisional != null) {
                if (!items.containsKey(historyId)) {
                    items.put(historyId, provisional.withIdentity(historyId, entityId, vehicleTrackId));
                } else {
                    provisional.recycle();
                }
                promoted = true;
            }
        } else {
            String knownId = plateOwners.get(plateKey);
            RecognitionHistoryItem known = items.get(knownId);
            if (known != null) {
                historyId = knownId;
                entityId = known.entityId;
                vehicleTrackId = known.vehicleTrackId;
            }
        }
        RecognitionHistoryItem existing = items.get(historyId);
        // A later association can identify the first crop without running MZ again.
        if (sourcePreview == null || sourcePreview.isRecycled()) {
            trimToCapacity();
            return promoted;
        }
        // The per-MZ callback and final pipeline result may contain the same crop.
        if (existing != null && capturedAtMillis <= existing.capturedAtMillis) {
            trimToCapacity();
            return promoted;
        }
        if (existing == null) {
            Bitmap preview = copy(sourcePreview);
            if (preview == null) return false;
            existing = new RecognitionHistoryItem(
                    historyId,
                    sceneGeneration,
                    entityId,
                    vehicleTrackId,
                    plateTrackId,
                    trackId,
                    normalizedText,
                    confidence,
                    plateConfidence,
                    capturedAtMillis,
                    preview,
                    characters,
                    timing,
                    confirmed,
                    Math.max(0, observations),
                    sharpness,
                    captureSource == null ? "normal" : captureSource
            );
        } else {
            existing.text = normalizedText;
            existing.confidence = confidence;
            existing.plateConfidence = plateConfidence;
            existing.capturedAtMillis = capturedAtMillis;
            existing.confirmed = confirmed;
            existing.observations = Math.max(existing.observations, observations);
            existing.captureSource = captureSource == null
                    ? existing.captureSource : captureSource;
            if (isBetterPreview(
                    confidence,
                    sharpness,
                    capturedAtMillis,
                    existing.previewConfidence,
                    existing.previewSharpness,
                    existing.previewCapturedAtMillis
            )) {
                Bitmap replacement = copy(sourcePreview);
                if (replacement != null) {
                    existing.recycle();
                    existing.previewBitmap = replacement;
                    existing.vehicleTrackId = vehicleTrackId;
                    existing.plateTrackId = plateTrackId;
                    existing.trackId = trackId;
                    existing.previewConfidence = confidence;
                    existing.previewSharpness = sharpness;
                    existing.previewCapturedAtMillis = capturedAtMillis;
                    existing.replacePreviewMetadata(characters, timing);
                }
            }
        }
        items.remove(historyId);
        items.put(historyId, existing);
        trimToCapacity();
        return true;
    }

    public synchronized List<RecognitionHistoryItem> newestFirst() {
        List<RecognitionHistoryItem> snapshot = new ArrayList<>(items.values());
        Collections.reverse(snapshot);
        return snapshot;
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized boolean remove(String historyId) {
        RecognitionHistoryItem removed = items.remove(historyId);
        if (removed == null) return false;
        removed.recycle();
        plateOwners.values().removeIf(historyId::equals);
        return true;
    }

    public synchronized void clear() {
        for (RecognitionHistoryItem item : items.values()) item.recycle();
        items.clear();
        plateOwners.clear();
    }

    static String historyId(
            long sceneGeneration,
            long entityId,
            long plateTrackId,
            long trackId
    ) {
        if (entityId > 0L) return sceneGeneration + ":entity:" + entityId;
        if (plateTrackId > 0L) return sceneGeneration + ":plate:" + plateTrackId;
        return sceneGeneration + ":track:" + trackId;
    }

    static boolean isBetterPreview(
            double candidateConfidence,
            float candidateSharpness,
            long candidateCapturedAtMillis,
            double currentConfidence,
            float currentSharpness,
            long currentCapturedAtMillis
    ) {
        int confidenceOrder = Double.compare(candidateConfidence, currentConfidence);
        if (confidenceOrder != 0) return confidenceOrder > 0;
        int sharpnessOrder = Float.compare(candidateSharpness, currentSharpness);
        if (sharpnessOrder != 0) return sharpnessOrder > 0;
        return candidateCapturedAtMillis > currentCapturedAtMillis;
    }

    private void trimToCapacity() {
        while (items.size() > capacity) {
            Map.Entry<String, RecognitionHistoryItem> oldest =
                    items.entrySet().iterator().next();
            items.remove(oldest.getKey());
            oldest.getValue().recycle();
        }
        plateOwners.values().removeIf(id -> !items.containsKey(id));
        while (plateOwners.size() > capacity * 4) {
            plateOwners.remove(plateOwners.keySet().iterator().next());
        }
    }

    private static Bitmap copy(Bitmap source) {
        return source.copy(Bitmap.Config.ARGB_8888, false);
    }
}
