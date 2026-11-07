package com.example.alpr_v1.setup;

import android.graphics.Bitmap;
import com.example.alpr_v1.camera.CameraSetupSnapshot;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.Point2;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Immutable calibrated analysis domain. Camera coordinates remain canonical everywhere else. */
public final class TvBboxCrop {
    public static final String SCHEMA="alpr.tv_bbox_crop.v1";
    public static final String CAMERA_SPACE="camera_full_bitmap_xy",LOCAL_SPACE="tv_bbox_local_xy";
    public final int left,top,right,bottom,sourceWidth,sourceHeight;
    public final CameraPreviewGeometry geometry;
    public final CameraSetupSnapshot cameraSetup;
    public final String calibrationDigest,digest;
    private final String snapshot,calibration;

    private TvBboxCrop(JSONObject json){
        try {
            require(SCHEMA.equals(json.getString("schema"))&&SetupJson.verified(json),"TV_BBOX_INVALID_SNAPSHOT");
            require("TV_BBOX_CROP".equals(json.getString("input_mode"))
                    &&"CROP_ONLY_TRANSLATION".equals(json.getString("processing_transform"))
                    &&"NOT_APPLIED".equals(json.getString("H_TV"))
                    &&CAMERA_SPACE.equals(json.getString("source_coordinate_space"))
                    &&LOCAL_SPACE.equals(json.getString("input_coordinate_space")),"TV_BBOX_INVALID_SPACE");
            geometry=CameraPreviewGeometry.fromJson(json.getJSONObject("source_geometry"));
            cameraSetup=CameraSetupSnapshot.fromJson(json.getJSONObject("camera_setup"));
            JSONObject c=json.getJSONObject("calibration_snapshot");validateCalibration(c,geometry,cameraSetup);
            calibrationDigest=c.getString("digest_sha256");
            require(calibrationDigest.equals(json.getString("calibration_digest_sha256")),"TV_BBOX_CALIBRATION_DIGEST");
            int[] bounds=bounds(c.getJSONArray("corners_norm"),geometry.sourceWidth,geometry.sourceHeight);
            JSONObject b=json.getJSONObject("tv_bbox_px");
            left=integer(b,"left");top=integer(b,"top");right=integer(b,"right");bottom=integer(b,"bottom");
            sourceWidth=integer(json,"source_width_px");sourceHeight=integer(json,"source_height_px");
            require(sourceWidth==geometry.sourceWidth&&sourceHeight==geometry.sourceHeight
                    &&left==bounds[0]&&top==bounds[1]&&right==bounds[2]&&bottom==bounds[3]
                    &&integer(json,"input_width_px")==width()&&integer(json,"input_height_px")==height(),"TV_BBOX_INVALID_BOUNDS");
            JSONObject offset=json.getJSONObject("offset_to_camera_full");
            require(integer(offset,"x")==left&&integer(offset,"y")==top,"TV_BBOX_INVALID_OFFSET");
            require(SetupJson.canonical(c.getJSONArray("corners_norm")).equals(SetupJson.canonical(json.getJSONArray("corners_norm"))),"TV_BBOX_CORNERS_CONFLICT");
            snapshot=json.toString();calibration=c.toString();digest=json.getString("digest_sha256");
        }catch(Exception error){throw new IllegalArgumentException("Invalid TV BBOX snapshot",error);}
    }
    public int width(){return right-left;}
    public int height(){return bottom-top;}
    public JSONObject toJson(){try{return new JSONObject(snapshot);}catch(Exception e){throw new IllegalStateException(e);}}
    public JSONObject calibrationJson(){try{return new JSONObject(calibration);}catch(Exception e){throw new IllegalStateException(e);}}
    public static TvBboxCrop fromJson(JSONObject json){return new TvBboxCrop(json);}
    public static TvBboxCrop prepare(JSONObject confirmed,CameraPreviewGeometry current,CameraSetupSnapshot setup){
        try {
            validateCalibration(confirmed,current,setup);
            JSONArray corners=confirmed.getJSONArray("corners_norm");int[] b=bounds(corners,current.sourceWidth,current.sourceHeight);
            JSONObject json=new JSONObject().put("schema",SCHEMA).put("input_mode","TV_BBOX_CROP")
                    .put("processing_transform","CROP_ONLY_TRANSLATION").put("H_TV","NOT_APPLIED")
                    .put("source_coordinate_space",CAMERA_SPACE).put("input_coordinate_space",LOCAL_SPACE)
                    .put("source_width_px",current.sourceWidth).put("source_height_px",current.sourceHeight)
                    .put("corners_norm",new JSONArray(corners.toString()))
                    .put("tv_bbox_px",box(b[0],b[1],b[2],b[3]))
                    .put("input_width_px",b[2]-b[0]).put("input_height_px",b[3]-b[1])
                    .put("offset_to_camera_full",new JSONObject().put("x",b[0]).put("y",b[1]))
                    .put("calibration_digest_sha256",confirmed.getString("digest_sha256"))
                    .put("calibration_snapshot",new JSONObject(confirmed.toString()))
                    .put("source_geometry",current.toJson()).put("camera_setup",setup.toJson());
            return new TvBboxCrop(SetupJson.seal(json));
        }catch(Exception error){throw new IllegalArgumentException("TV_BBOX_CALIBRATION_REQUIRED",error);}
    }
    private static void validateCalibration(JSONObject c,CameraPreviewGeometry current,CameraSetupSnapshot setup)throws Exception{
        require(c!=null&&current!=null&&setup!=null&&SetupJson.verified(c)
                &&TvSetupCalibration.SCHEMA.equals(c.getString("schema"))&&"READY".equals(c.getString("status"))
                &&"source_frame_normalized_v1".equals(c.getString("coordinate_space"))
                &&"TL_TR_BR_BL".equals(c.getString("corner_order"))
                &&"operator_manual_tv_corners".equals(c.getString("calibration_source")),"TV_BBOX_NOT_READY");
        require(CameraPreviewGeometry.fromJson(c.getJSONObject("source_geometry")).sameGeometry(current),"TV_BBOX_STALE_GEOMETRY");
        require(current.cameraId.equals(setup.selection.logicalCameraId)
                &&java.util.Objects.equals(current.physicalCameraId,setup.selection.physicalCameraId)
                &&Math.abs(current.zoomRatio-setup.zoomRatio)<1e-5,"TV_BBOX_CAMERA_SETUP_MISMATCH");
        java.time.Instant.parse(c.getString("confirmed_at_utc"));
        bounds(c.getJSONArray("corners_norm"),current.sourceWidth,current.sourceHeight);
    }
    public static int[] bounds(JSONArray corners,int width,int height)throws Exception{
        require(width>0&&height>0&&corners!=null&&corners.length()==4,"TV_BBOX_INVALID_CORNERS");
        double[] x=new double[4],y=new double[4];double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(int i=0;i<4;i++){
            JSONObject p=corners.getJSONObject(i);x[i]=p.getDouble("x");y[i]=p.getDouble("y");
            require(Double.isFinite(x[i])&&Double.isFinite(y[i])&&x[i]>=0&&x[i]<=1&&y[i]>=0&&y[i]<=1,"TV_BBOX_INVALID_CORNERS");
            minX=Math.min(minX,x[i]*width);maxX=Math.max(maxX,x[i]*width);minY=Math.min(minY,y[i]*height);maxY=Math.max(maxY,y[i]*height);
        }
        for(int i=0;i<4;i++){
            int j=(i+1)%4,k=(i+2)%4;
            require((x[j]-x[i])*(y[k]-y[j])-(y[j]-y[i])*(x[k]-x[j])>1e-12,"TV_BBOX_INVALID_QUAD");
        }
        int l=Math.max(0,(int)Math.floor(minX)),t=Math.max(0,(int)Math.floor(minY));
        int r=Math.min(width,(int)Math.ceil(maxX)),b=Math.min(height,(int)Math.ceil(maxY));
        require(r>l&&b>t,"TV_BBOX_EMPTY");return new int[]{l,t,r,b};
    }
    public void requireCurrent(Bitmap source,CameraPreviewGeometry current,CameraSetupSnapshot applied){
        require(source!=null&&!source.isRecycled()&&source.getWidth()==sourceWidth&&source.getHeight()==sourceHeight
                &&geometry.sameGeometry(current)&&cameraSetup.same(applied),"TV_BBOX_SOURCE_GEOMETRY_CHANGED");
    }
    /** The local bitmap is a crop, without interpolation, masking, rotation or scaling. */
    public Frame open(Bitmap source,CameraPreviewGeometry current,CameraSetupSnapshot applied){
        requireCurrent(source,current,applied);long start=android.os.SystemClock.elapsedRealtimeNanos();
        Bitmap input=left==0&&top==0&&right==sourceWidth&&bottom==sourceHeight?source:Bitmap.createBitmap(source,left,top,width(),height());
        return new Frame(this,source,input,android.os.SystemClock.elapsedRealtimeNanos()-start);
    }
    public boolean sameSelection(TvBboxCrop other){
        try{return other!=null&&geometry.sameGeometry(other.geometry)&&cameraSetup.same(other.cameraSetup)
                &&SetupJson.canonical(toJson().getJSONArray("corners_norm")).equals(SetupJson.canonical(other.toJson().getJSONArray("corners_norm")));}
        catch(Exception e){return false;}
    }
    /** The only translation from TV-local detections to canonical camera detections. */
    public Detection toCamera(Detection local){
        List<Point2> points=new ArrayList<>();for(Point2 p:local.keypoints)points.add(new Point2(p.x+left,p.y+top,p.confidence));
        return new Detection(local.classId,local.confidence,local.left+left,local.top+top,local.right+left,local.bottom+top,points);
    }
    public static JSONObject box(int left,int top,int right,int bottom){
        try{return new JSONObject().put("left",left).put("top",top).put("right",right).put("bottom",bottom);}
        catch(Exception e){throw new IllegalArgumentException(e);}
    }
    private static int integer(JSONObject o,String key)throws Exception{
        Object n=o.opt(key);require((n instanceof Integer||n instanceof Long)&&((Number)n).longValue()>=Integer.MIN_VALUE&&((Number)n).longValue()<=Integer.MAX_VALUE,"TV_BBOX_INTEGER_REQUIRED");return ((Number)n).intValue();
    }
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalArgumentException(reason);}
    public static final class Frame implements AutoCloseable {
        public final TvBboxCrop crop;
        public final Bitmap camera,input;
        public final long cropElapsedNanos;
        private boolean closed;
        private Frame(TvBboxCrop crop,Bitmap camera,Bitmap input,long duration){this.crop=crop;this.camera=camera;this.input=input;cropElapsedNanos=duration;}
        @Override public void close(){if(!closed){closed=true;if(input!=camera&&!input.isRecycled())input.recycle();}}
    }
}
