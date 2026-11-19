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
    public boolean dynamicOwner;
    public long analysisRunId, displayNumber, consensusSourceNanos=Long.MIN_VALUE, consensusEpoch;
    public String currentText="";
    public double currentConfidence;
    public boolean currentConfirmed;
    public int bestConsensusObservations;
    public String bestConsensusObservationKey="";
    public String displayText() {
        if(!dynamicOwner)return text;
        return (displayNumber>0?"P"+displayNumber:"Tablica")+" · "+currentText;
    }
    public double displayConfidence() { return dynamicOwner?currentConfidence:confidence; }
    public boolean displayConfirmed() {
        return dynamicOwner?com.example.alpr_v1.domain.DynamicReadingSelection.confirmedForDisplay(
                currentConfirmed,currentConfidence,bestConsensusObservations):confirmed;
    }
    boolean updateConsensus(com.example.alpr_v1.pipeline.PlateObservation observation) {
        long source=observation.sourceTimestampNanos>0?observation.sourceTimestampNanos:observation.capturedElapsedNanos;
        long epoch=observation.identityOwner()==null?0:observation.identityOwner().epoch;
        if(epoch<consensusEpoch||epoch==consensusEpoch&&source<consensusSourceNanos)return false;
        // Freshness fences follow every attempt; the presented candidate follows only improvements.
        consensusSourceNanos=source;consensusEpoch=epoch;
        boolean better=com.example.alpr_v1.domain.DynamicReadingSelection.improves(observation.recognitionConfidence,
                observation.observations,currentConfidence,bestConsensusObservations,!currentText.isEmpty());
        if(observation.text.isEmpty()||!better)return false;
        currentText=observation.text;currentConfidence=observation.recognitionConfidence;
        currentConfirmed=observation.confirmed;bestConsensusObservations=observation.observations;
        bestConsensusObservationKey=new RecognitionHistoryObservation(observation,"",null).key();
        return true;
    }
    public RecognitionHistoryObservation bestConsensusObservation() {
        return observationRecords.get(bestConsensusObservationKey);
    }
    void mergeBestConsensus(RecognitionHistoryItem other) {
        if(com.example.alpr_v1.domain.DynamicReadingSelection.improves(other.currentConfidence,other.bestConsensusObservations,
                currentConfidence,bestConsensusObservations,!currentText.isEmpty())&&!other.currentText.isEmpty()) {
            currentText=other.currentText;currentConfidence=other.currentConfidence;currentConfirmed=other.currentConfirmed;
            bestConsensusObservations=other.bestConsensusObservations;bestConsensusObservationKey=other.bestConsensusObservationKey;
            if(other.previewBitmap!=null&&!other.previewBitmap.isRecycled()) {
                previewBitmap=other.previewBitmap;text=other.text;confidence=other.confidence;
                plateConfidence=other.plateConfidence;capturedAtMillis=other.capturedAtMillis;
                previewConfidence=other.previewConfidence;previewSharpness=other.previewSharpness;
                previewCapturedAtMillis=other.previewCapturedAtMillis;characters=other.characters;timing=other.timing;
                confirmed=other.confirmed;captureSource=other.captureSource;
            }
        }
        if(other.consensusEpoch>consensusEpoch||other.consensusEpoch==consensusEpoch&&other.consensusSourceNanos>consensusSourceNanos) {
            consensusEpoch=other.consensusEpoch;consensusSourceNanos=other.consensusSourceNanos;
        }
    }
    private void copyDynamicTo(RecognitionHistoryItem target) {
        target.dynamicOwner=dynamicOwner;target.analysisRunId=analysisRunId;target.displayNumber=displayNumber;
        target.currentText=currentText;target.currentConfidence=currentConfidence;target.currentConfirmed=currentConfirmed;
        target.consensusSourceNanos=consensusSourceNanos;target.consensusEpoch=consensusEpoch;
        target.bestConsensusObservations=bestConsensusObservations;target.bestConsensusObservationKey=bestConsensusObservationKey;
    }
    private final java.util.LinkedHashMap<String, RecognitionHistoryObservation> observationRecords = new java.util.LinkedHashMap<>();
    public List<RecognitionHistoryObservation> observationRecords() {
        return Collections.unmodifiableList(new ArrayList<>(observationRecords.values()));
    }
    boolean record(RecognitionHistoryObservation observation) {
        String key = RecognitionHistoryStore.numberKey(text);
        if (!dynamicOwner && (key.isEmpty() || !key.equals(observation.registrationKey))) return false;
        RecognitionHistoryObservation old = observationRecords.get(observation.key());
        if (old != null && !(old.entityId == 0L && observation.entityId > 0L)) return false;
        observationRecords.put(observation.key(), observation);
        return true;
    }

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
        snapshot.observationRecords.putAll(observationRecords);
        copyDynamicTo(snapshot);
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
        moved.observationRecords.putAll(observationRecords);
        copyDynamicTo(moved);
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
    /** A view may still borrow the old thumbnail until RecyclerView rebinds it. */
    void retire() { if(dynamicOwner)previewBitmap=null;else recycle(); }
}
