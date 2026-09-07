package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.domain.NormalizedBounds;

/** Centered optical zoom changes target coordinates as well as its dimensions. */
public final class ZoomTargetGeometry {
    private ZoomTargetGeometry() { }
    static NormalizedBounds transform(NormalizedBounds bounds, float ratio) {
        if (!Float.isFinite(ratio) || ratio <= 0f) throw new IllegalArgumentException("zoom ratio");
        return new NormalizedBounds(map(bounds.left,ratio),map(bounds.top,ratio),
                map(bounds.right,ratio),map(bounds.bottom,ratio));
    }
    public static float map(float coordinate, float ratio) {
        return Math.max(0f,Math.min(1f,.5f+(coordinate-.5f)*ratio));
    }
}
