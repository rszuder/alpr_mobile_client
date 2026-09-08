package com.example.alpr_v1.camera;
import org.junit.Test;
import static org.junit.Assert.*;
public class LiveFrameRateMeterTest {
    @Test public void countsCameraFramesEvenWhenNoInferenceCompletes() {
        LiveFrameRateMeter meter=new LiveFrameRateMeter();
        assertTrue(Double.isNaN(meter.rate(0)));
        for(int i=0;i<60;i++)meter.onFrame(i*1_000_000_000L/30);
        assertEquals(30,meter.rate(2_000_000_000L),.001);
        assertEquals(15,meter.rate(3_000_000_000L),.001);
        assertEquals(0,meter.rate(4_000_000_000L),.001);
    }
    @Test public void rollingWindowDoesNotAccumulateAnOldSession() {
        LiveFrameRateMeter meter=new LiveFrameRateMeter();
        for(int second=0;second<10;second++)for(int i=0;i<24;i++)meter.onFrame(second*1_000_000_000L+i*40_000_000L);
        assertEquals(24,meter.rate(10_000_000_000L),.001);
        meter.reset();assertTrue(Double.isNaN(meter.rate(11_000_000_000L)));
        meter.onFrame(11_000_000_000L);assertTrue(Double.isNaN(meter.rate(11_100_000_000L)));
        meter.onFrame(1);assertTrue(Double.isNaN(meter.rate(2)));
    }
}
