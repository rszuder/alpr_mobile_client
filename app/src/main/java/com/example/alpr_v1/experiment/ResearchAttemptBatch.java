package com.example.alpr_v1.experiment;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.vision.Detection;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Passive, frame-local audit of actual MT calls and their downstream MZ work. */
public final class ResearchAttemptBatch {
    private final ResearchSessionStore store;
    private final ContinuityStamp stamp;
    private final String policy;
    private final float zoom;
    private final long traceFrameId;
    private final String sceneEventId;
    private final Map<AcquisitionAttemptRecord,Boolean> decoded = new IdentityHashMap<>();
    private final Map<Detection,String> postprocessRoles = new IdentityHashMap<>();
    private boolean postprocessReached, postprocessBypassed;
    private final List<AcquisitionAttemptRecord> records = new ArrayList<>();
    private final Map<Detection,AcquisitionAttemptRecord> detections = new IdentityHashMap<>();
    private final Map<AcquisitionAttemptRecord,Integer> detectionCounts = new IdentityHashMap<>();
    private final Map<AcquisitionAttemptRecord,AcquisitionAttemptRecord> invocations = new IdentityHashMap<>();
    public ResearchAttemptBatch(ResearchSessionStore store,ContinuityStamp stamp,String policy,float zoom) {
        this(store,stamp,policy,zoom,0L);
    }
    public ResearchAttemptBatch(ResearchSessionStore store,ContinuityStamp stamp,String policy,float zoom,long traceFrameId) {
        this.store=store; this.stamp=stamp; this.policy=policy; this.zoom=zoom;
        this.traceFrameId=traceFrameId;
        this.sceneEventId=store.f5aEvidence()==null ? "" : store.f5aEvidence().sceneFor(stamp);
    }
    public AcquisitionAttemptRecord beginMt(long entity,long vehicle,int left,int top,int right,int bottom,
                                            int inputWidth,int inputHeight) {
        AcquisitionAttemptRecord record = store.beginInvocationAttempt(stamp,policy,zoom,entity,vehicle);
        if (record != null) {
            record.put("roi_left",left); record.put("roi_top",top); record.put("roi_right",right); record.put("roi_bottom",bottom);
            record.put("input_width",inputWidth); record.put("input_height",inputHeight);
            records.add(record);
            detectionCounts.put(record,0); invocations.put(record,record);
            decoded.put(record,false);
            if(store.f5aEvidence()!=null)record.put("f5a_scene_event_id",sceneEventId);
        }
        return record;
    }
    /** Historical invocation for isolated callers without frame dimensions. */
    public void detected(AcquisitionAttemptRecord call,Detection detection,boolean validQuad) {
        detected(call,detection,validQuad,0,0);
    }
    /** Source-frame Pose belongs to this MT detection, never to a later crop. */
    public void detected(AcquisitionAttemptRecord call,Detection detection,boolean validQuad,
                         int sourceWidth,int sourceHeight) {
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
                    "primary_plate_region_top_fraction","mt_backend",
                    "source_frame_expected","source_frame_entry","source_frame_status",
                    "source_frame_missing_evidence_reason","source_frame_width_px",
                    "source_frame_height_px","source_frame_coordinate_space",
                    "source_frame_origin","source_frame_capture_elapsed_nanos"}) record.put(key,call.data.opt(key));
            if(call.inputPayloadFactory!=null)record.inputPayload=call.inputPayloadFactory.get();
            else record.copyEvidence(call.plateCrop ? call.mtInputImage : call.image);
            if (!record.plateCrop) record.put("evidence_kind",call.data.optString("mt_input_evidence_kind",
                    call.data.optString("evidence_kind")));
            record.tvBboxFrame=call.tvBboxFrame;
            if(call.data.has("tv_bbox_frame_key")){
                record.put("tv_bbox_frame_key",call.data.opt("tv_bbox_frame_key"));
                record.put("tv_bbox_roi_local_xy",call.data.opt("tv_bbox_roi_local_xy"));
                record.put("tv_bbox_coordinate_space",call.data.opt("tv_bbox_coordinate_space"));
            }
            records.add(record); invocations.put(record,call);
        }
        record.put("mt_detection_index",index);
        record.put("mt_status",validQuad ? "VALID_QUAD" : "DETECTION_INVALID_QUAD");
        record.put("plate_confidence",detection.confidence);
        record.put("plate_left",detection.left); record.put("plate_top",detection.top);
        record.put("plate_right",detection.right); record.put("plate_bottom",detection.bottom);
        MtPoseGeometryEncoder.Result pose = MtPoseGeometryEncoder.encode(
                detection,validQuad,sourceWidth,sourceHeight);
        record.put("mt_pose_status",pose.status);
        record.put("mt_pose_corners_norm",pose.cornersNorm);
        record.put("mt_pose_source_width_px",sourceWidth);
        record.put("mt_pose_source_height_px",sourceHeight);
        record.put("mt_pose_coordinate_space",MtPoseGeometryEncoder.COORDINATE_SPACE);
        record.put("mt_pose_corner_order",MtPoseGeometryEncoder.CORNER_ORDER);
        detections.put(detection,record);
    }
    public AcquisitionAttemptRecord forDetection(Detection detection) { return detections.get(detection); }
    public void decoded(AcquisitionAttemptRecord call) { if(call!=null)decoded.put(call,true); }
    public void rejectedBeforeNms(Detection detection) {
        if(store.f5aEvidence()!=null)postprocessRoles.put(detection,"INSUFFICIENT_KEYPOINTS");
    }
    /** Observe the result of the ONE existing deduplication; never execute or replay it. */
    public void postprocessed(List<Detection> inputs,List<Detection> retained,boolean bypassed) {
        if(store.f5aEvidence()==null)return;
        Set<Detection> kept=Collections.newSetFromMap(new IdentityHashMap<>());kept.addAll(retained);
        for(Detection detection:inputs)postprocessRoles.put(detection,kept.contains(detection) ? "FINAL" : "SUPPRESSED");
        postprocessReached=true;postprocessBypassed=bypassed;
    }
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
        }
        if(store.f5aEvidence()!=null)persistRoles(cancellation,failure);
        for(AcquisitionAttemptRecord record:records)store.submit(record);
        records.clear(); detections.clear(); detectionCounts.clear(); invocations.clear();
        decoded.clear();postprocessRoles.clear();
    }
    private void persistRoles(String cancellation,String failure) {
        for(AcquisitionAttemptRecord call:records) {
            if(invocations.get(call)!=call || !call.data.optBoolean("mt_executed"))continue;
            JSONArray raw=new JSONArray(),finals=new JSONArray(),suppressed=new JSONArray(),rejected=new JSONArray(),unresolved=new JSONArray();
            // Decoder order is the existing detection index/attempt order, not a new geometric match.
            for(AcquisitionAttemptRecord record:records)if(invocations.get(record)==call && !record.data.isNull("mt_detection_index")) {
                raw.put(record.attemptId);String role="";
                for(Map.Entry<Detection,AcquisitionAttemptRecord> entry:detections.entrySet())
                    if(entry.getValue()==record) { role=postprocessRoles.getOrDefault(entry.getKey(),"");break; }
                if("FINAL".equals(role))finals.put(record.attemptId);
                else if("SUPPRESSED".equals(role))suppressed.put(record.attemptId);
                else if("INSUFFICIENT_KEYPOINTS".equals(role)) {
                    JSONObject item=new JSONObject();F5aEvidence.put(item,"attempt_id",record.attemptId);
                    F5aEvidence.put(item,"reason",role);rejected.put(item);
                } else unresolved.put(record.attemptId);
            }
            JSONObject row=new JSONObject();
            F5aEvidence.put(row,"schema","alpr.mt_detection_roles.v1");F5aEvidence.put(row,"session_id",call.sessionId);
            F5aEvidence.put(row,"scene_event_id",sceneEventId.isEmpty() ? null : sceneEventId);
            F5aEvidence.put(row,"mt_invocation_id",call.mtInvocationId());F5aEvidence.put(row,"source_stamp",F5aEvidence.stampJson(stamp));
            F5aEvidence.put(row,"trace_frame_id",traceFrameId);
            String source=call.data.optString("source_frame_entry");F5aEvidence.put(row,"source_frame_entry",source.isEmpty() ? null : source);
            JSONArray roi=new JSONArray();for(String key:new String[]{"roi_left","roi_top","roi_right","roi_bottom"})roi.put(call.data.optInt(key));
            F5aEvidence.put(row,"roi",roi);F5aEvidence.put(row,"postprocess_scope_id",call.sessionId+"/frame-"+traceFrameId);
            F5aEvidence.put(row,"raw_scope","DECODER_OUTPUT");F5aEvidence.put(row,"role_scope","MERGED_MT_DEDUP");
            F5aEvidence.put(row,"raw_attempt_ids",Boolean.TRUE.equals(decoded.get(call)) ? raw : null);
            F5aEvidence.put(row,"final_attempt_ids",finals);F5aEvidence.put(row,"suppressed_attempt_ids",suppressed);
            F5aEvidence.put(row,"rejected_before_nms",rejected);F5aEvidence.put(row,"unresolved_attempt_ids",unresolved);
            F5aEvidence.put(row,"postprocess_status",!postprocessReached ? "NOT_REACHED" : postprocessBypassed ? "BYPASSED" : "COMPLETED");
            F5aEvidence.put(row,"role_proof_status",!sceneEventId.isEmpty() && postprocessReached && Boolean.TRUE.equals(decoded.get(call))
                    && unresolved.length()==0 ? "COMPLETE" : "INCOMPLETE");
            F5aEvidence.put(row,"stale_or_cancelled",!cancellation.isEmpty());F5aEvidence.put(row,"cancel_reason",cancellation);
            F5aEvidence.put(row,"execution_error",call.data.optString("execution_error"));F5aEvidence.put(row,"processing_error",failure);
            store.f5aEvidence().roles(row);
        }
    }
}
