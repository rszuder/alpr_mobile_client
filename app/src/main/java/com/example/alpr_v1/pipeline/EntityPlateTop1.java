package com.example.alpr_v1.pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic ranking after ownership and geometry validation, without OCR text. */
final class EntityPlateTop1 {
    static final class Candidate {
        final int sourceIndex;
        final long entityId;
        final boolean validQuad;
        final float quality, confidence, sharpness, previousAgreement, previousDistance, verticalAgreement;
        Candidate(int index, long entity, boolean validQuad, float quality, float confidence,
                float sharpness, float previousAgreement, float previousDistance, float verticalAgreement) {
            sourceIndex = index; entityId = entity; this.validQuad = validQuad; this.quality = quality;
            this.confidence = confidence; this.sharpness = sharpness; this.previousAgreement = previousAgreement;
            this.previousDistance = previousDistance; this.verticalAgreement = verticalAgreement;
        }
        float score() { return .30f * previousAgreement + .25f * quality + .25f * confidence
                + .10f * sharpness + .10f * verticalAgreement; }
    }
    private static final Comparator<Candidate> RANK = Comparator
            .comparingDouble(Candidate::score).reversed()
            .thenComparing(Comparator.comparingDouble((Candidate c) -> c.quality).reversed())
            .thenComparing(Comparator.comparingDouble((Candidate c) -> c.confidence).reversed())
            .thenComparingDouble(c -> c.previousDistance).thenComparingInt(c -> c.sourceIndex);
    static List<Integer> select(List<Candidate> candidates, long requestedEntityId) {
        Map<Long, Candidate> selected = new HashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate.entityId <= 0 || !candidate.validQuad
                    || requestedEntityId > 0 && candidate.entityId != requestedEntityId) continue;
            Candidate previous = selected.get(candidate.entityId);
            if (previous == null || RANK.compare(candidate, previous) < 0) selected.put(candidate.entityId, candidate);
        }
        List<Integer> indices = new ArrayList<>();
        for (Candidate candidate : selected.values()) indices.add(candidate.sourceIndex);
        indices.sort(Integer::compareTo);
        return indices;
    }
}
