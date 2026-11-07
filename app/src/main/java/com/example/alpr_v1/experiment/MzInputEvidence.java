package com.example.alpr_v1.experiment;

import com.example.alpr_v1.inference.TensorInfo;
import com.example.alpr_v1.inference.InferenceBackend;
import com.example.alpr_v1.inference.RuntimeExecutionEvidence;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.vision.PreparedInput;
import com.example.alpr_v1.vision.Point2;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.concurrent.atomic.AtomicLong;

/** Optional passive evidence. Production remains OFF pending Desktop contract acceptance. */
public final class MzInputEvidence {
    public static final String SCHEMA="alpr.mz_input_evidence.v1";
    public static final String CAPABILITY="mz_input_evidence_v1";
    public static final String INDEX="samples/mz_input_evidence.jsonl";
    public static final int MAX_TENSOR_BYTES=8*1024*1024, MAX_OUTSTANDING=2, MAX_PAYLOADS=16;
    public static final long MAX_MEMORY_BYTES=16L*1024*1024, MAX_SESSION_BYTES=64L*1024*1024;
    public static final String[] STAMP={"scene_generation","visual_epoch","camera_transform_generation",
            "source_sequence","source_timestamp_nanos","source_timestamp_domain"};
    private final ResearchSessionStore store;
    private final LongSupplier clock;
    private int outstanding, reservedPayloads;
    private long memory, reservedDisk;
    private final AtomicLong captureCost=new AtomicLong(),payloadCost=new AtomicLong(),indexCost=new AtomicLong();
    MzInputEvidence(ResearchSessionStore store,LongSupplier clock){this.store=store;this.clock=clock;}
    // Desktop F5A/17 accepts synthetic evidence only; real device purpose is not approved.
    public static final String DIAGNOSTIC_BLOCK_REASON="BLOCKED_DESKTOP_PURPOSE";
    public static void requireDiagnosticStartAllowed(){throw new IllegalStateException(DIAGNOSTIC_BLOCK_REASON);}
    public static boolean productionEnabled(){return false;}
    static void put(JSONObject row,String key,Object value){
        try{row.put(key,value==null?JSONObject.NULL:value);}catch(JSONException ignored){/* independent diagnostic failure */}
    }
    static JSONObject stamp(JSONObject attempt){JSONObject row=new JSONObject();for(String key:STAMP)put(row,key,attempt.opt(key));return row;}
    public synchronized long outstandingBytes(){return memory;}
    public synchronized int outstandingCaptures(){return outstanding;}
    private synchronized String reserve(int bytes){
        if(bytes<=0||bytes>MAX_TENSOR_BYTES)return "TENSOR_SIZE_LIMIT";
        if(outstanding>=MAX_OUTSTANDING||memory+bytes>MAX_MEMORY_BYTES)return "MEMORY_OR_QUEUE_LIMIT";
        if(reservedPayloads>=MAX_PAYLOADS||reservedDisk+bytes>MAX_SESSION_BYTES)return "SESSION_PAYLOAD_LIMIT";
        outstanding++;memory+=bytes;reservedPayloads++;reservedDisk+=bytes;return "";
    }
    private synchronized void release(Capture capture,boolean unentered){
        if(!capture.reserved)return;
        capture.reserved=false;outstanding--;memory-=capture.byteSize;
        if(unentered){reservedPayloads--;reservedDisk-=capture.byteSize;}
        capture.bytes=null;
    }
    public final class Capture {
        final AcquisitionAttemptRecord attempt;
        final JSONObject row=new JSONObject();
        byte[] bytes;
        int byteSize, callIndex;
        boolean reserved, entered, journaled;
        String outcome="ENTERED",errorType="";
        Capture(AcquisitionAttemptRecord attempt){this.attempt=attempt;}
        /** Only called at actual backend entry, never on scheduling/preprocessing. */
        public void started(){
            if(entered)return;entered=true;
            callIndex=attempt.mzInputCallCount++;
        }
        public void returned(){if(entered)outcome="RETURNED";}
        public void threw(Throwable error){if(entered){outcome="THREW";errorType=error.getClass().getSimpleName();}}
        public void discardIfNotStarted(){if(!entered){missing(row,"CANCELLED_BEFORE_CALL");release(this,true);}}
    }
    Capture prepare(AcquisitionAttemptRecord attempt,PreparedInput input,ModelInputSpec spec,TensorInfo info,
                    List<Point2> recognitionCorners,String modelId,String variantId,String fingerprint,InferenceBackend backend){
        long started=clock.getAsLong();Capture c=new Capture(attempt);
        try{
            JSONObject tensor=new JSONObject();
            put(tensor,"shape",new JSONArray(info.shape));put(tensor,"dtype",info.dataType);
            put(tensor,"layout",spec.layout());put(tensor,"color",spec.colorSpace());put(tensor,"byte_size",info.byteSize);
            put(tensor,"normalization_scale",spec.scale());put(tensor,"normalization_offset",spec.offset());
            put(tensor,"quantization_scale",info.quantizationScale);put(tensor,"quantization_zero_point",info.quantizationZeroPoint);
            put(tensor,"byte_order",input.buffer.order().toString());
            put(tensor,"original_position",input.buffer.position());put(tensor,"original_limit",input.buffer.limit());
            put(tensor,"original_capacity",input.buffer.capacity());put(tensor,"range_start",0);put(tensor,"range_end",info.byteSize);
            put(tensor,"entry",null);put(tensor,"sha256",null);put(tensor,"write_status","CAPTURED");put(tensor,"missing_reason",null);
            put(c.row,"tensor",tensor);
            JSONObject observed=backend.executionEvidence();
            Object modelSha=observed==null?JSONObject.NULL:observed.opt("model_sha256");
            put(c.row,"model",new JSONObject().put("model_id",modelId).put("variant_id",variantId)
                    .put("model_fingerprint",fingerprint).put("backend",backend.runtimeName())
                    .put("artifact_sha256",modelSha==null?JSONObject.NULL:modelSha)
                    .put("artifact_sha256_status",modelSha==null||modelSha==JSONObject.NULL?"NOT_AVAILABLE":"OBSERVED_RUNTIME_METADATA"));
            put(c.row,"crop_width",input.sourceWidth);put(c.row,"crop_height",input.sourceHeight);
            put(c.row,"letterbox",new JSONObject().put("scale",input.scale).put("pad_x",input.padX).put("pad_y",input.padY)
                    .put("padding_rgb",114).put("interpolation","bilinear"));
            JSONArray corners=new JSONArray();for(Point2 point:recognitionCorners)corners.put(new JSONArray().put(point.x).put(point.y));
            put(c.row,"recognition_corners_source_px",corners);
            put(c.row,"recognition_corner_order","AS_PASSED_TO_PLATE_RECTIFIER");
            put(c.row,"homography",new JSONObject().put("status","NOT_AVAILABLE")
                    .put("reason","RECTIFIER_TRANSFORM_NOT_EXPOSED").put("H",JSONObject.NULL).put("H_inverse",JSONObject.NULL));
            c.byteSize=info.byteSize;
            String reason=input.buffer.limit()!=info.byteSize||input.buffer.capacity()!=info.byteSize
                    ||!input.buffer.isDirect()||input.buffer.order()!=ByteOrder.nativeOrder()?"BUFFER_CONTRACT_UNAVAILABLE":reserve(c.byteSize);
            if(reason.isEmpty()){
                c.reserved=true;
                c.bytes=new byte[c.byteSize];
                ByteBuffer view=input.buffer.duplicate();view.position(0);view.limit(c.byteSize);view.get(c.bytes);
            }else missing(c.row,reason);
        }catch(Exception|OutOfMemoryError error){release(c,true);missing(c.row,"CAPTURE_FAILED");}
        long elapsed=Math.max(0,clock.getAsLong()-started);captureCost.addAndGet(elapsed);
        put(c.row,"capture_elapsed_nanos",elapsed);
        put(c.row,"captured_elapsed_nanos",clock.getAsLong());
        if(attempt.mzInputs==null)attempt.mzInputs=new ArrayList<>();attempt.mzInputs.add(c);
        return c;
    }
    static void missing(JSONObject row,String reason){
        JSONObject tensor=row.optJSONObject("tensor");if(tensor==null){tensor=new JSONObject();put(row,"tensor",tensor);}
        put(tensor,"write_status","MISSING");put(tensor,"missing_reason",reason);put(tensor,"entry",null);put(tensor,"sha256",null);
    }
    void release(AcquisitionAttemptRecord attempt,String reason){
        if(attempt.mzInputs!=null)for(Capture c:attempt.mzInputs){
            if(!c.journaled)missing(c.row,reason);release(c,!c.entered);
        }
    }
    void submitted(AcquisitionAttemptRecord attempt){
        attempt.put("mz_input_call_count",attempt.mzInputCallCount);
        if(attempt.mzInputs!=null)for(Capture c:attempt.mzInputs)put(c.row,"queued_elapsed_nanos",clock.getAsLong());
    }
    void writeAttempt(AcquisitionAttemptRecord attempt){
        if(attempt.mzInputs==null)return;
        for(Capture c:attempt.mzInputs){
            if(c.journaled)continue;
            long started=clock.getAsLong();
            try{
                context(c.row,attempt.data,store.directory());
                put(c.row,"call_index",c.entered?c.callIndex:null);
                put(c.row,"invocation_status",c.entered?c.outcome:"NOT_RUN");
                if(!c.errorType.isEmpty())put(c.row,"backend_error_type",c.errorType);
                if(c.entered && c.bytes!=null){
                    String entry="samples/mz_inputs/"+attempt.attemptId+"/"+c.callIndex+".bin";
                    File target=new File(store.directory(),entry),temp=new File(target+".tmp");
                    Files.createDirectories(target.getParentFile().toPath());
                    put(c.row,"queue_elapsed_nanos",Math.max(0,started-c.row.optLong("queued_elapsed_nanos",started)));
                    Files.write(temp.toPath(),c.bytes);
                    String hash=RuntimeExecutionEvidence.sha256(temp);
                    Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
                    JSONObject tensor=c.row.getJSONObject("tensor");put(tensor,"entry",entry);put(tensor,"sha256",hash);
                    put(tensor,"write_status","WRITTEN");put(tensor,"missing_reason",null);
                }else if(!c.entered){
                    put(c.row,"call_index",null);put(c.row,"invocation_status","NOT_RUN");missing(c.row,"CANCELLED_BEFORE_CALL");
                }
            }catch(Exception error){missing(c.row,"PAYLOAD_WRITE_FAILED");}
            finally{
                long elapsed=Math.max(0,clock.getAsLong()-started);payloadCost.addAndGet(elapsed);put(c.row,"write_elapsed_nanos",elapsed);
                long indexStarted=clock.getAsLong();
                try{ResearchSessionStore.append(new File(store.directory(),INDEX),c.row.toString()+"\n");c.journaled=true;}
                catch(Exception error){attempt.put("mz_input_evidence_index_error","INDEX_WRITE_FAILED");}
                finally{indexCost.addAndGet(Math.max(0,clock.getAsLong()-indexStarted));}
                release(c,!c.entered);
            }
        }
    }
    JSONObject costs(){
        JSONObject row=new JSONObject();put(row,"capture_total_elapsed_nanos",captureCost.get());
        put(row,"payload_write_total_elapsed_nanos",payloadCost.get());put(row,"index_append_total_elapsed_nanos",indexCost.get());
        put(row,"scope","DIAGNOSTIC_ONLY_NOT_MODEL_INFERENCE");return row;
    }
    static void context(JSONObject row,JSONObject attempt,File directory)throws Exception{
        put(row,"schema",SCHEMA);put(row,"session_id",attempt.get("session_id"));put(row,"attempt_id",attempt.get("attempt_id"));
        put(row,"mt_invocation_id",attempt.opt("mt_invocation_id"));put(row,"source_stamp",stamp(attempt));
        put(row,"stale_or_cancelled",attempt.optBoolean("stale_or_cancelled"));put(row,"cancel_reason",attempt.optString("cancel_reason"));
        put(row,"mt_status",attempt.optString("mt_status"));put(row,"mz_status",attempt.optString("mz_status"));
        JSONObject roi=new JSONObject();for(String key:new String[]{"left","top","right","bottom"})put(roi,key,attempt.opt("roi_"+key));
        put(row,"roi",roi);put(row,"roi_policy",attempt.opt("roi_policy"));
        JSONObject pose=new JSONObject();for(String key:new String[]{"status","corners_norm","source_width_px","source_height_px","coordinate_space","corner_order"})put(pose,key,attempt.opt("mt_pose_"+key));
        put(row,"mt_pose",pose);put(row,"mt_detection_index",attempt.opt("mt_detection_index"));
        JSONObject bbox=new JSONObject();for(String key:new String[]{"left","top","right","bottom"})put(bbox,key,attempt.opt("plate_"+key));put(row,"mt_bbox",bbox);
        put(row,"source_image",image(directory,attempt.optString("source_frame_entry"),"JPEG",attempt.optString("source_frame_missing_evidence_reason")));
        JSONObject source=row.getJSONObject("source_image");put(source,"width_px",attempt.opt("source_frame_width_px"));
        put(source,"height_px",attempt.opt("source_frame_height_px"));put(source,"coordinate_space",attempt.opt("source_frame_coordinate_space"));
        put(source,"origin",attempt.opt("source_frame_origin"));
        put(row,"rectified_crop",image(directory,"plate_crop".equals(attempt.optString("evidence_kind"))?attempt.optString("evidence_entry"):"","JPEG","CROP_NOT_RECORDED"));
    }
    private static JSONObject image(File directory,String entry,String encoding,String missing)throws Exception{
        JSONObject row=new JSONObject().put("encoding",encoding).put("exact_tensor_input",false);
        File file=new File(directory,entry);
        if(!entry.isEmpty()&&file.isFile())return row.put("status","WRITTEN").put("entry",entry)
                .put("sha256",RuntimeExecutionEvidence.sha256(file)).put("missing_reason",JSONObject.NULL);
        return row.put("status","MISSING").put("entry",JSONObject.NULL).put("sha256",JSONObject.NULL)
                .put("missing_reason",missing.isEmpty()?"SOURCE_NOT_RECORDED":missing);
    }
    /** Reconcile only proven call counts. Lost in-flight work remains unknown, never a fabricated invocation. */
    static void finalizeStored(File directory,JSONObject session,JSONObject schema)throws Exception{
        if(!session.optBoolean("mz_input_evidence_enabled"))return;
        Map<String,JSONObject> rows=new LinkedHashMap<>();boolean damaged=false;
        File index=new File(directory,INDEX);
        if(index.isFile())try(BufferedReader in=Files.newBufferedReader(index.toPath(),StandardCharsets.UTF_8)){
            String line;while((line=in.readLine())!=null)if(!line.trim().isEmpty())try{
                JSONObject row=new JSONObject(line);String key=row.getString("attempt_id")+":"+row.opt("call_index");
                if(rows.putIfAbsent(key,row)!=null)damaged=true;
            }catch(Exception error){damaged=true;}
        }
        StringBuilder text=new StringBuilder();int expected=0,written=0,missing=0,notRun=0;
        File attempts=new File(directory,"samples/attempts.jsonl");
        try(BufferedReader in=Files.newBufferedReader(attempts.toPath(),StandardCharsets.UTF_8)){
            String line;while((line=in.readLine())!=null)if(!line.trim().isEmpty()){
                JSONObject attempt=new JSONObject(line);int count=attempt.optInt("mz_input_call_count",0);
                if(count==0 && attempt.optBoolean("mz_executed"))damaged=true;
                int records=Math.max(1,count);
                for(int i=0;i<records;i++){
                    String key=attempt.getString("attempt_id")+":"+(count==0?"null":i);
                    JSONObject row=rows.remove(key);
                    if(row==null){row=new JSONObject();context(row,attempt,directory);put(row,"call_index",count==0?null:i);
                        put(row,"invocation_status",count==0?"NOT_RUN":"ENTERED");missing(row,count==0?"MZ_NOT_RUN":"WRITE_NOT_RECORDED");}
                    JSONObject tensor=row.getJSONObject("tensor");
                    if(count==0){notRun++;put(row,"invocation_status","NOT_RUN");put(row,"call_index",null);}
                    else{
                        expected++;
                        String entry=tensor.optString("entry","");File payload=new File(directory,entry);
                        if("WRITTEN".equals(tensor.optString("write_status"))){
                            if(!entry.equals("samples/mz_inputs/"+attempt.getString("attempt_id")+"/"+i+".bin")||!payload.isFile()||payload.length()!=tensor.optLong("byte_size",-1)
                                    ||!RuntimeExecutionEvidence.sha256(payload).equals(tensor.optString("sha256")))missing(row,"PAYLOAD_INTEGRITY_FAILURE");
                        }
                        if("WRITTEN".equals(tensor.optString("write_status")))written++;else missing++;
                    }
                    text.append(row).append('\n');
                }
            }
        }
        if(!rows.isEmpty())damaged=true;
        ResearchSessionStore.atomicText(index,text.toString());
        schema.getJSONObject("capabilities").put(CAPABILITY,true);schema.put("mz_input_evidence_file",INDEX);
        boolean unknown=damaged||session.optBoolean("pending_sample_loss_unknown");
        session.put("mz_input_evidence",new JSONObject().put("schema",SCHEMA).put("purpose","SYNTHETIC_DIAGNOSTIC")
                .put("expected_calls",expected).put("written",written).put("missing",missing).put("not_run_attempts",notRun)
                .put("pending_loss_unknown",unknown).put("complete",expected>0&&missing==0&&!unknown)
                .put("limits",new JSONObject().put("tensor_bytes",MAX_TENSOR_BYTES).put("memory_bytes",MAX_MEMORY_BYTES)
                        .put("outstanding_captures",MAX_OUTSTANDING).put("payloads",MAX_PAYLOADS).put("session_bytes",MAX_SESSION_BYTES))
                .put("desktop_contract_status","BLOCKED_DESKTOP_CONTRACT"));
    }
}
