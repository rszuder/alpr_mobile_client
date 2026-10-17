package com.example.alpr_v1.experiment;

import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.GeometryUtils;
import com.example.alpr_v1.vision.Point2;
import java.util.ArrayList;
import java.util.List;

/**
 * Research-only representation of the *original* MT Pose output.
 * Independent of rectification, crop creation, tracking and MZ execution.
 * The four points are in source-frame coordinates, then normalized by the
 * full source-frame size. A BBOX is NEVER used to create missing keypoints.
 */
public final class MtPoseGeometryEncoder {
    public static final String COORDINATE_SPACE = "source_frame_normalized_v1";
    public static final String CORNER_ORDER = "TL_TR_BR_BL";

    public static final class Result {
        public final String status;
        public final String cornersNorm;
        private Result(String status, String cornersNorm) {
            this.status = status;
            this.cornersNorm = cornersNorm;
        }
    }

    private MtPoseGeometryEncoder() { }

    public static Result encode(Detection detection, boolean validQuad, int sourceWidth, int sourceHeight) {
        if (detection == null || detection.keypoints == null || detection.keypoints.size() < 4) {
            return new Result("MISSING_KEYPOINTS", "");
        }
        if (!validQuad) return new Result("INVALID_QUAD", "");
        if (sourceWidth <= 0 || sourceHeight <= 0) return new Result("INVALID_SOURCE_SIZE", "");

        List<Point2> four = new ArrayList<>(detection.keypoints.subList(0, 4));
        for (Point2 point : four) {
            if (point == null || !Float.isFinite(point.x) || !Float.isFinite(point.y)
                    || point.x < 0f || point.x > sourceWidth || point.y < 0f || point.y > sourceHeight) {
                return new Result("INVALID_COORDINATES", "");
            }
        }
        // Match the canonical ordering used by PlateGeometry.from for legacy crops.
        // This reorders the *actual model points*; it does not synthesize any point.
        final List<Point2> ordered;
        try {
            ordered = GeometryUtils.orderQuad(four);
        } catch (RuntimeException error) {
            return new Result("INVALID_POLYGON", "");
        }
        if (ordered == null || ordered.size() != 4 || !isConvex(ordered)) {
            return new Result("INVALID_POLYGON", "");
        }
        StringBuilder result = new StringBuilder();
        for (Point2 point : ordered) {
            double x = (double) point.x / sourceWidth;
            double y = (double) point.y / sourceHeight;
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) {
                return new Result("INVALID_COORDINATES", "");
            }
            if (result.length() != 0) result.append(';');
            result.append(Double.toString(x)).append(':').append(Double.toString(y));
        }
        return new Result("VALID_QUAD", result.toString());
    }

    private static boolean isConvex(List<Point2> points) {
        double sign = 0;
        double polygonTwiceArea = 0;
        for (int index = 0; index < 4; index++) {
            Point2 a = points.get(index), b = points.get((index + 1) % 4);
            Point2 c = points.get((index + 2) % 4);
            double turn = ((double) b.x - a.x) * ((double) c.y - b.y)
                    - ((double) b.y - a.y) * ((double) c.x - b.x);
            if (!Double.isFinite(turn) || Math.abs(turn) <= 1e-9) return false;
            if (sign == 0) sign = Math.signum(turn);
            else if (Math.signum(turn) != sign) return false;
            polygonTwiceArea += (double) a.x * b.y - (double) b.x * a.y;
        }
        return Math.abs(polygonTwiceArea) > 1e-8;
    }
}
