package com.example.alpr_v1.camera;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhoneOrientationEstimatorTest {
    private static final long NOW = 1_000_000_000L;

    @Test public void uprightPortraitHasZeroDeviation() {
        PhoneOrientationEstimator estimator = new PhoneOrientationEstimator();
        estimator.update(0f, 9.81f, 0f, NOW);
        PhoneOrientationEstimator.Snapshot result = estimator.snapshot(NOW, 0);
        assertTrue(result.available);
        assertFalse(result.warning);
        assertEquals(0f, result.deviationDegrees, 0.01f);
        assertEquals(0f, result.sidewaysDegrees, 0.01f);
        assertEquals(0f, result.forwardDegrees, 0.01f);
    }

    @Test public void distinguishesSidewaysAndForwardTilt() {
        PhoneOrientationEstimator sideways = new PhoneOrientationEstimator();
        sideways.update(-4.905f, 8.495709f, 0f, NOW);
        assertEquals(30f, sideways.snapshot(NOW, 0).sidewaysDegrees, 0.01f);
        assertEquals(0f, sideways.snapshot(NOW, 0).forwardDegrees, 0.01f);
        PhoneOrientationEstimator forward = new PhoneOrientationEstimator();
        forward.update(0f, 8.495709f, 4.905f, NOW);
        assertEquals(0f, forward.snapshot(NOW, 0).sidewaysDegrees, 0.01f);
        assertEquals(30f, forward.snapshot(NOW, 0).forwardDegrees, 0.01f);
    }

    @Test public void landscapeAndUpsideDownRequireCorrection() {
        PhoneOrientationEstimator estimator = new PhoneOrientationEstimator();
        estimator.update(9.81f, 0f, 0f, NOW);
        assertEquals(90f, estimator.snapshot(NOW, 0).deviationDegrees, 0.01f);
        assertTrue(estimator.snapshot(NOW, 0).warning);
        estimator.reset();
        estimator.update(0f, -9.81f, 0f, NOW);
        assertEquals(180f, estimator.snapshot(NOW, 0).deviationDegrees, 0.01f);
        assertTrue(estimator.snapshot(NOW, 0).warning);
    }

    @Test public void flatPhoneDoesNotInventSidewaysAngle() {
        PhoneOrientationEstimator estimator = new PhoneOrientationEstimator();
        estimator.update(0f, 0f, 9.81f, NOW);
        PhoneOrientationEstimator.Snapshot result = estimator.snapshot(NOW, 0);
        assertTrue(result.available);
        assertTrue(result.warning);
        assertTrue(Float.isNaN(result.sidewaysDegrees));
        assertEquals(90f, result.forwardDegrees, 0.01f);
    }

    @Test public void displayRemappingSupportsDevicesWithDifferentNaturalOrientation() {
        float[][] upright = {{0f, 9.81f}, {-9.81f, 0f}, {0f, -9.81f}, {9.81f, 0f}};
        for (int rotation = 0; rotation < 4; rotation++) {
            PhoneOrientationEstimator estimator = new PhoneOrientationEstimator();
            estimator.update(upright[rotation][0], upright[rotation][1], 0f, NOW);
            assertEquals(0f, estimator.snapshot(NOW, rotation).deviationDegrees, 0.01f);
        }
    }

    @Test public void warningHasHysteresis() {
        PhoneOrientationEstimator estimator = new PhoneOrientationEstimator();
        sampleSideways(estimator, 32f, NOW);
        assertTrue(estimator.snapshot(NOW, 0).warning);
        // Gaps reset smoothing; the warning still uses the lower recovery threshold.
        sampleSideways(estimator, 28f, NOW * 3);
        assertTrue(estimator.snapshot(NOW * 3, 0).warning);
        sampleSideways(estimator, 24f, NOW * 5);
        assertFalse(estimator.snapshot(NOW * 5, 0).warning);
        sampleSideways(estimator, 28f, NOW * 7);
        assertFalse(estimator.snapshot(NOW * 7, 0).warning);
    }

    @Test public void staleInvalidOrResetSamplesAreUnavailableInsteadOfZeroDegrees() {
        PhoneOrientationEstimator estimator = new PhoneOrientationEstimator();
        assertFalse(estimator.snapshot(NOW, 0).available);
        estimator.update(Float.NaN, 9.81f, 0f, NOW);
        estimator.update(0f, 0f, 0f, NOW);
        estimator.update(0f, 25f, 0f, NOW);
        assertFalse(estimator.snapshot(NOW, 0).available);
        estimator.update(0f, 9.81f, 0f, NOW);
        assertFalse(estimator.snapshot(NOW - 1L, 0).available);
        assertFalse(estimator.snapshot(NOW * 3, 0).available);
        estimator.reset();
        assertFalse(estimator.snapshot(NOW, 0).available);
    }

    @Test public void oldSamplesCannotReplaceCurrentOrientation() {
        PhoneOrientationEstimator estimator = new PhoneOrientationEstimator();
        estimator.update(0f, 9.81f, 0f, NOW);
        estimator.update(9.81f, 0f, 0f, NOW - 1L);
        assertEquals(0f, estimator.snapshot(NOW, 0).deviationDegrees, 0.01f);
    }

    private static void sampleSideways(PhoneOrientationEstimator estimator, float degrees, long timestamp) {
        double radians = Math.toRadians(degrees);
        estimator.update((float) (-9.81 * Math.sin(radians)),
                (float) (9.81 * Math.cos(radians)), 0f, timestamp);
    }
}
