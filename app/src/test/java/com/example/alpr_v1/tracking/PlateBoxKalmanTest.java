package com.example.alpr_v1.tracking;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PlateBoxKalmanTest {
    @Test
    public void reducesCenterJitter() {
        PlateBoxKalman filter = new PlateBoxKalman();
        float[] noise = {2.4f, -2.1f, 1.8f, -1.7f, 2.0f, -1.4f, 1.2f, -1.0f};
        float rawError = 0f;
        float filteredError = 0f;
        for (int index = 0; index < noise.length; index++) {
            float expected = 50f + index * 1.5f;
            float measured = expected + noise[index];
            PlateBoxKalman.Box filtered = filter.update(
                    new PlateBoxKalman.Box(measured, 40f, 30f, 12f)
            );
            rawError += Math.abs(measured - expected);
            filteredError += Math.abs(filtered.centerX - expected);
        }
        assertTrue(filteredError < rawError);
    }

    @Test
    public void predictsForwardDuringShortMiss() {
        PlateBoxKalman filter = new PlateBoxKalman();
        filter.update(new PlateBoxKalman.Box(20f, 30f, 24f, 10f));
        filter.update(new PlateBoxKalman.Box(23f, 30f, 24f, 10f));
        filter.update(new PlateBoxKalman.Box(26f, 30f, 24f, 10f));

        PlateBoxKalman.Box predicted = filter.predict();

        assertTrue(predicted.centerX > 26f);
        assertTrue(predicted.width > 20f);
    }
}
