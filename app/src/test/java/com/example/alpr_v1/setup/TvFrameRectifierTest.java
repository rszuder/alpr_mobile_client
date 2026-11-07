package com.example.alpr_v1.setup;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.example.alpr_v1.continuity.SourceFrameStamp;
import com.example.alpr_v1.continuity.SourceTimestampDomain;
import com.example.alpr_v1.vision.Point2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TvFrameRectifierTest {
    private static final SourceFrameStamp STAMP=new SourceFrameStamp(71,1000,SourceTimestampDomain.UNKNOWN,3,2,1);
    private static double[] camera(double u,double v){double w=1+.0009*u+.0005*v;return new double[]{(200+2.4*u+.3*v)/w,(180+.18*u+2*v)/w};}
    private static List<Point2> corners(){List<Point2> result=new ArrayList<>();for(double[] p:new double[][]{{0,0},{511,0},{511,287},{0,287}}){double[] q=camera(p[0],p[1]);result.add(new Point2((float)(q[0]/1920),(float)(q[1]/1080)));}return result;}
    private static int pattern(double u,double v){return ((int)(u/64)+(int)(v/48))%2==0?Color.rgb(35,190,150):Color.rgb(225,155,35);}
    /** Independent inverse of the known rational projection: no Matrix or production H is used. */
    private static Bitmap source(){return source(false);}
    private static Bitmap source(boolean pillarbox){int[] pixels=new int[1920*1080];Arrays.fill(pixels,Color.rgb(25,35,55));
        for(int y=0;y<1080;y++)for(int x=0;x<1920;x++){
            double px=x+.5,py=y+.5,a=2.4-.0009*px,b=.3-.0005*px,c=px-200,d=.18-.0009*py,e=2-.0005*py,f=py-180;
            double determinant=a*e-b*d,u=(c*e-b*f)/determinant,v=(a*f-c*d)/determinant;
            if(u>=0&&u<=511&&v>=0&&v<=287)pixels[y*1920+x]=pillarbox&&(u<80||u>431)?Color.BLACK:pattern(u,v);
        }return Bitmap.createBitmap(pixels,1920,1080,Bitmap.Config.ARGB_8888);
    }
    @Test public void panelRectificationRetainsPillarboxInsteadOfInventingPhotoBounds() throws Exception {
        Bitmap source=source(true);CameraPreviewGeometry g=geometry();TvFrameRectifier.Result result=TvFrameRectifier.rectifyPrototype(source,STAMP,calibration(g),g,g,512,288);
        for(int y:new int[]{24,100,240}){assertEquals(Color.BLACK,result.image.getPixel(32,y));assertEquals(Color.BLACK,result.image.getPixel(480,y));assertEquals(pattern(200,y),result.image.getPixel(200,y));}
        String target=System.getenv("F5A14B_FIXTURES");if(target!=null){File dir=new File(target);dir.mkdirs();png(source,new File(dir,"synthetic-panel-pillarbox-source.png"));png(result.image,new File(dir,"synthetic-panel-pillarbox-rectified.png"));}
        result.image.recycle();source.recycle();
    }
    private static CameraPreviewGeometry geometry(){return TvSetupGeometryTest.geometry(0,960,540,1,"synthetic-camera",1);}
    private static TvSetupCalibration calibration(CameraPreviewGeometry g){return TvSetupCalibration.confirm(corners(),g,1,"2026-11-05T12:00:00Z");}
    @Test public void realTrapezoidPixelsRectifyAndBothImagesRetainTheirIdentity() throws Exception {
        Bitmap source=source();byte[] before=pixelsSha(source);CameraPreviewGeometry g=geometry();
        TvFrameRectifier.Result result=TvFrameRectifier.rectifyPrototype(source,STAMP,calibration(g),g,g,512,288);
        assertNotSame(source,result.image);assertArrayEquals(before,pixelsSha(source));assertSame(STAMP,result.sourceStamp);
        assertEquals(1920,source.getWidth());assertEquals(512,result.image.getWidth());assertEquals(288,result.image.getHeight());
        int checked=0;for(int y=12;y<288;y+=24)for(int x=16;x<512;x+=32){assertEquals("Checkerboard must be straight at "+x+","+y,pattern(x,y),result.image.getPixel(x,y));checked++;}
        assertEquals(192,checked);assertTrue(result.durationNanos>=0);
        for(int y=24;y<288;y+=48)for(int x=64;x<512;x+=64){
            int nearest=99;for(int i=x-3;i<=x+3;i++)if(orange(result.image.getPixel(i-1,y))!=orange(result.image.getPixel(i,y)))nearest=Math.min(nearest,Math.abs(i-x));
            assertTrue("Vertical grid edge moved over 2 px",nearest<=2);
        }
        for(int x=32;x<512;x+=64)for(int y=48;y<288;y+=48){
            int nearest=99;for(int i=y-3;i<=y+3;i++)if(orange(result.image.getPixel(x,i-1))!=orange(result.image.getPixel(x,i)))nearest=Math.min(nearest,Math.abs(i-y));
            assertTrue("Horizontal grid edge moved over 2 px",nearest<=2);
        }
        String location=System.getenv("F5A14B_FIXTURES");if(location!=null){File dir=new File(location);dir.mkdirs();
            File raw=new File(dir,"synthetic-camera-full.png"),warped=new File(dir,"synthetic-tv-rectified.png");png(source,raw);png(result.image,warped);
            JSONObject descriptor=result.geometryJson().put("synthetic",true).put("source_image_entry",raw.getName()).put("source_image_sha256",hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(raw.toPath()))))
                    .put("input_image_entry",warped.getName()).put("input_image_sha256",hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(warped.toPath()))))
                    .put("mt_invocation_ids",new JSONArray()).put("attempt_ids",new JSONArray()).put("missing_reason",JSONObject.NULL);
            Files.write(new File(dir,"tv-rectification-prototype.json").toPath(),SetupJson.seal(descriptor).toString(2).getBytes(StandardCharsets.UTF_8));
        }result.image.recycle();source.recycle();
    }
    @Test public void handCalculatedProjectionAndInversePreserveRoiAndNonAxisPose(){
        Bitmap source=source();CameraPreviewGeometry g=geometry();TvFrameRectifier.Result result=TvFrameRectifier.rectifyPrototype(source,STAMP,calibration(g),g,g,512,288);
        for(double[] p:new double[][]{{0,0},{511,0},{511,287},{0,287},{173,91},{255.5,143.5}}){double[] q=camera(p[0],p[1]);assertArrayEquals(p,result.sourceToInput(q[0],q[1]),.001);assertArrayEquals(q,result.inputToSource(p[0],p[1]),.001);}
        List<Point2> roi=Arrays.asList(new Point2(64,48),new Point2(448,48),new Point2(448,240),new Point2(64,240));
        List<Point2> pose=Arrays.asList(new Point2(100,60),new Point2(400,73),new Point2(387,204),new Point2(112,190));
        for(List<Point2> quad:Arrays.asList(roi,pose)){List<Point2> back=result.mapFourCorners(result.mapFourCorners(quad,true),false);
            for(int i=0;i<4;i++){assertEquals(quad.get(i).x,back.get(i).x,.001);assertEquals(quad.get(i).y,back.get(i).y,.001);}}
        assertThrows(IllegalArgumentException.class,()->result.mapFourCorners(roi.subList(0,3),false));result.image.recycle();source.recycle();
    }
    @Test public void staleDraftWrongDimensionsAndInvalidTargetAreRejected(){
        Bitmap source=source();CameraPreviewGeometry g=geometry();TvSetupCalibration confirmed=calibration(g);
        assertThrows(IllegalArgumentException.class,()->TvFrameRectifier.rectifyPrototype(source,STAMP,TvSetupCalibration.empty(),g,g,512,288));
        assertThrows(IllegalArgumentException.class,()->TvFrameRectifier.rectifyPrototype(source,STAMP,confirmed.invalidated("ZOOM_CHANGED"),g,g,512,288));
        assertThrows(IllegalArgumentException.class,()->TvFrameRectifier.rectifyPrototype(source,STAMP,confirmed,g,TvSetupGeometryTest.geometry(0,960,540,2,"synthetic-camera",1),512,288));
        CameraPreviewGeometry changed=TvSetupGeometryTest.geometry(0,960,540,2,"synthetic-camera",1);
        assertThrows(IllegalArgumentException.class,()->TvFrameRectifier.rectifyPrototype(source,STAMP,calibration(changed),g,changed,512,288));
        assertThrows(IllegalArgumentException.class,()->TvFrameRectifier.rectifyPrototype(source,STAMP,confirmed,g,g,1,288));
        assertThrows(IllegalArgumentException.class,()->TvFrameRectifier.rectifyPrototype(source,STAMP,confirmed,g,g,8192,8192));
        Bitmap wrong=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);assertThrows(IllegalArgumentException.class,()->TvFrameRectifier.rectifyPrototype(wrong,STAMP,confirmed,g,g,512,288));wrong.recycle();source.recycle();
    }
    @Test public void fullFrameIsTheIdenticalBitmapAndSessionAdmissionBlocksRectification() throws Exception {
        Bitmap source=source();byte[] before=pixelsSha(source);
        assertSame(source,ResearchInputMode.CAMERA_FULL_FRAME.cameraInputForSession(source));assertArrayEquals(before,pixelsSha(source));
        IllegalStateException error=assertThrows(IllegalStateException.class,()->ResearchInputMode.TV_RECTIFIED.cameraInputForSession(source));
        assertEquals("BLOCKED_DESKTOP_CONTRACT",error.getMessage());assertArrayEquals(before,pixelsSha(source));source.recycle();
    }
    private static byte[] pixelsSha(Bitmap source)throws Exception{ByteBuffer b=ByteBuffer.allocate(source.getByteCount());source.copyPixelsToBuffer(b);return MessageDigest.getInstance("SHA-256").digest(b.array());}
    private static boolean orange(int color){return Color.red(color)>Color.green(color);}
    private static String hex(byte[] data){StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    private static void png(Bitmap bitmap,File file)throws Exception{try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}}
}
