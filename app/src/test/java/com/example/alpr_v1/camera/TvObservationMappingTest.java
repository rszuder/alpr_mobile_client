package com.example.alpr_v1.camera;

import android.app.Application;
import android.graphics.*;
import androidx.camera.core.*;
import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.setup.*;
import com.example.alpr_v1.vision.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TvObservationMappingTest {
    public static LumaFrame frame(CameraPreviewGeometry g,byte[] raw,int maximumWidth,long sequence)throws Exception {
        return frame(g,raw,maximumWidth,sequence,new CameraSetupSnapshot(new CameraSelection(g.cameraId,g.physicalCameraId),(float)g.zoomRatio),g.bindingGeneration,false);
    }
    static LumaFrame frame(CameraPreviewGeometry g,byte[] raw,int maximumWidth,long sequence,CameraSetupSnapshot setup,int binding,boolean truncated)throws Exception {
        int row=g.bufferWidth*2+8,base=17;ByteBuffer buffer=ByteBuffer.allocate(base+row*g.bufferHeight+5);
        for(int y=0;y<g.bufferHeight;y++)for(int x=0;x<g.bufferWidth;x++)buffer.put(base+y*row+x*2,raw[y*g.bufferWidth+x]);
        buffer.position(base);buffer.limit(truncated?base+row:base+row*g.bufferHeight);
        int position=buffer.position(),limit=buffer.limit();
        JSONArray c=g.toJson().getJSONArray("crop_rect_px");Rect crop=new Rect(c.getInt(0),c.getInt(1),c.getInt(2),c.getInt(3));
        ImageProxy.PlaneProxy plane=new ImageProxy.PlaneProxy(){public ByteBuffer getBuffer(){return buffer;}public int getRowStride(){return row;}public int getPixelStride(){return 2;}};
        ImageInfo info=(ImageInfo)Proxy.newProxyInstance(ImageInfo.class.getClassLoader(),new Class[]{ImageInfo.class},(p,m,a)->{
            if(m.getName().equals("getRotationDegrees"))return g.rotationDegrees;
            if(m.getName().equals("getTimestamp"))return sequence;throw new UnsupportedOperationException(m.getName());});
        ImageProxy image=(ImageProxy)Proxy.newProxyInstance(ImageProxy.class.getClassLoader(),new Class[]{ImageProxy.class},(p,m,a)->{
            switch(m.getName()){case "getWidth":return g.bufferWidth;case "getHeight":return g.bufferHeight;case "getCropRect":return crop;
                case "getPlanes":return new ImageProxy.PlaneProxy[]{plane};case "getImageInfo":return info;case "close":return null;default:throw new UnsupportedOperationException(m.getName());}});
        LumaFrame f=LumaFrame.copyFrom(image,maximumWidth,new SourceFrameStamp(sequence,sequence,SourceTimestampDomain.CAMERAX_SENSOR,0,0,0),binding,setup);
        assertEquals(position,buffer.position());assertEquals(limit,buffer.limit());return f;
    }
    static CameraPreviewGeometry geometry(int rotation,int vw,int vh){
        return TvBboxCropTest.geometry(rotation%180==0?608:384,rotation%180==0?384:608,rotation,672,448,new double[]{32,24,640,408},vw,vh,1.5f);
    }
    static SceneObservationDomain domain(CameraPreviewGeometry g)throws Exception {
        return new SceneObservationDomain(TvBboxCrop.prepare(TvBboxCropTest.calibration(g),g,TvBboxCropTest.setup((float)g.zoomRatio)));
    }
    static byte[] raw(CameraPreviewGeometry g){byte[] a=new byte[g.bufferWidth*g.bufferHeight];for(int y=0;y<g.bufferHeight;y++)for(int x=0;x<g.bufferWidth;x++)a[y*g.bufferWidth+x]=(byte)((x*3+y*7)%230+10);return a;}
    static byte[] changedOutside(byte[] a,CameraPreviewGeometry g,ObservationMask source)throws Exception {
        byte[] b=a.clone();JSONArray c=g.toJson().getJSONArray("crop_rect_px");
        double[] map=CameraPreviewGeometry.bufferToSource(new double[]{c.getDouble(0),c.getDouble(1),c.getDouble(2),c.getDouble(3)},g.rotationDegrees);
        Random random=new Random(12);
        for(int y=0;y<g.bufferHeight;y++)for(int x=0;x<g.bufferWidth;x++){
            double[] p=CameraPreviewGeometry.map(map,x+.5,y+.5);
            if(!source.contains((int)Math.floor(p[0]),(int)Math.floor(p[1])))b[y*g.bufferWidth+x]=(byte)random.nextInt(256);
        }return b;
    }
    @Test public void fRawCropRotationStrideAndExactNearestSamplingExcludeExterior()throws Exception {
        for(int rotation:new int[]{0,90,180,270})for(int scale:new int[]{96,240}){
            CameraPreviewGeometry g=geometry(rotation,800,600);SceneObservationDomain d=domain(g);byte[] a=raw(g),b=changedOutside(a,g,d.sourceMask(g.sourceWidth,g.sourceHeight));
            LumaFrame f=frame(g,a,scale,1);SceneObservationDomain.Frame fa=d.luma(f),fb=d.luma(frame(g,b,scale,2));
            assertArrayEquals("rotation="+rotation+" scale="+scale,fa.gray,fb.gray);assertSame(fa.mask,fb.mask);
            for(int y=0;y<f.height;y++)for(int x=0;x<f.width;x++){
                int ox=rotation==90?f.height-1-y:rotation==180?f.width-1-x:rotation==270?y:x;
                int oy=rotation==90?x:rotation==180?f.height-1-y:rotation==270?f.width-1-x:y;
                int sx=32+Math.min(607,Math.round((x+.5f)*608/f.width-.5f)),sy=24+Math.min(383,Math.round((y+.5f)*384/f.height-.5f));
                assertEquals(fa.mask.contains(ox,oy)?a[sy*g.bufferWidth+sx]:0,fa.gray[oy*fa.width+ox]);
            }
        }
    }
    @Test public void cameraIdZoomBindingRotationCropAndTruncatedPlaneFailClosed()throws Exception {
        CameraPreviewGeometry g=geometry(90,800,600);SceneObservationDomain d=domain(g);byte[] a=raw(g);
        for(CameraSetupSnapshot setup:new CameraSetupSnapshot[]{TvBboxCropTest.setup(1),new CameraSetupSnapshot(new CameraSelection("2",null),1.5f),null}) {
            LumaFrame f=frame(g,a,240,1,setup,g.bindingGeneration,false);assertThrows(IllegalArgumentException.class,()->d.luma(f));
        }
        assertThrows(IllegalArgumentException.class,()->d.luma(frame(g,a,240,1,TvBboxCropTest.setup(1.5f),g.bindingGeneration+1,false)));
        assertThrows(IllegalArgumentException.class,()->d.luma(frame(g,a,240,1,TvBboxCropTest.setup(1.5f),g.bindingGeneration,true)));
        CameraPreviewGeometry wrong=geometry(270,800,600);assertThrows(IllegalArgumentException.class,()->d.luma(frame(wrong,a,240,1)));
        CameraPreviewGeometry cropped=TvBboxCropTest.geometry(384,600,90,672,448,new double[]{40,24,640,408},800,600,1.5f);
        assertThrows(IllegalArgumentException.class,()->d.luma(frame(cropped,a,240,1)));
    }
    @Test public void ePreviewFallbackUsesActualFitCenterMappingInPortraitAndLandscape()throws Exception {
        for(int rotation:new int[]{0,90,180,270})for(int[] size:new int[][]{{360,800},{900,450}}){
            CameraPreviewGeometry g=geometry(rotation,size[0],size[1]);SceneObservationDomain d=domain(g);
            Bitmap a=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
            Random r=new Random(8);ObservationMask m=d.sourceMask(g.sourceWidth,g.sourceHeight);
            for(int y=0;y<size[1];y++)for(int x=0;x<size[0];x++){
                double[] p=g.viewToNormalized(x+.5,y+.5);int sx=(int)Math.floor(p[0]*g.sourceWidth),sy=(int)Math.floor(p[1]*g.sourceHeight);
                int value=Color.rgb((x*5+y)%256,(x+y*3)%256,(x+y)%256);a.setPixel(x,y,value);
                b.setPixel(x,y,m.contains(sx,sy)?value:Color.rgb(r.nextInt(256),r.nextInt(256),r.nextInt(256)));
            }
            SceneObservationDomain.Frame fa=d.preview(a,g),fb=d.preview(b,g);assertArrayEquals(fa.gray,fb.gray);assertSame(fa.mask,fb.mask);
            SceneChangeDetector detector=new SceneChangeDetector();detector.updateLuma(fa.gray,fa.width,fa.height,fa.mask);detector.updateLuma(fa.gray,fa.width,fa.height,fa.mask);
            assertFalse(detector.updateLuma(fb.gray,fb.width,fb.height,fb.mask).sceneChanged);
            SceneAnchorGuard guard=new SceneAnchorGuard();guard.anchorLuma(fa.gray,fa.width,fa.height,fa.mask);assertFalse(guard.evaluateLuma(fb.gray,fb.width,fb.height,fb.mask).changed);
            a.recycle();b.recycle();
        }
    }
    @Test public void invalidPreviewGeometryCannotFallBackToWholeView()throws Exception {
        CameraPreviewGeometry g=geometry(0,800,600);SceneObservationDomain d=domain(g);Bitmap a=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888);
        assertThrows(IllegalArgumentException.class,()->d.preview(a,null));
        assertThrows(IllegalArgumentException.class,()->d.preview(a,geometry(0,900,600)));
        assertThrows(IllegalArgumentException.class,()->d.preview(a,geometry(90,800,600)));
        assertThrows(IllegalArgumentException.class,()->d.sourceMask(608,385));a.recycle();
    }
    @Test public void sourceScalarMaskDoesNotAlterFullCameraOrRectangularModelCrop()throws Exception {
        TvBboxCrop crop=TvBboxCropTest.selection(640,400);SceneObservationDomain d=new SceneObservationDomain(crop);
        Bitmap source=Bitmap.createBitmap(640,400,Bitmap.Config.ARGB_8888);source.eraseColor(Color.GREEN);int[] before=new int[640*400];source.getPixels(before,0,640,0,0,640,400);
        try(TvBboxCrop.Frame frame=crop.open(source,crop.geometry,crop.cameraSetup)){
            int outsideX=crop.left+1,outsideY=crop.bottom-2;assertFalse(d.sourceMask(640,400).contains(outsideX,outsideY));
            SceneChangeDetector detector=new SceneChangeDetector();detector.update(source,d.sourceMask(640,400));
            assertEquals(Color.GREEN,frame.input.getPixel(outsideX-crop.left,outsideY-crop.top));
            int[] after=new int[before.length];source.getPixels(after,0,640,0,0,640,400);assertArrayEquals(before,after);
        }source.recycle();
    }
    @Test public void previewMirrorUsesExistingMapperInsteadOfSecondFitCenterGuess()throws Exception {
        CameraPreviewGeometry g=TvBboxCropTest.geometry(640,400);JSONObject j=g.toJson();
        j.put("source_to_view_matrix",new JSONArray(new double[]{-1.25,0,800,0,1.25,50,0,0,1}));j.put("mirrored",true);
        CameraPreviewGeometry mirrored=CameraPreviewGeometry.fromJson(j);SceneObservationDomain d=domain(mirrored);
        Bitmap b=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888);for(int y=0;y<600;y++)for(int x=0;x<800;x++)b.setPixel(x,y,Color.rgb(x%256,x%256,x%256));
        SceneObservationDomain.Frame f=d.preview(b,mirrored);int x=100,y=70;assertTrue(f.mask.contains(x,y));
        double[] p=mirrored.normalizedToView((x+.5)/f.width,(y+.5)/f.height);int expected=(int)Math.floor(p[0])%256;
        assertEquals(expected,f.gray[y*f.width+x]&255);b.recycle();
    }
}
