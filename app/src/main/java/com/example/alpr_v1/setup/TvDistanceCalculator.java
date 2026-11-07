package com.example.alpr_v1.setup;

import com.example.alpr_v1.vision.Point2;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/** Setup estimates only. No camera control, bitmap, model, ROI or measurement-clock dependencies. */
public final class TvDistanceCalculator {
    private TvDistanceCalculator() {}
    public static final class Inputs {
        public final double widthM, heightM, diagonalIn, aspectWidth, aspectHeight, margin;
        public final String mode;
        private Inputs(double w,double h,double diagonal,double aw,double ah,double margin,String mode) {
            if(!positive(w)||!positive(h)||!positive(aw)||!positive(ah)||!Double.isFinite(margin)||margin<0||margin>=.5)
                throw new IllegalArgumentException("Podaj dodatnie wymiary i margines 0–49,99% na każdej krawędzi");
            widthM=w;heightM=h;diagonalIn=diagonal;aspectWidth=aw;aspectHeight=ah;this.margin=margin;this.mode=mode;
        }
        public static Inputs diagonal(double inches,double aw,double ah,double margin) {
            if(!positive(inches)||!positive(aw)||!positive(ah))throw new IllegalArgumentException("Nieprawidłowa przekątna lub proporcje");
            double hyp=Math.hypot(aw,ah),d=inches*.0254;
            return new Inputs(d*aw/hyp,d*ah/hyp,inches,aw,ah,margin,"DIAGONAL_IN");
        }
        public static Inputs sizeCm(double width,double height,double margin) {
            return new Inputs(width/100,height/100,Math.hypot(width,height)/2.54,width,height,margin,"SIZE_CM");
        }
        public JSONObject toJson() {
            try{return new JSONObject().put("width_m",widthM).put("height_m",heightM).put("diagonal_in",diagonalIn)
                    .put("aspect_width",aspectWidth).put("aspect_height",aspectHeight).put("input_mode",mode);}
            catch(org.json.JSONException e){throw new IllegalArgumentException(e);}
        }
    }
    public static final class Estimate {
        public final String method, reason;
        public final Double distanceM, fovH, fovV;
        private final JSONObject metadata;
        private Estimate(String method,Double d,Double h,Double v,String reason,JSONObject metadata) {
            this.method=method;distanceM=d;fovH=h;fovV=v;this.reason=reason;
            try{this.metadata=metadata==null?null:new JSONObject(metadata.toString());}
            catch(org.json.JSONException e){throw new IllegalArgumentException(e);}
        }
        public boolean available(){return distanceM!=null;}
        public JSONObject toJson() {
            try{return new JSONObject().put("status",available()?"ESTIMATED":"UNAVAILABLE").put("method",method)
                    .put("distance_m",distanceM==null?JSONObject.NULL:distanceM)
                    .put("effective_fov_h_degrees",fovH==null?JSONObject.NULL:fovH)
                    .put("effective_fov_v_degrees",fovV==null?JSONObject.NULL:fovV).put("reason",reason)
                    .put("assumptions",new JSONArray().put("Centralna, w przybliżeniu prostopadła geometria; sprawdzenie czterech rogów na żywo jest nadrzędne")
                            .put("Niepewność nie została zmierzona; perspektywa i metadane mogą ograniczać dokładność")
                            .put("Odległość kadrowania nie gwarantuje ostrości, czytelności znaków ani skuteczności ALPR")
                            .put("MEASURED_RATIO wymaga stałego kadrowania i zmierzonej odległości operatora"))
                    .put("camera_metadata",metadata==null?JSONObject.NULL:new JSONObject(metadata.toString()));}
            catch(org.json.JSONException e){throw new IllegalArgumentException(e);}
        }
    }
    public static Estimate unavailable(String reason){return new Estimate("NONE",null,null,null,reason,null);}
    public static Estimate fromFov(Inputs input,double h,double v) {
        if(input==null||!angle(h)||!angle(v))return unavailable("Brak poprawnych efektywnych kątów widzenia");
        double d=Math.max(input.widthM/(2*Math.tan(Math.toRadians(h)/2)*(1-2*input.margin)),
                input.heightM/(2*Math.tan(Math.toRadians(v)/2)*(1-2*input.margin)));
        if(!positive(d))return unavailable("Nieprawidłowy wynik geometrii");
        return new Estimate("CAMERA_METADATA",d,h,v,"Przybliżenie pinhole z efektywnego kadru; niepewność niezmierzona",null);
    }
    /** Effective cone is independent of TV dimensions and is shared by setup and distance UI. */
    public static final class FieldOfView {
        public final Double horizontalDegrees,verticalDegrees;
        public final String reason;
        private FieldOfView(Double h,Double v,String reason){horizontalDegrees=h;verticalDegrees=v;this.reason=reason;}
        public boolean available(){return horizontalDegrees!=null;}
        private static FieldOfView unavailable(String reason){return new FieldOfView(null,null,reason);}
    }
    public static Estimate fromCamera(Inputs input,JSONObject camera,CameraPreviewGeometry geometry) {
        FieldOfView fov=effectiveFov(camera,geometry);if(!fov.available())return unavailable(fov.reason);
        Estimate estimate=fromFov(input,fov.horizontalDegrees,fov.verticalDegrees);
        return new Estimate(estimate.method,estimate.distanceM,estimate.fovH,estimate.fovV,estimate.reason,camera);
    }
    /** Camera2 crop is post-control.zoomRatio on API 30+, so apply that ratio once only. */
    public static FieldOfView effectiveFov(JSONObject camera,CameraPreviewGeometry geometry) {
        if(camera==null||geometry==null)return FieldOfView.unavailable("Brak aktualnej geometrii lub metadanych aktywnej kamery");
        if(geometry.physicalCameraId!=null)return FieldOfView.unavailable("Brak sprawdzonej projekcji fizycznego obiektywu do aktywnego strumienia");
        try {
            if(!geometry.cameraId.equals(camera.getString("camera_id")))return FieldOfView.unavailable("Metadane innej kamery");
            double focal=camera.getDouble("focal_length_mm"),sw=camera.getDouble("sensor_width_mm"),sh=camera.getDouble("sensor_height_mm");
            double pw=camera.getDouble("pixel_array_width"),ph=camera.getDouble("pixel_array_height");
            double zoom=camera.getDouble("control_zoom_ratio");
            JSONArray ar=camera.getJSONArray("active_array_rect_px"),cr=camera.getJSONArray("capture_crop_rect_px");
            if(!positive(focal)||!positive(sw)||!positive(sh)||!positive(pw)||!positive(ph)||!positive(zoom))
                return FieldOfView.unavailable("Niepełna ogniskowa, sensor lub zoom w metadanych");
            double al=ar.getDouble(0),at=ar.getDouble(1),aw=ar.getDouble(2)-al,ah=ar.getDouble(3)-at;
            double cl=cr.getDouble(0),ct=cr.getDouble(1),cw=cr.getDouble(2)-cl,ch=cr.getDouble(3)-ct;
            if(!positive(aw)||!positive(ah)||!positive(cw)||!positive(ch)||cl<al||ct<at||cw>aw||ch>ah
                    ||Math.abs(cl+cw/2-al-aw/2)>1||Math.abs(ct+ch/2-at-ah/2)>1)
                return FieldOfView.unavailable("Nieznany lub niecentralny crop; brak wiarygodnego efektywnego FOV");
            double totalZoom=Math.max(aw/cw,ah/ch)*zoom;
            if(Math.abs(totalZoom-geometry.zoomRatio)>Math.max(.03,.03*geometry.zoomRatio))
                return FieldOfView.unavailable("Zoom CameraX i wynik Camera2 nie są zgodne");
            JSONArray bm=camera.getJSONArray("sensor_to_buffer_matrix");
            boolean bufferSwapped=Math.abs(bm.getDouble(1))>Math.abs(bm.getDouble(0));
            double aspect=bufferSwapped?geometry.bufferHeight/(double)geometry.bufferWidth:geometry.bufferWidth/(double)geometry.bufferHeight;
            double w=cw/zoom,h=ch/zoom;
            if(w/h>aspect)w=h*aspect;else h=w/aspect;
            JSONArray crop=geometry.toJson().getJSONArray("crop_rect_px");
            double bw=crop.getDouble(2)-crop.getDouble(0),bh=crop.getDouble(3)-crop.getDouble(1);
            if(Math.abs(crop.getDouble(0)+bw/2-geometry.bufferWidth/2.0)>1
                    ||Math.abs(crop.getDouble(1)+bh/2-geometry.bufferHeight/2.0)>1)
                return FieldOfView.unavailable("Niecentralny viewport; brak sprawdzonej projekcji efektywnego FOV");
            w*=bufferSwapped?bh/geometry.bufferHeight:bw/geometry.bufferWidth;
            h*=bufferSwapped?bw/geometry.bufferWidth:bh/geometry.bufferHeight;
            double tanH=w*sw/pw/(2*focal),tanV=h*sh/ph/(2*focal);
            if(bufferSwapped ^ (geometry.rotationDegrees==90||geometry.rotationDegrees==270)) {
                double t=tanH;tanH=tanV;tanV=t;
            }
            // Conservative cone around the optical centre, contained in the useful preview pixels.
            double[] u=geometry.usableViewRect();
            double[] a=geometry.viewToNormalized(u[0],u[1]),b=geometry.viewToNormalized(u[2],u[3]);
            double fx=2*Math.min(.5-Math.min(a[0],b[0]),Math.max(a[0],b[0])-.5);
            double fy=2*Math.min(.5-Math.min(a[1],b[1]),Math.max(a[1],b[1])-.5);
            if(!positive(fx)||!positive(fy))return FieldOfView.unavailable("Użyteczny podgląd nie obejmuje osi kamery");
            double fovH=Math.toDegrees(2*Math.atan(tanH*Math.min(1,fx))),fovV=Math.toDegrees(2*Math.atan(tanV*Math.min(1,fy)));
            return new FieldOfView(fovH,fovV,"");
        } catch(Exception e){return FieldOfView.unavailable("Brak spójnych metadanych sensora, ogniskowej, cropu i transformacji");}
    }
    public static Estimate fromMeasured(Inputs input,double measured,List<Point2> corners,CameraPreviewGeometry geometry) {
        if(input==null||!positive(measured)||geometry==null||TvSetupCalibration.validation(corners,geometry,1)!=null)
            return unavailable("Przybliżenie proporcjonalne wymaga poprawnej kalibracji i zmierzonej odległości");
        double left=Double.POSITIVE_INFINITY,top=left,right=Double.NEGATIVE_INFINITY,bottom=right;
        for(Point2 p:corners){double[] q=geometry.normalizedToView(p.x,p.y);left=Math.min(left,q[0]);right=Math.max(right,q[0]);top=Math.min(top,q[1]);bottom=Math.max(bottom,q[1]);}
        double[] u=geometry.usableViewRect();
        double d=measured*Math.max((right-left)/(u[2]-u[0]),(bottom-top)/(u[3]-u[1]))/(1-2*input.margin);
        if(!positive(d))return unavailable("Nieprawidłowa proporcja obrazu");
        return new Estimate("MEASURED_RATIO",d,null,null,"Przybliżenie z pomiaru operatora, przy stałym kadrowaniu i ustawieniu TV",null);
    }
    private static boolean positive(double x){return Double.isFinite(x)&&x>0;}
    private static boolean angle(double x){return positive(x)&&x<180;}
}
