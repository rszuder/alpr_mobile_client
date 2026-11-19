package com.example.alpr_v1.setup;

import com.example.alpr_v1.camera.CameraSetupReadiness;
import com.example.alpr_v1.camera.CameraSetupSnapshot;
import com.example.alpr_v1.vision.Point2;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Operator approval is durable; live camera compatibility is a separate, revocable fact. */
public final class TvSetupCalibration {
    public static final String SCHEMA="alpr.tv_setup_calibration.v1";
    public final List<Point2> corners;
    /** Geometry at the original manual confirmation, never rewritten by UI resizing/rebinding. */
    public final CameraPreviewGeometry reference;
    public final String confirmedAt;
    private final CameraPreviewGeometry validated;
    private final boolean reusable;
    private final String missingReason,approvalSnapshot,revalidatedAt;

    private TvSetupCalibration(List<Point2> points,CameraPreviewGeometry origin,String time,
            CameraPreviewGeometry validated,boolean reusable,String reason,String approval,String revalidatedAt) {
        corners=Collections.unmodifiableList(new ArrayList<>(points));reference=origin;confirmedAt=time;
        this.validated=validated;this.reusable=reusable;missingReason=reason;approvalSnapshot=approval;this.revalidatedAt=revalidatedAt;
    }
    public static TvSetupCalibration empty(){return new TvSetupCalibration(Collections.emptyList(),null,null,null,false,"NOT_CALIBRATED",null,null);}
    /** Explicit hard invalidation remains latched; a camera poll cannot undo it. */
    public TvSetupCalibration invalidated(String reason) {
        return corners.isEmpty()?this:new TvSetupCalibration(corners,reference,confirmedAt,null,false,reason,approvalSnapshot,revalidatedAt);
    }
    /** Technical loss of readiness preserves the operator's original approval. */
    public TvSetupCalibration awaitingVerification(String reason) {
        if(corners.isEmpty()||!reusable)return this;
        if(validated==null&&missingReason.equals(reason))return this;
        return new TvSetupCalibration(corners,reference,confirmedAt,null,true,reason,approvalSnapshot,revalidatedAt);
    }
    public static TvSetupCalibration confirm(List<Point2> points,CameraPreviewGeometry geometry,float density,String utc) {
        String error=validation(points,geometry,density);
        if(error!=null)throw new IllegalArgumentException(error);
        Instant.parse(utc);
        TvSetupCalibration value=new TvSetupCalibration(points,geometry,utc,geometry,true,"",null,null);
        // A sealed representation of this actual manual approval; the UI attaches its exact persisted record below.
        return value.withStoredApproval(value.snapshot(geometry,null,null,TvDistanceCalculator.unavailable("NOT_USED_FOR_TV_BBOX_INPUT"),utc).toString());
    }
    public TvSetupCalibration withStoredApproval(String text) {
        TvSetupCalibration saved=restore(text);
        if(!saved.reusable||reference==null||!reference.sameGeometry(saved.reference)||!confirmedAt.equals(saved.confirmedAt)
                ||!pointsEqual(corners,saved.corners))throw new IllegalArgumentException("STORED_APPROVAL_CONFLICT");
        return new TvSetupCalibration(corners,reference,confirmedAt,validated,reusable,missingReason,text,revalidatedAt);
    }
    private static boolean pointsEqual(List<Point2> a,List<Point2> b){
        if(a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++)if(Float.compare(a.get(i).x,b.get(i).x)!=0||Float.compare(a.get(i).y,b.get(i).y)!=0)return false;
        return true;
    }
    public boolean reused(){return revalidatedAt!=null;}
    /** No source/pixel transformations: only an exact compatible source may reuse approval. */
    public TvSetupCalibration revalidated(CameraPreviewGeometry current,CameraSetupReadiness camera,long elapsedNanos,String utc) {
        if(!reusable||corners.isEmpty())return this;
        if(camera==null)return awaitingVerification("CAMERA_SETUP_SNAPSHOT_MISSING");
        // Stream freshness gates START separately; a gap cannot erase an already verified mapper.
        if(current!=null&&camera.bindingGeneration==current.bindingGeneration&&"READY".equals(status(current))
                &&(camera.reason==CameraSetupReadiness.Reason.NO_FRESH_CAMERA_FRAME||camera.ready()&&!camera.frameFreshAt(elapsedNanos)))return this;
        if(!camera.ready())return awaitingVerification(camera.reason.name());
        if(!camera.frameFreshAt(elapsedNanos))return awaitingVerification("NO_FRESH_CAMERA_FRAME");
        if(current==null)return awaitingVerification("CAMERA_SETUP_GEOMETRY_MISMATCH");
        if(camera.bindingGeneration!=current.bindingGeneration)return awaitingVerification("CAMERA_SETUP_GENERATION_CHANGED");
        CameraSetupSnapshot applied=camera.applied;
        if(!current.cameraId.equals(applied.selection.logicalCameraId)
                ||!java.util.Objects.equals(current.physicalCameraId,applied.selection.physicalCameraId)
                ||Math.abs(current.zoomRatio-applied.zoomRatio)>=1e-5)return awaitingVerification("CAMERA_SETUP_GEOMETRY_MISMATCH");
        if(!TvEditDraft.sameCoordinates(reference,current))return awaitingVerification("TV_CALIBRATION_SOURCE_CHANGED");
        if(!confirmationTimeValid(utc))return awaitingVerification("CONFIRMATION_TIME_IN_FUTURE");
        if(approvalSnapshot==null||!SetupJson.verified(approval()))return invalidated("INVALID_STORED_CALIBRATION");
        if("READY".equals(status(current)))return this;
        return new TvSetupCalibration(corners,reference,confirmedAt,current,true,"",approvalSnapshot,utc);
    }
    public JSONObject approval(){
        try{return approvalSnapshot==null?null:new JSONObject(approvalSnapshot);}
        catch(org.json.JSONException e){throw new IllegalStateException("INVALID_STORED_CALIBRATION",e);}
    }
    /** Read-only persisted timestamp check, shared by live admission and Options. */
    public boolean confirmationTimeValid(String utc){
        return confirmedAt!=null&&!Instant.parse(confirmedAt).isAfter(Instant.parse(utc));
    }
    public String status(CameraPreviewGeometry current) {
        if(corners.isEmpty())return "UNCALIBRATED";
        return validated!=null&&validated.sameGeometry(current)?"READY":"STALE";
    }
    public String reason(CameraPreviewGeometry current) {
        if("READY".equals(status(current)))return "";
        return missingReason.isEmpty()?"CAMERA_GEOMETRY_CHANGED":missingReason;
    }
    public JSONObject snapshot(CameraPreviewGeometry current,TvDistanceCalculator.Inputs inputs,
            Double measured,TvDistanceCalculator.Estimate distance,String frozenAt) {
        try {
            JSONArray points=new JSONArray();
            for(Point2 p:corners)points.put(new JSONObject().put("x",p.x).put("y",p.y));
            JSONArray reasons=new JSONArray();String reason=reason(current);if(!reason.isEmpty())reasons.put(reason);
            CameraPreviewGeometry evidence="READY".equals(status(current))?validated:reference;
            JSONObject json=new JSONObject().put("schema",SCHEMA).put("status",status(current)).put("reason_codes",reasons)
                    .put("calibration_source","operator_manual_tv_corners").put("coordinate_space","source_frame_normalized_v1")
                    .put("corner_order","TL_TR_BR_BL").put("corners_norm",points)
                    .put("source_geometry",evidence==null?JSONObject.NULL:evidence.toJson())
                    .put("confirmed_at_utc",confirmedAt==null?JSONObject.NULL:confirmedAt).put("frozen_at_utc",frozenAt)
                    .put("tv_dimensions",inputs==null?JSONObject.NULL:inputs.toJson())
                    .put("margin_each_edge",inputs==null?JSONObject.NULL:inputs.margin)
                    .put("measured_distance_m",measured==null?JSONObject.NULL:measured)
                    .put("distance_estimate",distance.toJson());
            return SetupJson.seal(json);
        }catch(org.json.JSONException e){throw new IllegalArgumentException(e);}
    }
    public static TvSetupCalibration restore(String text) {
        if(text==null||text.isEmpty())return empty();
        try {
            JSONObject j=new JSONObject(text);
            if(!SCHEMA.equals(j.getString("schema"))||!SetupJson.verified(j)
                    ||!"source_frame_normalized_v1".equals(j.getString("coordinate_space"))
                    ||!"TL_TR_BR_BL".equals(j.getString("corner_order"))
                    ||!"operator_manual_tv_corners".equals(j.getString("calibration_source"))
                    ||!java.util.Arrays.asList("READY","STALE","UNCALIBRATED").contains(j.getString("status")))throw new IllegalArgumentException();
            JSONArray a=j.getJSONArray("corners_norm");if(a.length()==0)return empty();
            List<Point2> p=new ArrayList<>();for(int i=0;i<a.length();i++)p.add(new Point2((float)a.getJSONObject(i).getDouble("x"),(float)a.getJSONObject(i).getDouble("y")));
            CameraPreviewGeometry g=CameraPreviewGeometry.fromJson(j.getJSONObject("source_geometry"));
            if(validation(p,g,1)!=null)throw new IllegalArgumentException();
            String at=j.getString("confirmed_at_utc");Instant.parse(at);
            boolean approved="READY".equals(j.getString("status"));
            return new TvSetupCalibration(p,g,at,null,approved,approved?"AWAITING_CAMERA_REVALIDATION":"STORED_CALIBRATION_REQUIRES_CONFIRMATION",text,null);
        }catch(Exception e){return new TvSetupCalibration(Collections.emptyList(),null,null,null,false,"INVALID_STORED_CALIBRATION",null,null);}
    }

    /** Screen-space thresholds are touch constraints, unrelated to MT quality or GT geometry. */
    public static String validation(List<Point2> points,CameraPreviewGeometry geometry,float density) {
        if(geometry==null)return "Brak aktualnej transformacji kamery";
        if(points==null||points.size()!=4)return "Wskaż cztery rogi: LG, PG, PD, LD";
        double area=0;double[][] view=new double[4][];
        for(int i=0;i<4;i++){
            Point2 a=points.get(i),b=points.get((i+1)%4),c=points.get((i+2)%4);
            if(a==null||b==null||c==null||!TvCornerEditGeometry.contains(geometry,a))return "Każdy róg musi leżeć na widocznym obrazie kamery, poza UI i letterboxem";
            double cross=(b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x);
            if(!Double.isFinite(cross)||cross<=1e-9)return "Rogi LG–PG–PD–LD muszą tworzyć wypukły czworokąt bez przecięć";
            view[i]=geometry.normalizedToView(a.x,a.y);
        }
        for(int i=0;i<4;i++){
            double[] a=view[i],b=view[(i+1)%4];area+=a[0]*b[1]-b[0]*a[1];
            if(Math.hypot(a[0]-b[0],a[1]-b[1])<24*Math.max(1,density))return "Rogi są zbyt blisko dla bezpiecznej edycji (min. 24 dp)";
        }
        if(Math.abs(area)/2<576*Math.max(1,density)*Math.max(1,density))return "Zbyt mały obrys do edycji";
        return null;
    }
}
