package com.example.alpr_v1.ui;

import android.graphics.*;
import android.os.*;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import com.example.alpr_v1.R;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.vision.Point2;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/** Native host layout and real MainActivity HUD listeners, including screen touch dispatch. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=android.app.Application.class,qualifiers="w360dp-h800dp-mdpi",shadows=TvBboxStartUiTest.CameraIo.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class HudCockpitFixHostTest {
    NonModalHudControlsTest controls;
    InstrumentPanelHostTest panel;
    @After public void close()throws Exception{if(controls!=null)controls.close();if(panel!=null)panel.close();}
    void activity()throws Exception{controls=new NonModalHudControlsTest();controls.setup();}
    void panel(int w,int h,float font)throws Exception{panel=new InstrumentPanelHostTest();panel.create(w,h,font);panel.on();}
    View view(int id){return controls!=null?controls.view(id):panel.view(id);}
    LiveHudView hud(){return (LiveHudView)view(R.id.live_hud_row);}
    Rect module(){return NonModalHudTest.local(hud(),view(R.id.hud1_module));}
    void layout(){if(controls!=null)controls.layout();else panel.layout();}
    void tap(int id){
        ViewGroup root=(ViewGroup)view(R.id.main);View button=view(id);assertTrue(button.isShown());assertTrue(button.isEnabled());
        Rect rect=NonModalHudTest.local(root,button);long time=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,rect.exactCenterX(),rect.exactCenterY(),0);
        MotionEvent up=MotionEvent.obtain(time,time+1,MotionEvent.ACTION_UP,rect.exactCenterX(),rect.exactCenterY(),0);
        try{assertTrue(root.dispatchTouchEvent(down));assertTrue(root.dispatchTouchEvent(up));}finally{down.recycle();up.recycle();}
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));layout();
    }
    void state(boolean first,boolean second){
        assertEquals(first,controls.presentation.diagnosticsExpanded());
        assertEquals(first?6:0,hud().visibleInstrumentCount());assertEquals(second,view(R.id.live_model_info).isShown());
        if(first){assertEquals(180,module().width());assertTrue(view(R.id.hud_camera_fps).isShown());assertTrue(view(R.id.hud_mz_time).isShown());}
        assertNull(org.robolectric.shadows.ShadowDialog.getLatestDialog());
    }
    Rect corner(int index){int w=180,h=module().height(),x=index%2==0?hud().getWidth()-8-w:8,y=index<2?8:hud().getHeight()-8-h;return new Rect(x,y,x+w,y+h);}
    void block(HudProtectedArea area,int index){Rect r=corner(index);float x=index%2==0?r.right-2:r.left+2,y=index<2?r.top+2:r.bottom-2;area.add(new RectF(x,y,x+1,y+1));}

    @Test public void firstScreenTouchesShowActualInstrumentsInBothOrders()throws Exception{
        activity();
        Rect original=NonModalHudTest.local((ViewGroup)view(R.id.main),view(R.id.camera_preview));
        state(false,false);tap(R.id.live_diagnostics_toggle);state(true,false);
        assertEquals("—",((TextView)view(R.id.hud_camera_fps)).getText().toString());
        tap(R.id.live_model_info_toggle);state(true,true);
        assertTrue(((TextView)view(R.id.hud_source_resolution)).getText().toString().contains("zadana"));
        tap(R.id.live_diagnostics_toggle);state(false,true);tap(R.id.live_model_info_toggle);state(false,false);
        tap(R.id.live_model_info_toggle);state(false,true);tap(R.id.live_diagnostics_toggle);state(true,true);
        assertEquals(original,NonModalHudTest.local((ViewGroup)view(R.id.main),view(R.id.camera_preview)));
        assertFalse(controls.f.sessions.experiment.isRunning());
    }
    @Test public void statusUpdatesDoNotUndoEitherHudSelection()throws Exception{
        activity();tap(R.id.live_diagnostics_toggle);tap(R.id.live_model_info_toggle);
        for(LivePresentationController.State next:new LivePresentationController.State[]{LivePresentationController.State.PREVIEW,LivePresentationController.State.STATIC_IDLE,LivePresentationController.State.ERROR,LivePresentationController.State.STOPPED}){
            controls.presentation.showState(next,"");controls.call("renderAnalysisControls");controls.call("renderHudModelInfo");layout();state(true,true);
        }
        assertFalse(controls.f.sessions.experiment.isRunning());
    }
    @Test public void fourCornersAreChosenInOrderAndSafeAnchorDoesNotJumpBack()throws Exception{
        panel(360,800,1);Rect camera=panel.rect(R.id.camera_preview);HudProtectedArea area=new HudProtectedArea();hud().setProtectedAreaSupplier(()->area);
        assertEquals(corner(0),module());
        for(int previous=0;previous<3;previous++){block(area,previous);layout();assertEquals(6,hud().visibleInstrumentCount());assertEquals(corner(previous+1),module());}
        hud().setProtectedAreaSupplier(HudProtectedArea::new);layout();assertEquals(corner(3),module());
        tap(R.id.live_diagnostics_toggle);tap(R.id.live_diagnostics_toggle);assertEquals(corner(0),module());
        assertEquals(camera,panel.rect(R.id.camera_preview));
    }
    @Test public void noSpaceNoticeHasFixedSlotAndRecoveryRequiresEightDp()throws Exception{
        panel(360,800,1.3f);Rect camera=panel.rect(R.id.camera_preview),strip=panel.rect(R.id.main_information_strip);
        final HudProtectedArea[] area={new HudProtectedArea()};for(int i=0;i<4;i++)block(area[0],i);hud().setProtectedAreaSupplier(()->area[0]);layout();
        assertEquals(0,hud().visibleInstrumentCount());assertTrue(view(R.id.hud1_blocked_notice).isShown());
        assertEquals("HUD 1: brak miejsca",((TextView)view(R.id.hud1_blocked_notice)).getText().toString());
        panel.save("fix_all_corners_blocked");
        Rect first=corner(0);area[0]=new HudProtectedArea();for(int i=1;i<4;i++)block(area[0],i);
        area[0].add(new RectF(first.right+6,first.top+10,first.right+7,first.top+20));layout();assertEquals(0,hud().visibleInstrumentCount());
        area[0]=new HudProtectedArea();for(int i=1;i<4;i++)block(area[0],i);layout();assertEquals(6,hud().visibleInstrumentCount());assertEquals(first,module());
        assertEquals(View.INVISIBLE,view(R.id.hud1_blocked_notice).getVisibility());
        assertEquals(camera,panel.rect(R.id.camera_preview));assertEquals(strip,panel.rect(R.id.main_information_strip));
        panel.save("fix_all_corners_recovered");
    }
    @Test public void portraitTvAndRealPlateLabelUseLowerFreeCornersWithoutMovingResults()throws Exception{
        panel(360,800,1);TvCalibrationOverlayView tv=(TvCalibrationOverlayView)view(R.id.tv_setup_overlay);
        int width=hud().getWidth(),height=hud().getHeight();CameraPreviewGeometry g=TvBboxCropTest.geometry(width,height,0,width,height,new double[]{0,0,width,height},width,height,1);
        tv.setGeometry(g);tv.setCorners(Arrays.asList(new Point2(.03f,.04f),new Point2(.97f,.04f),new Point2(.90f,.60f),new Point2(.1f,.60f)));tv.setVisibility(View.VISIBLE);layout();
        assertEquals(6,hud().visibleInstrumentCount());assertEquals(corner(2),module());
        Rect at=module();float l=(at.right-60f)/width,r=(at.right-20f)/width,t=(at.bottom-30f)/height,b=(at.bottom-10f)/height;
        RectF bounds=new RectF(l,t,r,b);OverlayItem plate=new OverlayItem(OverlayItem.Kind.PLATE,bounds,Arrays.asList(new PointF(l,t),new PointF(r,t),new PointF(r,b),new PointF(l,b)),"HOST PLATE",42,false);
        DetectionOverlayView detections=(DetectionOverlayView)view(R.id.detection_overlay);detections.setCameraGeometry(g);detections.setItems(Collections.singletonList(plate),width,height);
        List<RectF> before=detections.snapshotRenderBoundsForTesting();layout();assertEquals(6,hud().visibleInstrumentCount());assertEquals(corner(3),module());
        assertFalse(detections.hudProtectedArea().intersects(new RectF(module()),4));assertFalse(tv.hudProtectedArea().intersects(new RectF(module()),4));assertEquals(before,detections.snapshotRenderBoundsForTesting());
        panel.save("fix_portrait_tv_plate_lower_left");
    }
    @Test public void hudTwoIsAFramedInstrumentToTheRightOfLevelAndAboveThermalData()throws Exception{
        panel(320,720,1.3f);tap(R.id.live_model_info_toggle);
        Rect level=panel.rect(R.id.phone_orientation_panel),config=panel.rect(R.id.hud2_viewport),thermal=panel.rect(R.id.main_thermal_slot);
        assertNotNull(view(R.id.main_information_strip).getBackground());assertNotNull(view(R.id.tv_frame_summary).getBackground());assertNotNull(view(R.id.live_model_info).getBackground());
        assertTrue(config.left>level.right);assertTrue(config.bottom<=thermal.top);assertFalse(Rect.intersects(panel.rect(R.id.camera_preview),config));
        assertSame(view(R.id.main_data_column),view(R.id.hud2_viewport).getParent());
        for(int id:new int[]{R.id.hud_source_resolution,R.id.hud_mp_variant,R.id.hud_mt_variant,R.id.hud_mz_variant}){
            CharSequence text=((TextView)view(id)).getText();assertTrue(text instanceof Spanned);
            assertEquals(1,((Spanned)text).getSpans(0,text.length(),ForegroundColorSpan.class).length);
        }
        Rect before=panel.rect(R.id.main_information_strip);tap(R.id.live_model_info_toggle);assertFalse(view(R.id.live_model_info).isShown());assertEquals(before,panel.rect(R.id.main_information_strip));
    }
    @Test public void narrowPreviewHidesOnlyTelemetryAndRestoresItAfterResize()throws Exception{
        panel(360,800,1);hud().setProtectedAreaSupplier(HudProtectedArea::new);
        hud().measure(View.MeasureSpec.makeMeasureSpec(190,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY));hud().layout(0,0,190,300);
        assertEquals(0,hud().visibleInstrumentCount());assertTrue(hud().isSpaceBlocked());
        hud().requestLayout();layout();assertEquals(6,hud().visibleInstrumentCount());assertEquals(corner(0),module());
    }
}
