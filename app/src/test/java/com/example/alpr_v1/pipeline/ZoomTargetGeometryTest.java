package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.domain.NormalizedBounds;
import org.junit.Test;
import static org.junit.Assert.*;

public class ZoomTargetGeometryTest {
    @Test public void opticalZoomMovesOffCenterPlateToActualMtPosition() {
        NormalizedBounds base=new NormalizedBounds(300f/960f,412f/1280f,347f/960f,436f/1280f);
        NormalizedBounds zoomed=ZoomTargetGeometry.transform(base,1.8f);
        assertEquals(.2066f,zoomed.centerX(),.001f);
        assertEquals(.19625f,zoomed.centerY(),.001f);
        assertEquals(base.width()*1.8f,zoomed.width(),.0001f);
        NormalizedBounds returned=ZoomTargetGeometry.transform(zoomed,1f/1.8f);
        assertEquals(base.left,returned.left,.0001f); assertEquals(base.bottom,returned.bottom,.0001f);
    }
}
