package com.example.alpr_v1.experiment;

import com.example.alpr_v1.setup.SetupJson;
import com.example.alpr_v1.setup.TvBboxCrop;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional input domain, separate from the approved outer TV calibration. No homography. */
public final class Final180PhotoRoi {
    public static final String PROFILE="f6_final180_v1", ENABLED="f6_final180_enabled_v1", PREFERENCE="f6_photo_roi_v1";
    public final int left,top,right,bottom;
    private final String json;
    private Final180PhotoRoi(JSONObject value,TvBboxCrop tv) {
        try {
            if(tv==null||!"alpr.f6_photo_roi.v1".equals(value.getString("schema"))||!SetupJson.verified(value)
                    ||!tv.digest.equals(value.getString("tv_bbox_crop_digest_sha256"))
                    ||!TvBboxCrop.CAMERA_SPACE.equals(value.getString("coordinate_space"))
                    ||value.getInt("source_width_px")!=tv.sourceWidth||value.getInt("source_height_px")!=tv.sourceHeight)
                throw new IllegalArgumentException("F6_PHOTO_ROI_STALE");
            JSONObject b=value.getJSONObject("photo_roi_camera_xy");
            left=b.getInt("left");top=b.getInt("top");right=b.getInt("right");bottom=b.getInt("bottom");
            int[] actual=TvBboxCrop.bounds(value.getJSONArray("corners_norm"),tv.sourceWidth,tv.sourceHeight);
            if(left!=actual[0]||top!=actual[1]||right!=actual[2]||bottom!=actual[3]
                    ||left<tv.left||top<tv.top||right>tv.right||bottom>tv.bottom||right<=left||bottom<=top)
                throw new IllegalArgumentException("F6_PHOTO_ROI_OUTSIDE_TV");
            json=value.toString();
        } catch(Exception error) { throw new IllegalArgumentException("F6_PHOTO_ROI_REQUIRED_OR_STALE",error); }
    }
    public static Final180PhotoRoi fromJson(JSONObject value,TvBboxCrop tv){return new Final180PhotoRoi(value,tv);}
    /** Actual execution policy is independent of the operator's free-text variant label. */
    public static void requireExecution(JSONObject execution){
        try{
            JSONObject stages=execution.getJSONObject("stages");
            if(!PROFILE.equals(execution.getString("capture_profile"))
                    ||!"TV_BBOX_CROP".equals(execution.getString("input_mode"))
                    ||!"r0_full_frame".equals(execution.getString("roi_budget_policy"))
                    ||!Boolean.FALSE.equals(stages.getJSONObject("mp").get("enabled"))
                    ||!Boolean.TRUE.equals(stages.getJSONObject("mt").get("enabled"))
                    ||!Boolean.TRUE.equals(stages.getJSONObject("mz").get("enabled")))
                throw new IllegalArgumentException("F6 requires actual R0 without MP");
            TvBboxCrop tv=TvBboxCrop.fromJson(execution.getJSONObject("tv_bbox_crop"));
            if(!tv.cameraSetup.same(com.example.alpr_v1.camera.CameraSetupSnapshot.fromJson(execution.getJSONObject("camera_setup"))))
                throw new IllegalArgumentException("F6 camera setup conflict");
            fromJson(execution.getJSONObject("f6_photo_roi"),tv);
        }catch(Exception e){throw new IllegalArgumentException("F6_EXECUTION_INVALID: "+e.getMessage(),e);}
    }
    public static Final180PhotoRoi prepare(JSONArray corners,TvBboxCrop tv) {
        try {
            int[] b=TvBboxCrop.bounds(corners,tv.sourceWidth,tv.sourceHeight);
            return fromJson(SetupJson.seal(new JSONObject().put("schema","alpr.f6_photo_roi.v1")
                    .put("coordinate_space",TvBboxCrop.CAMERA_SPACE).put("corners_norm",new JSONArray(corners.toString()))
                    .put("source_width_px",tv.sourceWidth).put("source_height_px",tv.sourceHeight)
                    .put("tv_bbox_crop_digest_sha256",tv.digest).put("photo_roi_camera_xy",TvBboxCrop.box(b[0],b[1],b[2],b[3]))),tv);
        } catch(Exception error){throw new IllegalArgumentException("F6_PHOTO_ROI_INVALID",error);}
    }
    public JSONObject toJson(){try{return new JSONObject(json);}catch(Exception error){throw new IllegalStateException(error);}}
}
