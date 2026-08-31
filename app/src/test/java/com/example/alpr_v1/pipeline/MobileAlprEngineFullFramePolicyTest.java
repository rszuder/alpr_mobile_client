package com.example.alpr_v1.pipeline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MobileAlprEngineFullFramePolicyTest {
    @Test
    public void fullSensorRegionDoesNotApplyScanWorkingViewport() {
        VehicleRoiSelector.Region region =
                MobileAlprEngine.fullSensorFrameRegion(1920, 1080);

        assertEquals(0, region.left);
        assertEquals(0, region.top);
        assertEquals(1920, region.right);
        assertEquals(1080, region.bottom);
    }
}
