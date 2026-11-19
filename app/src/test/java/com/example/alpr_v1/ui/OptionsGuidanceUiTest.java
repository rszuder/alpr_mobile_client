package com.example.alpr_v1.ui;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.ui.readiness.*;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import java.io.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-xhdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class OptionsGuidanceUiTest {
    ReadinessUiTest h;
    @Before public void setup(){h=new ReadinessUiTest();h.preferences();}
    @After public void close()throws Exception{if(h!=null)h.close();RuntimeEnvironment.setFontScale(1f);}
    SettingsActivity valid()throws Exception{ReadinessTestModels.install(RuntimeEnvironment.getApplication(),false,true);return h.open();}
    ReadinessPresentation.Marker marker(SettingsActivity a,int card){return (ReadinessPresentation.Marker)a.findViewById(card).getTag(R.id.readiness_section_marker);}
    @Test public void compactGuidanceMarksAutomaticChecksWithoutOfferingReimport()throws Exception{
        SettingsActivity a=valid();Map<String,?> before=new HashMap<>(h.prefs.getAll());
        assertTrue(a.findViewById(R.id.readiness_summary_panel) instanceof MaterialCardView);
        assertEquals(ReadinessResult.State.DEFERRED,h.result(a).state);
        for(int card:new int[]{R.id.options_camera_card,R.id.options_models_card,R.id.options_session_card})assertEquals(R.color.options_accent,marker(a,card).color());
        assertNull(marker(a,R.id.options_mode_card));assertNull(marker(a,R.id.options_advanced_card));
        for(String issue:new String[]{"camera_verification","session_preparation"}){
            View row=a.findViewById(R.id.readiness_findings).findViewWithTag(issue);assertNotNull(row);
            assertEquals(View.GONE,row.findViewWithTag("explanation:"+issue).getVisibility());assertNull(row.findViewWithTag("action:"+issue));
        }
        assertEquals(-1,F6ConfigurationUiTest.sections(a).expandedSection());assertEquals(before,h.prefs.getAll());
        capture(a,"01-deferred-compact.png");
    }
    @Test public void detailsExpandInPlaceAndSurviveActivityRecreation()throws Exception{
        SettingsActivity a=valid();View button=a.findViewById(R.id.readiness_findings).findViewWithTag("details:session_preparation");button.performClick();
        assertEquals(View.VISIBLE,a.findViewById(R.id.readiness_findings).findViewWithTag("explanation:session_preparation").getVisibility());
        assertEquals(-1,F6ConfigurationUiTest.sections(a).expandedSection());h.controller.recreate();a=h.controller.get();ReadinessUiTest.await(a);
        assertEquals(View.VISIBLE,a.findViewById(R.id.readiness_findings).findViewWithTag("explanation:session_preparation").getVisibility());
        a.findViewById(R.id.readiness_findings).findViewWithTag("details:session_preparation").performClick();
        assertEquals(View.GONE,a.findViewById(R.id.readiness_findings).findViewWithTag("explanation:session_preparation").getVisibility());
    }
    @Test public void exactFieldStaysMarkedAfterFocusCueAndOnlyFixedIssueDisappears()throws Exception{
        ReadinessTestModels.install(RuntimeEnvironment.getApplication(),false,true);
        h.prefs.edit().putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP").putBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,true).commit();
        SettingsActivity a=h.open();capture(a,"02-missing-setting.png");h.action(a,"tv_fixed_zoom");
        View control=a.findViewById(R.id.settings_research_autozoom_switch),owner=(View)control.getParent();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(2));F6ConfigurationUiTest.layout(a);
        ReadinessPresentation.Marker field=(ReadinessPresentation.Marker)owner.getTag(R.id.readiness_field_marker);
        assertNotNull(field);assertEquals(R.color.alpr_warning,field.color());assertNotNull(owner.getForeground());assertTrue(control.isShown());
        capture(a,"03-exact-setting.png");((MaterialSwitch)control).setChecked(false);ReadinessUiTest.await(a);
        assertNull(owner.getTag(R.id.readiness_field_marker));assertNull(marker(a,R.id.options_advanced_card));assertNotNull(marker(a,R.id.options_camera_card));
        assertNull(h.result(a).finding("tv_fixed_zoom"));assertNotNull(h.result(a).finding("tv_approval"));
        ((com.google.android.material.appbar.MaterialToolbar)a.findViewById(R.id.settings_toolbar)).getMenu().performIdentifierAction(R.id.readiness_back_summary,0);
        F6ConfigurationUiTest.settle(a);ReadinessUiTest.await(a);assertEquals(-1,F6ConfigurationUiTest.sections(a).expandedSection());capture(a,"04-after-correction.png");
    }
    @Test public void refreshingAnUnsavedFieldKeepsTheOpenSectionFocusAndPreferences()throws Exception{
        SettingsActivity a=valid();F6ConfigurationUiTest.select(a,3);
        TextView field=a.findViewById(R.id.settings_experiment_series_id);field.requestFocus();F6ConfigurationUiTest.layout(a);
        Map<String,?> before=new HashMap<>(h.prefs.getAll());field.setText("UNSAVED_GUIDANCE");ReadinessUiTest.await(a);
        assertEquals(before,h.prefs.getAll());assertTrue(field.hasFocus());assertEquals(3,F6ConfigurationUiTest.sections(a).expandedSection());assertFalse(F6ConfigurationUiTest.sections(a).animating());
        assertNotNull(marker(a,R.id.options_session_card));
    }
    @Test public void allSectionsReflectFindingsEvenWhenTheSummaryShowsOnlyThree()throws Exception{
        SettingsActivity a=h.open();android.widget.LinearLayout rows=a.findViewById(R.id.readiness_findings);assertEquals(3,rows.getChildCount());
        assertEquals(R.color.alpr_warning,marker(a,R.id.options_models_card).color());assertNotNull(marker(a,R.id.options_session_card));
        a.findViewById(R.id.readiness_show_all).performClick();assertTrue(rows.getChildCount()>3);assertNotNull(marker(a,R.id.options_camera_card));
        a.findViewById(R.id.readiness_show_all).performClick();assertEquals(3,rows.getChildCount());assertNotNull(marker(a,R.id.options_session_card));
    }
    @Test public void fontAndOrientationMatrixKeepsStatusLabelsReadable()throws Exception{
        for(boolean landscape:new boolean[]{false,true})for(float font:new float[]{1f,1.5f}){
            if(h.controller!=null){h.close();h=new ReadinessUiTest();h.preferences();}
            RuntimeEnvironment.setQualifiers(landscape?"w800dp-h360dp-land-xhdpi":"w360dp-h800dp-port-xhdpi");RuntimeEnvironment.setFontScale(font);
            SettingsActivity a=valid();View root=a.findViewById(R.id.settings_root);int width=landscape?1600:720,height=landscape?720:1600;
            for(int i=0;i<3;i++){root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);}
            for(int id:new int[]{R.id.readiness_state,R.id.options_camera_header,R.id.options_models_header,R.id.options_session_header}){
                TextView t=a.findViewById(id);assertNotNull(t.getLayout());
                for(int line=0;line<t.getLineCount();line++)assertEquals(t.getText().toString(),0,t.getLayout().getEllipsisCount(line));
                assertTrue(t.getText().toString(),t.getLayout().getHeight()+t.getCompoundPaddingTop()+t.getCompoundPaddingBottom()<=t.getHeight());
            }
            for(int color:new int[]{R.color.options_accent,R.color.alpr_warning})assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(a.getColor(color),a.getColor(R.color.options_guidance_surface))>=4.5);
            capture(a,"matrix-"+(landscape?"landscape":"portrait")+"-font-"+font+".png");
        }
    }
    static void capture(SettingsActivity a,String name)throws Exception{
        String folder=System.getenv("OPTIONS_GUIDANCE_UI_ARTIFACTS");if(folder==null)return;
        File directory=new File(folder);directory.mkdirs();View root=a.findViewById(R.id.settings_root);
        Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
        try(FileOutputStream out=new FileOutputStream(new File(directory,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
}
