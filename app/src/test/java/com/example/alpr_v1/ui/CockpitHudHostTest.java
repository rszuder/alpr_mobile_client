package com.example.alpr_v1.ui;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.example.alpr_v1.R;
import com.example.alpr_v1.camera.PhoneOrientationEstimator;
import com.example.alpr_v1.metrics.*;
import com.example.alpr_v1.pipeline.ModelRuntimeSummary;
import com.google.android.material.button.MaterialButton;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/** Actual Android layout/Canvas with synthetic inputs. No CameraX IO, device or model execution. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=android.app.Application.class,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class CockpitHudHostTest {
    static final int[] BLOCKS={R.id.cockpit_fps_block,R.id.cockpit_temperature_block,R.id.cockpit_resources_block,
            R.id.cockpit_mp_block,R.id.cockpit_mt_block,R.id.cockpit_mz_block};
    MainUiReadabilityTest h;
    @After public void close(){if(h!=null)h.close();}
    void create(int w,int height,float font)throws Exception{
        h=new MainUiReadabilityTest();h.create(w,height,font);
        h.a.findViewById(R.id.live_model_info_toggle).setOnClickListener(v->h.a.presentation.toggleModelInfo());
        h.confirmTv();
        h.a.tv.onGeometry((com.example.alpr_v1.setup.CameraPreviewGeometry)TvBboxStartUiTest.get(h.a.tv,"geometry"),null,"SYNTHETIC_HOST");
        h.layout();
    }
    View view(int id){return h.a.findViewById(id);}
    String text(int id){return ((TextView)view(id)).getText().toString();}
    LiveHudView hud(){return (LiveHudView)view(R.id.live_hud_row);}
    Rect bounds(int id){return MainUiReadabilityTest.bounds(view(id));}
    void layout(){h.layout();view(R.id.main).getViewTreeObserver().dispatchOnPreDraw();}
    void toggle(int id){assertTrue(view(id).performClick());layout();}
    void values(){
        MetricsCollector m=new MetricsCollector();m.startMeasurementSession();InferenceTrace t=new InferenceTrace(2);
        t.putDurationNanos("vehicle_inference",85_000_000L);t.putDurationNanos("plate_inference",200_000_000L);t.putDurationNanos("character_inference",420_000_000L);m.add(t);
        hud().clearMetrics();hud().render(m.liveSnapshot(),"ignored by HUD 1",false,29.5);hud().resetDeviceSampling();hud().renderDeviceMetrics(36.2);
        ((LiveModelInfoView)view(R.id.live_model_info)).render(Arrays.asList(
                new ModelRuntimeSummary("MP","Vehicle model","ncnn_fp32","FP32","NCNN/CPU"),
                new ModelRuntimeSummary("MT","Plate model","tflite_fp32","FP32","LiteRT/CPU"),
                new ModelRuntimeSummary("MZ","Character model","tflite_fp32","FP32","LiteRT/CPU")),"Kadr: 1280×960");
        ((PhoneLevelView)view(R.id.phone_orientation_panel)).render(orientation(0,0));
        h.a.presentation.showResult(99,"B7737",.26,true,2);h.a.presentation.showState(LivePresentationController.State.STATIC_IDLE,"");
        ((TextView)view(R.id.main_device_status)).setText("BAT 36,2°C · TH0 · HEAD —");
    }
    void matrix(int w,int height,float font)throws Exception{
        create(w,height,font);values();layout();Rect camera=bounds(R.id.camera_preview);
        for(int[] state:new int[][]{{0,0},{1,0},{1,1},{0,1}}){
            if(h.a.presentation.diagnosticsExpanded()!=(state[0]==1))toggle(R.id.live_diagnostics_toggle);
            if((view(R.id.live_model_info).getVisibility()==View.VISIBLE)!=(state[1]==1))toggle(R.id.live_model_info_toggle);
            layout();assertEquals(camera,bounds(R.id.camera_preview));
            assertEquals(camera,bounds(R.id.detection_overlay));assertEquals(camera,bounds(R.id.tv_setup_overlay));
            assertEquals(state[0]==1,view(R.id.live_hud_row).isShown());assertEquals(state[1]==1,view(R.id.live_model_info).isShown());
            assertFalse(Rect.intersects(camera,bounds(R.id.hud2_viewport)));
            assertTrue(bounds(R.id.hud2_viewport).top>=camera.bottom);
            assertNull(org.robolectric.shadows.ShadowDialog.getLatestDialog());
            for(int id:BLOCKS)if(view(id).isShown()){
                assertTrue(camera.contains(bounds(id)));
                ViewGroup host=(ViewGroup)view(R.id.main_hud_storage);Rect local=NonModalHudTest.local(host,view(id));
                DetectionOverlayView detections=(DetectionOverlayView)view(R.id.detection_overlay);
                assertFalse(detections.hudProtectedArea().intersects(new RectF(local),0));
                assertFalse(((TvCalibrationOverlayView)view(R.id.tv_setup_overlay)).hudProtectedArea().intersects(new RectF(local),0));
            }
            for(int id:new int[]{R.id.hud_source_resolution,R.id.hud_mp_variant,R.id.hud_mt_variant,R.id.hud_mz_variant})
                if(view(id).isShown())assertTrue(((TextView)view(id)).getLayout().getHeight()<=view(id).getHeight());
            save("cockpit_"+w+"_"+height+"_font"+Math.round(font*10)+"_hud"+state[0]+state[1]);
        }
    }
    @Test public void portrait320Font10()throws Exception{matrix(320,720,1);}
    @Test public void portrait320Font13()throws Exception{matrix(320,720,1.3f);}
    @Test public void portrait360Font10()throws Exception{matrix(360,800,1);}
    @Test public void portrait360Font13()throws Exception{matrix(360,800,1.3f);}
    @Test public void landscape720Font10()throws Exception{matrix(720,360,1);}
    @Test public void landscape720Font13()throws Exception{matrix(720,360,1.3f);}

    @Test public void telemetryHasCorrectUnitsMissingDataAndHonestZeroHandling()throws Exception{
        create(360,800,1);MetricsCollector m=new MetricsCollector();m.startMeasurementSession();
        hud().clearMetrics();hud().render(m.liveSnapshot(),"",true,Double.NaN);
        for(int id:new int[]{R.id.hud_camera_fps,R.id.hud_mp_time,R.id.hud_mt_time,R.id.hud_mz_time})assertEquals("—",text(id));
        hud().resetDeviceSampling();hud().renderDeviceMetrics(36.2);assertEquals("36,2",text(R.id.hud_temperature));assertEquals("—",text(R.id.hud_resources));
        assertTrue(view(R.id.hud_temperature).getContentDescription().toString().contains("baterii"));
        assertTrue(view(R.id.hud_resources).getContentDescription().toString().contains("CPU aplikacji"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1001));hud().renderDeviceMetrics(Double.NaN);assertEquals("—",text(R.id.hud_temperature));assertEquals("0",text(R.id.hud_resources));
        InferenceTrace trace=new InferenceTrace(2);trace.putDurationNanos("vehicle_inference",0);trace.putDurationNanos("plate_inference",250_000);trace.putDurationNanos("character_inference",1_250_000_000L);m.add(trace);
        hud().clearMetrics();hud().render(m.liveSnapshot(),"",false,0);
        assertEquals("0,0",text(R.id.hud_camera_fps));assertEquals("—",text(R.id.hud_mp_time));assertEquals("<1",text(R.id.hud_mt_time));assertEquals("1,25",text(R.id.hud_mz_time));
        assertTrue(view(R.id.hud_mt_time).getContentDescription().toString().contains("suma inferencji"));
        TextView digits=(TextView)view(R.id.hud_mz_time);assertEquals(digits.getPaint().measureText("111"),digits.getPaint().measureText("888"),.01f);
        values();assertEquals("85",text(R.id.hud_mp_time));assertEquals("200",text(R.id.hud_mt_time));assertEquals("420",text(R.id.hud_mz_time));
        assertNull(view(R.id.hud_mz_score_legend));assertNull(view(R.id.hud1_context));assertNull(view(R.id.hud2_context));
    }
    @Test public void modelInfoUsesOpenedBackendAndClearsMissingValues()throws Exception{
        create(320,720,1.3f);LiveModelInfoView model=(LiveModelInfoView)view(R.id.live_model_info);
        model.render(Collections.singletonList(new ModelRuntimeSummary("MT","Actual model","actual_variant_42","FP16","LiteRT/GPU","UNVERIFIED")),"Kadr: 640×400 · zadana");
        assertTrue(text(R.id.hud_mt_variant).contains("GPU"));assertTrue(text(R.id.hud_mt_variant).contains("FP16"));
        assertTrue(view(R.id.hud_mt_variant).getContentDescription().toString().contains("actual_variant_42"));assertTrue(text(R.id.hud_source_resolution).contains("zadana"));
        assertEquals("MP · —",text(R.id.hud_mp_variant));model.render(Collections.emptyList(),"Kadr: —");assertEquals("MT · —",text(R.id.hud_mt_variant));
        assertFalse(view(R.id.hud_mt_variant).getContentDescription().toString().contains("GPU"));
    }
    @Test public void collisionHidesOnlyBlockedInstrumentsAndRestoresRequestedHud()throws Exception{
        create(360,800,1);values();toggle(R.id.live_diagnostics_toggle);assertEquals(6,hud().visibleInstrumentCount());
        Rect camera=bounds(R.id.camera_preview);HudProtectedArea blocked=new HudProtectedArea();
        View tmp=view(R.id.cockpit_temperature_block);Rect location=NonModalHudTest.local(hud(),tmp);blocked.add(new RectF(location.left+4,0,location.right-4,hud().getHeight()));
        hud().setProtectedAreaSupplier(()->blocked);layout();
        assertEquals("A free corner keeps the whole group visible",6,hud().visibleInstrumentCount());
        assertFalse(blocked.intersects(new RectF(NonModalHudTest.local(hud(),view(R.id.hud1_module))),4));
        assertTrue(h.a.presentation.diagnosticsExpanded());
        HudProtectedArea full=new HudProtectedArea();full.add(new RectF(0,0,hud().getWidth(),hud().getHeight()));hud().setProtectedAreaSupplier(()->full);layout();
        assertEquals(0,hud().visibleInstrumentCount());assertTrue(h.a.presentation.diagnosticsExpanded());
        assertFalse(((MaterialButton)view(R.id.live_diagnostics_toggle)).isActivated());
        assertTrue(view(R.id.live_diagnostics_toggle).getStateDescription().toString().contains("czasowo ukryty"));
        hud().setProtectedAreaSupplier(HudProtectedArea::new);layout();assertEquals(6,hud().visibleInstrumentCount());
        assertTrue(((MaterialButton)view(R.id.live_diagnostics_toggle)).isActivated());assertEquals(camera,bounds(R.id.camera_preview));
    }
    @Test public void actualDetectionsKeepPriorityWithoutMovingTheirGeometry()throws Exception{
        create(360,800,1);values();toggle(R.id.live_diagnostics_toggle);
        DetectionOverlayView overlay=(DetectionOverlayView)view(R.id.detection_overlay);
        Rect block=NonModalHudTest.local(hud(),view(R.id.cockpit_resources_block));float w=overlay.getWidth(),height=overlay.getHeight();
        float l=(block.left-2)/w,t=Math.max(0,(block.top-2)/height),r=Math.min(1,(block.right+2)/w),b=(block.bottom+2)/height;
        OverlayItem plate=new OverlayItem(OverlayItem.Kind.PLATE,new RectF(l,t,r,b),Arrays.asList(new PointF(l,t),new PointF(r,t),new PointF(r,b),new PointF(l,b)),"B7737 91%",42,false);
        overlay.setItems(Collections.singletonList(plate),(int)w,(int)height);List<RectF> before=overlay.snapshotRenderBoundsForTesting();layout();
        assertEquals(6,hud().visibleInstrumentCount());
        assertFalse(overlay.hudProtectedArea().intersects(new RectF(NonModalHudTest.local(hud(),view(R.id.hud1_module))),4));
        toggle(R.id.live_model_info_toggle);toggle(R.id.live_diagnostics_toggle);assertEquals(before,overlay.snapshotRenderBoundsForTesting());assertEquals(1,overlay.renderedKindCountForTesting(OverlayItem.Kind.PLATE));
    }
    @Test public void phoneLevelUsesActualAnglesAndUnavailableHasNoBall()throws Exception{
        create(360,800,1);PhoneLevelView level=(PhoneLevelView)view(R.id.phone_orientation_panel);
        level.render(orientation(0,0));assertTrue(level.getContentDescription().toString().contains("poprawnie"));assertColor(level,h.a.getColor(R.color.cockpit_cyan));
        level.render(PhoneOrientationEstimator.Snapshot.unavailable());level.render(orientation(40,0));assertTrue(level.getContentDescription().toString().contains("pionowo"));assertColor(level,h.a.getColor(R.color.alpr_warning));
        level.render(PhoneOrientationEstimator.Snapshot.unavailable());assertColor(level,0xFF8C9AAA);assertEquals(0f,(float)TvBboxStartUiTest.get(level,"ballX"),0);assertEquals(0f,(float)TvBboxStartUiTest.get(level,"ballY"),0);
        assertFalse(level.getContentDescription().toString().contains("azymut"));
    }
    @Test public void existingAutoZoomTargetHasPriorityOverInstruments()throws Exception{
        create(360,800,1);values();toggle(R.id.live_diagnostics_toggle);
        Rect preferred=bounds(R.id.cockpit_resources_block);View target=view(R.id.auto_zoom_target);
        target.setVisibility(View.VISIBLE);target.setX(preferred.left);target.setY(preferred.top);layout();
        Rect obstacle=bounds(R.id.auto_zoom_target);
        for(int id:BLOCKS)if(view(id).isShown())assertFalse(Rect.intersects(obstacle,bounds(id)));
        assertEquals(obstacle,bounds(R.id.auto_zoom_target));assertTrue(target.isShown());
    }
    @Test public void outlineCollisionMatchesIndependentLineGeometryIncludingDiagonalTvEdges()throws Exception{
        Random random=new Random(20261108);
        // Android's compile API omits AWT; use the host JDK geometry only as an independent oracle.
        Object rectangle=Class.forName("java.awt.geom.Rectangle2D$Float").getConstructor(float.class,float.class,float.class,float.class).newInstance(98f,98f,84f,44f);
        java.lang.reflect.Method intersects=Class.forName("java.awt.geom.Rectangle2D").getMethod("intersectsLine",double.class,double.class,double.class,double.class);
        for(int i=0;i<1000;i++){
            float x1=random.nextFloat()*500-100,y1=random.nextFloat()*500-100,x2=random.nextFloat()*500-100,y2=random.nextFloat()*500-100;
            RectF candidate=new RectF(100,100,180,140);HudProtectedArea area=new HudProtectedArea();area.edge(x1,y1,x2,y2);
            boolean expected=(boolean)intersects.invoke(rectangle,(double)x1,(double)y1,(double)x2,(double)y2);
            assertEquals(expected,area.intersects(candidate,2));
        }
        HudProtectedArea outline=new HudProtectedArea();outline.outline(new RectF(0,0,300,300));
        assertFalse(outline.intersects(new RectF(100,100,180,140),2));
        assertTrue(outline.intersects(new RectF(0,100,20,140),2));
    }
    static PhoneOrientationEstimator.Snapshot orientation(float sideways,float forward){
        PhoneOrientationEstimator e=new PhoneOrientationEstimator();double side=Math.toRadians(sideways),pitch=Math.toRadians(forward);
        e.update((float)(-9.81*Math.sin(side)*Math.cos(pitch)),(float)(9.81*Math.cos(side)*Math.cos(pitch)),(float)(9.81*Math.sin(pitch)),1);return e.snapshot(1,0);
    }
    static void assertColor(View v,int color){Bitmap bitmap=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);try{v.draw(new Canvas(bitmap));int count=0;for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if(bitmap.getPixel(x,y)==color)count++;assertTrue(count>5);}finally{bitmap.recycle();}}
    void save(String name)throws Exception{
        String target=System.getenv("F5A_COCKPIT_EVIDENCE");if(target==null)return;java.nio.file.Path directory=Paths.get(target);Files.createDirectories(directory);
        Bitmap image=Bitmap.createBitmap(h.width,h.height,Bitmap.Config.ARGB_8888);try{h.a.getWindow().getDecorView().draw(new Canvas(image));try(OutputStream out=Files.newOutputStream(directory.resolve(name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}}finally{image.recycle();}
        String row=name+"\t"+bounds(R.id.camera_preview)+"\t"+bounds(R.id.hud2_viewport)+"\t"+hud().visibleInstrumentCount()+"\n";
        Files.write(directory.resolve("cockpit_rectangles.tsv"),row.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
}
