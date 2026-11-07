package com.example.alpr_v1.experiment;

import com.example.alpr_v1.continuity.ContinuityStamp;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Passive F5A evidence. No detector, scheduler, NMS or inference admission policy. */
public final class F5aEvidence {
    public static final String SCENES = "samples/scene_evidence.jsonl";
    public static final String ROLES = "samples/mt_detection_roles.jsonl";
    static final String JOURNAL = "samples/scene_evidence_journal.jsonl";
    /** An owned snapshot; the Android adapter and host JPEG fixtures use the same writer path. */
    public interface Frame {
        int width(); int height();
        void write(File file) throws IOException;
        void release();
    }
    private final ResearchSessionStore store;
    private final Map<String,JSONObject> scenes = new LinkedHashMap<>();
    private final Map<String,JSONObject> roles = new LinkedHashMap<>();
    private final Set<String> revisions = new HashSet<>();
    private long sequence, metadataConflicts;
    private String active = "";
    private boolean closed;
    F5aEvidence(ResearchSessionStore store) { this.store = store; }

    public static JSONObject stampJson(ContinuityStamp stamp) {
        JSONObject out = new JSONObject();
        put(out,"scene_generation",stamp.sceneGeneration); put(out,"visual_epoch",stamp.visualEpoch);
        put(out,"camera_transform_generation",stamp.cameraTransformGeneration);
        put(out,"source_sequence",stamp.sourceSequence); put(out,"source_timestamp_nanos",stamp.sourceTimestampNanos);
        put(out,"source_timestamp_domain",stamp.sourceTimestampDomain.name()); return out;
    }
    public static void put(JSONObject row,String key,Object value) {
        try { row.put(key,value == null ? JSONObject.NULL : value); }
        catch (org.json.JSONException error) { throw new IllegalArgumentException("Invalid evidence field: "+key,error); }
    }
    static JSONObject copy(JSONObject value) {
        try { return new JSONObject(value.toString()); }
        catch (org.json.JSONException error) { throw new IllegalArgumentException(error); }
    }
    synchronized void started(long wallMillis,long elapsedNanos) {
        if (sequence == 0) create("SESSION_START","session_start",null,null,wallMillis,elapsedNanos);
    }
    public void boundary(long revision,String reason,ContinuityStamp stamp,long wall,long now) {
        boundary("default",revision,reason,stamp,wall,now);
    }
    public synchronized void boundary(String pipelineOwner,long revision,String reason,ContinuityStamp stamp,long wall,long now) {
        if (closed || !store.accepting() || !revisions.add(pipelineOwner+"/"+revision)) return;
        JSONObject old = scenes.get(active);
        if (old != null && "PENDING".equals(old.optString("write_status"))) {
            missing(old,"superseded_before_capture"); journal("SCENE",old);
        }
        create("SCENE_BOUNDARY",reason,revision,stamp,wall,now);
    }
    private void create(String type,String reason,Long revision,ContinuityStamp stamp,long wall,long now) {
        String id = store.sessionId()+"-s"+String.format(Locale.ROOT,"%08d",++sequence);
        JSONObject row = new JSONObject();
        put(row,"schema","alpr.scene_evidence.v1"); put(row,"session_id",store.sessionId());
        put(row,"scene_event_id",id); put(row,"event_type",type); put(row,"event_reason",reason);
        put(row,"transition_revision",revision); put(row,"event_stamp",stamp == null ? null : stampJson(stamp));
        put(row,"observed_at_utc",Instant.ofEpochMilli(wall).toString()); put(row,"event_elapsed_nanos",now);
        for (String key:new String[]{"source_stamp","capture_elapsed_nanos","capture_duration_nanos",
                "source_frame_entry","source_frame_sha256","source_frame_width_px","source_frame_height_px","missing_reason"}) put(row,key,null);
        put(row,"source_frame_coordinate_space","mt_source_bitmap_xy");
        put(row,"source_frame_origin","pipeline_bitmap_before_roi"); put(row,"settling_status","NOT_ASSESSED");
        put(row,"write_status","PENDING"); put(row,"mp_observations",new JSONArray());
        put(row,"mt_invocation_ids",new JSONArray()); put(row,"mt_execution_status","NOT_RUN");
        put(row,"mt_not_run_reason","no_mt_invocation_for_scene"); put(row,"links_status","COMPLETE");
        scenes.put(id,row); active=id; journal("SCENE",row);
    }
    public synchronized boolean pending() {
        JSONObject row=scenes.get(active);
        return !closed && store.accepting() && row != null && "PENDING".equals(row.optString("write_status"));
    }
    private static boolean generations(JSONObject a,JSONObject b) {
        for(String key:new String[]{"scene_generation","visual_epoch","camera_transform_generation"})
            if(a.optLong(key,-1)!=b.optLong(key,-2))return false;
        return true;
    }
    private static boolean eligible(JSONObject event,JSONObject source) {
        if (!generations(event,source)) return false;
        return event.optString("source_timestamp_domain").equals(source.optString("source_timestamp_domain"))
                && source.optLong("source_sequence") >= event.optLong("source_sequence");
    }
    /** Bind START once, and never associate old in-flight work with a later scene. */
    public synchronized String sceneFor(ContinuityStamp stamp) {
        JSONObject source=stampJson(stamp), row=scenes.get(active);
        if (row == null || closed) return "";
        JSONObject event=row.optJSONObject("event_stamp");
        if(event==null) { put(row,"event_stamp",source); journal("SCENE",row); return active; }
        return eligible(event,source) ? active : "";
    }
    /** Called before conversion; the returned ID is a generation-pinned capture ticket. */
    public synchronized String ticket(ContinuityStamp stamp) {
        return pending() ? sceneFor(stamp) : "";
    }
    public void captureMissing(String ticket,String reason) {
        synchronized(this) {
            JSONObject row=scenes.get(ticket);
            if(row!=null && "PENDING".equals(row.optString("write_status"))) {
                missing(row,reason); journal("SCENE",row);
            }
        }
    }
    /** Takes ownership only on success. The caller releases a rejected frame. */
    boolean capture(String ticket,ContinuityStamp stamp,Frame frame,long now,long duration) {
        final JSONObject row;
        synchronized(this) {
            row=scenes.get(ticket);
            if(closed || !store.accepting() || row==null || !ticket.equals(active)
                    || !"PENDING".equals(row.optString("write_status")))return false;
            if(row.optJSONObject("event_stamp")==null || !eligible(row.optJSONObject("event_stamp"),stampJson(stamp)))return false;
            put(row,"source_stamp",stampJson(stamp)); put(row,"capture_elapsed_nanos",now);
            put(row,"capture_duration_nanos",Math.max(0,duration));
            put(row,"source_frame_width_px",frame.width()); put(row,"source_frame_height_px",frame.height());
            put(row,"source_frame_entry","samples/frames/scene-"+ticket+".jpg");
            put(row,"write_status","CAPTURED"); journal("SCENE",row);
        }
        if(!store.enqueueF5a(() -> write(row,frame))) {
            synchronized(this) { missing(row,"writer_queue_full"); journal("SCENE",row); }
            return false;
        }
        return true;
    }
    private void write(JSONObject row,Frame frame) {
        String entry=row.optString("source_frame_entry");
        File target=new File(store.directory(),entry), temp=new File(target.getPath()+".tmp");
        try {
            frame.write(temp);
            int[] size=jpegDimensions(temp);
            if(size[0]!=frame.width() || size[1]!=frame.height())throw new IOException("Evidence JPEG dimensions differ from snapshot");
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
            String sha=com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(target);
            synchronized(this) { put(row,"source_frame_sha256",sha); put(row,"write_status","WRITTEN"); }
        } catch(Exception | OutOfMemoryError error) {
            synchronized(this) { missing(row,"source_frame_write_failed"); }
            try { Files.deleteIfExists(temp.toPath()); Files.deleteIfExists(target.toPath()); } catch(IOException ignored) { }
        } finally {
            frame.release();
            synchronized(this) { journal("SCENE",row); }
        }
    }
    private static void missing(JSONObject row,String reason) {
        put(row,"write_status","MISSING"); put(row,"missing_reason",reason);
        put(row,"source_frame_entry",null); put(row,"source_frame_sha256",null);
    }
    public synchronized void invocation(String sceneId,String invocation) {
        JSONObject row=scenes.get(sceneId);
        if(row==null) {
            row=scenes.get(active);
            if(row!=null) {
                put(row,"links_status","INCOMPLETE");
                if(row.optJSONArray("mt_invocation_ids").length()==0) {
                    put(row,"mt_execution_status","UNKNOWN");put(row,"mt_not_run_reason",null);
                }
                journal("SCENE",row);
            }
            return;
        }
        JSONArray ids=row.optJSONArray("mt_invocation_ids");
        for(int i=0;i<ids.length();i++)if(invocation.equals(ids.optString(i)))return;
        ids.put(invocation); put(row,"mt_execution_status","EXECUTED"); put(row,"mt_not_run_reason",null);
        journal("SCENE",row);
    }
    public synchronized void observation(String sceneId,JSONObject mp,String noMtReason) {
        JSONObject row=scenes.get(sceneId); if(row==null)return;
        if(mp!=null)row.optJSONArray("mp_observations").put(copy(mp));
        if("NOT_RUN".equals(row.optString("mt_execution_status")) && noMtReason!=null && !noMtReason.isEmpty())
            put(row,"mt_not_run_reason",noMtReason);
        journal("SCENE",row);
    }
    public synchronized void roles(JSONObject row) {
        String id=row.optString("mt_invocation_id");
        if(id.isEmpty() || roles.containsKey(id)) { metadataConflicts++;return; }
        roles.put(id,copy(row)); journal("ROLE",row);
    }
    synchronized void close(String reason) {
        closed=true;
        for(JSONObject row:scenes.values())if("PENDING".equals(row.optString("write_status"))) {
            missing(row,"timer".equals(reason) ? "no_frame_before_session_end" : "session_stopped_before_capture");
            journal("SCENE",row);
        }
    }
    private void journal(String kind,JSONObject value) {
        JSONObject envelope=new JSONObject(); put(envelope,"kind",kind); put(envelope,"record",copy(value));
        String line=envelope.toString()+"\n";
        store.enqueueF5a(() -> {
            try { ResearchSessionStore.append(new File(store.directory(),JOURNAL),line); }
            catch(IOException error) { /* Retained metadata is retried by flushFinal; failed final persistence throws. */ }
        });
    }
    /** Off the UI/inference threads, after the shared writer has drained. */
    synchronized void flushFinal() throws IOException {
        // Rewrite from retained metadata, so a full image queue cannot silently discard an event ID.
        StringBuilder text=new StringBuilder();
        for(JSONObject row:scenes.values())appendEnvelope(text,"SCENE",row);
        for(JSONObject row:roles.values())appendEnvelope(text,"ROLE",row);
        if(metadataConflicts>0) {
            JSONObject error=new JSONObject();put(error,"session_id",store.sessionId());
            put(error,"metadata_conflicts",metadataConflicts);appendEnvelope(text,"ERROR",error);
        }
        ResearchSessionStore.atomicText(new File(store.directory(),JOURNAL),text.toString());
        // Successful final persistence repairs queue rejection; interruption before this point stays explicit.
    }
    private static void appendEnvelope(StringBuilder out,String kind,JSONObject row) {
        JSONObject envelope=new JSONObject();put(envelope,"kind",kind);put(envelope,"record",row);
        out.append(envelope).append('\n');
    }
    static void finalizeStored(File directory,JSONObject session,JSONObject schema) throws Exception {
        if(!session.optBoolean("f5a_evidence_extensions"))return;
        Map<String,JSONObject> scenes=new LinkedHashMap<>(), roles=new LinkedHashMap<>();
        long[] losses={0};
        File journal=new File(directory,JOURNAL);
        if(journal.isFile())try(BufferedReader input=Files.newBufferedReader(journal.toPath(),StandardCharsets.UTF_8)) {
            String line;while((line=input.readLine())!=null) {
                if(line.trim().isEmpty())continue;
                try {
                    JSONObject envelope=new JSONObject(line), row=envelope.getJSONObject("record");
                    if(!session.getString("session_id").equals(row.getString("session_id")))throw new IOException("F5A session mismatch");
                    if("SCENE".equals(envelope.getString("kind")))scenes.put(row.getString("scene_event_id"),row);
                    else if("ROLE".equals(envelope.getString("kind")))roles.put(row.getString("mt_invocation_id"),row);
                    else throw new IOException("Unknown F5A journal kind");
                }catch(Exception invalid){losses[0]++;}
            }
        }
        boolean interrupted="process_interrupted".equals(session.optString("completion_reason"));
        put(session,"scene_evidence_pending_loss_unknown",interrupted);
        // START is a durable obligation even if the process died before its first journal task.
        if(scenes.isEmpty() && !session.isNull("started_at") && session.has("started_at")) {
            JSONObject row=new JSONObject();
            for(String key:new String[]{"event_stamp","source_stamp","transition_revision","capture_elapsed_nanos","capture_duration_nanos",
                    "source_frame_width_px","source_frame_height_px"})put(row,key,null);
            put(row,"schema","alpr.scene_evidence.v1");put(row,"session_id",session.getString("session_id"));
            put(row,"scene_event_id",session.getString("session_id")+"-s00000001");put(row,"event_type","SESSION_START");
            put(row,"event_reason","session_start");put(row,"observed_at_utc",session.optString("started_at"));
            put(row,"event_elapsed_nanos",session.optLong("started_elapsed_nanos"));
            put(row,"source_frame_coordinate_space","mt_source_bitmap_xy");put(row,"source_frame_origin","pipeline_bitmap_before_roi");
            put(row,"settling_status","NOT_ASSESSED");put(row,"mp_observations",new JSONArray());put(row,"mt_invocation_ids",new JSONArray());
            put(row,"mt_not_run_reason",null);put(row,"mt_execution_status","UNKNOWN");put(row,"links_status","INCOMPLETE");
            missing(row,"process_interrupted");scenes.put(row.getString("scene_event_id"),row);losses[0]++;
        }
        long written=0,missing=0;
        for(JSONObject row:scenes.values()) {
            String status=row.optString("write_status");
            if(!"WRITTEN".equals(status) && !"MISSING".equals(status))missing(row,"process_interrupted");
            if("WRITTEN".equals(row.optString("write_status"))) {
                String entry=row.optString("source_frame_entry");
                File file=safeFrame(directory,entry);
                if(!file.isFile()) { missing(row,"missing_source_frame_image");losses[0]++; }
                else if(!com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(file).equals(row.optString("source_frame_sha256"))) {
                    missing(row,"source_frame_hash_mismatch");losses[0]++;
                } else {
                    int[] dimensions=jpegDimensions(file);
                    if(dimensions[0]!=row.optInt("source_frame_width_px") || dimensions[1]!=row.optInt("source_frame_height_px")) {
                        missing(row,"source_frame_dimensions_mismatch");losses[0]++;
                    }
                }
            }
            if(interrupted) {put(row,"links_status","INCOMPLETE");if(row.optJSONArray("mt_invocation_ids").length()==0) {
                put(row,"mt_execution_status","UNKNOWN");put(row,"mt_not_run_reason",null);
            }}
            if("WRITTEN".equals(row.optString("write_status")))written++;else missing++;
        }
        Set<String> executed=new HashSet<>();
        Set<String> callsWithSource=new HashSet<>();
        ResearchSessionStore.forEachRecord(new File(directory,"samples/attempts.jsonl"),row -> {
            if(row.optBoolean("mt_executed")) {
                String id=row.optString("mt_invocation_id");executed.add(id);
                String source=row.optString("source_frame_entry");
                if(!source.isEmpty() && safeFrame(directory,source).isFile())callsWithSource.add(id);
            }
        });
        boolean rolesComplete=roles.keySet().equals(executed) && callsWithSource.equals(executed) && losses[0]==0 && !interrupted;
        for(JSONObject row:roles.values())rolesComplete &= "COMPLETE".equals(row.optString("role_proof_status"))
                && scenes.containsKey(row.optString("scene_event_id"));
        put(session,"scene_evidence_expected",scenes.size());put(session,"scene_evidence_written",written);
        put(session,"scene_evidence_missing",missing);put(session,"scene_evidence_integrity_loss_count",losses[0]);
        boolean complete=missing==0 && losses[0]==0 && !interrupted && !scenes.isEmpty();
        for(JSONObject row:scenes.values())complete &= "COMPLETE".equals(row.optString("links_status"));
        put(session,"scene_evidence_complete",complete);
        put(session,"mt_detection_roles_complete",rolesComplete);
        put(schema.getJSONObject("capabilities"),"scene_evidence_v1",true);
        put(schema.getJSONObject("capabilities"),"mt_detection_roles_v1",true);
        put(schema,"scene_evidence_file",SCENES);put(schema,"mt_detection_roles_file",ROLES);
        writeRows(new File(directory,SCENES),scenes.values());writeRows(new File(directory,ROLES),roles.values());
    }
    private static File safeFrame(File root,String entry) throws IOException {
        if(!entry.startsWith("samples/frames/") || entry.contains("\\") || entry.contains("..") || entry.contains(":"))
            throw new IOException("Unsafe F5A image path");
        File file=new File(root,entry);
        if(!file.getCanonicalPath().startsWith(new File(root,"samples/frames").getCanonicalPath()+File.separator))
            throw new IOException("F5A image escaped frame directory");
        return file;
    }
    /** Small JPEG header reader shared by host/native finalization; no platform decoder needed. */
    static int[] jpegDimensions(File file) throws IOException {
        try(DataInputStream input=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if(input.readUnsignedShort()!=0xffd8)throw new IOException("Invalid JPEG signature");
            while(true) {
                if(input.readUnsignedByte()!=255)throw new IOException("Invalid JPEG marker");
                int marker;do {marker=input.readUnsignedByte();}while(marker==255);
                if(marker==0xd8 || marker==1 || marker>=0xd0 && marker<=0xd7)continue;
                if(marker==0xd9 || marker==0xda)throw new IOException("JPEG dimensions missing");
                int length=input.readUnsignedShort();if(length<2)throw new IOException("Invalid JPEG segment");
                if(marker>=0xc0 && marker<=0xcf && marker!=0xc4 && marker!=0xc8 && marker!=0xcc) {
                    if(length<8)throw new IOException("Invalid JPEG SOF");input.readUnsignedByte();
                    int height=input.readUnsignedShort(),width=input.readUnsignedShort();
                    if(width<=0 || height<=0)throw new IOException("Invalid JPEG dimensions");return new int[]{width,height};
                }
                int remaining=length-2;
                while(remaining>0){int skipped=input.skipBytes(remaining);if(skipped==0){input.readByte();skipped=1;}remaining-=skipped;}
            }
        }
    }
    private static void writeRows(File file,Collection<JSONObject> rows) throws IOException {
        StringBuilder text=new StringBuilder();for(JSONObject row:rows)text.append(row).append('\n');
        ResearchSessionStore.atomicText(file,text.toString());
    }
}
