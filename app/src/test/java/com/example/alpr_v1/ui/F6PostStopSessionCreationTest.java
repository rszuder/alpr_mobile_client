package com.example.alpr_v1.ui;

import android.app.Application;
import android.util.Size;
import com.example.alpr_v1.*;
import com.example.alpr_v1.camera.CameraController;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.metrics.ResearchArchive;
import com.example.alpr_v1.setup.TvBboxCrop;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Actual Activity -> preparation executor -> durable store -> finalizer -> history.
 * Only camera/model IO is synthetic. No device or existing export is modified. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",
        shadows={F5a24CameraStartTest.ModelIo.class,F6PostStopSessionCreationTest.CameraIo.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class F6PostStopSessionCreationTest {
    /** The synthetic physical source remains available when preview resumes after STOP.
     * CameraX resource IO is replaced; source/calibration admission still uses the real controller. */
    @Implements(value=CameraController.class,isInAndroidSdk=false)
    public static class CameraIo {
        @org.robolectric.annotation.RealObject CameraController owner;
        @Implementation public void start(CameraController.FrameHandler frames,CameraController.LumaFrameHandler luma,
                CameraController.ErrorHandler errors,Size size,boolean high){
            if(luma==null)F5a26SingleClickStartTest.StartIo.previews.incrementAndGet();
            else F5a26SingleClickStartTest.StartIo.starts.incrementAndGet();
        }
        @Implementation public void stop(){owner.releaseFocus("STOP / zamknięcie kamery");}
    }
    F5a26SingleClickStartTest h;
    @Before public void setup()throws Exception{
        h=new F5a26SingleClickStartTest();h.setup();
        drain(h.f.sessions,"historyExecutor");
        h.hardware.fresh();TvBboxCrop input=h.f.tv.selectionForInput(h.hardware.camera.captureSetupReadiness().applied);
        Final180PhotoRoi roi=Final180PhotoRoi.prepare(new JSONArray("[{x:.25,y:.3},{x:.7,y:.3},{x:.7,y:.7},{x:.25,y:.7}]"),input);
        Final180PhotoRoiApproval proof=Final180PhotoRoiApproval.capture(roi,input,new JSONObject(h.f.tv.approvedTvRecord()),"SYNTHETIC_POST_STOP_TEST");
        h.f.prefs.edit().putBoolean(Final180PhotoRoi.ENABLED,true)
                .putString(Final180PhotoRoi.PREFERENCE,roi.toJson().toString())
                .putString(Final180PhotoRoiApproval.PREFERENCE,proof.toJson().toString())
                .putString(SettingsActivity.KEY_EXPERIMENT_VARIANT,"R0_FINAL")
                .putString(SettingsActivity.KEY_EXPERIMENT_NOTES,"TEST_ONLY; SYNTHETIC camera/model IO; NOT device acceptance").commit();
        android.content.SharedPreferences tvPrefs=(android.content.SharedPreferences)TvBboxStartUiTest.get(h.f.tv,"preferences");
        tvPrefs.edit().putString(Final180PhotoRoi.PREFERENCE,roi.toJson().toString())
                .putString(Final180PhotoRoiApproval.PREFERENCE,proof.toJson().toString())
                .putString("tv_setup_calibration_v1",h.f.tv.approvedTvRecord()).commit();
    }
    @After public void close()throws Exception{if(h!=null)h.close();}
    void drain(Object owner,String field)throws Exception{
        ((ExecutorService)TvBboxStartUiTest.get(owner,field)).submit(()->{}).get(30,TimeUnit.SECONDS);
        ShadowLooper.shadowMainLooper().idle();
    }
    Set<String> ids(){
        File[] dirs=ResearchSessionStore.sessionsRoot(RuntimeEnvironment.getApplication()).listFiles();
        Set<String> result=new TreeSet<>();if(dirs!=null)for(File d:dirs)if(d.isDirectory())result.add(d.getName());return result;
    }
    void refreshMany()throws Exception{
        for(int i=0;i<60;i++){
            h.hardware.fresh();h.call("refreshExperimentStartReadiness");h.drain();
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(250));
        }
    }
    ResearchSessionStore start()throws Exception{
        h.hardware.fresh();h.call("renderAnalysisControls");assertTrue(h.button().isEnabled());h.button().performClick();h.drain();
        assertTrue(h.f.sessions.experiment.isRunning());assertFalse(h.f.intent().pending());return h.f.sessions.store();
    }
    void stopAndFinish()throws Exception{
        h.button().performClick();h.drain();drain(h.f.sessions,"finalizer");
        assertFalse(h.f.sessions.finalizing());assertFalse(h.f.sessions.experiment.isRunning());assertFalse(h.f.intent().pending());
    }
    void history()throws Exception{
        TvBboxStartUiTest.call(h.f.main,"requestExportDestination",new Class<?>[]{ResearchArchive.Kind.class},ResearchArchive.Kind.RESEARCH_SESSION);
        drain(h.f.main,"backgroundExecutor");
        android.app.Dialog dialog=ShadowDialog.getLatestDialog();if(dialog!=null)dialog.dismiss();
    }
    void restartInventory()throws Exception{
        TvBboxStartUiTest.call(h.f.sessions,"onCleared",new Class<?>[0]);
        h.f.sessions=new ResearchSessionViewModel(RuntimeEnvironment.getApplication());
        h.set("researchSessions",h.f.sessions);h.set("experimentSession",h.f.sessions.experiment);
        drain(h.f.sessions,"historyExecutor");
    }
    @Test public void passiveReadinessNeverAllocatesASessionOrLoadsModels()throws Exception{
        Set<String> before=ids();refreshMany();
        assertEquals("Readiness without START must not allocate a durable session",before,ids());
        assertNull(h.f.sessions.store());assertEquals(0,F5a24CameraStartTest.ModelIo.loads.get());
        assertFalse(h.f.intent().pending());
        String status=((android.widget.TextView)h.f.main.findViewById(R.id.live_status)).getText().toString();
        assertFalse("Idle must not claim a running preparation: "+status,status.contains("Przygotowanie modeli i zapisu"));
    }
    @Test public void f3AfLifecycleAndOneStartStopHistoryRestartPreserveCalibration()throws Exception{
        CameraController camera=h.hardware.camera;
        org.robolectric.util.ReflectionHelpers.setField(camera,"focusAfAutoSupported",true);
        java.util.List<androidx.concurrent.futures.CallbackToFutureAdapter.Completer<androidx.camera.core.FocusMeteringResult>> futures=
                org.robolectric.util.ReflectionHelpers.getField(h.hardware.hardware,"focusFutures");
        h.f.prefs.edit().putString("tv_setup_calibration_v1",h.f.tv.approvedTvRecord()).commit();
        String tv=h.f.prefs.getString("tv_setup_calibration_v1",""),photo=h.f.prefs.getString(Final180PhotoRoi.PREFERENCE,""),approval=h.f.prefs.getString(Final180PhotoRoiApproval.PREFERENCE,"");
        Set<String> before=ids();
        camera.focusOnSource(h.f.geometry,320,200);assertEquals("PENDING",camera.focusStatus());
        h.call("renderAnalysisControls");assertEquals("FOCUS",h.call("experimentStartBlocker").toString());assertEquals(before,ids());
        futures.get(futures.size()-1).set(androidx.camera.core.FocusMeteringResult.create(false));ShadowLooper.shadowMainLooper().idle();
        assertEquals("FAILED",camera.focusStatus());assertFalse(camera.focusHeld());
        camera.releaseFocus("retry");camera.focusOnSource(h.f.geometry,320,200);int cancelled=futures.size()-1;
        camera.releaseFocus("cancel");futures.get(cancelled).set(androidx.camera.core.FocusMeteringResult.create(true));ShadowLooper.shadowMainLooper().idle();
        assertFalse(camera.focusHeld());
        camera.focusOnSource(h.f.geometry,320,200);int timedOut=futures.size()-1;
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(6100));assertEquals("FAILED",camera.focusStatus());
        futures.get(timedOut).set(androidx.camera.core.FocusMeteringResult.create(true));ShadowLooper.shadowMainLooper().idle();assertFalse(camera.focusHeld());
        camera.releaseFocus("retry");h.hardware.fresh();camera.focusOnSource(h.f.geometry,320,200);
        futures.get(futures.size()-1).set(androidx.camera.core.FocusMeteringResult.create(true));ShadowLooper.shadowMainLooper().idle();assertTrue(camera.focusHeld());
        java.util.List<androidx.camera.core.FocusMeteringAction> actions=org.robolectric.util.ReflectionHelpers.getField(h.hardware.hardware,"focusActions");
        assertFalse(actions.get(actions.size()-1).isAutoCancelEnabled());
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(7));assertTrue(camera.focusHeld());
        ResearchSessionStore first=start();assertTrue(camera.focusHeld());
        stopAndFinish();assertFalse(camera.focusRequested());assertFalse(camera.focusHeld());
        File archive=new File(first.directory(),"final/"+first.sessionId()+".alprsession");
        assertEquals("PARTIAL",Final180ArchiveValidator.validate(archive));
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)){
            JSONObject saved=new JSONObject(new String(zip.getInputStream(zip.getEntry("session.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("HELD",saved.getJSONObject("camera_observation").getJSONObject("focus").getString("status"));
        }
        Set<String> expected=ids();assertEquals(before.size()+1,expected.size());
        refreshMany();history();history();
        h.f.prefs.edit().putString(Final180PhotoRoiApproval.PREFERENCE,"{}").commit();
        h.call("requestExperimentStart");h.drain();assertFalse(h.f.intent().pending());assertEquals(expected,ids());
        h.f.prefs.edit().putString(Final180PhotoRoiApproval.PREFERENCE,approval).commit();
        restartInventory();refreshMany();history();assertEquals(expected,ids());
        h.hardware.fresh();camera.focusOnSource(h.f.geometry,320,200);int old=futures.size()-1;
        camera.apply(new com.example.alpr_v1.camera.CameraSelection("SYNTHETIC_OTHER_SOURCE",null),1);
        assertEquals("LOST",camera.focusStatus());
        futures.get(old).set(androidx.camera.core.FocusMeteringResult.create(true));ShadowLooper.shadowMainLooper().idle();assertFalse(camera.focusHeld());
        h.call("requestExperimentStart");h.drain();assertEquals(expected,ids());
        assertEquals(tv,h.f.prefs.getString("tv_setup_calibration_v1",""));
        assertEquals(photo,h.f.prefs.getString(Final180PhotoRoi.PREFERENCE,""));assertEquals(approval,h.f.prefs.getString(Final180PhotoRoiApproval.PREFERENCE,""));
    }
    @Test public void directPreparationWithoutStartHasNoDurableSideEffects()throws Exception{
        Set<String> before=ids();h.hardware.fresh();
        TvBboxStartUiTest.call(h.f.main,"prepareCameraStart",new Class<?>[]{boolean.class},false);h.drain();
        assertEquals(before,ids());assertNull(h.f.sessions.store());assertEquals(0,F5a24CameraStartTest.ModelIo.loads.get());
    }
    @Test public void oneStartStopFinalizeAndHistoryReopenCannotPrepareAnotherSession()throws Exception{
        Set<String> before=ids();ResearchSessionStore first=start();stopAndFinish();
        Set<String> expected=new TreeSet<>(before);expected.add(first.sessionId());
        File archive=new File(first.directory(),"final/"+first.sessionId()+".alprsession");
        assertTrue(archive.isFile());assertEquals("PARTIAL",F6ProducerContractVerifier.validate(archive));
        byte[] original=Files.readAllBytes(new File(first.directory(),"session.json").toPath());
        refreshMany();history();history();refreshMany();
        assertEquals(expected,ids());assertSame(first,h.f.sessions.store());
        assertArrayEquals(original,Files.readAllBytes(new File(first.directory(),"session.json").toPath()));
        assertEquals(1,F5a26SingleClickStartTest.StartIo.starts.get());
        String output=System.getenv("F6_POST_STOP_FIXTURES");
        if(output!=null){Files.createDirectories(new File(output).toPath());Files.copy(archive.toPath(),new File(output,"synthetic-main-start-stop-no-frame-PARTIAL.alprsession").toPath());}
    }
    @Test public void rejectedStartDoesNotAllocateAndLaterReadinessCannotReplayIt()throws Exception{
        Set<String> before=ids();String saved=h.f.prefs.getString(Final180PhotoRoiApproval.PREFERENCE,"");
        h.f.prefs.edit().putString(Final180PhotoRoiApproval.PREFERENCE,"{}").commit();
        h.call("requestExperimentStart");h.drain();assertFalse(h.f.intent().pending());assertEquals(before,ids());
        h.f.prefs.edit().putString(Final180PhotoRoiApproval.PREFERENCE,saved).commit();
        refreshMany();history();assertEquals(before,ids());assertFalse(h.f.sessions.experiment.isRunning());
        restartInventory();refreshMany();history();assertEquals(before,ids());assertNull(h.f.sessions.store());
    }
    @Test public void stoppedQueuedPreparationCannotAllocateAfterItsRequestWasCancelled()throws Exception{
        Set<String> before=ids();CountDownLatch release=h.block();
        try{h.hardware.fresh();h.button().performClick();assertTrue(h.f.intent().pending());h.button().performClick();assertFalse(h.f.intent().pending());}
        finally{release.countDown();}
        h.drain();refreshMany();assertEquals(before,ids());assertEquals(0,F5a26SingleClickStartTest.StartIo.starts.get());
    }
    @Test public void replacedRequestCannotBeCompletedByAnOlderPreparation()throws Exception{
        Set<String> before=ids();CountDownLatch release=h.block();
        try{h.hardware.fresh();h.button().performClick();h.button().performClick();h.hardware.fresh();h.button().performClick();}
        finally{release.countDown();}
        h.drain();assertTrue(h.f.sessions.experiment.isRunning());assertEquals(before.size()+1,ids().size());
        ResearchSessionStore accepted=h.f.sessions.store();stopAndFinish();refreshMany();
        assertEquals(before.size()+1,ids().size());assertSame(accepted,h.f.sessions.store());
        assertEquals(1,F5a26SingleClickStartTest.StartIo.starts.get());
    }
    @Test public void newViewModelInventoryAndHistoryAfterStopDoNotCreateSessions()throws Exception{
        ResearchSessionStore first=start();stopAndFinish();Set<String> expected=ids();
        restartInventory();refreshMany();history();
        assertEquals(expected,ids());assertNull(h.f.sessions.store());assertFalse(h.f.sessions.experiment.isRunning());
        assertTrue(ResearchSessionSummary.catalog(ResearchSessionStore.sessionsRoot(RuntimeEnvironment.getApplication())).stream()
                .anyMatch(s->first.sessionId().equals(s.session.optString("session_id"))));
    }
    @Test public void writeProgressLocksNewWorkButKeepsHistoryAndFullDetails()throws Exception{
        Set<String> before=ids();String photo=h.f.prefs.getString(Final180PhotoRoi.PREFERENCE,"");
        ResearchOperationState ops=h.f.sessions.operations;String ticket=ops.begin("SYNTHETIC-PROGRESS","QUEUE_DRAIN","TEST_ONLY");
        try{
            for(String stage:new String[]{"QUEUE_DRAIN","FINALIZING_JSONL","BUILDING_ARCHIVE","VALIDATING_ARCHIVE","COPYING_DOCUMENT","VERIFYING_DOCUMENT"}){
                boolean bytes=stage.endsWith("DOCUMENT");ops.update(ticket,stage,bytes?37:0,bytes?100:0,null);h.call("renderAnalysisControls");
                assertFalse(stage,h.button().isEnabled());assertFalse(h.f.main.findViewById(R.id.tv_frame_edit).isEnabled());
                assertFalse(h.f.main.findViewById(R.id.f6_photo_roi_edit).isEnabled());assertFalse(h.f.main.findViewById(R.id.f6_focus_toggle).isEnabled());
                for(int id:new int[]{R.id.tv_frame_edit,R.id.f6_photo_roi_edit,R.id.f6_focus_toggle}){
                    android.widget.TextView control=h.f.main.findViewById(id);
                    int disabled=control.getTextColors().getColorForState(new int[]{-android.R.attr.state_enabled},0);
                    assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(disabled,h.f.main.getColor(R.color.alpr_surface))>=4.5);
                    assertNotEquals(disabled,control.getTextColors().getColorForState(new int[]{android.R.attr.state_enabled},0));
                }
                assertFalse(h.f.main.findViewById(R.id.camera_preview_button).isEnabled());assertFalse(h.f.main.findViewById(R.id.experiment_timer_button).isEnabled());
                assertFalse(h.f.main.findViewById(R.id.auto_zoom_button).isEnabled());assertFalse(h.f.main.findViewById(R.id.live_scene_mode_toggle).isEnabled());
                assertFalse(h.f.main.findViewById(R.id.collection_toggle).isEnabled());
                assertTrue(h.f.main.findViewById(R.id.export_report_button).isEnabled());assertTrue(h.f.main.findViewById(R.id.session_operation_details).isEnabled());
                assertEquals(android.view.View.VISIBLE,h.f.main.findViewById(R.id.session_operation_panel).getVisibility());
                com.google.android.material.progressindicator.LinearProgressIndicator p=h.f.main.findViewById(R.id.session_operation_progress);
                assertEquals(!bytes,p.isIndeterminate());if(bytes)assertEquals(37,p.getProgress());
                assertEquals(!bytes,ResearchOperationPresentation.from(ops.presentation()).detail.contains("START"));
                h.call("requestExperimentStart");assertFalse(h.f.intent().pending());assertEquals(before,ids());
                assertEquals(false,h.call("researchDocumentDestinationAvailable"));ShadowDialog.getLatestDialog().dismiss();
            }
            android.net.Uri document=android.net.Uri.parse("content://synthetic/blocked-export");java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
            Shadows.shadowOf(RuntimeEnvironment.getApplication().getContentResolver()).registerOutputStream(document,output);
            h.f.sessions.exportArchive(new File("SYNTHETIC_NOT_BUILT.alprsession"),document);drain(h.f.sessions,"historyExecutor");
            assertEquals(0,output.size());assertTrue(ops.historyDescription().contains("Nie rozpoczęto kopiowania"));
            h.f.main.findViewById(R.id.session_operation_details).performClick();
            android.app.Dialog dialog=ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());
            assertTrue(((android.widget.TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains(ticket));dialog.dismiss();
            history();assertEquals(before,ids());assertTrue(ops.hasWriteWork());
            assertEquals(photo,h.f.prefs.getString(Final180PhotoRoi.PREFERENCE,""));
        }finally{ops.end(ticket,"CANCELLED",null,null);h.call("renderAnalysisControls");}
        assertFalse(ops.hasWriteWork());h.hardware.fresh();h.call("renderAnalysisControls");assertTrue(h.button().isEnabled());
    }
    @Test public void preparationButtonsOpenTheExistingSeparateEditorsWithoutSaving()throws Exception{
        h.hardware.fresh();h.call("renderAnalysisControls");
        Map<String,?> before=new HashMap<>(h.f.prefs.getAll());TvSetupController existing=h.f.tv;
        assertTrue(h.f.main.findViewById(R.id.tv_frame_edit).isEnabled());assertTrue(h.f.main.findViewById(R.id.f6_photo_roi_edit).isEnabled());
        h.f.main.findViewById(R.id.tv_frame_edit).performClick();assertEquals(TvSetupController.Mode.CALIBRATION,existing.mode());assertTrue(existing.editing());
        existing.close();ShadowLooper.shadowMainLooper().idle();h.hardware.fresh();h.call("renderAnalysisControls");
        h.f.main.findViewById(R.id.f6_photo_roi_edit).performClick();assertEquals(TvSetupController.Mode.F6_PHOTO,existing.mode());assertTrue(existing.editing());
        existing.close();ShadowLooper.shadowMainLooper().idle();assertSame(existing,h.f.tv);
        assertEquals(before.get(Final180PhotoRoi.PREFERENCE),h.f.prefs.getString(Final180PhotoRoi.PREFERENCE,""));
        assertEquals(before.get(Final180PhotoRoiApproval.PREFERENCE),h.f.prefs.getString(Final180PhotoRoiApproval.PREFERENCE,""));
        assertFalse(h.f.sessions.experiment.isRunning());assertNull(h.f.sessions.store());
        h.set("experimentModeEnabled",false);h.call("renderAnalysisControls");assertEquals(android.view.View.GONE,h.f.main.findViewById(R.id.main_calibration_actions).getVisibility());
    }
    @Test @Config(qualifiers="w360dp-h800dp-xhdpi") public void operationBannerDoesNotResizeCameraOrHideHistory()throws Exception{
        h.hardware.fresh();h.call("renderAnalysisControls");android.view.View root=h.f.main.findViewById(R.id.main);
        CameraWindowUi.install(h.f.main.getWindow(),root);
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root,new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.of(0,48,0,48))
                .setInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.of(0,48,0,48))
                .setInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.displayCutout(),androidx.core.graphics.Insets.of(0,48,0,0))
                .setInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.mandatorySystemGestures(),androidx.core.graphics.Insets.of(0,0,0,48)).build());
        assertEquals(48,root.getPaddingBottom());
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(720,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1600,android.view.View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);
        android.view.View preview=h.f.main.findViewById(R.id.camera_preview);
        android.graphics.Rect before=new android.graphics.Rect(preview.getLeft(),preview.getTop(),preview.getRight(),preview.getBottom());
        for(int id:new int[]{R.id.tv_frame_edit,R.id.f6_photo_roi_edit,R.id.f6_focus_toggle}){
            android.view.View control=h.f.main.findViewById(id);assertTrue(control.getMeasuredHeight()>=96);assertTrue(control.getMeasuredWidth()>=96);
        }
        F6ConfigurationUiTest.hostImage(root,"main-preparation-host.png");
        String ticket=h.f.sessions.operations.begin("SYNTHETIC-LAYOUT","COPYING_DOCUMENT","");
        try{
            h.f.sessions.operations.update(ticket,"COPYING_DOCUMENT",37,100,null);h.call("renderAnalysisControls");
            root.measure(android.view.View.MeasureSpec.makeMeasureSpec(720,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1600,android.view.View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);
            assertEquals(before,new android.graphics.Rect(preview.getLeft(),preview.getTop(),preview.getRight(),preview.getBottom()));
            android.view.View panel=h.f.main.findViewById(R.id.session_operation_panel),toolbar=h.f.main.findViewById(R.id.top_app_bar);
            android.graphics.Rect panelBounds=new android.graphics.Rect();panel.getDrawingRect(panelBounds);
            ((android.view.ViewGroup)root).offsetDescendantRectToMyCoords(panel,panelBounds);
            android.graphics.Rect headerBounds=new android.graphics.Rect();android.view.View header=h.f.main.findViewById(R.id.normal_camera_hud);header.getDrawingRect(headerBounds);
            ((android.view.ViewGroup)root).offsetDescendantRectToMyCoords(header,headerBounds);
            assertTrue(panelBounds.top>=toolbar.getBottom());assertTrue(headerBounds.contains(panelBounds));
            assertTrue(panelBounds.bottom<=preview.getTop());
            // This fixture inflates the real Activity content without attaching an Android Window.
            // Check its measured/clipped coordinates rather than a non-existent window's visibility.
            android.view.View history=h.f.main.findViewById(R.id.export_report_button);
            android.graphics.Rect visible=new android.graphics.Rect();history.getDrawingRect(visible);
            ((android.view.ViewGroup)root).offsetDescendantRectToMyCoords(history,visible);
            for(android.view.View v=history;;v=(android.view.View)v.getParent()){
                assertEquals(android.view.View.VISIBLE,v.getVisibility());if(v==root)break;
            }
            assertTrue(visible.width()>0&&visible.height()>0);
            assertTrue(new android.graphics.Rect(0,48,root.getWidth(),root.getHeight()-48).contains(visible));
            assertTrue(panelBounds.bottom<visible.top);
            F6ConfigurationUiTest.hostImage(root,"main-export-progress-host.png");
        }finally{h.f.sessions.operations.end(ticket,"CANCELLED",null,null);}
    }
    @Test public void actualStopKeepsHistoryAvailableWhileFinalizerIsQueuedAndNeverStartsAgain()throws Exception{
        ResearchSessionStore first=start();Set<String> expected=ids();
        ExecutorService finalizer=(ExecutorService)TvBboxStartUiTest.get(h.f.sessions,"finalizer");
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        finalizer.execute(()->{entered.countDown();try{release.await(30,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        try{
            h.button().performClick();h.drain();h.call("renderAnalysisControls");assertTrue(h.f.sessions.finalizing());
            assertFalse(h.button().isEnabled());assertFalse(h.f.intent().pending());
            assertTrue(h.f.main.findViewById(R.id.export_report_button).isEnabled());
            assertEquals(first.sessionId(),h.f.sessions.operations.presentation().sessionId);
            assertEquals("Domykanie zadań w locie",((android.widget.TextView)h.f.main.findViewById(R.id.session_operation_title)).getText().toString());
            h.call("requestExperimentStart");assertFalse(h.f.intent().pending());assertEquals(expected,ids());
        }finally{release.countDown();}
        drain(h.f.sessions,"finalizer");h.call("renderAnalysisControls");
        assertFalse(h.f.sessions.finalizing());assertEquals("Archiwum sesji gotowe",((android.widget.TextView)h.f.main.findViewById(R.id.session_operation_title)).getText().toString());
        assertEquals("PARTIAL",F6ProducerContractVerifier.validate(new File(first.directory(),"final/"+first.sessionId()+".alprsession")));
        history();assertEquals(expected,ids());assertFalse(h.f.sessions.experiment.isRunning());
    }
    @Test public void settingsCannotBeOpenedDirectlyDuringWriteAndDoesNotTouchPreferences()throws Exception{
        ResearchOperationState ops=h.f.sessions.operations;String ticket=ops.begin("SYNTHETIC-SETTINGS","VALIDATING_ARCHIVE","");
        android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences(SettingsActivity.PREFERENCES,0);
        Map<String,?> before=new HashMap<>(prefs.getAll());
        org.robolectric.android.controller.ActivityController<SettingsActivity> screen=null;
        try{
            screen=Robolectric.buildActivity(SettingsActivity.class).setup();assertTrue(screen.get().isFinishing());
            assertEquals(before,prefs.getAll());assertFalse(h.f.sessions.ready());assertTrue(ops.hasWriteWork());
        }finally{if(screen!=null)screen.pause().stop().destroy();ops.end(ticket,"CANCELLED",null,null);}
        assertEquals(before,prefs.getAll());assertTrue(h.f.sessions.ready());
    }
    @Test public void terminalReceiptsRetainTheirMeaningAndInventoryCannotReplaceThem()throws Exception{
        ResearchOperationState ops=h.f.sessions.operations;
        String archive=ops.begin("SYNTHETIC-ARCHIVE","BUILDING_ARCHIVE","");ops.end(archive,"SUCCEEDED",null,null);
        h.call("renderAnalysisControls");assertEquals("Archiwum sesji gotowe",((android.widget.TextView)h.f.main.findViewById(R.id.session_operation_title)).getText().toString());
        String inventory=ops.begin("","INVENTORY","");ops.end(inventory,"SUCCEEDED",null,null);h.call("renderAnalysisControls");
        assertEquals(archive,ops.presentation().id);
        String document=ops.begin("SYNTHETIC-DOCUMENT","VALIDATING_ARCHIVE","");ops.update(document,"VERIFYING_DOCUMENT",100,100,null);
        ops.end(document,"SAVED_UNVERIFIED","content://synthetic/without-readback",null);h.call("renderAnalysisControls");
        assertTrue(((android.widget.TextView)h.f.main.findViewById(R.id.session_operation_title)).getText().toString().contains("brak potwierdzenia"));
        String failed=ops.begin("SYNTHETIC-FAIL","BUILDING_ARCHIVE","");ops.end(failed,"FAILED",null,new java.io.IOException("SYNTHETIC_NO_SPACE"));
        String next=ops.begin("SYNTHETIC-NEXT","COPYING_DOCUMENT","");assertFalse(ops.end(failed,"SUCCEEDED",null,null));
        assertTrue(ops.hasWriteWork());assertEquals(next,ops.presentation().id);
        ops.update(next,"VERIFYING_DOCUMENT",100,100,null);ops.end(next,"SUCCEEDED","content://synthetic/verified",null);h.call("renderAnalysisControls");
        assertEquals("Eksport zapisany i sprawdzony",((android.widget.TextView)h.f.main.findViewById(R.id.session_operation_title)).getText().toString());
        assertTrue(ops.historyDescription().contains("SYNTHETIC_NO_SPACE"));assertFalse(ops.hasWriteWork());
    }
    @Test public void stoppedOrRestartedOperationNeverClaimsVerifiedExport()throws Exception{
        File dead=h.hardware.f.temp.newFolder();ResearchOperationState original=new ResearchOperationState(h.hardware.f.temp.newFolder(),s->{});
        String id=original.begin("SYNTHETIC-DEAD","COPYING_DOCUMENT","content://synthetic/interrupted");
        try{
            original.update(id,"COPYING_DOCUMENT",37,100,null);
            File opRoot=(File)TvBboxStartUiTest.get(original,"directory");for(File f:opRoot.listFiles())Files.copy(f.toPath(),new File(dead,f.getName()).toPath());
            ResearchOperationState restored=new ResearchOperationState(dead,s->{});restored.restore();
            assertFalse(restored.hasWriteWork());assertEquals("INTERRUPTED",restored.presentation().status);
            ResearchOperationPresentation state=ResearchOperationPresentation.from(restored.presentation());
            assertFalse(state.active);assertFalse(state.determinate);assertEquals("Operacja została przerwana",state.title);
            assertTrue(restored.historyDescription().contains("PROCESS_ENDED_DURING_COPYING_DOCUMENT"));
        }finally{original.end(id,"CANCELLED",null,null);}
    }
}
