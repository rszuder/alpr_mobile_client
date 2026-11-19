package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.DialogInterface;
import android.graphics.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.example.alpr_v1.*;
import com.example.alpr_v1.autotune.*;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.ui.readiness.*;
import com.google.android.material.card.MaterialCardView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-xhdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class NormalCpuReadinessUiTest {
    ReadinessUiTest h;ModelRegistry models;
    @Before public void setup()throws Exception {
        h=new ReadinessUiTest();h.preferences();models=ReadinessTestModels.install(RuntimeEnvironment.getApplication(),true,true);
        RuntimeEnvironment.getApplication().getSharedPreferences(NormalExecutionSettings.PREFERENCES,0).edit().clear().commit();
        h.prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false).putBoolean("vehicle_cascade_enabled",true).commit();
    }
    @After public void close()throws Exception {h.close();RuntimeEnvironment.setFontScale(1f);}
    AlertDialog dialog(SettingsActivity a,int node){a.findViewById(node).performClick();return (AlertDialog)ShadowDialog.getLatestDialog();}
    Spinner cpu(AlertDialog d){
        Spinner value=d.getWindow().getDecorView().findViewWithTag("normal_cpu_threads");
        if(value==null){
            d.getListView().setSelection(d.getListView().getCount()-1);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            View root=d.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.AT_MOST));
            root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());
            value=root.findViewWithTag("normal_cpu_threads");
        }
        assertNotNull("CPU footer reachable by scrolling the variant list",value);return value;
    }
    @Test public void validConfigurationExplainsDeferredWorkWithoutCountersOrBrightCards()throws Exception {
        SettingsActivity a=h.open();String heading=((TextView)a.findViewById(R.id.readiness_state)).getText().toString();
        String explanation=((TextView)a.findViewById(R.id.readiness_counts)).getText().toString();
        assertEquals("Ustawienia są poprawne.",heading);assertTrue(explanation.contains("po włączeniu podglądu"));assertTrue(explanation.contains("uruchamiania analizy"));
        assertFalse(explanation.contains("Do potwierdzenia"));
        for(int id:new int[]{R.id.options_camera_card,R.id.options_models_card,R.id.options_session_card}) {
            MaterialCardView card=a.findViewById(id);assertEquals(a.getColor(R.color.options_outline),card.getStrokeColor());
            assertEquals(Math.round(a.getResources().getDisplayMetrics().density),card.getStrokeWidth());
        }
        for(int id:new int[]{R.id.options_camera_header,R.id.options_models_header}){
            String text=((TextView)a.findViewById(id)).getText().toString();assertFalse(text.contains("Po START:"));assertFalse(text.contains("Przy uruchamianiu:"));
        }
        capture(a,"readiness-normal.png");
    }
    @Test public void cpuEditNeedsConfirmationAndDoesNotChangeResearchRevision()throws Exception {
        SettingsActivity a=h.open();NormalExecutionSettings normal=new NormalExecutionSettings(a);
        Map<String,?> before=new HashMap<>(h.prefs.getAll());InstalledModel mt=models.getActive(ModelRole.PLATE);
        AlertDialog d=dialog(a,R.id.settings_node_plate);int n=Math.min(3,NormalExecutionSettings.maximumCpuThreads());cpu(d).setSelection(n);
        d.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();assertEquals(0,normal.requestedThreads(mt));assertEquals(before,h.prefs.getAll());
        d=dialog(a,R.id.settings_node_plate);cpu(d).setSelection(n);captureDialog(d,"cpu-mt-dialog.png");d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();ReadinessUiTest.await(a);
        assertEquals(n,normal.requestedThreads(mt));assertEquals(before,h.prefs.getAll());
        assertTrue(((TextView)a.findViewById(R.id.settings_node_plate)).getText().toString().contains("CPU: "+n));
        h.controller.recreate();a=h.controller.get();ReadinessUiTest.await(a);d=dialog(a,R.id.settings_node_plate);assertEquals(n,cpu(d).getSelectedItemPosition());
        cpu(d).setSelection(0);org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals("Auto selected in actual Spinner",0,cpu(d).getSelectedItemPosition());
        assertTrue("Apply enabled",d.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled());
        d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();ReadinessUiTest.await(a);
        assertEquals("Auto persisted after recreation",0,new NormalExecutionSettings(a).requestedThreads(mt));
    }
    @Test public void eachActualModelDialogEditsOnlyItsOwnOrdinaryProfile()throws Exception {
        SettingsActivity a=h.open();NormalExecutionSettings normal=new NormalExecutionSettings(a);
        int[] nodes={R.id.settings_node_vehicle,R.id.settings_node_plate,R.id.settings_node_character};ModelRole[] roles={ModelRole.VEHICLE,ModelRole.PLATE,ModelRole.CHARACTER};
        for(int i=0;i<nodes.length;i++){
            AlertDialog d=dialog(a,nodes[i]);cpu(d).setSelection(Math.min(i+1,NormalExecutionSettings.maximumCpuThreads()));d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();ReadinessUiTest.await(a);
            for(int j=0;j<roles.length;j++)assertEquals(j<=i?Math.min(j+1,NormalExecutionSettings.maximumCpuThreads()):0,normal.requestedThreads(models.getActive(roles[j])));
        }
    }
    @Test public void researchDialogCannotSaveOrdinaryCpuAndR0ShowsMpDisabled()throws Exception {
        h.prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true).commit();SettingsActivity a=h.open();
        Map<String,?> before=new HashMap<>(a.getSharedPreferences(NormalExecutionSettings.PREFERENCES,0).getAll());
        AlertDialog d=dialog(a,R.id.settings_node_plate);assertFalse(cpu(d).isShown());d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        assertEquals(before,a.getSharedPreferences(NormalExecutionSettings.PREFERENCES,0).getAll());
        assertTrue(((TextView)a.findViewById(R.id.settings_node_vehicle)).getText().toString().contains("Wyłączony"));
    }
    @Test public void openingOlderOptionsDoesNotMigrateOrWriteAnyPreference()throws Exception {
        h.prefs.edit().remove(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY).putString("roi_budget_policy","r0_full_frame").commit();
        Map<String,?> before=new HashMap<>(h.prefs.getAll());SettingsActivity a=h.open();
        assertEquals(before,h.prefs.getAll());
        assertEquals("r0_full_frame",ReflectionHelpers.<ReadinessSnapshot>callInstanceMethod(a,"effectiveReadiness",ReflectionHelpers.ClassParameter.from(long.class,2L)).text(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,""));
    }
    @Test public void modelCardsAndSelectorRemainReadableAtNormalAndLargeFonts()throws Exception {
        for(float font:new float[]{1f,1.5f}){
            RuntimeEnvironment.setFontScale(font);if(h.controller!=null){h.controller.recreate();ReadinessUiTest.await(h.controller.get());}else h.open();
            SettingsActivity a=h.controller.get();if(F6ConfigurationUiTest.sections(a).expandedSection()!=2)F6ConfigurationUiTest.select(a,2);F6ConfigurationUiTest.layout(a);
            for(int id:new int[]{R.id.settings_node_vehicle,R.id.settings_node_plate,R.id.settings_node_character}){
                TextView tile=a.findViewById(id);assertNotNull(tile.getLayout());for(int line=0;line<tile.getLineCount();line++)assertEquals(0,tile.getLayout().getEllipsisCount(line));
                assertTrue(tile.getText().toString()+" font="+font+" layout="+tile.getLayout().getHeight()+" height="+tile.getHeight(),tile.getLayout().getHeight()<=tile.getHeight()-tile.getCompoundPaddingTop()-tile.getCompoundPaddingBottom());
            }
            capture(a,"models-font-"+font+".png");AlertDialog d=dialog(a,R.id.settings_node_plate);cpu(d);
            captureDialog(d,"dialog-font-"+font+".png");
            for(int action:new int[]{DialogInterface.BUTTON_POSITIVE,DialogInterface.BUTTON_NEGATIVE}){
                View button=d.getButton(action);Rect visible=new Rect();assertTrue(button.getGlobalVisibleRect(visible));
                assertEquals("Action must be fully visible at font "+font,button.getHeight(),visible.height());
            }
            d.dismiss();
        }
    }
    @Test public void normalCpuEditsLeaveActualResearchConfigurationSnapshotIdentical()throws Exception {
        h.prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true).commit();SettingsActivity a=h.open();
        org.json.JSONObject before=ResearchConfigurationText.planned(h.prefs,models,new AutoTuneManager(a));
        NormalExecutionSettings normal=new NormalExecutionSettings(a);for(ModelRole role:ModelRole.values())normal.save(models.getActive(role),1);
        org.json.JSONObject after=ResearchConfigurationText.planned(h.prefs,models,new AutoTuneManager(a));assertEquals(before.toString(),after.toString());
    }
    @Test public void openingOptionsNeverProbesAnUnobservedNcnnLibrary()throws Exception {
        InstalledModel mt=models.getActive(ModelRole.PLATE);org.json.JSONObject manifest=new org.json.JSONObject(mt.manifest().rawJson());
        manifest.getJSONArray("variants").getJSONObject(0).put("runtime","ncnn");
        java.nio.file.Files.write(mt.resolve("manifest.json").toPath(),manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ReflectionHelpers.setStaticField(com.example.alpr_v1.inference.RuntimeBackendFactory.class,"observedNcnn",null);
        Map<String,?> before=new HashMap<>(h.prefs.getAll());SettingsActivity a=h.open();
        assertNull(com.example.alpr_v1.inference.RuntimeBackendFactory.observedAvailability(ModelRuntime.NCNN));
        assertEquals(before,h.prefs.getAll());assertEquals(0,h.sessionDirectories());
        String tile=((TextView)a.findViewById(R.id.settings_node_plate)).getText().toString();
        assertTrue(tile,tile.contains("NCNN"));assertTrue(tile,tile.contains("do ustalenia"));
        ReadinessFinding pending=h.result(a).finding("model_plate_selection");
        assertNotNull(pending);assertEquals(ReadinessFinding.Status.NOT_CHECKABLE_YET,pending.status);
        assertFalse(pending.blocksByExistingRule);assertNull(pending.target);
    }
    @Test public void explicitCpuAndVariantConfirmationAlsoDefersNcnnLibraryOpening()throws Exception {
        InstalledModel mt=models.getActive(ModelRole.PLATE);org.json.JSONObject manifest=new org.json.JSONObject(mt.manifest().rawJson());
        manifest.getJSONArray("variants").getJSONObject(0).put("runtime","ncnn");
        java.nio.file.Files.write(mt.resolve("manifest.json").toPath(),manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        new AutoTuneManager(RuntimeEnvironment.getApplication()).clearPinnedVariant(mt);
        ReflectionHelpers.setStaticField(com.example.alpr_v1.inference.RuntimeBackendFactory.class,"observedNcnn",null);
        SettingsActivity a=h.open();AlertDialog d=dialog(a,R.id.settings_node_plate);
        d.getListView().performItemClick(d.getListView().getChildAt(1),1,1);cpu(d).setSelection(1);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();ReadinessUiTest.await(a);
        assertNull(com.example.alpr_v1.inference.RuntimeBackendFactory.observedAvailability(ModelRuntime.NCNN));
        assertEquals(1,new NormalExecutionSettings(a).requestedThreads(mt));
        assertEquals("selected",new AutoTuneManager(a).storedVariantId(mt));assertEquals(0,h.sessionDirectories());
    }
    @Test public void r0WithoutAnActiveMpShowsDisabledInsteadOfAMissingModel()throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("model_registry",0).edit().remove("active.vehicle").commit();
        h.prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true).putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,"r0_full_frame").commit();
        SettingsActivity a=h.open();assertNull(h.result(a).finding("model_vehicle"));
        assertTrue(((TextView)a.findViewById(R.id.settings_node_vehicle)).getText().toString().contains("Wyłączony"));
        assertEquals(a.getString(R.string.settings_node_badge_disabled),((TextView)a.findViewById(R.id.settings_node_vehicle_badge)).getText().toString());
    }
    static void capture(SettingsActivity a,String name)throws Exception {captureView(a.getWindow().getDecorView(),name);}
    static void captureDialog(AlertDialog d,String name)throws Exception {org.robolectric.shadows.ShadowLooper.idleMainLooper();captureView(d.getWindow().getDecorView(),name);}
    static void captureView(View root,String name)throws Exception {
        String folder=System.getenv("NORMAL_CPU_UI_ARTIFACTS");if(folder==null)return;File dir=new File(folder);dir.mkdirs();
        if(root.getWidth()==0||root.getHeight()==0){root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.AT_MOST));root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());}
        Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));
        try(FileOutputStream stream=new FileOutputStream(new File(dir,name))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{b.recycle();}
    }
}
