package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.NormalizedBounds;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Keeps the original foreground exclusion and candidate error, including no supported pixels. */
public final class CoarseMotionSupportEquivalenceTest {
    @Test public void cachedSupportPreservesEveryComparedTranslationError()throws Exception {
        int width=48,height=64;byte[] a=new byte[width*height],b=new byte[a.length];
        new Random(516L).nextBytes(a);new Random(615L).nextBytes(b);
        Method cache=GlobalLumaMotionTracker.class.getDeclaredMethod("translationSourceSupport",int.class,int.class,List.class);cache.setAccessible(true);
        Method error=GlobalLumaMotionTracker.class.getDeclaredMethod("translationError",byte[].class,byte[].class,int.class,int.class,
                boolean[].class,int.class,int.class,int.class,int.class,int.class,int.class,int.class);error.setAccessible(true);
        List<List<NormalizedBounds>> variants=Arrays.asList(Collections.emptyList(),
                Arrays.asList(new NormalizedBounds(.15f,.2f,.55f,.65f),new NormalizedBounds(.4f,.1f,.85f,.4f),null),
                Collections.singletonList(new NormalizedBounds(0,0,1,1)));
        for(List<NormalizedBounds> masks:variants) {
            boolean[] support=(boolean[])cache.invoke(null,width,height,masks);
            GlobalLumaMotionTracker tracker=new GlobalLumaMotionTracker();
            for(int step:new int[]{1,4,8})for(int dy=-4;dy<=4;dy+=2)for(int dx=-6;dx<=6;dx+=2) {
                float value=(Float)error.invoke(tracker,a,b,width,height,support,7,41,6,58,dx,dy,step);
                assertEquals("same float error for every candidate",Float.floatToIntBits(reference(a,b,width,height,masks,dx,dy,step)),Float.floatToIntBits(value));
            }
        }
    }
    static float reference(byte[] a,byte[] b,int width,int height,List<NormalizedBounds> masks,int dx,int dy,int step) {
        long difference=0;int samples=0;
        for(int y=6;y<58;y+=step)for(int x=7;x<41;x+=step) {
            boolean excluded=false;
            for(NormalizedBounds mask:masks) {
                if(mask==null||!mask.valid())continue;
                if(x/(float)width>=mask.left-.025f&&x/(float)width<=mask.right+.025f
                        &&y/(float)height>=mask.top-.025f&&y/(float)height<=mask.bottom+.025f){excluded=true;break;}
            }
            if(excluded)continue;
            difference+=Math.abs((a[y*width+x]&255)-(b[(y+dy)*width+x+dx]&255));samples++;
        }
        return samples<=0?Float.POSITIVE_INFINITY:difference/(float)samples;
    }
}
