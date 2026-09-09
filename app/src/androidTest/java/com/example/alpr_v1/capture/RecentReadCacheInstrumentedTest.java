package com.example.alpr_v1.capture;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static com.example.alpr_v1.capture.DynamicRecognitionHistoryInstrumentedTest.*;

@RunWith(AndroidJUnit4.class)
public class RecentReadCacheInstrumentedTest {
    @Test public void ownsCopiesRejectsEmptyAndCarriedReadsAndReleasesEvictions() {
        Bitmap source = Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888);
        RecentReadCache cache = new RecentReadCache(2);
        try {
            cache.remember(observation(source,1,1,1,1,"AAA",true,""),telemetry(),"normal");
            cache.remember(observation(source,1,1,1,2,"AAA",false,"AAA"),telemetry(),"normal");
            assertTrue(cache.entries().isEmpty());
            cache.remember(observation(source,1,1,1,3,"AAA"),telemetry(),"normal");
            Bitmap owned = cache.entries().get(0).observation.previewBitmap;
            assertNotSame(source,owned);
            cache.remember(observation(source,1,1,1,3,"AAA"),telemetry(),"normal");
            assertEquals(1,cache.entries().size());
            cache.remember(observation(source,1,2,2,4,"AAA"),telemetry(),"zoom");
            cache.remember(observation(source,1,3,3,5,"AAB"),telemetry(),"normal");
            assertEquals(2,cache.entries().size());
            assertTrue(owned.isRecycled());
            Bitmap remaining = cache.entries().get(0).observation.previewBitmap;
            cache.clear();
            assertTrue(remaining.isRecycled());
            assertFalse(source.isRecycled());
        } finally { cache.clear(); source.recycle(); }
    }
}
