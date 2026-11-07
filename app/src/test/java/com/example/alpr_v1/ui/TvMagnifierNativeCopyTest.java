package com.example.alpr_v1.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.View;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Exercises the previously missing boundary: native crop/flip/rotate BEFORE PixelCopy's srcRect.
 * The native stage is an independent raster model of AOSP Readback.cpp, not device execution. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class TvMagnifierNativeCopyTest {
    private Bitmap board(){Bitmap b=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);for(int y=0;y<480;y++)for(int x=0;x<640;x++)b.setPixel(x,y,Color.rgb((x&63)*4,(y&63)*4,(x>>6)|((y>>6)<<4)));return b;}
    private static float x(int c){return Color.red(c)/4f+((Color.blue(c)&15)<<6);}
    private static float y(int c){return Color.green(c)/4f+((Color.blue(c)>>4)<<6);}
    private static double[] values(Matrix m){float[] a=new float[9];m.getValues(a);double[] b=new double[9];for(int i=0;i<9;i++)b[i]=a[i];return b;}
    private static Bitmap nativeImage(Bitmap raw,Rect crop,int flags){
        int w=crop.width(),h=crop.height();Matrix m=new Matrix();m.setTranslate(-crop.left,-crop.top);
        if((flags&1)!=0)m.postScale(-1,1,w/2f,h/2f);
        if((flags&2)!=0)m.postScale(1,-1,w/2f,h/2f);
        if((flags&4)!=0){m.postRotate(90);m.postTranslate(h,0);}
        Bitmap logical=Bitmap.createBitmap((flags&4)!=0?h:w,(flags&4)!=0?w:h,Bitmap.Config.ARGB_8888);
        new Canvas(logical).drawBitmap(raw,m,new Paint());return logical;
    }
    private static void copy(Bitmap logical,Rect request,Bitmap destination){
        Rect source=request==null?new Rect(0,0,logical.getWidth(),logical.getHeight()):new Rect(request);
        assertTrue(source.intersect(0,0,logical.getWidth(),logical.getHeight()));
        new Canvas(destination).drawBitmap(logical,source,new Rect(0,0,destination.getWidth(),destination.getHeight()),new Paint(Paint.FILTER_BITMAP_FLAG));
    }
    @Test public void regressionPartialReadbackInBufferCoordinatesShowsWrongPixels(){
        Bitmap raw=board(),logical=nativeImage(raw,new Rect(32,24,608,456),4);
        Bitmap canonical=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);copy(logical,null,canonical);
        Rect oldRect=new Rect(370,180,430,240);Bitmap old=Bitmap.createBitmap(60,60,Bitmap.Config.ARGB_8888);copy(logical,oldRect,old);
        int wrong=old.getPixel(30,30),correct=canonical.getPixel(400,210);
        assertTrue("native logical crop is not a crop in canonical inner-view coordinates",Math.hypot(x(wrong)-x(correct),y(wrong)-y(correct))>40);
    }
    @Test @Config(sdk={29,31,36}) public void fullSnapshotHandlesNativeCropAllFlipRotateFlagsAndEveryCorner()throws Exception{
        Bitmap raw=board(),figure=Bitmap.createBitmap(640,620,Bitmap.Config.ARGB_8888);Canvas proof=new Canvas(figure);proof.drawColor(0xff111923);
        for(int flags=0;flags<8;flags++){
            Rect crop=new Rect(32,24,608,456);Bitmap logical=nativeImage(raw,crop,flags);int w=logical.getWidth(),h=logical.getHeight();
            float scale=Math.min(360f/w,260f/h);Matrix sourceToView=new Matrix();sourceToView.setScale(scale,scale);sourceToView.postTranslate((360-w*scale)/2,(260-h*scale)/2);
            CameraPreviewGeometry g=new CameraPreviewGeometry(w,h,640,480,new double[]{32,24,608,456},(flags&4)!=0?90:0,"0","BACK",1,1,1,360,260,values(sourceToView),new double[]{0,0,360,260},0);
            Matrix snapshotToView=new Matrix();snapshotToView.setScale(w/640f,h/480f);snapshotToView.postConcat(sourceToView);
            TvCalibrationMagnifier.Source source=new TvCalibrationMagnifier.Source(new TvMagnifierGeometry(g,640,480,snapshotToView),new Object(),null,false);
            Bitmap[] copied={null};
            TvCalibrationMagnifier loupe=new TvCalibrationMagnifier(new View(RuntimeEnvironment.getApplication()),ignored->source,(s,dest,done)->{copy(logical,null,dest);copied[0]=dest;done.accept(PixelCopy.SUCCESS);},SystemClock::uptimeMillis,new float[]{120,80,4,6});
            for(double[] p:new double[][]{{.17,.19},{.81,.22},{.79,.77},{.21,.79},{.51,.53},{0,0},{1,1}}){
                double[] q=g.normalizedToView(p[0],p[1]);loupe.show(g,(float)q[0],(float)q[1]);assertTrue(loupe.hasCurrentPixels());
                Bitmap image=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);loupe.drawCamera(new Canvas(image));
                // Sample inside the image, away from the coordinate codec carry boundaries.
                int sx=p[0]==0?92:p[0]==1?28:60,sy=p[1]==0?68:p[1]==1?12:40;
                float lx=(float)(p[0]*w+(sx+.5-60)/(4*scale)),ly=(float)(p[1]*h+(sy+.5-40)/(4*scale));
                int expected=logical.getPixel(Math.max(0,Math.min(w-1,(int)lx)),Math.max(0,Math.min(h-1,(int)ly))),actual=image.getPixel(sx,sy);
                if(Math.abs(x(expected)-x(actual))>1||Math.abs(y(expected)-y(actual))>1){
                    float[] snapshot={(float)q[0]+(sx+.5f-60)/4,(float)q[1]+(sy+.5f-40)/4};source.geometry.viewToRaw.mapPoints(snapshot);
                    int c=copied[0].getPixel((int)snapshot[0],(int)snapshot[1]);
                    System.out.println("probe flags="+flags+" p="+java.util.Arrays.toString(p)+" logical="+lx+","+ly+" canonical="+java.util.Arrays.toString(snapshot)+" pixels expected="+x(expected)+","+y(expected)+" canonical="+x(c)+","+y(c)+" rendered="+x(actual)+","+y(actual));
                }
                assertEquals("X flags="+flags,x(expected),x(actual),1.01);assertEquals("Y flags="+flags,y(expected),y(actual),1.01);
                if(p[0]==0)assertEquals(0xff17212b,image.getPixel(8,40));if(p[0]==1)assertEquals(0xff17212b,image.getPixel(112,40));
                image.recycle();
            }
            // A contrast fiducial independently verifies the reticle centre without decoding
            // two rounds of 8-bit colour interpolation as if they were geometry shifts.
            Paint marker=new Paint(Paint.ANTI_ALIAS_FLAG);marker.setColor(Color.WHITE);new Canvas(logical).drawCircle(w/2f,h/2f,5,marker);
            loupe.dismiss();double[] centre=g.normalizedToView(.5,.5);loupe.show(g,(float)centre[0],(float)centre[1]);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(101));assertTrue(loupe.hasCurrentPixels());
            Bitmap markerImage=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);loupe.drawCamera(new Canvas(markerImage));double sumX=0,sumY=0,count=0;
            for(int py=0;py<80;py++)for(int px=0;px<120;px++)if(Color.blue(markerImage.getPixel(px,py))>240){sumX+=px+.5;sumY+=py+.5;count++;}
            assertTrue(count>0);assertEquals("fiducial X",60,sumX/count,4*scale);assertEquals("fiducial Y",40,sumY/count,4*scale);markerImage.recycle();
            int saved=proof.save();proof.translate((flags%4)*155,480+(flags/4)*65);proof.scale(1,.75f);loupe.drawWindow(proof);proof.restoreToCount(saved);
            if(flags==4)proof.drawBitmap(logical,new Rect(0,0,w,h),new Rect(180,10,460,440),new Paint(Paint.FILTER_BITMAP_FLAG));
            loupe.dismiss();logical.recycle();
        }
        File f=new File("build/reports/tv-magnifier/native-readback-regression.png");assertTrue(f.getParentFile().isDirectory()||f.getParentFile().mkdirs());
        try(FileOutputStream out=new FileOutputStream(f)){assertTrue(figure.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
    @Test public void popupStaysInsidePreviewAwayFromEditedCornerAndHeader(){
        Rect bounds=new Rect(30,290,375,605);
        for(int[] p:new int[][]{{35,295},{370,295},{370,600},{35,600}}){Rect window=TvCalibrationMagnifier.popupBounds(bounds,p[0],p[1],120,80,8);assertNotNull(window);assertTrue(bounds.contains(window));assertFalse(window.contains(p[0],p[1]));}
        assertNull(TvCalibrationMagnifier.popupBounds(new Rect(0,0,60,40),0,0,120,80,8));
    }
    @Test public void memoryLimitDoesNotAttemptReadback(){
        CameraPreviewGeometry g=new CameraPreviewGeometry(640,480,640,480,new double[]{0,0,640,480},0,"0","BACK",1,1,1,640,480,new double[]{1,0,0,0,1,0,0,0,1},new double[]{0,0,640,480});
        TvCalibrationMagnifier.Source source=new TvCalibrationMagnifier.Source(new TvMagnifierGeometry(g,8192,8192,new Matrix()),new Object(),null,false);int[] calls={0};
        TvCalibrationMagnifier l=new TvCalibrationMagnifier(new View(RuntimeEnvironment.getApplication()),ignored->source,(s,b,done)->calls[0]++,SystemClock::uptimeMillis,new float[]{120,80,4,6});
        l.show(g,100,100);assertEquals(0,calls[0]);assertFalse(l.hasCurrentPixels());l.dismiss();
    }
    @Test public void snapshotsReuseAtMostTwoBitmapsAndReleaseThemWhenClosed(){
        CameraPreviewGeometry g=new CameraPreviewGeometry(640,480,640,480,new double[]{0,0,640,480},0,"0","BACK",1,1,1,640,480,new double[]{1,0,0,0,1,0,0,0,1},new double[]{0,0,640,480});
        TvCalibrationMagnifier.Source source=new TvCalibrationMagnifier.Source(new TvMagnifierGeometry(g,640,480,new Matrix()),new Object(),null,false);
        java.util.Set<Bitmap> buffers=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());int[] calls={0};
        TvCalibrationMagnifier l=new TvCalibrationMagnifier(new View(RuntimeEnvironment.getApplication()),ignored->source,(s,b,done)->{buffers.add(b);calls[0]++;b.eraseColor(Color.RED);done.accept(PixelCopy.SUCCESS);},SystemClock::uptimeMillis,new float[]{120,80,4,6});
        l.show(g,100,100);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1000));
        assertTrue(calls[0]>=10);assertEquals(2,buffers.size());l.dismiss();for(Bitmap b:buffers)assertTrue(b.isRecycled());
    }
}
