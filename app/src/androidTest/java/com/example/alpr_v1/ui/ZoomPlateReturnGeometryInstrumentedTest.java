package com.example.alpr_v1.ui;

import android.graphics.PointF;
import android.graphics.RectF;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.alpr_v1.continuity.ContinuityStamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ZoomPlateReturnGeometryInstrumentedTest {
    private ContinuityStamp stamp(long scene,long visual,long transform) { return new ContinuityStamp(scene,visual,transform,1); }
    private OverlayItem plate(float right,long track,String label) {
        return new OverlayItem(OverlayItem.Kind.PLATE,new RectF(.4f,.5f,right,.56f),Arrays.asList(
                new PointF(.4f,.5f),new PointF(right,.503f),new PointF(right,.56f),new PointF(.4f,.557f)),label,track,false);
    }
    @Test public void restoresExactBaseOutlineSmoothlyAndKeepsImprovedLabelAndNewTrack() {
        ZoomPlateReturnGeometry geometry=new ZoomPlateReturnGeometry();
        OverlayItem base=plate(.64f,1,"RJA5001G 35%"),cropped=plate(.59f,9,"RJA5001G 94%");
        geometry.capture(base,stamp(1,1,0),7);
        assertTrue(geometry.beginReturn(Collections.singletonList(cropped),1.8f,stamp(1,1,1),9,7));
        assertEquals(.59f,geometry.atZoom(1.8f,stamp(1,1,2)).get(0).normalizedBounds.right,.00001f);
        assertEquals(.615f,geometry.atZoom(1.4f,stamp(1,1,2)).get(0).normalizedBounds.right,.00001f);
        OverlayItem returned=geometry.atZoom(1f,stamp(1,1,2)).get(0);
        assertEquals(base.normalizedBounds,returned.normalizedBounds);
        assertEquals(base.normalizedKeypoints,returned.normalizedKeypoints);
        assertEquals(cropped.label,returned.label);assertEquals(9,returned.trackId);
        assertTrue(returned.carriedPrediction);
        assertEquals(.59f,cropped.normalizedBounds.right,.00001f);
    }
    @Test public void originalSnapshotSurvivesMutableInputsAndInterruptedReturnProgress() {
        ZoomPlateReturnGeometry geometry=new ZoomPlateReturnGeometry();OverlayItem base=plate(.64f,1,"base");
        geometry.capture(base,stamp(1,1,0),7);
        base.normalizedBounds.right=.55f;base.normalizedKeypoints.get(1).x=.55f;
        OverlayItem zoomed=plate(.58f,1,"better");
        assertTrue(geometry.beginReturn(Collections.singletonList(zoomed),1.8f,stamp(1,1,1),1,7));
        zoomed.normalizedBounds.right=.52f;
        geometry.atZoom(1.2f,stamp(1,1,2));
        assertEquals(.61f,geometry.atZoom(1.4f,stamp(1,1,2)).get(0).normalizedBounds.right,.00001f);
        assertEquals(.64f,geometry.atZoom(1f,stamp(1,1,2)).get(0).normalizedKeypoints.get(1).x,.00001f);
    }
    @Test public void missingDetectionReturnsBaselineWithoutInventingOtherTargets() {
        ZoomPlateReturnGeometry geometry=new ZoomPlateReturnGeometry();geometry.capture(plate(.64f,1,"base"),stamp(1,1,0),7);
        assertTrue(geometry.beginReturn(Collections.emptyList(),1.8f,stamp(1,1,1),1,7));
        assertEquals(.64f,geometry.atZoom(1f,stamp(1,1,2)).get(0).normalizedBounds.right,.00001f);
        assertFalse(geometry.beginReturn(Collections.emptyList(),1.8f,stamp(1,1,1),3,7));
    }
    @Test public void rejectsSceneChangeOwnerChangeMovedTargetAndUnknownTrackChurn() {
        ZoomPlateReturnGeometry geometry=new ZoomPlateReturnGeometry();geometry.capture(plate(.64f,1,"base"),stamp(1,1,0),7);
        List<OverlayItem> clipped=Collections.singletonList(plate(.59f,1,"new"));
        assertFalse(geometry.beginReturn(clipped,1.8f,stamp(2,1,1),1,7));
        assertFalse(geometry.beginReturn(clipped,1.8f,stamp(1,2,1),1,7));
        assertFalse(geometry.beginReturn(clipped,1.8f,stamp(1,1,1),1,8));
        assertFalse(geometry.beginReturn(clipped,1.8f,stamp(1,1,1),9,0));
        OverlayItem moved=new OverlayItem(OverlayItem.Kind.PLATE,new RectF(.65f,.5f,.85f,.56f),Collections.emptyList(),"",1,false);
        assertFalse(geometry.beginReturn(Collections.singletonList(moved),1.8f,stamp(1,1,1),1,7));
        assertTrue(geometry.beginReturn(clipped,1.8f,stamp(1,1,1),1,7));
        assertNull(geometry.atZoom(1f,stamp(2,1,2)));
        geometry.discardForDifferentOwner(8);assertNull(geometry.atZoom(1f,stamp(1,1,2)));
    }
    @Test public void bboxOnlyBaselineAndQuadZoomInterpolateWithoutAJump() {
        ZoomPlateReturnGeometry geometry=new ZoomPlateReturnGeometry();
        geometry.capture(new OverlayItem(OverlayItem.Kind.PLATE,new RectF(.4f,.5f,.64f,.56f),Collections.emptyList(),"",1,false),stamp(1,1,0),7);
        assertTrue(geometry.beginReturn(Collections.singletonList(plate(.58f,1,"new")),1.8f,stamp(1,1,1),1,7));
        assertEquals(4,geometry.atZoom(1.4f,stamp(1,1,2)).get(0).normalizedKeypoints.size());
        assertTrue(geometry.atZoom(1f,stamp(1,1,2)).get(0).normalizedKeypoints.isEmpty());
        geometry.clear();assertNull(geometry.atZoom(1f,stamp(1,1,2)));
    }
}
