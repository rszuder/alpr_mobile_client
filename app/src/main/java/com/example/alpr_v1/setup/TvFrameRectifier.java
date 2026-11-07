package com.example.alpr_v1.setup;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import com.example.alpr_v1.continuity.SourceFrameStamp;
import com.example.alpr_v1.vision.Point2;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Test-only 14B prototype. No call sites in acquisition, pipeline, tracker, or session writer. */
public final class TvFrameRectifier {
    private TvFrameRectifier(){}
    public static final class Result {
        public final Bitmap image;
        public final SourceFrameStamp sourceStamp;
        public final long durationNanos;
        private final double[] h,inverse;
        private final int sourceWidth,sourceHeight;
        private final String geometryDigest,calibrationDigest;
        private final JSONObject sourceGeometry,calibrationSnapshot;
        private Result(Bitmap image,SourceFrameStamp stamp,long duration,double[] h,int w,int height,CameraPreviewGeometry geometry,TvSetupCalibration calibration){
            this.image=image;sourceStamp=stamp;durationNanos=duration;this.h=h.clone();inverse=CameraPreviewGeometry.inverse(h);
            sourceWidth=w;sourceHeight=height;sourceGeometry=geometry.toJson();geometryDigest=SetupJson.sha256(sourceGeometry);
            calibrationSnapshot=calibration.snapshot(geometry,null,null,TvDistanceCalculator.unavailable("SYNTHETIC_PROTOTYPE"),calibration.confirmedAt);
            calibrationDigest=calibrationSnapshot.optString("digest_sha256");
        }
        public double[] sourceToInput(double x,double y){return CameraPreviewGeometry.map(h,x,y);}
        public double[] inputToSource(double x,double y){return CameraPreviewGeometry.map(inverse,x,y);}
        /** ROI boxes are mapped as four corners, never promoted to model Pose. */
        public List<Point2> mapFourCorners(List<Point2> points,boolean toSource){
            if(points==null||points.size()!=4)throw new IllegalArgumentException("Expected four source corners");
            List<Point2> result=new ArrayList<>();for(Point2 p:points){double[] q=toSource?inputToSource(p.x,p.y):sourceToInput(p.x,p.y);result.add(new Point2((float)q[0],(float)q[1]));}return result;
        }
        public JSONObject geometryJson(){try{return new JSONObject().put("schema","alpr.tv_rectification.prototype.v1")
                .put("status","PROTOTYPE_TEST_ONLY").put("production_admission",ResearchInputMode.TV_BLOCK_REASON)
                .put("input_mode",ResearchInputMode.TV_RECTIFIED.name()).put("source_coordinate_space","camera_source_bitmap_xy")
                .put("input_coordinate_space","tv_rectified_bitmap_xy").put("source_width_px",sourceWidth).put("source_height_px",sourceHeight)
                .put("input_width_px",image.getWidth()).put("input_height_px",image.getHeight())
                .put("h_source_to_input",SetupJson.array(h)).put("h_input_to_source",SetupJson.array(inverse))
                .put("source_geometry_digest_sha256",geometryDigest).put("calibration_digest_sha256",calibrationDigest)
                .put("source_geometry",new JSONObject(sourceGeometry.toString())).put("calibration_snapshot",new JSONObject(calibrationSnapshot.toString()))
                .put("source_image_origin","camera_bitmap_after_crop_rotation_before_model_roi")
                .put("rectification_duration_nanos",durationNanos).put("interpolation","ANDROID_CANVAS_BILINEAR")
                .put("duration_clock","SYSTEM_NANO_TIME")
                .put("source_stamp",new JSONObject().put("source_sequence",sourceStamp.sourceSequence)
                        .put("source_timestamp_nanos",sourceStamp.sourceTimestampNanos).put("source_timestamp_domain",sourceStamp.domain.name())
                        .put("scene_generation",sourceStamp.sceneGeneration).put("visual_epoch",sourceStamp.visualEpoch)
                        .put("camera_transform_generation",sourceStamp.cameraTransformGeneration));
            }catch(org.json.JSONException error){throw new IllegalArgumentException(error);}}
    }
    public static Result rectifyPrototype(Bitmap source,SourceFrameStamp stamp,TvSetupCalibration calibration,
            CameraPreviewGeometry frameGeometry,CameraPreviewGeometry geometry,int width,int height){
        if(source==null||source.isRecycled()||stamp==null||geometry==null||calibration==null
                ||frameGeometry==null||!frameGeometry.sameGeometry(geometry)
                ||!"READY".equals(calibration.status(geometry))||source.getWidth()!=geometry.sourceWidth||source.getHeight()!=geometry.sourceHeight)
            throw new IllegalArgumentException("Unconfirmed or stale camera/calibration geometry");
        if(width<2||height<2||width>8192||height>8192||(long)width*height>16_777_216L)
            throw new IllegalArgumentException("Invalid prototype target size (maximum 16 megapixels)");
        long start=System.nanoTime();
        float[] points=new float[8];for(int i=0;i<4;i++){Point2 p=calibration.corners.get(i);points[2*i]=p.x*source.getWidth();points[2*i+1]=p.y*source.getHeight();}
        Matrix matrix=new Matrix();if(!matrix.setPolyToPoly(points,0,new float[]{0,0,width-1,0,width-1,height-1,0,height-1},0,4))
            throw new IllegalArgumentException("Singular TV homography");
        float[] values=new float[9];matrix.getValues(values);double[] h=new double[9];for(int i=0;i<9;i++)h[i]=values[i];
        CameraPreviewGeometry.inverse(h);
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(result);canvas.drawColor(Color.BLACK);canvas.drawBitmap(source,matrix,new Paint(Paint.FILTER_BITMAP_FLAG));
        return new Result(result,stamp,Math.max(0,System.nanoTime()-start),h,source.getWidth(),source.getHeight(),geometry,calibration);
    }
}
