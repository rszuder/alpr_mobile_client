package com.example.alpr_v1.continuity;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TvObservationMaskTest {
    static final int W=240,H=160;
    static final double[] Q={30,22,210,35,184,141,48,128};
    static ObservationMask mask(){return ObservationMask.polygon(W,H,"tv/reference",Q);}
    // Independent ray-intersection oracle, not the production convex half-plane predicate.
    static boolean inside(double x,double y){
        boolean result=false;
        for(int i=0,j=3;i<4;j=i++) if((Q[2*i+1]>y)!=(Q[2*j+1]>y)
                &&x<(Q[2*j]-Q[2*i])*(y-Q[2*i+1])/(Q[2*j+1]-Q[2*i+1])+Q[2*i]) result=!result;
        return result;
    }
    static byte[] pattern(){byte[] a=new byte[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++)a[y*W+x]=(byte)(40+((x/5+y/5)%2)*150);return a;}
    static byte[] outside(byte[] original,int seed,boolean bboxOnly){
        byte[] a=original.clone();Random r=new Random(seed);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            boolean pixelInside=inside(x+.00001,y+.00001)&&inside(x+.99999,y+.00001)
                    &&inside(x+.99999,y+.99999)&&inside(x+.00001,y+.99999);
            if(!pixelInside&&(!bboxOnly||x>=30&&x<210&&y>=22&&y<141))a[y*W+x]=(byte)r.nextInt(256);
        }return a;
    }
    @Test public void wholePixelsAndPatchBoundsAgreeWithIndependentOracle(){
        ObservationMask m=mask();int supported=0,excluded=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            if(m.contains(x,y)){supported++;assertTrue(inside(x+.00001,y+.00001));assertTrue(inside(x+.99999,y+.99999));}
            else excluded++;
        }assertTrue(supported>10000);assertTrue(excluded>10000);assertFalse(m.contains(31,140));assertTrue(m.contains(110,80));
        assertFalse(m.supports(48,128,3));assertTrue(m.supports(110,80,6));
        assertFalse(m.supports(Float.NaN,80,1));
    }
    @Test public void arbitraryExteriorHasBitIdenticalRestrictedBuffers(){
        ObservationMask m=mask();byte[] a=pattern(),expected=m.restrictedCopy(a);
        for(int seed=0;seed<25;seed++)assertArrayEquals(expected,m.restrictedCopy(outside(a,seed,false)));
        assertArrayEquals(pattern(),a);
    }
    @Test public void bboxCornersAreExcludedEvenThoughTheyBelongToCrop(){
        ObservationMask m=mask();byte[] a=pattern();assertFalse(Arrays.equals(a,outside(a,2,true)));
        assertArrayEquals(m.restrictedCopy(a),m.restrictedCopy(outside(a,2,true)));
    }
    @Test public void scalarSamplesIgnoreExteriorBeforeExposureMean(){
        ObservationMask m=mask();byte[] a=pattern();
        for(int seed=0;seed<20;seed++)assertArrayEquals(m.samples(a,null),m.samples(outside(a,seed,false),null),0f);
        assertTrue(m.samples(a,null).length<400);
    }
    @Test public void pyramidSupportRequiresEveryAveragedChild(){
        ObservationMask a=mask(),b=a.downsample2(),c=b.downsample2();
        for(int y=0;y<b.height;y++)for(int x=0;x<b.width;x++)assertEquals(a.rectangle(x*2,y*2,x*2+1,y*2+1),b.contains(x,y));
        for(int y=0;y<c.height;y++)for(int x=0;x<c.width;x++)if(c.contains(x,y))assertTrue(a.rectangle(x*4,y*4,x*4+3,y*4+3));
    }
    @Test public void rasterRectangleIntegralHasNoHolesOrOffByOne(){
        boolean[] a=new boolean[120];Arrays.fill(a,true);a[5*12+6]=false;
        ObservationMask m=ObservationMask.raster(12,10,"hole",a);
        assertFalse(m.rectangle(4,4,7,7));assertTrue(m.rectangle(0,0,5,9));assertFalse(m.contains(6,5));
        assertFalse(m.rectangle(-1,0,2,2));assertFalse(m.rectangle(0,0,12,9));
    }
    @Test public void invalidOrEmptyGeometryCannotSilentlyBecomeFullFrame(){
        assertThrows(IllegalArgumentException.class,()->ObservationMask.polygon(240,160,"bad",new double[]{0,0,200,140,200,0,0,140}));
        assertThrows(IllegalArgumentException.class,()->ObservationMask.polygon(240,160,"bad",new double[]{0,0,10,0,20,0,0,10}));
        assertThrows(IllegalArgumentException.class,()->mask().requireSize(160,240));
        ObservationMask empty=ObservationMask.raster(240,160,"empty",new boolean[240*160]);
        assertThrows(IllegalArgumentException.class,()->empty.samples(pattern(),null));
    }
    @Test public void calibrationIdentityAndDimensionsBelongToReference(){
        assertTrue(ObservationMask.same(mask(),mask()));
        assertFalse(ObservationMask.same(mask(),ObservationMask.polygon(W,H,"new",Q)));
        assertFalse(ObservationMask.same(mask(),null));assertTrue(ObservationMask.same(null,null));
    }
}
