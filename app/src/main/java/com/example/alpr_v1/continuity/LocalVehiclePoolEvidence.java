package com.example.alpr_v1.continuity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Source-bound local object evidence. It never represents an MP or MT measurement. */
public final class LocalVehiclePoolEvidence {
    public enum Status { PROVEN, CONTRADICTED, UNAVAILABLE }
    public static final class Entry {
        public final long entityId, vehicleTrackId, ownerEpoch;
        public final Status status;
        public final boolean vehicle, plate;
        public Entry(long entityId,long vehicleTrackId,long ownerEpoch,Status status,boolean vehicle,boolean plate) {
            this.entityId=entityId;this.vehicleTrackId=vehicleTrackId;this.ownerEpoch=ownerEpoch;
            this.status=status;this.vehicle=vehicle;this.plate=plate;
            if(entityId<=0||status==null||status==Status.PROVEN&&(!vehicle&&!plate||vehicleTrackId<=0||ownerEpoch<=0))
                throw new IllegalArgumentException("invalid local owner evidence");
        }
    }
    private static final LocalVehiclePoolEvidence NONE=new LocalVehiclePoolEvidence(null,Collections.emptyMap());
    public final SourceFrameStamp source;
    public final Map<Long,Entry> entries;
    public LocalVehiclePoolEvidence(SourceFrameStamp source,Map<Long,Entry> entries) {
        this.source=source;
        Map<Long,Entry> copy=new LinkedHashMap<>();
        for(Map.Entry<Long,Entry> item:entries.entrySet()) {
            if(item.getValue()==null||item.getKey()!=item.getValue().entityId)throw new IllegalArgumentException("local owner key mismatch");
            copy.put(item.getKey(),item.getValue());
        }
        this.entries=Collections.unmodifiableMap(copy);
    }
    public static LocalVehiclePoolEvidence unavailable(){return NONE;}
    public boolean matches(SourceFrameStamp stamp) {
        return source!=null&&stamp!=null&&source.domain.cameraDerived()&&source.domain==stamp.domain
                &&source.sceneGeneration==stamp.sceneGeneration&&source.visualEpoch==stamp.visualEpoch
                &&source.cameraTransformGeneration==stamp.cameraTransformGeneration
                &&source.sourceSequence==stamp.sourceSequence&&source.sourceTimestampNanos==stamp.sourceTimestampNanos;
    }
    public boolean matches(long sequence,long time,SourceTimestampDomain domain) {
        return source!=null&&source.domain.cameraDerived()&&source.domain==domain
                &&source.sourceSequence==sequence&&source.sourceTimestampNanos==time;
    }
    public boolean proves(long entityId) {Entry e=entries.get(entityId);return e!=null&&e.status==Status.PROVEN;}
    public int provenCount(){int n=0;for(Entry e:entries.values())if(e.status==Status.PROVEN)n++;return n;}
}
