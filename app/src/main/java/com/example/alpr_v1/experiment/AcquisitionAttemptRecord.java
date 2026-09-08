package com.example.alpr_v1.experiment;

import android.graphics.Bitmap;
import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.metrics.ImageDifficultyMetrics;
import com.example.alpr_v1.pipeline.PlateObservation;
import org.json.JSONObject;
import java.util.Collections;

/** Mutable only on its inference frame; ownership passes to the store on submit. */
public final class AcquisitionAttemptRecord {
    public enum MtStatus { NOT_RUN, NO_DETECTION, DETECTION_INVALID_QUAD, VALID_QUAD }
    public enum RectificationStatus { NOT_RUN, FAILED, OK }
    public enum MzStatus { NOT_RUN, NO_CHARACTERS, READ }
    public final String attemptId, sessionId;
    final ResearchSessionStore store;
    final JSONObject data = new JSONObject();
    Bitmap image;
    long imageBytes;
    Bitmap mtInputImage;
    long mtInputImageBytes;
    boolean ownsAttemptPermit = true;
    boolean plateCrop;
    private PlateObservation observation;

    AcquisitionAttemptRecord(ResearchSessionStore store,String id,ContinuityStamp stamp,
                             String roiPolicy,float zoom,long entity,long vehicle) {
        this.store = store; attemptId = id; sessionId = store.sessionId();
        put("attempt_id",id); put("session_id",sessionId);
        put("scene_generation",stamp.sceneGeneration); put("visual_epoch",stamp.visualEpoch);
        put("camera_transform_generation",stamp.cameraTransformGeneration);
        put("entity_id",entity); put("vehicle_track_id",vehicle); put("plate_track_id",0L);
        put("source_sequence",stamp.sourceSequence); put("source_timestamp_nanos",stamp.sourceTimestampNanos);
        put("source_timestamp_domain",stamp.sourceTimestampDomain.name());
        put("attempt_started_elapsed_nanos",android.os.SystemClock.elapsedRealtimeNanos());
        put("captured_at_ms",System.currentTimeMillis()); put("roi_policy",roiPolicy);
        put("capture_source",zoom > 1.01f ? "auto_zoom" : "normal"); put("camera_zoom_ratio",zoom);
        put("mt_status",MtStatus.NOT_RUN.name()); put("rectification_status",RectificationStatus.NOT_RUN.name());
        put("mt_executed",false); put("mt_invocation_id","");
        put("mt_detection_index",JSONObject.NULL); put("mt_detection_count",JSONObject.NULL);
        put("mt_input_evidence_entry",""); put("mt_input_missing_evidence_reason","");
        put("mz_status",MzStatus.NOT_RUN.name()); put("prediction",""); put("consensus_prediction","");
        put("plate_confidence",0); put("recognition_confidence",0);
        put("evidence_kind",""); put("evidence_entry",""); put("missing_evidence_reason","");
        put("stale_or_cancelled",false); put("cancel_reason",""); put("write_state","QUEUED");
        refreshIdentity();
    }
    public void put(String key,Object value) {
        if (value instanceof Number && !Double.isFinite(((Number)value).doubleValue())) value=JSONObject.NULL;
        try { data.put(key,value); }
        catch (org.json.JSONException error) { store.recordMetadataFailure("attempt_metadata:"+key); }
    }
    public void associate(long entity,long vehicle,long track) {
        put("entity_id",entity); put("vehicle_track_id",vehicle); put("plate_track_id",track); refreshIdentity();
    }
    private void refreshIdentity() { put("subject_key",identity().subjectKey); }
    public ResearchSampleIdentity identity() {
        return new ResearchSampleIdentity(attemptId,sessionId,data.optLong("scene_generation"),
                data.optLong("entity_id"),data.optLong("vehicle_track_id"),data.optLong("plate_track_id"));
    }
    /** Called immediately before entering the MT backend, including calls that throw. */
    public void mtStarted() {
        put("mt_invocation_id",attemptId); put("mt_executed",true);
        put("mt_status",MtStatus.NO_DETECTION.name()); put("mt_detection_count",0);
    }
    public void copyEvidence(Bitmap bitmap) {
        store.copyImage(this,bitmap,false);
        put("mt_input_missing_evidence_reason",data.optString("missing_evidence_reason"));
    }
    public void copyPlateCrop(Bitmap bitmap) {
        put("rectification_status",RectificationStatus.OK.name()); store.copyImage(this,bitmap,true);
    }
    public void observe(PlateObservation value) {
        observation = value;
        associate(value.entityId,value.vehicleTrackId,value.plateTrackId);
        put("prediction",value.freshPrediction); put("consensus_prediction",value.text);
        put("plate_confidence",value.plateConfidence); put("consensus_confidence",value.recognitionConfidence);
        double confidence=0.0;
        for (com.example.alpr_v1.pipeline.PlateCharacter character:value.characters) confidence+=character.confidence;
        put("recognition_confidence",value.characters.isEmpty() ? 0.0 : confidence/value.characters.size());
        value.researchIdentity = identity();
    }
    public void cancel(String reason) { put("stale_or_cancelled",true); put("cancel_reason",reason); }
    CapturedPlateItem cropMetadata() {
        PlateObservation o = observation;
        CapturedPlateItem item = o == null ? new CapturedPlateItem(attemptId,sessionId,
                data.optLong("plate_track_id"),null,data.optString("consensus_prediction"),
                data.optDouble("plate_confidence",0.0),data.optDouble("recognition_confidence",0.0),false,
                Collections.emptyList(),data.optLong("captured_at_ms"),data.optLong("attempt_started_elapsed_nanos"),
                0f,null,(float)data.optDouble("camera_zoom_ratio",1),data.optString("capture_source"),
                com.example.alpr_v1.pipeline.PlateGeometry.unavailable(),ImageDifficultyMetrics.measure(image),
                false,"READ".equals(data.optString("mz_status")),false,0,0,"unknown",Collections.emptyList(),data.optString("prediction"))
                : new CapturedPlateItem(attemptId,sessionId,o.trackId,null,o.text,o.plateConfidence,
                data.optDouble("recognition_confidence"),o.confirmed,o.characters,o.capturedAtMillis,o.capturedElapsedNanos,
                o.sharpness,o.timing,(float)data.optDouble("camera_zoom_ratio",1),data.optString("capture_source"),
                o.geometry,ImageDifficultyMetrics.measure(image),o.confirmed,o.freshMzSuccessful,
                o.cropSupportsConsensus,o.observations,o.mzAttemptIndex,o.layout,o.rowCounts,o.freshPrediction);
        item.researchIdentity = identity();
        return item;
    }
}
