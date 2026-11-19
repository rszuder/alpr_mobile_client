package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.example.alpr_v1.*;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.model.*;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=F5a24CameraStartTest.ModelIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class F5a25SettingsCopyTest {
    F5a24CameraStartTest t;SharedPreferences prefs;
    final List<ActivityController<SettingsActivity>> windows=new ArrayList<>();
    @Before public void setup()throws Exception{
        t=new F5a24CameraStartTest();t.setup();
        ModelRegistry fixtures=(ModelRegistry)TvBboxStartUiTest.get(t.f.main,"modelRegistry"),installed=new ModelRegistry(t.f.main);
        for(ModelRole role:ModelRole.values()){
            InstalledModel m=fixtures.getActive(role);File target=new File(installed.modelsRoot(),m.storageId());target.mkdirs();
            for(File f:m.directory().listFiles())if(f.isFile())Files.copy(f.toPath(),new File(target,f.getName()).toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
        prefs=t.f.main.getSharedPreferences(SettingsActivity.PREFERENCES,0);
        prefs.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true).putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget")
                .putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,"r0_full_frame").putString(SettingsActivity.KEY_RESEARCH_MP_EXECUTION,"cpu_2")
                .putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"cpu_2").putString(SettingsActivity.KEY_RESEARCH_MZ_EXECUTION,"cpu_2").commit();
    }
    SettingsActivity open(){ActivityController<SettingsActivity> c=Robolectric.buildActivity(SettingsActivity.class).setup().visible();windows.add(c);return c.get();}
    void closeWindow(ActivityController<SettingsActivity> c){((ExecutorService)ReflectionHelpers.getField(c.get(),"backgroundExecutor")).shutdownNow();c.pause().stop().destroy();}
    @After public void close()throws Exception{for(ActivityController<SettingsActivity> c:windows)closeWindow(c);if(t!=null)t.close();}
    MaterialAutoCompleteTextView dropdown(SettingsActivity a,int id){return a.findViewById(id);}
    List<String> choices(MaterialAutoCompleteTextView v){List<String> out=new ArrayList<>();for(int i=0;i<v.getAdapter().getCount();i++)out.add(v.getAdapter().getItem(i).toString());return out;}
    @Test public void allLabelsExplainThreadsAndKeepWireNames()throws Exception{
        String[] expected={"Automatycznie","CPU · 1 wątek","CPU · 2 wątki","CPU · 4 wątki","GPU · procesor graficzny"};
        String[] wires={"auto","cpu_1","cpu_2","cpu_4","gpu"};int i=0;
        InstalledModel model=new ModelRegistry(t.f.main).getActive(ModelRole.PLATE);
        for(ResearchExecutionChoice c:ResearchExecutionChoice.values()){assertEquals(expected[i],c.label());assertEquals(wires[i++],c.wireName());}
        for(ResearchExecutionChoice c:new ResearchExecutionChoice[]{ResearchExecutionChoice.CPU_1,ResearchExecutionChoice.CPU_2,ResearchExecutionChoice.CPU_4}){
            assertEquals(c==ResearchExecutionChoice.CPU_1?1:c==ResearchExecutionChoice.CPU_2?2:4,c.resolveApproved(model.manifest().variants().get(0)).cpuThreads);
        }
        assertTrue(ResearchExecutionChoice.GPU.resolveApproved(model.manifest().variants().get(0)).gpu);
        assertFalse(ResearchExecutionChoice.supportedFor(ModelRuntime.NCNN).contains(ResearchExecutionChoice.GPU));
    }
    @Test public void R0DoesNotRequireOrRewriteMpAndR1R2RestoreItsExplicitProfile(){
        SettingsActivity a=open();MaterialAutoCompleteTextView mp=dropdown(a,R.id.settings_research_mp_execution);
        assertEquals("MP · nieuruchamiany w wariancie R0",((TextView)a.findViewById(R.id.settings_research_mp_summary)).getText().toString());
        assertEquals("—",mp.getText().toString());assertFalse(mp.isEnabled());assertEquals("cpu_2",prefs.getString(SettingsActivity.KEY_RESEARCH_MP_EXECUTION,""));
        for(int id:new int[]{R.id.settings_roi_r1,R.id.settings_roi_r2}){
            ((MaterialButtonToggleGroup)a.findViewById(R.id.settings_roi_budget_group)).check(id);
            assertTrue(mp.isEnabled());assertEquals("CPU · 2 wątki",mp.getText().toString());assertEquals("cpu_2",prefs.getString(SettingsActivity.KEY_RESEARCH_MP_EXECUTION,""));
        }
    }
    @Test public void storedAutoNeedsAnExplicitSelectionAndIsNeverSilentlyMigrated(){
        prefs.edit().putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"auto").commit();SettingsActivity a=open();
        MaterialAutoCompleteTextView mt=dropdown(a,R.id.settings_research_mt_execution);List<String> labels=choices(mt);
        assertFalse(labels.contains("Automatycznie"));assertTrue(labels.contains("GPU · procesor graficzny"));
        assertEquals(a.getString(R.string.f25_explicit_execution_required),mt.getText().toString());assertEquals("auto",prefs.getString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,""));
        int selected=labels.indexOf("CPU · 2 wątki");mt.getOnItemClickListener().onItemClick(null,new TextView(a),selected,selected);
        assertEquals("cpu_2",prefs.getString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,""));assertEquals("CPU · 2 wątki",mt.getText().toString());
        assertThrows(IllegalArgumentException.class,()->ResearchExecutionChoice.AUTO.resolveApproved(new ModelRegistry(a).getActive(ModelRole.PLATE).manifest().variants().get(0)));
    }
    @Test public void basicOptionsHaveOneThreadExplanationAndNoTensorDiagnosticBlock(){
        SettingsActivity a=open();View root=a.getWindow().getDecorView();ArrayList<View> found=new ArrayList<>();
        root.findViewsWithText(found,MzInputEvidence.DIAGNOSTIC_BLOCK_REASON,View.FIND_VIEWS_WITH_TEXT);assertTrue(found.isEmpty());
        root.findViewsWithText(found,"Dowód wejścia MZ",View.FIND_VIEWS_WITH_TEXT);assertTrue(found.isEmpty());
        root.findViewsWithText(found,a.getString(R.string.f25_cpu_threads_hint),View.FIND_VIEWS_WITH_TEXT);assertEquals(1,found.size());
        assertFalse(MzInputEvidence.productionEnabled());assertThrows(IllegalStateException.class,MzInputEvidence::requireDiagnosticStartAllowed);
        ActivityController<DiagnosticsActivity> diagnostic=Robolectric.buildActivity(DiagnosticsActivity.class).setup().visible();
        try{
            View decor=diagnostic.get().getWindow().getDecorView();layout(decor,360,800);
            TextView status=diagnostic.get().findViewById(R.id.diagnostics_mz_input_evidence_status);showWithin(status,800);
            assertTrue(status.getText().toString().contains("tensorów wejściowych MZ"));assertTrue(status.getText().toString().contains(MzInputEvidence.DIAGNOSTIC_BLOCK_REASON));
            capture(decor,360,800,"f25_diagnostics_tensor_status_synthetic.png");
        }catch(Exception error){throw new AssertionError(error);}finally{diagnostic.pause().stop().destroy();}
    }
    @Test public void profileCopyRendersAtCompactSizesAndLargeFonts()throws Exception{
        int[][] sizes={{320,720,10},{320,720,13},{360,800,10},{360,800,13},{720,360,13}};
        for(int[] size:sizes){
            RuntimeEnvironment.setQualifiers("w"+size[0]+"dp-h"+size[1]+"dp-mdpi");
            Configuration c=new Configuration(t.f.main.getResources().getConfiguration());c.fontScale=size[2]/10f;
            RuntimeEnvironment.getApplication().getResources().updateConfiguration(c,RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
            prefs.edit().putString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,"gpu").commit();SettingsActivity a=open();
            View decor=a.getWindow().getDecorView();layout(decor,size[0],size[1]);TextView hint=a.findViewById(R.id.settings_cpu_threads_hint);
            hint.requestRectangleOnScreen(new Rect(0,0,hint.getWidth(),hint.getHeight()),true);layout(decor,size[0],size[1]);
            hint.requestRectangleOnScreen(new Rect(0,0,hint.getWidth(),hint.getHeight()),true);org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle();layout(decor,size[0],size[1]);
            showWithin(hint,size[1]);
            MaterialAutoCompleteTextView gpu=dropdown(a,R.id.settings_research_mt_execution);
            assertEquals("GPU · procesor graficzny",gpu.getText().toString());assertNotNull(gpu.getLayout());
            int available=gpu.getWidth()-gpu.getCompoundPaddingLeft()-gpu.getCompoundPaddingRight();
            for(int line=0;line<gpu.getLayout().getLineCount();line++)assertTrue("Profile label clipped",gpu.getLayout().getLineWidth(line)<=available+1);
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(gpu.getCurrentTextColor(),a.getColor(R.color.alpr_card))>=4.5);
            Rect visible=new Rect();assertTrue(hint.getGlobalVisibleRect(visible));assertTrue("Thread explanation clipped",visible.height()>=hint.getHeight()-1);
            assertEquals("gpu",prefs.getString(SettingsActivity.KEY_RESEARCH_MT_EXECUTION,""));
            capture(decor,size[0],size[1],"f25_options_"+size[0]+"_"+size[1]+"_font"+size[2]+"_synthetic.png");
            ActivityController<SettingsActivity> window=windows.remove(windows.size()-1);closeWindow(window);
        }
    }
    static void layout(View v,int w,int h){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
    static void showWithin(View target,int height){
        int[] position=new int[2];target.getLocationInWindow(position);int overflow=position[1]+target.getHeight()-height+8;
        if(overflow>0){ViewParent parent=target.getParent();while(parent instanceof View){
            if(parent instanceof androidx.core.widget.NestedScrollView){((View)parent).scrollBy(0,overflow);break;}parent=parent.getParent();
        }}
        target.getLocationInWindow(position);assertTrue("Text outside rendered viewport",position[1]+target.getHeight()<=height-7);
    }
    static void capture(View decor,int width,int height,String name)throws Exception{
        String folder=System.getenv("F5A_COCKPIT_EVIDENCE");if(folder==null)return;
        Files.createDirectories(Paths.get(folder));Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);decor.draw(new Canvas(image));
        try(OutputStream out=Files.newOutputStream(Paths.get(folder,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
    }
}
