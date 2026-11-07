package com.example.alpr_v1.setup;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Objects;

/** Immutable mapping of the full, cropped/rotated source bitmap, before model ROIs. */
public final class CameraPreviewGeometry {
    public final int sourceWidth, sourceHeight, bufferWidth, bufferHeight, rotationDegrees;
    public final int previewWidth, previewHeight, bindingGeneration;
    public final int displayRotationDegrees;
    public final long viewportRevision;
    public final String cameraId, lensFacing, physicalCameraId;
    public final double zoomRatio;
    public final boolean mirrored;
    private final double[] crop, toView, toSource, usable;

    public CameraPreviewGeometry(int sourceWidth, int sourceHeight, int bufferWidth, int bufferHeight,
            double[] crop, int rotation, String cameraId, String lensFacing, double zoom,
            int generation, long revision, int viewWidth, int viewHeight,
            double[] sourceToView, double[] usableViewRect) {
        this(sourceWidth,sourceHeight,bufferWidth,bufferHeight,crop,rotation,cameraId,lensFacing,zoom,
                generation,revision,viewWidth,viewHeight,sourceToView,usableViewRect,rotation);
    }

    public CameraPreviewGeometry(int sourceWidth, int sourceHeight, int bufferWidth, int bufferHeight,
            double[] crop, int rotation, String cameraId, String lensFacing, double zoom,
            int generation, long revision, int viewWidth, int viewHeight,
            double[] sourceToView, double[] usableViewRect, int displayRotation) {
        this(sourceWidth,sourceHeight,bufferWidth,bufferHeight,crop,rotation,cameraId,lensFacing,zoom,generation,revision,
                viewWidth,viewHeight,sourceToView,usableViewRect,displayRotation,null);
    }
    private CameraPreviewGeometry(int sourceWidth,int sourceHeight,int bufferWidth,int bufferHeight,double[] crop,int rotation,
            String cameraId,String lensFacing,double zoom,int generation,long revision,int viewWidth,int viewHeight,
            double[] sourceToView,double[] usableViewRect,int displayRotation,String physicalCameraId){
        if (sourceWidth <= 0 || sourceHeight <= 0 || bufferWidth <= 0 || bufferHeight <= 0
                || viewWidth <= 0 || viewHeight <= 0 || !(zoom > 0) || !Double.isFinite(zoom)
                || rotation % 90 != 0 || rotation < 0 || rotation > 270
                || displayRotation % 90 != 0 || displayRotation < 0 || displayRotation > 270
                || crop.length != 4 || usableViewRect.length != 4)
            throw new IllegalArgumentException("Invalid camera geometry");
        for (double x : crop) if (!Double.isFinite(x)) throw new IllegalArgumentException("Invalid crop");
        for (double x : usableViewRect) if (!Double.isFinite(x)) throw new IllegalArgumentException("Invalid usable rect");
        if (crop[0] < 0 || crop[1] < 0 || crop[2] > bufferWidth || crop[3] > bufferHeight
                || crop[2] <= crop[0] || crop[3] <= crop[1]
                || usableViewRect[0] < 0 || usableViewRect[1] < 0
                || usableViewRect[2] > viewWidth || usableViewRect[3] > viewHeight
                || usableViewRect[2] <= usableViewRect[0] || usableViewRect[3] <= usableViewRect[1])
            throw new IllegalArgumentException("Invalid crop or visible pixels");
        this.sourceWidth = sourceWidth; this.sourceHeight = sourceHeight;
        int expectedWidth=(int)Math.round(rotation%180==0?crop[2]-crop[0]:crop[3]-crop[1]);
        int expectedHeight=(int)Math.round(rotation%180==0?crop[3]-crop[1]:crop[2]-crop[0]);
        if(sourceWidth!=expectedWidth||sourceHeight!=expectedHeight)throw new IllegalArgumentException("Source dimensions disagree with crop/rotation");
        this.bufferWidth = bufferWidth; this.bufferHeight = bufferHeight;
        this.crop = crop.clone(); rotationDegrees = rotation; this.cameraId = Objects.requireNonNull(cameraId);
        this.lensFacing = Objects.requireNonNull(lensFacing); zoomRatio = zoom;
        this.physicalCameraId=physicalCameraId;
        bindingGeneration = generation; viewportRevision = revision;
        previewWidth = viewWidth; previewHeight = viewHeight;
        displayRotationDegrees=displayRotation;
        toView = sourceToView.clone(); toSource = inverse(toView); usable = usableViewRect.clone();
        double left=Double.POSITIVE_INFINITY,top=left,right=Double.NEGATIVE_INFINITY,bottom=right;
        for(double[] corner:new double[][]{{0,0},{sourceWidth,0},{sourceWidth,sourceHeight},{0,sourceHeight}}){
            double[] p=map(toView,corner[0],corner[1]);left=Math.min(left,p[0]);top=Math.min(top,p[1]);right=Math.max(right,p[0]);bottom=Math.max(bottom,p[1]);
        }
        usable[0]=Math.max(usable[0],left);usable[1]=Math.max(usable[1],top);
        usable[2]=Math.min(usable[2],right);usable[3]=Math.min(usable[3],bottom);
        if(usable[2]<=usable[0]||usable[3]<=usable[1])throw new IllegalArgumentException("Source image outside preview");
        mirrored = toView[0] * toView[4] - toView[1] * toView[3] < 0;
    }

    public double[] normalizedToView(double x, double y) { return map(toView, x * sourceWidth, y * sourceHeight); }
    public CameraPreviewGeometry withPhysicalCameraId(String physical){return new CameraPreviewGeometry(sourceWidth,sourceHeight,bufferWidth,bufferHeight,crop,
            rotationDegrees,cameraId,lensFacing,zoomRatio,bindingGeneration,viewportRevision,previewWidth,previewHeight,toView,usable,displayRotationDegrees,physical);}
    public double[] viewToNormalized(double x, double y) {
        double[] p = map(toSource, x, y);
        return new double[]{p[0] / sourceWidth, p[1] / sourceHeight};
    }
    public double[] sourceToViewMatrix() { return toView.clone(); }
    public double[] usableViewRect() { return usable.clone(); }
    public boolean visible(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) return false;
        double[] p = normalizedToView(x, y);
        return p[0] >= usable[0] && p[0] < usable[2] && p[1] >= usable[1] && p[1] < usable[3];
    }
    public boolean sameGeometry(CameraPreviewGeometry other) {
        return other != null && sourceWidth == other.sourceWidth && sourceHeight == other.sourceHeight
                && bufferWidth == other.bufferWidth && bufferHeight == other.bufferHeight
                && rotationDegrees == other.rotationDegrees && cameraId.equals(other.cameraId)
                && Objects.equals(physicalCameraId,other.physicalCameraId)
                && displayRotationDegrees == other.displayRotationDegrees
                && lensFacing.equals(other.lensFacing) && bindingGeneration == other.bindingGeneration
                && previewWidth == other.previewWidth && previewHeight == other.previewHeight
                && Math.abs(zoomRatio - other.zoomRatio) < 1e-5 && Arrays.equals(crop, other.crop)
                && close(toView, other.toView) && close(usable, other.usable);
    }
    private static boolean close(double[] a, double[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) > 1e-4) return false;
        return true;
    }
    /** Same camera pixels, independent of an editor panel resizing the presentation view. */
    public boolean sameSourceGeometry(CameraPreviewGeometry other) {
        return other != null && sourceWidth == other.sourceWidth && sourceHeight == other.sourceHeight
                && bufferWidth == other.bufferWidth && bufferHeight == other.bufferHeight
                && rotationDegrees == other.rotationDegrees && displayRotationDegrees == other.displayRotationDegrees
                && cameraId.equals(other.cameraId) && lensFacing.equals(other.lensFacing) && mirrored == other.mirrored
                && Objects.equals(physicalCameraId,other.physicalCameraId)
                && bindingGeneration == other.bindingGeneration && Math.abs(zoomRatio-other.zoomRatio)<1e-5
                && Arrays.equals(crop,other.crop);
    }
    public double presentationDifferencePixels(CameraPreviewGeometry other) {
        if (!sameSourceGeometry(other)) return Double.POSITIVE_INFINITY;
        double maximum=0;
        for (double[] point:new double[][]{{0,0},{1,0},{1,1},{0,1},{.5,.5}}) {
            double[] a=normalizedToView(point[0],point[1]),b=other.normalizedToView(point[0],point[1]);
            maximum=Math.max(maximum,Math.hypot(a[0]-b[0],a[1]-b[1]));
        }
        for(int i=0;i<4;i++)maximum=Math.max(maximum,Math.abs(usable[i]-other.usable[i]));
        return maximum;
    }
    public JSONObject toJson() {
        try {
            JSONObject json=new JSONObject().put("source_image_width", sourceWidth).put("source_image_height", sourceHeight)
                    .put("buffer_width", bufferWidth).put("buffer_height", bufferHeight)
                    .put("crop_rect_px", SetupJson.array(crop)).put("rotation_degrees", rotationDegrees)
                    .put("display_rotation_degrees",displayRotationDegrees)
                    .put("camera_id", cameraId).put("lens_facing", lensFacing).put("mirrored", mirrored)
                    .put("zoom_ratio", zoomRatio).put("binding_generation", bindingGeneration)
                    .put("viewport_revision", viewportRevision).put("preview_width", previewWidth)
                    .put("preview_height", previewHeight).put("source_to_view_matrix", SetupJson.array(toView))
                    .put("usable_view_rect", SetupJson.array(usable));
            if(physicalCameraId!=null)json.put("physical_camera_id",physicalCameraId);
            return json;
        } catch (org.json.JSONException e) { throw new IllegalArgumentException(e); }
    }
    public static CameraPreviewGeometry fromJson(JSONObject json) {
        try {
            CameraPreviewGeometry geometry=new CameraPreviewGeometry(json.getInt("source_image_width"), json.getInt("source_image_height"),
                    json.getInt("buffer_width"), json.getInt("buffer_height"), values(json.getJSONArray("crop_rect_px"),4),
                    json.getInt("rotation_degrees"), json.getString("camera_id"), json.getString("lens_facing"),
                    json.getDouble("zoom_ratio"), json.getInt("binding_generation"), json.getLong("viewport_revision"),
                    json.getInt("preview_width"), json.getInt("preview_height"),
                    values(json.getJSONArray("source_to_view_matrix"),9), values(json.getJSONArray("usable_view_rect"),4),
                    json.optInt("display_rotation_degrees",json.getInt("rotation_degrees")));
            return json.isNull("physical_camera_id")?geometry:geometry.withPhysicalCameraId(json.getString("physical_camera_id"));
        } catch (Exception e) { throw new IllegalArgumentException("Invalid stored geometry",e); }
    }
    private static double[] values(JSONArray a, int size) throws org.json.JSONException {
        if (a.length() != size) throw new IllegalArgumentException("Invalid matrix size");
        double[] r = new double[size]; for (int i=0;i<size;i++) r[i]=a.getDouble(i); return r;
    }
    public static double[] map(double[] m, double x, double y) {
        double w = m[6]*x+m[7]*y+m[8];
        if (!Double.isFinite(w) || Math.abs(w)<1e-12) throw new IllegalArgumentException("Singular mapping");
        double[] result={(m[0]*x+m[1]*y+m[2])/w,(m[3]*x+m[4]*y+m[5])/w};
        if(!Double.isFinite(result[0])||!Double.isFinite(result[1]))throw new IllegalArgumentException("Invalid mapped point");
        return result;
    }
    public static double[] multiply(double[] a, double[] b) {
        double[] r=new double[9];
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)for(int k=0;k<3;k++)r[row*3+col]+=a[row*3+k]*b[k*3+col];
        return r;
    }
    public static double[] inverse(double[] m) {
        if(m.length!=9)throw new IllegalArgumentException("Expected 3x3 matrix");
        for(double x:m)if(!Double.isFinite(x))throw new IllegalArgumentException("Invalid matrix");
        double d=m[0]*(m[4]*m[8]-m[5]*m[7])-m[1]*(m[3]*m[8]-m[5]*m[6])+m[2]*(m[3]*m[7]-m[4]*m[6]);
        if(!Double.isFinite(d)||Math.abs(d)<1e-12)throw new IllegalArgumentException("Singular matrix");
        double[] r={m[4]*m[8]-m[5]*m[7],m[2]*m[7]-m[1]*m[8],m[1]*m[5]-m[2]*m[4],
                m[5]*m[6]-m[3]*m[8],m[0]*m[8]-m[2]*m[6],m[2]*m[3]-m[0]*m[5],
                m[3]*m[7]-m[4]*m[6],m[1]*m[6]-m[0]*m[7],m[0]*m[4]-m[1]*m[3]};
        for(int i=0;i<9;i++)r[i]/=d; return r;
    }
    /** Same crop-then-clockwise-rotate operation as CameraImageConverter; no model input changes. */
    public static double[] bufferToSource(double[] c, int rotation) {
        switch(rotation) {
            case 0:return new double[]{1,0,-c[0],0,1,-c[1],0,0,1};
            case 90:return new double[]{0,-1,c[3],1,0,-c[0],0,0,1};
            case 180:return new double[]{-1,0,c[2],0,-1,c[3],0,0,1};
            case 270:return new double[]{0,1,-c[1],-1,0,c[2],0,0,1};
            default:throw new IllegalArgumentException("Invalid rotation");
        }
    }
}
