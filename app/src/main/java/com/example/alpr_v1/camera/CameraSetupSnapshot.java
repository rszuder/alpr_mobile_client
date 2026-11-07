package com.example.alpr_v1.camera;

import org.json.JSONObject;

/** Confirmed camera and zoom at admission, independent of model settings and the TV quad. */
public final class CameraSetupSnapshot {
    public static final String SCHEMA="alpr.camera_setup.v1",PREFERENCE="tv_camera_setup_v1";
    public final CameraSelection selection;
    public final float zoomRatio;
    public CameraSetupSnapshot(CameraSelection selection,float zoom){
        if(selection==null||selection.isDefault()||!Float.isFinite(zoom)||zoom<=0)throw new IllegalArgumentException("Unconfirmed camera setup");
        this.selection=selection;zoomRatio=zoom;
    }
    public JSONObject toJson(){try{return new JSONObject().put("schema",SCHEMA).put("logical_camera_id",selection.logicalCameraId)
            .put("physical_camera_id",selection.physicalCameraId==null?JSONObject.NULL:selection.physicalCameraId).put("zoom_ratio",zoomRatio);
        }catch(org.json.JSONException e){throw new IllegalArgumentException(e);}}
    public static CameraSetupSnapshot fromJson(JSONObject json){try{
        if(!SCHEMA.equals(json.getString("schema")))throw new IllegalArgumentException("Unknown camera setup");
        return new CameraSetupSnapshot(new CameraSelection(json.getString("logical_camera_id"),json.isNull("physical_camera_id")?null:json.getString("physical_camera_id")),(float)json.getDouble("zoom_ratio"));
    }catch(Exception e){throw new IllegalArgumentException("Invalid camera setup",e);}}
    public static CameraSetupSnapshot restore(String value){try{return fromJson(new JSONObject(value));}catch(Exception e){return null;}}
    public boolean same(CameraSetupSnapshot other){return other!=null&&selection.equals(other.selection)&&Math.abs(zoomRatio-other.zoomRatio)<1e-4;}
}
