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
    private final LinkedHashMap<String, String> readingGroups = new LinkedHashMap<>();
    private long nextReadingGroup = 1L;

    /**
     * Static images group by canonical registration; dynamic images group by physical owner.
     * Text equality never merges dynamic vehicle-domain identities.
     */
    public synchronized boolean upsertObservation(com.example.alpr_v1.pipeline.PlateObservation observation,
            ObservationTelemetry telemetry, boolean dynamic, String captureSource) {
        return upsertObservation(observation,telemetry,dynamic,captureSource,"");
    }
    public synchronized boolean upsertObservation(com.example.alpr_v1.pipeline.PlateObservation observation,
            ObservationTelemetry telemetry,boolean dynamic,String captureSource,String captureSessionId) {
        if (observation == null || !observation.hasFreshMzRead()) return false;
        if(dynamic)return upsertDynamicObservation(observation,telemetry,captureSource,captureSessionId);
        RecognitionHistoryObservation record = new RecognitionHistoryObservation(observation, captureSource, telemetry);
        String key = numberKey(record.text);
        if (key.isEmpty()) return false;
        RecognitionHistoryItem existing = items.get(readingGroups.get(key));
        boolean changed = false;
        if (existing == null) {
            Bitmap source = observation.previewBitmap;
            if (source == null || source.isRecycled()) return false;
            Bitmap preview = copy(source);
            if (preview == null) return false;
            existing = new RecognitionHistoryItem("reading:" + nextReadingGroup++, observation.sceneGeneration,
                    observation.entityId, observation.vehicleTrackId, observation.plateTrackId, observation.trackId,
                    record.text, record.confidence, observation.plateConfidence, observation.capturedAtMillis,
                    preview, observation.characters, observation.timing, observation.confirmed, 1,
                    observation.sharpness, captureSource);
            readingGroups.put(key, existing.historyId);
            changed = true;
        }
        // Keep the original image, text and its confidence/provenance together.
        // Later canonical matches keep their own raw text and need metadata only.
        changed |= existing.record(record);
        existing.observations = existing.observationRecords().size();
        existing.lastObservationAtMillis = Math.max(existing.lastObservationAtMillis, observation.capturedAtMillis);
        items.remove(existing.historyId);
        items.put(existing.historyId, existing);
        trimToCapacity();
        return changed;
    }

    /** Shared canonical key. Raw MZ text stays with the representative image and each observation. */
    public static String numberKey(String text) {
        return com.example.alpr_v1.domain.RegistrationTextNormalizer.registrationKey(text);
    }
    private final Map<String,Long> dynamicAliases=new LinkedHashMap<>();
    private static String dynamicKey(long run,long scene,long entity,long visual,long plate) {
        return "dynamic:"+run+":"+scene+(entity>0?":entity:"+entity:":visual:"+visual+":plate:"+plate);
    }
    private boolean upsertDynamicObservation(com.example.alpr_v1.pipeline.PlateObservation observation,
            ObservationTelemetry telemetry,String source,String captureSessionId) {
        RecognitionHistoryObservation record=new RecognitionHistoryObservation(observation,source,telemetry,captureSessionId);
        if(record.registrationKey.isEmpty())return false;
        com.example.alpr_v1.tracking.DynamicVehicleIdentity.Stamp owner=observation.identityOwner();
        long run=observation.dynamicAnalysisRunId();
        long entity=dynamicAliases.getOrDefault(dynamicKey(run,observation.sceneGeneration,observation.entityId,0,0),observation.entityId);
        String key=dynamicKey(run,observation.sceneGeneration,entity,observation.visualEpoch,observation.plateTrackId);
        RecognitionHistoryItem item=items.get(key);
        if(item==null && owner!=null && entity>0) {
            String provisionalKey=dynamicKey(run,observation.sceneGeneration,0,observation.visualEpoch,observation.plateTrackId);
            RecognitionHistoryItem provisional=items.remove(provisionalKey);
            if(provisional!=null) {
                item=provisional.withIdentity(key,entity,observation.vehicleTrackId);
                item.displayNumber=owner.displayNumber;
            }
        }
        if(item==null) {
            if(observation.previewBitmap==null||observation.previewBitmap.isRecycled())return false;
            Bitmap image=copy(observation.previewBitmap);
            if(image==null)return false;
            item=new RecognitionHistoryItem(key,observation.sceneGeneration,entity,observation.vehicleTrackId,
                    observation.plateTrackId,observation.trackId,record.text,record.confidence,observation.plateConfidence,
                    observation.capturedAtMillis,image,observation.characters,observation.timing,observation.confirmed,
                    0,observation.sharpness,source);
            item.dynamicOwner=true;item.analysisRunId=run;item.displayNumber=owner==null?0:owner.displayNumber;
        }
        boolean fresh=item.record(record);
        if(!fresh)return false;
        boolean improved=item.updateConsensus(observation);
        if(improved&&item.observations>0&&observation.previewBitmap!=null&&!observation.previewBitmap.isRecycled()) {
            Bitmap replacement=copy(observation.previewBitmap);
            if(replacement!=null) {
                // Release our reference, not a bitmap potentially still borrowed by the renderer.
                item.previewBitmap=replacement;item.text=record.text;item.confidence=record.confidence;
                item.plateConfidence=record.plateConfidence;item.capturedAtMillis=record.capturedAtMillis;
                item.previewConfidence=record.confidence;item.previewSharpness=observation.sharpness;
                item.previewCapturedAtMillis=record.capturedAtMillis;item.replacePreviewMetadata(observation.characters,observation.timing);
                item.vehicleTrackId=observation.vehicleTrackId;item.plateTrackId=observation.plateTrackId;
                item.trackId=observation.trackId;item.confirmed=record.confirmed;item.captureSource=source;
            }
        }
        item.observations=item.observationRecords().size();
        item.lastObservationAtMillis=Math.max(item.lastObservationAtMillis,record.capturedAtMillis);
        items.remove(key);items.put(key,item);trimToCapacity();return true;
    }
    public synchronized boolean promoteDynamicOwners(long run,long scene,Map<Long,Long> aliases,
            Map<Long,com.example.alpr_v1.tracking.DynamicVehicleIdentity.Presentation> identities) {
        boolean changed=false;
        for(Map.Entry<Long,Long> alias:aliases.entrySet()) {
            String previous=dynamicKey(run,scene,alias.getKey(),0,0),canonical=dynamicKey(run,scene,alias.getValue(),0,0);
            dynamicAliases.put(previous,alias.getValue());
            RecognitionHistoryItem old=items.remove(previous);if(old==null)continue;
            changed=true;
            RecognitionHistoryItem target=items.get(canonical);
            if(target==null) {target=old.withIdentity(canonical,alias.getValue(),old.vehicleTrackId);items.put(canonical,target);}
            else {
                for(RecognitionHistoryObservation observation:old.observationRecords())target.record(observation);
                target.mergeBestConsensus(old);
                old.retire();
            }
            com.example.alpr_v1.tracking.DynamicVehicleIdentity.Presentation presentation=identities.get(alias.getValue());
            if(presentation!=null)target.displayNumber=presentation.number;
            target.observations=target.observationRecords().size();
        }
        while(dynamicAliases.size()>capacity*4)dynamicAliases.remove(dynamicAliases.keySet().iterator().next());
        return changed;
    }

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
        if (existing != null && capturedAtMillis <= existing.lastObservationAtMillis) {
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
            existing.lastObservationAtMillis = capturedAtMillis;
            existing.observations = Math.max(existing.observations, observations);
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
                    // Caption, confidence and provenance belong to the selected bitmap and MZ boxes.
                    // A weaker observation must not relabel an older, better crop.
                    existing.recycle();
                    existing.previewBitmap = replacement;
                    existing.text = normalizedText;
                    existing.confidence = confidence;
                    existing.plateConfidence = plateConfidence;
                    existing.capturedAtMillis = capturedAtMillis;
                    existing.confirmed = confirmed;
                    existing.captureSource = captureSource == null ? "normal" : captureSource;
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
        removed.retire();
        plateOwners.values().removeIf(historyId::equals);
        readingGroups.values().removeIf(historyId::equals);
        return true;
    }

    public synchronized void clear() {
        for (RecognitionHistoryItem item : items.values()) item.retire();
        items.clear();
        plateOwners.clear();
        readingGroups.clear();
        dynamicAliases.clear();
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
            oldest.getValue().retire();
        }
        plateOwners.values().removeIf(id -> !items.containsKey(id));
        readingGroups.values().removeIf(id -> !items.containsKey(id));
        while (plateOwners.size() > capacity * 4) {
            plateOwners.remove(plateOwners.keySet().iterator().next());
        }
    }

    private static Bitmap copy(Bitmap source) {
        return source.copy(Bitmap.Config.ARGB_8888, false);
    }
}
