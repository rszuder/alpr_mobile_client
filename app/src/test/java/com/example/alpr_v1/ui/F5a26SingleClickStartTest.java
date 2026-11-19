package com.example.alpr_v1.ui;

import android.app.Application;
import android.os.SystemClock;
import android.util.Size;
import android.widget.TextView;
import com.example.alpr_v1.*;
import com.example.alpr_v1.camera.*;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.metrics.MetricsCollector;
import com.example.alpr_v1.pipeline.*;
import com.google.android.material.button.MaterialButton;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Actual button -> executor -> measurement -> camera callbacks; only model/camera IO is synthetic. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",
        shadows={F5a24CameraStartTest.ModelIo.class,F5a26SingleClickStartTest.StartIo.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class F5a26SingleClickStartTest {
    F5a24CameraStartTest hardware;TvBboxStartUiTest f;ExecutorService worker;
    @Before public void setup()throws Exception{
        StartIo.starts.set(0);StartIo.previews.set(0);hardware=new F5a24CameraStartTest();hardware.setup();f=hardware.f;worker=hardware.executor;
        ((LivePresentationController)TvBboxStartUiTest.get(f.main,"livePresentation")).dispose();call("bindViews");
        set("searchPlateButton",f.main.findViewById(R.id.search_plate_button));set("releaseTargetButton",f.main.findViewById(R.id.release_target_button));
        call("configureAnalysisControls");
    }
    @After public void close()throws Exception{
        if(f!=null){((LivePresentationController)TvBboxStartUiTest.get(f.main,"livePresentation")).dispose();}
        if(hardware!=null)hardware.close();
    }
    Object call(String name)throws Exception{return TvBboxStartUiTest.call(f.main,name,new Class<?>[0]);}
    void set(String name,Object value)throws Exception{TvBboxStartUiTest.set(f.main,name,value);}
    Object get(String name)throws Exception{return TvBboxStartUiTest.get(f.main,name);}
    MaterialButton button(){return f.main.findViewById(R.id.analysis_start_button);}
    MetricsCollector metrics()throws Exception{return (MetricsCollector)get("metricsCollector");}
    void drain()throws Exception{worker.submit(()->{}).get(15,TimeUnit.SECONDS);ShadowLooper.shadowMainLooper().idle();}
    void normal()throws Exception{
        set("experimentModeEnabled",false);f.prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).commit();
        set("experimentTimerConfig",TimerConfig.of(true,300));call("renderAnalysisControls");
    }
    void startNormal()throws Exception{normal();button().performClick();drain();assertTrue((boolean)get("cameraStarted"));assertEquals(1,StartIo.starts.get());}
    CountDownLatch block()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        worker.execute(()->{entered.countDown();try{release.await(15,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(5,TimeUnit.SECONDS));return release;
    }
    void research(RoiBudgetPolicy policy)throws Exception{
        set("experimentRoiBudgetPolicy",policy);hardware.fresh();int loads=F5a24CameraStartTest.ModelIo.loads.get();
        assertTrue(button().isEnabled());assertTrue(button().performClick());
        drain();
        assertTrue((boolean)get("cameraStarted"));assertTrue(f.sessions.experiment.isRunning());assertEquals(1,StartIo.starts.get());
        assertEquals(policy,f.sessions.experiment.frozenExecutionConfig().roiBudgetPolicy);
        assertFalse(f.intent().pending());call("refreshExperimentStartReadiness");assertEquals(1,StartIo.starts.get());
        assertEquals(loads+1,F5a24CameraStartTest.ModelIo.loads.get());
        assertEquals("Stop",button().getText().toString());assertFalse(f.main.findViewById(R.id.experiment_timer_button).isEnabled());
    }
    @Test public void pendingSafSelectionRestoresActualArchiveAndKindWithoutStartingMeasurement()throws Exception{
        java.io.File selected=new java.io.File(f.main.getFilesDir(),"research/sessions/SYNTHETIC/final/SYNTHETIC.alprsession");
        set("pendingPersistentResearchArchive",selected);set("pendingExportKind",com.example.alpr_v1.metrics.ResearchArchive.Kind.RESEARCH_SESSION);
        android.os.Bundle saved=new android.os.Bundle();
        TvBboxStartUiTest.call(f.main,"saveResearchExportSelection",new Class<?>[]{android.os.Bundle.class},saved);
        set("pendingPersistentResearchArchive",null);set("pendingExportKind",null);
        TvBboxStartUiTest.call(f.main,"restoreResearchExportSelection",new Class<?>[]{android.os.Bundle.class},saved);
        assertEquals(selected,get("pendingPersistentResearchArchive"));
        assertEquals(com.example.alpr_v1.metrics.ResearchArchive.Kind.RESEARCH_SESSION,get("pendingExportKind"));
        assertFalse(f.sessions.experiment.isRunning());assertEquals(0,StartIo.starts.get());
    }
    @Test public void sessionHistoryEntrySurvivesNewViewModelWithoutFinishedRamSession()throws Exception{
        MaterialButton history=f.main.findViewById(R.id.export_report_button);set("exportReportButton",history);
        normal();call("renderAnalysisControls");assertEquals(android.view.View.VISIBLE,history.getVisibility());
        assertTrue(history.isEnabled());assertEquals("Sesje / Eksport",history.getText().toString());
        ResearchSessionViewModel old=f.sessions;TvBboxStartUiTest.call(old,"onCleared",new Class<?>[0]);
        f.sessions=new ResearchSessionViewModel(RuntimeEnvironment.getApplication());
        set("researchSessions",f.sessions);set("experimentSession",f.sessions.experiment);call("renderAnalysisControls");
        assertEquals(ExperimentSession.State.IDLE,f.sessions.experiment.state());
        assertEquals(android.view.View.VISIBLE,history.getVisibility());assertTrue(history.isEnabled());
    }
    @Test public void stopWithRejectedCropBarrierKeepsSessionAndReleasesItsFinalizationOwner()throws Exception{
        research(RoiBudgetPolicy.FULL_FRAME);
        ResearchExecutionConfig frozen=f.sessions.experiment.frozenExecutionConfig();
        ((ExecutorService)TvBboxStartUiTest.get(f.gallery,"cropPreparer")).shutdown();
        button().performClick();drain();
        assertFalse(f.sessions.finalizing());assertFalse(f.sessions.experiment.isRunning());
        assertEquals(ResearchSessionStore.State.ERROR,f.sessions.store().state());
        assertTrue(new java.io.File(f.sessions.store().directory(),"session.json").isFile());
        assertTrue(f.sessions.operationDescription().contains("RejectedExecutionException"));
        assertEquals(frozen,f.sessions.experiment.snapshot().frozenExecutionConfig);
    }
    @Test public void browsingHistoryCannotLaunchFilePickerOverRunningMeasurement()throws Exception{
        research(RoiBudgetPolicy.FULL_FRAME);
        assertFalse((boolean)call("researchDocumentDestinationAvailable"));
        assertTrue(f.sessions.experiment.isRunning());assertTrue((boolean)get("cameraStarted"));
        assertNull(get("pendingExportKind"));assertEquals(1,StartIo.starts.get());
    }
    @Test public void oneClickStartsReadyR0()throws Exception{research(RoiBudgetPolicy.FULL_FRAME);}
    @Test public void oneClickStartsReadyR1()throws Exception{research(RoiBudgetPolicy.ONE_ROI);}
    @Test public void oneClickStartsReadyR2()throws Exception{research(RoiBudgetPolicy.TWO_ROI);}
    @Test public void oneClickStartsOrdinaryAnalysisAfterPreparation()throws Exception{
        startNormal();assertTrue(f.sessions.timerState().running);assertEquals(ExperimentSession.State.IDLE,f.sessions.experiment.state());assertNull(f.sessions.store());
        assertTrue(metrics().isMeasurementSessionActive());assertFalse(f.intent().pending());
    }
    @Test public void nextOrdinarySessionKeepsPresetAndStartsFromItsFullDuration()throws Exception{
        startNormal();String first=f.sessions.timerState().id;button().performClick();drain();
        TimerConfig preset=(TimerConfig)get("experimentTimerConfig");assertTrue(preset.enabled());assertEquals(300,preset.durationSeconds());
        assertEquals("5:00",((TextView)f.main.findViewById(R.id.experiment_timer_value)).getText().toString());
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2));button().performClick();drain();
        assertTrue((boolean)get("cameraStarted"));assertNotEquals(first,f.sessions.timerState().id);
        assertEquals(SystemClock.elapsedRealtimeNanos()+300_000_000_000L,f.sessions.timerState().deadlineElapsedNanos);assertEquals(2,StartIo.starts.get());
    }
    @Test public void previousFinalizationKeepsOneOrdinaryStartRequestUntilReady()throws Exception{
        normal();TvBboxStartUiTest.set(f.sessions,"finalizing",true);button().performClick();
        assertTrue(f.intent().pending());assertEquals("Stop",button().getText().toString());assertFalse(((AnalysisStartGate)get("analysisStartGate")).preparing());
        TvBboxStartUiTest.set(f.sessions,"finalizing",false);call("refreshExperimentStartReadiness");drain();
        assertTrue((boolean)get("cameraStarted"));assertEquals(1,StartIo.starts.get());assertFalse(f.intent().pending());
    }
    @Test public void firstResearchClickWaitsForZoomConfirmationAndThenStartsOnce()throws Exception{
        TvBboxStartUiTest.set(hardware.camera,"setupZoomAcknowledged",false);call("renderAnalysisControls");assertTrue(button().isEnabled());
        button().performClick();assertTrue(f.intent().pending());assertFalse(f.sessions.experiment.isRunning());assertEquals(0,F5a24CameraStartTest.ModelIo.loads.get());
        TvBboxStartUiTest.set(hardware.camera,"setupZoomAcknowledged",true);hardware.fresh();call("refreshExperimentStartReadiness");drain();
        assertTrue(f.sessions.experiment.isRunning());assertEquals(1,StartIo.starts.get());assertEquals(1,F5a24CameraStartTest.ModelIo.loads.get());
    }
    @Test public void unboundCameraClickStartsPreviewButCannotStartMeasurementBeforeConfirmation()throws Exception{
        hardware.camera.stop();set("cameraPreviewActive",false);call("renderAnalysisControls");assertTrue(button().isEnabled());
        button().performClick();assertTrue(f.intent().pending());assertTrue((boolean)get("cameraPreviewActive"));assertEquals(1,StartIo.previews.get());
        assertEquals(0,StartIo.starts.get());assertFalse(f.sessions.experiment.isRunning());assertNull(f.sessions.store());assertNull(get("preparedExperimentStart"));
        button().performClick();assertFalse(f.intent().pending());
    }
    @Test public void previousResearchFinalizationKeepsOneStartIntentWithoutAllowingEarlyAdmission()throws Exception{
        TvBboxStartUiTest.set(f.sessions,"finalizing",true);call("renderAnalysisControls");assertTrue(button().isEnabled());
        button().performClick();assertTrue(f.intent().pending());assertFalse(f.sessions.experiment.isRunning());assertEquals(0,StartIo.starts.get());
        TvBboxStartUiTest.set(f.sessions,"finalizing",false);hardware.fresh();call("refreshExperimentStartReadiness");drain();
        assertTrue(f.sessions.experiment.isRunning());assertEquals(1,StartIo.starts.get());assertFalse(f.intent().pending());
    }
    @Test public void startShowsStopImmediatelyWhileWorkerIsBlockedAndStopCancelsItsLateCompletion()throws Exception{
        normal();CountDownLatch release=block();
        try{
            button().performClick();assertEquals("Stop",button().getText().toString());assertTrue(f.intent().pending());
            assertTrue(((AnalysisStartGate)get("analysisStartGate")).preparing());assertFalse(f.sessions.timerState().running);
            button().performClick();assertFalse(f.intent().pending());assertFalse(((AnalysisStartGate)get("analysisStartGate")).preparing());
        }finally{release.countDown();}
        drain();assertFalse((boolean)get("cameraStarted"));assertEquals(0,StartIo.starts.get());assertFalse(metrics().isMeasurementSessionActive());
    }
    @Test public void rapidStartStopStartExecutesOnlyTheLastAuthorizedRequest()throws Exception{
        normal();CountDownLatch release=block();
        try{button().performClick();button().performClick();button().performClick();assertTrue(f.intent().pending());}
        finally{release.countDown();}
        drain();drain();assertTrue((boolean)get("cameraStarted"));assertEquals(1,StartIo.starts.get());assertTrue(f.sessions.timerState().running);
    }
    @Test public void stopReturnsBeforeQueuedPipelineCleanupAndClosesAdmissionImmediately()throws Exception{
        startNormal();long generation=(long)get("analysisControlGeneration");CountDownLatch release=block();
        try{
            button().performClick();assertEquals(1,release.getCount());assertFalse((boolean)get("cameraStarted"));
            assertFalse(f.sessions.timerState().running);assertFalse(metrics().isMeasurementSessionActive());assertEquals("Start",button().getText().toString());
            assertEquals(false,TvBboxStartUiTest.call(f.main,"cameraAnalysisAdmitted",new Class<?>[]{long.class},generation));
        }finally{release.countDown();}
        drain();assertFalse((boolean)get("cameraStarted"));assertEquals(1,StartIo.starts.get());
    }
    @Test public void frameGapAtClickKeepsOneIntentAndFreshFrameStartsWithoutAnotherClick()throws Exception{
        ShadowSystemClock.advanceBy(Duration.ofMillis(600));call("renderAnalysisControls");
        assertTrue(button().isEnabled());button().performClick();assertTrue(f.intent().pending());assertFalse(f.sessions.experiment.isRunning());assertEquals(0,StartIo.starts.get());
        assertTrue(((TextView)f.main.findViewById(R.id.live_status_secondary)).getText().toString().contains("NO_FRESH_CAMERA_FRAME"));
        hardware.fresh();call("refreshExperimentStartReadiness");drain();assertTrue(f.sessions.experiment.isRunning());assertEquals(1,StartIo.starts.get());assertFalse(f.intent().pending());
    }
    @Test public void frameGapDuringPreparationWaitsWithLoadedModelsAndOriginalIntent()throws Exception{
        CountDownLatch release=block();hardware.fresh();
        try{assertTrue(button().isEnabled());button().performClick();assertTrue(f.intent().pending());ShadowSystemClock.advanceBy(Duration.ofMillis(600));}
        finally{release.countDown();}
        drain();assertTrue(f.intent().pending());assertNotNull(get("preparedExperimentStart"));assertEquals(1,F5a24CameraStartTest.ModelIo.loads.get());
        assertFalse(f.sessions.experiment.isRunning());assertEquals(-1,f.sessions.experiment.timerDeadlineElapsedNanos());
        hardware.fresh();call("refreshExperimentStartReadiness");assertTrue(f.sessions.experiment.isRunning());assertEquals(1,StartIo.starts.get());assertEquals(1,F5a24CameraStartTest.ModelIo.loads.get());
    }
    @Test public void explicitStopDuringFrameWaitPreventsAutomaticStartOnRecovery()throws Exception{
        ShadowSystemClock.advanceBy(Duration.ofMillis(600));call("renderAnalysisControls");
        button().performClick();assertTrue(f.intent().pending());button().performClick();assertFalse(f.intent().pending());
        drain();hardware.fresh();call("refreshExperimentStartReadiness");assertFalse(f.sessions.experiment.isRunning());assertEquals(0,StartIo.starts.get());
    }
    @Test public void actualZoomChangeCancelsPendingStartInsteadOfUsingOldConfirmation()throws Exception{
        CountDownLatch release=block();hardware.fresh();
        try{button().performClick();hardware.camera.apply(CameraSelection.defaultBack(),2);call("refreshExperimentStartReadiness");assertFalse(f.intent().pending());}
        finally{release.countDown();}
        drain();assertFalse(f.sessions.experiment.isRunning());assertEquals(0,StartIo.starts.get());assertEquals("TV_BBOX_CROP",f.prefs.getString(SettingsActivity.KEY_TV_INPUT_MODE,""));
    }
    @Test public void missingPreparationContextIsRejectedEvenDuringAFrameGap()throws Exception{
        Object ready=hardware.prepare(RoiBudgetPolicy.FULL_FRAME);TvBboxStartUiTest.set(ready,"cameraContext",null);f.intent().request();
        ShadowSystemClock.advanceBy(Duration.ofMillis(600));assertFalse(hardware.admit(ready));
        assertFalse(f.intent().pending());assertNull(get("preparedExperimentStart"));assertFalse(f.sessions.experiment.isRunning());assertEquals(0,StartIo.starts.get());
    }
    @Test public void technicalResumeUsesCurrentContextAndKeepsMeasurementT0AndDeadline()throws Exception{
        startNormal();long deadline=f.sessions.timerState().deadlineElapsedNanos;String id=f.sessions.timerState().id;
        long t0=(long)TvBboxStartUiTest.get(metrics(),"sessionStartedElapsedNanos");set("cameraStarted",false);ShadowSystemClock.advanceBy(Duration.ofSeconds(3));
        TvBboxStartUiTest.call(f.main,"startCamera",new Class<?>[]{boolean.class},false);
        assertTrue((boolean)get("cameraStarted"));assertEquals(2,StartIo.starts.get());assertEquals(deadline,f.sessions.timerState().deadlineElapsedNanos);
        assertEquals(id,f.sessions.timerState().id);assertEquals(t0,TvBboxStartUiTest.get(metrics(),"sessionStartedElapsedNanos"));
    }
    @Test public void expiredOrStoppedRunCannotBeResumedByAnOldCallback()throws Exception{
        startNormal();ShadowSystemClock.advanceBy(Duration.ofSeconds(301));set("cameraStarted",false);
        TvBboxStartUiTest.call(f.main,"startCamera",new Class<?>[]{boolean.class},false);assertFalse((boolean)get("cameraStarted"));assertEquals(1,StartIo.starts.get());
        f.sessions.closeMeasurement(ExperimentSession.CompletionReason.TIMER,metrics());set("cameraStarted",true);
        TvBboxStartUiTest.call(f.main,"onAnalysisTimerExpired",new Class<?>[]{String.class},f.sessions.timerState().id);
        assertFalse((boolean)get("cameraStarted"));assertEquals("0:00",((TextView)f.main.findViewById(R.id.experiment_timer_value)).getText().toString());
        TvBboxStartUiTest.call(f.main,"startCamera",new Class<?>[]{boolean.class},false);assertEquals(1,StartIo.starts.get());assertNull(f.sessions.store());
    }
    @Implements(value=CameraController.class,isInAndroidSdk=false)
    public static class StartIo {
        static final AtomicInteger starts=new AtomicInteger();
        static final AtomicInteger previews=new AtomicInteger();
        @Implementation public void start(CameraController.FrameHandler frames,CameraController.LumaFrameHandler luma,
                CameraController.ErrorHandler errors,Size size,boolean high){if(luma==null)previews.incrementAndGet();else starts.incrementAndGet();}
    }
}
