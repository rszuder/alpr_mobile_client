package com.example.alpr_v1.camera;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.util.SizeF;
import android.view.View;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.ZoomState;
import androidx.camera.view.PreviewView;
import androidx.camera.view.transform.OutputTransform;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import com.example.alpr_v1.R;
import com.example.alpr_v1.BuildConfig;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import com.example.alpr_v1.setup.SetupJson;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/** Read-only CameraX/Camera2 metadata bridge. No bitmap copies or camera requests. */
@ExperimentalCamera2Interop
public final class CameraGeometryProvider {
    public enum Availability { WAITING_FRAME, WAITING_TRANSFORM, READY, INVALID_TRANSFORM }
    /** Android UI state only. Does not add fields to research/session evidence. */
    public static final class GeometryState {
        public final Availability availability;
        public final CameraPreviewGeometry geometry;
        public final JSONObject distanceMetadata;
        public final String reasonCode,detail,distanceReason,diagnostics;
        public final int bindingGeneration;
        public final long frameTimestampNanos;
        private GeometryState(Availability availability,CameraPreviewGeometry geometry,JSONObject metadata,
                String code,String detail,String distanceReason,int binding,long timestamp){
            this(availability,geometry,metadata,code,detail,distanceReason,binding,timestamp,"");
        }
        private GeometryState(Availability availability,CameraPreviewGeometry geometry,JSONObject metadata,
                String code,String detail,String distanceReason,int binding,long timestamp,String diagnostics){
            this.availability=availability;this.geometry=geometry;distanceMetadata=metadata;
            reasonCode=code;this.detail=detail;this.distanceReason=distanceReason;
            bindingGeneration=binding;frameTimestampNanos=timestamp;
            this.diagnostics=diagnostics;
        }
        public static GeometryState ready(CameraPreviewGeometry geometry,JSONObject metadata,String reason){
            if(geometry==null)throw new IllegalArgumentException("READY requires geometry");
            return new GeometryState(Availability.READY,geometry,metadata,"","",reason==null?"":reason,geometry.bindingGeneration,0);
        }
        public static GeometryState waiting(Availability availability,String code,String detail,int binding){
            if(availability==Availability.READY)throw new IllegalArgumentException("Missing READY geometry");
            return new GeometryState(availability,null,null,code==null?"":code,detail==null?"":detail,"",binding,0);
        }
    }
    public interface Listener { void onGeometryState(GeometryState state); }
    // Device IO ports; production always reads Camera2/PreviewView. Tests control only hardware data.
    interface MetadataReader { String cameraId(); <T>T get(CameraCharacteristics.Key<T> key); }
    interface Inputs {
        MetadataReader cameraMetadata(Camera camera);
        Matrix sensorToView(PreviewView preview);
        default OutputTransform previewViewport(PreviewView preview){return preview.getOutputTransform();}
    }
    private static final Inputs LIVE_INPUTS=new Inputs(){
        public MetadataReader cameraMetadata(Camera camera){Camera2CameraInfo ci=Camera2CameraInfo.from(camera.getCameraInfo());
            return new MetadataReader(){public String cameraId(){return ci.getCameraId();}
                public <T>T get(CameraCharacteristics.Key<T> key){return ci.getCameraCharacteristic(key);}};}
        public Matrix sensorToView(PreviewView preview){return preview.getSensorToViewTransform();}
    };
    private final PreviewView preview;
    private final LifecycleOwner owner;
    private final Inputs inputs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean queued=new AtomicBoolean();
    private final AtomicReferenceArray<Capture> captures=new AtomicReferenceArray<>(8);
    private final AtomicInteger captureSequence=new AtomicInteger();
    private volatile Frame frame;
    private volatile FrameFailure frameFailure;
    private volatile int generation=-1;
    private volatile float zoom=1f;
    private Camera bound;
    private MetadataReader info;
    private Observer<ZoomState> zoomObserver;
    private Listener listener;
    private CameraPreviewGeometry published;
    private JSONObject metadata;
    private String stateKey="";
    private final AtomicBoolean forcePublication=new AtomicBoolean();
    private GeometryState latest;
    private long revision;
    private JSONObject diagnostics=new JSONObject();
    private Supplier<Size> previewViewportSize=()->null;
    private Supplier<AnalysisTransformResolver.RawGeometry> analysisGeometry=()->null;
    private String physicalCameraId;
    private String observedPhysicalCameraId;

    public CameraGeometryProvider(PreviewView preview,LifecycleOwner owner) {
        this(preview,owner,LIVE_INPUTS);
    }
    CameraGeometryProvider(PreviewView preview,LifecycleOwner owner,Inputs inputs) {
        this.preview=preview;this.owner=owner;this.inputs=inputs;
        preview.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->schedule());
        preview.getPreviewStreamState().observe(owner,state->schedule());
    }
    public void setListener(Listener listener){this.listener=listener;refresh();}
    public void refresh(){forcePublication.set(true);schedule();}
    public GeometryState latestState(){return latest;}
    public void onBound(Camera camera,int binding,Supplier<Size> viewportSize) {
        onBound(camera,binding,viewportSize,()->null,null);
    }
    public void onBound(Camera camera,int binding,Supplier<Size> viewportSize,
            Supplier<AnalysisTransformResolver.RawGeometry> analysisGeometry,String physicalCameraId) {
        detachZoom();bound=camera;generation=binding;frame=null;frameFailure=null;metadata=null;
        previewViewportSize=viewportSize;
        this.analysisGeometry=analysisGeometry;this.physicalCameraId=physicalCameraId;
        observedPhysicalCameraId=physicalCameraId;
        diagnostics=new JSONObject();
        for(int i=0;i<8;i++)captures.set(i,null);
        try{info=inputs.cameraMetadata(camera);}catch(RuntimeException e){info=null;}
        zoomObserver=state->{if(generation!=binding||state==null)return;
            if(Math.abs(zoom-state.getZoomRatio())>1e-5){zoom=state.getZoomRatio();unavailable(Availability.WAITING_FRAME,"ZOOM_CHANGED","");}
            schedule();};
        bound.getCameraInfo().getZoomState().observe(owner,zoomObserver);
        ZoomState state=bound.getCameraInfo().getZoomState().getValue();zoom=state==null?1f:state.getZoomRatio();
        unavailable(Availability.WAITING_FRAME,"CAMERA_REBOUND","");
    }
    public void onStopped(){detachZoom();bound=null;info=null;generation=-1;frame=null;frameFailure=null;metadata=null;unavailable(Availability.WAITING_FRAME,"CAMERA_UNAVAILABLE","");}
    private void detachZoom(){if(bound!=null&&zoomObserver!=null)bound.getCameraInfo().getZoomState().removeObserver(zoomObserver);zoomObserver=null;}
    private void unavailable(Availability availability,String code,String detail){
        published=null;metadata=null;
        deliver(new GeometryState(availability,null,null,code,detail,"",generation,frame==null?0:frame.timestamp,diagnosticText()),"");
    }
    private void deliver(GeometryState state,String metadataKey){
        String key=state.availability+":"+state.bindingGeneration+":"+state.reasonCode+":"+state.detail+":"
                +(state.geometry==null?0:state.geometry.viewportRevision)+":"+state.distanceReason+":"+metadataKey;
        boolean forced=forcePublication.getAndSet(false),changed=!key.equals(stateKey);latest=state;
        if(changed||forced){stateKey=key;
            if(BuildConfig.DEBUG){Frame f=frame;android.util.Log.d("ALPR_TV_GEOMETRY",state.availability+" "+state.reasonCode
                    +" binding="+generation+" view="+preview.getWidth()+"x"+preview.getHeight()
                    +" frame="+(f==null?"none":f.width+"x"+f.height+" crop="+f.crop+" rotation="+f.rotation)
                    +" detail="+state.detail+" diagnostics="+state.diagnostics.replace('\n',' '));}
            if(listener!=null)listener.onGeometryState(state);
        }
    }

    public CameraCaptureSession.CaptureCallback captureCallback(int binding) {
        return captureCallback(binding,null);
    }
    public CameraCaptureSession.CaptureCallback captureCallback(int binding,String selectedPhysicalId) {
        return new CameraCaptureSession.CaptureCallback(){
            @Override public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result) {
                if(generation!=binding)return;
                Long time=result.get(CaptureResult.SENSOR_TIMESTAMP);if(time==null)return;
                Float focal=result.get(CaptureResult.LENS_FOCAL_LENGTH);
                Rect crop=result.get(CaptureResult.SCALER_CROP_REGION);
                Float ratio=android.os.Build.VERSION.SDK_INT>=30?result.get(CaptureResult.CONTROL_ZOOM_RATIO):null;
                Integer stabilization=result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE);
                Integer optical=result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE);
                String activePhysical=result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                if(selectedPhysicalId!=null)activePhysical=physicalStreamIdentity(selectedPhysicalId,activePhysical,result.getPhysicalCameraResults().keySet());
                captures.set(captureSequence.getAndIncrement()&7,new Capture(time,focal,crop,ratio==null?1:ratio,
                        stabilization!=null&&stabilization!=0||optical!=null&&optical!=0,activePhysical));
                Frame f=frame;if(f!=null&&f.timestamp==time)schedule();
            }
        };
    }
    static String physicalStreamIdentity(String selected,String logicalActive,java.util.Set<String> physicalResults){
        // ACTIVE_PHYSICAL_ID describes the logical stream; an explicitly pinned output can differ.
        return selected!=null&&physicalResults.contains(selected)?selected:logicalActive;
    }
    /** Called before the original analyzer callback; never waits for UI/capture/IO. */
    public void observeFrame(ImageProxy image,int binding) {
        if(generation!=binding)return;
        try{
            float[] matrix=new float[9];image.getImageInfo().getSensorToBufferTransformMatrix().getValues(matrix);
            Frame next=new Frame(image.getWidth(),image.getHeight(),new Rect(image.getCropRect()),
                    image.getImageInfo().getRotationDegrees(),image.getImageInfo().getTimestamp(),matrix,binding);
            if(generation!=binding)return;frameFailure=null;frame=next;
        }catch(RuntimeException missingMetadata){
            // UI observation must not prevent the original analyzer/inference callback.
            if(generation!=binding)return;frame=null;frameFailure=new FrameFailure(binding,missingMetadata.getClass().getSimpleName());
        }
        schedule();
    }
    private void schedule(){if(queued.compareAndSet(false,true))main.post(()->{queued.set(false);publish();});}
    private Capture capture(long timestamp){for(int i=0;i<8;i++){Capture c=captures.get(i);if(c!=null&&c.timestamp==timestamp)return c;}return null;}

    private void publish() {
        Frame f=frame;MetadataReader ci=info;
        diagnostics=new JSONObject();
        diagnostic("binding_generation",generation);diagnostic("viewport_revision",revision);
        diagnostic("preview_size_px",new org.json.JSONArray().put(preview.getWidth()).put(preview.getHeight()));
        diagnostic("zoom_ratio",zoom);
        if(f!=null){diagnostic("frame_timestamp_nanos",f.timestamp);diagnostic("image_proxy_size_px",new org.json.JSONArray().put(f.width).put(f.height));
            diagnostic("image_crop_rect_px",rect(f.crop));diagnostic("remaining_rotation_degrees",f.rotation);
            diagnostic("sensor_to_buffer_matrix",SetupJson.array(f.matrix));}
        if(generation<0){unavailable(Availability.WAITING_FRAME,"CAMERA_UNAVAILABLE","");return;}
        FrameFailure failure=frameFailure;
        if(failure!=null&&failure.binding==generation){unavailable(Availability.INVALID_TRANSFORM,"FRAME_METADATA_UNAVAILABLE",failure.detail);return;}
        if(f==null||f.binding!=generation){unavailable(Availability.WAITING_FRAME,"FRAME_NOT_AVAILABLE","");return;}
        Capture captured=capture(f.timestamp);
        if(captured!=null&&captured.physicalCameraId!=null){
            if(physicalCameraId!=null&&!physicalCameraId.equals(captured.physicalCameraId)){
                unavailable(Availability.INVALID_TRANSFORM,"PHYSICAL_CAMERA_ID_MISMATCH","CaptureResult did not confirm selected physical camera");return;}
            observedPhysicalCameraId=captured.physicalCameraId;
        }
        if(ci==null){unavailable(Availability.INVALID_TRANSFORM,"CAMERA_INFO_UNAVAILABLE","");return;}
        if(preview.getWidth()<=0||preview.getHeight()<=0){unavailable(Availability.WAITING_TRANSFORM,"PREVIEW_NOT_MEASURED","");return;}
        Matrix sensorToView;
        try{sensorToView=inputs.sensorToView(preview);}
        catch(Exception e){unavailable(Availability.INVALID_TRANSFORM,"STOP_GEOMETRY",e.getClass().getSimpleName());return;}
        if(sensorToView==null){unavailable(Availability.WAITING_TRANSFORM,"PREVIEW_TRANSFORM_UNAVAILABLE","");return;}
        CameraPreviewGeometry candidate;
        try {
            diagnostic("camera_id",ci.cameraId());diagnostic("display_rotation_degrees",preview.getDisplay()==null?0:preview.getDisplay().getRotation()*90);
            diagnostic("scale_type",preview.getScaleType().name());diagnostic("implementation_mode",preview.getImplementationMode().name());
            if(!preview.getMatrix().isIdentity())throw new IllegalArgumentException("STOP_GEOMETRY: custom PreviewView matrix");
            OutputTransform viewport=inputs.previewViewport(preview);
            if(viewport==null){unavailable(Availability.WAITING_TRANSFORM,"PREVIEW_VIEWPORT_UNAVAILABLE","");return;}
            Size viewportSize=previewViewportSize.get();
            if(viewportSize==null){unavailable(Availability.WAITING_TRANSFORM,"PREVIEW_RESOLUTION_UNAVAILABLE","");return;}
            double[] c={f.crop.left,f.crop.top,f.crop.right,f.crop.bottom};
            int w=f.crop.width(),h=f.crop.height();if(f.rotation==90||f.rotation==270){int t=w;w=h;h=t;}
            AnalysisTransformResolver.RawGeometry raw=analysisGeometry.get();
            if(raw!=null){diagnostic("raw_analysis_size_px",new org.json.JSONArray().put(raw.width).put(raw.height));
                diagnostic("raw_analysis_crop_rect_px",rect(raw.crop));diagnostic("raw_analysis_rotation_degrees",raw.rotation);}
            AnalysisTransformResolver.Result resolved=AnalysisTransformResolver.resolve(f.matrix,f.width,f.height,f.crop,f.rotation,raw,ci.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
            diagnostic("effective_sensor_to_buffer_matrix",SetupJson.array(resolved.effective));diagnostic("sdk_140_cpu_rotation_corrected",resolved.corrected);
            double[] sensorToSource=CameraPreviewGeometry.multiply(CameraPreviewGeometry.bufferToSource(c,f.rotation),resolved.effective);
            double[] mapping=CameraPreviewGeometry.multiply(values(sensorToView),CameraPreviewGeometry.inverse(sensorToSource));
            diagnostic("sensor_to_view_matrix",SetupJson.array(values(sensorToView)));
            diagnostic("buffer_to_source_matrix",SetupJson.array(CameraPreviewGeometry.bufferToSource(c,f.rotation)));
            diagnostic("source_to_view_matrix",SetupJson.array(mapping));
            Integer facing=ci.get(CameraCharacteristics.LENS_FACING);
            diagnostic("lens_facing",facing==null?JSONObject.NULL:facing);
            diagnostic("source_bitmap_size_px",new org.json.JSONArray().put(w).put(h));
            validateViewport(mapping,w,h,viewport,viewportSize,facing!=null&&facing==CameraCharacteristics.LENS_FACING_FRONT);
            double[] usable={0,0,preview.getWidth(),preview.getHeight()};
            View toolbar=preview.getRootView().findViewById(R.id.top_app_bar);
            if(toolbar!=null&&toolbar.getVisibility()==View.VISIBLE){int[] p=new int[2],t=new int[2];preview.getLocationInWindow(p);toolbar.getLocationInWindow(t);usable[1]=Math.max(0,t[1]+toolbar.getHeight()-p[1]);}
            String lens=facing!=null&&facing==CameraCharacteristics.LENS_FACING_FRONT?"FRONT":facing!=null&&facing==CameraCharacteristics.LENS_FACING_BACK?"BACK":"EXTERNAL";
            candidate=new CameraPreviewGeometry(w,h,f.width,f.height,c,f.rotation,ci.cameraId(),lens,
                    zoom,generation,revision+1,preview.getWidth(),preview.getHeight(),mapping,usable,
                    preview.getDisplay()==null?0:preview.getDisplay().getRotation()*90);
            if(observedPhysicalCameraId!=null)candidate=candidate.withPhysicalCameraId(observedPhysicalCameraId);
        }catch(Exception e){unavailable(Availability.INVALID_TRANSFORM,"STOP_GEOMETRY",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());return;}
        boolean changed=published==null||published.previewWidth!=candidate.previewWidth
                ||published.previewHeight!=candidate.previewHeight||published.presentationDifferencePixels(candidate)>.25;
        if(changed){published=candidate;revision++;}
        // Optional distance metadata must never revoke an otherwise valid camera mapping.
        String reason="",key="";JSONObject next=null;
        try{
            int[] capabilities=ci.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean logical=false;if(capabilities!=null)for(int cap:capabilities)if(cap==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)logical=true;
            if(logical||physicalCameraId!=null)reason="Niepotwierdzona projekcja fizycznej kamery do strumienia; brak sprawdzonego FOV";
            else if(captured==null)reason="Brak metadanych Camera2 tej samej klatki";
            else if(captured.stabilized)reason="Stabilizacja kamery zmienia efektywny crop; brak sprawdzonego FOV";
            else {
                SizeF physical=ci.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                Size pixels=ci.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                Rect active=ci.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                Float focus=ci.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
                if(captured.focal==null||captured.crop==null||physical==null||pixels==null||active==null)
                    reason="Brak ogniskowej, sensora, aktywnego obszaru lub cropu";
                else next=new JSONObject().put("camera_id",ci.cameraId()).put("focal_length_mm",captured.focal)
                        .put("sensor_width_mm",physical.getWidth()).put("sensor_height_mm",physical.getHeight())
                        .put("pixel_array_width",pixels.getWidth()).put("pixel_array_height",pixels.getHeight())
                        .put("active_array_rect_px",rect(active)).put("capture_crop_rect_px",rect(captured.crop))
                        .put("control_zoom_ratio",captured.ratio).put("minimum_focus_distance_diopters",focus==null?JSONObject.NULL:focus)
                        .put("capture_timestamp_nanos",captured.timestamp).put("sensor_to_buffer_matrix",SetupJson.array(f.matrix))
                        .put("source","Camera2 CaptureResult matched by SENSOR_TIMESTAMP + CameraCharacteristics; CameraX ImageInfo");
            }
            JSONObject comparison=next==null?new JSONObject():new JSONObject(next.toString());comparison.remove("capture_timestamp_nanos");
            key=SetupJson.canonical(comparison);
        }catch(Exception e){next=null;reason="Brak wiarygodnych metadanych odległości ("+e.getClass().getSimpleName()+")";}
        metadata=next;
        // Listener failures are UI errors, not evidence of an invalid CameraX matrix.
        deliver(new GeometryState(Availability.READY,published,next,"","",reason,generation,f.timestamp,diagnosticText()),key);
    }
    /** Validate the actual SDK viewport, including independent integer rounding of stream crops.
     * The mapping itself always comes from camera matrices, never from these validation bounds. */
    private void validateViewport(double[] mapping,int width,int height,OutputTransform viewport,Size size,boolean front){
        double[] reference=values(viewport.getMatrix());
        diagnostic("preview_viewport_matrix",SetupJson.array(reference));
        diagnostic("preview_viewport_size_px",new org.json.JSONArray().put(size.getWidth()).put(size.getHeight()));
        double[] inverse=CameraPreviewGeometry.inverse(mapping);
        double left=Double.POSITIVE_INFINITY,top=left,right=Double.NEGATIVE_INFINITY,bottom=right;
        for(double[] point:new double[][]{{-1,-1},{1,-1},{1,1},{-1,1}}){
            double[] screen=CameraPreviewGeometry.map(reference,point[0],point[1]);
            double[] source=CameraPreviewGeometry.map(inverse,screen[0],screen[1]);
            left=Math.min(left,source[0]);top=Math.min(top,source[1]);right=Math.max(right,source[0]);bottom=Math.max(bottom,source[1]);
        }
        boolean swapped=Math.abs(reference[3])>Math.abs(reference[0]);
        double previewW=swapped?size.getHeight():size.getWidth(),previewH=swapped?size.getWidth():size.getHeight();
        if(previewW<=0||previewH<=0)throw new IllegalArgumentException("STOP_GEOMETRY: empty Preview ViewPort");
        // ViewPorts rounds each stream edge by <= 0.5 raw pixels. Express both errors in source pixels.
        double toleranceX=.5*(1+(right-left)/previewW)+.001,toleranceY=.5*(1+(bottom-top)/previewH)+.001;
        diagnostic("viewport_edges_in_source_px",SetupJson.array(new double[]{left,top,right,bottom}));
        diagnostic("crop_rounding_tolerance_source_px",SetupJson.array(new double[]{toleranceX,toleranceY}));
        if(Math.abs(left)>toleranceX||Math.abs(right-width)>toleranceX||Math.abs(top)>toleranceY||Math.abs(bottom-height)>toleranceY)
            throw new IllegalArgumentException("STOP_GEOMETRY: source/Preview ViewPort mismatch (crop rounding exceeded)");
        // The converter has already applied ImageInfo's remaining rotation. No second rotation is allowed.
        if((front?mapping[0]>=0:mapping[0]<=0)||mapping[4]<=0
                ||Math.abs(mapping[1]*height)>2||Math.abs(mapping[3]*width)>2
                ||Math.abs(mapping[6]*width)>1e-6||Math.abs(mapping[7]*height)>1e-6)
            throw new IllegalArgumentException("STOP_GEOMETRY: source/Preview orientation mismatch");
        double roundTrip=0;
        for(double[] point:new double[][]{{0,0},{width,0},{width,height},{0,height}}){
            double[] screen=CameraPreviewGeometry.map(mapping,point[0],point[1]);
            double[] back=CameraPreviewGeometry.map(inverse,screen[0],screen[1]);
            roundTrip=Math.max(roundTrip,Math.hypot(back[0]-point[0],back[1]-point[1]));
        }
        diagnostic("round_trip_max_source_px",roundTrip);
        if(roundTrip>2)throw new IllegalArgumentException("STOP_GEOMETRY: round trip exceeds 2 px");
    }
    private void diagnostic(String key,Object value){try{diagnostics.put(key,value);}catch(org.json.JSONException ignored){}}
    private String diagnosticText(){try{return diagnostics.toString(2);}catch(org.json.JSONException e){return diagnostics.toString();}}
    private static org.json.JSONArray rect(Rect r){return SetupJson.array(new double[]{r.left,r.top,r.right,r.bottom});}
    private static double[] values(Matrix m){float[] v=new float[9];m.getValues(v);double[] d=new double[9];for(int i=0;i<9;i++)d[i]=v[i];return d;}
    private static final class Frame {
        final int width,height,rotation,binding;final Rect crop;final long timestamp;final double[] matrix;
        Frame(int w,int h,Rect c,int r,long t,float[] m,int b){width=w;height=h;crop=c;rotation=r;timestamp=t;binding=b;matrix=new double[9];for(int i=0;i<9;i++)matrix[i]=m[i];}
    }
    private static final class FrameFailure {
        final int binding;final String detail;
        FrameFailure(int binding,String detail){this.binding=binding;this.detail=detail;}
    }
    private static final class Capture {
        final long timestamp;final Float focal;final Rect crop;final float ratio;final boolean stabilized;
        final String physicalCameraId;
        Capture(long t,Float f,Rect c,float r,boolean s,String physical){timestamp=t;focal=f;crop=c==null?null:new Rect(c);ratio=r;stabilized=s;physicalCameraId=physical;}
    }
}
