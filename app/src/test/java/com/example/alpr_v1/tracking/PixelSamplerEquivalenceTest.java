package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.ObservationMask;
import java.lang.reflect.*;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/** Numerical regression for the hot path, including the original border behavior. */
public final class PixelSamplerEquivalenceTest {
    @Test public void optimizedSamplingIsBitIdenticalToThePreviousBilinearFormula()throws Exception {
        int width=37,height=51;byte[] pixels=new byte[width*height];new Random(754L).nextBytes(pixels);
        Class<?> imageClass=Class.forName("com.example.alpr_v1.tracking.SparsePyramidalFlow$Image");
        Constructor<?> constructor=imageClass.getDeclaredConstructor(byte[].class,int.class,int.class,ObservationMask.class);constructor.setAccessible(true);
        Object image=constructor.newInstance(pixels,width,height,null);
        Method sample=SparsePyramidalFlow.class.getDeclaredMethod("sample",imageClass,float.class,float.class);sample.setAccessible(true);
        Random coordinates=new Random(1514L);
        for(int i=0;i<4000;i++) {
            float x=(width-1)*coordinates.nextFloat(),y=(height-1)*coordinates.nextFloat();
            if(i<8){x=new float[]{-.25f,-0f,0f,.5f,width-1,width-1.25f,1f,1.75f}[i];y=i%2==0?0f:height-1;}
            float actual=(Float)sample.invoke(null,image,x,y);
            assertEquals("exact float at "+x+","+y,Float.floatToIntBits(reference(pixels,width,height,x,y)),Float.floatToIntBits(actual));
        }
    }
    static float reference(byte[] pixels,int width,int height,float x,float y) {
        int left=(int)Math.floor(x),top=(int)Math.floor(y);float dx=x-left,dy=y-top;
        int right=Math.min(width-1,left+1),bottom=Math.min(height-1,top+1);left=Math.max(0,left);top=Math.max(0,top);
        float a=(pixels[top*width+left]&255)*(1f-dx)+(pixels[top*width+right]&255)*dx;
        float b=(pixels[bottom*width+left]&255)*(1f-dx)+(pixels[bottom*width+right]&255)*dx;
        return a*(1f-dy)+b*dy;
    }
}
