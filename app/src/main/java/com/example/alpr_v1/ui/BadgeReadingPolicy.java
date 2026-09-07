package com.example.alpr_v1.ui;

import com.example.alpr_v1.acquisition.EntityRecognitionSnapshot;

/** The badge retains the strongest reading for its entity until a scene reset. */
public final class BadgeReadingPolicy {
    private BadgeReadingPolicy() {}

    public static EntityRecognitionSnapshot retainBest(
            EntityRecognitionSnapshot previous, EntityRecognitionSnapshot candidate
    ) {
        if (candidate == null || candidate.text.isEmpty()) return previous;
        if (previous == null) return candidate;
        EntityRecognitionSnapshot best = confidence(candidate) > confidence(previous)
                ? candidate : previous;
        // Confirmation of the same text can advance without lowering the displayed confidence.
        if (previous.text.equals(candidate.text) && !best.confirmed
                && (previous.confirmed || candidate.confirmed)) {
            return new EntityRecognitionSnapshot(best.entityId, best.plateTrackId, best.text,
                    best.confidence, true, Math.max(previous.observations, candidate.observations));
        }
        return best;
    }

    private static double confidence(EntityRecognitionSnapshot reading) {
        return Double.isFinite(reading.confidence) ? reading.confidence : 0.0;
    }
}
