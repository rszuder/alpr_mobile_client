package com.example.alpr_v1.ui;

import android.app.Application;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.graphics.ColorUtils;
import com.example.alpr_v1.*;
import com.example.alpr_v1.acquisition.EntityRecognitionSnapshot;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.vision.Point2;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/** Native host text/layout/Canvas, no camera, APK install or detector invocation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainUiReadabilityTest {
    public static class Harness extends AppCompatActivity {
        LivePresentationController presentation;TvSetupController tv;
        @Override public void onCreate(Bundle saved){
            setTheme(R.style.Theme_ALPR_v1);super.onCreate(saved);setContentView(R.layout.activity_main);
            findViewById(R.id.recognition_hint).setVisibility(View.GONE);findViewById(R.id.export_report_button).setVisibility(View.GONE);
            presentation=presenter(this);
            tv=new TvSetupController(this,findViewById(R.id.camera_preview),findViewById(R.id.detection_overlay),()->true,()->{},(n,d)->{});
            findViewById(R.id.tv_frame_summary).setVisibility(View.VISIBLE);
            MainActivity tintContext=Robolectric.buildActivity(MainActivity.class).get();
            try {
                Method tint=MainActivity.class.getDeclaredMethod("tintCameraAction",com.google.android.material.button.MaterialButton.class,int.class);tint.setAccessible(true);
                for(int id:new int[]{R.id.analysis_start_button,R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button,R.id.live_scene_mode_toggle,R.id.search_plate_button,R.id.release_target_button,R.id.auto_zoom_button,R.id.experiment_timer_button,R.id.experiment_thermal_button})
                    tint.invoke(tintContext,findViewById(id),R.color.alpr_accent);
            }catch(Exception e){throw new AssertionError(e);}
        }
    }
    static LivePresentationController presenter(AppCompatActivity a){return new LivePresentationController(
            a.findViewById(R.id.live_status_strip),a.findViewById(R.id.live_status_dot),a.findViewById(R.id.live_status),a.findViewById(R.id.live_status_secondary),
            a.findViewById(R.id.live_diagnostics_toggle),a.findViewById(R.id.live_event),a.findViewById(R.id.live_hud_row),a.findViewById(R.id.live_hud),
            a.findViewById(R.id.recognition_hint),a.findViewById(R.id.confirmed_result_tray),a.findViewById(R.id.confirmed_result_text),
            a.findViewById(R.id.confirmed_result_confidence),a.findViewById(R.id.confirmed_result_meta));}
    ActivityController<Harness> owner;Harness a;int width,height;float scale;
    static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    void create(int w,int h,float f){
        if(owner!=null){owner.pause().stop().destroy();owner=null;}
        width=w;height=h;scale=f;RuntimeEnvironment.setQualifiers("w"+w+"dp-h"+h+"dp-mdpi");
        Configuration c=new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());c.fontScale=f;
        RuntimeEnvironment.getApplication().getResources().updateConfiguration(c,RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
        RuntimeEnvironment.getApplication().getSharedPreferences("alpr_ui",0).edit().clear().commit();
        owner=Robolectric.buildActivity(Harness.class).setup().visible();a=owner.get();layout();
    }
    @After public void close(){if(owner!=null){a.presentation.dispose();owner.pause().stop().destroy();}}
    void layout(){
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        View decor=a.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));decor.layout(0,0,width,height);
    }
    static Rect bounds(View v){Rect r=new Rect();assertTrue(v.getGlobalVisibleRect(r));return r;}
    void completeText(int id){
        TextView v=a.findViewById(id);assertTrue("hidden "+id,v.isShown());assertNotNull(v.getLayout());
        for(int i=0;i<v.getLayout().getLineCount();i++)assertEquals("ellipsis "+id,0,v.getLayout().getEllipsisCount(i));
        int count=v.getLayout().getLineCount();assertEquals("missing end "+id+": "+v.getText(),v.getText().length(),v.getLayout().getLineEnd(count-1));
        assertTrue("height "+id+": "+v.getText(),v.getLayout().getHeight()+v.getCompoundPaddingTop()+v.getCompoundPaddingBottom()<=v.getHeight());
    }
    void statusFits(){
        completeText(R.id.live_status);if(a.findViewById(R.id.live_status_secondary).isShown())completeText(R.id.live_status_secondary);
        Rect status=bounds(a.findViewById(R.id.live_status_strip));
        for(int id:new int[]{R.id.live_diagnostics_toggle,R.id.live_model_info_toggle}){
            completeText(id);View hud=a.findViewById(id);assertTrue(hud.isEnabled());assertTrue(hud.getHeight()>=48);assertTrue(hud.getWidth()>=48);assertFalse(Rect.intersects(status,bounds(hud)));
        }
    }
    void confirmTv()throws Exception{
        PreviewView preview=a.findViewById(R.id.camera_preview);CameraPreviewGeometry g=TvBboxCropTest.geometry(640,400,0,640,400,new double[]{0,0,640,400},preview.getWidth(),preview.getHeight(),1);
        set(a.tv,"geometry",g);set(a.tv,"calibration",TvSetupCalibration.confirm(Arrays.asList(new Point2(.12f,.18f),new Point2(.83f,.24f),new Point2(.76f,.70f),new Point2(.19f,.65f)),g,1,"2026-11-07T10:00:00Z"));
        a.tv.refreshInputSummary();layout();
    }
    void png(String name)throws Exception {
        String folder=System.getenv("F5A_MAIN_UI_EVIDENCE");if(folder==null)return;java.nio.file.Path dir=Paths.get(folder);Files.createDirectories(dir);
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);a.getWindow().getDecorView().draw(new Canvas(image));
        try(OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
        String row=name+"\t"+width+"\t"+height+"\t"+scale+"\t"+bounds(a.findViewById(R.id.camera_preview))+"\n";
        Files.write(dir.resolve("layout_cases.tsv"),row.getBytes(java.nio.charset.StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    void matrix(int w,int h,float font)throws Exception {
        create(w,h,font);confirmTv();
        ((TextView)a.findViewById(R.id.collection_toggle)).setText(R.string.camera_action_crops_automatic);
        ((TextView)a.findViewById(R.id.analysis_start_button)).setText(R.string.main_ui_stop_session);
        ((TextView)a.findViewById(R.id.gallery_open_button)).setText(R.string.camera_action_review);
        ((TextView)a.findViewById(R.id.gallery_count_badge)).setText("19");
        a.findViewById(R.id.experiment_timer_button).setVisibility(View.VISIBLE);((TextView)a.findViewById(R.id.experiment_timer_button)).setText("Timer");
        a.findViewById(R.id.experiment_thermal_button).setVisibility(View.VISIBLE);((TextView)a.findViewById(R.id.experiment_thermal_button)).setText("BAT 36,2°C · TH0 · HEAD —");
        a.findViewById(R.id.main_device_status).setVisibility(View.GONE);
        a.presentation.showState(LivePresentationController.State.STATIC_IDLE,"");a.presentation.showResult(99,"B7737",.26,true,2);a.presentation.showState(LivePresentationController.State.STATIC_IDLE,"");layout();
        png("main_"+w+"_font"+Math.round(font*10)+"_idle");statusFits();
        for(int id:new int[]{R.id.tv_frame_status,R.id.confirmed_result_text,R.id.confirmed_result_confidence,R.id.live_scene_mode_toggle,R.id.search_plate_button,
                R.id.release_target_button,R.id.auto_zoom_button,R.id.analysis_start_button,R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button,
                R.id.gallery_count_badge,R.id.experiment_timer_button,R.id.experiment_thermal_button})completeText(id);
        assertEquals("Scene label and short mode",2,((TextView)a.findViewById(R.id.live_scene_mode_toggle)).getLineCount());
        assertFalse(Rect.intersects(bounds(a.findViewById(R.id.experiment_timer_button)),bounds(a.findViewById(R.id.experiment_thermal_button))));
        Rect camera=bounds(a.findViewById(R.id.camera_preview));assertTrue(camera.height()>height*.35);
        a.tv.setInputSummaryProvider(()->a.getString(R.string.main_ui_input_active,a.getString(R.string.main_ui_input_tv)));layout();completeText(R.id.tv_frame_status);
        assertEquals("START copy must not change viewport",camera,bounds(a.findViewById(R.id.camera_preview)));
        a.presentation.showState(LivePresentationController.State.RECOGNIZING,"");layout();statusFits();
        a.presentation.showUserStatus(LivePresentationController.State.PREVIEW,"Przygotowanie analizy","Wczytywanie modeli");layout();statusFits();
        a.presentation.showUserStatus(LivePresentationController.State.ERROR,"Obserwacja TV zatrzymana","Sprawdź kalibrację TV");layout();statusFits();
        a.presentation.stop();layout();assertEquals(View.VISIBLE,a.findViewById(R.id.live_status_strip).getVisibility());
        assertEquals(a.getString(R.string.main_ui_stopped),((TextView)a.findViewById(R.id.live_status)).getText().toString());
    }
    @Test public void layout320Font10()throws Exception{matrix(320,720,1);}
    @Test public void layout320Font13()throws Exception{matrix(320,720,1.3f);}
    @Test public void layout360Font10()throws Exception{matrix(360,800,1);}
    @Test public void layout360Font13()throws Exception{matrix(360,800,1.3f);}
    @Test public void layout411Font10()throws Exception{matrix(411,900,1);}
    @Test public void layout411Font13()throws Exception{matrix(411,900,1.3f);}
    @Test public void percentagesKeepTheirSeparateMemoryScopesAndStabilityBoolean(){
        create(360,800,1);LivePresentationController p=a.presentation;
        p.showResult(9,"B7737",.26,true,2);assertEquals("stabilne · MZ 26%",((TextView)a.findViewById(R.id.confirmed_result_confidence)).getText().toString());
        p.showResult(9,"B7737",.19,true,3);assertTrue(((TextView)a.findViewById(R.id.confirmed_result_confidence)).getText().toString().contains("26%"));
        p.showResult(10,"B7737",.51,false,1);assertEquals("odczyt · MZ 51%",((TextView)a.findViewById(R.id.confirmed_result_confidence)).getText().toString());
        assertEquals(10,p.presentedTrackId());assertTrue(a.findViewById(R.id.confirmed_result_confidence).getContentDescription().toString().contains("nie oznacza stabilności"));
        p.clearResult();assertEquals(0,p.presentedTrackId());
    }
    @Test public void idleIsClearedByStoppedErrorAndMissingModels(){
        create(360,800,1);LivePresentationController p=a.presentation;p.showState(LivePresentationController.State.STATIC_IDLE,"");
        assertEquals("Czekam na zmianę",((TextView)a.findViewById(R.id.live_status_secondary)).getText().toString());
        p.showState(LivePresentationController.State.ERROR,"");assertEquals(View.GONE,a.findViewById(R.id.live_status_secondary).getVisibility());
        p.showMissingModelsStatus("Brak modeli","Zaimportuj modele");p.showState(LivePresentationController.State.STATIC_IDLE,"");
        assertFalse(((TextView)a.findViewById(R.id.live_status)).getText().toString().contains("Scena gotowa"));p.stop();
        assertFalse(a.findViewById(R.id.live_status_strip).getContentDescription().toString().contains("Czekam"));
    }
    @Test public void hudActionsRemainClickableAndLegendIsAvailable()throws Exception{
        create(360,800,1.3f);a.presentation.showState(LivePresentationController.State.STATIC_IDLE,"");
        Rect camera=bounds(a.findViewById(R.id.camera_preview));
        assertTrue(a.findViewById(R.id.live_diagnostics_toggle).performClick());layout();
        assertNull(org.robolectric.shadows.ShadowDialog.getLatestDialog());
        assertNull(a.findViewById(R.id.hud_mz_score_legend));
        assertFalse(a.findViewById(R.id.hud1_viewport) instanceof android.widget.ScrollView);
        a.presentation.showResult(99,"B7737",.26,true,2);layout();
        assertTrue(a.findViewById(R.id.confirmed_result_confidence).getContentDescription().toString().contains("minimum średnich"));
        assertEquals(camera,bounds(a.findViewById(R.id.camera_preview)));
        a.findViewById(R.id.live_diagnostics_toggle).performClick();layout();assertFalse(a.findViewById(R.id.live_hud_row).isShown());
    }
    static OverlayItem vehicle(long id,float l,float t,float r,float b){return new OverlayItem(OverlayItem.Kind.VEHICLE,new RectF(l,t,r,b),Collections.emptyList(),"P"+id,id,false);}
    @Test public void labelEmphasisAndExclusionsDoNotChangeDetectionGeometryOrTapSelection()throws Exception {
        create(360,800,1.3f);DetectionOverlayView overlay=a.findViewById(R.id.detection_overlay);layout();
        OverlayItem plate=new OverlayItem(OverlayItem.Kind.PLATE,new RectF(.16f,.38f,.4f,.47f),Arrays.asList(new PointF(.16f,.38f),new PointF(.4f,.4f),new PointF(.38f,.47f),new PointF(.17f,.45f)),"tablica B7737 91%",99,false);
        List<OverlayItem> items=Arrays.asList(vehicle(9,.08f,.16f,.44f,.65f),vehicle(10,.46f,.25f,.72f,.66f),vehicle(11,.74f,.20f,.96f,.64f),plate);
        overlay.setItems(items,640,400);overlay.setVehicleEntityStates(Collections.singleton(9L),Collections.emptySet(),Collections.singletonMap(9L,new EntityRecognitionSnapshot(9,999,"B7737",.51,true,2)));
        List<RectF> before=overlay.snapshotRenderBoundsForTesting();
        overlay.setPresentedResultTrackId(999);assertTrue(overlay.primaryLabelForTesting(OverlayItem.Kind.VEHICLE,9));
        assertFalse(overlay.primaryLabelForTesting(OverlayItem.Kind.VEHICLE,10));assertTrue(overlay.vehicleLabelForTesting(9).contains("zap. MZ 51%"));
        List<RectF> blocked=new ArrayList<>();int[] origin=new int[2];overlay.getLocationOnScreen(origin);
        for(int id:new int[]{R.id.live_diagnostics_toggle,R.id.live_model_info_toggle,R.id.phone_orientation_panel,R.id.auto_zoom_control}){
            Rect r=bounds(a.findViewById(id));RectF block=new RectF(r);block.offset(-origin[0],-origin[1]);
            if(block.intersect(0,0,overlay.getWidth(),overlay.getHeight()))blocked.add(block);
        }
        overlay.setLabelExclusionRects(blocked);assertEquals(before,overlay.snapshotRenderBoundsForTesting());
        assertEquals(3,overlay.renderedKindCountForTesting(OverlayItem.Kind.VEHICLE));assertEquals(1,overlay.renderedKindCountForTesting(OverlayItem.Kind.PLATE));
        assertEquals(4,overlay.snapshotBadgeBoundsForTesting().size());
        for(RectF badge:overlay.snapshotBadgeBoundsForTesting())for(RectF r:blocked)assertFalse("badge covers controls: "+badge,RectF.intersects(badge,r));
        png("main_360_font13_badges");
        overlay.setPresentedResultTrackId(0);assertEquals(before,overlay.snapshotRenderBoundsForTesting());
        java.util.concurrent.atomic.AtomicLong tapped=new java.util.concurrent.atomic.AtomicLong();overlay.setVehicleTapListener(tapped::set);
        RectF target=before.get(0);MotionEvent down=MotionEvent.obtain(0,1,MotionEvent.ACTION_DOWN,target.centerX(),target.centerY(),0);
        MotionEvent up=MotionEvent.obtain(0,2,MotionEvent.ACTION_UP,target.centerX(),target.centerY(),0);
        try{assertTrue(overlay.onTouchEvent(down));assertTrue(overlay.onTouchEvent(up));assertEquals(9L,tapped.get());}finally{down.recycle();up.recycle();}
    }
}
