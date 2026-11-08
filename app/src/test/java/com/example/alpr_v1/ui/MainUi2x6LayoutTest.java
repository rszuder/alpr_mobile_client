package com.example.alpr_v1.ui;

import android.app.Application;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import com.example.alpr_v1.R;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.vision.Point2;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/** Production layout and Canvas on native Android host rendering, without a camera. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainUi2x6LayoutTest {
    MainUiReadabilityTest h;
    final int[][] buttons={{R.id.live_scene_mode_toggle,R.id.search_plate_button,R.id.release_target_button,R.id.auto_zoom_button,R.id.live_diagnostics_toggle,R.id.live_model_info_toggle},
            {R.id.analysis_start_button,R.id.collection_toggle,R.id.camera_preview_button,R.id.gallery_open_button,R.id.experiment_timer_button,R.id.export_report_button}};
    @After public void close(){if(h!=null)h.close();}
    void create(int w,int height,float font){
        h=new MainUiReadabilityTest();h.create(w,height,font);
        for(int[] row:buttons)for(int id:row)h.a.findViewById(id).setVisibility(View.VISIBLE);
        ((TextView)h.a.findViewById(R.id.gallery_open_button)).setText(R.string.camera_action_review);
        ((TextView)h.a.findViewById(R.id.collection_toggle)).setText(R.string.camera_action_crops_automatic);
        ((TextView)h.a.findViewById(R.id.gallery_count_badge)).setText("19");
        h.a.presentation.showState(LivePresentationController.State.STATIC_IDLE,"");h.a.presentation.showResult(9,"B7737",.26,true,2);
        h.a.presentation.showState(LivePresentationController.State.STATIC_IDLE,"");
        clock("2:34");h.layout();h.layout();
    }
    void clock(String value){
        h.a.findViewById(R.id.experiment_timer_status).setVisibility(View.VISIBLE);
        ((TextView)h.a.findViewById(R.id.experiment_timer_value)).setText(value);
        ((TextView)h.a.findViewById(R.id.experiment_timer_caption)).setText(R.string.main_timer_remaining);
    }
    Rect bounds(int id){return MainUiReadabilityTest.bounds(h.a.findViewById(id));}
    void grid(){
        Rect preview=bounds(R.id.camera_preview),top=bounds(R.id.normal_camera_hud);
        assertTrue(preview.height()>0);assertTrue(top.bottom<=preview.top);
        for(int row=0;row<2;row++){
            ViewGroup parent=h.a.findViewById(row==0?R.id.auto_zoom_control:R.id.camera_action_row);assertEquals(6,parent.getChildCount());
            int min=Integer.MAX_VALUE,max=0,last=-1;
            for(int i=0;i<6;i++){
                ViewGroup slot=(ViewGroup)parent.getChildAt(i);View button=h.a.findViewById(buttons[row][i]);
                assertSame(slot,button.getParent());min=Math.min(min,slot.getWidth());max=Math.max(max,slot.getWidth());
                assertTrue(button.getWidth()>=48);assertTrue(button.getHeight()>=48);
                Rect rect=bounds(buttons[row][i]);assertFalse(Rect.intersects(preview,rect));assertTrue(rect.top>=preview.bottom);
                assertTrue(last<=rect.left);last=rect.right;h.completeText(buttons[row][i]);
                TextView caption=(TextView)button;int intended=caption.getText().toString().split("\n",-1).length;
                assertTrue("broken word: "+caption.getText()+" width="+caption.getWidth()+" padding="+caption.getCompoundPaddingLeft()+","+caption.getCompoundPaddingRight()+" glyph="+caption.getPaint().measureText(caption.getText().toString()),caption.getLineCount()<=intended);
            }assertTrue("equal slot width",max-min<=1);
        }
        Rect first=bounds(R.id.auto_zoom_control),second=bounds(R.id.camera_action_row);
        assertTrue(second.top-first.bottom<=4);assertTrue(second.top>=first.bottom);
        assertEquals(first.left,second.left);assertEquals(first.right,second.right);
        for(int id:new int[]{R.id.top_app_bar,R.id.live_status_strip,R.id.tv_frame_summary,R.id.main_device_result_row,R.id.phone_orientation_panel,R.id.confirmed_result_tray})
            assertFalse("control on preview "+id,Rect.intersects(preview,bounds(id)));
        assertEquals(1,count(h.a.findViewById(R.id.main),R.id.live_diagnostics_toggle));assertEquals(1,count(h.a.findViewById(R.id.main),R.id.live_model_info_toggle));
        assertFalse(Rect.intersects(bounds(R.id.live_status),bounds(R.id.experiment_timer_status)));
        com.google.android.material.appbar.MaterialToolbar toolbar=h.a.findViewById(R.id.top_app_bar);
        for(int i=0;i<toolbar.getChildCount();i++)if(toolbar.getChildAt(i) instanceof TextView){
            TextView title=(TextView)toolbar.getChildAt(i);
            if(title.getText().toString().equals(toolbar.getTitle().toString())){
                assertEquals("toolbar title",0,title.getLayout().getEllipsisCount(0));
                int panel=androidx.core.graphics.ColorUtils.compositeColors(h.a.getColor(R.color.alpr_panel),Color.BLACK);
                assertTrue("toolbar contrast",androidx.core.graphics.ColorUtils.calculateContrast(title.getCurrentTextColor(),panel)>=4.5);
            }
        }
        h.completeText(R.id.live_status);h.completeText(R.id.live_status_secondary);h.completeText(R.id.experiment_timer_value);
        h.completeText(R.id.experiment_timer_caption);h.completeText(R.id.confirmed_result_confidence);
    }
    static int count(View v,int id){int result=v.getId()==id?1:0;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)result+=count(g.getChildAt(i),id);}return result;}
    void png(String name)throws Exception{
        String directory=System.getenv("F5A_2X6_EVIDENCE");if(directory==null)return;
        java.nio.file.Path dir=Paths.get(directory);Files.createDirectories(dir);
        Bitmap image=Bitmap.createBitmap(h.width,h.height,Bitmap.Config.ARGB_8888);h.a.getWindow().getDecorView().draw(new Canvas(image));
        try(OutputStream stream=Files.newOutputStream(dir.resolve(name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{image.recycle();}
        StringBuilder row=new StringBuilder(name+"\t"+h.width+"\t"+h.height+"\t"+h.scale+"\t"+bounds(R.id.camera_preview));
        for(int[] group:buttons)for(int id:group)row.append('\t').append(bounds(id));row.append('\n');
        Files.write(dir.resolve("layout_rectangles.tsv"),row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    void matrix(int w,int height,float font)throws Exception{create(w,height,font);h.confirmTv();grid();png("grid_"+w+"_"+height+"_font"+Math.round(font*10));}
    @Test public void portrait320Font10()throws Exception{matrix(320,720,1);}
    @Test public void portrait320Font13()throws Exception{matrix(320,720,1.3f);}
    @Test public void portrait360Font10()throws Exception{matrix(360,800,1);}
    @Test public void portrait360Font13()throws Exception{matrix(360,800,1.3f);}
    @Test public void landscape720Font10()throws Exception{matrix(720,360,1);}
    @Test public void landscape720Font13()throws Exception{matrix(720,360,1.3f);}
    @Test public void hiddenDisabledAndClockValuesNeverMoveSlotsOrPreview(){
        create(360,800,1.3f);Rect camera=bounds(R.id.camera_preview),gallery=bounds(R.id.main_slot_gallery),start=bounds(R.id.main_slot_start);
        for(String time:new String[]{"2:33","0:00","1:00:00","596523:14:07"}){clock(time);h.layout();assertEquals(camera,bounds(R.id.camera_preview));}
        for(int id:new int[]{R.id.experiment_timer_button,R.id.export_report_button,R.id.live_diagnostics_toggle}){
            h.a.findViewById(id).setVisibility(View.GONE);h.layout();assertEquals(camera,bounds(R.id.camera_preview));assertEquals(gallery,bounds(R.id.main_slot_gallery));assertEquals(start,bounds(R.id.main_slot_start));
        }
        h.a.findViewById(R.id.collection_toggle).setEnabled(false);h.a.findViewById(R.id.experiment_timer_status).setVisibility(View.GONE);h.a.presentation.clearResult();h.layout();assertEquals(camera,bounds(R.id.camera_preview));
    }
    @Test public void gestureAndThreeButtonInsetsKeepControlsOutsidePreview(){
        create(360,800,1.3f);View root=h.a.findViewById(R.id.main);CameraWindowUi.install(h.a.getWindow(),root);
        for(int bottom:new int[]{24,48}){
            int type=WindowInsetsCompat.Type.systemBars();Insets bars=Insets.of(0,24,0,bottom);
            h.layout();
            // Explicit OS input: Robolectric does not render a real gesture/three-button system bar.
            ViewCompat.dispatchApplyWindowInsets(root,new WindowInsetsCompat.Builder().setInsetsIgnoringVisibility(type,bars).setInsets(type,bars).setVisible(type,true).build());
            root.measure(View.MeasureSpec.makeMeasureSpec(h.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h.height,View.MeasureSpec.EXACTLY));root.layout(0,0,h.width,h.height);
            assertEquals(bottom,root.getPaddingBottom());grid();
            assertTrue(bounds(R.id.camera_action_row).bottom<=h.height-bottom);
        }
    }
    @Test public void hudDialogsKeepTheViewportAndRestoreTheirOriginalViews()throws Exception{
        create(360,800,1.3f);Rect camera=bounds(R.id.camera_preview);
        View hud=h.a.findViewById(R.id.live_hud_row),model=h.a.findViewById(R.id.live_model_info);
        android.view.ViewParent hudParent=hud.getParent(),modelParent=model.getParent();
        h.a.findViewById(R.id.live_diagnostics_toggle).performClick();h.layout();
        assertTrue(hud.isShown());assertFalse(model.isShown());assertNull(ShadowDialog.getLatestDialog());
        assertNull(h.a.findViewById(R.id.hud_mz_score_legend));assertEquals(camera,bounds(R.id.camera_preview));
        assertTrue(h.a.presentation.toggleModelInfo());h.layout();assertTrue(hud.isShown());assertTrue(model.isShown());
        assertSame(hudParent,hud.getParent());assertSame(modelParent,model.getParent());assertEquals(camera,bounds(R.id.camera_preview));
        h.a.findViewById(R.id.live_diagnostics_toggle).performClick();h.layout();assertFalse(hud.isShown());assertTrue(model.isShown());
        assertFalse(h.a.presentation.toggleModelInfo());h.layout();assertFalse(model.isShown());assertEquals(camera,bounds(R.id.camera_preview));
        assertNull(ShadowDialog.getLatestDialog());
    }
    @Test public void resizedPreviewMapsAllCornersAndPreservesTheTrapezoid()throws Exception{
        create(360,800,1.3f);Rect camera=bounds(R.id.camera_preview);DetectionOverlayView overlay=h.a.findViewById(R.id.detection_overlay);
        overlay.setPreviewSourceSize(1920,1080);overlay.setAnalysisViewportEnabled(true);
        overlay.setCameraGeometry(TvBboxCropTest.geometry(1920,1080,0,1920,1080,new double[]{0,0,1920,1080},camera.width(),camera.height(),1));
        overlay.setItems(Collections.emptyList(),1920,1080);
        assertNotNull(overlay.analysisViewportBoundsForTesting());
        Bitmap blank=Bitmap.createBitmap(overlay.getWidth(),overlay.getHeight(),Bitmap.Config.ARGB_8888);
        try{overlay.draw(new Canvas(blank));for(int y=0;y<blank.getHeight();y+=3)for(int x=0;x<blank.getWidth();x+=3)assertEquals("decorative shading",0,Color.alpha(blank.getPixel(x,y)));}finally{blank.recycle();}
        List<Point2> points=Arrays.asList(new Point2(.12f,.18f),new Point2(.83f,.24f),new Point2(.76f,.70f),new Point2(.19f,.65f));
        for(int rotation:new int[]{0,90,180,270}){
            int w=rotation%180==0?1760:960,height=rotation%180==0?960:1760;
            CameraPreviewGeometry g=TvBboxCropTest.geometry(w,height,rotation,1920,1080,new double[]{80,60,1840,1020},camera.width(),camera.height(),1.5f);
            TvSetupCalibration calibration=TvSetupCalibration.confirm(points,g,1,"2026-11-07T12:00:00Z");
            for(Point2 p:points){double[] xy=g.normalizedToView(p.x,p.y),back=g.viewToNormalized(xy[0],xy[1]);assertEquals((double)p.x*w,back[0]*w,1e-6);assertEquals((double)p.y*height,back[1]*height,1e-6);}
            CameraPreviewGeometry changed=TvBboxCropTest.geometry(w,height,rotation,1920,1080,new double[]{80,60,1840,1020},camera.width(),camera.height()-20,1.5f);
            assertEquals("STALE",calibration.status(changed));
            overlay.setCameraGeometry(g);
            List<PointF> pose=new ArrayList<>();for(Point2 p:points)pose.add(new PointF(p.x,p.y));
            OverlayItem plate=new OverlayItem(OverlayItem.Kind.PLATE,new RectF(.12f,.18f,.83f,.70f),pose,"MT Pose 91%",99,false);
            overlay.setItems(Arrays.asList(MainUiReadabilityTest.vehicle(9,.08f,.14f,.90f,.80f),plate),w,height);
            assertEquals(1,overlay.renderedKindCountForTesting(OverlayItem.Kind.PLATE));assertEquals(1,overlay.renderedKindCountForTesting(OverlayItem.Kind.VEHICLE));
            java.lang.reflect.Field rendered=DetectionOverlayView.class.getDeclaredField("renderItems");rendered.setAccessible(true);
            int matched=0;
            for(Object item:(List<?>)rendered.get(overlay)){
                java.lang.reflect.Field vertices=item.getClass().getDeclaredField("points");vertices.setAccessible(true);
                List<?> actual=(List<?>)vertices.get(item);if(actual.isEmpty())continue;assertEquals(4,actual.size());
                for(int i=0;i<4;i++){PointF pixel=(PointF)actual.get(i);double[] expected=g.normalizedToView(points.get(i).x,points.get(i).y);assertEquals(expected[0],pixel.x,.001);assertEquals(expected[1],pixel.y,.001);}matched++;
            }assertEquals(1,matched);
            List<Point2> outer=Arrays.asList(new Point2(.05f,.1f),new Point2(.95f,.15f),new Point2(.9f,.9f),new Point2(.1f,.85f));
            MainUiReadabilityTest.set(h.a.tv,"geometry",g);MainUiReadabilityTest.set(h.a.tv,"calibration",TvSetupCalibration.confirm(outer,g,1,"2026-11-07T12:00:00Z"));
            h.a.tv.onGeometry(g,null,"SYNTHETIC_HOST");h.layout();
            assertEquals(camera,bounds(R.id.tv_setup_overlay));
            if(rotation==0)png("grid_360_font13_synthetic_geometry");
        }
    }
}
