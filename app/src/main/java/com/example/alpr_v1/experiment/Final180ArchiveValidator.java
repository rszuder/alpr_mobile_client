package com.example.alpr_v1.experiment;

import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.setup.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Streaming validation of the optional F6 producer. Missing evidence is PARTIAL; identity conflicts are errors. */
public final class Final180ArchiveValidator {
    private Final180ArchiveValidator(){}
    interface JsonRow {void accept(JSONObject row)throws Exception;}
    static JSONObject json(ZipFile zip,String name)throws Exception{
        ZipEntry e=zip.getEntry(name);require(e!=null&&e.getSize()<=32L*1024*1024,"Missing/oversized "+name);
        try(InputStream in=zip.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){require(out.size()+n<=32*1024*1024,"JSON size");out.write(buffer,0,n);}
            return new JSONObject(out.toString("UTF-8"));}
    }
    static void rows(ZipFile zip,String name,JsonRow row)throws Exception{
        rows(zip,name,false,row);
    }
    static void rows(ZipFile zip,String name,boolean tolerateBrokenTail,JsonRow row)throws Exception{
        ZipEntry e=zip.getEntry(name);require(e!=null,"Missing "+name);
        try(BufferedReader in=new BufferedReader(new InputStreamReader(zip.getInputStream(e),StandardCharsets.UTF_8))){String line;
            while((line=ExternalJsonSort.line(in))!=null)if(!line.trim().isEmpty()){
                JSONObject parsed;try{parsed=new JSONObject(line);}catch(JSONException broken){if(tolerateBrokenTail)continue;throw broken;}
                row.accept(parsed);}}
    }
    static void sameStamp(JSONObject a,JSONObject b)throws Exception{
        require(a!=null&&b!=null,"Missing SourceStamp");
        for(String key:MzInputEvidence.STAMP)require(Objects.equals(a.opt(key),b.opt(key)),"SourceStamp conflict: "+key);
    }
    static JSONObject attemptStamp(JSONObject a)throws Exception{JSONObject s=new JSONObject();for(String key:MzInputEvidence.STAMP)s.put(key,a.opt(key));return s;}
    static void require(boolean ok,String reason)throws IOException{if(!ok)throw new IOException("F6_EXPORT_INVALID: "+reason);}
    public static String validate(File archive)throws Exception{
        ResearchArchive.verifyEntryHashes(archive);
        try(ZipFile zip=new ZipFile(archive);ExternalJsonSort joins=new ExternalJsonSort(archive.getAbsoluteFile().getParentFile())){
            Set<String> names=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){String n=entries.nextElement().getName();require(names.add(n)&&!n.startsWith("/")&&!n.contains("\\")&&!n.contains(":")&&!Arrays.asList(n.split("/")).contains(".."),"Unsafe/duplicate ZIP entry");}
            JSONObject session=json(zip,"session.json"),descriptor=json(zip,"samples/schema.json");
            require(Final180Evidence.enabled(session)&&descriptor.getJSONObject("capabilities").optBoolean(Final180PhotoRoi.PROFILE),"Undeclared F6 profile");
            require(Final180Evidence.FILE.equals(descriptor.getString("f6_execution_file")),"Lifecycle file");
            JSONObject execution=session.getJSONObject("execution"),summary=session.getJSONObject("f6_evidence");
            TvBboxCrop tv=TvBboxCrop.fromJson(session.getJSONObject("tv_bbox_crop"));
            Final180PhotoRoi photo=Final180PhotoRoi.fromJson(execution.getJSONObject("f6_photo_roi"),tv);
            try{Final180PhotoRoi.requireExecution(execution);}
            catch(IllegalArgumentException invalid){throw new IOException("F6_EXPORT_INVALID: "+invalid.getMessage(),invalid);}
            require(SetupJson.canonical(tv.toJson()).equals(SetupJson.canonical(execution.getJSONObject("tv_bbox_crop"))),"Frozen TV conflict");
            require(SetupJson.canonical(session).equals(SetupJson.canonical(json(zip,"report.json").getJSONObject("research_collection"))),"Report/session conflict");
            boolean declaredLoss=!summary.optBoolean("complete")||summary.optBoolean("pending_loss_unknown");
            boolean[] partial={declaredLoss};long[] seq={0},eventCount={0},frames={0},imageWritten={0},imageMissing={0};
            rows(zip,Final180Evidence.FILE,declaredLoss&&summary.optLong("projection_error_count")>0,e->{
                require(Final180Evidence.SCHEMA.equals(e.getString("schema"))&&session.getString("session_id").equals(e.getString("session_id")),"Event identity");
                require((e.opt("event_sequence") instanceof Long||e.opt("event_sequence") instanceof Integer)&&(e.opt("elapsed_nanos") instanceof Long||e.opt("elapsed_nanos") instanceof Integer),"Integer lifecycle clocks");
                long n=e.getLong("event_sequence");require(n>seq[0],"Lifecycle ordering/duplicate");if(n!=seq[0]+1)partial[0]=true;seq[0]=n;eventCount[0]++;
                require("ANDROID_ELAPSED_REALTIME".equals(e.getString("time_domain"))&&e.getLong("elapsed_nanos")>=0,"Event clock");
                String frame=e.isNull("frame_key")?"":e.optString("frame_key","");if(!frame.isEmpty()&&e.optJSONObject("source_stamp")!=null){TvBboxInputValidator.stamp(e.getJSONObject("source_stamp"));
                    require(frame.equals(SetupJson.sha256(new JSONObject().put("source_stamp",e.getJSONObject("source_stamp")).put("snapshot",tv.digest))),"Event frame identity");
                    if("FRAME_OPENED".equals(e.optString("event"))||"FRAME_FINISHED".equals(e.optString("event")))joins.add("F"+frame+"\u0001"+"2"+String.format(Locale.ROOT,"%020d",n),e);}
                String mt=e.isNull("mt_invocation_id")?"":e.optString("mt_invocation_id","");if(!mt.isEmpty())joins.add("M"+mt+"\u0001"+("MT_STARTED".equals(e.optString("event"))?"0":"3")+String.format(Locale.ROOT,"%020d",n),e);
            });
            if(eventCount[0]!=summary.optLong("events_persisted")||(!declaredLoss&&eventCount[0]!=summary.optLong("events_expected")))partial[0]=true;
            rows(zip,TvBboxEvidence.INDEX,r->{require(TvBboxEvidence.SCHEMA.equals(r.getString("schema"))&&session.getString("session_id").equals(r.getString("session_id")),"Frame schema/session");
                require(tv.digest.equals(r.getString("tv_bbox_crop_digest_sha256"))&&"CROP_ONLY_TRANSLATION".equals(r.getString("processing_transform"))&&"NOT_APPLIED".equals(r.getString("H_TV")),"Transform");
                JSONObject stamp=r.getJSONObject("source_stamp");TvBboxInputValidator.stamp(stamp);
                String key=r.getString("frame_key");require(key.equals(SetupJson.sha256(new JSONObject().put("source_stamp",stamp).put("snapshot",tv.digest))),"Frame key");
                boolean rejected="REJECTED_GEOMETRY".equals(r.optString("pipeline_status"));
                for(String name:new String[]{"source_image","crop_image"}){boolean source="source_image".equals(name);
                    boolean ok=TvBboxInputValidator.image(zip,r.getJSONObject(name),source?TvBboxCrop.CAMERA_SPACE:TvBboxCrop.LOCAL_SPACE,
                            source?(rejected?-1:tv.sourceWidth):tv.width(),source?(rejected?-1:tv.sourceHeight):tv.height());
                    if(ok)imageWritten[0]++;else{imageMissing[0]++;partial[0]=true;}}
                joins.add("F"+key+"\u0001"+"0",r);frames[0]++;
            });
            rows(zip,"samples/attempts.jsonl",declaredLoss&&session.optLong("integrity_loss_count")>0,a->{
                require(session.getString("session_id").equals(a.getString("session_id")),"Attempt session");
                joins.add("A"+a.getString("attempt_id")+"\u0001"+"0",a);
                String frame=a.getString("tv_bbox_frame_key");joins.add("F"+frame+"\u0001"+"1"+a.getString("attempt_id"),a);
                if(a.optBoolean("mt_executed")){require(!a.optString("mt_invocation_id").isEmpty(),"Missing invocation ID");joins.add("M"+a.getString("mt_invocation_id")+"\u0001"+"2"+a.getString("attempt_id"),a);}
                else require(a.optString("mt_invocation_id").isEmpty()&&"NOT_RUN".equals(a.getString("mt_status")),"Invented invocation");
            });
            rows(zip,F5aEvidence.ROLES,r->joins.add("M"+r.getString("mt_invocation_id")+"\u0001"+"1",r));
            long sceneCount=0;
            try(android.util.JsonReader in=new android.util.JsonReader(new InputStreamReader(zip.getInputStream(zip.getEntry(F5aEvidence.SCENES)),StandardCharsets.UTF_8))){
                in.setLenient(true);
                while(in.peek()!=android.util.JsonToken.END_DOCUMENT){String id=null,schema=null,owner=null;in.beginObject();
                    while(in.hasNext()){String key=in.nextName();if("scene_event_id".equals(key))id=in.nextString();else if("schema".equals(key))schema=in.nextString();else if("session_id".equals(key))owner=in.nextString();else in.skipValue();}
                    in.endObject();require(id!=null&&"alpr.scene_evidence.v1".equals(schema)&&session.getString("session_id").equals(owner),"Scene identity");
                    joins.add("S"+id+"\u0001"+"0",new JSONObject());sceneCount++;
                }
            }
            require(sceneCount==session.getLong("scene_evidence_expected"),"Scene count");
            class Group {
                String id="";JSONObject base,start,role;int bases,starts,ends,opened,decoded=-1;boolean returned,failed;
                Map<String,JSONObject> attempts=new LinkedHashMap<>();Set<String> mzStarted=new HashSet<>(),mzReturned=new HashSet<>();Set<Integer> indices=new HashSet<>();
                Map<String,String> mzCallAttempts=new HashMap<>();
                void flush()throws Exception{
                    if(id.isEmpty())return;
                    if(id.charAt(0)=='A'||id.charAt(0)=='S')require(bases==1,"Duplicate attempt/scene identity");
                    else if(id.charAt(0)=='F'){
                        require(bases<=1,"Duplicate frame");
                        if(base==null){require(declaredLoss,"Frame evidence lost without loss status");partial[0]=true;}
                        else{JSONArray links=base.getJSONArray("attempt_links");require(links.length()==attempts.size(),"Frame link count");
                            Set<String> linked=new HashSet<>();for(int i=0;i<links.length();i++){JSONObject l=links.getJSONObject(i),a=attempts.get(l.getString("attempt_id"));
                                require(a!=null&&linked.add(l.getString("attempt_id")),"Unknown/duplicate attempt link");
                                for(String key:new String[]{"mt_invocation_id","mt_executed","mt_detection_index"})require(Objects.equals(a.opt(key),l.opt(key)),"Link identity: "+key);
                                require(SetupJson.canonical(a.opt("tv_bbox_roi_local_xy")).equals(SetupJson.canonical(l.opt("roi_local_xy"))),"ROI link");
                                require(SetupJson.canonical(a.opt("tv_bbox_local_geometry")).equals(SetupJson.canonical(l.opt("local_geometry"))),"Geometry link");}
                        }
                        if(opened==0||ends==0)partial[0]=true;
                    }else{
                        require(starts<=1&&bases<=1,"Duplicate invocation START/roles");
                        if(start==null){require(declaredLoss,"Executed invocation without START");partial[0]=true;}
                        if(start!=null){for(JSONObject a:attempts.values()){sameStamp(start.getJSONObject("source_stamp"),attemptStamp(a));require(start.getString("frame_key").equals(a.getString("tv_bbox_frame_key")),"Invocation frame");}}
                        if(role==null){partial[0]=true;}else{
                            if(start!=null){sameStamp(start.getJSONObject("source_stamp"),role.getJSONObject("source_stamp"));require(Objects.equals(start.opt("scene_event_id"),role.opt("scene_event_id")),"Role scene identity");}
                            JSONArray raw=role.optJSONArray("raw_attempt_ids");if(raw==null)partial[0]=true;
                            else{Set<String> rawIds=new HashSet<>();for(int i=0;i<raw.length();i++)require(rawIds.add(raw.getString(i)),"Duplicate RAW");
                                for(String a:rawIds)if(!attempts.containsKey(a)){require(declaredLoss,"Missing detection attempt");partial[0]=true;}
                                Set<String> assigned=new HashSet<>();
                                for(String key:new String[]{"final_attempt_ids","suppressed_attempt_ids","unresolved_attempt_ids"}){JSONArray ids=role.getJSONArray(key);for(int i=0;i<ids.length();i++)require(assigned.add(ids.getString(i))&&rawIds.contains(ids.getString(i)),"Invalid/contradictory role membership");}
                                JSONArray rejected=role.getJSONArray("rejected_before_nms");for(int i=0;i<rejected.length();i++){String a=rejected.getJSONObject(i).getString("attempt_id");require(assigned.add(a)&&rawIds.contains(a),"Invalid rejected role");}
                                require(assigned.equals(rawIds),"Unclassified decoder output");
                                for(JSONObject a:attempts.values())if(!a.isNull("mt_detection_index"))require(rawIds.contains(a.getString("attempt_id")),"Detection without raw membership");}
                            if(!"COMPLETE".equals(role.optString("role_proof_status")))partial[0]=true;
                        }
                        if(!returned||decoded<0||failed)partial[0]=true;
                        for(JSONObject a:attempts.values()){
                            if(!a.isNull("mt_detection_index"))require(indices.add(a.getInt("mt_detection_index")),"Duplicate detection index");
                            if(decoded>=0)require(a.getInt("mt_detection_count")==decoded,"Detection count");
                            if(a.optBoolean("mz_executed")&&!mzCallAttempts.containsValue(a.getString("attempt_id")))partial[0]=true;
                        }
                        if(decoded>=0){require(indices.size()<=decoded,"Too many detection rows");if(indices.size()!=decoded)partial[0]=true;
                            for(int i:indices)require(i>=0&&i<decoded,"Detection index range");}
                        if(!mzReturned.containsAll(mzStarted))partial[0]=true;
                    }
                }
                void accept(String key,JSONObject r)throws Exception{
                    int split=key.indexOf('\u0001');String next=key.substring(0,split);char type=key.charAt(split+1);
                    if(!next.equals(id)){flush();id=next;base=null;start=null;role=null;bases=starts=ends=opened=0;decoded=-1;returned=failed=false;attempts.clear();mzStarted.clear();mzReturned.clear();mzCallAttempts.clear();indices.clear();}
                    if(id.charAt(0)=='A'||id.charAt(0)=='S'){bases++;return;}
                    if(id.charAt(0)=='F'){
                        if(type=='0'){base=r;bases++;}
                        else if(type=='1'){
                            require(attempts.put(r.getString("attempt_id"),r)==null,"Duplicate attempt");
                            if(base!=null)sameStamp(base.getJSONObject("source_stamp"),attemptStamp(r));
                            JSONObject roi=r.getJSONObject("tv_bbox_roi_local_xy");require(TvBboxCrop.LOCAL_SPACE.equals(r.getString("tv_bbox_coordinate_space")),"ROI coordinate space");
                            require(r.getInt("roi_left")==roi.getInt("left")+tv.left&&r.getInt("roi_top")==roi.getInt("top")+tv.top
                                    &&r.getInt("roi_right")==roi.getInt("right")+tv.left&&r.getInt("roi_bottom")==roi.getInt("bottom")+tv.top,"ROI translation");
                            if(r.optBoolean("mt_executed"))require(r.getInt("roi_left")==photo.left&&r.getInt("roi_top")==photo.top&&r.getInt("roi_right")==photo.right&&r.getInt("roi_bottom")==photo.bottom,"Actual MT pixels outside F6 photo ROI");
                            if(!r.isNull("mt_detection_index"))TvBboxInputValidator.validateDetection(r.getJSONObject("tv_bbox_local_geometry"),r,tv);
                        }else {if(base!=null)sameStamp(base.getJSONObject("source_stamp"),r.getJSONObject("source_stamp"));if("FRAME_OPENED".equals(r.getString("event")))opened++;else ends++;}
                    }else if(type=='0'){start=r;starts++;require(r.getString("attempt_id").equals(r.getString("mt_invocation_id")),"START ID");}
                    else if(type=='1'){role=r;bases++;}
                    else if(type=='2')require(attempts.put(r.getString("attempt_id"),r)==null,"Duplicate attempt");
                    else{
                        if(start!=null){sameStamp(start.getJSONObject("source_stamp"),r.getJSONObject("source_stamp"));require(start.getString("frame_key").equals(r.getString("frame_key")),"Late event rebound");}
                        String event=r.getString("event");if("MT_RETURNED".equals(event))returned=true;
                        if("MT_DECODED".equals(event))decoded=r.getJSONObject("details").getInt("raw_detection_count");
                        if("MT_FAILED".equals(event)||"MZ_FAILED".equals(event))failed=true;
                        if("MZ_STARTED".equals(event)){String mz=r.getString("mz_invocation_id");require(mz.startsWith(r.getString("attempt_id")+"/mz-")&&mzStarted.add(mz),"Duplicate/missing MZ START");mzCallAttempts.put(mz,r.getString("attempt_id"));}
                        if("MZ_RETURNED".equals(event)||"MZ_FAILED".equals(event)){String mz=r.getString("mz_invocation_id"),owner=mzCallAttempts.get(mz);
                            if(owner==null){require(declaredLoss,"MZ result without START");partial[0]=true;}else require(owner.equals(r.getString("attempt_id")),"MZ/attempt identity");
                            if("MZ_RETURNED".equals(event))require(mzReturned.add(mz),"Duplicate MZ return");}
                    }
                }
            }
            Group g=new Group();joins.each(g::accept);g.flush();
            JSONObject tvSummary=session.getJSONObject("tv_bbox_input_evidence");require(frames[0]==tvSummary.getLong("frames")&&imageWritten[0]==tvSummary.getLong("images_written")&&imageMissing[0]==tvSummary.getLong("images_missing"),"Image summary counts");
            require(!summary.optBoolean("complete")||!partial[0],"False complete claim");
            return partial[0]?"PARTIAL":"PASS";
        }
    }
}
