package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.continuity.SceneContinuityProfile;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.continuity.SceneTransitionCoordinator;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class FinalPipelineResultDispatchInstrumentedTest {
    @Test
    public void delayedStaleResultRecyclesBitmapOnceWithoutUiOrCrop()
            throws Exception {
        SceneTransitionCoordinator coordinator = new SceneTransitionCoordinator(
                SceneHandlingMode.DYNAMIC_CONTINUITY,
                SceneContinuityProfile.INITIAL
        );
        ContinuityStamp resultStamp = coordinator.stamp(8_000_000_000L);
        Bitmap crop = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888);
        PlateObservation observation = observation(crop, resultStamp);
        PipelineResult rawResult = new PipelineResult(
                "recognized",
                "test",
                Collections.emptyList(),
                Collections.emptyList(),
                1280,
                720,
                Collections.singletonList(observation),
                false,
                resultStamp
        );
        PipelineResult result = rawResult.withContinuityStamp(resultStamp);
        CountDownLatch queued = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean uiPresented = new AtomicBoolean(false);
        AtomicBoolean cropCollected = new AtomicBoolean(false);

        Thread dispatch = new Thread(() -> {
            queued.countDown();
            try {
                if (!release.await(5L, TimeUnit.SECONDS)) return;
                if (PipelineResultDispatchGate.isCurrent(
                        coordinator.stamp(resultStamp.sourceTimestampNanos),
                        result
                )) {
                    uiPresented.set(true);
                    cropCollected.set(true);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                result.close();
            }
        }, "device-final-result-dispatch-test");
        dispatch.start();

        assertTrue(queued.await(2L, TimeUnit.SECONDS));
        coordinator.requestSoftReacquire("device_dispatch_race", 1_000_000_000L);
        release.countDown();
        dispatch.join(5_000L);

        assertFalse(dispatch.isAlive());
        assertFalse(uiPresented.get());
        assertFalse(cropCollected.get());
        assertTrue(crop.isRecycled());
        assertTrue(result.isClosed());
        assertTrue(rawResult.isClosed());
        assertEquals(1, result.effectiveCloseCountForTest());
        assertEquals(1, rawResult.effectiveCloseCountForTest());

        result.close();
        assertEquals(1, result.effectiveCloseCountForTest());
    }

    private static PlateObservation observation(
            Bitmap crop,
            ContinuityStamp stamp
    ) {
        return new PlateObservation(
                41L,
                PlateVehicleAssociation.direct(21L, 31L, "dispatch_test"),
                MtWorkKind.VEHICLE_ROI,
                MtReason.SCAN_NEXT_CANDIDATE,
                5L,
                crop,
                "WE911GT",
                0.9,
                0.8,
                true,
                4,
                Collections.emptyList(),
                0L,
                stamp.sourceTimestampNanos,
                0.7f,
                null,
                null,
                PlateGeometry.unavailable(),
                true,
                true,
                "WE911GT",
                true,
                1,
                TemporalCharacterAggregator.LAYOUT_SINGLE_ROW,
                Collections.singletonList(7),
                "WE911G",
                "WE911GT",
                stamp
        );
    }
}
