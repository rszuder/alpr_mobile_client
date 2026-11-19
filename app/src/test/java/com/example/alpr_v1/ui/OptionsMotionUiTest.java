package com.example.alpr_v1.ui;

import android.animation.ValueAnimator;
import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import com.example.alpr_v1.DiagnosticsActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.SettingsActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static com.example.alpr_v1.ui.F6ConfigurationUiTest.*;

/** Real settings, dialogs and layout; no phone, camera or inference is involved. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class OptionsMotionUiTest {
    private final F6ConfigurationUiTest fixture=new F6ConfigurationUiTest();

    private SettingsActivity open(){
        durationScale(1);
        SettingsActivity activity=fixture.open(true);
        // ShadowActivity stores focus separately instead of delegating to the real decor.
        View decor=activity.getWindow().getDecorView();
        Shadows.shadowOf(activity).setCurrentFocus(decor.findFocus());
        decor.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus,newFocus)->Shadows.shadowOf(activity).setCurrentFocus(newFocus));
        layout(activity);
        return activity;
    }

    private static void durationScale(float value){
        ReflectionHelpers.callStaticMethod(ValueAnimator.class,"setDurationScale",
                ReflectionHelpers.ClassParameter.from(float.class,value));
    }

    @After public void close(){fixture.close();durationScale(1);}

    private static void advanceAnimation(SettingsActivity activity,long millis){
        ValueAnimator animator=ReflectionHelpers.getField(sections(activity),"animation");
        assertNotNull("A laid out section must animate when system animations are enabled",animator);
        // Drive the real animator deterministically; host vsync does not model phone frame timing.
        animator.setCurrentPlayTime(millis);layout(activity);
    }

    @Test public void rapidSelectionReversesFromCurrentHeightAndIgnoresOldCallbacks(){
        SettingsActivity a=open();Map<String,?> before=new HashMap<>(fixture.prefs.getAll());
        a.findViewById(HEADERS[0]).performClick();
        advanceAnimation(a,64);
        int height=a.findViewById(BODIES[0]).getLayoutParams().height;
        assertTrue("Opening must have intermediate frames",height>0);
        assertTrue(sections(a).animating());
        int arrow=((MaterialButton)a.findViewById(HEADERS[0])).getIcon().getLevel();
        assertTrue(arrow>0&&arrow<10000);
        a.findViewById(HEADERS[0]).performClick();
        assertEquals("Reversal starts at the displayed height",height,a.findViewById(BODIES[0]).getLayoutParams().height);
        advanceAnimation(a,32);
        a.findViewById(HEADERS[1]).performClick();a.findViewById(HEADERS[2]).performClick();
        advanceAnimation(a,48);
        a.findViewById(HEADERS[3]).performClick();settle(a);
        fixture.assertOnly(a,3);assertFalse(sections(a).animating());
        for(int i=0;i<5;i++){
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,a.findViewById(BODIES[i]).getLayoutParams().height);
            assertEquals(i==3?10000:0,((MaterialButton)a.findViewById(HEADERS[i])).getIcon().getLevel());
        }
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(1));fixture.assertOnly(a,3);
        assertEquals(before,fixture.prefs.getAll());
    }

    @Test public void refreshingValuesDoesNotRestartAnAnimation(){
        SettingsActivity a=open();a.findViewById(HEADERS[2]).performClick();
        advanceAnimation(a,64);
        int height=a.findViewById(BODIES[2]).getLayoutParams().height;
        for(int i=0;i<20;i++)sections(a).refresh();
        assertEquals(height,a.findViewById(BODIES[2]).getLayoutParams().height);
        assertTrue(sections(a).animating());settle(a);
        fixture.assertOnly(a,2);assertFalse(sections(a).animating());
    }

    @Test public void disabledSystemAnimationsSwitchImmediately(){
        SettingsActivity a=open();durationScale(0);
        assertFalse(ValueAnimator.areAnimatorsEnabled());
        a.findViewById(HEADERS[4]).performClick();fixture.assertOnly(a,4);
        assertFalse(sections(a).animating());
        a.findViewById(HEADERS[4]).performClick();fixture.assertOnly(a,-1);
    }

    @Test public void stoppingMidAnimationAndRecreationKeepSectionAndDraft(){
        SettingsActivity a=open();a.findViewById(HEADERS[3]).performClick();
        advanceAnimation(a,40);
        ((EditText)a.findViewById(R.id.settings_experiment_series_id)).setText("V2-DRAFT");
        Bundle saved=new Bundle();fixture.controller.saveInstanceState(saved).pause().stop();
        assertFalse(sections(a).animating());fixture.assertOnly(a,3);
        fixture.controller.destroy();
        fixture.controller=Robolectric.buildActivity(SettingsActivity.class).setup(saved).visible();
        a=fixture.controller.get();layout(a);settle(a);
        fixture.assertOnly(a,3);assertFalse(sections(a).animating());
        assertEquals("V2-DRAFT",((EditText)a.findViewById(R.id.settings_experiment_series_id)).getText().toString());
        // A system picker pauses/stops this Activity and later returns to the same instance.
        fixture.controller.pause().stop().start().resume().visible();settle(a);
        fixture.assertOnly(a,3);
        assertEquals("V2-DRAFT",((EditText)a.findViewById(R.id.settings_experiment_series_id)).getText().toString());
    }

    @Test public void collapsingFocusedFieldClosesKeyboardAndKeepsText(){
        SettingsActivity a=open();select(a,3);
        EditText field=a.findViewById(R.id.settings_experiment_series_id);field.setText("KEEP-TEXT");
        assertTrue(field.requestFocus());
        assertSame(field,a.getCurrentFocus());
        InputMethodManager keyboard=(InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.showSoftInput(field,InputMethodManager.SHOW_IMPLICIT);
        assertTrue(Shadows.shadowOf(keyboard).isSoftInputVisible());
        select(a,3);fixture.assertOnly(a,-1);
        assertFalse(Shadows.shadowOf(keyboard).isSoftInputVisible());
        assertFalse(descendant(a.getCurrentFocus(),a.findViewById(BODIES[3])));
        assertEquals("KEEP-TEXT",field.getText().toString());
        // Identity keeps its existing onPause persistence boundary.
        fixture.controller.pause();
        assertEquals("KEEP-TEXT",fixture.prefs.getString(SettingsActivity.KEY_EXPERIMENT_SERIES_ID,""));
        fixture.controller.resume();
    }

    private static WindowInsetsCompat insets(boolean bars,boolean ime){
        int system=WindowInsetsCompat.Type.systemBars();
        WindowInsetsCompat.Builder builder=new WindowInsetsCompat.Builder()
                .setInsets(system,bars?Insets.of(0,48,0,96):Insets.NONE)
                .setInsetsIgnoringVisibility(system,Insets.of(0,48,0,96)).setVisible(system,bars)
                .setDisplayCutout(new DisplayCutoutCompat(new Rect(0,24,0,0),Collections.singletonList(new Rect(0,0,80,24))))
                .setMandatorySystemGestureInsets(Insets.of(0,0,0,32))
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(),Insets.of(0,24,0,0))
                .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.mandatorySystemGestures(),Insets.of(0,0,0,32));
        if(android.os.Build.VERSION.SDK_INT>=30)builder.setInsets(WindowInsetsCompat.Type.ime(),ime?Insets.of(0,0,0,500):Insets.NONE)
                .setVisible(WindowInsetsCompat.Type.ime(),ime);
        return builder.build();
    }

    private static void dispatchInsets(View root,boolean bars,boolean ime){
        WindowInsetsCompat value=insets(bars,ime);
        assertEquals(24,value.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout()).top);
        if(android.os.Build.VERSION.SDK_INT<30){
            // AndroidX reads the root's physical cutout on API 29, not only the dispatch argument.
            Object viewRoot=ReflectionHelpers.callInstanceMethod(root,"getViewRootImpl");
            ReflectionHelpers.setField(viewRoot,"mLastWindowInsets",value.toWindowInsets());
        }
        ViewCompat.dispatchApplyWindowInsets(root,value);
    }

    @Test @Config(sdk={29,36}) public void transientBarsKeepSafeInsetsAndViewportUnchanged(){
        SettingsActivity a=open();View root=a.findViewById(R.id.settings_root);
        NestedScrollView scroll=a.findViewById(R.id.settings_scroll);
        dispatchInsets(root,false,false);layout(a);
        Rect before=new Rect(scroll.getLeft(),scroll.getTop(),scroll.getRight(),scroll.getBottom());
        int top=root.getPaddingTop(),bottom=root.getPaddingBottom();
        assertEquals(24,top);assertEquals(32,bottom);
        for(int i=0;i<3;i++){
            dispatchInsets(root,true,false);layout(a);
            assertEquals(top,root.getPaddingTop());assertEquals(bottom,root.getPaddingBottom());
            assertEquals(before,new Rect(scroll.getLeft(),scroll.getTop(),scroll.getRight(),scroll.getBottom()));
            CameraWindowUi.install(a.getWindow(),root); // Repeated binding must not accumulate padding.
            dispatchInsets(root,false,false);layout(a);
            assertEquals(top,root.getPaddingTop());assertEquals(bottom,root.getPaddingBottom());
        }
        if(android.os.Build.VERSION.SDK_INT>=30){
            int padding=scroll.getPaddingBottom();
            dispatchInsets(root,false,true);
            assertEquals(padding+500-bottom,scroll.getPaddingBottom());
            dispatchInsets(root,false,false);assertEquals(padding,scroll.getPaddingBottom());
        }
    }

    private static void assertHidden(Window window){
        int required=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        assertEquals(required,window.getDecorView().getSystemUiVisibility()&required);
    }

    @Test @Config(sdk=29) public void helpAndHistoryDialogsKeepFullscreenAndExistingListeners(){
        SettingsActivity a=open();select(a,0);assertHidden(a.getWindow());
        a.findViewById(R.id.options_mode_help).performClick();
        android.app.Dialog help=ShadowDialog.getLatestDialog();assertTrue(help.isShowing());assertHidden(help.getWindow());help.dismiss();
        int[] events={0,0};
        AlertDialog custom=new ImmersiveDialogBuilder(a).setTitle("SYNTHETIC").setMessage("Callbacks").create();
        custom.setOnShowListener(d->events[0]++);custom.setOnDismissListener(d->events[1]++);custom.show();
        ShadowLooper.shadowMainLooper().idle();
        assertHidden(custom.getWindow());assertEquals(1,events[0]);custom.dismiss();
        ShadowLooper.shadowMainLooper().idle();assertEquals(1,events[1]);
        AlertDialog history=ResearchSessionPicker.showHistory(a,Collections.emptyList(),()->"SYNTHETIC",file->fail("No session exists"),Runnable::run);
        assertHidden(history.getWindow());history.dismiss();
        a.getWindow().getDecorView().dispatchWindowFocusChanged(true);assertHidden(a.getWindow());
        fixture.assertOnly(a,0);
    }

    @Test @Config(sdk=29) public void diagnosticsUsesTheSameFullscreenPolicy(){
        ActivityController<DiagnosticsActivity> diagnostics=Robolectric.buildActivity(DiagnosticsActivity.class).setup().visible();
        try{assertHidden(diagnostics.get().getWindow());}
        finally{diagnostics.pause().stop().destroy();}
    }

    @Test public void renderAllSectionsWithLargeFontsAndBothSystemThemes()throws Exception{
        for(boolean night:new boolean[]{false,true})for(float font:new float[]{1,1.5f}){
            RuntimeEnvironment.setQualifiers("w360dp-h800dp-"+(night?"night":"notnight")+"-xhdpi");
            RuntimeEnvironment.setFontScale(font);SettingsActivity a=open();settle(a);
            String prefix="options-v2-"+(night?"night":"day")+"-font-"+(int)(font*100);
            hostImage(a.findViewById(R.id.settings_root),prefix+"-collapsed.png");
            // Five existing setting sections plus the separate guidance card.
            assertEquals(6,countCards(a.findViewById(R.id.options_content)));
            for(int id:CARDS)assertTrue(a.findViewById(id) instanceof MaterialCardView);
            Map<String,?> before=new HashMap<>(fixture.prefs.getAll());
            for(int i=0;i<5;i++){
                select(a,i);fixture.assertOnly(a,i);
                MaterialButton header=a.findViewById(HEADERS[i]);
                assertEquals("Only the section owns a border",0,header.getStrokeWidth());
                Rect visible=new Rect();assertTrue(header.getGlobalVisibleRect(visible));
                assertEquals("Expanded header "+i+" font="+font+" night="+night+" must remain fully visible; clipped="+visible+
                        " scroll="+((NestedScrollView)a.findViewById(R.id.settings_scroll)).getScrollY(),header.getHeight(),visible.height());
                assertTrue(header.getMeasuredHeight()>=96);
                MaterialCardView card=a.findViewById(CARDS[i]);
                assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(header.getCurrentTextColor(),card.getCardBackgroundColor().getDefaultColor())>=4.5);
                // Draw the attached window root. Material floating-label cutouts depend on
                // window coordinates and render incorrectly when a detached card is drawn alone.
                View root=a.findViewById(R.id.settings_root);
                hostImage(root,prefix+"-section-"+i+".png");
                hostImage(root,prefix+"-viewport-"+i+".png");
                NestedScrollView scroll=a.findViewById(R.id.settings_scroll);
                Rect bounds=new Rect();card.getDrawingRect(bounds);scroll.offsetDescendantRectToMyCoords(card,bounds);
                int end=Math.max(scroll.getScrollY(),bounds.bottom-scroll.getHeight()),page=1;
                while(scroll.getScrollY()<end){
                    int oldY=scroll.getScrollY();
                    scroll.scrollTo(0,Math.min(end,oldY+Math.max(1,scroll.getHeight()*3/4)));layout(a);
                    if(scroll.getScrollY()==oldY)break;
                    hostImage(root,prefix+"-section-"+i+"-page-"+(page++)+".png");
                }
            }
            assertEquals(before,fixture.prefs.getAll());
            fixture.close();fixture.controller=null;
        }
    }

    private static int countCards(View view){
        int count=view instanceof MaterialCardView?1:0;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=countCards(group.getChildAt(i));}
        return count;
    }
}
