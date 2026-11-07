package com.example.alpr_v1.experiment;

import com.example.alpr_v1.metrics.ResearchArchive;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Strict reader for the optional Android producer contract, not a Desktop adapter. */
public final class MzInputEvidenceValidator {
    private MzInputEvidenceValidator(){}
    public static String validate(File archive)throws Exception{
        ResearchArchive.verifyEntryHashes(archive);
        try(ZipFile zip=new ZipFile(archive)){
            Set<String> names=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                String n=entries.nextElement().getName();require(names.add(n),"Duplicate ZIP entry");
                require(!n.startsWith("/")&&!n.contains("\\")&&!Arrays.asList(n.split("/")).contains(".."),"Unsafe ZIP path");
            }
            if(zip.getEntry("samples/schema.json")==null)return "ABSENT";
            JSONObject descriptor=new JSONObject(read(zip,"samples/schema.json",4*1024*1024));
            JSONObject caps=descriptor.optJSONObject("capabilities");
            if(caps==null||!caps.optBoolean(MzInputEvidence.CAPABILITY))return "ABSENT";
            require(MzInputEvidence.INDEX.equals(descriptor.getString("mz_input_evidence_file")),"Evidence index path");
            JSONObject session=new JSONObject(read(zip,"session.json",4*1024*1024));
            validateDiagnosticPurpose(zip,session);
            Map<String,JSONObject> attempts=new LinkedHashMap<>();
            for(JSONObject row:rows(read(zip,"samples/attempts.jsonl",32*1024*1024)))
                require(attempts.put(row.getString("attempt_id"),row)==null,"Duplicate attempt");
            Set<String> keys=new HashSet<>();int expected=0,written=0,missing=0,notRun=0;long writtenBytes=0;
            for(JSONObject row:rows(read(zip,MzInputEvidence.INDEX,32*1024*1024))){
                require(MzInputEvidence.SCHEMA.equals(row.getString("schema")),"Schema");
                require(session.getString("session_id").equals(row.getString("session_id")),"Session identity");
                JSONObject attempt=attempts.get(row.getString("attempt_id"));require(attempt!=null,"Unknown attempt");
                require(attempt.optString("mt_invocation_id").equals(row.getString("mt_invocation_id")),"Unknown MT invocation");
                require(attempt.optBoolean("stale_or_cancelled")==row.getBoolean("stale_or_cancelled")
                        &&attempt.optString("cancel_reason").equals(row.getString("cancel_reason")),"Cancellation mismatch");
                JSONObject stamp=row.getJSONObject("source_stamp");
                for(String key:MzInputEvidence.STAMP)require(Objects.equals(attempt.opt(key),stamp.opt(key)),"SourceStamp mismatch: "+key);
                JSONObject roi=row.getJSONObject("roi");for(String k:new String[]{"left","top","right","bottom"})
                    require(same(attempt.opt("roi_"+k),roi.opt(k)),"ROI mismatch");
                require(same(attempt.opt("mt_detection_index"),row.opt("mt_detection_index")),"Detection index mismatch");
                JSONObject pose=row.getJSONObject("mt_pose");
                for(String k:new String[]{"status","corners_norm","source_width_px","source_height_px","coordinate_space","corner_order"})
                    require(same(attempt.opt("mt_pose_"+k),pose.opt(k)),"MT Pose mismatch");
                JSONObject tensor=row.getJSONObject("tensor");
                String key=attempt.getString("attempt_id")+":"+row.opt("call_index");require(keys.add(key),"Duplicate evidence record");
                if(row.isNull("call_index")){
                    require(!attempt.optBoolean("mz_executed")&&attempt.optInt("mz_input_call_count",0)==0,"NOT_RUN contradicts invocation");
                    require("NOT_RUN".equals(row.getString("invocation_status")),"NOT_RUN status");notRun++;
                    require(!"WRITTEN".equals(tensor.getString("write_status")),"Unexecuted tensor claimed as input");
                }else{
                    int call=row.getInt("call_index");require(call>=0&&call<attempt.optInt("mz_input_call_count",0)
                            &&attempt.getBoolean("mz_executed")&&attempt.getBoolean("mt_executed"),"Unknown MZ call");
                    require(Arrays.asList("ENTERED","RETURNED","THREW").contains(row.getString("invocation_status")),"Invocation status");
                    expected++;
                    if("WRITTEN".equals(tensor.getString("write_status"))){
                        String name="samples/mz_inputs/"+attempt.getString("attempt_id")+"/"+call+".bin";
                        require(name.equals(tensor.getString("entry")),"Tensor path");
                        int bytes=tensor.getInt("byte_size");require(bytes>0&&bytes<=MzInputEvidence.MAX_TENSOR_BYTES,"Tensor byte budget");
                        writtenBytes+=bytes;require(writtenBytes<=MzInputEvidence.MAX_SESSION_BYTES,"Session tensor byte budget");
                        String dtype=tensor.getString("dtype");int scalar="FLOAT32".equals(dtype)?4:
                                Arrays.asList("UINT8","INT8").contains(dtype)?1:0;require(scalar!=0,"Tensor dtype");
                        JSONArray shape=tensor.getJSONArray("shape");require(shape.length()==4&&shape.getInt(0)==1,"Tensor shape");
                        long elements=1;for(int i=0;i<shape.length();i++){
                            long d=shape.getLong(i);require(d>0&&shape.getDouble(i)==d,"Tensor dimension");elements=Math.multiplyExact(elements,d);
                        }
                        require(Math.multiplyExact(elements,scalar)==bytes,"Tensor shape / dtype byte count");
                        String layout=tensor.getString("layout");require(Arrays.asList("NHWC","NCHW").contains(layout),"Tensor layout");
                        require(shape.getInt("NHWC".equals(layout)?3:1)==3,"Tensor channels");
                        require(Arrays.asList("RGB","BGR").contains(tensor.getString("color")),"Tensor color");
                        require(Arrays.asList("LITTLE_ENDIAN","BIG_ENDIAN").contains(tensor.getString("byte_order")),"Byte order");
                        require(tensor.getInt("range_start")==0&&tensor.getInt("range_end")==bytes
                                &&tensor.getInt("original_limit")==bytes&&tensor.getInt("original_capacity")==bytes,"Tensor buffer range");
                        require(Double.isFinite(tensor.getDouble("normalization_scale"))&&Double.isFinite(tensor.getDouble("normalization_offset")),"Normalization");
                        byte[] data=bytes(zip,name,bytes);require(data.length==bytes,"Payload length");
                        require(hex(MessageDigest.getInstance("SHA-256").digest(data)).equals(tensor.getString("sha256")),"Tensor SHA mismatch");
                        require(tensor.isNull("missing_reason"),"WRITTEN missing reason");
                        JSONObject model=row.getJSONObject("model");require(!model.getString("model_id").isEmpty()&&!model.getString("variant_id").isEmpty(),"Model identity");
                        require(model.isNull("artifact_sha256")||model.getString("artifact_sha256").matches("[0-9a-f]{64}"),"Model artifact SHA format");
                        JSONObject frozen=session.optJSONObject("execution");
                        if(frozen!=null){JSONObject mz=frozen.getJSONObject("stages").getJSONObject("mz");
                            require(mz.getString("model_id").equals(model.getString("model_id"))&&mz.getString("variant_id").equals(model.getString("variant_id")),"Frozen model mismatch");}
                        require(row.getInt("crop_width")>0&&row.getInt("crop_height")>0,"Crop dimensions");
                        JSONObject letterbox=row.getJSONObject("letterbox");require(letterbox.getDouble("scale")>0&&Double.isFinite(letterbox.getDouble("scale"))
                                &&Double.isFinite(letterbox.getDouble("pad_x"))&&Double.isFinite(letterbox.getDouble("pad_y")),"Letterbox");
                        JSONObject h=row.getJSONObject("homography");require("NOT_AVAILABLE".equals(h.getString("status"))
                                &&"RECTIFIER_TRANSFORM_NOT_EXPOSED".equals(h.getString("reason"))&&h.isNull("H")&&h.isNull("H_inverse"),"Unproven homography");
                        written++;
                    }else{require("MISSING".equals(tensor.getString("write_status")),"Write status");missing++;}
                }
                if(!"WRITTEN".equals(tensor.getString("write_status")))require(!tensor.optString("missing_reason").isEmpty()
                        &&tensor.isNull("entry")&&tensor.isNull("sha256"),"Missing evidence must have reason and no payload claim");
                checkImage(zip,row.getJSONObject("source_image"),attempt.optString("source_frame_entry"));
                String crop="plate_crop".equals(attempt.optString("evidence_kind"))?attempt.optString("evidence_entry"):"";
                checkImage(zip,row.getJSONObject("rectified_crop"),crop);
            }
            for(JSONObject attempt:attempts.values()){
                int calls=attempt.optInt("mz_input_call_count",0);
                require(calls>=0&&calls<=1000,"Call count");
                if(calls==0)require(keys.contains(attempt.getString("attempt_id")+":null"),"Missing NOT_RUN record");
                for(int i=0;i<calls;i++)require(keys.contains(attempt.getString("attempt_id")+":"+i),"Missing invocation evidence status");
            }
            JSONObject summary=session.getJSONObject("mz_input_evidence");
            require(expected==summary.getInt("expected_calls")&&written==summary.getInt("written")&&missing==summary.getInt("missing")
                    &&notRun==summary.getInt("not_run_attempts"),"Summary mismatch");
            require(written<=MzInputEvidence.MAX_PAYLOADS,"Payload count limit");
            if(summary.getBoolean("complete"))require(expected>0&&missing==0&&!summary.getBoolean("pending_loss_unknown"),"False complete claim");
            return summary.getBoolean("complete")?"PASS":"PARTIAL";
        }
    }
    private static void validateDiagnosticPurpose(ZipFile zip,JSONObject session)throws Exception{
        JSONObject report=new JSONObject(read(zip,"report.json",32*1024*1024));
        JSONObject research=report.optJSONObject("research_collection");
        require(Boolean.TRUE.equals(session.opt("mz_input_evidence_enabled"))
                &&research!=null&&Boolean.TRUE.equals(research.opt("mz_input_evidence_enabled")),
                "Missing frozen diagnostic setting");
        JSONObject summary=session.optJSONObject("mz_input_evidence");
        Object purpose=session.opt("measurement_purpose");
        require("SYNTHETIC_DIAGNOSTIC".equals(purpose)
                &&purpose.equals(research.opt("measurement_purpose"))
                &&summary!=null&&purpose.equals(summary.opt("purpose")),"Diagnostic purpose");
        Object series=session.opt("series_id");
        require(series instanceof String&&((String)series).startsWith("SYNTHETIC-"),"Diagnostic series");
    }
    private static void checkImage(ZipFile zip,JSONObject row,String expected)throws Exception{
        require(!row.getBoolean("exact_tensor_input"),"JPEG claimed as exact tensor");
        if("WRITTEN".equals(row.getString("status"))){
            require(!expected.isEmpty()&&expected.equals(row.getString("entry")),"Image identity");
            require(hex(MessageDigest.getInstance("SHA-256").digest(bytes(zip,expected,20*1024*1024))).equals(row.getString("sha256")),"Image SHA");
        }else require("MISSING".equals(row.getString("status"))&&!row.optString("missing_reason").isEmpty(),"Image missing reason");
    }
    private static List<JSONObject> rows(String text)throws Exception{
        List<JSONObject> rows=new ArrayList<>();for(String line:text.split("\n"))if(!line.trim().isEmpty())rows.add(new JSONObject(line));return rows;
    }
    private static String read(ZipFile zip,String entry,int maximum)throws IOException{return new String(bytes(zip,entry,maximum),StandardCharsets.UTF_8);}
    private static byte[] bytes(ZipFile zip,String name,int maximum)throws IOException{
        ZipEntry entry=zip.getEntry(name);if(entry==null||entry.getSize()>maximum)throw new IOException("Missing or oversized "+name);
        try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>maximum)throw new IOException("Entry budget");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    private static boolean same(Object a,Object b){return Objects.equals(a==null?JSONObject.NULL:a,b==null?JSONObject.NULL:b);}
    private static void require(boolean condition,String reason)throws IOException{if(!condition)throw new IOException("MZ_INPUT_EVIDENCE_INVALID: "+reason);}
}
