package com.example.alpr_v1.experiment;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import com.example.alpr_v1.BuildConfig;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.model.ModelRole;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** One durable run. No dependence on gallery capacity, bitmaps owned by UI, or activity lifetime. */
public final class ResearchSessionStore {
    public enum State { PREPARED, RUNNING, FINALIZING, COMPLETED, PARTIAL, ERROR }
    public interface ImageEncoder { void write(Bitmap bitmap,File target) throws IOException; }
    public static final String SAMPLE_SCHEMA = "alpr.mobile_research_samples.v2";
    private static final long MAX_IMAGE_BYTES = 64L*1024*1024;
    private static final String PROCESS = UUID.randomUUID().toString();
    public static final String[] ATTEMPT_COLUMNS = ("attempt_id,session_id,subject_key,scene_generation,visual_epoch,"
            + "camera_transform_generation,entity_id,vehicle_track_id,plate_track_id,source_sequence,source_timestamp_nanos,"
            + "attempt_started_elapsed_nanos,roi_policy,capture_source,camera_zoom_ratio,mt_status,rectification_status,"
            + "mz_status,prediction,consensus_prediction,plate_confidence,recognition_confidence,evidence_kind,evidence_entry,"
            + "stale_or_cancelled,cancel_reason,write_state,missing_evidence_reason,mz_executed,mt_invocation_id,"
            + "mt_detection_index,mt_detection_count,mt_executed,roi_left,roi_top,roi_right,roi_bottom,input_width,input_height,"
            + "plate_left,plate_top,plate_right,plate_bottom,input_scale,input_pad_x,input_pad_y,"
            + "mt_input_evidence_entry,mt_input_missing_evidence_reason,execution_error").split(",");
    private final File directory;
    private final JSONObject metadata;
    private final ThreadPoolExecutor writer;
    private final Semaphore attempts;
    private final int attemptCapacity;
    private final ImageEncoder encoder;
    private final AtomicLong sequence = new AtomicLong(), dropped = new AtomicLong(), droppedTelemetry = new AtomicLong();
    private final AtomicLong imageBytes = new AtomicLong(), written = new AtomicLong();
    private volatile boolean accepting;
    private volatile State state = State.PREPARED;
    private volatile String lastFailure = "";
    private final ArrayBlockingQueue<String> rejectedRecords = new ArrayBlockingQueue<>(256);
    private long lastCheckpointNanos, lastReportedDrops;
    public final long preparedElapsedNanos;

    public static File sessionsRoot(Context context) { return new File(context.getFilesDir(),"research/sessions"); }
    public static ResearchSessionStore prepare(Context context,ExperimentSession.Prepared prepared) throws Exception {
        return prepare(sessionsRoot(context),prepared,256,64,128L*1024*1024,ResearchSessionStore::encodeJpeg);
    }
    static ResearchSessionStore prepare(File root,ExperimentSession.Prepared prepared,int capacity,int attemptCapacity,
                                        long minimumFreeBytes,ImageEncoder encoder) throws Exception {
        Files.createDirectories(root.toPath());
        if (!root.isDirectory() || root.getUsableSpace() < minimumFreeBytes)
            throw new IOException("Brak miejsca na trwałą sesję badawczą");
        File directory = new File(root,prepared.sessionId);
        if (!directory.mkdir()) throw new IOException("Nie można utworzyć katalogu sesji: " + directory);
        for (String child : new String[]{"samples/crops","samples/evidence","telemetry","pipeline","final"})
            Files.createDirectories(new File(directory,child).toPath());
        JSONObject json = new JSONObject().put("schema","alpr.mobile_research_session.v1")
                .put("session_id",prepared.sessionId).put("state","PREPARED")
                .put("experiment_type",prepared.experimentType).put("variant",prepared.variant)
                .put("series_id",prepared.identity.seriesId).put("scenario_id",prepared.identity.scenarioId)
                .put("replicate_index",prepared.identity.replicateIndex)
                .put("created_at",Instant.ofEpochMilli(prepared.createdAtMillis).toString())
                .put("started_at",JSONObject.NULL).put("finished_at",JSONObject.NULL)
                .put("completion_reason","").put("collection_complete",false)
                .put("sample_contract_version",SAMPLE_SCHEMA).put("collection_mode","automatic")
                .put("storage_prepared",true).put("app_version",BuildConfig.VERSION_NAME)
                .put("app_build",new JSONObject().put("git_commit",BuildConfig.GIT_COMMIT)
                        .put("git_dirty",BuildConfig.GIT_DIRTY).put("git_dirty_available",BuildConfig.GIT_DIRTY_AVAILABLE)
                        .put("source_state",BuildConfig.GIT_DIRTY_AVAILABLE ? (BuildConfig.GIT_DIRTY ? "dirty" : "clean") : "unknown")
                        .put("built_at_utc",BuildConfig.BUILT_AT_UTC).put("build_type",BuildConfig.BUILD_TYPE)
                        .put("version_name",BuildConfig.VERSION_NAME).put("version_code",BuildConfig.VERSION_CODE))
                .put("review_location","desktop").put("operator_intervention_in_sampling",false)
                .put("process_owner",PROCESS).put("dropped_sample_count",0).put("dropped_telemetry_count",0);
        if (prepared.execution != null) {
            json.put("execution",prepared.execution.toJson());
            json.put("model_refs",prepared.execution.modelRefsJson());
            if (!prepared.execution.basePackageManifestJson.isEmpty())
                atomicText(new File(directory,"pipeline/package_manifest.json"),prepared.execution.basePackageManifestJson);
            for (ModelRole role : ModelRole.values()) {
                ResearchStageExecutionConfig stage = prepared.execution.stage(role);
                if (stage.enabled && stage.modelRef != null)
                    atomicText(new File(directory,"pipeline/"+role.wireName()+"_manifest.json"),stage.modelRef.modelManifestJson());
            }
        }
        for (String path : new String[]{"samples/attempts.jsonl","samples/crops.jsonl","samples/write_states.jsonl",
                "telemetry/events.jsonl","telemetry/traces.jsonl","telemetry/thermal.jsonl"})
            atomicText(new File(directory,path),"");
        atomicText(new File(directory,"session.json"),json.toString(2));
        ResearchSessionStore store = new ResearchSessionStore(directory,json,capacity,attemptCapacity,encoder);
        try { store.writer.submit(() -> {}).get(10,TimeUnit.SECONDS); }
        catch (Exception error) { store.writer.shutdownNow(); throw error; }
        return store;
    }
    private ResearchSessionStore(File directory,JSONObject metadata,int capacity,int attemptCapacity,ImageEncoder encoder) {
        this.directory = directory; this.metadata = metadata; this.encoder = encoder;
        this.attemptCapacity = attemptCapacity; attempts = new Semaphore(attemptCapacity);
        writer = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),r -> {
            Thread thread = new Thread(r,"research-session-writer"); thread.setDaemon(true); return thread;
        },new ThreadPoolExecutor.AbortPolicy());
        preparedElapsedNanos = SystemClock.elapsedRealtimeNanos();
    }
    public String sessionId() { return metadata.optString("session_id"); }
    public File directory() { return directory; }
    public State state() { return state; }
    public boolean accepting() { return accepting; }
    public long droppedSampleCount() { return dropped.get(); }
    public synchronized void abortPreparation(String reason) {
        accepting=false; state=State.ERROR; writer.shutdown();
        try {
            metadata.put("state","ERROR").put("collection_complete",false)
                    .put("completion_reason",metadata.optString("completion_reason").isEmpty()
                            ? "preparation_failed" : metadata.optString("completion_reason"))
                    .put("last_write_error",reason);
            atomicText(new File(directory,"session.json"),metadata.toString(2));
        } catch (Exception ignored) { }
    }
    public synchronized void startAt(long wallMillis,long elapsedNanos) throws Exception {
        if (state != State.PREPARED || elapsedNanos < preparedElapsedNanos)
            throw new IllegalStateException("Magazyn sesji nie został przygotowany przed t0");
        metadata.put("started_at",Instant.ofEpochMilli(wallMillis).toString());
        metadata.put("started_elapsed_nanos",elapsedNanos).put("storage_prepared_elapsed_nanos",preparedElapsedNanos);
        state = State.RUNNING; metadata.put("state",state.name());
        // Durable state transition before admission; directory/images setup happened before t0.
        atomicText(new File(directory,"session.json"),metadata.toString(2));
        accepting = true;
    }
    public synchronized void closeAdmission(String reason,long wallMillis,long elapsedNanos) {
        if (state != State.RUNNING) return;
        accepting = false; state = State.FINALIZING;
        try {
            metadata.put("state",state.name()).put("finished_at",Instant.ofEpochMilli(wallMillis).toString())
                    .put("finished_elapsed_nanos",elapsedNanos).put("completion_reason",reason);
        } catch (Exception error) { fail("session_stop_metadata"); }
    }
    public synchronized AcquisitionAttemptRecord beginAttempt(ContinuityStamp stamp,String roiPolicy,float zoom,long entity,long vehicle) {
        if (!accepting) return null;
        String id = sessionId()+"-a"+String.format(Locale.ROOT,"%08d",sequence.incrementAndGet());
        if (!attempts.tryAcquire()) {
            AcquisitionAttemptRecord rejected = new AcquisitionAttemptRecord(this,id,stamp,roiPolicy,zoom,entity,vehicle);
            reject(rejected,"attempt_capacity"); return null;
        }
        return new AcquisitionAttemptRecord(this,id,stamp,roiPolicy,zoom,entity,vehicle);
    }
    /** An already admitted inference batch must retain MT metadata even at STOP or image capacity. */
    synchronized AcquisitionAttemptRecord beginInvocationAttempt(ContinuityStamp stamp,String policy,float zoom,long entity,long vehicle) {
        if (state != State.RUNNING && state != State.FINALIZING) return null;
        String id = sessionId()+"-a"+String.format(Locale.ROOT,"%08d",sequence.incrementAndGet());
        AcquisitionAttemptRecord record = new AcquisitionAttemptRecord(this,id,stamp,policy,zoom,entity,vehicle);
        record.ownsAttemptPermit = attempts.tryAcquire();
        if (!record.ownsAttemptPermit) {
            record.put("missing_evidence_reason","attempt_capacity"); fail("attempt_capacity:"+id);
        }
        return record;
    }
    void copyImage(AcquisitionAttemptRecord record,Bitmap source,boolean crop) {
        if (!record.ownsAttemptPermit) return;
        if (source == null || source.isRecycled()) { record.put("missing_evidence_reason","image_unavailable"); fail("image_unavailable"); return; }
        long bytes = (long)source.getWidth()*source.getHeight()*4;
        if (imageBytes.addAndGet(bytes) > MAX_IMAGE_BYTES) {
            imageBytes.addAndGet(-bytes); record.put("missing_evidence_reason","image_memory_budget"); fail("image_memory_budget"); return;
        }
        Bitmap copy = null;
        try { copy = source.copy(Bitmap.Config.ARGB_8888,false); }
        catch (OutOfMemoryError | RuntimeException error) { record.put("missing_evidence_reason","image_copy_failed"); }
        if (copy == null) { imageBytes.addAndGet(-bytes); fail("image_copy_failed"); return; }
        // Retain the actual MT input when downstream MZ replaces the primary evidence with a crop.
        if (crop && !record.plateCrop && record.image != null) {
            record.mtInputImage = record.image; record.mtInputImageBytes = record.imageBytes;
            record.put("mt_input_evidence_kind",record.data.optString("evidence_kind"));
            record.image = null; record.imageBytes = 0;
        }
        releasePrimaryImage(record);
        record.image = copy; record.imageBytes = bytes; record.plateCrop = crop;
        record.put("evidence_kind",crop ? "plate_crop" : "mt_input_roi");
        record.put("missing_evidence_reason","");
    }
    private void releasePrimaryImage(AcquisitionAttemptRecord record) {
        if (record.image != null && !record.image.isRecycled()) record.image.recycle();
        imageBytes.addAndGet(-record.imageBytes); record.image = null; record.imageBytes = 0;
    }
    private void releaseImage(AcquisitionAttemptRecord record) {
        releasePrimaryImage(record);
        if (record.mtInputImage != null && !record.mtInputImage.isRecycled()) record.mtInputImage.recycle();
        imageBytes.addAndGet(-record.mtInputImageBytes); record.mtInputImage = null; record.mtInputImageBytes = 0;
    }
    public void submit(AcquisitionAttemptRecord record) {
        if (record == null) return;
        if (!accepting && state != State.PREPARED && !record.data.optBoolean("stale_or_cancelled")) record.cancel("session_stopped");
        try { writer.execute(() -> writeAttempt(record)); }
        catch (RejectedExecutionException error) {
            if (record.data.optBoolean("mt_executed")) record.put("mt_input_missing_evidence_reason","writer_queue_full");
            reject(record,"writer_queue_full"); releaseImage(record);
            if (record.ownsAttemptPermit) attempts.release();
        }
    }
    public void appendTelemetry(String file,String line) {
        if (!accepting || line == null) return;
        if (!Arrays.asList("traces.jsonl","events.jsonl","thermal.jsonl","frame_flow.jsonl").contains(file))
            throw new IllegalArgumentException("Unknown research telemetry stream");
        try { writer.execute(() -> { try { append(new File(directory,"telemetry/"+file),line+"\n"); }
            catch (Exception error) { droppedTelemetry.incrementAndGet(); lastFailure="telemetry_write_failed"; } }); }
        catch (RejectedExecutionException error) { droppedTelemetry.incrementAndGet(); lastFailure="telemetry_queue_full"; }
    }
    private void fail(String reason) { dropped.incrementAndGet(); lastFailure = reason; }
    public void recordMetadataFailure(String reason) { droppedTelemetry.incrementAndGet(); lastFailure=reason; }
    private void reject(AcquisitionAttemptRecord record,String reason) {
        fail(reason+":"+record.attemptId);
        record.put("write_state","FAILED"); record.put("missing_evidence_reason",reason);
        if (!rejectedRecords.offer(record.data.toString())) lastFailure="failure_journal_capacity";
    }
    private void flushRejected() throws IOException {
        String row;
        while ((row=rejectedRecords.poll()) != null) append(new File(directory,"samples/attempts.jsonl"),row+"\n");
    }
    private void writeAttempt(AcquisitionAttemptRecord record) {
        try {
            append(new File(directory,"samples/write_states.jsonl"),new JSONObject().put("attempt_id",record.attemptId)
                    .put("state","QUEUED").toString()+"\n");
            if (record.mtInputImage != null) {
                String inputEntry = "samples/evidence/"+record.attemptId+".jpg";
                File temp = new File(directory,inputEntry+".tmp");
                encoder.write(record.mtInputImage,temp);
                Files.move(temp.toPath(),new File(directory,inputEntry).toPath(),StandardCopyOption.REPLACE_EXISTING);
                record.put("mt_input_evidence_entry",inputEntry);
            }
            if (record.image != null) {
                String entry = "samples/"+(record.plateCrop ? "crops/" : "evidence/")+record.attemptId+".jpg";
                File target = new File(directory,entry), temp = new File(directory,entry+".tmp");
                encoder.write(record.image,temp);
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
                record.put("evidence_entry",entry);
                if (!record.plateCrop) record.put("mt_input_evidence_entry",entry);
                if (record.plateCrop) {
                    com.example.alpr_v1.capture.CapturedPlateItem item = record.cropMetadata();
                    JSONObject crop = new JSONObject(ResearchArchive.annotationsJsonl(Collections.singletonList(item)).trim());
                    String csv = ResearchArchive.cropIndexCsv(Collections.singletonList(item),false);
                    crop.put("_index_row",csv.substring(csv.indexOf('\n')+1).trim());
                    crop.put("evidence_entry",entry).put("consensus_confidence",record.data.opt("consensus_confidence"));
                    append(new File(directory,"samples/crops.jsonl"),crop.toString()+"\n");
                }
            } else if (record.data.optString("missing_evidence_reason").isEmpty()) {
                record.put("missing_evidence_reason","no_image_captured"); fail("no_image_captured:"+record.attemptId);
            }
            record.put("write_state",record.image == null ? "FAILED" : "WRITTEN");
            ensureMtInputReason(record);
            append(new File(directory,"samples/attempts.jsonl"),record.data.toString()+"\n");
            written.incrementAndGet();
        } catch (Exception error) {
            fail("sample_write_failed:"+record.attemptId+":"+error.getClass().getSimpleName());
            record.put("write_state","FAILED"); record.put("missing_evidence_reason","sample_write_failed");
            ensureMtInputReason(record);
            try { append(new File(directory,"samples/attempts.jsonl"),record.data.toString()+"\n"); } catch (Exception ignored) { }
        } finally {
            try { append(new File(directory,"samples/write_states.jsonl"),new JSONObject().put("attempt_id",record.attemptId)
                    .put("state",record.data.optString("write_state","FAILED")).toString()+"\n"); }
            catch (Exception error) { fail("write_state_failed"); }
            releaseImage(record); if (record.ownsAttemptPermit) attempts.release();
            try { flushRejected(); } catch (Exception error) { lastFailure="failure_journal_write_failed"; }
            checkpoint();
        }
    }
    private void ensureMtInputReason(AcquisitionAttemptRecord record) {
        if (record.data.optBoolean("mt_executed") && record.data.optString("mt_input_evidence_entry").isEmpty()
                && record.data.optString("mt_input_missing_evidence_reason").isEmpty()) {
            String reason = record.data.optString("missing_evidence_reason");
            record.put("mt_input_missing_evidence_reason",reason.isEmpty() ? "no_mt_input_captured" : reason);
            fail("missing_mt_input:"+record.attemptId);
        }
    }
    private void checkpoint() {
        long now=SystemClock.elapsedRealtimeNanos();
        if (now-lastCheckpointNanos<1_000_000_000L) return;
        lastCheckpointNanos=now;
        try {
            synchronized (this) {
                metadata.put("attempts_seen",sequence.get()).put("attempt_count",written.get())
                        .put("inflight_attempt_count",attemptCapacity-attempts.availablePermits())
                        .put("dropped_sample_count",dropped.get()).put("dropped_telemetry_count",droppedTelemetry.get())
                        .put("last_write_error",lastFailure);
                atomicText(new File(directory,"session.json"),metadata.toString(2));
            }
            long losses=dropped.get()+droppedTelemetry.get();
            if (losses>lastReportedDrops) {
                append(new File(directory,"telemetry/events.jsonl"),new JSONObject().put("event_type","research_collection_loss")
                        .put("dropped_sample_count",dropped.get()).put("dropped_telemetry_count",droppedTelemetry.get())
                        .put("reason",lastFailure).toString()+"\n"); lastReportedDrops=losses;
            }
        } catch (Exception error) { recordMetadataFailure("session_checkpoint_failed"); }
    }
    public static final class Telemetry {
        public final String report,traces,thermal,frameFlow,events,log;
        public Telemetry(String report,String traces,String thermal,String frameFlow,String events,String log) {
            this.report=report; this.traces=traces; this.thermal=thermal; this.frameFlow=frameFlow; this.events=events; this.log=log;
        }
    }
    /** Called off the UI/inference thread, after the pipeline's final in-flight work. */
    public File finish(Telemetry telemetry,ResearchExecutionConfig frozen) throws Exception {
        if (accepting || state != State.FINALIZING) throw new IllegalStateException("Najpierw zakończ przyjmowanie prób");
        try {
            boolean attemptsDrained=attempts.tryAcquire(attemptCapacity,60,TimeUnit.SECONDS);
            if (!attemptsDrained) fail("inflight_attempt_timeout");
            writer.shutdown();
            if (!writer.awaitTermination(60,TimeUnit.SECONDS)) { fail("writer_drain_timeout"); throw new IOException("Nie zakończono kolejki zapisu"); }
            flushRejected();
            saveTelemetry(telemetry);
            State completed = dropped.get()+droppedTelemetry.get() == 0 ? State.COMPLETED : State.PARTIAL;
            synchronized (this) {
                metadata.put("state","FINALIZING").put("collection_complete",false)
                        .put("inflight_attempt_count",attemptsDrained ? 0 : attemptCapacity-attempts.availablePermits())
                        .put("dropped_sample_count",dropped.get()).put("dropped_telemetry_count",droppedTelemetry.get())
                        .put("attempt_count",written.get()).put("attempts_seen",sequence.get()).put("last_write_error",lastFailure);
                atomicText(new File(directory,"session.json"),metadata.toString(2));
            }
            File archive = buildArchive(directory,frozen,completed);
            state = State.valueOf(new JSONObject(read(new File(directory,"session.json"))).getString("state"));
            return archive;
        } catch (Exception error) {
            state = State.ERROR;
            synchronized (this) {
                metadata.put("state","ERROR").put("collection_complete",false).put("last_write_error",error.toString());
                try { atomicText(new File(directory,"session.json"),metadata.toString(2)); } catch (Exception ignored) { }
            }
            throw error;
        }
    }
    private void saveTelemetry(Telemetry value) throws Exception {
        atomicText(new File(directory,"telemetry/report.json"),value.report);
        atomicText(new File(directory,"telemetry/traces.csv"),value.traces);
        atomicText(new File(directory,"telemetry/thermal.csv"),value.thermal);
        atomicText(new File(directory,"telemetry/frame_flow.csv"),value.frameFlow);
        // The live stream survives a process interruption. Preserve final-only events as a separate snapshot.
        atomicText(new File(directory,"telemetry/events-final.jsonl"),value.events);
        atomicText(new File(directory,"telemetry/application.log"),value.log);
        if (dropped.get()+droppedTelemetry.get() > 0)
            append(new File(directory,"telemetry/events.jsonl"),new JSONObject().put("event_type","research_collection_loss")
                    .put("dropped_sample_count",dropped.get()).put("dropped_telemetry_count",droppedTelemetry.get())
                    .put("reason",lastFailure).toString()+"\n");
    }
    public static List<File> recoverInterrupted(Context context) throws Exception {
        File root = sessionsRoot(context); Files.createDirectories(root.toPath());
        List<File> recovered = new ArrayList<>();
        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null) return recovered;
        IOException recoveryFailure = null;
        for (File directory : directories) {
            try {
            File file = new File(directory,"session.json");
            if (!file.isFile()) continue;
            JSONObject json = new JSONObject(read(file));
            String state = json.optString("state");
            if (PROCESS.equals(json.optString("process_owner"))) continue;
            boolean interrupted = state.equals("RUNNING") || state.equals("FINALIZING") || state.equals("PREPARED");
            File archive = new File(directory,"final/"+json.getString("session_id")+".alprsession");
            if (interrupted || !archive.isFile() && (state.equals("PARTIAL") || state.equals("ERROR") || state.equals("COMPLETED"))) {
                json.put("state","PARTIAL").put("collection_complete",false)
                        .put("completion_reason",interrupted ? "process_interrupted" : json.optString("completion_reason","archive_recovery"))
                        .put("finished_at",Instant.now().toString())
                        .put("pending_sample_loss_unknown",true);
                atomicText(file,json.toString(2)); recovered.add(directory);
                buildArchive(directory,null);
            }
            } catch (Exception error) { recoveryFailure = new IOException("Nie odzyskano sesji "+directory.getName(),error); }
        }
        if (recoveryFailure != null) throw recoveryFailure;
        return recovered;
    }
    public static File buildArchive(File directory,ResearchExecutionConfig frozen) throws Exception {
        return buildArchive(directory,frozen,null);
    }
    private static File buildArchive(File directory,ResearchExecutionConfig frozen,State completed) throws Exception {
        JSONObject session = new JSONObject(read(new File(directory,"session.json")));
        if (completed != null) session.put("state",completed.name()).put("collection_complete",completed == State.COMPLETED);
        finalizeSamples(directory,session);
        restoreTelemetryCsv(directory);
        JSONObject report = new JSONObject(readOr(new File(directory,"telemetry/report.json"),"{}"));
        if (!report.has("schema")) report.put("schema","alpr.mobile_benchmark_report.v1");
        if (!report.has("report_id")) report.put("report_id",session.getString("session_id"));
        // Recovery may run under a different APK: use provenance frozen before this session's START.
        if (session.has("app_build")) report.put("app_build",session.getJSONObject("app_build"));
        if (session.has("app_version")) report.put("app_version",session.getString("app_version"));
        report.put("research_collection",session);
        // Automatic runs never inherit manual gallery ground truth or sampling counts.
        JSONObject crops = report.optJSONObject("crop_session");
        if (crops == null) crops = new JSONObject();
        crops.put("session_id",session.getString("session_id")).put("active",false)
                .put("collected_count",session.optLong("crop_count")).put("collection_mode","automatic")
                .put("records",new org.json.JSONArray()).put("records_file","samples/annotations.jsonl");
        report.put("crop_session",crops);
        report.put("quality",com.example.alpr_v1.metrics.MetricsCollector.pendingDesktopReviewQuality(session.optLong("crop_count")));
        if (!report.has("experiment_session")) report.put("experiment_session",session);
        if (!report.has("experiment")) report.put("experiment",new JSONObject(session.toString())
                .put("effective_execution_config",session.optJSONObject("execution")));
        if (session.has("model_refs")) report.put("model_refs",session.getJSONObject("model_refs"));
        if (session.has("execution")) report.put("research_execution_config",session.getJSONObject("execution"));
        if (!report.has("execution") && session.has("execution")) {
            JSONObject stages=session.getJSONObject("execution").optJSONObject("stages");
            if (stages != null) report.put("execution",new JSONObject().put("vehicle",stages.optJSONObject("mp"))
                    .put("plate",stages.optJSONObject("mt")).put("character",stages.optJSONObject("mz")));
        }
        String id = session.getString("session_id");
        File destination = new File(directory,"final/"+id+".alprsession");
        Files.createDirectories(destination.getParentFile().toPath());
        File temporary = new File(destination.getPath()+".tmp");
        try (OutputStream output = new FileOutputStream(temporary)) {
            ResearchArchive.writePersistentResearchSession(output,report.toString(2),
                    readOr(new File(directory,"telemetry/traces.csv"),""),
                    readOr(new File(directory,"telemetry/thermal.csv"),""),
                    readOr(new File(directory,"telemetry/frame_flow.csv"),""),
                    readOr(new File(directory,"telemetry/events.jsonl"),""),
                    readOr(new File(directory,"telemetry/application.log"),""),directory,frozen);
        }
        ResearchArchive.verifyEntryHashes(temporary);
        Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);
        atomicText(new File(directory,"session.json"),session.toString(2));
        return destination;
    }
    private static void restoreTelemetryCsv(File directory) throws Exception {
        File traces = new File(directory,"telemetry/traces.jsonl");
        if (traces.isFile() && traces.length()>0) {
            try (BufferedWriter csv = Files.newBufferedWriter(new File(directory,"telemetry/traces.csv").toPath(),StandardCharsets.UTF_8)) {
                boolean[] first = {true};
                forEachRecord(traces,row -> {
                    if (first[0]) { csv.write(row.optString("_csv_header")); first[0]=false; }
                    csv.write(row.optString("_csv_row"));
                });
            }
        }
        for (String name : new String[]{"thermal","frame_flow"}) {
            File csvFile = new File(directory,"telemetry/"+name+".csv");
            if (csvFile.isFile()) continue;
            String header = name.equals("thermal")
                    ? "experiment_session_id,elapsed_ms,battery_temperature_c,thermal_status,thermal_headroom,headroom_available,battery_percent,charging,available_memory_bytes"
                    : "experiment_session_id,elapsed_ms,frames_received,frames_processed,frames_skipped_frame_gate,frames_skipped_camera_transform,frames_skipped_hard_scene_reset,frames_skipped_continuity_hold,frames_skipped_continuity_reacquire,estimated_upstream_gaps";
            try (BufferedWriter csv = Files.newBufferedWriter(csvFile.toPath(),StandardCharsets.UTF_8)) {
                csv.write(header); csv.newLine();
                forEachRecord(new File(directory,"telemetry/"+name+".jsonl"),row -> csv.write(csvRow(row,header.split(","))));
            }
        }
    }

    private static void finalizeSamples(File directory,JSONObject session) throws Exception {
        Map<String,long[]> owners = new HashMap<>();
        long[] counts = {0L,0L,0L}; // attempts, crops, missing images
        long malformed = malformedLines(new File(directory,"samples/attempts.jsonl"))
                + malformedLines(new File(directory,"samples/crops.jsonl"));
        long malformedTelemetry = 0L;
        for (String name : new String[]{"traces","events","events-final","thermal","frame_flow"})
            malformedTelemetry += malformedLines(new File(directory,"telemetry/"+name+".jsonl"));
        forEachRecord(new File(directory,"samples/attempts.jsonl"),row -> {
            counts[0]++;
            String evidence = row.optString("evidence_entry");
            boolean missingPrimary = row.optString("write_state").equals("WRITTEN")
                    && (evidence.isEmpty() || !new File(directory,evidence).isFile());
            if (missingPrimary) counts[2]++;
            String inputEvidence = row.optString("mt_input_evidence_entry");
            if (row.optBoolean("mt_executed") && row.has("mt_input_evidence_entry")
                    && (inputEvidence.isEmpty() || !new File(directory,inputEvidence).isFile())
                    && !(missingPrimary && inputEvidence.equals(evidence))) counts[2]++;
            if (row.optLong("entity_id")>0 && row.optLong("plate_track_id")>0)
                owners.put(row.optLong("scene_generation")+"/"+row.optLong("plate_track_id"),
                        new long[]{row.optLong("entity_id"),row.optLong("vehicle_track_id")});
        });
        try (BufferedWriter attempts = Files.newBufferedWriter(new File(directory,"samples/attempts.csv").toPath(),StandardCharsets.UTF_8)) {
            attempts.write(String.join(",",ATTEMPT_COLUMNS)); attempts.newLine();
            forEachRecord(new File(directory,"samples/attempts.jsonl"),row -> {
                promote(row,owners);
                if (row.optString("write_state").equals("WRITTEN")
                        && !new File(directory,row.optString("evidence_entry")).isFile())
                    row.put("write_state","FAILED").put("missing_evidence_reason","missing_image");
                if (row.optBoolean("mt_executed") && row.has("mt_input_evidence_entry")
                        && !new File(directory,row.optString("mt_input_evidence_entry")).isFile())
                    row.put("mt_input_missing_evidence_reason",row.optString("mt_input_missing_evidence_reason").isEmpty()
                            ? "missing_mt_input_image" : row.optString("mt_input_missing_evidence_reason"));
                attempts.write(csvRow(row,ATTEMPT_COLUMNS));
            });
        }
        try (BufferedWriter index = Files.newBufferedWriter(new File(directory,"samples/index.csv").toPath(),StandardCharsets.UTF_8);
             BufferedWriter annotations = Files.newBufferedWriter(new File(directory,"samples/annotations.jsonl").toPath(),StandardCharsets.UTF_8)) {
            index.write(ResearchArchive.cropIndexCsv(Collections.emptyList()));
            forEachRecord(new File(directory,"samples/crops.jsonl"),row -> {
                counts[1]++;
                promote(row,owners);
                index.write(row.optString("_index_row")+","+csvRow(row,new String[]{"attempt_id","subject_key",
                        "scene_generation","entity_id","vehicle_track_id","plate_track_id"}));
                row.remove("_index_row"); annotations.write(row.toString()); annotations.newLine();
            });
        }
        atomicText(new File(directory,"samples/schema.json"),new JSONObject().put("schema",SAMPLE_SCHEMA)
                .put("subject_identity","scene_generation+entity_id").put("human_review","desktop")
                .put("mt_invocation_identity","one_backend_execution_one_input")
                .put("mt_detection_index_base",0).put("mt_detection_order","decoder_output")
                .put("attempts_file","samples/attempts.csv").put("crops_file","samples/index.csv").toString(2));
        long incomplete = malformed+malformedTelemetry+counts[2];
        long previousIncomplete = session.optLong("integrity_loss_count");
        long previousTelemetryIncomplete = session.optLong("integrity_telemetry_loss_count");
        session.put("attempt_count",counts[0]).put("crop_count",counts[1]).put("integrity_loss_count",incomplete)
                .put("integrity_telemetry_loss_count",malformedTelemetry)
                .put("dropped_sample_count",Math.max(0L,session.optLong("dropped_sample_count")
                        -previousIncomplete+previousTelemetryIncomplete)+malformed+counts[2])
                .put("dropped_telemetry_count",Math.max(0L,session.optLong("dropped_telemetry_count")
                        -previousTelemetryIncomplete)+malformedTelemetry);
        if (incomplete>0 || session.optLong("dropped_sample_count")>0 || session.optLong("dropped_telemetry_count")>0) {
            session.put("state","PARTIAL").put("collection_complete",false);
        }
        if (incomplete>0) {
            session.put("last_write_error","sample_integrity_failure");
            append(new File(directory,"telemetry/events.jsonl"),new JSONObject().put("event_type","research_integrity_loss")
                    .put("malformed_records",malformed).put("malformed_telemetry_records",malformedTelemetry)
                    .put("missing_images",counts[2]).toString()+"\n");
        }
    }
    private static long malformedLines(File file) throws IOException {
        if (!file.isFile()) return 0L;
        long count=0;
        try (BufferedReader reader=Files.newBufferedReader(file.toPath(),StandardCharsets.UTF_8)) {
            String line; while ((line=reader.readLine())!=null) {
                if (line.trim().isEmpty()) continue;
                try { new JSONObject(line); } catch (org.json.JSONException invalid) { count++; }
            }
        }
        return count;
    }
    private static void promote(JSONObject row,Map<String,long[]> owners) throws Exception {
        long[] owner = owners.get(row.optLong("scene_generation")+"/"+row.optLong("plate_track_id"));
        if (row.optLong("entity_id") == 0 && owner != null) row.put("entity_id",owner[0]).put("vehicle_track_id",owner[1]);
        row.put("subject_key",ResearchSampleIdentity.subjectKey(row.optString("session_id"),row.optLong("scene_generation"),
                row.optLong("entity_id"),row.optLong("plate_track_id"),row.optString("attempt_id")));
    }
    interface RowConsumer { void accept(JSONObject row) throws Exception; }
    static void forEachRecord(File file,RowConsumer consumer) throws Exception {
        if (!file.isFile()) return;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(),StandardCharsets.UTF_8)) {
            String line;
            while ((line=reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                JSONObject row;
                try { row = new JSONObject(line); } catch (org.json.JSONException truncated) { continue; }
                consumer.accept(row);
            }
        }
    }
    public static String csvRow(JSONObject row,String[] columns) {
        StringBuilder line = new StringBuilder();
        for (String column : columns) {
            if (line.length()>0) line.append(',');
            Object value = row.opt(column);
            line.append('"').append(value == null || value == JSONObject.NULL ? "" : value.toString().replace("\"","\"\""))
                    .append('"');
        }
        return line.append('\n').toString();
    }
    static void encodeJpeg(Bitmap bitmap,File target) throws IOException {
        try (FileOutputStream output = new FileOutputStream(target)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG,94,output)) throw new IOException("JPEG encoding failed");
            output.getFD().sync();
        }
    }
    static void append(File file,String text) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file,true)) { output.write(text.getBytes(StandardCharsets.UTF_8)); output.getFD().sync(); }
    }
    static void atomicText(File file,String text) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        File temporary = new File(file.getPath()+".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) { output.write(text.getBytes(StandardCharsets.UTF_8)); output.getFD().sync(); }
        Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    static String read(File file) throws IOException { return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8); }
    static String readOr(File file,String fallback) throws IOException { return file.isFile() ? read(file) : fallback; }
}
