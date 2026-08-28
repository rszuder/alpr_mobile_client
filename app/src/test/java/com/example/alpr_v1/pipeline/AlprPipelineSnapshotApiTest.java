package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.alpr_v1.tracking.VehicleTrackingFrame;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class AlprPipelineSnapshotApiTest {
    @Test
    public void phaseThreeCanReadImmutableVehicleTrackingFrame() throws Exception {
        Method method = AlprPipeline.class.getMethod("latestVehicleTrackingFrame");

        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(VehicleTrackingFrame.class, method.getReturnType());
    }
}
