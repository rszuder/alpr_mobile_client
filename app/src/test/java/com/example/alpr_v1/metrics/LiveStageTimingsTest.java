package com.example.alpr_v1.metrics;

import com.example.alpr_v1.continuity.ContinuityStamp;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiveStageTimingsTest {
    @Test public void skippedMzBetweenHudPollsKeepsItsLastActualTime() {
        LiveStageTimings timings = new LiveStageTimings();
        ContinuityStamp stamp = new ContinuityStamp(1, 2, 0, 100);
        timings.observe(stamp, Collections.singletonMap("character_inference", 420_000_000L));
        timings.observe(stamp.withSourceTimestamp(200), Collections.singletonMap("vehicle_inference", 85_000_000L));
        timings.observe(stamp.withSourceTimestamp(300), Collections.emptyMap());
        assertEquals(420.0, timings.milliseconds("character_inference"), .001);
        assertEquals(85.0, timings.milliseconds("vehicle_inference"), .001);
        assertTrue(Double.isNaN(timings.milliseconds("plate_inference")));
        timings.observe(stamp.withSourceTimestamp(400), Collections.singletonMap("character_inference", 380_000_000L));
        assertEquals(380.0, timings.milliseconds("character_inference"), .001);
    }

    @Test public void sceneEpochTransformAndExplicitResetClearOldModelTimes() {
        for (ContinuityStamp next : new ContinuityStamp[]{
                new ContinuityStamp(2, 2, 0, 200), new ContinuityStamp(1, 3, 0, 200),
                new ContinuityStamp(1, 2, 1, 200)}) {
            LiveStageTimings timings = new LiveStageTimings();
            timings.observe(new ContinuityStamp(1, 2, 0, 100),
                    Collections.singletonMap("character_inference", 420_000_000L));
            timings.observe(next, Collections.singletonMap("vehicle_inference", 85_000_000L));
            assertTrue(Double.isNaN(timings.milliseconds("character_inference")));
            assertEquals(85.0, timings.milliseconds("vehicle_inference"), .001);
            timings.reset();
            assertTrue(Double.isNaN(timings.milliseconds("vehicle_inference")));
        }
    }
}
