package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.example.alpr_v1.*;
import com.example.alpr_v1.experiment.Final180PhotoRoi;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import static org.junit.Assert.*;

/** Actual settings Activity/XML; no camera, model inference, GT or device settings involved. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class F6ConfigurationUiTest {
    ActivityController<SettingsActivity> controller;
    SharedPreferences prefs;
    SettingsActivity open(boolean f6){
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences(SettingsActivity.PREFERENCES,0);
        prefs.edit().clear().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true)
                .putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget").putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,"r0_full_frame")
                .putString(SettingsActivity.KEY_EXPERIMENT_VARIANT,"R0_FINAL").putString(SettingsActivity.KEY_TV_INPUT_MODE,"TV_BBOX_CROP")
                .putBoolean(Final180PhotoRoi.ENABLED,f6).commit();
        controller=Robolectric.buildActivity(SettingsActivity.class).setup().visible();return controller.get();
    }
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
    @Test public void actualSettingsShowsF6AndR0TogetherAndKeepsTypeUnchanged(){
        SettingsActivity activity=open(true);
        String text=((TextView)activity.findViewById(R.id.settings_research_configuration_summary)).getText().toString();
        assertEquals("Końcowe badanie na fotografiach",text);
        assertTrue(((com.google.android.material.button.MaterialButton)activity.findViewById(R.id.settings_roi_r0)).isChecked());
        assertEquals("roi_budget",prefs.getString(SettingsActivity.KEY_EXPERIMENT_TYPE,""));
        ViewGroup section=activity.findViewById(R.id.options_mode_body);
        assertTrue("F6 and the category share the existing mode section",descendant(activity.findViewById(R.id.settings_f6_enabled),section));
        assertTrue(descendant(activity.findViewById(R.id.settings_experiment_type),section));
    }
    @Test public void operatorVariantLabelAloneNeverClaimsF6(){
        SettingsActivity activity=open(false);String text=((TextView)activity.findViewById(R.id.settings_research_configuration_summary)).getText().toString();
        assertEquals("Analiza obrazu z ekranu TV",text);
        assertEquals("R0_FINAL",prefs.getString(SettingsActivity.KEY_EXPERIMENT_VARIANT,""));
    }
    static final int[] HEADERS={R.id.options_mode_header,R.id.options_camera_header,R.id.options_models_header,R.id.options_session_header,R.id.options_advanced_header};
    static final int[] BODIES={R.id.options_mode_body,R.id.options_camera_body,R.id.options_models_body,R.id.options_session_body,R.id.options_advanced_body};
    static final int[] CARDS={R.id.options_mode_card,R.id.options_camera_card,R.id.options_models_card,R.id.options_session_card,R.id.options_advanced_card};
    static void layout(SettingsActivity a){
        View root=a.findViewById(R.id.settings_root);
        root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));
        root.layout(0,0,720,1600);
        root.getViewTreeObserver().dispatchOnPreDraw();
    }
    static void settle(SettingsActivity a){
        for(int i=0;i<4;i++){ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(250));layout(a);}
    }
    static void select(SettingsActivity a,int section){a.findViewById(HEADERS[section]).performClick();settle(a);}
    static OptionsSectionsController sections(SettingsActivity a){return ReflectionHelpers.getField(a,"optionsSections");}
    static boolean descendant(View child,ViewGroup parent){while(child!=null){if(child==parent)return true;child=child.getParent() instanceof View?(View)child.getParent():null;}return false;}
    void assertOnly(SettingsActivity a,int selected){for(int i=0;i<BODIES.length;i++)assertEquals("section "+i,i==selected?View.VISIBLE:View.GONE,a.findViewById(BODIES[i]).getVisibility());}
    @Test public void fiveSectionsOpenOneAtATimeWithoutWritingConfiguration(){
        SettingsActivity a=open(true);assertOnly(a,-1);layout(a);
        java.util.Map<String,?> before=new java.util.HashMap<>(prefs.getAll());
        for(int section=0;section<HEADERS.length;section++){
            select(a,section);assertOnly(a,section);
            assertTrue(a.findViewById(HEADERS[section]).getContentDescription().toString().contains("Rozwinięte"));
        }
        select(a,4);assertOnly(a,-1);
        assertEquals("Accordion must not change settings_revision or any recognition preference",before,prefs.getAll());
        assertTrue(descendant(a.findViewById(R.id.settings_f6_photo_roi),a.findViewById(R.id.options_camera_body)));
        assertTrue(descendant(a.findViewById(R.id.settings_experiment_series_id),a.findViewById(R.id.options_session_body)));
        assertTrue(descendant(a.findViewById(R.id.settings_research_mt_execution),a.findViewById(R.id.options_models_body)));
    }
    @Test public void actualSwitchStateIsTextualAndDisabledIsNotOff(){
        SettingsActivity a=open(true);MaterialSwitch f6=a.findViewById(R.id.settings_f6_enabled);
        TextView status=a.findViewById(R.id.settings_f6_state);
        MaterialCardView surface=a.findViewById(R.id.options_mode_card);
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(status.getCurrentTextColor(),surface.getCardBackgroundColor().getDefaultColor())>=4.5);
        assertEquals("Włączone",((TextView)a.findViewById(R.id.settings_f6_state)).getText().toString());
        f6.setEnabled(false);((OptionsSectionsController)org.robolectric.util.ReflectionHelpers.getField(a,"optionsSections")).refresh();
        assertTrue(f6.isChecked());assertFalse(f6.isEnabled());
        assertEquals("Włączone",((TextView)a.findViewById(R.id.settings_f6_state)).getText().toString());
        f6.setEnabled(true);f6.setChecked(false);
        assertEquals("Wyłączone",((TextView)a.findViewById(R.id.settings_f6_state)).getText().toString());
        assertFalse(prefs.getBoolean(Final180PhotoRoi.ENABLED,true));assertEquals("roi_budget",prefs.getString(SettingsActivity.KEY_EXPERIMENT_TYPE,""));
        MaterialSwitch experiment=a.findViewById(R.id.settings_experiment_switch);experiment.setChecked(false);
        assertEquals(View.GONE,a.findViewById(R.id.options_research_identity).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.options_research_execution).getVisibility());
        assertEquals("Wyłączone",((TextView)a.findViewById(R.id.settings_experiment_state)).getText().toString());
        assertEquals("Skanowanie tablic",((TextView)a.findViewById(R.id.settings_research_configuration_summary)).getText().toString());
        experiment.setChecked(true);
        assertEquals(View.VISIBLE,a.findViewById(R.id.options_research_identity).getVisibility());
        assertEquals("roi_budget",prefs.getString(SettingsActivity.KEY_EXPERIMENT_TYPE,""));
    }
    @Test public void expandedSectionAndValuesSurviveActivityRecreation(){
        SettingsActivity a=open(true);select(a,3);
        ((TextView)a.findViewById(R.id.settings_experiment_series_id)).setText("SYNTHETIC-OPTIONS");
        prefs.edit().putString("tv_setup_calibration_v1","PRESERVE-TV").putString(Final180PhotoRoi.PREFERENCE,"PRESERVE-PHOTO").commit();
        Bundle saved=new Bundle();controller.saveInstanceState(saved).pause().stop().destroy();
        controller=Robolectric.buildActivity(SettingsActivity.class).setup(saved).visible();a=controller.get();assertOnly(a,3);
        assertEquals("SYNTHETIC-OPTIONS",((TextView)a.findViewById(R.id.settings_experiment_series_id)).getText().toString());
        assertEquals("PRESERVE-TV",prefs.getString("tv_setup_calibration_v1",""));assertEquals("PRESERVE-PHOTO",prefs.getString(Final180PhotoRoi.PREFERENCE,""));
        assertEquals("r0_full_frame",prefs.getString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,""));
    }
    @Test @Config(qualifiers="w360dp-h800dp-xhdpi") public void smallScreenAndLargeTextKeepSectionsAndControlsReachable()throws Exception{
        RuntimeEnvironment.setFontScale(1.5f);SettingsActivity a=open(true);
        View root=a.findViewById(R.id.settings_root);NestedScrollView scroll=a.findViewById(R.id.settings_scroll);
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root,new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.of(0,48,0,48))
                .setInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.of(0,48,0,48))
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(),Insets.of(0,48,0,0))
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.mandatorySystemGestures(),Insets.of(0,0,0,48)).build());
        assertEquals(48,root.getPaddingBottom());
        for(int section=0;section<HEADERS.length;section++){
            select(a,section);
            root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);
            View header=a.findViewById(HEADERS[section]);assertTrue(header.getMeasuredHeight()>=96);assertTrue(header.getMeasuredWidth()<=720);
            View body=a.findViewById(BODIES[section]);assertTrue(body.getMeasuredHeight()>0);
            android.graphics.Rect r=new android.graphics.Rect();header.getDrawingRect(r);scroll.offsetDescendantRectToMyCoords(header,r);scroll.scrollTo(0,r.top);
            android.graphics.Rect visible=new android.graphics.Rect();assertTrue("header reachable "+section,header.getGlobalVisibleRect(visible));assertTrue(visible.height()>=96);
            hostImage(root,"options-large-text-"+section+".png");
        }
        assertTrue("Scroll range must include all final controls",scroll.getChildAt(0).getHeight()>scroll.getHeight());
    }
    static void hostImage(View root,String name)throws Exception{
        String directory=System.getenv("F6_OPTIONS_UI_ARTIFACTS");if(directory==null)return;
        java.io.File file=new java.io.File(directory,name);file.getParentFile().mkdirs();
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(image));try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
}
