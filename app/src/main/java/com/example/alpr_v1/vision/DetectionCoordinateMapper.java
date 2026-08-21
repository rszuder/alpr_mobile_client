package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.List;

/** Mapuje detekcję z tensora letterbox do pełnej klatki, również dla ROI. */
public final class DetectionCoordinateMapper {
    private DetectionCoordinateMapper() {}

    public static Detection toSource(
            Detection detection,
            PreparedInput input,
            int offsetX,
            int offsetY
    ) {
        List<Point2> points = new ArrayList<>();
        for (Point2 point : detection.keypoints) {
            points.add(new Point2(
                    offsetX + input.toSourceX(point.x),
                    offsetY + input.toSourceY(point.y),
                    point.confidence
            ));
        }
        return new Detection(
                detection.classId,
                detection.confidence,
                offsetX + input.toSourceX(detection.left),
                offsetY + input.toSourceY(detection.top),
                offsetX + input.toSourceX(detection.right),
                offsetY + input.toSourceY(detection.bottom),
                points
        );
    }
}
