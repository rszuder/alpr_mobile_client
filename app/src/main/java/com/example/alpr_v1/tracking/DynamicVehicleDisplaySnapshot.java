package com.example.alpr_v1.tracking;

import com.example.alpr_v1.domain.NormalizedBounds;
import com.example.alpr_v1.domain.NormalizedQuad;
import com.example.alpr_v1.domain.PlateTextConsensus;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One atomic owner/number/geometry presentation. Contains no camera bitmap or neural output. */
public final class DynamicVehicleDisplaySnapshot {
    public final long sourceTimeNanos;
    public final long analysisRunId, sceneGeneration;
    public final Map<Long,Long> canonicalOwners;
    public final Map<Long,DynamicVehicleIdentity.Presentation> identities;
    public final Map<Long,NormalizedBounds> localGeometry;
    /** Current low-confidence pixels for presentation only; never identity/MT/MZ proof. */
    public final Map<Long,NormalizedBounds> tentativeGeometry;
    public final Map<Long,Long> plateOwners;
    public final Map<Long,com.example.alpr_v1.acquisition.DynamicVehicleSizeGate.Decision> sizeDecisions;
    public final Map<Long,DynamicPlateGeometryGate.Decision> plateGeometryDecisions;
    public static final class Vehicle {
        public final long entityId,plateTrackId,measuredAtNanos,plateMeasuredAtNanos,plateProofAtNanos,geometryAtNanos;
        public final PlateTextConsensus reading;
        /** Historical best nonempty aggregate; current evidence above remains authoritative for acquisition. */
        public final PlateTextConsensus displayReading;
        public final long displayReadingSourceNanos;
        public final int mzAttempts;
        /** A real queued/running MZ job for this exact owner, not a historical attempt counter. */
        public final boolean mzInProgress;
        public final NormalizedQuad plate;
        Vehicle(long entity,long track,long measured,long plateMeasured,long plateProof,
                PlateTextConsensus reading,NormalizedQuad plate) {
            this(entity,track,measured,plateMeasured,plateProof,reading,plate,plateProof>0?plateProof:measured);
        }
        Vehicle(long entity,long track,long measured,long plateMeasured,long plateProof,
                PlateTextConsensus reading,NormalizedQuad plate,long geometryAt) {
            this(entity,track,measured,plateMeasured,plateProof,reading,plate,geometryAt,
                    com.example.alpr_v1.domain.DynamicReadingSelection.badgeEligible(reading)?reading:PlateTextConsensus.EMPTY,
                    measured,reading!=null&&reading.available()?1:0);
        }
        Vehicle(long entity,long track,long measured,long plateMeasured,long plateProof,PlateTextConsensus reading,
                NormalizedQuad plate,long geometryAt,PlateTextConsensus displayReading,long displaySource,int mzAttempts) {
            this(entity,track,measured,plateMeasured,plateProof,reading,plate,geometryAt,displayReading,displaySource,mzAttempts,false);
        }
        Vehicle(long entity,long track,long measured,long plateMeasured,long plateProof,PlateTextConsensus reading,
                NormalizedQuad plate,long geometryAt,PlateTextConsensus displayReading,long displaySource,int mzAttempts,
                boolean mzInProgress) {
            entityId=entity;plateTrackId=track;measuredAtNanos=measured;
            plateMeasuredAtNanos=plateMeasured;plateProofAtNanos=plateProof;this.reading=reading;this.plate=plate;
            geometryAtNanos=geometryAt;
            this.displayReading=displayReading;displayReadingSourceNanos=displaySource;this.mzAttempts=mzAttempts;
            this.mzInProgress=mzInProgress;
        }
    }
    public final Map<Long,Vehicle> vehicles;
    DynamicVehicleDisplaySnapshot(long time,long run,long scene,Map<Long,Long> aliases,
            Map<Long,DynamicVehicleIdentity.Presentation> identities,Map<Long,NormalizedBounds> geometry,Map<Long,Long> plates) {
        this(time,run,scene,aliases,identities,geometry,plates,Collections.emptyMap());
    }
    DynamicVehicleDisplaySnapshot(long time,long run,long scene,Map<Long,Long> aliases,
            Map<Long,DynamicVehicleIdentity.Presentation> identities,Map<Long,NormalizedBounds> geometry,
            Map<Long,Long> plates,Map<Long,Vehicle> vehicles) {
        this(time,run,scene,aliases,identities,geometry,plates,vehicles,Collections.emptyMap());
    }
    DynamicVehicleDisplaySnapshot(long time,long run,long scene,Map<Long,Long> aliases,
            Map<Long,DynamicVehicleIdentity.Presentation> identities,Map<Long,NormalizedBounds> geometry,
            Map<Long,Long> plates,Map<Long,Vehicle> vehicles,
            Map<Long,com.example.alpr_v1.acquisition.DynamicVehicleSizeGate.Decision> sizes) {
        this(time,run,scene,aliases,identities,geometry,plates,vehicles,sizes,Collections.emptyMap());
    }
    DynamicVehicleDisplaySnapshot(long time,long run,long scene,Map<Long,Long> aliases,
            Map<Long,DynamicVehicleIdentity.Presentation> identities,Map<Long,NormalizedBounds> geometry,
            Map<Long,Long> plates,Map<Long,Vehicle> vehicles,
            Map<Long,com.example.alpr_v1.acquisition.DynamicVehicleSizeGate.Decision> sizes,
            Map<Long,DynamicPlateGeometryGate.Decision> plateGeometry) {
        this(time,run,scene,aliases,identities,geometry,plates,vehicles,sizes,plateGeometry,Collections.emptyMap());
    }
    DynamicVehicleDisplaySnapshot(long time,long run,long scene,Map<Long,Long> aliases,
            Map<Long,DynamicVehicleIdentity.Presentation> identities,Map<Long,NormalizedBounds> geometry,
            Map<Long,Long> plates,Map<Long,Vehicle> vehicles,
            Map<Long,com.example.alpr_v1.acquisition.DynamicVehicleSizeGate.Decision> sizes,
            Map<Long,DynamicPlateGeometryGate.Decision> plateGeometry,Map<Long,NormalizedBounds> tentative) {
        sourceTimeNanos=time;
        analysisRunId=run;sceneGeneration=scene;
        canonicalOwners=Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
        this.identities=Collections.unmodifiableMap(new LinkedHashMap<>(identities));
        localGeometry=Collections.unmodifiableMap(new LinkedHashMap<>(geometry));
        tentativeGeometry=Collections.unmodifiableMap(new LinkedHashMap<>(tentative));
        plateOwners=Collections.unmodifiableMap(new LinkedHashMap<>(plates));
        this.vehicles=Collections.unmodifiableMap(new LinkedHashMap<>(vehicles));
        sizeDecisions=Collections.unmodifiableMap(new LinkedHashMap<>(sizes));
        plateGeometryDecisions=Collections.unmodifiableMap(new LinkedHashMap<>(plateGeometry));
    }
}
