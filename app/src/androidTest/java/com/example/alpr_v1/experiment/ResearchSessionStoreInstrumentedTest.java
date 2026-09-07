package com.example.alpr_v1.experiment;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
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

    static String entry(java.util.zip.ZipFile zip,String name) throws Exception {
        assertNotNull("Missing "+name,zip.getEntry(name));
        try (InputStream input=zip.getInputStream(zip.getEntry(name));ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n; while ((n=input.read(buffer))!=-1) output.write(buffer,0,n);
            return output.toString("UTF-8");
        }
    }
}
