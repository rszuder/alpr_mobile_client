package com.example.alpr_v1.camera;

import android.util.Size;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=32,application=android.app.Application.class)
public class OrdinaryDynamicResourcePolicyTest {
    static final long G=1024L*OrdinaryDynamicResourcePolicy.MIB;
    @Test public void ramAndHeapBoundariesHaveConservativeDefaults() {
        assertEquals(921600,new OrdinaryDynamicResourcePolicy(0,false,G).analysisPixels);
        assertEquals(921600,new OrdinaryDynamicResourcePolicy(3*G,false,G).analysisPixels);
        assertEquals(2073600,new OrdinaryDynamicResourcePolicy(4*G,false,G).analysisPixels);
        assertEquals(2073600,new OrdinaryDynamicResourcePolicy(6*G,false,G).analysisPixels);
        assertEquals(3686400,new OrdinaryDynamicResourcePolicy(8*G,false,G).analysisPixels);
        assertEquals(921600,new OrdinaryDynamicResourcePolicy(8*G,true,G).analysisPixels);
        assertEquals(921600,new OrdinaryDynamicResourcePolicy(8*G,false,128*1024*1024L).analysisPixels);
    }
    @Test public void twelveMegapixelsCannotSlipThroughAResolutionFallbackOrRotation() {
        OrdinaryDynamicResourcePolicy policy=new OrdinaryDynamicResourcePolicy(4*G,false,G);
        java.util.List<Size> supported=Arrays.asList(new Size(4000,3000),new Size(1920,1080),new Size(1440,1080),new Size(640,480));
        assertEquals(new Size(1440,1080),policy.analysisSize(supported,new Size(3000,4000)));
        for(Size size:OrdinaryDynamicResourcePolicy.bounded(supported,new Size(4000,3000),policy.analysisPixels))
            assertTrue(OrdinaryDynamicResourcePolicy.pixels(size)<=policy.analysisPixels);
        assertThrows(IllegalStateException.class,()->policy.analysisSize(Arrays.asList(new Size(4000,3000)),new Size(4000,3000)));
    }
    @Test public void bitmapLeasesNeverExceedTheBudgetAndEveryExitCanCloseTwice() {
        BitmapMemoryBudget budget=new BitmapMemoryBudget(100);
        BitmapMemoryBudget.Lease source=budget.acquire(70),preview=budget.acquire(30);
        assertNotNull(source);assertNotNull(preview);assertNull(budget.acquire(1));
        assertNull(budget.acquire(-1));assertEquals(100,budget.peakBytes());
        source.close();source.close();assertEquals(30,budget.usedBytes());
        preview.close();assertEquals(0,budget.usedBytes());
        for(int i=0;i<1000;i++)try(BitmapMemoryBudget.Lease next=budget.acquire(80)){assertNotNull(next);}
        assertEquals(0,budget.usedBytes());assertEquals(100,budget.peakBytes());
    }
}
