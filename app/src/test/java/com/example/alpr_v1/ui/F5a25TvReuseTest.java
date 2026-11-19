package com.example.alpr_v1.ui;

import android.app.Application;
import android.os.SystemClock;
import com.example.alpr_v1.*;
import com.example.alpr_v1.camera.*;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import com.example.alpr_v1.metrics.MetricsCollector;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Real CameraController/MainActivity; only model backend IO and hardware ports are synthetic. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=F5a24CameraStartTest.ModelIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class F5a25TvReuseTest {
    F5a24CameraStartTest t;String original;
    @Before public void setup()throws Exception{
        t=new F5a24CameraStartTest();t.setup();
        TvSetupCalibration c=(TvSetupCalibration)TvBboxStartUiTest.get(t.f.tv,"calibration");
        original=c.snapshot(t.f.geometry,null,null,TvDistanceCalculator.unavailable("SYNTHETIC_HOST"),c.confirmedAt).toString();
        t.f.main.getSharedPreferences("alpr_ui",0).edit().putString("tv_setup_calibration_v1",original).commit();
        t.f.tv=new TvSetupController(t.f.main,t.f.main.findViewById(R.id.camera_preview),t.f.main.findViewById(R.id.detection_overlay),
                ()->!t.f.sessions.experiment.isRunning(),()->{},(n,d)->{});
        TvBboxStartUiTest.set(t.f.main,"tvSetup",t.f.tv);t.f.tv.setCameraControls(t.camera);
        t.f.tv.onGeometry(t.f.geometry,null,"SYNTHETIC_RESTORED_CAMERA");
    }
    @After public void close()throws Exception{if(t!=null)t.close();}
    TvSetupCalibration calibration()throws Exception{return (TvSetupCalibration)TvBboxStartUiTest.get(t.f.tv,"calibration");}
    void ready()throws Exception{t.fresh();assertEquals("",t.call("tvBboxStartReason").toString());}
    void unchangedApproval()throws Exception{
        assertEquals(original,t.f.main.getSharedPreferences("alpr_ui",0).getString("tv_setup_calibration_v1",""));
        assertEquals(new JSONObject(original).getString("confirmed_at_utc"),calibration().confirmedAt);
        assertEquals(SetupJson.canonical(new JSONObject(original).getJSONArray("corners_norm")),SetupJson.canonical(calibration().snapshot(current(),null,null,TvDistanceCalculator.unavailable("SYNTHETIC_HOST"),calibration().confirmedAt).getJSONArray("corners_norm")));
        assertFalse(t.f.sessions.experiment.isRunning());assertFalse(t.f.intent().pending());
    }
    CameraPreviewGeometry current()throws Exception{return (CameraPreviewGeometry)TvBboxStartUiTest.get(t.f.tv,"geometry");}
    @Test public void restartWithIntegralApprovalAndFreshCameraReusesCorners()throws Exception{
        ready();assertEquals("READY",calibration().status(current()));unchangedApproval();
    }
    @Test public void newBindingRevalidatesSamePixelsInsteadOfRequiringCorners()throws Exception{
        JSONObject geometry=t.f.geometry.toJson();geometry.put("binding_generation",t.f.geometry.bindingGeneration+1);
        ReflectionHelpers.setField(t.camera,"bindingGeneration",t.f.geometry.bindingGeneration+1);
        t.f.tv.onGeometry(CameraPreviewGeometry.fromJson(geometry),null,"SYNTHETIC_REBIND");
        ready();assertEquals("READY",calibration().status(current()));unchangedApproval();
    }
    @Test public void previewResizeReusesOriginalNormalizedCorners()throws Exception{
        ready();JSONObject geometry=t.f.geometry.toJson();geometry.put("preview_width",t.f.geometry.previewWidth+100);
        t.f.tv.onGeometry(CameraPreviewGeometry.fromJson(geometry),null,"SYNTHETIC_VIEW_ONLY");
        ready();assertEquals("READY",calibration().status(current()));unchangedApproval();
        JSONObject frozen=t.f.tv.selectionForInput().calibrationJson();assertEquals(current().previewWidth,frozen.getJSONObject("source_geometry").getInt("preview_width"));
    }
    @Test public void restoredApprovalStillWaitsForZoomAcknowledgement()throws Exception{
        ReflectionHelpers.setField(t.camera,"setupZoomAcknowledged",false);
        assertTrue(t.call("tvBboxStartReason").toString().contains("ZOOM_NOT_ACKNOWLEDGED"));assertEquals("STALE",calibration().status(current()));
        ReflectionHelpers.setField(t.camera,"setupZoomAcknowledged",true);ready();unchangedApproval();
    }
    @Test public void mismatchedBindingCannotRestoreCalibrationUntilItsMapperArrives()throws Exception{
        ReflectionHelpers.setField(t.camera,"bindingGeneration",t.f.geometry.bindingGeneration+1);t.fresh();
        assertTrue(t.call("tvBboxStartReason").toString().contains("CAMERA_SETUP_GENERATION_CHANGED"));assertEquals("STALE",calibration().status(current()));
        JSONObject g=t.f.geometry.toJson();g.put("binding_generation",t.f.geometry.bindingGeneration+1);
        t.f.tv.onGeometry(CameraPreviewGeometry.fromJson(g),null,"SYNTHETIC_REBIND");ready();unchangedApproval();
    }
    @Test public void twoSessionsReuseOneApprovalAndArchiveItsRealRevalidation()throws Exception{
        ready();String approvalDigest=new JSONObject(original).getString("digest_sha256");
        for(int run=1;run<=2;run++){
            Object prepared=t.prepare(RoiBudgetPolicy.FULL_FRAME);assertTrue(t.admit(prepared));
            TvBboxStartUiTest.set(t.f.main,"preparedExperimentStart",null);
            ResearchSessionStore store=t.f.sessions.store();TvBboxProducerFixture.drain(store);
            JSONObject event=null;
            for(String line:Files.readAllLines(store.directory().toPath().resolve("telemetry/events.jsonl"),StandardCharsets.UTF_8)){
                JSONObject row=new JSONObject(line);if("tv_setup_calibration_revalidated".equals(row.optString("event_type")))event=row;
            }
            assertNotNull(event);assertEquals(approvalDigest,event.getString("original_digest_sha256"));
            assertEquals(SetupJson.canonical(new JSONObject(original)),SetupJson.canonical(event.getJSONObject("original_calibration_snapshot")));
            assertEquals(new JSONObject(original).getString("confirmed_at_utc"),event.getString("confirmed_at_utc"));
            assertFalse(event.getBoolean("physical_alignment_verified"));assertFalse(event.has("frame_id"));assertFalse(event.has("track_id"));
            assertEquals(t.f.geometry.bindingGeneration,event.getInt("camera_binding_generation"));
            assertEquals(F5a25CalibrationReuseTest.keys(new JSONObject(original)),F5a25CalibrationReuseTest.keys(store.tvSetupSnapshot()));
            ResearchExecutionConfig config=(ResearchExecutionConfig)TvBboxStartUiTest.get(prepared,"config");
            assertEquals(config.tvBboxCrop().calibrationDigest,event.getString("calibration_digest_sha256"));
            assertEquals(2,config.plate.cpuThreads);assertEquals(2,config.character.cpuThreads);assertFalse(config.vehicle.enabled);
            t.f.sessions.closeMeasurement(ExperimentSession.CompletionReason.MANUAL,(MetricsCollector)TvBboxStartUiTest.get(t.f.main,"metricsCollector"));
            java.io.File archive=store.finish(new ResearchSessionStore.Telemetry("{\"synthetic\":true,\"device_run\":\"NOT_RUN\"}","","","","",""),config);
            assertNotEquals("ABSENT",TvBboxInputValidator.validate(archive));assertTrue(t.f.sessions.ready());
            String folder=System.getenv("F5A_COCKPIT_EVIDENCE");if(folder!=null){Files.createDirectories(Paths.get(folder));Files.copy(archive.toPath(),Paths.get(folder,"f25_reuse_session_"+run+"_synthetic.alprsession"));}
            unchangedApproval();
        }
    }
    @Test public void corruptedPersistenceAfterRestoreClosesStartUntilManualApproval()throws Exception{
        ready();JSONObject bad=new JSONObject(original);bad.getJSONArray("corners_norm").getJSONObject(0).put("x",.4);
        t.f.main.getSharedPreferences("alpr_ui",0).edit().putString("tv_setup_calibration_v1",bad.toString()).commit();
        assertTrue(t.call("tvBboxStartReason").toString().contains("INVALID_STORED_CALIBRATION"));assertEquals("STALE",calibration().status(current()));
        assertFalse(t.f.sessions.experiment.isRunning());assertFalse(t.f.intent().pending());
    }
    @Test public void immediateStopBeforeDurableStartStillPreservesValidationWithoutOpeningAdmission()throws Exception{
        ready();Object prepared=t.prepare(RoiBudgetPolicy.FULL_FRAME);ResearchSessionStore store=t.f.sessions.store();
        java.util.concurrent.ThreadPoolExecutor writer=ReflectionHelpers.getField(store,"writer");
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        writer.execute(()->{entered.countDown();try{release.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(t.admit(prepared));assertFalse(store.accepting());
        t.f.sessions.closeMeasurement(ExperimentSession.CompletionReason.MANUAL,(MetricsCollector)TvBboxStartUiTest.get(t.f.main,"metricsCollector"));
        release.countDown();TvBboxProducerFixture.drain(store);assertFalse(store.accepting());
        String events=new String(Files.readAllBytes(store.directory().toPath().resolve("telemetry/events.jsonl")),StandardCharsets.UTF_8);
        assertTrue(events.contains("tv_setup_calibration_revalidated"));assertFalse(t.f.sessions.experiment.isRunning());
    }
    @Test public void failedValidationEventWriteNeverOpensResearchAdmission()throws Exception{
        ready();t.prepare(RoiBudgetPolicy.FULL_FRAME);ResearchSessionStore store=t.f.sessions.store();
        Path events=store.directory().toPath().resolve("telemetry/events.jsonl");Files.delete(events);Files.createDirectory(events);
        java.util.concurrent.atomic.AtomicReference<Exception> failure=new java.util.concurrent.atomic.AtomicReference<>();
        JSONObject proof=t.f.tv.revalidationEvidence(t.camera.captureSetupReadiness()).put("event_type","tv_setup_calibration_revalidated");
        java.util.concurrent.CompletableFuture<Void> committed=store.startAtQueued(System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos(),t.f.tv.selectionForInput().calibrationJson(),proof,failure::set);
        assertThrows(java.util.concurrent.ExecutionException.class,()->committed.get(10,java.util.concurrent.TimeUnit.SECONDS));
        TvBboxProducerFixture.drain(store);assertNotNull(failure.get());assertFalse(store.accepting());assertFalse(t.f.sessions.experiment.isRunning());
    }
}
