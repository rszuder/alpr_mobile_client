package com.example.alpr_v1.experiment;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.metrics.ResearchArchive;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ResearchSessionStoreInstrumentedTest {
    private File files;
    private Context context;
    private final List<ResearchSessionStore> stores = new ArrayList<>();
    @Before public void setup() throws Exception {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        files=new File(target.getCacheDir(),"research-store-test-"+UUID.randomUUID());
        Files.createDirectories(files.toPath());
        context=new ContextWrapper(target) {
            @Override public File getFilesDir() { return files; }
            @Override public Context getApplicationContext() { return this; }
        };
    }
    @After public void cleanup() throws Exception {
        for (ResearchSessionStore store:stores) {
            java.lang.reflect.Field field=ResearchSessionStore.class.getDeclaredField("writer"); field.setAccessible(true);
            ExecutorService writer=(ExecutorService)field.get(store); writer.shutdownNow(); writer.awaitTermination(5,TimeUnit.SECONDS);
        }
        try (java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(files.toPath())) {
            for (java.nio.file.Path path:(Iterable<java.nio.file.Path>)paths.sorted(Comparator.reverseOrder())::iterator)
                Files.deleteIfExists(path);
        }
    }
    private ExperimentSession.Prepared prepared(ExperimentSession session) {
        return session.prepare("quality","R0",TimerConfig.of(true,5),ThermalConfig.disabled(),ExperimentIdentity.defaults(),null);
    }
    private ResearchSessionStore store(ExperimentSession.Prepared prepared) throws Exception {
        ResearchSessionStore value=ResearchSessionStore.prepare(ResearchSessionStore.sessionsRoot(context),prepared,
                32,16,0,ResearchSessionStore::encodeJpeg); stores.add(value); return value;
    }
    private ResearchSessionStore running() throws Exception {
        ExperimentSession session=new ExperimentSession(); ExperimentSession.Prepared prepared=prepared(session);
        ResearchSessionStore store=store(prepared); long elapsed=SystemClock.elapsedRealtimeNanos(), wall=System.currentTimeMillis();
        store.startAt(wall,elapsed); assertTrue(session.startPrepared(prepared,wall,elapsed)); return store;
    }
    private AcquisitionAttemptRecord sample(ResearchSessionStore store,long scene,long entity,long track,boolean crop) {
        AcquisitionAttemptRecord record=store.beginAttempt(new ContinuityStamp(scene,0,0,1L),"R0",1f,entity,entity);
        if (record == null) return null;
        record.associate(entity,entity,track); record.put("mt_status",crop ? "VALID_QUAD" : "NO_DETECTION");
        Bitmap image=Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888); image.eraseColor(Color.GREEN);
        if (crop) {
            record.copyPlateCrop(image); record.put("mz_executed",true); record.put("mz_status","READ");
            record.put("prediction","WI1234A"); record.put("consensus_prediction","WI1234A");
        } else record.copyEvidence(image);
        image.recycle(); return record;
    }
    private File finish(ResearchSessionStore store) throws Exception {
        store.closeAdmission("timer",System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
        return store.finish(new ResearchSessionStore.Telemetry("{\"schema\":\"alpr.mobile_benchmark_report.v1\"}",
                "frame_id\n","thermal\n","flow\n","","test log"),null);
    }
    @Test public void preparedStorageExistsBeforeDomainAndMetricsStartAndUsesOneT0() throws Exception {
        ExperimentSession domain=new ExperimentSession(); MetricsCollector metrics=new MetricsCollector();
        ExperimentSession.Prepared prepared=prepared(domain); ResearchSessionStore store=store(prepared);
        assertEquals("PREPARED",new JSONObject(ResearchSessionStore.read(new File(store.directory(),"session.json"))).getString("state"));
        assertFalse(domain.isRunning()); assertFalse(metrics.isMeasurementSessionActive()); assertFalse(store.accepting());
        long elapsed=SystemClock.elapsedRealtimeNanos(), wall=System.currentTimeMillis();
        assertTrue(store.preparedElapsedNanos<=elapsed);
        store.startAt(wall,elapsed); domain.startPrepared(prepared,wall,elapsed);
        metrics.startMeasurementSession(wall,elapsed,System.nanoTime());
        assertTrue(store.accepting()); assertTrue(domain.isRunning()); assertTrue(metrics.isMeasurementSessionActive());
        assertEquals(prepared.sessionId,domain.sessionId());
        assertEquals(elapsed,new JSONObject(ResearchSessionStore.read(new File(store.directory(),"session.json"))).getLong("started_elapsed_nanos"));
        domain.finish(ExperimentSession.CompletionReason.TIMER); finish(store);
        assertFalse(store.accepting()); assertEquals(ResearchSessionStore.State.COMPLETED,store.state());
    }
    @Test public void unwritableRootAndLowSpaceBlockPreparationWithoutStartingDomain() throws Exception {
        ExperimentSession domain=new ExperimentSession(); ExperimentSession.Prepared prepared=prepared(domain);
        File blocker=new File(files,"not-a-directory"); Files.write(blocker.toPath(),new byte[]{1});
        try { ResearchSessionStore.prepare(blocker,prepared,2,2,0,ResearchSessionStore::encodeJpeg); fail(); }
        catch (IOException expected) { assertFalse(domain.isRunning()); }
        try { ResearchSessionStore.prepare(new File(files,"low-space"),prepared,2,2,Long.MAX_VALUE,ResearchSessionStore::encodeJpeg); fail(); }
        catch (IOException expected) { assertFalse(domain.isRunning()); }
    }
    @Test public void allMzAttemptsSurvivePreviewEvictionAndHaveHashesAndDesktopReviewDefaults() throws Exception {
        ResearchSessionStore store=running();
        for (int i=0;i<3;i++) store.submit(sample(store,12L,104L,7L,true));
        File archive=finish(store); ResearchArchive.verifyEntryHashes(archive);
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            String attempts=entry(zip,"samples/attempts.csv"), crops=entry(zip,"samples/index.csv");
            assertEquals(4,attempts.split("\n").length); assertEquals(4,crops.split("\n").length);
            assertTrue(attempts.contains(store.sessionId()+"/sg-12/entity-104"));
            assertTrue(crops.contains("attempt_id,subject_key,scene_generation,entity_id,vehicle_track_id,plate_track_id"));
            assertEquals(3,entry(zip,"samples/annotations.jsonl").split("\n").length);
            for (String line:entry(zip,"samples/annotations.jsonl").split("\n")) {
                JSONObject record=new JSONObject(line); assertEquals("not_reviewed",record.getString("verification_status"));
                assertEquals("",record.getString("ground_truth_text"));
                assertNotNull(zip.getEntry("samples/crops/"+record.getString("capture_id")+".jpg"));
            }
            JSONObject hashes=new JSONObject(entry(zip,"manifest.json")).getJSONObject("entry_sha256");
            assertTrue(hashes.has("samples/attempts.csv")); assertTrue(hashes.has("samples/schema.json"));
        }
    }
    @Test public void mtMissHasEvidenceAndCancelledWorkKeepsItsOwnReason() throws Exception {
        ResearchSessionStore store=running(); AcquisitionAttemptRecord miss=sample(store,1,42,0,false);
        store.submit(miss);
        AcquisitionAttemptRecord cancelled=sample(store,1,42,1,true); cancelled.cancel("scene_superseded");
        store.closeAdmission("manual",System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos()); store.submit(cancelled);
        File archive=store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),null);
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            assertNotNull(zip.getEntry("samples/evidence/"+miss.attemptId+".jpg"));
            String attempts=entry(zip,"samples/attempts.csv"); assertTrue(attempts.contains("NO_DETECTION"));
            assertTrue(attempts.contains("scene_superseded")); assertFalse(attempts.contains("session_stopped"));
        }
    }
    @Test public void laterEntityAssociationPromotesFallbackAndNewStaticSceneStaysDistinct() throws Exception {
        ResearchSessionStore store=running();
        store.submit(sample(store,1,0,7,true)); store.submit(sample(store,1,104,7,true));
        store.submit(sample(store,2,104,7,true)); File archive=finish(store);
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            String[] rows=entry(zip,"samples/annotations.jsonl").split("\n");
            assertEquals(new JSONObject(rows[0]).getString("subject_key"),new JSONObject(rows[1]).getString("subject_key"));
            assertNotEquals(new JSONObject(rows[1]).getString("subject_key"),new JSONObject(rows[2]).getString("subject_key"));
        }
    }
    @Test public void failedImageWriteMakesSessionPartialAndKeepsFailedAttempt() throws Exception {
        ExperimentSession domain=new ExperimentSession();
        ResearchSessionStore store=ResearchSessionStore.prepare(ResearchSessionStore.sessionsRoot(context),prepared(domain),
                4,4,0,(bitmap,file)->{throw new IOException("simulated disk failure");}); stores.add(store);
        store.startAt(System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
        store.submit(sample(store,1,1,1,true)); File archive=finish(store);
        assertEquals(ResearchSessionStore.State.PARTIAL,store.state()); assertTrue(store.droppedSampleCount()>0);
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            assertFalse(new JSONObject(entry(zip,"session.json")).getBoolean("collection_complete"));
            assertTrue(entry(zip,"samples/attempts.csv").contains("FAILED"));
            assertTrue(entry(zip,"events.jsonl").contains("research_collection_loss"));
        }
    }
    @Test public void boundedQueueReportsOverflowAndFinalizationWaitsForWriter() throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        ExperimentSession domain=new ExperimentSession();
        ResearchSessionStore store=ResearchSessionStore.prepare(ResearchSessionStore.sessionsRoot(context),prepared(domain),1,4,0,
                (bitmap,file)->{ entered.countDown(); try { if (!release.await(5,TimeUnit.SECONDS)) throw new IOException("timeout"); }
                    catch (InterruptedException error) { throw new IOException(error); } ResearchSessionStore.encodeJpeg(bitmap,file); });
        stores.add(store); store.startAt(System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
        store.submit(sample(store,1,1,1,true)); assertTrue(entered.await(2,TimeUnit.SECONDS));
        store.submit(sample(store,1,1,1,true)); AcquisitionAttemptRecord rejected=sample(store,1,1,1,true); store.submit(rejected);
        assertEquals("FAILED",rejected.data.getString("write_state")); assertTrue(store.droppedSampleCount()>0);
        ExecutorService finalizer=Executors.newSingleThreadExecutor();
        try {
            Future<File> archive=finalizer.submit(()->finish(store));
            Thread.sleep(100); assertFalse(archive.isDone()); release.countDown();
            assertTrue(archive.get(10,TimeUnit.SECONDS).isFile()); assertEquals(ResearchSessionStore.State.PARTIAL,store.state());
        } finally { release.countDown(); finalizer.shutdownNow(); }
    }
    @Test public void interruptedRunBecomesPartialArchiveWithoutContinuation() throws Exception {
        ResearchSessionStore store=running(); store.submit(sample(store,1,1,1,true));
        java.lang.reflect.Field field=ResearchSessionStore.class.getDeclaredField("writer"); field.setAccessible(true);
        ThreadPoolExecutor writer=(ThreadPoolExecutor)field.get(store); writer.submit(()->{}).get(5,TimeUnit.SECONDS);
        writer.shutdown(); writer.awaitTermination(5,TimeUnit.SECONDS);
        File metadata=new File(store.directory(),"session.json"); JSONObject json=new JSONObject(ResearchSessionStore.read(metadata));
        json.put("process_owner","terminated-process"); ResearchSessionStore.atomicText(metadata,json.toString());
        assertEquals(1,ResearchSessionStore.recoverInterrupted(context).size());
        JSONObject recovered=new JSONObject(ResearchSessionStore.read(metadata));
        assertEquals("PARTIAL",recovered.getString("state")); assertEquals("process_interrupted",recovered.getString("completion_reason"));
        assertFalse(recovered.getBoolean("collection_complete"));
        File archive=new File(store.directory(),"final/"+store.sessionId()+".alprsession"); assertTrue(archive.isFile());
        ResearchArchive.verifyEntryHashes(archive);
    }
    @Test public void truncatedJournalAndMissingImageCannotProduceCompleteArchive() throws Exception {
        ResearchSessionStore store=running(); AcquisitionAttemptRecord record=sample(store,1,1,1,true);
        store.submit(record);
        java.lang.reflect.Field field=ResearchSessionStore.class.getDeclaredField("writer"); field.setAccessible(true);
        ((ThreadPoolExecutor)field.get(store)).submit(()->{}).get(5,TimeUnit.SECONDS);
        Files.delete(new File(store.directory(),"samples/crops/"+record.attemptId+".jpg").toPath());
        ResearchSessionStore.append(new File(store.directory(),"samples/attempts.jsonl"),"{\"attempt_id\":\"truncated");
        File archive=finish(store); assertEquals(ResearchSessionStore.State.PARTIAL,store.state());
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            JSONObject session=new JSONObject(entry(zip,"session.json"));
            assertFalse(session.getBoolean("collection_complete"));
            assertEquals(2L,session.getLong("integrity_loss_count"));
        }
    }

    @Test public void emptyMzPredictionIsNeverReplacedWithPreviousConsensus() throws Exception {
        ResearchSessionStore store=running(); AcquisitionAttemptRecord record=sample(store,1,104,7,true);
        record.put("mz_status","NO_CHARACTERS"); record.put("prediction","");
        record.put("consensus_prediction","PREVIOUS123"); record.put("recognition_confidence",0.0);
        store.submit(record); File archive=finish(store);
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            JSONObject crop=new JSONObject(entry(zip,"samples/annotations.jsonl").trim());
            assertEquals("",crop.getString("prediction")); assertEquals("PREVIOUS123",crop.getString("consensus_prediction"));
            assertEquals(0.0,crop.getDouble("recognition_confidence"),0.0);
            assertTrue(entry(zip,"samples/index.csv").contains(",\"\",\"PREVIOUS123\","));
            assertEquals(0,new JSONObject(entry(zip,"session.json")).getInt("inflight_attempt_count"));
        }
    }

    @Test public void mtInvocationContractZeroOneManyAndTwoCallsSurvivesCsvAndArchive() throws Exception {
        ResearchSessionStore store=running();
        ResearchAttemptBatch batch=batch(store);
        List<String> ids=new ArrayList<>();
        int[] sizes={0,1,3,2,1};
        for (int size:sizes) {
            AcquisitionAttemptRecord call=mtCall(batch,true); ids.add(call.attemptId);
            for (int index=0;index<size;index++) {
                com.example.alpr_v1.vision.Detection detection=detection(index);
                batch.detected(call,detection,index!=1);
                assertEquals(index,batch.forDetection(detection).data.getInt("mt_detection_index"));
                // A crop must not destroy the input, even if another child is recorded later.
                if (index==0) {
                    Bitmap crop=Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888); crop.eraseColor(Color.RED);
                    call.copyPlateCrop(crop); crop.recycle(); call.put("mz_executed",true);
                }
            }
        }
        batch.finish("","");
        File archive=finish(store); ResearchArchive.verifyEntryHashes(archive);
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            JSONObject session=new JSONObject(entry(zip,"session.json"));
            assertEquals("COMPLETED",session.getString("state")); assertTrue(session.getBoolean("collection_complete"));
            assertTrue(session.getBoolean("storage_prepared")); assertEquals("automatic",session.getString("collection_mode"));
            assertEquals(8,session.getInt("attempt_count")); assertEquals(4,session.getInt("crop_count"));
            assertEquals(0,session.getInt("dropped_sample_count")); assertEquals(0,session.getInt("dropped_telemetry_count"));
            assertEquals(0,session.getInt("integrity_loss_count"));
            List<List<String>> csv=parseCsv(entry(zip,"samples/attempts.csv"));
            List<String> header=csv.get(0); assertEquals(9,csv.size());
            Map<String,List<JSONObject>> groups=new LinkedHashMap<>();
            for (String line:entry(zip,"samples/attempts.jsonl").split("\n")) {
                JSONObject row=new JSONObject(line);
                groups.computeIfAbsent(row.getString("mt_invocation_id"),key->new ArrayList<>()).add(row);
                assertTrue(row.getBoolean("mt_executed"));
                assertEquals(store.sessionId(),row.getString("session_id"));
                assertEquals(7,row.getLong("scene_generation")); assertEquals(8,row.getLong("visual_epoch"));
                assertEquals(9,row.getLong("camera_transform_generation"));
                assertEquals(55,row.getLong("source_sequence")); assertEquals(9876,row.getLong("source_timestamp_nanos"));
                assertEquals(store.sessionId()+"/sg-7/entity-42",row.getString("subject_key"));
                for (String key:new String[]{"roi_left","roi_top","roi_right","roi_bottom","input_width","input_height",
                        "input_scale","input_pad_x","input_pad_y"}) assertTrue(key,row.has(key));
                assertEquals(10,row.getInt("roi_left")); assertEquals(20,row.getInt("roi_top"));
                assertEquals(110,row.getInt("roi_right")); assertEquals(70,row.getInt("roi_bottom"));
                assertEquals(64,row.getInt("input_width")); assertEquals(32,row.getInt("input_height"));
                String input=row.getString("mt_input_evidence_entry"); assertNotNull(zip.getEntry(input));
                try (InputStream stream=zip.getInputStream(zip.getEntry(input))) {
                    Bitmap image=BitmapFactory.decodeStream(stream); assertNotNull(image);
                    assertEquals(64,image.getWidth()); assertEquals(32,image.getHeight());
                    assertTrue(Color.green(image.getPixel(10,10))>240); image.recycle();
                }
                if (row.optBoolean("mz_executed")) assertNotEquals(input,row.getString("evidence_entry"));
            }
            assertEquals(5,groups.size());
            for (int invocation=0;invocation<sizes.length;invocation++) {
                List<JSONObject> rows=groups.get(ids.get(invocation));
                assertEquals(Math.max(1,sizes[invocation]),rows.size());
                for (int index=0;index<rows.size();index++) {
                    JSONObject row=rows.get(index); assertEquals(sizes[invocation],row.getInt("mt_detection_count"));
                    if (sizes[invocation]==0) {
                        assertTrue(row.isNull("mt_detection_index")); assertEquals("NO_DETECTION",row.getString("mt_status"));
                    } else {
                        assertEquals(index,row.getInt("mt_detection_index"));
                        assertEquals(30+index,row.getDouble("plate_left"),0);
                    }
                }
            }
            // Legacy reader selects existing columns by name and ignores additive fields.
            for (int i=1;i<csv.size();i++) {
                assertEquals(header.size(),csv.get(i).size());
                assertEquals(store.sessionId(),csv.get(i).get(header.indexOf("session_id")));
                assertNotNull(zip.getEntry(csv.get(i).get(header.indexOf("evidence_entry"))));
            }
            assertEquals("",csv.get(1).get(header.indexOf("mt_detection_index")));
            assertEquals("0",csv.get(1).get(header.indexOf("mt_detection_count")));
            assertEquals("alpr.mobile_research_samples.v2",new JSONObject(entry(zip,"samples/schema.json")).getString("schema"));
            assertEquals("alpr.mobile_research_bundle.v1",new JSONObject(entry(zip,"manifest.json")).getString("schema"));
        }
    }

    @Test public void mtCancelledAfterBackendKeepsInvocationAndDecoderIndicesAcrossStop() throws Exception {
        ResearchSessionStore store=running(); ResearchAttemptBatch batch=batch(store);
        AcquisitionAttemptRecord call=mtCall(batch,true); batch.detected(call,detection(0),true);
        store.closeAdmission("manual",System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
        batch.detected(call,detection(1),false);
        String reason="scene_superseded, \"camera\"\nnew epoch";
        batch.finish(reason,""); File archive=store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),null);
        ResearchArchive.verifyEntryHashes(archive);
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            List<List<String>> rows=parseCsv(entry(zip,"samples/attempts.csv")); assertEquals(3,rows.size());
            for (int i=1;i<rows.size();i++) {
                assertEquals(rows.get(0).size(),rows.get(i).size());
                assertEquals(reason,rows.get(i).get(rows.get(0).indexOf("cancel_reason")));
                assertEquals("true",rows.get(i).get(rows.get(0).indexOf("stale_or_cancelled")));
                assertEquals(call.attemptId,rows.get(i).get(rows.get(0).indexOf("mt_invocation_id")));
                assertEquals("2",rows.get(i).get(rows.get(0).indexOf("mt_detection_count")));
            }
        }
    }

    @Test public void mtNotRunIsDistinctAndMissingInputCannotCompleteEvenWithMzCrop() throws Exception {
        ResearchSessionStore store=running(); ResearchAttemptBatch batch=batch(store);
        AcquisitionAttemptRecord notRun=batch.beginMt(42,42,10,20,110,70,64,32);
        AcquisitionAttemptRecord call=mtCall(batch,false); batch.detected(call,detection(0),true);
        Bitmap crop=Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888); call.copyPlateCrop(crop); crop.recycle();
        batch.finish("","backend or preparation failed"); File archive=finish(store);
        assertEquals(ResearchSessionStore.State.PARTIAL,store.state());
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            String[] rows=entry(zip,"samples/attempts.jsonl").split("\n");
            JSONObject first=new JSONObject(rows[0]); assertEquals(notRun.attemptId,first.getString("attempt_id"));
            assertEquals("NOT_RUN",first.getString("mt_status")); assertFalse(first.getBoolean("mt_executed"));
            assertEquals("",first.getString("mt_invocation_id")); assertTrue(first.isNull("mt_detection_count"));
            JSONObject second=new JSONObject(rows[1]); assertFalse(second.getString("mt_input_missing_evidence_reason").isEmpty());
            assertNotNull(zip.getEntry(second.getString("evidence_entry")));
        }
    }

    @Test public void mtCapacityLossRetainsInvocationRowsAndCountsAndMarksPartial() throws Exception {
        ExperimentSession domain=new ExperimentSession();
        ResearchSessionStore store=ResearchSessionStore.prepare(ResearchSessionStore.sessionsRoot(context),prepared(domain),
                32,1,0,ResearchSessionStore::encodeJpeg); stores.add(store);
        store.startAt(System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
        ResearchAttemptBatch batch=batch(store); AcquisitionAttemptRecord call=mtCall(batch,true);
        for (int i=0;i<3;i++) batch.detected(call,detection(i),true);
        AcquisitionAttemptRecord miss=mtCall(batch,true);
        batch.finish("",""); File archive=finish(store); ResearchArchive.verifyEntryHashes(archive);
        assertEquals(ResearchSessionStore.State.PARTIAL,store.state());
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            String[] rows=entry(zip,"samples/attempts.jsonl").split("\n"); assertEquals(4,rows.length);
            for (int i=0;i<3;i++) {
                JSONObject row=new JSONObject(rows[i]); assertEquals(call.attemptId,row.getString("mt_invocation_id"));
                assertEquals(3,row.getInt("mt_detection_count")); assertEquals(i,row.getInt("mt_detection_index"));
                if (i>0) assertEquals("attempt_capacity",row.getString("mt_input_missing_evidence_reason"));
            }
            JSONObject row=new JSONObject(rows[3]); assertEquals(miss.attemptId,row.getString("mt_invocation_id"));
            assertEquals("NO_DETECTION",row.getString("mt_status")); assertEquals(0,row.getInt("mt_detection_count"));
        }
    }

    @Test public void deletedMtInputBesideExistingCropIsIntegrityLossAndProvenanceStaysFrozen() throws Exception {
        ResearchSessionStore store=running(); ResearchAttemptBatch batch=batch(store);
        AcquisitionAttemptRecord call=mtCall(batch,true); batch.detected(call,detection(0),true);
        Bitmap crop=Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888); call.copyPlateCrop(crop); crop.recycle();
        batch.finish("",""); File archive=finish(store);
        JSONObject original=new JSONObject(ResearchSessionStore.read(new File(store.directory(),"session.json")));
        JSONObject build=original.getJSONObject("app_build");
        assertEquals(com.example.alpr_v1.BuildConfig.GIT_COMMIT,build.getString("git_commit"));
        assertEquals(com.example.alpr_v1.BuildConfig.GIT_DIRTY,build.getBoolean("git_dirty"));
        assertEquals(com.example.alpr_v1.BuildConfig.GIT_DIRTY_AVAILABLE,build.getBoolean("git_dirty_available"));
        java.time.Instant.parse(build.getString("built_at_utc"));
        // Simulate recovery on another APK: the saved build must override a later report's provenance.
        ResearchSessionStore.atomicText(new File(store.directory(),"telemetry/report.json"),
                "{\"app_build\":{\"git_commit\":\"other-apk\"},\"app_version\":\"other-version\"}");
        Files.delete(new File(store.directory(),"samples/evidence/"+call.attemptId+".jpg").toPath());
        archive=ResearchSessionStore.buildArchive(store.directory(),null); ResearchArchive.verifyEntryHashes(archive);
        try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
            JSONObject session=new JSONObject(entry(zip,"session.json")); assertEquals("PARTIAL",session.getString("state"));
            assertFalse(session.getBoolean("collection_complete")); assertEquals(1,session.getInt("integrity_loss_count"));
            JSONObject report=new JSONObject(entry(zip,"report.json"));
            assertEquals(build.toString(),report.getJSONObject("app_build").toString());
            assertEquals(com.example.alpr_v1.BuildConfig.VERSION_NAME,report.getString("app_version"));
            assertTrue(entry(zip,"samples/attempts.csv").contains("missing_mt_input_image"));
        }
    }

    @Test public void telemetryLossAndTruncatedTelemetryBothPreventCompletion() throws Exception {
        for (boolean truncated:new boolean[]{false,true}) {
            ResearchSessionStore store=running();
            if (truncated) store.appendTelemetry("traces.jsonl","{\"incomplete\":");
            else store.recordMetadataFailure("simulated_telemetry_loss");
            File archive=finish(store); ResearchArchive.verifyEntryHashes(archive);
            assertEquals(ResearchSessionStore.State.PARTIAL,store.state());
            try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)) {
                JSONObject session=new JSONObject(entry(zip,"session.json"));
                assertFalse(session.getBoolean("collection_complete"));
                assertEquals(1,session.getInt(truncated ? "integrity_loss_count" : "dropped_telemetry_count"));
                assertEquals(0,session.getInt("dropped_sample_count"));
                assertEquals(1,session.getInt("dropped_telemetry_count"));
            }
            File rebuilt=ResearchSessionStore.buildArchive(store.directory(),null);
            try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(rebuilt)) {
                assertEquals(1,new JSONObject(entry(zip,"session.json")).getInt("dropped_telemetry_count"));
            }
        }
    }

    private static ResearchAttemptBatch batch(ResearchSessionStore store) {
        return new ResearchAttemptBatch(store,new ContinuityStamp(7,8,9,55,9876,
                com.example.alpr_v1.continuity.SourceTimestampDomain.UNKNOWN),"R1",1f);
    }
    private static AcquisitionAttemptRecord mtCall(ResearchAttemptBatch batch,boolean evidence) {
        AcquisitionAttemptRecord call=batch.beginMt(42,42,10,20,110,70,64,32); assertNotNull(call);
        call.put("input_scale",0.64); call.put("input_pad_x",0); call.put("input_pad_y",0);
        if (evidence) {
            Bitmap input=Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888); input.eraseColor(Color.GREEN);
            call.copyEvidence(input); input.recycle();
        }
        call.mtStarted(); return call;
    }
    private static com.example.alpr_v1.vision.Detection detection(int index) {
        return new com.example.alpr_v1.vision.Detection(0,0.9f,30+index,35,60+index,45,Collections.emptyList());
    }
    private static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows=new ArrayList<>(); List<String> row=new ArrayList<>();
        StringBuilder cell=new StringBuilder(); boolean quoted=false;
        for (int i=0;i<csv.length();i++) {
            char c=csv.charAt(i);
            if (c=='"') {
                if (quoted && i+1<csv.length() && csv.charAt(i+1)=='"') { cell.append('"'); i++; }
                else quoted=!quoted;
            } else if (!quoted && (c==',' || c=='\n')) {
                row.add(cell.toString()); cell.setLength(0);
                if (c=='\n') { rows.add(row); row=new ArrayList<>(); }
            } else cell.append(c);
        }
        assertFalse("Unclosed CSV quote",quoted); assertEquals(0,cell.length()); return rows;
    }

    static String entry(java.util.zip.ZipFile zip,String name) throws Exception {
        assertNotNull("Missing "+name,zip.getEntry(name));
        try (InputStream input=zip.getInputStream(zip.getEntry(name));ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n; while ((n=input.read(buffer))!=-1) output.write(buffer,0,n);
            return output.toString("UTF-8");
        }
    }
}
