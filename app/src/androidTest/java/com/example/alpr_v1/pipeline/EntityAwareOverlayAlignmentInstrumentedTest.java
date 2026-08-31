package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.alpr_v1.ui.EntityAwareOverlayAlignment;
import com.example.alpr_v1.ui.OverlayItem;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class EntityAwareOverlayAlignmentInstrumentedTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void overlappingNeighborDoesNotFollowFocusedPlate() {
        OverlayItem vehicleA = item(
                OverlayItem.Kind.VEHICLE, 1L,
                0.10f, 0.10f, 0.70f, 0.80f
        );
        OverlayItem vehicleB = item(
                OverlayItem.Kind.VEHICLE, 2L,
                0.30f, 0.10f, 0.90f, 0.80f
        );
        OverlayItem roiA = item(
                OverlayItem.Kind.VEHICLE_ROI, 1L,
                0.20f, 0.20f, 0.65f, 0.70f
        );
        OverlayItem plateA = item(
                OverlayItem.Kind.PLATE, 101L,
                0.45f, 0.45f, 0.55f, 0.50f
        );
        TargetSnapshot live = target(
                item(OverlayItem.Kind.PLATE, 101L,
                        0.55f, 0.45f, 0.65f, 0.50f)
        );

        List<OverlayItem> aligned = EntityAwareOverlayAlignment.align(
                Arrays.asList(vehicleA, vehicleB, roiA, plateA),
                live,
                1L
        );

        assertEquals(0.20f, aligned.get(0).normalizedBounds.left, EPSILON);
        assertSame(vehicleB, aligned.get(1));
        assertEquals(0.30f, aligned.get(1).normalizedBounds.left, EPSILON);
        assertEquals(0.30f, aligned.get(2).normalizedBounds.left, EPSILON);
        assertEquals(0.55f, aligned.get(3).normalizedBounds.left, EPSILON);
    }

    @Test
    public void missingEntityProofLeavesDiagnosticsUnchanged() {
        OverlayItem vehicleA = item(
                OverlayItem.Kind.VEHICLE, 1L,
                0.10f, 0.10f, 0.70f, 0.80f
        );
        OverlayItem vehicleB = item(
                OverlayItem.Kind.VEHICLE, 2L,
                0.30f, 0.10f, 0.90f, 0.80f
        );
        OverlayItem plateA = item(
                OverlayItem.Kind.PLATE, 101L,
                0.45f, 0.45f, 0.55f, 0.50f
        );

        List<OverlayItem> aligned = EntityAwareOverlayAlignment.align(
                Arrays.asList(vehicleA, vehicleB, plateA),
                target(item(OverlayItem.Kind.PLATE, 101L,
                        0.55f, 0.45f, 0.65f, 0.50f)),
                0L
        );

        assertSame(vehicleA, aligned.get(0));
        assertSame(vehicleB, aligned.get(1));
        assertEquals(0.55f, aligned.get(2).normalizedBounds.left, EPSILON);
    }

    private static TargetSnapshot target(OverlayItem plate) {
        return new TargetSnapshot(
                TargetSnapshot.State.TRACKING,
                plate.trackId,
                plate,
                0.90f,
                0.05f,
                0.90f,
                8,
                0,
                3,
                1,
                3,
                1L,
                null,
                plate.trackId,
                "test",
                0,
                0,
                3,
                1L,
                1L,
                0
        );
    }

    private static OverlayItem item(
            OverlayItem.Kind kind,
            long trackId,
            float left,
            float top,
            float right,
            float bottom
    ) {
        return new OverlayItem(
                kind,
                new RectF(left, top, right, bottom),
                Collections.emptyList(),
                "",
                trackId,
                false
        );
    }
}
