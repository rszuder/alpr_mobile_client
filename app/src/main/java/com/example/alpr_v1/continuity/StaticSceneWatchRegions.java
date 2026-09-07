package com.example.alpr_v1.continuity;

import com.example.alpr_v1.domain.NormalizedBounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed image regions, deliberately without entity or technical track identity. */
public final class StaticSceneWatchRegions {
    public final List<NormalizedBounds> bounds;
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
    }
}
