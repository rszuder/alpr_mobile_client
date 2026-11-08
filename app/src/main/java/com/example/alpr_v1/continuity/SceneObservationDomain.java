package com.example.alpr_v1.continuity;

import android.graphics.Bitmap;
import com.example.alpr_v1.camera.LumaFrame;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import com.example.alpr_v1.setup.TvBboxCrop;
import org.json.JSONArray;
import java.util.LinkedHashMap;
import java.util.Map;

/** Frozen TV observation contract. All outputs are private, upright observation buffers.
 * Camera evidence and model bitmaps never pass through this class.
 */
public final class SceneObservationDomain {
    public final TvBboxCrop crop;
    private final ObservationMask source;
    private final Map<String,ObservationMask> masks=new LinkedHashMap<String,ObservationMask>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String,ObservationMask> e){return size()>4;}
    };
    public static final class Frame {
        public final byte[] gray;
        public final int width,height;
        public final ObservationMask mask;
        private Frame(byte[] g,int w,int h,ObservationMask m){gray=g;width=w;height=h;mask=m;}
    }
    public SceneObservationDomain(TvBboxCrop crop) {
        if(crop==null) throw new IllegalArgumentException("TV_OBSERVATION_CALIBRATION_REQUIRED");
        this.crop=crop;
        try {
            JSONArray a=crop.toJson().getJSONArray("corners_norm"); double[] q=new double[8];
            for(int i=0;i<4;i++){q[2*i]=a.getJSONObject(i).getDouble("x")*crop.sourceWidth;q[2*i+1]=a.getJSONObject(i).getDouble("y")*crop.sourceHeight;}
            source=ObservationMask.polygon(crop.sourceWidth,crop.sourceHeight,crop.digest+"/source",q);
        }catch(Exception e){throw new IllegalArgumentException("TV_OBSERVATION_CALIBRATION_REQUIRED",e);}
    }
    public ObservationMask sourceMask(int width,int height){source.requireSize(width,height);return source;}
    public void requireGeometry(CameraPreviewGeometry geometry){
        if(!crop.geometry.sameGeometry(geometry) || crop.geometry.viewportRevision!=geometry.viewportRevision)
            throw new IllegalArgumentException("TV_OBSERVATION_STALE_GEOMETRY");
    }
    public Frame luma(LumaFrame f) {
        CameraPreviewGeometry g=crop.geometry;
        try {
            JSONArray c=g.toJson().getJSONArray("crop_rect_px");
            if(f==null||!f.completePlane||f.bufferWidth!=g.bufferWidth||f.bufferHeight!=g.bufferHeight||f.rotationDegrees!=g.rotationDegrees
                    ||f.bindingGeneration!=g.bindingGeneration||!crop.cameraSetup.same(f.cameraSetup)
                    ||f.cropLeft!=c.getInt(0)||f.cropTop!=c.getInt(1)||f.cropRight!=c.getInt(2)||f.cropBottom!=c.getInt(3))
                throw new IllegalArgumentException("TV_OBSERVATION_LUMA_GEOMETRY_CHANGED");
        }catch(org.json.JSONException e){throw new IllegalArgumentException("TV_OBSERVATION_LUMA_GEOMETRY_CHANGED",e);}
        int r=f.rotationDegrees,w=r%180==0?f.width:f.height,h=r%180==0?f.height:f.width;
        String key="luma/"+f.width+"/"+f.height;
        ObservationMask mask;
        synchronized(masks){mask=masks.get(key);}
        byte[] gray=new byte[w*h]; boolean[] valid=mask==null?new boolean[w*h]:null;
        double[] transform=CameraPreviewGeometry.bufferToSource(new double[]{f.cropLeft,f.cropTop,f.cropRight,f.cropBottom},r);
        for(int y=0;y<f.height;y++) for(int x=0;x<f.width;x++) {
            int ox=r==90?f.height-1-y:r==180?f.width-1-x:r==270?y:x;
            int oy=r==90?x:r==180?f.height-1-y:r==270?f.width-1-x:y, i=oy*w+ox;
            if(valid!=null){
                int sx=f.cropLeft+Math.min(f.cropRight-f.cropLeft-1,Math.round((x+.5f)*(f.cropRight-f.cropLeft)/f.width-.5f));
                int sy=f.cropTop+Math.min(f.cropBottom-f.cropTop-1,Math.round((y+.5f)*(f.cropBottom-f.cropTop)/f.height-.5f));
                double[] a=CameraPreviewGeometry.map(transform,sx,sy),b=CameraPreviewGeometry.map(transform,sx+1,sy+1);
                valid[i]=source.rectangle((int)Math.round(Math.min(a[0],b[0])),(int)Math.round(Math.min(a[1],b[1])),
                        (int)Math.round(Math.max(a[0],b[0]))-1,(int)Math.round(Math.max(a[1],b[1]))-1);
            }
            if(mask==null?valid[i]:mask.contains(ox,oy)) gray[i]=f.gray[y*f.width+x];
        }
        if(mask==null){mask=ObservationMask.raster(w,h,crop.digest+"/"+key,valid);synchronized(masks){masks.put(key,mask);}}
        mask.samples(gray,null); // Fail closed if the scalar channel has no support.
        return new Frame(gray,w,h,mask);
    }
    public Frame preview(Bitmap bitmap,CameraPreviewGeometry current) {
        requireGeometry(current);
        if(bitmap==null||bitmap.isRecycled()) throw new IllegalArgumentException("TV_OBSERVATION_NO_PREVIEW");
        int w=Math.min(240,crop.sourceWidth),h=Math.max(1,(int)Math.round(w*(double)crop.sourceHeight/crop.sourceWidth));
        String key="preview/"+bitmap.getWidth()+"/"+bitmap.getHeight();
        ObservationMask mask; synchronized(masks){mask=masks.get(key);}
        boolean[] valid=mask==null?new boolean[w*h]:null;byte[] gray=new byte[w*h];
        double bx=bitmap.getWidth()/(double)current.previewWidth,by=bitmap.getHeight()/(double)current.previewHeight;
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            double[] view=current.normalizedToView((x+.5)/w,(y+.5)/h);
            int px=(int)Math.floor(view[0]*bx),py=(int)Math.floor(view[1]*by),i=y*w+x;
            if(valid!=null){
                boolean ok=px>=1&&py>=1&&px<bitmap.getWidth()-1&&py<bitmap.getHeight()-1;
                // Preview filtering footprint, including neighbouring display pixels and source interpolation.
                for(int dy=-1;dy<=2&&ok;dy+=3) for(int dx=-1;dx<=2&&ok;dx+=3){
                    double[] s=current.viewToNormalized((px+dx)/bx,(py+dy)/by);
                    int sx=(int)Math.floor(s[0]*crop.sourceWidth),sy=(int)Math.floor(s[1]*crop.sourceHeight);
                    ok=source.rectangle(sx-1,sy-1,sx+1,sy+1)&&current.visible(s[0],s[1]);
                }
                valid[i]=ok;
            }
            if(mask==null?valid[i]:mask.contains(x,y)){
                int p=bitmap.getPixel(px,py);gray[i]=(byte)Math.round(.2126f*((p>>16)&255)+.7152f*((p>>8)&255)+.0722f*(p&255));
            }
        }
        if(mask==null){mask=ObservationMask.raster(w,h,crop.digest+"/"+key,valid);synchronized(masks){masks.put(key,mask);}}
        mask.samples(gray,null);
        return new Frame(gray,w,h,mask);
    }
}
