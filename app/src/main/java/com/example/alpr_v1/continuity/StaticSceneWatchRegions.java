package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed image regions, deliberately without entity or technical track identity. */
public final class StaticSceneWatchRegions {
    public final List<NormalizedBounds> bounds;
    public final List<NormalizedBounds> featureBounds;
    public final String source;

    public StaticSceneWatchRegions(List<NormalizedBounds> vehicles, List<NormalizedBounds> plates,
            float margin) {
        List<NormalizedBounds> selected = vehicles != null && !vehicles.isEmpty() ? vehicles : plates;
        source = vehicles != null && !vehicles.isEmpty() ? "vehicles"
                : plates != null && !plates.isEmpty() ? "plates" : "global";
        List<NormalizedBounds> expanded = new ArrayList<>();
        if (selected != null) for (NormalizedBounds b : selected) {
            if (b == null || !b.valid()) continue;
            float dx = b.width() * margin, dy = b.height() * margin;
            expanded.add(new NormalizedBounds(Math.max(0f, b.left - dx), Math.max(0f, b.top - dy),
                    Math.min(1f, b.right + dx), Math.min(1f, b.bottom + dy)));
        }
        bounds = Collections.unmodifiableList(expanded);
        List<NormalizedBounds> features = new ArrayList<>();
        addFeatures(features, plates);
        addFeatures(features, vehicles);
        featureBounds = Collections.unmodifiableList(features);
    }

    private StaticSceneWatchRegions(List<NormalizedBounds> bounds, List<NormalizedBounds> features, String source) {
        this.bounds = Collections.unmodifiableList(bounds);
        featureBounds = Collections.unmodifiableList(features); this.source = source;
    }

    private static void addFeatures(List<NormalizedBounds> result, List<NormalizedBounds> input) {
        if (input == null) return;
        for (NormalizedBounds b : input) {
            if (b == null || !b.valid()) continue;
            boolean duplicate = false;
            for (NormalizedBounds existing : result) if (existing.iou(b) >= .85f) { duplicate = true; break; }
            if (!duplicate) result.add(b);
        }
    }

    public StaticSceneWatchRegions atZoom(float ratio) {
        if (!Float.isFinite(ratio) || ratio <= 1.001f) return this;
        return new StaticSceneWatchRegions(project(bounds, ratio), project(featureBounds, ratio), source);
    }

    private static List<NormalizedBounds> project(List<NormalizedBounds> input, float ratio) {
        List<NormalizedBounds> result = new ArrayList<>();
        for (NormalizedBounds b : input) {
            NormalizedBounds mapped = new NormalizedBounds(.5f + (b.left - .5f) * ratio,
                    .5f + (b.top - .5f) * ratio, .5f + (b.right - .5f) * ratio, .5f + (b.bottom - .5f) * ratio);
            if (mapped.valid()) result.add(mapped);
        }
        return result;
    }
}
