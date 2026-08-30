package com.example.alpr_v1.acquisition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable public view of the entity-keyed Scan queue. */
public final class AcquisitionQueueSnapshot {
    public final long revision;
    public final long sceneGeneration;
    public final long activeEntityId;
    public final List<AcquisitionCandidate> candidates;
    public final Map<Long, AcquisitionPriorityBreakdown> prioritiesByEntityId;

    public AcquisitionQueueSnapshot(
            long revision,
            long sceneGeneration,
            long activeEntityId,
            List<AcquisitionCandidate> candidates,
            Map<Long, AcquisitionPriorityBreakdown> prioritiesByEntityId
    ) {
        this.revision = Math.max(0L, revision);
        this.sceneGeneration = Math.max(0L, sceneGeneration);
        this.activeEntityId = Math.max(0L, activeEntityId);
        this.candidates = Collections.unmodifiableList(new ArrayList<>(
                candidates == null ? Collections.emptyList() : candidates
        ));
        this.prioritiesByEntityId = Collections.unmodifiableMap(new LinkedHashMap<>(
                prioritiesByEntityId == null
                        ? Collections.emptyMap() : prioritiesByEntityId
        ));
    }

    public int size() {
        return candidates.size();
    }

    public AcquisitionCandidate find(long entityId) {
        for (AcquisitionCandidate candidate : candidates) {
            if (candidate.entityId == entityId) return candidate;
        }
        return null;
    }

    public static AcquisitionQueueSnapshot empty(long sceneGeneration) {
        return new AcquisitionQueueSnapshot(
                0L, sceneGeneration, 0L,
                Collections.emptyList(), Collections.emptyMap()
        );
    }
}
