package com.example.alpr_v1.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Size;
import android.view.PixelCopy;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import androidx.camera.view.PreviewView;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import com.example.alpr_v1.vision.Point2;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

/** PixelCopy is the only substituted boundary. Matrix composition and raster rendering are real. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32, qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class TvCalibrationMagnifierTest {
    private TvCalibrationMagnifier loupe;
    private ActivityController<Activity> activity;
    private final int width=120, height=80;
    private final float zoom=4;
    private TvCalibrationMagnifier.Source source;
    private CameraPreviewGeometry camera;
    private Bitmap board;
    private int requests, active, maxActive;
    private boolean delayed;
    private final List<java.util.function.IntConsumer> completions=new ArrayList<>();
    private final List<Rect> copies=new ArrayList<>();

    @After public void close(){if(loupe!=null)loupe.dismiss();if(activity!=null)activity.pause().stop().destroy();}

    private static Bitmap coordinates(int width,int height){
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        int[] pixels=new int[width*height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[y*width+x]=Color.rgb(x&255,y&255,(x>>8)|((y>>8)<<4));
        bitmap.setPixels(pixels,0,width,0,0,width,height);return bitmap;
    }
    private static int px(int color){return Color.red(color)+((Color.blue(color)&15)<<8);}
    private static int py(int color){return Color.green(color)+((Color.blue(color)>>4)<<8);}
    private static Matrix matrix(double[] values){Matrix m=new Matrix();float[] f=new float[9];for(int i=0;i<9;i++)f[i]=(float)values[i];m.setValues(f);return m;}
    private static double[] values(Matrix m){float[] f=new float[9];m.getValues(f);double[] d=new double[9];for(int i=0;i<9;i++)d[i]=f[i];return d;}
    private CameraPreviewGeometry geometry(int bufferW,int bufferH,double[] crop,int rotation,Matrix rawToView,boolean mirror,double cameraZoom){
        int w=(int)(rotation%180==0?crop[2]-crop[0]:crop[3]-crop[1]);
        int h=(int)(rotation%180==0?crop[3]-crop[1]:crop[2]-crop[0]);
        double[] sourceToView=CameraPreviewGeometry.multiply(values(rawToView),CameraPreviewGeometry.inverse(CameraPreviewGeometry.bufferToSource(crop,rotation)));
        return new CameraPreviewGeometry(w,h,bufferW,bufferH,crop,rotation,"synthetic",mirror?"FRONT":"BACK",cameraZoom,1,1,
                360,260,sourceToView,new double[]{0,0,360,260},rotation);
    }
    private void setup(Matrix rawToView,double[] crop,int rotation,boolean mirror){
        setup(rawToView,crop,rotation,mirror,640,480);
    }
    private void setup(Matrix rawToView,double[] crop,int rotation,boolean mirror,int rawWidth,int rawHeight){
        board=coordinates(rawWidth,rawHeight);
        camera=geometry(rawWidth,rawHeight,crop,rotation,rawToView,mirror,1);
        source=new TvCalibrationMagnifier.Source(new TvMagnifierGeometry(camera,rawWidth,rawHeight,rawToView),new Object(),null,false);
        View anchor=new View(RuntimeEnvironment.getApplication());
        loupe=new TvCalibrationMagnifier(anchor,g->source,(s,target,done)->{
            requests++;active++;maxActive=Math.max(active,maxActive);copies.add(new Rect(0,0,target.getWidth(),target.getHeight()));
            new Canvas(target).drawBitmap(board,0,0,null);
            java.util.function.IntConsumer finish=result->{active--;done.accept(result);};
            if(delayed)completions.add(finish);else finish.accept(PixelCopy.SUCCESS);
        },SystemClock::uptimeMillis,new float[]{width,height,zoom,8});
    }
    private void fit(int rotation,boolean mirror,boolean crop){
        double[] c=crop?new double[]{32,24,608,456}:new double[]{0,0,640,480};
        int w=(int)(rotation%180==0?c[2]-c[0]:c[3]-c[1]),h=(int)(rotation%180==0?c[3]-c[1]:c[2]-c[0]);
        float scale=Math.min(360f/w,260f/h);
        Matrix view=new Matrix();view.setScale(mirror?-scale:scale,scale);
        view.postTranslate((360-w*scale)/2+(mirror?w*scale:0),(260-h*scale)/2);
        Matrix raw=matrix(CameraPreviewGeometry.bufferToSource(c,rotation));raw.postConcat(view);setup(raw,c,rotation,mirror);
    }
    private Bitmap image(){Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);loupe.drawCamera(new Canvas(b));return b;}
    private void show(float x,float y){loupe.show(camera,x,y);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(101));assertTrue(loupe.hasCurrentPixels());}
    private void sample(Bitmap rendered,int x,int y,float vx,float vy){
        float[] raw={vx+(x+.5f-width/2f)/zoom,vy+(y+.5f-height/2f)/zoom};source.geometry.viewToRaw.mapPoints(raw);
        int color=rendered.getPixel(x,y);
        assertEquals("raw X",raw[0]-.5,px(color),1.0);assertEquals("raw Y",raw[1]-.5,py(color),1.0);
    }

    @Test public void scaledCameraPixelsUnderCornerReplaceDistantNativeRegion(){
        Matrix raw=new Matrix();raw.setScale(.5f,.5f);raw.postTranslate(20,10);setup(raw,new double[]{0,0,640,480},0,false);
        show(181,131);Bitmap rendered=image();sample(rendered,60,40,181,131);sample(rendered,35,20,181,131);
        // Previous origin-subtraction sent (181,131) directly to a 640x480 buffer: wrong region.
        assertTrue(Math.abs(px(rendered.getPixel(60,40))-181)>100);
        assertEquals(board.getWidth(),copies.get(0).width());assertEquals(1,maxActive);
    }
    @Test public void actualPixelsForAllRotationsCropMirrorAndFitCenter(){
        for(int rotation:new int[]{0,90,180,270})for(boolean mirror:new boolean[]{false,true})for(boolean crop:new boolean[]{false,true}){
            fit(rotation,mirror,crop);
            for(double[] n:new double[][]{{.17,.19},{.81,.22},{.79,.77},{.21,.79},{.51,.53}}){
                double[] q=camera.normalizedToView(n[0],n[1]);show((float)q[0],(float)q[1]);Bitmap b=image();
                sample(b,60,40,(float)q[0],(float)q[1]);sample(b,44,32,(float)q[0],(float)q[1]);b.recycle();
            }
            loupe.dismiss();
        }
    }
    @Test public void clippedEdgesStayAtReticleWithNeutralPaddingOnEachSide(){
        fit(0,false,false);
        for(double[] n:new double[][]{{0,.51},{1,.51},{.51,0},{.51,1},{0,0},{1,1}}){
            double[] q=camera.normalizedToView(n[0],n[1]);show((float)q[0],(float)q[1]);Bitmap b=image();
            int sx=n[0]==0?80:n[0]==1?40:60,sy=n[1]==0?60:n[1]==1?20:40;
            sample(b,sx,sy,(float)q[0],(float)q[1]);
            if(n[0]==0)assertEquals(0xff17212b,b.getPixel(5,40));
            if(n[0]==1)assertEquals(0xff17212b,b.getPixel(115,40));
            if(n[1]==0)assertEquals(0xff17212b,b.getPixel(60,5));
            if(n[1]==1)assertEquals(0xff17212b,b.getPixel(60,75));
            b.recycle();
        }
    }
    @Test public void copiedRegionOutsideAnalysisCropIsNotVisible(){
        fit(0,false,true);double[] q=camera.normalizedToView(0,.55);show((float)q[0],(float)q[1]);
        assertEquals(0xff17212b,image().getPixel(5,40));
    }
    @Test public void latestMoveIsCoalescedAndCachedPixelsNeverRecenterWrongRegion(){
        fit(0,false,false);delayed=true;loupe.show(camera,100,100);
        for(int i=0;i<30;i++)loupe.show(camera,220+i,150);
        assertEquals(1,requests);assertEquals(1,maxActive);assertFalse(loupe.hasCurrentPixels());
        completions.remove(0).accept(PixelCopy.SUCCESS);
        assertTrue("full snapshot covers the latest point without shifting its pixels",loupe.hasCurrentPixels());sample(image(),60,40,249,150);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(101));assertEquals(2,requests);
        completions.remove(0).accept(PixelCopy.SUCCESS);assertTrue(loupe.hasCurrentPixels());sample(image(),60,40,249,150);
    }
    @Test public void rateIsAtMostTenPerSecondAndOnlyOneCopyIsInFlight(){
        fit(0,false,false);show(170,120);int before=requests;
        for(int i=0;i<100;i++){loupe.show(camera,170+i*.01f,120);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(10));}
        assertTrue(requests-before<=10);assertEquals(1,maxActive);
    }
    @Test public void hideDuringCopyRejectsCallbackAndKeepsNextGestureSerialized(){
        fit(0,false,false);delayed=true;loupe.show(camera,100,100);loupe.dismiss();loupe.show(camera,230,160);
        assertEquals(1,requests);completions.remove(0).accept(PixelCopy.SUCCESS);assertFalse(loupe.hasCurrentPixels());
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(101));assertEquals(2,requests);assertEquals(1,maxActive);
        completions.remove(0).accept(PixelCopy.SUCCESS);assertTrue(loupe.hasCurrentPixels());sample(image(),60,40,230,160);
    }
    @Test public void changingSurfaceIdentityRejectsLatePixels(){
        fit(0,false,false);delayed=true;loupe.show(camera,160,110);
        source=new TvCalibrationMagnifier.Source(source.geometry,new Object(),null,false);
        completions.remove(0).accept(PixelCopy.SUCCESS);assertFalse(loupe.hasCurrentPixels());
        assertEquals(0xff17212b,image().getPixel(60,40));
    }
    @Test public void zoomTransformChangeRejectsBothCachedAndLatePixels(){
        fit(0,false,false);show(160,110);delayed=true;
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(101));
        camera=geometry(640,480,new double[]{0,0,640,480},0,source.geometry.rawToView,false,2);
        source=new TvCalibrationMagnifier.Source(new TvMagnifierGeometry(camera,640,480,source.geometry.rawToView),source.identity,null,false);
        loupe.show(camera,160,110);assertFalse(loupe.hasCurrentPixels());
        completions.remove(0).accept(PixelCopy.SUCCESS);assertFalse(loupe.hasCurrentPixels());
    }
    @Test public void slowOrFailedCaptureNeverBecomesCurrent(){
        fit(0,false,false);delayed=true;loupe.show(camera,160,110);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        completions.remove(0).accept(PixelCopy.SUCCESS);assertFalse(loupe.hasCurrentPixels());
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(101));completions.remove(0).accept(PixelCopy.ERROR_SOURCE_NO_DATA);assertFalse(loupe.hasCurrentPixels());
    }
    @Test public void missingSourceClearsOldPixelsAndRecovers(){
        fit(0,false,false);show(160,110);TvCalibrationMagnifier.Source original=source;source=null;
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(101));assertFalse(loupe.hasCurrentPixels());
        source=original;org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(101));assertTrue(loupe.hasCurrentPixels());
    }
    @Test public void dismissStopsAllScheduledCopies(){
        fit(0,false,false);show(160,110);loupe.dismiss();int count=requests;
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertEquals(count,requests);assertFalse(loupe.hasCurrentPixels());
    }

    private static void field(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
    private Object sdk(int displayRotation,boolean front)throws Exception{
        Class<?> type=Class.forName("androidx.camera.view.PreviewTransformation");Constructor<?> c=type.getDeclaredConstructor();c.setAccessible(true);Object sdk=c.newInstance();
        field(sdk,"mResolution",new Size(640,480));field(sdk,"mSurfaceCropRect",new Rect(32,24,608,456));
        field(sdk,"mPreviewRotationDegrees",displayRotation);field(sdk,"mTargetRotation",displayRotation/90);
        field(sdk,"mIsFrontCamera",front);field(sdk,"mHasCameraTransform",true);field(sdk,"mSensorToBufferTransform",new Matrix());
        field(sdk,"mScaleType",PreviewView.ScaleType.FIT_CENTER);return sdk;
    }
    @Test @Config(sdk={29,36}) public void surfaceCorrectionMatchesPinnedCameraXSnapshotForEveryDisplayRotation()throws Exception{
        for(int r:new int[]{0,90,180,270}){
            Matrix expected=(Matrix)invoke(sdk(r,false),"getTextureViewCorrectionMatrix",new Class<?>[]{});
            assertArrayEquals(values(expected),values(TvMagnifierGeometry.surfaceCorrection(640,480,r)),.0001);
        }
    }
    @Test public void fullViewChainMatchesCameraXSnapshotPixelsForSurfaceAndTexture()throws Exception{
        activity=Robolectric.buildActivity(Activity.class).setup();Activity a=activity.get();
        FrameLayout root=new FrameLayout(a),preview=new FrameLayout(a);View overlay=new View(a);a.setContentView(root);
        root.addView(preview);root.addView(overlay);root.layout(0,0,500,700);preview.layout(17,31,377,291);overlay.layout(17,31,377,291);
        Bitmap raw=coordinates(640,480);
        for(boolean texture:new boolean[]{false,true})for(int rotation:new int[]{0,90,180,270})for(boolean front:new boolean[]{false,true}){
            preview.removeAllViews();View inner=texture?new TextureView(a):new SurfaceView(a);preview.addView(inner);inner.layout(0,0,640,480);
            Object transform=sdk(rotation,front);
            invoke(transform,"transformView",new Class<?>[]{Size.class,int.class,View.class},new Size(360,260),View.LAYOUT_DIRECTION_LTR,inner);
            Matrix correction=texture?((TextureView)inner).getTransform(new Matrix()):TvMagnifierGeometry.surfaceCorrection(640,480,rotation);
            Matrix mapping=TvCalibrationMagnifier.rawToOverlay(inner,overlay,640,480,correction);
            Bitmap expected=(Bitmap)invoke(transform,"createTransformedBitmap",new Class<?>[]{Bitmap.class,Size.class,int.class},raw,new Size(360,260),View.LAYOUT_DIRECTION_LTR);
            Bitmap actual=Bitmap.createBitmap(360,260,Bitmap.Config.ARGB_8888);new Canvas(actual).drawBitmap(raw,mapping,new Paint(Paint.FILTER_BITMAP_FLAG));
            for(int y=45;y<220;y+=23)for(int x=50;x<310;x+=29){
                int p=expected.getPixel(x,y),q=actual.getPixel(x,y);
                assertEquals("SDK X texture="+texture+" rotation="+rotation,px(p),px(q),1);assertEquals("SDK Y",py(p),py(q),1);
            }
            expected.recycle();actual.recycle();
        }
    }
    @Test public void rawSurfaceDimensionsAndAncestorMatricesAreIncluded(){
        activity=Robolectric.buildActivity(Activity.class).setup();Activity a=activity.get();FrameLayout root=new FrameLayout(a),parent=new FrameLayout(a);
        View child=new View(a),overlay=new View(a);a.setContentView(root);root.addView(parent);root.addView(overlay);parent.addView(child);
        root.layout(0,0,500,700);parent.layout(20,30,400,600);parent.setScaleX(.8f);parent.setScaleY(.7f);parent.setRotation(6);
        overlay.layout(10,25,370,285);child.layout(0,0,640,480);child.setPivotX(0);child.setPivotY(0);child.setScaleX(.5f);child.setScaleY(.5f);child.setTranslationX(7);child.setTranslationY(11);
        Matrix m=TvCalibrationMagnifier.rawToOverlay(child,overlay,1280,960,new Matrix());
        float[] actual={644,482};m.mapPoints(actual);
        float[] expected={322,241};Matrix global=new Matrix();child.transformMatrixToGlobal(global);global.mapPoints(expected);Matrix local=new Matrix();overlay.transformMatrixToLocal(local);local.mapPoints(expected);
        assertArrayEquals(expected,actual,.001f);
    }
    @Test public void rawBufferTwiceViewSizeStillShowsCorrectCameraPixels(){
        Matrix raw=new Matrix();raw.setScale(.25f,.25f);raw.postTranslate(20,10);
        setup(raw,new double[]{0,0,1280,960},0,false,1280,960);show(181,131);
        sample(image(),60,40,181,131);sample(image(),75,50,181,131);
    }
    private void touch(TvCalibrationOverlayView overlay,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(0,SystemClock.uptimeMillis(),action,x,y,0);
        assertTrue(overlay.onTouchEvent(event));event.recycle();
    }
    private TvCalibrationOverlayView editor()throws Exception{
        TvCalibrationOverlayView overlay=new TvCalibrationOverlayView(RuntimeEnvironment.getApplication(),null);
        overlay.layout(0,0,360,260);overlay.configure(new PreviewView(RuntimeEnvironment.getApplication()),()->{});
        overlay.setGeometry(camera);overlay.setCorners(java.util.Arrays.asList(new Point2(.3f,.3f),new Point2(.8f,.3f),new Point2(.8f,.7f),new Point2(.3f,.7f)));
        overlay.setEditing(true);field(overlay,"magnifier",loupe);return overlay;
    }
    @Test public void loupeFollowsSelectedCornerAfterGrabOffsetAndClampAndStopsOnUp()throws Exception{
        fit(0,false,false);TvCalibrationOverlayView overlay=editor();double[] initial=camera.normalizedToView(.3,.3);
        touch(overlay,MotionEvent.ACTION_DOWN,(float)initial[0]+7,(float)initial[1]+5);
        touch(overlay,MotionEvent.ACTION_MOVE,-40,-40);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(101));
        double[] edge=camera.normalizedToView(overlay.selectedPosition().x,overlay.selectedPosition().y);
        assertEquals(0,overlay.selectedPosition().x,.0001);assertEquals(0,overlay.selectedPosition().y,.0001);
        assertTrue(loupe.hasCurrentPixels());sample(image(),82,59,(float)edge[0],(float)edge[1]);
        touch(overlay,MotionEvent.ACTION_UP,-40,-40);assertFalse(loupe.hasCurrentPixels());
        assertEquals(0,overlay.corners().get(0).x,0);assertEquals(0,overlay.corners().get(0).y,0);
    }
    @Test public void cancelAndBackgroundDiscardLateImageWithoutChangingCancelSemantics()throws Exception{
        for(boolean background:new boolean[]{false,true}){
            fit(0,false,false);delayed=true;TvCalibrationOverlayView overlay=editor();double[] q=camera.normalizedToView(.3,.3);
            touch(overlay,MotionEvent.ACTION_DOWN,(float)q[0],(float)q[1]);touch(overlay,MotionEvent.ACTION_MOVE,150,140);
            if(background)overlay.onWindowVisibilityChanged(View.GONE);else touch(overlay,MotionEvent.ACTION_CANCEL,150,140);
            completions.remove(0).accept(PixelCopy.SUCCESS);assertFalse(loupe.hasCurrentPixels());
            assertEquals(.3,overlay.corners().get(0).x,.0001);assertEquals(.3,overlay.corners().get(0).y,.0001);
            int count=requests;org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));assertEquals(count,requests);
        }
    }
    @Test public void sourceLookupSkipsDecorationsAndHiddenOldSurface(){
        activity=Robolectric.buildActivity(Activity.class).setup();Activity a=activity.get();FrameLayout root=new FrameLayout(a);a.setContentView(root);
        root.addView(new View(a));SurfaceView old=new SurfaceView(a);old.setVisibility(View.GONE);root.addView(old);
        FrameLayout nested=new FrameLayout(a);root.addView(nested);TextureView active=new TextureView(a);nested.addView(active);
        assertSame(active,TvCalibrationMagnifier.findSurface(root));
    }
    @Test public void renderLabelledBoardAndLoupeAtCenterAndBoundary()throws Exception{
        fit(0,false,false);Bitmap figure=Bitmap.createBitmap(720,620,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(figure);canvas.drawColor(0xff0c131e);
        Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.WHITE);text.setTextSize(17);
        canvas.drawBitmap(board,40,35,null);for(int i=0;i<=600;i+=100)canvas.drawText("X="+i,40+i,25,text);
        for(int i=0;i<=400;i+=100)canvas.drawText("Y="+i,2,45+i,text);
        int left=30;
        for(double[] n:new double[][]{{.503,.507},{0,.51},{1,.51},{.51,0},{.51,1}}){
            double[] q=camera.normalizedToView(n[0],n[1]);show((float)q[0],(float)q[1]);int saved=canvas.save();canvas.translate(left,530);loupe.drawWindow(canvas);canvas.restoreToCount(saved);
            canvas.drawText("("+n[0]+", "+n[1]+")",left,520,text);left+=138;
        }
        File file=new File("build/reports/tv-magnifier/coordinate-board-and-loupes.png");assertTrue(file.getParentFile().isDirectory()||file.getParentFile().mkdirs());
        try(FileOutputStream out=new FileOutputStream(file)){assertTrue(figure.compress(Bitmap.CompressFormat.PNG,100,out));}
    }
}
