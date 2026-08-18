package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Detection {
    public final int classId;
    public final float confidence;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;
    public final List<Point2> keypoints;

    public Detection(
            int classId,
            float confidence,
            float left,
            float top,
            float right,
            float bottom,
            List<Point2> keypoints
    ) {
        this.classId = classId;
        this.confidence = confidence;
        this.left = Math.min(left, right);
        this.top = Math.min(top, bottom);
        this.right = Math.max(left, right);
        this.bottom = Math.max(top, bottom);
        this.keypoints = Collections.unmodifiableList(new ArrayList<>(keypoints));
    }

    public float width() { return Math.max(0f, right - left); }
    public float height() { return Math.max(0f, bottom - top); }
    public float centerX() { return (left + right) * 0.5f; }
    public float centerY() { return (top + bottom) * 0.5f; }
}
