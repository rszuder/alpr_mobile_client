package com.example.alpr_v1.setup;

import android.app.Application;
import android.graphics.*;
import androidx.camera.view.PreviewView;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.R;
import com.example.alpr_v1.camera.*;
import com.example.alpr_v1.vision.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TvBboxCropTest {
    public static CameraPreviewGeometry geometry(int w,int h,int rotation,int bw,int bh,double[] cameraCrop,int vw,int vh,float zoom){
        double scale=Math.min(vw/(double)w,vh/(double)h),dx=(vw-scale*w)/2,dy=(vh-scale*h)/2;
        return new CameraPreviewGeometry(w,h,bw,bh,cameraCrop,rotation,"0","BACK",zoom,7,3,vw,vh,
                new double[]{scale,0,dx,0,scale,dy,0,0,1},new double[]{0,0,vw,vh});
    }
    public static CameraPreviewGeometry geometry(int w,int h){return geometry(w,h,0,w,h,new double[]{0,0,w,h},800,600,1);}
    public static CameraSetupSnapshot setup(float zoom){return new CameraSetupSnapshot(new CameraSelection("0",null),zoom);}
    public static JSONObject calibration(CameraPreviewGeometry g)throws Exception{
        List<Point2> points=Arrays.asList(new Point2(.125f,.15f),new Point2(.875f,.20f),new Point2(.85f,.85f),new Point2(.15f,.80f));
        return TvSetupCalibration.confirm(points,g,1,"2026-11-06T20:00:00Z").snapshot(g,null,null,
                TvDistanceCalculator.unavailable("SYNTHETIC"),"2026-11-06T20:00:00Z");
    }
    public static TvBboxCrop selection(int w,int h)throws Exception{CameraPreviewGeometry g=geometry(w,h);return TvBboxCrop.prepare(calibration(g),g,setup(1));}
    @Test public void floorCeilAndNonAxisQuadUseSourcePixels()throws Exception{
        CameraPreviewGeometry g=geometry(1920,1080);JSONObject c=calibration(g);
        c.put("corners_norm",new JSONArray("[{x:0.12,y:0.18},{x:0.83,y:0.24},{x:0.76,y:0.70},{x:0.19,y:0.65}]"));SetupJson.seal(c);
        TvBboxCrop box=TvBboxCrop.prepare(c,g,setup(1));assertEquals(230,box.left);assertEquals(194,box.top);assertEquals(1594,box.right);assertEquals(756,box.bottom);
        assertEquals(1364,box.width());assertEquals(562,box.height());assertEquals("NOT_APPLIED",box.toJson().getString("H_TV"));
    }
    @Test public void wholeImageEdgesDoNotAddMarginAndCloseKeepsCameraAlive()throws Exception{
        CameraPreviewGeometry g=geometry(640,400);JSONObject c=calibration(g);c.put("corners_norm",new JSONArray("[{x:0,y:0},{x:1,y:0},{x:1,y:1},{x:0,y:1}]"));SetupJson.seal(c);
        TvBboxCrop box=TvBboxCrop.prepare(c,g,setup(1));Bitmap image=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);
        try(TvBboxCrop.Frame frame=box.open(image,g,setup(1))){assertSame(image,frame.input);assertEquals(0,box.left);assertEquals(640,box.right);}
        assertFalse(image.isRecycled());image.recycle();
    }
    @Test public void invalidDraftStaleAndCorruptCalibrationsCannotCrop()throws Exception{
        CameraPreviewGeometry g=geometry(640,400);
        for(String state:new String[]{"DRAFT","STALE","UNCALIBRATED"}){
            JSONObject c=calibration(g);c.put("status",state);SetupJson.seal(c);assertThrows(IllegalArgumentException.class,()->TvBboxCrop.prepare(c,g,setup(1)));
        }
        JSONObject bad=calibration(g);bad.put("confirmed_at_utc","tampered");assertThrows(IllegalArgumentException.class,()->TvBboxCrop.prepare(bad,g,setup(1)));
    }
    @Test public void crossedCollinearDuplicateAndOutsideCornersAreRejected()throws Exception{
        for(String text:new String[]{"[{x:.1,y:.1},{x:.9,y:.9},{x:.9,y:.1},{x:.1,y:.9}]",
                "[{x:.1,y:.1},{x:.3,y:.3},{x:.6,y:.6},{x:.9,y:.9}]",
                "[{x:.1,y:.1},{x:.9,y:.1},{x:.9,y:.1},{x:.1,y:.9}]",
                "[{x:-.1,y:.1},{x:.9,y:.1},{x:.9,y:.9},{x:.1,y:.9}]"})
            assertThrows(Exception.class,()->TvBboxCrop.bounds(new JSONArray(text),640,400));
        assertThrows(Exception.class,()->TvBboxCrop.bounds(new JSONArray(),640,400));
    }
    @Test public void cropContainsTheActualPixelsAndDoesNotMaskPolygon()throws Exception{
        TvBboxCrop box=selection(640,400);Bitmap image=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);
        for(int y=0;y<400;y++)for(int x=0;x<640;x++)image.setPixel(x,y,Color.rgb(x%256,y%256,(x+y)%256));
        try(TvBboxCrop.Frame frame=box.open(image,box.geometry,setup(1))){
            for(int y=0;y<box.height();y+=7)for(int x=0;x<box.width();x+=7)assertEquals(image.getPixel(x+box.left,y+box.top),frame.input.getPixel(x,y));
            assertEquals(image.getPixel(box.right-1,box.bottom-1),frame.input.getPixel(box.width()-1,box.height()-1));
        }assertFalse(image.isRecycled());image.recycle();
    }
    @Test public void cameraConverterRotationCropAndFitCenterRoundTrip()throws Exception{
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();activity.setTheme(R.style.Theme_ALPR_v1);
        for(int rotation:new int[]{0,90,180,270})for(int[] view:new int[][]{{360,800},{900,450}}){
            GeometryProviderFixture hardware=new GeometryProviderFixture(new PreviewView(activity),activity);
            hardware.width=720;hardware.height=480;hardware.crop=new Rect(32,24,688,456);hardware.rotation=rotation;
            hardware.bitmap=Bitmap.createBitmap(720,480,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(hardware.bitmap);c.drawColor(Color.BLUE);Paint p=new Paint();p.setColor(Color.RED);c.drawRect(32,24,220,180,p);
            CameraImageConverter.Result converted=CameraImageConverter.convert(hardware.image());
            int w=rotation%180==0?656:432,h=rotation%180==0?432:656;
            CameraPreviewGeometry g=geometry(w,h,rotation,720,480,new double[]{32,24,688,456},view[0],view[1],1);
            TvBboxCrop box=TvBboxCrop.prepare(calibration(g),g,setup(1));
            try(TvBboxCrop.Frame frame=box.open(converted.bitmap,g,setup(1))){
                assertEquals(converted.bitmap.getPixel(box.left,box.top),frame.input.getPixel(0,0));
                assertEquals(720,converted.bufferWidth);assertEquals(rotation,converted.rotationDegrees);assertEquals(hardware.crop,converted.cameraCropRect);
                for(double[] point:new double[][]{{.125,.15},{.875,.2},{.85,.85},{.15,.8}}){double[] v=g.normalizedToView(point[0],point[1]),back=g.viewToNormalized(v[0],v[1]);
                    assertEquals(point[0]*w,back[0]*w,1e-6);assertEquals(point[1]*h,back[1]*h,1e-6);}
            }converted.bitmap.recycle();hardware.bitmap.recycle();
        }
    }
    @Test public void zoomCameraAndPreviewGeometryCannotReuseFrozenInput()throws Exception{
        TvBboxCrop box=selection(640,400);Bitmap image=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);
        CameraPreviewGeometry zoom=geometry(640,400,0,640,400,new double[]{0,0,640,400},800,600,2);
        assertThrows(IllegalArgumentException.class,()->box.open(image,zoom,setup(2)));
        CameraPreviewGeometry viewport=geometry(640,400,0,640,400,new double[]{0,0,640,400},900,600,1);
        assertThrows(IllegalArgumentException.class,()->box.open(image,viewport,setup(1)));
        assertThrows(IllegalArgumentException.class,()->box.open(image,box.geometry,new CameraSetupSnapshot(new CameraSelection("2",null),1)));
        image.recycle();
    }
    @Test public void offsetsAreTranslationOnlyAndNeverCreatePoseFromABbox()throws Exception{
        TvBboxCrop crop=selection(640,400);Detection local=new Detection(0,.91f,20,40,200,100,
                Arrays.asList(new Point2(23,42),new Point2(196,47),new Point2(190,97),new Point2(27,90)));
        Detection mapped=crop.toCamera(local);assertEquals(4,mapped.keypoints.size());
        for(int i=0;i<4;i++){assertEquals(local.keypoints.get(i).x+crop.left,mapped.keypoints.get(i).x,0f);assertEquals(local.keypoints.get(i).y+crop.top,mapped.keypoints.get(i).y,0f);}
        assertTrue(crop.toCamera(new Detection(0,.9f,10,10,20,20,Collections.emptyList())).keypoints.isEmpty());
        ResearchInputMode.TV_BBOX_CROP.requireSessionSupported();
        Bitmap image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);assertSame(image,ResearchInputMode.CAMERA_FULL_FRAME.cameraInputForSession(image));assertThrows(IllegalStateException.class,()->ResearchInputMode.TV_BBOX_CROP.cameraInputForSession(image));image.recycle();
    }
}
