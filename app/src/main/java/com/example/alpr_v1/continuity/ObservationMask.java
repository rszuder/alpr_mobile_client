package com.example.alpr_v1.continuity;

import android.graphics.Bitmap;
import java.util.Arrays;

/** Observation-only support, in pixel-edge coordinates. Never an ALPR input mask.
 * A valid sample owns its entire pixel footprint; a valid patch owns every pixel.
 */
public final class ObservationMask {
    public final int width, height;
    public final String key;
    private final double[] quad;
    private final int[] invalid;
    private ObservationMask half;

    private ObservationMask(int width, int height, String key, double[] quad, boolean[] valid) {
        if (width <= 0 || height <= 0 || key == null) throw new IllegalArgumentException("TV_OBSERVATION_INVALID_MASK");
        this.width = width; this.height = height; this.key = key;
        this.quad = quad == null ? null : quad.clone();
        if (valid == null) invalid = null;
        else {
            if (valid.length != width * height) throw new IllegalArgumentException("TV_OBSERVATION_MASK_SIZE");
            invalid = new int[(width + 1) * (height + 1)];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int i = (y + 1) * (width + 1) + x + 1;
                invalid[i] = (valid[y * width + x] ? 0 : 1) + invalid[i - 1]
                        + invalid[i - width - 1] - invalid[i - width - 2];
            }
        }
    }

    public static ObservationMask polygon(int width, int height, String key, double[] corners) {
        if (corners == null || corners.length != 8) throw new IllegalArgumentException("TV_OBSERVATION_INVALID_QUAD");
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4, k = (i + 2) % 4;
            double x = corners[2*i], y = corners[2*i+1];
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > width || y < 0 || y > height
                    || (corners[2*j]-x)*(corners[2*k+1]-corners[2*j+1])
                    -(corners[2*j+1]-y)*(corners[2*k]-corners[2*j]) <= 1e-9)
                throw new IllegalArgumentException("TV_OBSERVATION_INVALID_QUAD");
        }
        return new ObservationMask(width, height, key, corners, null);
    }

    public static ObservationMask raster(int width, int height, String key, boolean[] valid) {
        return new ObservationMask(width, height, key, null, valid);
    }

    private boolean inside(double x, double y) {
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            if ((quad[2*j]-quad[2*i])*(y-quad[2*i+1])
                    -(quad[2*j+1]-quad[2*i+1])*(x-quad[2*i]) < -1e-8) return false;
        }
        return true;
    }

    /** Inclusive integer pixel bounds. Convexity makes the four extreme corners sufficient. */
    public boolean rectangle(int left, int top, int right, int bottom) {
        if (left < 0 || top < 0 || right >= width || bottom >= height || right < left || bottom < top) return false;
        if (quad != null) return inside(left, top) && inside(right+1., top)
                && inside(right+1., bottom+1.) && inside(left, bottom+1.);
        int stride = width + 1;
        return invalid[(bottom+1)*stride+right+1]-invalid[top*stride+right+1]
                -invalid[(bottom+1)*stride+left]+invalid[top*stride+left] == 0;
    }
    public boolean contains(int x, int y) { return rectangle(x, y, x, y); }
    /** Includes bilinear neighbours as well as the requested gradient/search radius. */
    public boolean supports(float x, float y, int radius) {
        return Float.isFinite(x) && Float.isFinite(y) && rectangle((int)Math.floor(x)-radius,
                (int)Math.floor(y)-radius, (int)Math.floor(x)+radius+1, (int)Math.floor(y)+radius+1);
    }
    public void requireSize(int w, int h) {
        if (w != width || h != height) throw new IllegalArgumentException("TV_OBSERVATION_MASK_SIZE");
    }
    public boolean same(ObservationMask other) { return other != null && width == other.width && height == other.height && key.equals(other.key); }
    public static boolean same(ObservationMask a, ObservationMask b) { return a == null ? b == null : a.same(b); }

    public byte[] restrictedCopy(byte[] gray) {
        if (gray == null || gray.length < width * height) throw new IllegalArgumentException("TV_OBSERVATION_NO_LUMA");
        byte[] copy = new byte[width * height];
        for (int y=0;y<height;y++) for (int x=0;x<width;x++) if (contains(x,y)) copy[y*width+x]=gray[y*width+x];
        return copy;
    }
    public synchronized ObservationMask downsample2() {
        if(half!=null) return half;
        int w=width/2,h=height/2; boolean[] v=new boolean[w*h];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) v[y*w+x]=rectangle(x*2,y*2,x*2+1,y*2+1);
        return half=raster(w,h,key+"/2",v);
    }
    /** The old 20x20 scalar grid, compacted to supported samples BEFORE exposure compensation. */
    public float[] samples(byte[] gray, Bitmap bitmap) {
        float[] values=new float[400]; int count=0;
        for(int gy=0;gy<20;gy++) for(int gx=0;gx<20;gx++) {
            int x=Math.min(width-1,Math.round((gx+.5f)*width/20));
            int y=Math.min(height-1,Math.round((gy+.5f)*height/20));
            if(!contains(x,y)) continue;
            if(bitmap==null) values[count++]=gray[y*width+x]&255;
            else { int p=bitmap.getPixel(x,y); values[count++]=.2126f*((p>>16)&255)+.7152f*((p>>8)&255)+.0722f*(p&255); }
        }
        if(count==0) throw new IllegalArgumentException("TV_OBSERVATION_NO_SUPPORTED_SAMPLES");
        return Arrays.copyOf(values,count);
    }
}
