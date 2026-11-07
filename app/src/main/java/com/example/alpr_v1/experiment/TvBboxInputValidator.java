package com.example.alpr_v1.experiment;

import android.graphics.BitmapFactory;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.vision.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Strict reader for the optional CROP_ONLY_TRANSLATION contract, without inference or repair. */
public final class TvBboxInputValidator {
    private TvBboxInputValidator(){}
    public static String validate(File archive)throws Exception{
        ResearchArchive.verifyEntryHashes(archive);
        try(ZipFile zip=new ZipFile(archive)){
            Set<String> names=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                String n=entries.nextElement().getName();require(names.add(n),"Duplicate ZIP entry");safe(n);
            }
            JSONObject session=json(zip,"session.json");
            JSONObject descriptor=names.contains("samples/schema.json")?json(zip,"samples/schema.json"):new JSONObject();
            JSONObject caps=descriptor.optJSONObject("capabilities");Object enabled=caps==null?null:caps.opt(TvBboxEvidence.CAPABILITY);
            boolean claimed="TV_BBOX_CROP".equals(session.optString("input_mode"))||session.has("tv_bbox_crop")
                    ||names.contains(TvBboxEvidence.INDEX);
            if(!Boolean.TRUE.equals(enabled)){require(!claimed,"Undeclared TV BBOX input");return "ABSENT";}
            require("TV_BBOX_CROP".equals(session.getString("input_mode"))&&TvBboxEvidence.INDEX.equals(descriptor.getString("tv_bbox_inputs_file")),"Mode/capability conflict");
            TvBboxCrop frozen=TvBboxCrop.fromJson(session.getJSONObject("tv_bbox_crop"));
            require("roi_budget".equals(session.getString("experiment_type")),"F5A-only mode");
            JSONObject execution=session.getJSONObject("execution");
            require("TV_BBOX_CROP".equals(execution.getString("input_mode"))
                    &&SetupJson.canonical(frozen.toJson()).equals(SetupJson.canonical(execution.getJSONObject("tv_bbox_crop"))),"Frozen input conflict");
            require(!execution.getJSONObject("feature_flags").getBoolean("autozoom"),"Frozen zoom required");
            require(session.getString("started_at").equals(session.getString("tv_bbox_crop_frozen_at_utc")),"START binding");
            JSONObject report=json(zip,"report.json").getJSONObject("research_collection");
            for(String key:new String[]{"input_mode","tv_bbox_crop","tv_bbox_crop_frozen_at_utc","tv_bbox_input_evidence"})
                require(SetupJson.canonical(session.opt(key)).equals(SetupJson.canonical(report.opt(key))),"Report conflict: "+key);
            Map<String,JSONObject> attempts=new LinkedHashMap<>();
            for(JSONObject a:rows(zip,"samples/attempts.jsonl"))require(attempts.put(a.getString("attempt_id"),a)==null,"Duplicate attempt");
            Map<String,JSONObject> scenes=new HashMap<>(),roles=new HashMap<>();
            for(JSONObject scene:rows(zip,"samples/scene_evidence.jsonl"))scenes.put(scene.getString("scene_event_id"),scene);
            for(JSONObject role:rows(zip,"samples/mt_detection_roles.jsonl"))roles.put(role.getString("mt_invocation_id"),role);
            Set<String> frameKeys=new HashSet<>(),linked=new HashSet<>();int written=0,missing=0,frames=0;
            for(JSONObject row:rows(zip,TvBboxEvidence.INDEX)){
                require(++frames<=TvBboxEvidence.MAX_FRAMES,"Frame limit");
                require(TvBboxEvidence.SCHEMA.equals(row.getString("schema"))&&session.getString("session_id").equals(row.getString("session_id")),"Frame identity");
                require(frozen.digest.equals(row.getString("tv_bbox_crop_digest_sha256"))
                        &&"CROP_ONLY_TRANSLATION".equals(row.getString("processing_transform"))&&"NOT_APPLIED".equals(row.getString("H_TV")),"Transform conflict");
                JSONObject stamp=row.getJSONObject("source_stamp");stamp(stamp);
                String key=row.getString("frame_key");JSONObject identity=new JSONObject().put("source_stamp",stamp).put("snapshot",frozen.digest);
                require(key.equals(SetupJson.sha256(identity))&&frameKeys.add(key),"Frame key/stamp conflict");
                String sceneId=row.optString("scene_event_id","");
                if(!row.isNull("scene_event_id")&&!sceneId.isEmpty()){
                    JSONObject scene=scenes.get(sceneId);require(scene!=null,"Unknown scene event");
                    JSONObject event=scene.optJSONObject("event_stamp");
                    if(event!=null)for(String field:new String[]{"scene_generation","visual_epoch","camera_transform_generation"})require(Objects.equals(event.opt(field),stamp.opt(field)),"Scene generation conflict");
                }
                boolean rejected="REJECTED_GEOMETRY".equals(row.optString("pipeline_status"));
                if(image(zip,row.getJSONObject("source_image"),TvBboxCrop.CAMERA_SPACE,rejected?-1:frozen.sourceWidth,rejected?-1:frozen.sourceHeight))written++;else missing++;
                if(image(zip,row.getJSONObject("crop_image"),TvBboxCrop.LOCAL_SPACE,frozen.width(),frozen.height()))written++;else missing++;
                JSONArray links=row.getJSONArray("attempt_links");require(!rejected||links.length()==0,"Rejected geometry invoked MT");
                for(int i=0;i<links.length();i++){
                    JSONObject link=links.getJSONObject(i);String id=link.getString("attempt_id");
                    JSONObject a=attempts.get(id);require(a!=null&&linked.add(id)&&key.equals(a.optString("tv_bbox_frame_key")),"Attempt/frame binding");
                    for(String field:MzInputEvidence.STAMP)require(Objects.equals(a.opt(field),stamp.opt(field)),"SourceStamp conflict: "+field);
                    require(Objects.equals(a.opt("mt_invocation_id"),link.opt("mt_invocation_id"))
                            &&Objects.equals(a.opt("mt_detection_index"),link.opt("mt_detection_index"))
                            &&Objects.equals(a.opt("mt_executed"),link.opt("mt_executed")),"MT identity conflict");
                    JSONObject roi=a.getJSONObject("tv_bbox_roi_local_xy");
                    require(SetupJson.canonical(roi).equals(SetupJson.canonical(link.getJSONObject("roi_local_xy"))),"ROI link conflict");
                    int l=integer(roi,"left"),t=integer(roi,"top"),r=integer(roi,"right"),b=integer(roi,"bottom");
                    require(l>=0&&t>=0&&r>l&&b>t&&r<=frozen.width()&&b<=frozen.height(),"ROI outside TV");
                    require(a.getInt("roi_left")==l+frozen.left&&a.getInt("roi_top")==t+frozen.top
                            &&a.getInt("roi_right")==r+frozen.left&&a.getInt("roi_bottom")==b+frozen.top,"ROI translation");
                    require(TvBboxCrop.LOCAL_SPACE.equals(a.getString("tv_bbox_coordinate_space")),"Local coordinate space");
                    JSONObject local=a.optJSONObject("tv_bbox_local_geometry");
                    require(SetupJson.canonical(local).equals(SetupJson.canonical(link.opt("local_geometry"))),"Local geometry link");
                    if(!a.isNull("mt_detection_index")){
                        require(local!=null,"Missing source-local MT geometry");validateDetection(local,a,frozen);
                    }
                    if(a.optBoolean("mt_executed")){
                        JSONObject role=roles.get(a.getString("mt_invocation_id"));require(role!=null,"Missing actual role index");
                        require("samples/mt_detection_roles.jsonl".equals(link.getString("role_index_file")),"Role reference");
                        if(!a.isNull("mt_detection_index"))require(contains(role.optJSONArray("raw_attempt_ids"),id),"Detection absent from RAW");
                    }else require(a.optString("mt_invocation_id").isEmpty(),"Invented invocation");
                }
            }
            for(JSONObject a:attempts.values())require(a.has("tv_bbox_frame_key")&&linked.contains(a.getString("attempt_id")),"Attempt without TV input proof");
            JSONObject summary=session.getJSONObject("tv_bbox_input_evidence");
            require(frames==summary.getInt("frames")&&written==summary.getInt("images_written")&&missing==summary.getInt("images_missing"),"Summary counts");
            if(summary.getBoolean("complete"))require(frames>0&&missing==0&&!summary.getBoolean("pending_loss_unknown"),"False complete claim");
            return summary.getBoolean("complete")?"PASS":"PARTIAL";
        }
    }
    private static void validateDetection(JSONObject local,JSONObject attempt,TvBboxCrop frozen)throws Exception{
        require(TvBboxCrop.LOCAL_SPACE.equals(local.getString("coordinate_space")),"Pose local space");
        JSONArray box=local.getJSONArray("bbox"),points=local.getJSONArray("corners_px");require(box.length()==4,"Local bbox");
        String[] keys={"plate_left","plate_top","plate_right","plate_bottom"};
        for(int i=0;i<4;i++){
            float expected=(float)box.getDouble(i)+(i%2==0?frozen.left:frozen.top);
            require(Float.isFinite(expected)&&Float.floatToIntBits(expected)==Float.floatToIntBits((float)attempt.getDouble(keys[i])),"MT bbox missing/double offset");
        }
        List<Point2> full=new ArrayList<>();for(int i=0;i<points.length();i++){
            JSONArray p=points.getJSONArray(i);require(p.length()==2,"Local point");float x=(float)p.getDouble(0)+frozen.left,y=(float)p.getDouble(1)+frozen.top;
            require(Float.isFinite(x)&&Float.isFinite(y),"Non-finite local Pose");full.add(new Point2(x,y));
        }
        if("VALID_QUAD".equals(attempt.optString("mt_pose_status"))){
            Detection expected=new Detection(0,1,(float)attempt.getDouble("plate_left"),(float)attempt.getDouble("plate_top"),
                    (float)attempt.getDouble("plate_right"),(float)attempt.getDouble("plate_bottom"),full);
            MtPoseGeometryEncoder.Result pose=MtPoseGeometryEncoder.encode(expected,true,frozen.sourceWidth,frozen.sourceHeight);
            require("VALID_QUAD".equals(pose.status)&&pose.cornersNorm.equals(attempt.getString("mt_pose_corners_norm")),"MT Pose missing/double offset");
            require(attempt.getInt("mt_pose_source_width_px")==frozen.sourceWidth&&attempt.getInt("mt_pose_source_height_px")==frozen.sourceHeight,"Pose source dimensions");
        }
    }
    private static boolean image(ZipFile zip,JSONObject image,String space,int width,int height)throws Exception{
        require("JPEG94".equals(image.getString("encoding"))&&Boolean.FALSE.equals(image.opt("exact_model_input"))
                &&space.equals(image.getString("coordinate_space")),"Image context/space");
        if("MISSING".equals(image.getString("status"))){require(image.isNull("entry")&&image.isNull("sha256")&&!image.optString("missing_reason").isEmpty(),"Missing image reason");return false;}
        require("WRITTEN".equals(image.getString("status")),"Image status");String path=image.getString("entry");safe(path);
        byte[] data=bytes(zip,path,20*1024*1024);require(hex(MessageDigest.getInstance("SHA-256").digest(data)).equals(image.getString("sha256")),"Image SHA");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);
        require(options.outWidth>0&&options.outHeight>0&&options.outWidth<=12000&&options.outHeight<=12000&&(long)options.outWidth*options.outHeight<=40_000_000,"Image size");
        require(options.outWidth==integer(image,"width_px")&&options.outHeight==integer(image,"height_px")
                &&(width<0||width==options.outWidth)&&(height<0||height==options.outHeight),"Image dimensions");return true;
    }
    private static void stamp(JSONObject stamp)throws Exception{
        for(String key:MzInputEvidence.STAMP)if(!"source_timestamp_domain".equals(key)){
            Object n=stamp.opt(key);require((n instanceof Integer||n instanceof Long)&&((Number)n).longValue()>=0,"Stamp int64: "+key);
        }
        com.example.alpr_v1.continuity.SourceTimestampDomain.valueOf(stamp.getString("source_timestamp_domain"));
    }
    private static int integer(JSONObject o,String key)throws Exception{Object n=o.opt(key);require((n instanceof Integer||n instanceof Long)&&((Number)n).longValue()>=Integer.MIN_VALUE&&((Number)n).longValue()<=Integer.MAX_VALUE,"Integer "+key);return ((Number)n).intValue();}
    private static boolean contains(JSONArray array,String value){if(array!=null)for(int i=0;i<array.length();i++)if(value.equals(array.optString(i)))return true;return false;}
    private static JSONObject json(ZipFile zip,String entry)throws Exception{return new JSONObject(new String(bytes(zip,entry,32*1024*1024),StandardCharsets.UTF_8));}
    private static List<JSONObject> rows(ZipFile zip,String entry)throws Exception{List<JSONObject> rows=new ArrayList<>();String text=new String(bytes(zip,entry,32*1024*1024),StandardCharsets.UTF_8);require(!text.startsWith("\ufeff"),"BOM");for(String line:text.split("\n"))if(!line.trim().isEmpty())rows.add(new JSONObject(line));return rows;}
    private static byte[] bytes(ZipFile zip,String path,int limit)throws Exception{ZipEntry entry=zip.getEntry(path);require(entry!=null&&entry.getSize()>=0&&entry.getSize()<=limit,"Missing/oversized "+path);try(InputStream input=zip.getInputStream(entry);ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){require(output.size()+n<=limit,"Read limit");output.write(b,0,n);}return output.toByteArray();}}
    private static void safe(String p)throws Exception{require(!p.startsWith("/")&&!p.contains("\\")&&!p.contains(":")&&!Arrays.asList(p.split("/")).contains(".."),"Unsafe path");}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    private static void require(boolean condition,String reason)throws IOException{if(!condition)throw new IOException("TV_BBOX_INPUT_INVALID: "+reason);}
}
