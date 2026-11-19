package com.example.alpr_v1.tracking;

import com.example.alpr_v1.ui.OverlayItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Wynik jednej aktualizacji trackera, używany równocześnie przez UI i scheduler. */
public final class PreviewTrackingFrame {
    public enum State { LIVE, REPLAYING, UNAVAILABLE }
    public final State state;
    public final List<TrackedPlate> trackedPlates;
    public final List<OverlayItem> overlayItems;
    public final long updatedAtNanos;
    public final com.example.alpr_v1.continuity.SourceFrameStamp sourceStamp;
    /** False after a camera-sample gap: valid positives are usable, an empty flow is N/A. */
    public final boolean absenceEvaluated;
    /** A live result for B does not evaluate the absence of unreturned owner A. */
    public final boolean ownerScopedEvaluation;

    PreviewTrackingFrame(List<TrackedPlate> trackedPlates, long updatedAtNanos) {
        this(trackedPlates, updatedAtNanos, null, true, State.LIVE);
    }

    private PreviewTrackingFrame(List<TrackedPlate> trackedPlates, long updatedAtNanos,
            com.example.alpr_v1.continuity.SourceFrameStamp sourceStamp, boolean absenceEvaluated, State state) {
        this(trackedPlates,updatedAtNanos,sourceStamp,absenceEvaluated,state,false);
    }
    private PreviewTrackingFrame(List<TrackedPlate> trackedPlates,long updatedAtNanos,
            com.example.alpr_v1.continuity.SourceFrameStamp sourceStamp,boolean absenceEvaluated,State state,boolean ownerScoped) {
        List<TrackedPlate> safe = trackedPlates == null
                ? Collections.emptyList()
                : new ArrayList<>(trackedPlates);
        this.trackedPlates = Collections.unmodifiableList(safe);
        List<OverlayItem> overlays = new ArrayList<>(safe.size());
        for (TrackedPlate plate : safe) {
            if (plate != null && plate.overlayItem != null) overlays.add(plate.overlayItem);
        }
        this.overlayItems = Collections.unmodifiableList(overlays);
        this.updatedAtNanos = Math.max(0L, updatedAtNanos);
        this.sourceStamp = sourceStamp;
        this.absenceEvaluated = absenceEvaluated;
        this.state = state;
        ownerScopedEvaluation=ownerScoped;
    }

    PreviewTrackingFrame withSourceStamp(com.example.alpr_v1.continuity.SourceFrameStamp source) {
        return withSourceStamp(source,absenceEvaluated);
    }

    PreviewTrackingFrame withSourceStamp(com.example.alpr_v1.continuity.SourceFrameStamp source,boolean evaluated) {
        return withSourceStamp(source,evaluated,ownerScopedEvaluation);
    }
    PreviewTrackingFrame withSourceStamp(com.example.alpr_v1.continuity.SourceFrameStamp source,boolean evaluated,boolean ownerScoped) {
        return new PreviewTrackingFrame(trackedPlates,updatedAtNanos,source,evaluated,state,ownerScoped);
    }

    static PreviewTrackingFrame pending(State state,com.example.alpr_v1.continuity.SourceFrameStamp source) {
        return new PreviewTrackingFrame(Collections.emptyList(),android.os.SystemClock.elapsedRealtimeNanos(),source,false,state);
    }
}
