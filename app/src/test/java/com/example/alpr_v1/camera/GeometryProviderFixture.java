package com.example.alpr_v1.camera;

import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.hardware.camera2.CameraCharacteristics;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.ImageInfo;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;
import androidx.camera.core.UseCase;
import androidx.camera.core.ViewPort;
import androidx.camera.core.impl.StreamSpec;
import androidx.camera.core.internal.ViewPorts;
import androidx.camera.core.ZoomState;
import androidx.camera.view.PreviewView;
import androidx.camera.view.transform.OutputTransform;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** Controlled hardware boundary; the production provider parses ImageProxy and publishes state. */
public final class GeometryProviderFixture {
    public final CameraGeometryProvider provider;
    public boolean transformAvailable=true,failDistanceMetadata,failIdentity,failFrameMetadata;
    public int width=1920,height=1080,rotation,binding=1;
    public Rect crop=new Rect(0,0,1920,1080);
    public Matrix sensorToBuffer=new Matrix(),expectedSensorToSource=new Matrix();
    public float viewOffsetX;
    public boolean sdkPreview,front;
    private Size sdkViewportSize;
    public Matrix sdkAnalysisToSensor=new Matrix(),sdkSensorToPreview=new Matrix();
    public Bitmap bitmap;
    public AnalysisTransformResolver.RawGeometry rawAnalysis;
    private Rect activeSensor;
    private final PreviewView preview;
    private long timestamp=1;
    private final MutableLiveData<ZoomState> zoomState=new MutableLiveData<>(zoom(1));
    private final Camera camera;

    public GeometryProviderFixture(PreviewView preview,LifecycleOwner owner){
        this.preview=preview;
        CameraInfo info=(CameraInfo)Proxy.newProxyInstance(CameraInfo.class.getClassLoader(),new Class[]{CameraInfo.class},
                (p,m,args)->{if(m.getName().equals("getZoomState"))return zoomState;return objectMethod(p,m.getName(),args);});
        camera=(Camera)Proxy.newProxyInstance(Camera.class.getClassLoader(),new Class[]{Camera.class},
                (p,m,args)->{if(m.getName().equals("getCameraInfo"))return info;return objectMethod(p,m.getName(),args);});
        provider=new CameraGeometryProvider(preview,owner,new CameraGeometryProvider.Inputs(){
            public CameraGeometryProvider.MetadataReader cameraMetadata(Camera ignored){
                if(failIdentity)throw new IllegalStateException("Camera identity unavailable");
                return new CameraGeometryProvider.MetadataReader(){
                    public String cameraId(){return "synthetic-camera";}
                    @SuppressWarnings("unchecked") public <T>T get(CameraCharacteristics.Key<T> key){
                        if(key.equals(CameraCharacteristics.LENS_FACING))return (T)Integer.valueOf(front?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK);
                        if(key.equals(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE))return (T)activeSensor;
                        if(failDistanceMetadata&&key.equals(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))throw new IllegalStateException("FOV metadata unavailable");
                        return null;
                    }
                };
            }
            public Matrix sensorToView(PreviewView view){
                if(!transformAvailable)return null;
                if(sdkPreview){Matrix matrix=view.getSensorToViewTransform();if(matrix!=null)matrix.postTranslate(viewOffsetX,0);return matrix;}
                Matrix mapping=expectedSourceToView(view);mapping.preConcat(expectedSensorToSource);
                mapping.postTranslate(viewOffsetX,0);return mapping;
            }
            public OutputTransform previewViewport(PreviewView view){
                if(sdkPreview)return view.getOutputTransform();
                Matrix reference=expectedSourceToView(view),normalized=new Matrix();
                int w=rotation%180==0?crop.width():crop.height(),h=rotation%180==0?crop.height():crop.width();
                normalized.setRectToRect(new RectF(-1,-1,1,1),new RectF(0,0,w,h),Matrix.ScaleToFit.FILL);
                reference.preConcat(normalized);return new OutputTransform(reference,new Size(w,h));
            }
        });
    }
    /** CameraX computes both viewport crop rectangles and the actual PreviewView transform. */
    public void configureSdk(Rect sensor,Size analysisSize,Size previewSize,int degrees){
        rawAnalysis=null;
        activeSensor=new Rect(sensor);
        Preview p=new Preview.Builder().build();ImageAnalysis a=new ImageAnalysis.Builder().build();
        Map<UseCase,StreamSpec> specs=new HashMap<>();specs.put(p,StreamSpec.builder(previewSize).build());specs.put(a,StreamSpec.builder(analysisSize).build());
        Map<UseCase,Rect> crops=ViewPorts.calculateViewPortRects(sensor,front,new Rational(analysisSize.getWidth(),analysisSize.getHeight()),degrees,ViewPort.FIT,View.LAYOUT_DIRECTION_LTR,specs);
        width=analysisSize.getWidth();height=analysisSize.getHeight();rotation=degrees;crop=crops.get(a);
        Matrix analysisToSensor=new Matrix();analysisToSensor.setRectToRect(new RectF(0,0,width,height),new RectF(sensor),Matrix.ScaleToFit.CENTER);analysisToSensor.invert(sensorToBuffer);
        sdkAnalysisToSensor.set(analysisToSensor);
        Matrix previewToSensor=new Matrix(),sensorToPreview=new Matrix();
        previewToSensor.setRectToRect(new RectF(0,0,previewSize.getWidth(),previewSize.getHeight()),new RectF(sensor),Matrix.ScaleToFit.CENTER);previewToSensor.invert(sensorToPreview);
        sdkSensorToPreview.set(sensorToPreview);
        SurfaceRequest.TransformationInfo info=SurfaceRequest.TransformationInfo.of(crops.get(p),degrees,Surface.ROTATION_0,true,sensorToPreview,front);
        sdkViewportSize=new Size(crops.get(p).width(),crops.get(p).height());
        Object transform=ReflectionHelpers.getField(preview,"mPreviewTransform");
        ReflectionHelpers.callInstanceMethod(transform,"setTransformationInfo",ClassParameter.from(SurfaceRequest.TransformationInfo.class,info),ClassParameter.from(Size.class,previewSize),ClassParameter.from(boolean.class,front));
        sdkPreview=true;
    }
    public void applySdkCpuRotationMetadata() throws Exception {
        rawAnalysis=new AnalysisTransformResolver.RawGeometry(width,height,crop,rotation,true);
        Class<?> type=Class.forName("androidx.camera.core.ImageAnalysisBlockingAnalyzer");
        Object analyzer=ReflectionHelpers.callConstructor(type);
        ReflectionHelpers.callInstanceMethod(analyzer,"setRelativeRotation",ClassParameter.from(int.class,rotation));
        ReflectionHelpers.callInstanceMethod(analyzer,"setSensorToBufferTransformMatrix",ClassParameter.from(Matrix.class,sensorToBuffer));
        ReflectionHelpers.callInstanceMethod(analyzer,"setViewPortCropRect",ClassParameter.from(Rect.class,crop));
        int outWidth=rotation%180==0?width:height,outHeight=rotation%180==0?height:width;
        ReflectionHelpers.callInstanceMethod(analyzer,"recalculateTransformMatrixAndCropRect",ClassParameter.from(int.class,width),ClassParameter.from(int.class,height),ClassParameter.from(int.class,outWidth),ClassParameter.from(int.class,outHeight));
        sensorToBuffer=ReflectionHelpers.getField(analyzer,"mUpdatedSensorToBufferTransformMatrix");
        crop=new Rect((Rect)ReflectionHelpers.getField(analyzer,"mUpdatedViewPortCropRect"));
        width=outWidth;height=outHeight;rotation=0;
    }
    private static Object objectMethod(Object proxy,String name,Object[] args){
        if(name.equals("hashCode"))return System.identityHashCode(proxy);
        if(name.equals("equals"))return proxy==args[0];
        if(name.equals("toString"))return "Controlled CameraX input";
        throw new UnsupportedOperationException(name);
    }
    public Matrix expectedSourceToView(PreviewView preview){
        int w=rotation%180==0?crop.width():crop.height(),h=rotation%180==0?crop.height():crop.width();
        Matrix matrix=new Matrix();matrix.setRectToRect(new RectF(0,0,w,h),new RectF(0,0,preview.getWidth(),preview.getHeight()),Matrix.ScaleToFit.CENTER);return matrix;
    }
    public void bind(){provider.onBound(camera,binding,()->sdkPreview?sdkViewportSize:
            new Size(rotation%180==0?crop.width():crop.height(),rotation%180==0?crop.height():crop.width()),()->rawAnalysis,null);}
    public void setZoom(float value){zoomState.setValue(zoom(value));}
    private static ZoomState zoom(float value){return new ZoomState(){
        public float getZoomRatio(){return value;}public float getMinZoomRatio(){return 1;}
        public float getMaxZoomRatio(){return 4;}public float getLinearZoom(){return (value-1)/3;}
    };}
    public void frame(){provider.observeFrame(image(),binding);}
    public ImageProxy image(){
        final int w=width,h=height,r=rotation;final Rect c=new Rect(crop);final Matrix matrix=new Matrix(sensorToBuffer);final long stamp=timestamp++;
        ImageInfo info=(ImageInfo)Proxy.newProxyInstance(ImageInfo.class.getClassLoader(),new Class[]{ImageInfo.class},(p,m,args)->{
            switch(m.getName()){
                case "getSensorToBufferTransformMatrix":if(failFrameMetadata)throw new UnsupportedOperationException("No sensor matrix");return new Matrix(matrix);
                case "getRotationDegrees":return r;
                case "getTimestamp":return stamp;
                default:return objectMethod(p,m.getName(),args);
            }
        });
        return (ImageProxy)Proxy.newProxyInstance(ImageProxy.class.getClassLoader(),new Class[]{ImageProxy.class},(p,m,args)->{
            switch(m.getName()){
                case "getWidth":return w;case "getHeight":return h;case "getCropRect":return new Rect(c);
                case "getImageInfo":return info;case "getFormat":return ImageFormat.YUV_420_888;
                case "toBitmap":return bitmap.copy(Bitmap.Config.ARGB_8888,true);
                case "close":throw new AssertionError("Metadata observer must not close the analyzer image");
                default:return objectMethod(p,m.getName(),args);
            }
        });
    }
}
