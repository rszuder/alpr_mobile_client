package com.example.alpr_v1.ui;

import static org.junit.Assert.assertEquals;

import android.graphics.RectF;

import org.junit.Test;

import java.util.Collections;

public final class PlateOverlayFreshnessTest {
    @Test
    public void singleMissRetainsPlateButExpiredGeometryIsRemoved() {
        PlateOverlayFreshness freshness = new PlateOverlayFreshness();
        OverlayItem plate = plate(11L, false);
        long start = 1_000_000_000L;

        freshness.recordFresh(Collections.singletonList(plate), start);

        assertEquals(
                1,
                freshness.retainDisplayable(
                        Collections.singletonList(plate),
                        start + 100_000_000L
                ).size()
        );
        assertEquals(
                0,
                freshness.retainDisplayable(
                        Collections.singletonList(plate),
                        start + 450_000_000L
                ).size()
        );
    }

    @Test
    public void carriedPredictionDoesNotExtendFreshnessDeadline() {
        PlateOverlayFreshness freshness = new PlateOverlayFreshness();
        OverlayItem fresh = plate(12L, false);
        OverlayItem predicted = plate(12L, true);
        long start = 2_000_000_000L;

        freshness.recordFresh(Collections.singletonList(fresh), start);
        freshness.recordFresh(
                Collections.singletonList(predicted),
                start + 300_000_000L
        );

        assertEquals(
                0,
                freshness.retainDisplayable(
                        Collections.singletonList(predicted),
                        start + 400_000_000L
                ).size()
        );
    }

    @Test
    public void freshKltUpdateRestartsDeadline() {
        PlateOverlayFreshness freshness = new PlateOverlayFreshness();
        OverlayItem plate = plate(13L, false);
        long start = 3_000_000_000L;

        freshness.recordFresh(Collections.singletonList(plate), start);
        freshness.recordFresh(
                Collections.singletonList(plate),
                start + 300_000_000L
        );

        assertEquals(
                1,
                freshness.retainDisplayable(
                        Collections.singletonList(plate),
                        start + 600_000_000L
                ).size()
        );
    }

    private static OverlayItem plate(long trackId, boolean predicted) {
        return new OverlayItem(
                OverlayItem.Kind.PLATE,
                new RectF(0.1f, 0.1f, 0.2f, 0.2f),
                Collections.emptyList(),
                "",
                trackId,
                predicted
        );
    }
}
