package com.example.alpr_v1.ui;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.*;
import android.util.Size;
import androidx.fragment.app.FragmentController;
import androidx.savedstate.SavedStateRegistryController;
import com.example.alpr_v1.*;
import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.camera.*;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.inference.RuntimeEvidenceFixture;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.pipeline.*;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.vision.Point2;
import com.example.alpr_v1.vision.CameraImageConverter;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Real camera readiness and Activity preparation. Only model backend IO is replaced. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=F5a24CameraStartTest.ModelIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class F5a24CameraStartTest {
    TvBboxStartUiTest f;CameraSetupControlTest hardware;CameraController camera;ExecutorService executor;
    @Before public void setup()throws Exception{
        ModelIo.loads.set(0);
        f=new TvBboxStartUiTest();f.temp.create();f.setup(true);
        ((CameraController)TvBboxStartUiTest.get(f.main,"cameraController")).close();
        hardware=new CameraSetupControlTest();ReflectionHelpers.callInstanceMethod(hardware,"start");
        camera=ReflectionHelpers.getField(hardware,"controller");
        ReflectionHelpers.setField(camera,"bindingGeneration",f.geometry.bindingGeneration);
        ReflectionHelpers.setField(camera,"boundAnalysisSize",new Size(640,400));
        ReflectionHelpers.setField(camera,"lastSetupFrameTimestamp",123L);
        TvBboxStartUiTest.set(f.main,"cameraController",camera);f.tv.setCameraControls(camera);
        TvBboxStartUiTest.set(f.main,"experimentRoiBudgetPolicy",RoiBudgetPolicy.FULL_FRAME);
        f.prefs.edit().putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"cpu_2").putString(SettingsActivity.KEY_RESEARCH_MZ_EXECUTION,"cpu_2")
                .putString(SettingsActivity.KEY_RESEARCH_MP_EXECUTION,"cpu_2").putString(SettingsActivity.KEY_EXPERIMENT_SERIES_ID,"SYNTHETIC-F5A24")
                .putString(SettingsActivity.KEY_EXPERIMENT_SCENARIO_ID,"HOST").putString(SettingsActivity.KEY_EXPERIMENT_NOTES,"synthetic=true; DEVICE_RUN=NOT_RUN")
                .putString(SettingsActivity.KEY_ANALYSIS_RESOLUTION_SELECTION,CameraResolutionSelection.exact(new Size(640,400)).wireName())
                .putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,RoiBudgetPolicy.FULL_FRAME.wireName()).commit();
        ModelRegistry registry=(ModelRegistry)TvBboxStartUiTest.get(f.main,"modelRegistry");
        ReflectionHelpers.setField(registry,"modelsRoot",f.temp.newFolder());
        Map<ModelRole,InstalledModel> active=ReflectionHelpers.getField(registry,"active");
        AutoTuneManager tune=new AutoTuneManager(f.main);TvBboxStartUiTest.set(f.main,"autoTuneManager",tune);
        for(ModelRole role:ModelRole.values()){
            java.io.File directory=new java.io.File(registry.modelsRoot(),role.wireName()+"/synthetic-f24-"+role.wireName());
            InstalledModel original=RuntimeEvidenceFixture.model(directory,"fp32","FLOAT32",null);
            JSONObject manifest=new JSONObject(original.manifest().rawJson()).put("role",role.wireName()).put("model_id","synthetic-f24-"+role.wireName()).put("task",role==ModelRole.PLATE?"pose":"detect");
            manifest.put("output",new JSONObject().put("decoder","ultralytics_yolo_raw_v1").put("class_count",1)
                    .put("keypoint_count",role==ModelRole.PLATE?4:0).put("keypoint_dimensions",role==ModelRole.PLATE?3:0)
                    .put("tensor_layout","channels_first").put("confidence_threshold",.25).put("iou_threshold",.45));
            Files.write(directory.toPath().resolve("manifest.json"),manifest.toString().getBytes(StandardCharsets.UTF_8));
            InstalledModel model=new InstalledModel(ModelManifest.parse(manifest.toString()),original.directory(),role.wireName());
            active.put(role,model);tune.pinVariant(model,"selected");
            android.content.SharedPreferences modelPrefs=ReflectionHelpers.getField(registry,"preferences");
            modelPrefs.edit().putString("active."+role.wireName(),model.storageId()).commit();
            android.content.SharedPreferences tunePrefs=ReflectionHelpers.getField(tune,"preferences");
            String key=ReflectionHelpers.callInstanceMethod(tune,"key",ReflectionHelpers.ClassParameter.from(InstalledModel.class,model));
            tunePrefs.edit().putString(key,new JSONObject().put("chosen_variant_id","selected").put("runtime","tflite").put("cpu_threads",2).put("gpu",false).toString()).commit();
        }
        executor=(ExecutorService)TvBboxStartUiTest.get(f.main,"pipelineInferenceExecutor");
    }
    @After public void close()throws Exception{
        String folder=System.getenv("F5A_COCKPIT_EVIDENCE");
        if(folder!=null){Path path=Paths.get(folder);Files.createDirectories(path);StringBuilder log=new StringBuilder();
            for(org.robolectric.shadows.ShadowLog.LogItem item:org.robolectric.shadows.ShadowLog.getLogsForTag("ALPR_CAMERA_SETUP"))log.append(item.msg).append('\n');
            Files.write(path.resolve("camera_setup_diagnostics.log"),log.toString().getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        if(f!=null){call("stopThermalUiMonitor");call("stopPhoneOrientationUi");((ExecutorService)TvBboxStartUiTest.get(f.main,"backgroundExecutor")).shutdownNow();f.store=f.sessions.store();f.close();f.temp.delete();}if(hardware!=null)hardware.stop();
    }
    Object call(String name)throws Exception{return TvBboxStartUiTest.call(f.main,name,new Class<?>[0]);}
    void fresh(){ReflectionHelpers.setField(camera,"lastStreamFrameNanos",SystemClock.elapsedRealtimeNanos());
        ((java.util.concurrent.atomic.AtomicLong)ReflectionHelpers.getField(camera,"setupFrameSequence")).incrementAndGet();
        long timestamp=ReflectionHelpers.getField(camera,"lastSetupFrameTimestamp");ReflectionHelpers.setField(camera,"lastSetupFrameTimestamp",timestamp+1);}
    Object prepare(RoiBudgetPolicy policy)throws Exception{
        TvBboxStartUiTest.set(f.main,"experimentRoiBudgetPolicy",policy);fresh();
        f.intent().request();
        TvBboxStartUiTest.call(f.main,"prepareCameraStart",new Class<?>[]{boolean.class},false);
        executor.submit(()->{}).get(15,TimeUnit.SECONDS);
        // Inspect an explicitly requested preparation while the real freshness gate
        // holds admission; passive prewarming is deliberately no longer supported.
        ShadowSystemClock.advanceBy(Duration.ofMillis(600));ShadowLooper.shadowMainLooper().idle();fresh();
        Object ready=TvBboxStartUiTest.get(f.main,"preparedExperimentStart");assertNotNull(ready);return ready;
    }
    boolean admit(Object ready)throws Exception{
        long now=SystemClock.elapsedRealtimeNanos();
        TvBboxStartUiTest.set(ready,"startWall",System.currentTimeMillis());TvBboxStartUiTest.set(ready,"startElapsed",now);TvBboxStartUiTest.set(ready,"startMonotonic",System.nanoTime());
        boolean started=(boolean)TvBboxStartUiTest.call(f.main,"beginAnalysisMeasurement",new Class<?>[]{ready.getClass()},ready);
        if(started)f.intent().consumeIfReady(true);
        return started;
    }
    void starts(RoiBudgetPolicy policy)throws Exception{
        Object ready=prepare(policy);ResearchExecutionConfig config=(ResearchExecutionConfig)TvBboxStartUiTest.get(ready,"config");
        assertEquals(policy,config.roiBudgetPolicy);assertEquals(policy.usesVehicleCascade(),config.vehicle.enabled);
        assertEquals(2,config.plate.cpuThreads);assertEquals(2,config.character.cpuThreads);assertEquals("fp32",config.plate.precision);
        assertTrue(admit(ready));TvBboxProducerFixture.drain(f.sessions.store());assertTrue(f.sessions.experiment.isRunning());
        assertTrue(f.sessions.store().accepting());assertEquals(config.cameraSetup.toJson().toString(),camera.setupSnapshot().toJson().toString());
        assertEquals(config.tvBboxCrop().calibrationDigest,f.sessions.store().tvSetupSnapshot().getString("digest_sha256"));
    }
    @Test public void R0StartsAfterRealControllerAndExecutorPreparation()throws Exception{starts(RoiBudgetPolicy.FULL_FRAME);}
    @Test public void R1StartsAfterRealControllerAndExecutorPreparation()throws Exception{starts(RoiBudgetPolicy.ONE_ROI);}
    @Test public void R2StartsAfterRealControllerAndExecutorPreparation()throws Exception{starts(RoiBudgetPolicy.TWO_ROI);}
    @Test public void freshCameraMustNotRemainBlockedByCachedTvState()throws Exception{
        assertNotNull(camera.setupSnapshot());TvBboxStartUiTest.set(f.tv,"cameraState",CameraSetupControl.State.unavailable());
        assertEquals("READY",f.tv.bboxPreparationStatus());
        TvBboxStartUiTest.set(f.tv,"cameraState",CameraSetupControl.State.unavailable());
        Object ready=prepare(RoiBudgetPolicy.FULL_FRAME);assertTrue(admit(ready));
        GeometryProviderFixture source=new GeometryProviderFixture(f.main.findViewById(R.id.camera_preview),f.main);
        source.width=640;source.height=400;source.rotation=0;source.crop=new Rect(0,0,640,400);source.bitmap=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);
        CameraImageConverter.Result converted=CameraImageConverter.convert(source.image());
        TvBboxCrop crop=((ResearchExecutionConfig)TvBboxStartUiTest.get(ready,"config")).tvBboxCrop();
        try(TvBboxCrop.Frame input=f.tv.openInput(crop,converted)){assertEquals(crop.width(),input.input.getWidth());}
        finally{if(!converted.bitmap.isRecycled())converted.bitmap.recycle();if(!source.bitmap.isRecycled())source.bitmap.recycle();}
    }
    @Test public void lateFrameVerificationMustNotSuppressNextPublication()throws Exception{
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(600));
        ReflectionHelpers.callInstanceMethod(camera,"verifySetupFrame",ReflectionHelpers.ClassParameter.from(int.class,f.geometry.bindingGeneration),ReflectionHelpers.ClassParameter.from(long.class,123L));
        assertFalse("An expired frame cannot latch successful readiness publication",(boolean)ReflectionHelpers.getField(camera,"setupFreshFramePublished"));
        fresh();ReflectionHelpers.callInstanceMethod(camera,"verifySetupFrame",ReflectionHelpers.ClassParameter.from(int.class,f.geometry.bindingGeneration),ReflectionHelpers.ClassParameter.from(long.class,124L));
        assertTrue((boolean)ReflectionHelpers.getField(camera,"setupFreshFramePublished"));assertNotNull(camera.setupSnapshot());
    }
    @Test public void preparationUsesUiConfirmationAcrossExecutorDispatch()throws Exception{
        CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1);
        executor.execute(()->{blocked.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(blocked.await(5,TimeUnit.SECONDS));fresh();f.intent().request();
        TvBboxStartUiTest.call(f.main,"prepareCameraStart",new Class<?>[]{boolean.class},false);
        ReflectionHelpers.setField(camera,"publishedSetup",CameraSetupControl.State.unavailable());release.countDown();
        executor.submit(()->{}).get(15,TimeUnit.SECONDS);ShadowSystemClock.advanceBy(Duration.ofMillis(600));ShadowLooper.shadowMainLooper().idle();fresh();
        Object ready=TvBboxStartUiTest.get(f.main,"preparedExperimentStart");
        assertNotNull("The worker must use the UI-captured camera configuration; preparation_error="+TvBboxStartUiTest.get(f.main,"experimentPreparationError"),ready);
        ResearchExecutionConfig config=(ResearchExecutionConfig)TvBboxStartUiTest.get(ready,"config");
        assertEquals(com.example.alpr_v1.setup.ResearchInputMode.TV_BBOX_CROP,config.inputMode());
        assertEquals(2,config.plate.cpuThreads);assertEquals(2,config.character.cpuThreads);
        assertFalse(f.sessions.experiment.isRunning());assertFalse(f.sessions.store().accepting());
    }
    @Test public void f6CapturedApprovalSurvivesWorkerDispatchPreferenceChangesAndActualStop()throws Exception{
        fresh();TvBboxCrop input=f.tv.selectionForInput(camera.captureSetupReadiness().applied);
        Final180PhotoRoi photo=Final180PhotoRoi.prepare(new org.json.JSONArray("[{x:.25,y:.3},{x:.7,y:.3},{x:.7,y:.7},{x:.25,y:.7}]"),input);
        String approved=f.tv.approvedTvRecord();Final180PhotoRoiApproval proof=Final180PhotoRoiApproval.capture(photo,input,new JSONObject(approved),"SYNTHETIC_MAIN_ACTIVITY_TEST");
        f.prefs.edit().putBoolean(Final180PhotoRoi.ENABLED,true).putString(Final180PhotoRoi.PREFERENCE,photo.toJson().toString())
                .putString(Final180PhotoRoiApproval.PREFERENCE,proof.toJson().toString()).putString(SettingsActivity.KEY_EXPERIMENT_VARIANT,"R0_FINAL")
                .putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,com.example.alpr_v1.continuity.SceneHandlingMode.STRICT_SCENE_BOUNDARY.wireName()).commit();
        // Same physical source, newer presentation revision: the approval retains its original TV digest.
        f.geometry=CameraPreviewGeometry.fromJson(f.geometry.toJson().put("viewport_revision",103));TvBboxStartUiTest.set(f.tv,"geometry",f.geometry);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        executor.execute(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(5,TimeUnit.SECONDS));fresh();f.intent().request();
        try{
            TvBboxStartUiTest.call(f.main,"prepareCameraStart",new Class<?>[]{boolean.class},false);
            Object captured=TvBboxStartUiTest.get(f.main,"preparingCameraContext");assertNotNull(captured);
            assertNotEquals(input.digest,((TvBboxCrop)TvBboxStartUiTest.get(captured,"input")).digest);
            assertEquals(photo.toJson().toString(),TvBboxStartUiTest.get(captured,"photoRecord"));
            Final180PhotoRoiApproval.resolve((String)TvBboxStartUiTest.get(captured,"photoRecord"),(String)TvBboxStartUiTest.get(captured,"photoApprovalContext"),
                    (TvBboxCrop)TvBboxStartUiTest.get(captured,"input"),new JSONObject((String)TvBboxStartUiTest.get(captured,"tvApproval")),null);
            f.prefs.edit().putString(Final180PhotoRoi.PREFERENCE,"{}").putString(Final180PhotoRoiApproval.PREFERENCE,"{}").commit();
        }finally{release.countDown();}
        executor.submit(()->{}).get(15,TimeUnit.SECONDS);
        assertNotNull(f.sessions.store()); // Snapshot is durable before the UI completion callback/admission.
        JSONObject durable=new JSONObject(new String(Files.readAllBytes(new java.io.File(f.sessions.store().directory(),"session.json").toPath()),StandardCharsets.UTF_8));
        assertEquals(photo.left,durable.getJSONObject("execution").getJSONObject("f6_photo_roi").getJSONObject("photo_roi_camera_xy").getInt("left"));
        f.prefs.edit().putString(Final180PhotoRoi.PREFERENCE,photo.toJson().toString()).putString(Final180PhotoRoiApproval.PREFERENCE,proof.toJson().toString()).commit();
        ShadowSystemClock.advanceBy(Duration.ofMillis(600));ShadowLooper.shadowMainLooper().idle();fresh();
        Object ready=TvBboxStartUiTest.get(f.main,"preparedExperimentStart");assertNotNull("F6 worker must use the captured proof: "+TvBboxStartUiTest.get(f.main,"experimentPreparationError"),ready);
        ResearchExecutionConfig frozen=(ResearchExecutionConfig)TvBboxStartUiTest.get(ready,"config");
        assertNotNull(frozen.final180PhotoRoi());assertEquals("R0_FINAL",frozen.variant);assertEquals(photo.left,frozen.final180PhotoRoi().left);
        assertEquals(2,frozen.plate.cpuThreads);assertEquals(2,frozen.character.cpuThreads);assertFalse(frozen.vehicle.enabled);
        assertTrue(admit(ready));TvBboxProducerFixture.drain(f.sessions.store());
        TvBboxStartUiTest.call(f.main,"renderAnalysisControls",new Class<?>[0]);
        String frozenCaption=((android.widget.TextView)f.main.findViewById(R.id.tv_frame_status)).getText().toString();
        assertTrue(frozenCaption,frozenCaption.contains("F6 · test końcowy"));assertTrue(frozenCaption,frozenCaption.contains("Strategia R0"));
        assertTrue(frozenCaption,frozenCaption.contains("CPU ×2"));
        f.prefs.edit().putBoolean(Final180PhotoRoi.ENABLED,false).putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"end_to_end")
                .putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,"r2_two_roi").putInt(SettingsActivity.KEY_REVISION,7).commit();
        TvBboxStartUiTest.call(f.main,"renderAnalysisControls",new Class<?>[0]);
        assertEquals("running caption must use the frozen session",frozenCaption,((android.widget.TextView)f.main.findViewById(R.id.tv_frame_status)).getText().toString());
        f.prefs.edit().putString(Final180PhotoRoi.PREFERENCE,"{}").putString(Final180PhotoRoiApproval.PREFERENCE,"{}").commit();
        CountDownLatch stopEntered=new CountDownLatch(1),stopRelease=new CountDownLatch(1);
        executor.execute(()->{stopEntered.countDown();try{stopRelease.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(stopEntered.await(5,TimeUnit.SECONDS));
        TvBboxStartUiTest.call(f.main,"finishAnalysisMeasurement",new Class<?>[]{ExperimentSession.CompletionReason.class},ExperimentSession.CompletionReason.MANUAL);
        try{
            assertTrue(f.sessions.finalizing());TvBboxStartUiTest.call(f.main,"renderAnalysisControls",new Class<?>[0]);
            assertEquals("finalizing caption must use the same frozen session",frozenCaption,((android.widget.TextView)f.main.findViewById(R.id.tv_frame_status)).getText().toString());
        }finally{stopRelease.countDown();}
        executor.submit(()->{}).get(15,TimeUnit.SECONDS);
        ((ExecutorService)TvBboxStartUiTest.get(f.sessions,"finalizer")).submit(()->{}).get(15,TimeUnit.SECONDS);ShadowLooper.shadowMainLooper().idle();
        ResearchSessionStore s=f.sessions.store();assertEquals(ResearchSessionStore.State.PARTIAL,s.state());
        java.io.File archive=new java.io.File(s.directory(),"final/"+s.sessionId()+".alprsession");assertEquals("PARTIAL",F6ProducerContractVerifier.validate(archive));
        JSONObject persisted=new JSONObject(new String(Files.readAllBytes(new java.io.File(s.directory(),"session.json").toPath()),StandardCharsets.UTF_8));
        assertEquals(SetupJson.canonical(frozen.toJson()),SetupJson.canonical(persisted.getJSONObject("execution")));
        assertEquals(0,persisted.getJSONObject("f6_evidence").getLong("mt_started_count"));
        String output=System.getenv("F6_FIXTURES");if(output!=null){Files.createDirectories(Paths.get(output));Files.copy(archive.toPath(),Paths.get(output,"lifecycle-main-activity-captured-config-stop-EMPTY.alprsession"),StandardCopyOption.REPLACE_EXISTING);}
    }
    @Test public void hardwareFailuresHaveDistinctCodesAndNoUnconfirmedSnapshot()throws Exception{
        Object bound=ReflectionHelpers.getField(camera,"camera");
        ReflectionHelpers.setField(camera,"camera",null);assertEquals(CameraSetupReadiness.Reason.CAMERA_NOT_BOUND,camera.captureSetupReadiness().reason);
        ReflectionHelpers.setField(camera,"camera",bound);ReflectionHelpers.setField(camera,"requestedSelection",new CameraSelection("0","2"));
        assertEquals(CameraSetupReadiness.Reason.CAMERA_ROUTE_UNCONFIRMED,camera.captureSetupReadiness().reason);assertNull(camera.setupSnapshot());
        ReflectionHelpers.setField(camera,"requestedSelection",CameraSelection.defaultBack());ReflectionHelpers.setField(camera,"setupZoomAcknowledged",false);
        assertEquals(CameraSetupReadiness.Reason.ZOOM_NOT_ACKNOWLEDGED,camera.captureSetupReadiness().reason);assertNull(camera.setupSnapshot());
        ReflectionHelpers.setField(camera,"setupZoomAcknowledged",true);ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(500));
        assertEquals(CameraSetupReadiness.Reason.NO_FRESH_CAMERA_FRAME,camera.captureSetupReadiness().reason);assertNull(camera.setupSnapshot());
        fresh();assertEquals(CameraSetupReadiness.Reason.READY,camera.captureSetupReadiness().reason);
    }
    @Test public void expiredPublishedPositiveSnapshotCannotAuthorizeWorker()throws Exception{
        assertNotNull(camera.setupSnapshot());ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(600));
        CameraSetupControl.State cached=ReflectionHelpers.getField(camera,"publishedSetup");assertNotNull(cached.applied);
        assertNull(executor.submit(camera::setupSnapshot).get(5,TimeUnit.SECONDS));
        fresh();assertNotNull(camera.setupSnapshot());
    }
    @Test public void frameGapKeepsPreparedModelsAndPendingIntentUntilExplicitCancel()throws Exception{
        TvBboxStartUiTest.set(f.main,"experimentTimerConfig",TimerConfig.of(true,300));
        Object ready=prepare(RoiBudgetPolicy.FULL_FRAME);int loads=ModelIo.loads.get();f.intent().request();
        ShadowSystemClock.advanceBy(Duration.ofMillis(600));call("refreshExperimentStartReadiness");
        assertSame(ready,TvBboxStartUiTest.get(f.main,"preparedExperimentStart"));assertTrue(f.intent().pending());
        assertFalse(f.sessions.experiment.isRunning());assertFalse(f.sessions.store().accepting());assertEquals(-1,f.sessions.experiment.timerDeadlineElapsedNanos());
        assertTrue(call("tvBboxStartReason").toString().contains("NO_FRESH_CAMERA_FRAME"));
        f.intent().cancel(); // An explicit STOP still prevents any recovered frame from starting.
        fresh();call("refreshExperimentStartReadiness");assertEquals("READY",call("experimentStartBlocker").toString());
        assertEquals(loads,ModelIo.loads.get());assertFalse(f.sessions.experiment.isRunning());
        f.intent().request();assertTrue(f.intent().consumeIfReady((boolean)call("isExperimentStartReady")));assertTrue(admit(ready));
    }
    @Test public void frameGapDuringExecutorWorkRetainsReadyModelsAndPendingStart()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        executor.execute(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(5,TimeUnit.SECONDS));fresh();
        f.intent().request();TvBboxStartUiTest.call(f.main,"prepareCameraStart",new Class<?>[]{boolean.class},false);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(600));release.countDown();executor.submit(()->{}).get(15,TimeUnit.SECONDS);ShadowLooper.shadowMainLooper().idle();
        assertNotNull(TvBboxStartUiTest.get(f.main,"preparedExperimentStart"));assertTrue(f.intent().pending());assertFalse(f.sessions.experiment.isRunning());
        assertFalse((boolean)TvBboxStartUiTest.get(f.main,"experimentPreparationFailed"));
        f.intent().cancel(); // An explicit STOP still prevents any recovered frame from starting.
        fresh();call("refreshExperimentStartReadiness");assertEquals("READY",call("experimentStartBlocker").toString());assertFalse(f.sessions.experiment.isRunning());
    }
    @Test public void missingCapturedContextCannotReachMeasurement()throws Exception{
        Object ready=prepare(RoiBudgetPolicy.FULL_FRAME);TvBboxStartUiTest.set(ready,"cameraContext",null);assertFalse(admit(ready));
        assertTrue(TvBboxStartUiTest.get(f.main,"cameraPreparationNotice").toString().contains("CAMERA_SETUP_SNAPSHOT_MISSING"));
        assertFalse(f.sessions.experiment.isRunning());assertFalse(f.sessions.store().accepting());
        assertEquals("TV_BBOX_CROP",f.prefs.getString(SettingsActivity.KEY_TV_INPUT_MODE,""));
    }
    @Test public void reboundGenerationCannotReuseSameLensAndZoomConfirmation()throws Exception{
        Object ready=prepare(RoiBudgetPolicy.FULL_FRAME);ReflectionHelpers.setField(camera,"bindingGeneration",f.geometry.bindingGeneration+1);
        assertFalse(admit(ready));assertTrue(TvBboxStartUiTest.get(f.main,"cameraPreparationNotice").toString().contains("CAMERA_SETUP_GENERATION_CHANGED"));
        assertFalse(f.sessions.experiment.isRunning());assertFalse(f.sessions.store().accepting());
    }
    @Test public void lensAndZoomRequestsInvalidatePendingPreparation()throws Exception{
        prepare(RoiBudgetPolicy.FULL_FRAME);f.intent().request();camera.apply(CameraSelection.defaultBack(),2);call("refreshExperimentStartReadiness");
        assertNull(TvBboxStartUiTest.get(f.main,"preparedExperimentStart"));assertFalse(f.intent().pending());assertFalse(f.sessions.experiment.isRunning());
        assertTrue(TvBboxStartUiTest.get(f.main,"cameraPreparationNotice").toString().contains("CAMERA_SETUP_SNAPSHOT_STALE"));
        camera.apply(new CameraSelection("0","2"),1);assertEquals(CameraSetupReadiness.Reason.CAMERA_ROUTE_UNCONFIRMED,camera.captureSetupReadiness().reason);
        assertFalse(f.sessions.store().accepting());
    }
    @Test public void changedGeometryAndStaleCalibrationStayBlocked()throws Exception{
        Object ready=prepare(RoiBudgetPolicy.FULL_FRAME);CameraPreviewGeometry wrong=TvBboxCropTest.geometry(640,400,0,640,400,new double[]{0,0,640,400},800,600,2);
        TvBboxStartUiTest.set(f.tv,"geometry",wrong);TvBboxStartUiTest.set(f.tv,"calibration",TvSetupCalibration.confirm(Arrays.asList(new Point2(.125f,.15f),new Point2(.875f,.20f),new Point2(.85f,.85f),new Point2(.15f,.80f)),wrong,1,"2026-11-06T20:00:00Z"));
        assertTrue(call("tvBboxStartReason").toString().contains("CAMERA_SETUP_GEOMETRY_MISMATCH"));assertFalse(admit(ready));assertFalse(f.sessions.experiment.isRunning());
        TvBboxStartUiTest.set(f.tv,"geometry",f.geometry);f.confirm("2026-11-06T20:00:00Z");
        TvBboxStartUiTest.set(f.tv,"calibration",((TvSetupCalibration)TvBboxStartUiTest.get(f.tv,"calibration")).invalidated("SYNTHETIC_STALE"));
        assertTrue(call("tvBboxStartReason").toString().contains("STALE"));assertFalse(f.sessions.store().accepting());
    }
    @Test public void oldBindingCallbackAfterStopCannotRestoreReadiness()throws Exception{
        int binding=f.geometry.bindingGeneration;camera.stop();
        ReflectionHelpers.callInstanceMethod(camera,"verifySetupFrame",ReflectionHelpers.ClassParameter.from(int.class,binding),ReflectionHelpers.ClassParameter.from(long.class,124L));
        assertEquals(CameraSetupReadiness.Reason.CAMERA_NOT_BOUND,camera.captureSetupReadiness().reason);assertNull(camera.setupSnapshot());
        assertTrue(call("tvBboxStartReason").toString().contains("CAMERA_NOT_BOUND"));assertFalse(f.sessions.experiment.isRunning());
    }
    @Test public void pauseAndResumeAfterOptionsRecoversWithoutRestoringOldStartIntent()throws Exception{
        resumeFromOptions(false);
    }
    @Test public void changedOptionsRevisionWaitsForAnExplicitStartBeforePreparingCurrentConfig()throws Exception{
        resumeFromOptions(true);
    }
    void resumeFromOptions(boolean changeSettings)throws Exception{
        SavedStateRegistryController saved=ReflectionHelpers.getField(f.main,"mSavedStateRegistryController");saved.performRestore(null);
        FragmentController fragments=ReflectionHelpers.getField(f.main,"mFragments");fragments.attachHost(null);fragments.dispatchCreate();fragments.dispatchActivityCreated();
        prepare(RoiBudgetPolicy.FULL_FRAME);f.intent().request();call("onPause");executor.submit(()->{}).get(15,TimeUnit.SECONDS);
        if(changeSettings)f.prefs.edit().putInt(SettingsActivity.KEY_REVISION,1).putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"cpu_4").commit();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(600));assertFalse(f.intent().pending());fresh();call("onResume");
        executor.submit(()->{}).get(15,TimeUnit.SECONDS);ShadowLooper.shadowMainLooper().idle();
        assertNull("Returning from options must not allocate another preparation",TvBboxStartUiTest.get(f.main,"preparedExperimentStart"));
        assertFalse(f.intent().pending());assertFalse(f.sessions.experiment.isRunning());assertFalse(f.sessions.store().accepting());
        Object recovered=prepare(RoiBudgetPolicy.FULL_FRAME);assertNotNull(recovered);assertEquals("READY",call("experimentStartBlocker").toString());
        ResearchExecutionConfig config=(ResearchExecutionConfig)TvBboxStartUiTest.get(recovered,"config");
        assertEquals(changeSettings?4:2,config.plate.cpuThreads);assertEquals(2,config.character.cpuThreads);
        assertTrue(f.intent().pending());assertFalse(f.sessions.experiment.isRunning());assertFalse(f.sessions.store().accepting());call("onPause");
    }
    @Implements(value=AlprPipeline.class,isInAndroidSdk=false)
    public static class ModelIo {
        static final AtomicInteger loads=new AtomicInteger();
        @Implementation public void prepareModels(){loads.incrementAndGet();}
    }
}
