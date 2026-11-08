package com.example.alpr_v1.ui;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.ColorUtils;
import com.example.alpr_v1.R;
import com.example.alpr_v1.metrics.*;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.vision.Point2;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=android.app.Application.class,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class InstrumentPanelHostTest {
    MainUiReadabilityTest h;
    @After public void close(){if(h!=null)h.close();}
    void create(int w,int height,float font)throws Exception{
        if(h!=null)h.close();h=new MainUiReadabilityTest();h.create(w,height,font);h.confirmTv();
        h.a.findViewById(R.id.live_model_info_toggle).setOnClickListener(v->h.a.presentation.toggleModelInfo());
        CameraPreviewGeometry g=(CameraPreviewGeometry)TvBboxStartUiTest.get(h.a.tv,"geometry");
        List<Point2> points=Arrays.asList(new Point2(.12f,.42f),new Point2(.85f,.38f),new Point2(.88f,.80f),new Point2(.18f,.83f));
        TvBboxStartUiTest.set(h.a.tv,"calibration",TvSetupCalibration.confirm(points,g,1,"2026-11-07T10:00:00Z"));
        h.a.tv.onGeometry(g,null,"SYNTHETIC_HOST");
        CockpitHudHostTest data=new CockpitHudHostTest();data.h=h;data.values();layout();
    }
    View view(int id){return h.a.findViewById(id);}
    LiveHudView hud(){return (LiveHudView)view(R.id.live_hud_row);}
    Rect rect(int id){return NonModalHudTest.local((ViewGroup)view(R.id.main),view(id));}
    Rect local(int id){return NonModalHudTest.local(hud(),view(id));}
    void layout(){h.layout();view(R.id.main).getViewTreeObserver().dispatchOnPreDraw();}
    void on(){if(!h.a.presentation.diagnosticsExpanded())view(R.id.live_diagnostics_toggle).performClick();layout();}

    @Test public void allWindowsHaveOneFixedTwoByThreeModuleAndConstantCamera()throws Exception{
        for(int[] window:new int[][]{{320,720},{360,800},{720,360}})for(float font:new float[]{1,1.3f}){
            create(window[0],window[1],font);Rect camera=rect(R.id.camera_preview),controls=rect(R.id.control_panel);
            on();view(R.id.live_diagnostics_toggle).performClick();layout();
            assertEquals(camera,rect(R.id.camera_preview));
            for(int[] state:new int[][]{{0,0},{1,0},{1,1},{0,1}}){
                if(h.a.presentation.diagnosticsExpanded()!=(state[0]==1))view(R.id.live_diagnostics_toggle).performClick();
                if((view(R.id.live_model_info).getVisibility()==View.VISIBLE)!=(state[1]==1))view(R.id.live_model_info_toggle).performClick();
                layout();assertEquals(camera,rect(R.id.camera_preview));assertEquals(controls,rect(R.id.control_panel));
                Rect module=local(R.id.hud1_module);assertEquals(180,module.width());assertEquals(font==1?80:96,module.height());
                assertEquals(camera.width()-188,module.left);assertEquals(8,module.top);
                assertEquals(state[0]==1?6:0,hud().visibleInstrumentCount());
                assertNull(org.robolectric.shadows.ShadowDialog.getLatestDialog());
                if(state[0]==1){
                    assertNotNull(view(R.id.hud1_module).getBackground());
                    int[] ids=CockpitHudHostTest.BLOCKS;
                    for(int i=0;i<ids.length;i++){
                        Rect cell=local(ids[i]);assertEquals(module.left+(i%3)*60,cell.left);assertEquals(module.top+(i/3)*module.height()/2,cell.top);
                        assertEquals(60,cell.width());assertEquals(module.height()/2,cell.height());assertNull(view(ids[i]).getBackground());
                    }
                }
                assertEquals(window[0]>window[1]?48:56,view(R.id.phone_orientation_panel).getWidth());
                assertFalse(Rect.intersects(camera,rect(R.id.hud2_viewport)));
                save("instruments_"+window[0]+"_"+window[1]+"_font"+Math.round(font*10)+"_hud"+state[0]+state[1]);
            }
        }
    }
    @Test public void changingValuesAndUnitsNeverMoveOrResizeCells()throws Exception{
        create(320,720,1.3f);on();Rect module=local(R.id.hud1_module),camera=rect(R.id.camera_preview);List<Rect> cells=new ArrayList<>();
        for(int id:CockpitHudHostTest.BLOCKS)cells.add(local(id));
        for(long duration:new long[]{0,250_000,85_000_000,1_250_000_000,3_600_000_000_000L,Long.MAX_VALUE}){
            MetricsCollector m=new MetricsCollector();m.startMeasurementSession();InferenceTrace t=new InferenceTrace(7);t.putDurationNanos("character_inference",duration);m.add(t);
            hud().clearMetrics();hud().render(m.liveSnapshot(),"",false,99999.9);hud().renderDeviceMetrics(-14.2);layout();
            assertEquals(module,local(R.id.hud1_module));assertEquals(camera,rect(R.id.camera_preview));
            for(int i=0;i<cells.size();i++)assertEquals(cells.get(i),local(CockpitHudHostTest.BLOCKS[i]));
            for(int id:new int[]{R.id.hud_camera_fps,R.id.hud_temperature,R.id.hud_resources,R.id.hud_mp_time,R.id.hud_mt_time,R.id.hud_mz_time}){
                TextView value=(TextView)view(id);assertTrue(value.getText().length()<=5);assertEquals(0,value.getLayout().getEllipsisCount(0));
            }
        }
        assertTrue(view(R.id.hud_mz_time).getContentDescription().toString().contains("suma inferencji"));
        assertTrue(view(R.id.hud_temperature).getContentDescription().toString().contains("-14,2°C"));
    }
    @Test public void anyCollisionHidesTheWholeModuleWithSpatialHysteresis()throws Exception{
        create(360,800,1);on();Rect anchor=local(R.id.hud1_module);final HudProtectedArea[] area={new HudProtectedArea()};hud().setProtectedAreaSupplier(()->area[0]);
        // All four anchors must be occupied before the complete group is hidden.
        for(int x:new int[]{10,hud().getWidth()-10})for(int y:new int[]{10,hud().getHeight()-10})area[0].add(new RectF(x,y,x+1,y+1));
        layout();assertEquals(0,hud().visibleInstrumentCount());
        for(int id:CockpitHudHostTest.BLOCKS)assertFalse(view(id).isShown());assertTrue(h.a.presentation.diagnosticsExpanded());
        assertFalse(view(R.id.live_diagnostics_toggle).isActivated());assertEquals(anchor,local(R.id.hud1_module));
        area[0]=new HudProtectedArea();
        for(int[] point:new int[][]{{10,10},{10,hud().getHeight()-10},{hud().getWidth()-10,hud().getHeight()-10}})area[0].add(new RectF(point[0],point[1],point[0]+1,point[1]+1));
        area[0].add(new RectF(anchor.right+6,anchor.top+10,anchor.right+7,anchor.top+20));layout();assertEquals(0,hud().visibleInstrumentCount());
        area[0]=new HudProtectedArea();layout();assertEquals(6,hud().visibleInstrumentCount());assertEquals(anchor,local(R.id.hud1_module));
    }
    @Test public void differentRealTvOutlinesAndPlateLabelsHavePriority()throws Exception{
        create(320,720,1.3f);on();Rect anchor=local(R.id.hud1_module);TvCalibrationOverlayView tv=(TvCalibrationOverlayView)view(R.id.tv_setup_overlay);
        for(double y:new double[]{.02,.42,.05,.48}){
            tv.setCorners(Arrays.asList(new Point2(.05f,(float)y),new Point2(.95f,(float)y),new Point2(.9f,.85f),new Point2(.1f,.85f)));layout();
            if(hud().visibleInstrumentCount()>0)assertFalse(tv.hudProtectedArea().intersects(new RectF(local(R.id.hud1_module)),4));
        }
        tv.setVisibility(View.INVISIBLE);
        DetectionOverlayView overlay=(DetectionOverlayView)view(R.id.detection_overlay);
        CameraPreviewGeometry g=(CameraPreviewGeometry)TvBboxStartUiTest.get(h.a.tv,"geometry");
        double[] a=g.viewToNormalized(anchor.left+30,anchor.bottom-12),b=g.viewToNormalized(anchor.right-20,anchor.bottom-2);
        RectF normalized=new RectF((float)a[0],(float)a[1],(float)b[0],(float)b[1]);
        OverlayItem plate=new OverlayItem(OverlayItem.Kind.PLATE,normalized,Arrays.asList(new PointF(normalized.left,normalized.top),new PointF(normalized.right,normalized.top),new PointF(normalized.right,normalized.bottom),new PointF(normalized.left,normalized.bottom)),"HOST TEST",91,false);
        overlay.setItems(Collections.singletonList(plate),g.sourceWidth,g.sourceHeight);List<RectF> before=overlay.snapshotRenderBoundsForTesting();layout();
        assertEquals(6,hud().visibleInstrumentCount());assertEquals(before,overlay.snapshotRenderBoundsForTesting());
        assertFalse(overlay.hudProtectedArea().intersects(new RectF(local(R.id.hud1_module)),4));
        overlay.setItems(Collections.emptyList(),g.sourceWidth,g.sourceHeight);layout();assertEquals(6,hud().visibleInstrumentCount());
    }
    @Test public void labelsAndValuesHaveContrastOnWhiteAndBlackCamera()throws Exception{
        create(360,800,1);on();Rect module=local(R.id.hud1_module);
        for(int background:new int[]{Color.WHITE,Color.BLACK}){
            Bitmap bitmap=Bitmap.createBitmap(hud().getWidth(),hud().getHeight(),Bitmap.Config.ARGB_8888);
            try{Canvas c=new Canvas(bitmap);c.drawColor(background);hud().draw(c);int actual=bitmap.getPixel(module.left+6,module.top+6);
                assertTrue(ColorUtils.calculateContrast(h.a.getColor(R.color.cockpit_cyan),actual)>=4.5);
                assertTrue(ColorUtils.calculateContrast(Color.WHITE,actual)>=4.5);
            }finally{bitmap.recycle();}
        }
    }
    @Test public void thermalActionRemainsClickableAndResultMetadataRemainsAccessible()throws Exception{
        create(360,800,1.3f);final int[] clicks={0};View thermal=view(R.id.experiment_thermal_button);
        thermal.setVisibility(View.VISIBLE);view(R.id.main_device_status).setVisibility(View.GONE);
        ((TextView)thermal).setText("BAT 36,2°C · TH0 · HEAD —");thermal.setOnClickListener(v->clicks[0]++);layout();
        assertTrue(thermal.performClick());assertEquals(1,clicks[0]);assertTrue(thermal.isShown());
        assertTrue(((TextView)thermal).getLayout().getHeight()<=thermal.getHeight());
        assertEquals(1,((TextView)view(R.id.confirmed_result_text)).getLineCount());assertEquals(View.GONE,view(R.id.confirmed_result_meta).getVisibility());
        assertTrue(view(R.id.confirmed_result_tray).getContentDescription().toString().contains("2 trafienia"));
    }
    void save(String name)throws Exception{
        String target=System.getenv("F5A_COCKPIT_EVIDENCE");if(target==null)return;java.nio.file.Path folder=Paths.get(target);Files.createDirectories(folder);
        Bitmap image=Bitmap.createBitmap(h.width,h.height,Bitmap.Config.ARGB_8888);try{h.a.getWindow().getDecorView().draw(new Canvas(image));try(OutputStream out=Files.newOutputStream(folder.resolve(name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}}finally{image.recycle();}
        String line=name+"\t"+rect(R.id.camera_preview)+"\t"+local(R.id.hud1_module)+"\t"+rect(R.id.hud2_viewport)+"\t"+rect(R.id.main_information_strip)+"\t"+hud().visibleInstrumentCount()+"\n";
        Files.write(folder.resolve("instruments_rectangles.tsv"),line.getBytes(java.nio.charset.StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
}
