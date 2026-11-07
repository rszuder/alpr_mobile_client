package com.example.alpr_v1.setup;

import com.example.alpr_v1.vision.Point2;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Operator-owned TV setup, never a detection or a reference annotation. */
public final class TvSetupCalibration {
    public static final String SCHEMA="alpr.tv_setup_calibration.v1";
    public final List<Point2> corners;
    public final CameraPreviewGeometry reference;
    public final String confirmedAt;
    private final boolean restored;
    private final String missingReason;

    private TvSetupCalibration(List<Point2> points,CameraPreviewGeometry geometry,String time,boolean restored,String reason) {
        corners=Collections.unmodifiableList(new ArrayList<>(points));reference=geometry;
        confirmedAt=time;this.restored=restored;missingReason=reason;
    }
    public static TvSetupCalibration empty(){return new TvSetupCalibration(Collections.emptyList(),null,null,false,"NOT_CALIBRATED");}
    /** Invalidation is latched: returning to an old zoom/viewport still requires the operator. */
    public TvSetupCalibration invalidated(String reason) {
        return corners.isEmpty()?this:new TvSetupCalibration(corners,reference,confirmedAt,true,reason);
    }
    public static TvSetupCalibration confirm(List<Point2> points,CameraPreviewGeometry geometry,float density,String utc) {
        String error=validation(points,geometry,density);
        if(error!=null)throw new IllegalArgumentException(error);
        Instant.parse(utc);
        return new TvSetupCalibration(points,geometry,utc,false,"");
    }
    public String status(CameraPreviewGeometry current) {
        if(corners.isEmpty())return "UNCALIBRATED";
        return !restored&&reference!=null&&reference.sameGeometry(current)?"READY":"STALE";
    }
    public String reason(CameraPreviewGeometry current) {
        if(corners.isEmpty())return missingReason;
        if(restored)return missingReason.isEmpty()?"RESTART_REQUIRES_CONFIRMATION":missingReason;
        return "READY".equals(status(current))?"":"CAMERA_GEOMETRY_CHANGED";
    }
    public JSONObject snapshot(CameraPreviewGeometry current,TvDistanceCalculator.Inputs inputs,
            Double measured,TvDistanceCalculator.Estimate distance,String frozenAt) {
        try {
            JSONArray points=new JSONArray();
            for(Point2 p:corners)points.put(new JSONObject().put("x",p.x).put("y",p.y));
            JSONArray reasons=new JSONArray();String reason=reason(current);if(!reason.isEmpty())reasons.put(reason);
            JSONObject json=new JSONObject().put("schema",SCHEMA).put("status",status(current)).put("reason_codes",reasons)
                    .put("calibration_source","operator_manual_tv_corners").put("coordinate_space","source_frame_normalized_v1")
                    .put("corner_order","TL_TR_BR_BL").put("corners_norm",points)
                    .put("source_geometry",reference==null?JSONObject.NULL:reference.toJson())
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
            return new TvSetupCalibration(p,g,at,true,"");
        }catch(Exception e){return new TvSetupCalibration(Collections.emptyList(),null,null,false,"INVALID_STORED_CALIBRATION");}
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
