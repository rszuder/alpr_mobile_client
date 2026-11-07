package com.example.alpr_v1.ui;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.pipeline.PlateGeometry;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.Point2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlateDisplayGeometryTest {
    private static final NormalizedBounds THIN_BOX = new NormalizedBounds(.1f, .204f, .3f, .206f);

    @Test public void recognitionOutlineUsesFiftyPixelQuadInsteadOfTwoPixelBox() {
        PlateGeometry raw = geometry(1000, 1000);
        PlateDisplayGeometry display = PlateDisplayGeometry.fromRecognition(raw);
        assertTrue(display.hasQuad());
        assertEquals(50f, display.bounds.height() * display.sourceHeight, .0001f);
        assertEquals(2f, raw.bboxHeightPx(), 0f);
        assertEquals(180f, display.bounds.top * display.sourceHeight, .0001f);
        assertEquals(230f, display.bounds.bottom * display.sourceHeight, .0001f);
    }

    @Test public void preZoomEffectiveCropHasItsOwnDimensionsAndPoints() {
        PlateGeometry raw = geometry(2000, 1500);
        PlateGeometry crop = geometry(1000, 500);
        PlateGeometry tagged = raw.withAutoZoomCropGeometry(crop, 23L);
        PlateDisplayGeometry display = PlateDisplayGeometry.fromRecognition(tagged);
        assertEquals(1000, display.sourceWidth);
        assertEquals(500, display.sourceHeight);
        assertEquals(.46f, display.bounds.bottom, .0001f);
        assertEquals(2000, tagged.sourceWidthPx);
        assertEquals(1500, tagged.sourceHeightPx);
        assertEquals(23L, tagged.cropReferenceSourceSequence);
        assertSame(crop, tagged.forRecognition());
    }

    @Test public void unorderedActualPointsAreCanonicalAndRemainIndependentFromBBox() {
        List<Point2> points = normalizedCorners();
        Collections.rotate(points, 2);
        PlateDisplayGeometry display = PlateDisplayGeometry.fromNormalized(points, THIN_BOX, 1000, 1000);
        assertEquals(.1f, display.cornersNorm.get(0).x, .0001f);
        assertEquals(.18f, display.cornersNorm.get(0).y, .0001f);
        assertEquals(.3f, display.cornersNorm.get(1).x, .0001f);
        assertEquals(.23f, display.cornersNorm.get(2).y, .0001f);
        assertEquals(.1f, display.cornersNorm.get(3).x, .0001f);
    }

    @Test public void missingCornersKeepLegacyBoxWithoutInventingAnOutline() {
        PlateDisplayGeometry display = PlateDisplayGeometry.fromNormalized(
                Collections.emptyList(), THIN_BOX, 1000, 1000);
        assertFalse(display.hasQuad());
        assertTrue(display.available());
        assertEquals(THIN_BOX, display.bounds);
        assertTrue(display.cornersNorm.isEmpty());
    }

    @Test public void invalidCornersFallBackWithoutChangingRawValues() {
        for (List<Point2> invalid : Arrays.asList(
                Arrays.asList(new Point2(.1f,.2f), new Point2(.1f,.2f), new Point2(.3f,.3f), new Point2(.1f,.3f)),
                Arrays.asList(new Point2(.1f,.2f), new Point2(.2f,.2f), new Point2(.3f,.2f), new Point2(.4f,.2f)),
                Arrays.asList(new Point2(Float.NaN,.2f), new Point2(.3f,.2f), new Point2(.3f,.3f), new Point2(.1f,.3f)),
                Arrays.asList(new Point2(.1f,Float.POSITIVE_INFINITY), new Point2(.3f,.2f), new Point2(.3f,.3f), new Point2(.1f,.3f)),
                Arrays.asList(new Point2(-.1f,.2f), new Point2(.3f,.2f), new Point2(.3f,.3f), new Point2(.1f,.3f)),
                Arrays.asList(null, new Point2(.3f,.2f), new Point2(.3f,.3f), new Point2(.1f,.3f)))) {
            PlateDisplayGeometry display = PlateDisplayGeometry.fromNormalized(invalid, THIN_BOX, 1000, 1000);
            assertFalse(display.hasQuad());
            assertEquals(THIN_BOX, display.bounds);
        }
    }

    @Test public void refreshedOutlineCarriesNewSourceDimensionsAndPositionTogether() {
        PlateDisplayGeometry old = PlateDisplayGeometry.fromRecognition(geometry(1000, 1000));
        List<Point2> moved = new ArrayList<>();
        for (Point2 point : normalizedCorners()) moved.add(new Point2(point.x + .1f, point.y + .2f));
        PlateDisplayGeometry next = PlateDisplayGeometry.fromNormalized(moved, THIN_BOX, 640, 480);
        assertTrue(old.mayReplaceWith(next));
        assertEquals(640, next.sourceWidth);
        assertEquals(480, next.sourceHeight);
        assertEquals(.2f, next.bounds.left, .0001f);
        assertEquals(.38f, next.bounds.top, .0001f);
        assertEquals(.05f, next.bounds.height(), .0001f);
    }

    @Test public void bboxOnlyRefreshCannotOverwriteMeasuredQuad() {
        PlateDisplayGeometry measured = PlateDisplayGeometry.fromRecognition(geometry(1000, 1000));
        PlateDisplayGeometry boxOnly = PlateDisplayGeometry.fromNormalized(null, THIN_BOX, 640, 480);
        assertFalse(measured.mayReplaceWith(boxOnly));
        assertTrue(boxOnly.mayReplaceWith(measured));
        assertTrue(boxOnly.mayReplaceWith(boxOnly));
        assertFalse(measured.mayReplaceWith(null));
    }

    @Test public void sourceDataCannotMutateOwnedOutline() {
        List<Point2> points = normalizedCorners();
        PlateDisplayGeometry display = PlateDisplayGeometry.fromNormalized(points, THIN_BOX, 1000, 1000);
        points.clear();
        assertEquals(4, display.cornersNorm.size());
        assertEquals(.05f, display.bounds.height(), .0001f);
        try {
            display.cornersNorm.clear();
            fail("Outline must be immutable");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test public void sourceAspectDoesNotAlterMeasuredPixelExtent() {
        for (int[] size : new int[][]{{1000,1000},{1000,500},{640,1280}}) {
            PlateDisplayGeometry display = PlateDisplayGeometry.fromRecognition(geometry(size[0],size[1]));
            assertEquals(200f, display.bounds.width() * size[0], .0001f);
            assertEquals(50f, display.bounds.height() * size[1], .0001f);
        }
    }

    @Test public void unavailableSourceCannotRefreshExistingGeometry() {
        PlateDisplayGeometry unavailable = PlateDisplayGeometry.fromRecognition(PlateGeometry.unavailable());
        assertFalse(unavailable.available());
        assertFalse(unavailable.hasQuad());
        assertFalse(PlateDisplayGeometry.fromRecognition(geometry(1000,1000)).mayReplaceWith(unavailable));
        assertFalse(PlateDisplayGeometry.fromRecognition(null).available());
    }

    private static List<Point2> normalizedCorners() {
        return new ArrayList<>(Arrays.asList(new Point2(.1f,.18f),new Point2(.3f,.18f),
                new Point2(.3f,.23f),new Point2(.1f,.23f)));
    }

    private static PlateGeometry geometry(int width, int height) {
        List<Point2> corners = Arrays.asList(new Point2(100,180), new Point2(300,180),
                new Point2(300,230), new Point2(100,230));
        return PlateGeometry.from(width,height,new Detection(0,.9f,100,204,300,206,corners),corners);
    }
}
