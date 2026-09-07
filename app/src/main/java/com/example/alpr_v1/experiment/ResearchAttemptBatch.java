package com.example.alpr_v1.experiment;

import android.graphics.Bitmap;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.vision.Detection;
import org.json.JSONObject;
import java.util.*;

/** Passive, frame-local audit of actual MT calls and their downstream MZ work. */
public final class ResearchAttemptBatch {
    private final ResearchSessionStore store;
    private final ContinuityStamp stamp;
    private final String policy;
    private final float zoom;
    private final List<AcquisitionAttemptRecord> records = new ArrayList<>();
    private final Map<Detection,AcquisitionAttemptRecord> detections = new IdentityHashMap<>();
    private final Set<AcquisitionAttemptRecord> assigned = Collections.newSetFromMap(new IdentityHashMap<>());
    public ResearchAttemptBatch(ResearchSessionStore store,ContinuityStamp stamp,String policy,float zoom) {
        this.store=store; this.stamp=stamp; this.policy=policy; this.zoom=zoom;
    }
    public AcquisitionAttemptRecord beginMt(long entity,long vehicle,int left,int top,int right,int bottom,
                                            int inputWidth,int inputHeight) {
        AcquisitionAttemptRecord record = store.beginAttempt(stamp,policy,zoom,entity,vehicle);
        if (record != null) {
            record.put("mt_invocation_id",record.attemptId);
            record.put("roi_left",left); record.put("roi_top",top); record.put("roi_right",right); record.put("roi_bottom",bottom);
            record.put("input_width",inputWidth); record.put("input_height",inputHeight);
            records.add(record);
        }
        return record;
    }
    public void detected(AcquisitionAttemptRecord call,Detection detection,boolean validQuad) {
        if (call == null) return;
        AcquisitionAttemptRecord record = call;
        if (!assigned.add(call)) {
            record = store.beginAttempt(stamp,policy,zoom,call.data.optLong("entity_id"),call.data.optLong("vehicle_track_id"));
            if (record == null) return;
            record.put("mt_invocation_id",call.attemptId); record.copyEvidence(call.image); records.add(record);
        }
        record.put("mt_status",validQuad ? "VALID_QUAD" : "DETECTION_INVALID_QUAD");
        record.put("plate_confidence",detection.confidence);
        record.put("plate_left",detection.left); record.put("plate_top",detection.top);
        record.put("plate_right",detection.right); record.put("plate_bottom",detection.bottom);
        detections.put(detection,record);
    }
    public AcquisitionAttemptRecord forDetection(Detection detection) { return detections.get(detection); }
    public void observe(Detection detection,PlateObservation observation) {
        AcquisitionAttemptRecord record = detections.get(detection);
        if (record != null) record.observe(observation);
    }
    public void finish(String cancellation,String failure) {
        for (AcquisitionAttemptRecord record : records) {
            if (!cancellation.isEmpty()) record.cancel(cancellation);
            if (!failure.isEmpty()) record.put("execution_error",failure);
            store.submit(record);
        }
        records.clear(); detections.clear();
    }
}
