package com.example.alpr_v1.ui;

import android.app.Application;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.example.alpr_v1.R;
import com.example.alpr_v1.metrics.*;
import com.example.alpr_v1.pipeline.ModelRuntimeSummary;
import com.example.alpr_v1.setup.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Production HUD views and presenter; native host rendering, no camera/model invocation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NonModalHudTest {
    MainUiReadabilityTest h;
    @After public void close(){if(h!=null)h.close();}
    void create(int width,int height,float font){
        h=new MainUiReadabilityTest();h.create(width,height,font);
        h.a.findViewById(R.id.live_model_info_toggle).setOnClickListener(v->h.a.presentation.toggleModelInfo());
        h.a.presentation.showState(LivePresentationController.State.STATIC_IDLE,"");
        h.a.presentation.showResult(9,"B7737",.26,true,2);
        h.a.presentation.showState(LivePresentationController.State.STATIC_IDLE,"");
        MetricsCollector metrics=new MetricsCollector();metrics.startMeasurementSession();
        InferenceTrace trace=new InferenceTrace(2);trace.putDurationNanos("character_inference",420_000_000L);metrics.add(trace);
        ((LiveHudView)h.a.findViewById(R.id.live_hud_row)).render(metrics.liveSnapshot(),"1920 × 1080",false,29.5);
        ((LiveModelInfoView)h.a.findViewById(R.id.live_model_info)).render(Arrays.asList(
                new ModelRuntimeSummary("MP","Vehicle","tflite_fp32","FP32","LiteRT/CPU"),
                new ModelRuntimeSummary("MT","Plate","tflite_fp32","FP32","LiteRT/CPU"),
                new ModelRuntimeSummary("MZ","Characters","tflite_fp32","FP32","LiteRT/CPU")),"Kadr: 1920 × 1080");
        h.layout();
    }
    View view(int id){return h.a.findViewById(id);}
    Rect bounds(int id){return MainUiReadabilityTest.bounds(view(id));}
    void toggle(int id){assertTrue(view(id).performClick());h.layout();h.layout();}
    void states(boolean first,boolean second){
        assertEquals(first,view(R.id.live_hud_row).isShown());assertEquals(second,view(R.id.live_model_info).isShown());
        assertEquals(first,view(R.id.hud1_viewport).isShown());assertTrue(view(R.id.hud2_viewport).isShown());
        assertEquals(first,h.a.presentation.diagnosticsExpanded());assertNull(ShadowDialog.getLatestDialog());
    }
    @Test public void independentTogglesBothOrdersKeepViewsInTheirOriginalParents(){
        create(360,800,1.3f);Rect preview=bounds(R.id.camera_preview);
        ViewParent first=view(R.id.live_hud_row).getParent(),second=view(R.id.live_model_info).getParent();
        int flags=h.a.getWindow().getAttributes().flags;states(false,false);
        toggle(R.id.live_diagnostics_toggle);states(true,false);toggle(R.id.live_model_info_toggle);states(true,true);
        toggle(R.id.live_diagnostics_toggle);states(false,true);toggle(R.id.live_model_info_toggle);states(false,false);
        toggle(R.id.live_model_info_toggle);states(false,true);toggle(R.id.live_diagnostics_toggle);states(true,true);
        toggle(R.id.live_model_info_toggle);states(true,false);toggle(R.id.live_diagnostics_toggle);states(false,false);
        assertSame(first,view(R.id.live_hud_row).getParent());assertSame(second,view(R.id.live_model_info).getParent());
        assertEquals(flags,h.a.getWindow().getAttributes().flags);assertEquals(preview,bounds(R.id.camera_preview));
    }
    void matrix(int w,int height,float font)throws Exception{
        create(w,height,font);h.confirmTv();Rect camera=bounds(R.id.camera_preview);toggle(R.id.live_diagnostics_toggle);toggle(R.id.live_model_info_toggle);states(true,true);
        Rect first=bounds(R.id.hud1_viewport),second=bounds(R.id.hud2_viewport);
        assertTrue(camera.contains(first));assertTrue(camera.bottom<=second.top);assertFalse(Rect.intersects(camera,second));
        assertTrue(first.width()>0&&first.height()>0&&second.width()>0&&second.height()>0);
        png("both_"+w+"_"+height+"_font"+Math.round(font*10));
        assertFalse(view(R.id.hud1_viewport) instanceof ScrollView);
        assertFalse(view(R.id.hud2_viewport) instanceof ScrollView);
        assertEquals(camera,bounds(R.id.camera_preview));png("both_"+w+"_"+height+"_font"+Math.round(font*10));
    }
    @Test public void portrait320Font10()throws Exception{matrix(320,720,1);}
    @Test public void portrait320Font13()throws Exception{matrix(320,720,1.3f);}
    @Test public void portrait360Font10()throws Exception{matrix(360,800,1);}
    @Test public void portrait360Font13()throws Exception{matrix(360,800,1.3f);}
    @Test public void landscape720Font10()throws Exception{matrix(720,360,1);}
    @Test public void landscape720Font13()throws Exception{matrix(720,360,1.3f);}
    @Test public void noGlobalDimAndTouchOutsidePanelsReachesDetectionOverlay(){
        create(360,800,1);toggle(R.id.live_diagnostics_toggle);toggle(R.id.live_model_info_toggle);
        ViewGroup host=(ViewGroup)view(R.id.main_hud_storage);assertFalse(host.isClickable());assertNull(host.getBackground());
        List<Rect> occupied=new ArrayList<>();
        for(int id:new int[]{R.id.cockpit_fps_block,R.id.cockpit_temperature_block,R.id.cockpit_resources_block,R.id.cockpit_mp_block,R.id.cockpit_mt_block,R.id.cockpit_mz_block})
            if(view(id).isShown())occupied.add(local(host,view(id)));
        Bitmap bitmap=Bitmap.createBitmap(host.getWidth(),host.getHeight(),Bitmap.Config.ARGB_8888);
        int tested=0,px=-1,py=-1;
        try{
            host.draw(new Canvas(bitmap));
            for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if(!contains(occupied,x,y)){
                assertEquals("dim outside HUD at "+x+","+y,0,Color.alpha(bitmap.getPixel(x,y)));tested++;if(px<0&&x>8&&y>8){px=x;py=y;}
            }
        }finally{bitmap.recycle();}
        assertTrue(tested>1000);assertTrue(px>=0);
        DetectionOverlayView detections=(DetectionOverlayView)view(R.id.detection_overlay);AtomicLong tapped=new AtomicLong();detections.setVehicleTapListener(tapped::set);
        detections.setItems(Collections.singletonList(MainUiReadabilityTest.vehicle(42,0,0,1,1)),host.getWidth(),host.getHeight());
        ViewGroup root=(ViewGroup)view(R.id.main);Rect position=local(root,detections);
        MotionEvent down=MotionEvent.obtain(0,1,MotionEvent.ACTION_DOWN,position.left+px,position.top+py,0),up=MotionEvent.obtain(0,2,MotionEvent.ACTION_UP,position.left+px,position.top+py,0);
        try{assertTrue(root.dispatchTouchEvent(down));assertTrue(root.dispatchTouchEvent(up));assertEquals(42,tapped.get());}finally{down.recycle();up.recycle();}
    }
    @Test public void calibrationAndCalculatorTemporarilyHideParentAndRestoreBothSelections()throws Exception{
        create(360,800,1);toggle(R.id.live_diagnostics_toggle);toggle(R.id.live_model_info_toggle);
        for(TvSetupController.Mode mode:new TvSetupController.Mode[]{TvSetupController.Mode.CALIBRATION,TvSetupController.Mode.DISTANCE}){
            h.a.tv.open(mode);h.layout();assertEquals(View.INVISIBLE,view(R.id.main_hud_storage).getVisibility());
            assertEquals(View.VISIBLE,view(R.id.live_hud_row).getVisibility());assertEquals(View.VISIBLE,view(R.id.live_model_info).getVisibility());
            h.a.tv.close();for(int i=0;i<5&&h.a.tv.editing();i++)h.layout();
            assertFalse(h.a.tv.editing());states(true,true);
        }
    }
    @Test public void hudTogglesDoNotInvalidateConfirmedSourceMappingOrCalibration()throws Exception{
        create(360,800,1.3f);h.confirmTv();
        CameraPreviewGeometry before=(CameraPreviewGeometry)TvBboxStartUiTest.get(h.a.tv,"geometry");
        TvSetupCalibration calibration=(TvSetupCalibration)TvBboxStartUiTest.get(h.a.tv,"calibration");
        String digest=h.a.tv.snapshot(1794080000000L).toString();Rect camera=bounds(R.id.camera_preview);
        double[] pixel=before.normalizedToView(.12,.18);
        toggle(R.id.live_model_info_toggle);toggle(R.id.live_diagnostics_toggle);toggle(R.id.live_model_info_toggle);toggle(R.id.live_diagnostics_toggle);
        assertSame(before,TvBboxStartUiTest.get(h.a.tv,"geometry"));assertSame(calibration,TvBboxStartUiTest.get(h.a.tv,"calibration"));
        assertEquals(digest,h.a.tv.snapshot(1794080000000L).toString());assertEquals("READY",calibration.status(before));assertEquals(camera,bounds(R.id.camera_preview));
        assertArrayEquals(pixel,before.normalizedToView(.12,.18),0);
    }
    @Test public void diagnosticThrottlingUnknownValuesAndContextArePreserved()throws Exception{
        create(360,800,1);toggle(R.id.live_diagnostics_toggle);toggle(R.id.live_model_info_toggle);
        h.a.presentation.updateDiagnostics("snapshot A");
        assertEquals("420",((TextView)view(R.id.hud_mz_time)).getText().toString());
        assertNull(view(R.id.hud1_context));assertNull(view(R.id.hud2_context));assertNull(view(R.id.hud_mz_score_legend));
        String first=((TextView)view(R.id.live_hud)).getText().toString();h.a.presentation.updateDiagnostics("snapshot B");
        assertEquals(first,((TextView)view(R.id.live_hud)).getText().toString());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1001));h.a.presentation.updateDiagnostics("snapshot C");
        assertEquals("snapshot C",((TextView)view(R.id.live_hud)).getText().toString());
        h.a.presentation.stop();h.layout();states(true,true);
        ((LiveHudView)view(R.id.live_hud_row)).clearMetrics();assertEquals("—",((TextView)view(R.id.hud_mz_time)).getText().toString());
        h.a.presentation.showState(LivePresentationController.State.ERROR,"");h.layout();states(true,true);
    }
    static boolean contains(List<Rect> rectangles,int x,int y){for(Rect r:rectangles)if(r.contains(x,y))return true;return false;}
    static Rect local(ViewGroup root,View child){Rect r=new Rect(0,0,child.getWidth(),child.getHeight());root.offsetDescendantRectToMyCoords(child,r);return r;}
    void png(String name)throws Exception{
        String folder=System.getenv("F5A_COCKPIT_EVIDENCE");if(folder==null)return;java.nio.file.Path directory=Paths.get(folder);Files.createDirectories(directory);
        Bitmap image=Bitmap.createBitmap(h.width,h.height,Bitmap.Config.ARGB_8888);h.a.getWindow().getDecorView().draw(new Canvas(image));
        try(OutputStream out=Files.newOutputStream(directory.resolve(name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
        String row=name+"\t"+h.width+"\t"+h.height+"\t"+h.scale+"\t"+bounds(R.id.camera_preview)+"\t"+bounds(R.id.hud1_viewport)+"\t"+bounds(R.id.hud2_viewport)+"\n";
        Files.write(directory.resolve("hud_rectangles.tsv"),row.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
}
