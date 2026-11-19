package com.example.alpr_v1.ui;

import android.graphics.RectF;
import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.tracking.VehicleCandidate;
import com.example.alpr_v1.tracking.VehicleTrackingFrame;
import java.util.*;

/** Presentation projection of a real MP frame. Predicted boxes stay in the tracker only. */
public final class VehicleMeasurementOverlay {
    private VehicleMeasurementOverlay() { }
    /** A measured MP miss cannot erase an owner still supported by local pixels/coasting. */
    public static List<OverlayItem> retainSupportedVehicles(List<OverlayItem> projected, VehicleTrackingFrame frame,
            com.example.alpr_v1.tracking.DynamicVehicleDisplaySnapshot snapshot) {
        if(snapshot==null)return retainObservedVehicles(projected,frame);
        Set<Long> measured=new HashSet<>(),rendered=new HashSet<>();
        if(frame!=null&&snapshot.sourceTimeNanos>=frame.sourceTimestampNanos
                &&snapshot.sourceTimeNanos-frame.sourceTimestampNanos<=com.example.alpr_v1.tracking.DynamicVehicleIdentity.COAST_NANOS)
            for(VehicleCandidate candidate:frame.candidates)
            if(candidate.sourceIndex>=0&&candidate.missedUpdates==0)measured.add(candidate.entityId);
        List<OverlayItem> result=new ArrayList<>();
        if(projected!=null)for(OverlayItem item:projected) {
            if(item==null)continue;
            if(item.kind!=OverlayItem.Kind.VEHICLE){result.add(item);continue;}
            com.example.alpr_v1.tracking.DynamicVehicleIdentity.Presentation owner=snapshot.identities.get(item.trackId);
            if(owner==null||!owner.visible||!measured.contains(item.trackId)&&!snapshot.localGeometry.containsKey(item.trackId))continue;
            NormalizedBounds local=snapshot.localGeometry.get(item.trackId);
            result.add(local==null?item:localItem(item.trackId,local));rendered.add(item.trackId);
        }
        for(Map.Entry<Long,NormalizedBounds> entry:snapshot.localGeometry.entrySet()) {
            com.example.alpr_v1.tracking.DynamicVehicleIdentity.Presentation owner=snapshot.identities.get(entry.getKey());
            if(owner!=null&&owner.visible&&!rendered.contains(entry.getKey()))result.add(localItem(entry.getKey(),entry.getValue()));
        }
        return Collections.unmodifiableList(result);
    }
    private static OverlayItem localItem(long owner,NormalizedBounds b) {
        return new OverlayItem(OverlayItem.Kind.VEHICLE,new RectF(b.left,b.top,b.right,b.bottom),Collections.emptyList(),"",owner,true);
    }
    /** A preview/global motion projection cannot resurrect a vehicle missed by the latest MP. */
    public static List<OverlayItem> retainObservedVehicles(List<OverlayItem> projected, VehicleTrackingFrame frame) {
        if (projected == null || projected.isEmpty()) return Collections.emptyList();
        Set<Long> measured = new HashSet<>();
        if (frame != null) for (VehicleCandidate candidate : frame.candidates) {
            if (candidate.sourceIndex >= 0 && candidate.missedUpdates == 0) measured.add(candidate.entityId);
        }
        List<OverlayItem> result = new ArrayList<>();
        for (OverlayItem item : projected) {
            if (item != null && (item.kind != OverlayItem.Kind.VEHICLE || measured.contains(item.trackId))) result.add(item);
        }
        return Collections.unmodifiableList(result);
    }
    public static List<OverlayItem> from(VehicleTrackingFrame frame,Map<Long,NormalizedBounds> raw) {
        if(frame==null||raw==null||raw.isEmpty())return Collections.emptyList();
        List<OverlayItem> result=new ArrayList<>();
        for(VehicleCandidate candidate:frame.candidates){
            NormalizedBounds measured=raw.get(candidate.entityId);
            if(candidate.predicted||candidate.missedUpdates>0||candidate.sourceIndex<0||measured==null||!measured.valid())continue;
            result.add(new OverlayItem(OverlayItem.Kind.VEHICLE,new RectF(measured.left,measured.top,measured.right,measured.bottom),
                    Collections.emptyList(),String.format(Locale.ROOT,"Pojazd %.0f%%",candidate.detectionConfidence*100),candidate.entityId,false));
        }
        return Collections.unmodifiableList(result);
    }
}
