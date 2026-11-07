package com.example.alpr_v1.setup;

import com.example.alpr_v1.camera.CameraSelection;
import com.example.alpr_v1.camera.CameraSetupSnapshot;
import com.example.alpr_v1.setup.TvCornerEditGeometry.Position;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

/** Private UI recovery state. Never a confirmed calibration or session evidence. */
public final class TvEditDraft {
    public static final String PREFERENCE="tv_setup_edit_draft_v1",SCHEMA="alpr.tv_setup_edit_draft.v1";
    public final List<Position> points;
    public final int selected;
    public final CameraPreviewGeometry reference;
    public final CameraSetupSnapshot camera;
    public TvEditDraft(List<Position> points,int selected,CameraPreviewGeometry reference,CameraSetupSnapshot camera){
        if(points==null||points.size()!=4||selected<0||selected>3||reference==null)throw new IllegalArgumentException("Incomplete draft");
        for(Position p:points)if(p==null||!Double.isFinite(p.x)||!Double.isFinite(p.y)||p.x<0||p.x>1||p.y<0||p.y>1)
            throw new IllegalArgumentException("Invalid draft point");
        if(camera!=null&&(!camera.selection.logicalCameraId.equals(reference.cameraId)
                ||!Objects.equals(camera.selection.physicalCameraId,reference.physicalCameraId)||Math.abs(camera.zoomRatio-reference.zoomRatio)>1e-4))
            throw new IllegalArgumentException("Draft camera disagrees with source geometry");
        this.points=Collections.unmodifiableList(new ArrayList<>(points));this.selected=selected;this.reference=reference;this.camera=camera;
    }
    public JSONObject toJson(){try{
        JSONArray corners=new JSONArray();for(Position p:points)corners.put(new JSONObject().put("x",p.x).put("y",p.y));
        return SetupJson.seal(new JSONObject().put("schema",SCHEMA).put("coordinate_space","source_frame_normalized_v1")
                .put("corner_order","TL_TR_BR_BL").put("corners_norm",corners).put("selected_corner",selected)
                .put("source_geometry",reference.toJson()).put("camera_setup",camera==null?JSONObject.NULL:camera.toJson())
                .put("saved_at_utc",Instant.now().toString()));
    }catch(Exception e){throw new IllegalArgumentException("Invalid draft",e);}}
    public static TvEditDraft restore(String value){try{
        JSONObject j=new JSONObject(value);
        if(!SCHEMA.equals(j.getString("schema"))||!SetupJson.verified(j)||!"source_frame_normalized_v1".equals(j.getString("coordinate_space"))
                ||!"TL_TR_BR_BL".equals(j.getString("corner_order")))return null;
        Instant.parse(j.getString("saved_at_utc"));JSONArray a=j.getJSONArray("corners_norm");List<Position> points=new ArrayList<>();
        for(int i=0;i<a.length();i++)points.add(new Position(a.getJSONObject(i).getDouble("x"),a.getJSONObject(i).getDouble("y")));
        return new TvEditDraft(points,j.getInt("selected_corner"),CameraPreviewGeometry.fromJson(j.getJSONObject("source_geometry")),
                j.isNull("camera_setup")?null:CameraSetupSnapshot.fromJson(j.getJSONObject("camera_setup")));
    }catch(Exception e){return null;}}
    public static TvEditDraft fromCalibration(TvSetupCalibration calibration){
        if(calibration==null||calibration.corners.size()!=4||calibration.reference==null)return null;
        List<Position> p=new ArrayList<>();for(com.example.alpr_v1.vision.Point2 point:calibration.corners)p.add(new Position(point.x,point.y));
        CameraPreviewGeometry g=calibration.reference;
        return new TvEditDraft(p,0,g,new CameraSetupSnapshot(new CameraSelection(g.cameraId,g.physicalCameraId),(float)g.zoomRatio));
    }
    /** Coordinate compatibility is distinct from validity. A new binding never implies READY. */
    public static boolean sameCoordinates(CameraPreviewGeometry a,CameraPreviewGeometry b){
        if(a==null||b==null)return false;if(a==b)return true;
        if(a.sourceWidth!=b.sourceWidth||a.sourceHeight!=b.sourceHeight||a.bufferWidth!=b.bufferWidth||a.bufferHeight!=b.bufferHeight
                ||a.rotationDegrees!=b.rotationDegrees||a.displayRotationDegrees!=b.displayRotationDegrees||a.mirrored!=b.mirrored
                ||!a.cameraId.equals(b.cameraId)||!a.lensFacing.equals(b.lensFacing)||!Objects.equals(a.physicalCameraId,b.physicalCameraId)
                ||Math.abs(a.zoomRatio-b.zoomRatio)>1e-5)return false;
        try{JSONArray x=a.toJson().getJSONArray("crop_rect_px"),y=b.toJson().getJSONArray("crop_rect_px");
            for(int i=0;i<4;i++)if(Math.abs(x.getDouble(i)-y.getDouble(i))>1e-5)return false;
            return true;
        }catch(Exception e){return false;}
    }
}
