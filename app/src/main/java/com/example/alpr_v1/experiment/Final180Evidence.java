package com.example.alpr_v1.experiment;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.inference.RuntimeExecutionEvidence;
import com.example.alpr_v1.setup.TvBboxCrop;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;

/** Optional lifecycle evidence, on the session's existing FIFO writer. Never an inference gate. */
public final class Final180Evidence {
    public static final String FILE="samples/f6_execution.jsonl", SCHEMA="alpr.f6_execution_event.v1";
    private final ResearchSessionStore store;
    private final AtomicLong sequence=new AtomicLong(),written=new AtomicLong(),losses=new AtomicLong(),writeNanos=new AtomicLong(),bytes=new AtomicLong(),captureNanos=new AtomicLong();
    Final180Evidence(ResearchSessionStore store){this.store=store;}
    public static boolean enabled(JSONObject session){JSONObject e=session==null?null:session.optJSONObject("execution");return e!=null&&Final180PhotoRoi.PROFILE.equals(e.optString("capture_profile"));}
    public void captureCost(long nanos){captureNanos.addAndGet(Math.max(0,nanos));}
    public synchronized void event(String type,ContinuityStamp stamp,String frame,String scene,long trace,
                                    String attempt,String mt,String mz,JSONObject details){
        long start=store.elapsedNow();
        try {
            JSONObject row=new JSONObject().put("schema",SCHEMA).put("session_id",store.sessionId())
                    .put("event_sequence",sequence.incrementAndGet()).put("event",type)
                    .put("elapsed_nanos",start).put("time_domain","ANDROID_ELAPSED_REALTIME")
                    .put("source_stamp",stamp==null?JSONObject.NULL:F5aEvidence.stampJson(stamp))
                    .put("frame_key",nullable(frame)).put("scene_event_id",nullable(scene)).put("trace_frame_id",trace>0?trace:JSONObject.NULL)
                    .put("attempt_id",nullable(attempt)).put("mt_invocation_id",nullable(mt)).put("mz_invocation_id",nullable(mz))
                    .put("details",details==null?new JSONObject():F5aEvidence.copy(details));
            String line=row+"\n";
            if(!store.enqueueF5a(()->{
                long now=store.elapsedNow();
                try {ResearchSessionStore.append(new File(store.directory(),FILE),line);written.incrementAndGet();bytes.addAndGet(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);}
                catch(Exception error){losses.incrementAndGet();store.recordMetadataFailure("f6_lifecycle_write_failed");}
                finally{writeNanos.addAndGet(Math.max(0,store.elapsedNow()-now));}
            }))losses.incrementAndGet();
        }catch(Exception|OutOfMemoryError error){losses.incrementAndGet();store.recordMetadataFailure("f6_lifecycle_metadata_failed");}
        finally{captureNanos.addAndGet(Math.max(0,store.elapsedNow()-start));}
    }
    private static Object nullable(String s){return s==null||s.isEmpty()||"null".equals(s)?JSONObject.NULL:s;}
    JSONObject summary()throws Exception{return new JSONObject().put("events_expected",sequence.get()).put("events_written",written.get())
            .put("lifecycle_loss_count",losses.get()).put("write_elapsed_nanos",writeNanos.get()).put("capture_elapsed_nanos",captureNanos.get())
            .put("lifecycle_bytes",bytes.get()).put("time_domain","ANDROID_ELAPSED_REALTIME").put("time_unit","ns");}
    public static JSONObject details(String key,Object value){JSONObject o=new JSONObject();F5aEvidence.put(o,key,value);return o;}
    static void rows(File f,ExternalJsonSort.Row row,long[] errors)throws Exception {
        ExternalJsonSort.rows(f,o->{try{row.accept("",o);}catch(Exception e){throw new IllegalArgumentException(e);}},e->errors[0]++);
    }
    private static String order(long value){return String.format(java.util.Locale.ROOT,"%020d",value);}
    private static void add(ExternalJsonSort sort,String key,JSONObject value)throws Exception{sort.add(key,value);}
    static void finalizeStored(File dir,JSONObject session,JSONObject descriptor)throws Exception {
        long[] invalid={0};
        JSONObject previous=session.optJSONObject("f6_evidence");
        boolean unknown=session.optBoolean("f6_pending_loss_unknown",true)||session.optBoolean("pending_sample_loss_unknown")
                ||"process_interrupted".equals(session.optString("completion_reason"));
        long[] events={0},last={0},starts={0};
        rows(new File(dir,FILE),(k,e)->{
            if(!SCHEMA.equals(e.getString("schema"))||!session.getString("session_id").equals(e.getString("session_id")))throw new IOException("F6 event identity");
            long seq=e.getLong("event_sequence");if(seq!=last[0]+1)invalid[0]++;last[0]=seq;events[0]++;
            if("MT_STARTED".equals(e.optString("event")))starts[0]++;
        },invalid);
        if(!unknown&&previous!=null&&events[0]!=previous.optLong("events_expected"))invalid[0]++;
        long[] sceneCounts={0,0,0},roles={0,0};
        try(ExternalJsonSort join=new ExternalJsonSort(dir);
            BufferedWriter sceneOut=ExternalJsonSort.writer(new File(dir,F5aEvidence.SCENES+".tmp"));
            BufferedWriter roleOut=ExternalJsonSort.writer(new File(dir,F5aEvidence.ROLES+".tmp"))){
            long[] n={0};
            rows(new File(dir,F5aEvidence.JOURNAL),(k,e)->{
                JSONObject r=e.getJSONObject("record");String kind=e.getString("kind");
                if("SCENE".equals(kind))add(join,"S"+r.getString("scene_event_id")+"\u0001"+"0"+order(++n[0]),r);
                else if("ROLE".equals(kind))add(join,"R"+r.getString("mt_invocation_id")+"\u0001"+"0"+order(++n[0]),r);
                else throw new IOException("Unknown scene journal record");
            },invalid);
            rows(new File(dir,FILE),(k,e)->{
                String scene=e.isNull("scene_event_id")?"":e.optString("scene_event_id","");if(scene.isEmpty())return;
                if("SCENE_OBSERVATION".equals(e.optString("event")))add(join,"S"+scene+"\u0001"+"1"+order(e.getLong("event_sequence")),e);
                if("MT_STARTED".equals(e.optString("event")))add(join,"S"+scene+"\u0001"+"2"+order(e.getLong("event_sequence")),e);
            },invalid);
            class Group {
                String id="",reason="no_mt_invocation_for_scene";JSONObject base;int copies;
                long mtCount;boolean opened,idsOpen,firstMp=true,firstId=true;
                void openScene()throws Exception{
                    if(opened)return;opened=true;
                    if(base==null){invalid[0]++;base=new JSONObject().put("schema","alpr.scene_evidence.v1").put("session_id",session.getString("session_id"))
                            .put("scene_event_id",id.substring(1)).put("event_type","EVIDENCE_UNAVAILABLE").put("event_reason","scene_record_not_persisted");}
                    String entry=base.optString("source_frame_entry","");File image="WRITTEN".equals(base.optString("write_status"))?F5aEvidence.safeFrame(dir,entry):new File(dir,"not-written");
                    boolean ok="WRITTEN".equals(base.optString("write_status"))&&image.isFile()&&RuntimeExecutionEvidence.sha256(image).equals(base.optString("source_frame_sha256"));
                    if(!ok){base.put("write_status","MISSING").put("source_frame_entry",JSONObject.NULL).put("source_frame_sha256",JSONObject.NULL);
                        if(base.isNull("missing_reason")||base.optString("missing_reason").isEmpty())base.put("missing_reason","source_evidence_not_persisted");}
                    sceneCounts[0]++;sceneCounts[ok?1:2]++;
                    for(String key:new String[]{"mp_observations","mt_invocation_ids","mt_execution_status","mt_not_run_reason","links_status"})base.remove(key);
                    String header=base.toString();sceneOut.write(header,0,header.length()-1);sceneOut.write(",\"mp_observations\":[");
                }
                void openIds()throws Exception{openScene();if(!idsOpen){sceneOut.write("],\"mt_invocation_ids\":[");idsOpen=true;}}
                void flush()throws Exception{
                    if(id.isEmpty())return;
                    if(id.charAt(0)=='R'){
                        roles[0]++;if(copies!=1||!"COMPLETE".equals(base.optString("role_proof_status"))||base.optBoolean("stale_or_cancelled")
                                ||!base.optString("execution_error").isEmpty()||!base.optString("processing_error").isEmpty())roles[1]++;
                        roleOut.write(base.toString());roleOut.newLine();
                    }else{
                        openIds();sceneOut.write("],\"mt_execution_status\":");sceneOut.write(JSONObject.quote(mtCount>0?"EXECUTED":unknown?"UNKNOWN":"NOT_RUN"));
                        sceneOut.write(",\"mt_not_run_reason\":");sceneOut.write(mtCount>0||unknown?"null":JSONObject.quote(reason));
                        sceneOut.write(",\"links_status\":");sceneOut.write(JSONObject.quote(unknown?"INCOMPLETE":"COMPLETE"));sceneOut.write('}');sceneOut.newLine();
                    }
                }
                void accept(String key,JSONObject value)throws Exception{
                    int split=key.indexOf('\u0001');String next=key.substring(0,split);char type=key.charAt(split+1);
                    if(!id.equals(next)){flush();id=next;base=null;reason="no_mt_invocation_for_scene";copies=0;mtCount=0;opened=idsOpen=false;firstMp=firstId=true;}
                    if(type=='0'){if(id.charAt(0)=='R'&&copies>0)throw new IOException("Duplicate MT role identity");base=value;copies++;}
                    else if(type=='1'){
                        openScene();JSONObject d=value.getJSONObject("details"),mp=d.optJSONObject("mp");
                        if(mp!=null){if(!firstMp)sceneOut.write(',');firstMp=false;sceneOut.write(mp.toString());}
                        reason=d.optString("not_run_reason",reason);
                    }else if(type=='2'){
                        openIds();if(!firstId)sceneOut.write(',');firstId=false;sceneOut.write(JSONObject.quote(value.getString("mt_invocation_id")));mtCount++;
                    }
                }
            }
            Group g=new Group();join.each(g::accept);g.flush();
        }
        Files.move(new File(dir,F5aEvidence.SCENES+".tmp").toPath(),new File(dir,F5aEvidence.SCENES).toPath(),StandardCopyOption.REPLACE_EXISTING);
        Files.move(new File(dir,F5aEvidence.ROLES+".tmp").toPath(),new File(dir,F5aEvidence.ROLES).toPath(),StandardCopyOption.REPLACE_EXISTING);
        long[] frameCounts={0,0,0,0};
        TvBboxCrop frozen=TvBboxCrop.fromJson(session.getJSONObject("tv_bbox_crop"));
        try(ExternalJsonSort join=new ExternalJsonSort(dir);BufferedWriter out=ExternalJsonSort.writer(new File(dir,TvBboxEvidence.INDEX+".tmp"))){
            long[] n={0};
            rows(new File(dir,TvBboxEvidence.JOURNAL),(k,r)->add(join,r.getString("frame_key")+"\u0001"+"0"+order(++n[0]),r),invalid);
            rows(new File(dir,FILE),(k,e)->{
                if("FRAME_OPENED".equals(e.optString("event")))add(join,e.getString("frame_key")+"\u0001"+"1",e);
            },invalid);
            rows(new File(dir,"samples/attempts.jsonl"),(k,a)->{
                String key=a.optString("tv_bbox_frame_key");if(key.isEmpty())throw new IOException("Attempt without frame key");
                add(join,key+"\u0001"+"2"+a.getString("attempt_id"),a);
            },invalid);
            class Group {
                String id="";JSONObject base,opened;JSONArray links=new JSONArray();
                void flush()throws Exception{
                    if(id.isEmpty())return;
                    if(base==null){
                        JSONObject stamp=opened==null?null:opened.optJSONObject("source_stamp");
                        if(stamp==null){invalid[0]++;return;}
                        base=new JSONObject().put("schema",TvBboxEvidence.SCHEMA).put("session_id",session.getString("session_id"))
                                .put("frame_key",id).put("source_stamp",stamp).put("scene_event_id",opened.opt("scene_event_id"))
                                .put("tv_bbox_crop_digest_sha256",frozen.digest).put("processing_transform","CROP_ONLY_TRANSLATION").put("H_TV","NOT_APPLIED")
                                .put("pipeline_status","WRITE_NOT_RECORDED").put("source_image",missingImage(TvBboxCrop.CAMERA_SPACE)).put("crop_image",missingImage(TvBboxCrop.LOCAL_SPACE));
                    }
                    base.put("attempt_links",links);frameCounts[0]++;
                    if("PENDING".equals(base.optString("pipeline_status"))||"WRITE_NOT_RECORDED".equals(base.optString("pipeline_status"))
                            ||base.optBoolean("stale_or_cancelled")||!base.optString("processing_error").isEmpty())frameCounts[3]++;
                    for(String name:new String[]{"source_image","crop_image"}){
                        JSONObject image=base.getJSONObject(name);File f="WRITTEN".equals(image.optString("status"))?F5aEvidence.safeFrame(dir,image.getString("entry")):new File(dir,"not-written");
                        boolean ok="WRITTEN".equals(image.optString("status"))&&f.isFile()&&RuntimeExecutionEvidence.sha256(f).equals(image.optString("sha256"));
                        if(!ok){image.put("status","MISSING").put("entry",JSONObject.NULL).put("sha256",JSONObject.NULL);
                            if(image.isNull("missing_reason")||image.optString("missing_reason").isEmpty())image.put("missing_reason","write_not_recorded");}
                        frameCounts[ok?1:2]++;
                    }
                    out.write(base.toString());out.newLine();
                }
                void accept(String key,JSONObject r)throws Exception{
                    int split=key.indexOf('\u0001');String next=key.substring(0,split);char type=key.charAt(split+1);
                    if(!id.equals(next)){flush();id=next;base=null;opened=null;links=new JSONArray();}
                    if(type=='0')base=r;
                    else if(type=='1')opened=r;
                    else {links.put(new JSONObject().put("attempt_id",r.getString("attempt_id")).put("mt_invocation_id",r.optString("mt_invocation_id"))
                            .put("mt_executed",r.optBoolean("mt_executed")).put("mt_detection_index",r.opt("mt_detection_index"))
                            .put("roi_local_xy",r.opt("tv_bbox_roi_local_xy")).put("local_geometry",r.opt("tv_bbox_local_geometry"))
                            .put("role_index_file",F5aEvidence.ROLES));}
                }
            }
            Group g=new Group();join.each(g::accept);g.flush();
        }
        Files.move(new File(dir,TvBboxEvidence.INDEX+".tmp").toPath(),new File(dir,TvBboxEvidence.INDEX).toPath(),StandardCopyOption.REPLACE_EXISTING);
        boolean losses=unknown||invalid[0]>0||session.optLong("dropped_sample_count")>0||session.optLong("dropped_telemetry_count")>0
                ||previous==null||previous.optLong("lifecycle_loss_count")>0;
        boolean roleComplete=!losses&&roles[0]==starts[0]&&roles[1]==0;
        boolean complete=!losses&&frameCounts[0]>0&&frameCounts[2]==0&&frameCounts[3]==0&&sceneCounts[2]==0&&roleComplete;
        JSONObject summary=previous==null?new JSONObject():previous;
        summary.put("schema","alpr.f6_evidence_summary.v1").put("events_persisted",events[0]).put("mt_started_count",starts[0])
                .put("projection_error_count",invalid[0]).put("pending_loss_unknown",unknown).put("complete",complete)
                .put("unlocated_loss_possible",unknown||invalid[0]>0||previous==null||previous.optLong("lifecycle_loss_count")>0)
                .put("hardware_desktop_acceptance","NOT_VERIFIED");
        session.put("capture_profile",Final180PhotoRoi.PROFILE).put("f6_evidence",summary)
                .put("scene_evidence_expected",sceneCounts[0]).put("scene_evidence_written",sceneCounts[1]).put("scene_evidence_missing",sceneCounts[2])
                .put("scene_evidence_integrity_loss_count",invalid[0]).put("scene_evidence_complete",!losses&&sceneCounts[0]>0&&sceneCounts[2]==0)
                .put("mt_detection_roles_complete",roleComplete)
                .put("tv_bbox_input_evidence",new JSONObject().put("schema",TvBboxEvidence.SCHEMA).put("frames",frameCounts[0])
                        .put("images_written",frameCounts[1]).put("images_missing",frameCounts[2]).put("pending_loss_unknown",unknown||frameCounts[3]>0)
                        .put("complete",!losses&&frameCounts[0]>0&&frameCounts[2]==0&&frameCounts[3]==0).put("desktop_contract_status","F6_PRODUCER_ONLY_NOT_VERIFIED_DESKTOP"));
        if(!complete)session.put("collection_complete",false).put("state","PARTIAL");
        descriptor.getJSONObject("capabilities").put(Final180PhotoRoi.PROFILE,true).put(TvBboxEvidence.CAPABILITY,true).put("scene_evidence_v1",true).put("mt_detection_roles_v1",true);
        descriptor.put("f6_execution_file",FILE).put("scene_evidence_file",F5aEvidence.SCENES).put("mt_detection_roles_file",F5aEvidence.ROLES)
                .put("tv_bbox_inputs_file",TvBboxEvidence.INDEX).put("f6_time_domain","ANDROID_ELAPSED_REALTIME").put("f6_time_unit","ns");
    }
    private static JSONObject missingImage(String space)throws Exception{return new JSONObject().put("encoding","JPEG94").put("exact_model_input",false)
            .put("coordinate_space",space).put("origin","write_not_recorded").put("status","MISSING").put("entry",JSONObject.NULL).put("sha256",JSONObject.NULL)
            .put("width_px",JSONObject.NULL).put("height_px",JSONObject.NULL).put("missing_reason","write_not_recorded");}
}
