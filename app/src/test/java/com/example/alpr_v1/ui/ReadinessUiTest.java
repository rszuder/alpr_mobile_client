package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.core.app.ActivityOptionsCompat;
import com.example.alpr_v1.*;
import com.example.alpr_v1.camera.CameraSetupControl;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.ui.readiness.*;
import com.example.alpr_v1.vision.Point2;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

/** Real Settings XML, dialogs and TV editor. Camera frames/model bytes and Activity launches are host fixtures. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-xhdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class ReadinessUiTest {
    ActivityController<SettingsActivity> controller;
    TvSetupUiTest tvFixture;
    TvSetupUiTest.HarnessActivity tvActivity;
    TvBboxStartUiTest mainFixture;
    SharedPreferences prefs;
    final List<Intent> returns=new ArrayList<>();

    @Before public void preferences(){
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CAMERA);
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences(SettingsActivity.PREFERENCES,0);
        prefs.edit().clear().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true)
                .putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget").putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,"r0_full_frame")
                .putString(SettingsActivity.KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME").putString(SettingsActivity.KEY_EXPERIMENT_SERIES_ID,"TEST_ONLY_READINESS")
                .putString(SettingsActivity.KEY_EXPERIMENT_SCENARIO_ID,"HOST").putString(SettingsActivity.KEY_EXPERIMENT_VARIANT,"HOST")
                .putInt(SettingsActivity.KEY_EXPERIMENT_REPLICATE_INDEX,1).commit();
    }
    @After public void close()throws Exception{
        if(controller!=null)controller.pause().stop().destroy();
        if(tvFixture!=null)tvFixture.destroy();
        if(mainFixture!=null){mainFixture.close();mainFixture.temp.delete();}
    }
    SettingsActivity open(){return open(new Intent(RuntimeEnvironment.getApplication(),SettingsActivity.class));}
    SettingsActivity open(Intent intent){controller=Robolectric.buildActivity(SettingsActivity.class,intent).setup().visible();await(controller.get());return controller.get();}
    static void await(SettingsActivity activity){
        try{
            ReadinessController owner=ReflectionHelpers.getField(activity,"readiness");
            for(int i=0;i<3;i++){
                ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(160));
                ExecutorService worker=ReflectionHelpers.getField(owner,"worker");worker.submit(()->{}).get(15,TimeUnit.SECONDS);
                ShadowLooper.shadowMainLooper().idle();F6ConfigurationUiTest.layout(activity);
            }
        }catch(Exception error){throw new AssertionError(error);}
    }
    ReadinessResult result(SettingsActivity a){return ((ReadinessController)ReflectionHelpers.getField(a,"readiness")).current();}
    void action(SettingsActivity a,String issue){
        View all=a.findViewById(R.id.readiness_show_all);
        View button=a.findViewById(R.id.readiness_findings).findViewWithTag("action:"+issue);
        if(button==null){all.performClick();button=a.findViewById(R.id.readiness_findings).findViewWithTag("action:"+issue);}
        assertNotNull("Action missing for "+issue,button);button.performClick();F6ConfigurationUiTest.settle(a);
    }
    @Test public void summaryShowsThreeFindingsAndFullListWithoutChangingPreferences()throws Exception{
        SettingsActivity a=open();Map<String,?> before=new HashMap<>(prefs.getAll());
        assertEquals("Wymaga ustawienia",((TextView)a.findViewById(R.id.readiness_state)).getText().toString());
        LinearLayout rows=a.findViewById(R.id.readiness_findings);assertEquals(3,rows.getChildCount());
        a.findViewById(R.id.readiness_show_all).performClick();assertTrue(rows.getChildCount()>3);
        a.findViewById(R.id.readiness_show_all).performClick();assertEquals(3,rows.getChildCount());
        for(int header:F6ConfigurationUiTest.HEADERS){a.findViewById(header).performClick();F6ConfigurationUiTest.settle(a);}
        assertEquals(before,prefs.getAll());assertEquals(0,sessionDirectories());
    }
    @Test public void unsavedDraftIsIncludedAndUsesTheSameValuesOnReturnToMain()throws Exception{
        ReadinessTestModels.install(RuntimeEnvironment.getApplication(),false,true);SettingsActivity a=open();
        int revision=prefs.getInt(SettingsActivity.KEY_REVISION,0);
        ((TextView)a.findViewById(R.id.settings_experiment_series_id)).setText("  UNSAVED SERIES  ");
        ((TextView)a.findViewById(R.id.settings_experiment_replicate_index)).setText("999999999999999999");await(a);
        ReadinessSnapshot snapshot=ReflectionHelpers.callInstanceMethod(a,"effectiveReadiness",ReflectionHelpers.ClassParameter.from(long.class,2L));
        assertEquals("UNSAVED SERIES",snapshot.text(SettingsActivity.KEY_EXPERIMENT_SERIES_ID,""));
        assertEquals(1,snapshot.values.get(SettingsActivity.KEY_EXPERIMENT_REPLICATE_INDEX));
        assertEquals("TEST_ONLY_READINESS",prefs.getString(SettingsActivity.KEY_EXPERIMENT_SERIES_ID,""));assertEquals(revision,prefs.getInt(SettingsActivity.KEY_REVISION,0));
        assertEquals(ReadinessResult.State.DEFERRED,result(a).state);
        controller.pause();assertEquals(snapshot.text(SettingsActivity.KEY_EXPERIMENT_SERIES_ID,""),prefs.getString(SettingsActivity.KEY_EXPERIMENT_SERIES_ID,""));
        controller.resume();await(a);assertEquals(ReadinessResult.State.DEFERRED,result(a).state);
    }
    @Test public void knownProblemLeadsToExactSwitchAndReturnRemovesOnlyFixedIssue()throws Exception{
        ReadinessTestModels.install(RuntimeEnvironment.getApplication(),false,true);
        prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP").putBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,true).commit();
        SettingsActivity a=open();capture(a.findViewById(R.id.settings_root),"field-01-summary.png");action(a,"tv_fixed_zoom");
        View target=a.findViewById(R.id.settings_research_autozoom_switch);android.graphics.Rect visible=new android.graphics.Rect();
        assertTrue(target.isShown());assertTrue(target.getGlobalVisibleRect(visible));assertEquals(target.getHeight(),visible.height());
        assertEquals(4,F6ConfigurationUiTest.sections(a).expandedSection());capture(a.findViewById(R.id.settings_root),"field-02-exact-control.png");
        ((MaterialSwitch)target).setChecked(false);await(a);assertNull(result(a).finding("tv_fixed_zoom"));assertNotNull(result(a).finding("tv_approval"));
        ((MaterialToolbar)a.findViewById(R.id.settings_toolbar)).getMenu().performIdentifierAction(R.id.readiness_back_summary,0);
        F6ConfigurationUiTest.settle(a);await(a);assertEquals(-1,F6ConfigurationUiTest.sections(a).expandedSection());
        assertEquals(0,((androidx.core.widget.NestedScrollView)a.findViewById(R.id.settings_scroll)).getScrollY());
        capture(a.findViewById(R.id.settings_root),"field-03-summary-after-correction.png");
    }
    @Test public void missingPinOpensActualVariantDialogAndCancelNeverPretendsToFixIt()throws Exception{
        com.example.alpr_v1.model.ModelRegistry registry=ReadinessTestModels.install(RuntimeEnvironment.getApplication(),false,true);
        new com.example.alpr_v1.autotune.AutoTuneManager(RuntimeEnvironment.getApplication()).clearPinnedVariant(registry.getActive(com.example.alpr_v1.model.ModelRole.PLATE));
        SettingsActivity a=open();action(a,"model_plate_variant");androidx.appcompat.app.AlertDialog dialog=(androidx.appcompat.app.AlertDialog)ShadowDialog.getLatestDialog();
        assertTrue(dialog.isShowing());dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();await(a);
        assertNotNull(result(a).finding("model_plate_variant"));assertEquals(-1,F6ConfigurationUiTest.sections(a).expandedSection());
        action(a,"model_plate_variant");dialog=(androidx.appcompat.app.AlertDialog)ShadowDialog.getLatestDialog();
        dialog.getListView().performItemClick(dialog.getListView().getChildAt(1),1,1);
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();await(a);
        assertNull(result(a).finding("model_plate_variant"));assertEquals(-1,F6ConfigurationUiTest.sections(a).expandedSection());
        assertNotNull(result(a).finding("session_preparation"));assertEquals(0,sessionDirectories());
    }
    @Test public void calibrationSaveReturnsThroughMainToFreshSummary()throws Exception{
        photoJourney(true,false);
    }
    @Test public void genericCameraToolsUseTheSameReturnContractWithoutCreatingAnIssue()throws Exception{
        ReadinessTestModels.install(RuntimeEnvironment.getApplication(),false,true);
        SettingsActivity a=open(new Intent(RuntimeEnvironment.getApplication(),SettingsActivity.class).putExtra(SettingsActivity.EXTRA_RETURN_TO_SUMMARY,true));
        assertNull(result(a).finding("photo_area"));
        F6ConfigurationUiTest.select(a,1);Map<String,?> before=new HashMap<>(prefs.getAll());
        a.findViewById(R.id.settings_tv_calibrate).performClick();
        Intent route=Shadows.shadowOf(a).getResultIntent();assertTrue(a.isFinishing());assertEquals("CALIBRATOR",route.getStringExtra(ReadinessReturnContext.EXTRA_ACTION));
        assertEquals("CALIBRATION",route.getStringExtra(ReadinessReturnContext.EXTRA_MODE));assertNotNull(route.getBundleExtra(ReadinessReturnContext.EXTRA));
        assertEquals(before,prefs.getAll());assertEquals(0,sessionDirectories());
    }
    @Test public void calibrationCancelReturnsAndKeepsTheMissingArea()throws Exception{
        photoJourney(false,false);
    }
    @Test public void calibrationBackgroundPreservesManualReturnWithoutOpeningOptions()throws Exception{
        photoJourney(false,true);
    }
    void photoJourney(boolean save,boolean background)throws Exception{
        prepareTv();ReadinessTestModels.install(RuntimeEnvironment.getApplication(),false,true);
        prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true).putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget")
                .putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,"r0_full_frame").putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP")
                .putBoolean(Final180PhotoRoi.ENABLED,true).commit();
        SettingsActivity a=open();assertEquals(ReadinessTarget.Action.PHOTO_AREA,result(a).finding("photo_area").target.action);
        if(save)capture(a.findViewById(R.id.settings_root),"photo-01-summary-missing-area.png");
        Map<String,?> values=new HashMap<>(prefs.getAll());action(a,"photo_area");
        ShadowActivity shadow=Shadows.shadowOf(a);Intent route=shadow.getResultIntent();assertTrue(a.isFinishing());
        assertNotNull(route.getBundleExtra(ReadinessReturnContext.EXTRA));assertEquals("F6_PHOTO",route.getStringExtra(ReadinessReturnContext.EXTRA_MODE));
        assertEquals("Navigation itself must not write configuration",values,prefs.getAll());
        controller.pause().stop().destroy();controller=null;
        prepareMain();
        ReflectionHelpers.setField(mainFixture.main,"tvSetup",tvActivity.setup);
        ReflectionHelpers.callInstanceMethod(mainFixture.main,"installReadinessEditorReturn");
        ReflectionHelpers.callInstanceMethod(mainFixture.main,"receiveReadinessSettingsResult",ReflectionHelpers.ClassParameter.from(Intent.class,route));
        // This detached Main fixture does not dispatch decorView.post; deliver the real lifecycle continuation.
        ReflectionHelpers.callInstanceMethod(mainFixture.main,"continueReadinessNavigation");
        ShadowLooper.shadowMainLooper().idle();tvCall("settle");assertEquals(TvSetupController.Mode.F6_PHOTO,tvActivity.setup.mode());assertTrue(tvActivity.setup.editing());
        if(save){
            JSONObject approved=new JSONObject(prefs.getString("tv_setup_calibration_v1",""));JSONArray corners=approved.getJSONArray("corners_norm");
            List<Point2> inner=new ArrayList<>();double cx=0,cy=0;
            for(int i=0;i<4;i++){cx+=corners.getJSONObject(i).getDouble("x")/4;cy+=corners.getJSONObject(i).getDouble("y")/4;}
            for(int i=0;i<4;i++)inner.add(new Point2((float)(cx+(corners.getJSONObject(i).getDouble("x")-cx)*.9),
                    (float)(cy+(corners.getJSONObject(i).getDouble("y")-cy)*.9)));
            CameraPreviewGeometry geometry=ReflectionHelpers.getField(tvFixture,"geometry");
            assertNull(TvSetupCalibration.validation(inner,geometry,tvActivity.getResources().getDisplayMetrics().density));
            tvActivity.overlay.setCorners(inner);tvCall("referenceFrame");
            capture(tvActivity.getWindow().getDecorView(),"photo-02-existing-calibrator.png");
            assertTrue(tvActivity.findViewById(R.id.tv_setup_confirm).isEnabled());tvActivity.findViewById(R.id.tv_setup_confirm).performClick();
        }else if(background)tvActivity.setup.onBackground();else tvActivity.setup.close();
        tvCall("settle");ShadowLooper.shadowMainLooper().idle();
        ReflectionHelpers.callInstanceMethod(mainFixture.main,"continueReadinessNavigation");
        if(background){
            assertEquals(0,returns.size());assertNotNull(ReflectionHelpers.getField(mainFixture.main,"readinessReturn"));
            ReflectionHelpers.callInstanceMethod(mainFixture.main,"continueReadinessNavigation");assertEquals(0,returns.size());
            ReflectionHelpers.setField(mainFixture.main,"readinessReturnManual",false);
            ReflectionHelpers.callInstanceMethod(mainFixture.main,"continueReadinessNavigation");
        }
        assertEquals(1,returns.size());assertFalse(tvActivity.setup.editing());
        a=open(returns.get(0));assertEquals(-1,F6ConfigurationUiTest.sections(a).expandedSection());
        if(save){assertNull(result(a).finding("photo_area"));assertEquals(ReadinessResult.State.DEFERRED,result(a).state);
            capture(a.findViewById(R.id.settings_root),"photo-03-summary-after-save.png");}
        else assertNotNull(result(a).finding("photo_area"));
        assertEquals(0,sessionDirectories());
        ReflectionHelpers.callInstanceMethod(mainFixture.main,"continueReadinessNavigation");assertEquals(1,returns.size());
    }
    void prepareTv()throws Exception{
        android.hardware.camera2.CameraCharacteristics characteristics=ShadowCameraCharacteristics.newCameraCharacteristics();
        Shadows.shadowOf(characteristics).set(android.hardware.camera2.CameraCharacteristics.LENS_FACING,android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK);
        Shadows.shadowOf((android.hardware.camera2.CameraManager)RuntimeEnvironment.getApplication().getSystemService(android.content.Context.CAMERA_SERVICE))
                .addCamera("synthetic-camera",characteristics);
        tvFixture=new TvSetupUiTest();ReflectionHelpers.setField(tvFixture,"portraitSource",true);
        ReflectionHelpers.setField(tvFixture,"width",720);ReflectionHelpers.setField(tvFixture,"height",1600);ReflectionHelpers.setField(tvFixture,"densityDpi",320);
        tvCall("create");tvCall("installCameraControls");Object fake=ReflectionHelpers.getField(tvFixture,"fakeCamera");
        ReflectionHelpers.setField(fake,"publishReadiness",true);Consumer<CameraSetupControl.State> listener=ReflectionHelpers.getField(fake,"listener");
        listener.accept(ReflectionHelpers.callInstanceMethod(fake,"state"));tvCall("open");tvCall("fourCorners");
        tvActivity=ReflectionHelpers.getField(tvFixture,"activity");tvActivity.findViewById(R.id.tv_setup_confirm).performClick();tvCall("settle");
        prefs=tvActivity.getSharedPreferences(SettingsActivity.PREFERENCES,0);assertFalse(prefs.getString("tv_setup_calibration_v1","").isEmpty());
    }
    void tvCall(String method){ReflectionHelpers.callInstanceMethod(tvFixture,method);}
    void prepareMain()throws Exception{
        mainFixture=new TvBboxStartUiTest();mainFixture.temp.create();mainFixture.setup();
        ReflectionHelpers.setField(mainFixture.main,"settingsScreen",new ActivityResultLauncher<Intent>(){
            public void launch(Intent input,ActivityOptionsCompat options){returns.add(input);}
            public void unregister(){} public ActivityResultContract<Intent,?> getContract(){return new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult();}
        });
    }
    @Test public void writeLockKeepsReturnContextAndUnlockDoesNotReopenOptionsAutomatically()throws Exception{
        prepareMain();ResearchOperationState operations=mainFixture.sessions.operations;
        String id=operations.begin("TEST_ONLY_READINESS","BUILDING_ARCHIVE","synthetic operation, no experiment");
        try{
            controller=Robolectric.buildActivity(SettingsActivity.class).setup();SettingsActivity a=controller.get();
            assertTrue(a.isFinishing());Intent blocked=Shadows.shadowOf(a).getResultIntent();assertNotNull(blocked);
            assertEquals("BLOCKED",blocked.getStringExtra(ReadinessReturnContext.EXTRA_ACTION));
            ReflectionHelpers.callInstanceMethod(mainFixture.main,"receiveReadinessSettingsResult",ReflectionHelpers.ClassParameter.from(Intent.class,blocked));
            ReflectionHelpers.callInstanceMethod(mainFixture.main,"continueReadinessNavigation");assertTrue(returns.isEmpty());
            assertNotNull(ReflectionHelpers.getField(mainFixture.main,"readinessReturn"));
            operations.end(id,"COMPLETED","host operation ended",null);
            ReflectionHelpers.callInstanceMethod(mainFixture.main,"continueReadinessNavigation");assertTrue(returns.isEmpty());
            ReflectionHelpers.setField(mainFixture.main,"readinessReturnManual",false);
            ReflectionHelpers.callInstanceMethod(mainFixture.main,"continueReadinessNavigation");assertEquals(1,returns.size());
        }finally{operations.end(id,"COMPLETED","fixture cleanup",null);}
    }
    @Test public void mainSummaryPreservesRunningAndWritePresentationAndFitsLargeText()throws Exception{
        RuntimeEnvironment.setFontScale(1.5f);prepareMain();MainActivity main=mainFixture.main;
        com.example.alpr_v1.model.ModelRegistry models=ReflectionHelpers.getField(main,"modelRegistry");
        ReadinessSnapshot snapshot=ReadinessSnapshot.capture(mainFixture.prefs,models,new com.example.alpr_v1.autotune.AutoTuneManager(main),null,
                ResearchSessionStore.sessionsRoot(main),ReadinessSnapshot.Device.options(true,false),1);
        ReadinessController readiness=new ReadinessController(ticket->snapshot,result->{});
        ReflectionHelpers.setField(main,"configurationReadiness",readiness);ReflectionHelpers.setField(main,"readinessSnapshot",snapshot);
        ReflectionHelpers.setField(main,"readinessConfiguration",new ReadinessEvaluator().evaluate(snapshot));
        try{
            ReflectionHelpers.callInstanceMethod(main,"renderConfigurationReadiness");
            TextView status=main.findViewById(R.id.tv_frame_status);assertTrue(status.getText().toString().contains("Wymaga ustawienia"));
            View root=main.findViewById(R.id.main);root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);
            assertTrue(status.getLayout().getHeight()<=status.getHeight());capture(root,"main-readiness-large-text.png");
            ReflectionHelpers.setField(main,"cameraStarted",true);mainFixture.tv.setResearchSummary("Trwa analiza");
            ReflectionHelpers.callInstanceMethod(main,"renderConfigurationReadiness");assertTrue(status.getText().toString().contains("Trwa analiza"));
            ReflectionHelpers.setField(main,"cameraStarted",false);String id=mainFixture.sessions.operations.begin("TEST_ONLY","BUILDING_ARCHIVE","fixture");
            try{mainFixture.tv.setResearchSummary("Trwa zapis");ReflectionHelpers.callInstanceMethod(main,"renderConfigurationReadiness");assertTrue(status.getText().toString().contains("Trwa zapis"));}
            finally{mainFixture.sessions.operations.end(id,"COMPLETED","fixture ended",null);}
        }finally{readiness.close();ReflectionHelpers.setField(main,"configurationReadiness",null);ReflectionHelpers.setField(main,"cameraStarted",false);}
    }
    @Test public void rapidChangesDiscardOlderChecksAndRecreationKeepsAWorkingSummaryReturn()throws Exception{
        ReadinessTestModels.install(RuntimeEnvironment.getApplication(),false,true);SettingsActivity a=open();
        ReadinessController readiness=ReflectionHelpers.getField(a,"readiness");ExecutorService worker=ReflectionHelpers.getField(readiness,"worker");
        CountDownLatch release=new CountDownLatch(1);worker.submit(()->{try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        prefs.edit().putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"auto").commit();ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(150));
        assertEquals("Sprawdzanie ustawień…",((TextView)a.findViewById(R.id.readiness_state)).getText().toString());
        prefs.edit().putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"cpu_2").commit();release.countDown();await(a);
        assertNull(result(a).finding("model_plate_execution"));
        prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP").putBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,true).commit();await(a);
        action(a,"tv_fixed_zoom");Bundle saved=new Bundle();controller.saveInstanceState(saved).pause().stop().destroy();
        controller=Robolectric.buildActivity(SettingsActivity.class).setup(saved).visible();a=controller.get();await(a);
        assertEquals(4,F6ConfigurationUiTest.sections(a).expandedSection());
        a.getOnBackPressedDispatcher().onBackPressed();F6ConfigurationUiTest.settle(a);assertFalse(a.isFinishing());assertEquals(-1,F6ConfigurationUiTest.sections(a).expandedSection());
    }
    @Test public void largeTextKeepsTheIssueActionAndItsExactDestinationReachable()throws Exception{
        RuntimeEnvironment.setFontScale(1.5f);ReadinessTestModels.install(RuntimeEnvironment.getApplication(),false,true);
        prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP").putBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,true).commit();
        SettingsActivity a=open();action(a,"tv_fixed_zoom");View target=a.findViewById(R.id.settings_research_autozoom_switch);
        android.graphics.Rect rect=new android.graphics.Rect();assertTrue(target.getGlobalVisibleRect(rect));assertEquals(target.getHeight(),rect.height());
        capture(a.findViewById(R.id.settings_root),"large-text-exact-control.png");
    }
    int sessionDirectories(){File[] dirs=ResearchSessionStore.sessionsRoot(RuntimeEnvironment.getApplication()).listFiles(File::isDirectory);return dirs==null?0:dirs.length;}
    static void capture(View root,String name)throws Exception{
        String dir=System.getenv("READINESS_UI_ARTIFACTS");if(dir==null)return;
        File file=new File(dir,name);file.getParentFile().mkdirs();
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bitmap));try(FileOutputStream stream=new FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
    }
}
