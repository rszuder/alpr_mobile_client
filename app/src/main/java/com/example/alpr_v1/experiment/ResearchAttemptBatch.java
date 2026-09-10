package com.example.alpr_v1.experiment;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.vision.Detection;
import java.util.*;

/** Passive, frame-local audit of actual MT calls and their downstream MZ work. */
public final class ResearchAttemptBatch {
    private final ResearchSessionStore store;
    private final ContinuityStamp stamp;
    private final String policy;
    private final float zoom;
    private final List<AcquisitionAttemptRecord> records = new ArrayList<>();
    private final Map<Detection,AcquisitionAttemptRecord> detections = new IdentityHashMap<>();
    private final Map<AcquisitionAttemptRecord,Integer> detectionCounts = new IdentityHashMap<>();
    private final Map<AcquisitionAttemptRecord,AcquisitionAttemptRecord> invocations = new IdentityHashMap<>();
    public ResearchAttemptBatch(ResearchSessionStore store,ContinuityStamp stamp,String policy,float zoom) {
        this.store=store; this.stamp=stamp; this.policy=policy; this.zoom=zoom;
    }
    public AcquisitionAttemptRecord beginMt(long entity,long vehicle,int left,int top,int right,int bottom,
                                            int inputWidth,int inputHeight) {
        AcquisitionAttemptRecord record = store.beginInvocationAttempt(stamp,policy,zoom,entity,vehicle);
        if (record != null) {
            record.put("roi_left",left); record.put("roi_top",top); record.put("roi_right",right); record.put("roi_bottom",bottom);
            record.put("input_width",inputWidth); record.put("input_height",inputHeight);
            records.add(record);
            detectionCounts.put(record,0); invocations.put(record,record);
        }
        return record;
    }
    public void detected(AcquisitionAttemptRecord call,Detection detection,boolean validQuad) {
        if (call == null) return;
        int index = detectionCounts.get(call);
        detectionCounts.put(call,index+1);
        AcquisitionAttemptRecord record = call;
        if (index > 0) {
            record = store.beginInvocationAttempt(stamp,policy,zoom,call.data.optLong("entity_id"),call.data.optLong("vehicle_track_id"));
            if (record == null) return;
            for (String key : new String[]{"mt_invocation_id","mt_executed","attempt_started_elapsed_nanos",
                    "roi_left","roi_top","roi_right","roi_bottom","input_width","input_height",
                    "input_scale","input_pad_x","input_pad_y","mt_roi_policy",
                    "primary_plate_region_top_fraction","mt_backend"}) record.put(key,call.data.opt(key));
            record.copyEvidence(call.plateCrop ? call.mtInputImage : call.image);
            if (!record.plateCrop) record.put("evidence_kind",call.data.optString("mt_input_evidence_kind",
                    call.data.optString("evidence_kind")));
            records.add(record); invocations.put(record,call);
        }
        record.put("mt_detection_index",index);
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
            if (record.data.optBoolean("mt_executed"))
                record.put("mt_detection_count",detectionCounts.get(invocations.get(record)));
            if (!cancellation.isEmpty()) record.cancel(cancellation);
            // A later MZ/postprocessing failure must not turn earlier valid MT calls into backend failures.
            if (!failure.isEmpty()) record.put("processing_error",failure);
            store.submit(record);
        }
        records.clear(); detections.clear(); detectionCounts.clear(); invocations.clear();
    }
}
