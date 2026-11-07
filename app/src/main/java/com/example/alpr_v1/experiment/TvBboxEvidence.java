package com.example.alpr_v1.experiment;

import android.graphics.Bitmap;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.inference.RuntimeExecutionEvidence;
import com.example.alpr_v1.setup.SetupJson;
import com.example.alpr_v1.setup.TvBboxCrop;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.Point2;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Optional frame-level input evidence. Uses the existing writer and image reservations. */
public final class TvBboxEvidence {
    public static final String SCHEMA="alpr.tv_bbox_input.v1",CAPABILITY="tv_bbox_crop_v1";
    public static final String INDEX="samples/tv_bbox_inputs.jsonl",JOURNAL="samples/tv_bbox_inputs_journal.jsonl";
    public static final int MAX_FRAMES=4096;
    private final ResearchSessionStore store;
    public final TvBboxCrop snapshot;
    private final Map<String,FrameRecord> frames=new LinkedHashMap<>();
    TvBboxEvidence(ResearchSessionStore store,TvBboxCrop snapshot){this.store=store;this.snapshot=snapshot;}

    public synchronized FrameRecord capture(TvBboxCrop.Frame frame,ContinuityStamp stamp){
        if(frame==null||!snapshot.digest.equals(frame.crop.digest))throw new IllegalArgumentException("TV_BBOX_SNAPSHOT_MISMATCH");
        return capture(frame.camera,frame.input,stamp,frame.cropElapsedNanos,"");
    }
    public synchronized FrameRecord rejected(Bitmap camera,ContinuityStamp stamp,String reason){return capture(camera,null,stamp,0,reason==null?"input_rejected":reason);}
    private FrameRecord capture(Bitmap camera,Bitmap input,ContinuityStamp stamp,long duration,String rejection){
        if(stamp==null)throw new IllegalArgumentException("TV_BBOX_SOURCE_STAMP_REQUIRED");
        JSONObject identity=new JSONObject();put(identity,"source_stamp",F5aEvidence.stampJson(stamp));put(identity,"snapshot",snapshot.digest);
        String key=SetupJson.sha256(identity);FrameRecord previous=frames.get(key);if(previous!=null)return previous;
        if(frames.size()>=MAX_FRAMES)throw new IllegalStateException("TV_BBOX_EVIDENCE_FRAME_LIMIT");
        FrameRecord row=new FrameRecord(key,stamp,camera,input,duration,rejection);frames.put(key,row);
        if(!store.enqueueF5a(row::write))row.queueRejected();
        return row;
    }
    public final class FrameRecord {
        public final String key;
        private final JSONObject row=new JSONObject();
        private final F5aEvidence.Frame full,crop;
        private FrameRecord(String key,ContinuityStamp stamp,Bitmap camera,Bitmap input,long duration,String rejection){
            this.key=key;
            put(row,"schema",SCHEMA);put(row,"session_id",store.sessionId());put(row,"frame_key",key);
            put(row,"source_stamp",F5aEvidence.stampJson(stamp));put(row,"tv_bbox_crop_digest_sha256",snapshot.digest);
            String scene=store.f5aEvidence()==null?"":store.f5aEvidence().sceneFor(stamp);
            put(row,"scene_event_id",scene.isEmpty()?null:scene);put(row,"processing_transform","CROP_ONLY_TRANSLATION");put(row,"H_TV","NOT_APPLIED");
            put(row,"crop_prepare_elapsed_nanos",duration);put(row,"captured_elapsed_nanos",store.elapsedNow());
            put(row,"pipeline_status",rejection.isEmpty()?"PENDING":"REJECTED_GEOMETRY");put(row,"cancel_reason",rejection);
            put(row,"stale_or_cancelled",!rejection.isEmpty());
            put(row,"source_image",TvBboxEvidence.image(camera,TvBboxCrop.CAMERA_SPACE,"camera_bitmap_after_camerax_crop_rotation_before_tv_bbox"));
            put(row,"crop_image",TvBboxEvidence.image(input,TvBboxCrop.LOCAL_SPACE,"crop_only_translation_from_camera_bitmap"));
            full=store.copyTvBboxEvidence(camera,true);
            crop=input==camera?null:store.copyTvBboxEvidence(input,false);
            ready(image("source_image"),full,"samples/frames/tv-camera-"+key+".jpg",camera==null?"source_frame_unavailable":"source_frame_memory_or_copy_limit");
            if(input==camera&&full!=null){
                put(image("crop_image"),"status","CAPTURED");put(image("crop_image"),"entry",image("source_image").opt("entry"));
            }else ready(image("crop_image"),crop,"samples/frames/tv-input-"+key+".jpg",input==null?rejection.isEmpty()?"crop_unavailable":rejection:"image_memory_or_copy_limit");
        }
        private JSONObject image(String name){return row.optJSONObject(name);}
        private void ready(JSONObject image,F5aEvidence.Frame pixels,String entry,String reason){
            if(pixels==null)missing(image,reason);else{put(image,"status","CAPTURED");put(image,"entry",entry);}
        }
        private void queueRejected(){
            if(full!=null)full.release();if(crop!=null)crop.release();
            missing(image("source_image"),"writer_queue_full");missing(image("crop_image"),"writer_queue_full");
        }
        private void write(){
            JSONObject source,input;
            synchronized(this){try{source=new JSONObject(image("source_image").toString());input=new JSONObject(image("crop_image").toString());}catch(Exception e){throw new IllegalStateException(e);}}
            writeImage(source,full);
            if(crop==null&&Objects.equals(input.opt("entry"),source.opt("entry"))&&!input.isNull("entry")){
                for(String k:new String[]{"status","entry","sha256","missing_reason"})put(input,k,source.opt(k));
            }else writeImage(input,crop);
            synchronized(this){put(row,"source_image",source);put(row,"crop_image",input);}
            journal();
        }
        private void writeImage(JSONObject image,F5aEvidence.Frame pixels){
            if(pixels==null)return;
            File target=new File(store.directory(),image.optString("entry")),temporary=new File(target+".tmp");
            long start=store.elapsedNow();
            try {
                pixels.write(temporary);int[] dimensions=F5aEvidence.jpegDimensions(temporary);
                if(dimensions[0]!=pixels.width()||dimensions[1]!=pixels.height())throw new IOException("TV input image dimensions");
                Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
                put(image,"sha256",RuntimeExecutionEvidence.sha256(target));put(image,"status","WRITTEN");put(image,"missing_reason",null);
            }catch(Exception|OutOfMemoryError error){
                missing(image,"image_write_failed");try{Files.deleteIfExists(temporary.toPath());Files.deleteIfExists(target.toPath());}catch(IOException ignored){}
            }finally{pixels.release();put(image,"write_elapsed_nanos",Math.max(0,store.elapsedNow()-start));}
        }
        private void journal(){try{ResearchSessionStore.append(new File(store.directory(),JOURNAL),json().toString()+"\n");}
            catch(Exception error){store.recordTvBboxFailure("tv_bbox_journal_write_failed");}}
        public void complete(String status,String cancellation){
            synchronized(this){put(row,"pipeline_status",status);put(row,"cancel_reason",cancellation);put(row,"stale_or_cancelled",!cancellation.isEmpty());}
            store.enqueueF5a(this::journal);
        }
        public void bind(AcquisitionAttemptRecord record,int left,int top,int right,int bottom){
            if(record==null)return;
            record.tvBboxFrame=this;record.put("tv_bbox_frame_key",key);
            record.put("tv_bbox_roi_local_xy",TvBboxCrop.box(left-snapshot.left,top-snapshot.top,right-snapshot.left,bottom-snapshot.top));
            record.put("tv_bbox_coordinate_space",TvBboxCrop.LOCAL_SPACE);
            attachSource(record);
        }
        synchronized void attachSource(AcquisitionAttemptRecord record){
            JSONObject image=image("source_image");record.put("source_frame_expected",true);
            record.put("source_frame_coordinate_space","mt_source_bitmap_xy");record.put("source_frame_origin","pipeline_bitmap_before_roi");
            record.put("source_frame_width_px",image.opt("width_px"));record.put("source_frame_height_px",image.opt("height_px"));
            record.put("source_frame_entry",image.isNull("entry")?"":image.optString("entry",""));record.put("source_frame_status",image.optString("status","MISSING"));
            record.put("source_frame_missing_evidence_reason",image.optString("missing_reason",""));
        }
        public void localDetection(AcquisitionAttemptRecord record,Detection local){
            if(record==null)return;
            try{
            JSONObject g=new JSONObject();put(g,"coordinate_space",TvBboxCrop.LOCAL_SPACE);
            put(g,"bbox",new JSONArray().put(local.left).put(local.top).put(local.right).put(local.bottom));
            JSONArray points=new JSONArray();for(Point2 p:local.keypoints)points.put(new JSONArray().put(p.x).put(p.y));
            put(g,"corners_px",points);record.put("tv_bbox_local_geometry",g);
            }catch(Exception invalid){record.put("tv_bbox_local_geometry_missing_reason","non_finite_geometry");}
        }
        synchronized JSONObject json(){try{return new JSONObject(row.toString());}catch(Exception e){throw new IllegalStateException(e);}}
    }
    void flushFinal()throws Exception{
        StringBuilder text=new StringBuilder();synchronized(this){for(FrameRecord row:frames.values())text.append(row.json()).append('\n');}
        ResearchSessionStore.atomicText(new File(store.directory(),JOURNAL),text.toString());
    }
    private static JSONObject image(Bitmap image,String space,String origin){
        JSONObject value=new JSONObject();put(value,"encoding","JPEG94");put(value,"exact_model_input",false);
        put(value,"coordinate_space",space);put(value,"origin",origin);put(value,"status","MISSING");
        put(value,"width_px",image==null?null:image.getWidth());put(value,"height_px",image==null?null:image.getHeight());
        put(value,"entry",null);put(value,"sha256",null);put(value,"missing_reason","not_captured");return value;
    }
    private static void missing(JSONObject image,String reason){put(image,"status","MISSING");put(image,"entry",null);put(image,"sha256",null);put(image,"missing_reason",reason);}
    static void put(JSONObject o,String key,Object value){F5aEvidence.put(o,key,value);}
    /** Final/recovery projection; relationships come from actual persisted attempts, never OCR/overlap. */
    static void finalizeStored(File directory,JSONObject session,JSONObject descriptor)throws Exception{
        if(!"TV_BBOX_CROP".equals(session.optString("input_mode")))return;
        TvBboxCrop frozen=TvBboxCrop.fromJson(session.getJSONObject("tv_bbox_crop"));
        Map<String,JSONObject> rows=new LinkedHashMap<>();File journal=new File(directory,JOURNAL);
        if(journal.isFile())for(String line:Files.readAllLines(journal.toPath(),StandardCharsets.UTF_8))if(!line.trim().isEmpty()){
            JSONObject row=new JSONObject(line);rows.put(row.getString("frame_key"),row);
        }
        Map<String,JSONArray> links=new LinkedHashMap<>();File attempts=new File(directory,"samples/attempts.jsonl");
        if(attempts.isFile())for(String line:Files.readAllLines(attempts.toPath(),StandardCharsets.UTF_8))if(!line.trim().isEmpty()){
            JSONObject a=new JSONObject(line);String key=a.optString("tv_bbox_frame_key");if(key.isEmpty())continue;
            JSONObject link=new JSONObject().put("attempt_id",a.getString("attempt_id")).put("mt_invocation_id",a.optString("mt_invocation_id"))
                    .put("mt_executed",a.optBoolean("mt_executed")).put("mt_detection_index",a.opt("mt_detection_index"))
                    .put("roi_local_xy",a.opt("tv_bbox_roi_local_xy")).put("local_geometry",a.opt("tv_bbox_local_geometry"))
                    .put("role_index_file","samples/mt_detection_roles.jsonl");
            links.computeIfAbsent(key,k->new JSONArray()).put(link);
            if(!rows.containsKey(key)){
                JSONObject recovered=new JSONObject().put("schema",SCHEMA).put("session_id",session.getString("session_id")).put("frame_key",key)
                        .put("source_stamp",F5aEvidence.stampJson(new com.example.alpr_v1.continuity.ContinuityStamp(
                                a.getLong("scene_generation"),a.getLong("visual_epoch"),a.getLong("camera_transform_generation"),
                                a.getLong("source_sequence"),a.getLong("source_timestamp_nanos"),com.example.alpr_v1.continuity.SourceTimestampDomain.valueOf(a.getString("source_timestamp_domain")))))
                        .put("tv_bbox_crop_digest_sha256",frozen.digest).put("scene_event_id",a.opt("f5a_scene_event_id"))
                        .put("processing_transform","CROP_ONLY_TRANSLATION").put("H_TV","NOT_APPLIED").put("pipeline_status","WRITE_NOT_RECORDED")
                        .put("source_image",image(null,TvBboxCrop.CAMERA_SPACE,"camera_bitmap_after_camerax_crop_rotation_before_tv_bbox"))
                        .put("crop_image",image(null,TvBboxCrop.LOCAL_SPACE,"crop_only_translation_from_camera_bitmap"));
                rows.put(key,recovered);
            }
        }
        int written=0,missing=0;boolean pending=false;StringBuilder text=new StringBuilder();
        for(Map.Entry<String,JSONObject> entry:rows.entrySet()){
            JSONObject row=entry.getValue();pending|="PENDING".equals(row.optString("pipeline_status"));row.put("attempt_links",links.getOrDefault(entry.getKey(),new JSONArray()));
            for(String name:new String[]{"source_image","crop_image"}){
                JSONObject image=row.getJSONObject(name);String path=image.optString("entry","");File file=new File(directory,path);
                if("WRITTEN".equals(image.optString("status"))&&file.isFile()&&RuntimeExecutionEvidence.sha256(file).equals(image.optString("sha256")))written++;
                else{String reason=image.optString("missing_reason","");
                    missing(image,"CAPTURED".equals(image.optString("status"))?"process_interrupted":reason.isEmpty()||"null".equals(reason)?"write_not_recorded":reason);missing++;}
            }
            text.append(row).append('\n');
        }
        ResearchSessionStore.atomicText(new File(directory,INDEX),text.toString());
        descriptor.getJSONObject("capabilities").put(CAPABILITY,true);descriptor.put("tv_bbox_inputs_file",INDEX);
        boolean unknown=session.optBoolean("tv_bbox_inputs_pending",true)||pending;
        session.put("tv_bbox_input_evidence",new JSONObject().put("schema",SCHEMA).put("frames",rows.size())
                .put("images_written",written).put("images_missing",missing).put("pending_loss_unknown",unknown)
                .put("complete",!unknown&&missing==0&&rows.size()>0).put("desktop_contract_status",com.example.alpr_v1.setup.ResearchInputMode.TV_BBOX_DESKTOP_CONTRACT_STATUS));
    }
}
