package com.example.alpr_v1.experiment;

import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.Point2;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure MT Pose research contract; no crop or MZ is needed. */
public final class MtPoseGeometryEncoderTest {
    private Detection quad() {
        return new Detection(0, .95f, 10, 20, 90, 80, Arrays.asList(
                new Point2(10, 20), new Point2(90, 20),
                new Point2(90, 80), new Point2(10, 80)));
    }

    @Test public void validQuadIsSerializedIndependentlyOfMz() {
        MtPoseGeometryEncoder.Result result = MtPoseGeometryEncoder.encode(quad(), true, 100, 100);
        assertEquals("VALID_QUAD", result.status);
        assertEquals(4, result.cornersNorm.split(";").length);
        assertTrue(result.cornersNorm.startsWith("0.1:0.2;"));
    }

    @Test public void noKeypointsNeverCreatesCornersFromBbox() {
        Detection bbox = new Detection(0, .95f, 10, 20, 90, 80, Collections.emptyList());
        MtPoseGeometryEncoder.Result result = MtPoseGeometryEncoder.encode(bbox, false, 100, 100);
        assertEquals("MISSING_KEYPOINTS", result.status);
        assertEquals("", result.cornersNorm);
    }

    @Test public void invalidQuadIsNotPromotedToValidPose() {
        MtPoseGeometryEncoder.Result result = MtPoseGeometryEncoder.encode(quad(), false, 100, 100);
        assertEquals("INVALID_QUAD", result.status);
        assertEquals("", result.cornersNorm);
    }

    @Test public void missingSourceDimensionsIsExplicit() {
        MtPoseGeometryEncoder.Result result = MtPoseGeometryEncoder.encode(quad(), true, 0, 100);
        assertEquals("INVALID_SOURCE_SIZE", result.status);
        assertEquals("", result.cornersNorm);
    }

    @Test public void outOfFramePointsAreNotClampedSilently() {
        Detection bad = new Detection(0, .9f, 10, 10, 95, 90, Arrays.asList(
                new Point2(-1, 10), new Point2(95, 10),
                new Point2(95, 90), new Point2(10, 90)));
        MtPoseGeometryEncoder.Result result = MtPoseGeometryEncoder.encode(bad, true, 100, 100);
        assertEquals("INVALID_COORDINATES", result.status);
        assertEquals("", result.cornersNorm);
    }
}
