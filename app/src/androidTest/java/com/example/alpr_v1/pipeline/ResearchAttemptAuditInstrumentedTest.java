package com.example.alpr_v1.pipeline;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.metrics.InferenceTrace;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.tracking.VehicleTrackingCoordinator;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ResearchAttemptAuditInstrumentedTest {
    @Test public void realMtMzCallsPersistEveryCropAndCancelledAttemptWithoutChangingPredictions() throws Exception {
        org.junit.Assume.assumeTrue("Requires -e liveResearch true and research-qa-source.png",
                "true".equals(InstrumentationRegistry.getArguments().getString("liveResearch")));
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File isolated=new File(target.getCacheDir(),"research-engine-test-"+UUID.randomUUID());
        Files.createDirectories(isolated.toPath());
        Context context=new ContextWrapper(target) {
            @Override public File getFilesDir() { return isolated; }
            @Override public Context getApplicationContext() { return this; }
        };
        ModelRegistry registry=new ModelRegistry(target); AutoTuneManager auto=new AutoTuneManager(target);
        ResearchExecutionConfig frozen=new ResearchExecutionConfig("quality","R0",RoiBudgetPolicy.FULL_FRAME,
                RecognitionProfile.BALANCED,"auto",false,false,false,true,true,true,
                ResearchStageExecutionConfig.disabled(ModelRole.VEHICLE),stage(registry,auto,ModelRole.PLATE),
                stage(registry,auto,ModelRole.CHARACTER));
        ExperimentSession session=new ExperimentSession(); ExperimentSession.Prepared prepared=session.prepare(
                "quality","R0",TimerConfig.disabled(),ThermalConfig.disabled(),ExperimentIdentity.defaults(),frozen);
        ResearchSessionStore store=ResearchSessionStore.prepare(context,prepared);
        Bitmap screen=BitmapFactory.decodeFile(new File(target.getExternalFilesDir(null),"research-qa-source.png").getPath());
        assertNotNull(screen);
        Bitmap frame=Bitmap.createBitmap(screen,50,380,590,420); screen.recycle();
        long elapsed=SystemClock.elapsedRealtimeNanos(), wall=System.currentTimeMillis();
        store.startAt(wall,elapsed); session.startPrepared(prepared,wall,elapsed);
        int mz=0;
        try {
            String baseline;
            try (MobileAlprEngine engine=engine(registry,auto,frozen)) {
                PipelineResult result=engine.run(frame,new InferenceTrace(1),new ContinuityStamp(1,0,0,1L),null,()->false);
                try { assertFalse("Reference image must produce MZ",result.plateObservations.isEmpty());
                    baseline=result.plateObservations.get(0).freshPrediction; }
                finally { result.close(); }
            }
            try (MobileAlprEngine engine=engine(registry,auto,frozen)) {
                engine.setResearchCollector(store,1f);
                for (int index=0;index<3;index++) {
                    long frameId=1+index*10L;
                    PipelineResult result=engine.run(frame,new InferenceTrace(frameId),new ContinuityStamp(1,0,0,frameId),null,()->false);
                    try {
                        for (PlateObservation observation:result.plateObservations) {
                            if (observation.freshMzAttempted) { mz++; assertNotNull(observation.researchIdentity); }
                        }
                        if (index==0) assertEquals(baseline,result.plateObservations.get(0).freshPrediction);
                    } finally { result.close(); }
                }
                assertTrue("Three actual MZ calls must be auditable",mz>=3);
                AtomicBoolean cancelled=new AtomicBoolean();
                try {
                    PipelineResult result=engine.run(frame,new InferenceTrace(41),new ContinuityStamp(1,0,0,41L),null,
                            cancelled::get,observation->cancelled.set(true));
                    result.close();
                } catch (MobileAlprEngine.ProcessingCancelledException expected) { }
                assertTrue(cancelled.get());
            }
            store.closeAdmission("manual",System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
            File archive=store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),frozen);
            ResearchArchive.verifyEntryHashes(archive);
            int crops=0,cancelled=0;
            Map<String,List<JSONObject>> invocations=new HashMap<>();
            try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive);
                 BufferedReader reader=new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry("samples/attempts.jsonl")),StandardCharsets.UTF_8))) {
                String line; while ((line=reader.readLine())!=null) {
                    JSONObject row=new JSONObject(line);
                    if (row.optBoolean("mt_executed")) {
                        String invocation=row.getString("mt_invocation_id"); assertFalse(invocation.isEmpty());
                        invocations.computeIfAbsent(invocation,key->new ArrayList<>()).add(row);
                        assertNotNull(zip.getEntry(row.getString("mt_input_evidence_entry")));
                        assertEquals("",row.getString("mt_input_missing_evidence_reason"));
                    }
                    if (row.optBoolean("mz_executed")) {
                        crops++; assertEquals("plate_crop",row.getString("evidence_kind"));
                        assertNotNull(zip.getEntry(row.getString("evidence_entry")));
                    }
                    if (row.optBoolean("stale_or_cancelled")) { cancelled++; assertEquals("scene_superseded",row.getString("cancel_reason")); }
                }
            }
            assertTrue(crops>=mz+1); assertTrue(cancelled>0);
            assertFalse(invocations.isEmpty());
            for (List<JSONObject> rows:invocations.values()) {
                int count=rows.get(0).getInt("mt_detection_count");
                assertEquals(Math.max(1,count),rows.size());
                for (int index=0;index<rows.size();index++) {
                    JSONObject row=rows.get(index); assertEquals(count,row.getInt("mt_detection_count"));
                    if (count==0) assertTrue(row.isNull("mt_detection_index"));
                    else assertEquals(index,row.getInt("mt_detection_index"));
                    for (String key:new String[]{"session_id","scene_generation","visual_epoch","camera_transform_generation",
                            "source_sequence","source_timestamp_nanos","roi_left","roi_top","roi_right","roi_bottom",
                            "input_width","input_height"}) assertEquals(key,rows.get(0).get(key),row.get(key));
                }
            }
            Files.copy(archive.toPath(),new File(target.getExternalFilesDir(null),"research-qa-mz.alprsession").toPath(),StandardCopyOption.REPLACE_EXISTING);
        } finally {
            frame.recycle();
            if (store.accepting()) {
                store.closeAdmission("error",System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos());
                store.finish(new ResearchSessionStore.Telemetry("{}","","","","",""),frozen);
            }
            try (java.util.stream.Stream<Path> paths=Files.walk(isolated.toPath())) {
                for (Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }
    private static MobileAlprEngine engine(ModelRegistry registry,AutoTuneManager auto,ResearchExecutionConfig frozen) {
        return new MobileAlprEngine(registry,auto,RoiBudgetPolicy.FULL_FRAME,MtExecutionPolicy.LEGACY_BURST,
                MtFallbackPolicy.SAME_CYCLE,VehicleTrackingPolicy.RAW_MP,frozen,new VehicleTrackingCoordinator());
    }
    private static ResearchStageExecutionConfig stage(ModelRegistry registry,AutoTuneManager auto,ModelRole role) {
        InstalledModel model=registry.getActive(role);
        return ResearchStageExecutionConfig.enabled(role,model,auto.chosenVariant(model),auto.chosenProfile(model),registry);
    }
}
