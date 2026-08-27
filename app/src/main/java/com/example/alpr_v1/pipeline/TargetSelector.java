package com.example.alpr_v1.pipeline;

import java.util.Collections;
import java.util.List;

/**
 * Pure-Java target ranking used before lock. Once {@code sticky} is true,
 * only the locked track may be selected; a missing track produces no result.
 */
public final class TargetSelector {
    private static final float PREFERRED_TRACK_BONUS = 0.08f;
    private static final float MINIMUM_ASSOCIATION_SCORE = 0.52f;
    private static final float MINIMUM_ASSOCIATION_MARGIN = 0.08f;
    private static final float MINIMUM_APPEARANCE_SIMILARITY = 0.58f;

    public static final class Weights {
        public final float center;
        public final float area;
        public final float detectionQuality;
        public final float sharpness;
        public final float trackAge;

        public Weights(
                float center,
                float area,
                float detectionQuality,
                float sharpness,
                float trackAge
        ) {
            float sum = Math.max(0f, center)
                    + Math.max(0f, area)
                    + Math.max(0f, detectionQuality)
                    + Math.max(0f, sharpness)
                    + Math.max(0f, trackAge);
            if (sum <= 0f) sum = 1f;
            this.center = Math.max(0f, center) / sum;
            this.area = Math.max(0f, area) / sum;
            this.detectionQuality = Math.max(0f, detectionQuality) / sum;
            this.sharpness = Math.max(0f, sharpness) / sum;
            this.trackAge = Math.max(0f, trackAge) / sum;
        }

        public static Weights defaults() {
            return new Weights(0.30f, 0.20f, 0.25f, 0.15f, 0.10f);
        }
    }

    public static final class Candidate {
        public final long trackId;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final float centerX;
        public final float centerY;
        public final float areaRatio;
        public final float detectionQuality;
        public final float sharpness;
        public final int ageFrames;
        public final float[] appearanceDescriptor;

        public Candidate(
                long trackId,
                float centerX,
                float centerY,
                float areaRatio,
                float detectionQuality,
                float sharpness,
                int ageFrames
        ) {
            float halfSide = (float) Math.sqrt(clamp01(areaRatio)) * 0.5f;
            this.trackId = trackId;
            this.left = clamp01(centerX - halfSide);
            this.top = clamp01(centerY - halfSide);
            this.right = clamp01(centerX + halfSide);
            this.bottom = clamp01(centerY + halfSide);
            this.centerX = clamp01(centerX);
            this.centerY = clamp01(centerY);
            this.areaRatio = clamp01(areaRatio);
            this.detectionQuality = clamp01(detectionQuality);
            this.sharpness = clamp01(sharpness);
            this.ageFrames = Math.max(0, ageFrames);
            this.appearanceDescriptor = null;
        }

        public Candidate(
                long trackId,
                float left,
                float top,
                float right,
                float bottom,
                float detectionQuality,
                float sharpness,
                int ageFrames,
                float[] appearanceDescriptor
        ) {
            this.trackId = trackId;
            this.left = clamp01(Math.min(left, right));
            this.top = clamp01(Math.min(top, bottom));
            this.right = clamp01(Math.max(left, right));
            this.bottom = clamp01(Math.max(top, bottom));
            this.centerX = (this.left + this.right) * 0.5f;
            this.centerY = (this.top + this.bottom) * 0.5f;
            this.areaRatio = clamp01(
                    (this.right - this.left) * (this.bottom - this.top)
            );
            this.detectionQuality = clamp01(detectionQuality);
            this.sharpness = clamp01(sharpness);
            this.ageFrames = Math.max(0, ageFrames);
            this.appearanceDescriptor = appearanceDescriptor == null
                    ? null : appearanceDescriptor.clone();
        }
    }

    public static final class Reference {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final float[] appearanceDescriptor;

        public Reference(
                float left,
                float top,
                float right,
                float bottom,
                float[] appearanceDescriptor
        ) {
            this.left = clamp01(Math.min(left, right));
            this.top = clamp01(Math.min(top, bottom));
            this.right = clamp01(Math.max(left, right));
            this.bottom = clamp01(Math.max(top, bottom));
            this.appearanceDescriptor = appearanceDescriptor == null
                    ? null : appearanceDescriptor.clone();
        }
    }

    public static final class Association {
        public final Candidate candidate;
        public final float score;
        public final float secondScore;
        public final String reason;

        private Association(
                Candidate candidate,
                float score,
                float secondScore,
                String reason
        ) {
            this.candidate = candidate;
            this.score = clamp01(score);
            this.secondScore = clamp01(secondScore);
            this.reason = reason == null ? "" : reason;
        }

        public boolean matched() {
            return candidate != null;
        }
    }

    public static final class Selection {
        public final Candidate candidate;
        public final float score;
        public final String reason;

        private Selection(Candidate candidate, float score, String reason) {
            this.candidate = candidate;
            this.score = clamp01(score);
            this.reason = reason == null ? "" : reason;
        }

        public boolean found() {
            return candidate != null;
        }
    }

    private final Weights weights;

    public TargetSelector() {
        this(Weights.defaults());
    }

    public TargetSelector(Weights weights) {
        this.weights = weights == null ? Weights.defaults() : weights;
    }

    public Selection select(
            List<Candidate> candidates,
            long preferredTrackId,
            boolean sticky
    ) {
        List<Candidate> safe = candidates == null
                ? Collections.emptyList() : candidates;
        Candidate preferred = findByTrackId(safe, preferredTrackId);
        if (sticky) {
            return preferred == null
                    ? new Selection(null, 0f, "sticky_target_missing")
                    : new Selection(preferred, score(preferred), "sticky_target_kept");
        }

        Candidate best = null;
        float bestScore = -1f;
        for (Candidate candidate : safe) {
            if (candidate == null || candidate.trackId <= 0L) continue;
            float candidateScore = score(candidate);
            if (candidate.trackId == preferredTrackId) {
                candidateScore = Math.min(1f, candidateScore + PREFERRED_TRACK_BONUS);
            }
            if (candidateScore > bestScore
                    || candidateScore == bestScore
                    && best != null
                    && candidate.trackId < best.trackId) {
                best = candidate;
                bestScore = candidateScore;
            }
        }
        return best == null
                ? new Selection(null, 0f, "no_candidate")
                : new Selection(
                        best,
                        bestScore,
                        best.trackId == preferredTrackId
                                ? "preferred_candidate" : "ranked_candidate"
                );
    }

    public float score(Candidate candidate) {
        if (candidate == null || candidate.trackId <= 0L) return 0f;
        float dx = candidate.centerX - 0.5f;
        float dy = candidate.centerY - 0.5f;
        float maximumDistance = (float) Math.sqrt(0.5f);
        float centerScore = clamp01(
                1f - (float) Math.sqrt(dx * dx + dy * dy) / maximumDistance
        );
        // About 8% of the frame is already considered a large plate target.
        float areaScore = clamp01(candidate.areaRatio / 0.08f);
        float ageScore = clamp01(candidate.ageFrames / 5f);
        return clamp01(
                weights.center * centerScore
                        + weights.area * areaScore
                        + weights.detectionQuality * candidate.detectionQuality
                        + weights.sharpness * candidate.sharpness
                        + weights.trackAge * ageScore
        );
    }

    /**
     * Re-associates a locked physical target after an external tracker assigned
     * a new id. The weights follow the target-lock specification.
     */
    public Association associate(
            Reference reference,
            List<Candidate> candidates
    ) {
        if (reference == null) {
            return new Association(null, 0f, 0f, "missing_reference");
        }
        Candidate best = null;
        float bestScore = -1f;
        float secondScore = -1f;
        for (Candidate candidate : candidates == null
                ? Collections.<Candidate>emptyList() : candidates) {
            if (candidate == null || candidate.trackId <= 0L) continue;
            float candidateScore = associationScore(reference, candidate);
            if (candidateScore < 0f) continue;
            if (candidateScore > bestScore) {
                secondScore = bestScore;
                bestScore = candidateScore;
                best = candidate;
            } else if (candidateScore > secondScore) {
                secondScore = candidateScore;
            }
        }
        float safeBest = Math.max(0f, bestScore);
        float safeSecond = Math.max(0f, secondScore);
        if (best == null || safeBest < MINIMUM_ASSOCIATION_SCORE) {
            return new Association(null, safeBest, safeSecond, "association_too_weak");
        }
        if (safeBest - safeSecond < MINIMUM_ASSOCIATION_MARGIN) {
            return new Association(null, safeBest, safeSecond, "association_ambiguous");
        }
        return new Association(best, safeBest, safeSecond, "appearance_geometry_match");
    }

    private static float associationScore(Reference reference, Candidate candidate) {
        float referenceWidth = Math.max(0.001f, reference.right - reference.left);
        float referenceHeight = Math.max(0.001f, reference.bottom - reference.top);
        float candidateWidth = Math.max(0.001f, candidate.right - candidate.left);
        float candidateHeight = Math.max(0.001f, candidate.bottom - candidate.top);
        float referenceCenterX = (reference.left + reference.right) * 0.5f;
        float referenceCenterY = (reference.top + reference.bottom) * 0.5f;
        float distance = (float) Math.hypot(
                candidate.centerX - referenceCenterX,
                candidate.centerY - referenceCenterY
        );
        float referenceDiagonal = (float) Math.hypot(referenceWidth, referenceHeight);
        float candidateDiagonal = (float) Math.hypot(candidateWidth, candidateHeight);
        float gate = Math.max(0.12f, 2.5f * Math.max(referenceDiagonal, candidateDiagonal));
        if (distance > gate) return -1f;

        float overlap = iou(
                reference.left, reference.top, reference.right, reference.bottom,
                candidate.left, candidate.top, candidate.right, candidate.bottom
        );
        float centerSimilarity = clamp01(1f - distance / Math.max(0.001f, gate));
        float referenceArea = referenceWidth * referenceHeight;
        float candidateArea = candidateWidth * candidateHeight;
        float scaleSimilarity = (float) Math.exp(
                -Math.abs(Math.log(candidateArea / referenceArea))
        );
        float appearanceSimilarity = normalizedCosine(
                reference.appearanceDescriptor,
                candidate.appearanceDescriptor
        );
        if (reference.appearanceDescriptor != null
                && candidate.appearanceDescriptor != null
                && appearanceSimilarity < MINIMUM_APPEARANCE_SIMILARITY) {
            return -1f;
        }
        return clamp01(
                0.45f * overlap
                        + 0.25f * centerSimilarity
                        + 0.15f * scaleSimilarity
                        + 0.15f * appearanceSimilarity
        );
    }

    private static float normalizedCosine(float[] first, float[] second) {
        if (first == null || second == null || first.length != second.length) return 0.5f;
        float dot = 0f;
        float firstEnergy = 0f;
        float secondEnergy = 0f;
        for (int index = 0; index < first.length; index++) {
            dot += first[index] * second[index];
            firstEnergy += first[index] * first[index];
            secondEnergy += second[index] * second[index];
        }
        float denominator = (float) Math.sqrt(firstEnergy * secondEnergy);
        if (denominator <= 1e-6f) return 0.5f;
        float cosine = Math.max(-1f, Math.min(1f, dot / denominator));
        return clamp01((cosine + 1f) * 0.5f);
    }

    private static float iou(
            float firstLeft,
            float firstTop,
            float firstRight,
            float firstBottom,
            float secondLeft,
            float secondTop,
            float secondRight,
            float secondBottom
    ) {
        float intersection = Math.max(
                0f,
                Math.min(firstRight, secondRight) - Math.max(firstLeft, secondLeft)
        ) * Math.max(
                0f,
                Math.min(firstBottom, secondBottom) - Math.max(firstTop, secondTop)
        );
        float firstArea = Math.max(0f, firstRight - firstLeft)
                * Math.max(0f, firstBottom - firstTop);
        float secondArea = Math.max(0f, secondRight - secondLeft)
                * Math.max(0f, secondBottom - secondTop);
        float union = firstArea + secondArea - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    private static Candidate findByTrackId(List<Candidate> candidates, long trackId) {
        if (trackId <= 0L) return null;
        for (Candidate candidate : candidates) {
            if (candidate != null && candidate.trackId == trackId) return candidate;
        }
        return null;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
