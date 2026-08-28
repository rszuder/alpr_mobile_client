package com.example.alpr_v1.camera;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Steruje pojedynczą adaptacyjną próbą zoomu dla stabilnego tracku tablicy. */
public final class AutoZoomController {
    public static final float REQUESTED_ZOOM_RATIO = 1.8f;
    public static final long SETTLING_MILLIS = 450L;

    private static final int MINIMUM_TRACK_OBSERVATIONS = 2;
    private static final float SMALL_PLATE_WIDTH = 0.24f;
    private static final double LOW_RECOGNITION_CONFIDENCE = 0.70;
    private static final double REQUIRED_CONFIDENCE_IMPROVEMENT = 0.10;
    private static final double STRONG_RECOGNITION_CONFIDENCE = 0.72;
    private static final double STRONG_CONFIDENCE_IMPROVEMENT = 0.15;
    private static final int REQUIRED_CONSISTENT_IMPROVEMENTS = 2;
    private static final long MINIMUM_ZOOMED_NANOS = 500_000_000L;
    private static final long MAXIMUM_ZOOMED_NANOS = 9_000_000_000L;

    public enum State {
        DISABLED,
        READY,
        ZOOM_SETTLING,
        ZOOMED_RETRY,
        RETURNING
    }

    public enum Action {
        NONE,
        REQUEST_ZOOM,
        RETURN_NORMAL
    }

    public static final class Sample {
        public final long trackId;
        public final float centerX;
        public final float centerY;
        public final float normalizedWidth;
        public final double recognitionConfidence;
        public final boolean confirmed;
        public final int observations;
        public final boolean validQuad;
        public final boolean recognitionExecuted;
        public final boolean recognitionSuccessful;
        public final String text;
        public final boolean stableTargetGeometry;

        public Sample(
                long trackId,
                float centerX,
                float centerY,
                float normalizedWidth,
                double recognitionConfidence,
                boolean confirmed,
                int observations,
                boolean validQuad,
                boolean recognitionExecuted,
                boolean recognitionSuccessful,
                String text
        ) {
            this(
                    trackId,
                    centerX,
                    centerY,
                    normalizedWidth,
                    recognitionConfidence,
                    confirmed,
                    observations,
                    validQuad,
                    recognitionExecuted,
                    recognitionSuccessful,
                    text,
                    false
            );
        }

        public Sample(
                long trackId,
                float centerX,
                float centerY,
                float normalizedWidth,
                double recognitionConfidence,
                boolean confirmed,
                int observations,
                boolean validQuad,
                boolean recognitionExecuted,
                boolean recognitionSuccessful,
                String text,
                boolean stableTargetGeometry
        ) {
            this.trackId = trackId;
            this.centerX = clamp(centerX);
            this.centerY = clamp(centerY);
            this.normalizedWidth = Math.max(0f, normalizedWidth);
            this.recognitionConfidence = normalizeConfidence(recognitionConfidence);
            this.confirmed = confirmed;
            this.observations = Math.max(0, observations);
            this.validQuad = validQuad;
            this.recognitionExecuted = recognitionExecuted;
            this.recognitionSuccessful = recognitionExecuted && recognitionSuccessful;
            this.text = normalizeText(text);
            this.stableTargetGeometry = stableTargetGeometry;
        }
    }

    public static final class Decision {
        public final Action action;
        public final long trackId;
        public final float centerX;
        public final float centerY;
        public final double beforeConfidence;
        public final double afterConfidence;
        public final String reason;

        private Decision(
                Action action,
                long trackId,
                float centerX,
                float centerY,
                double beforeConfidence,
                double afterConfidence,
                String reason
        ) {
            this.action = action;
            this.trackId = trackId;
            this.centerX = centerX;
            this.centerY = centerY;
            this.beforeConfidence = beforeConfidence;
            this.afterConfidence = afterConfidence;
            this.reason = reason == null ? "" : reason;
        }

        public static Decision none() {
            return new Decision(Action.NONE, 0L, 0.5f, 0.5f, 0.0, 0.0, "");
        }
    }

    private final Set<Long> attemptedTrackIds = new HashSet<>();
    private final Set<String> attemptedPlateTexts = new HashSet<>();
    private final Set<String> attemptedPlateRegions = new HashSet<>();
    private boolean featureEnabled;
    private State state = State.DISABLED;
    private long targetTrackId;
    private float targetCenterX = 0.5f;
    private float targetCenterY = 0.5f;
    private String targetText = "";
    private String beforeText = "";
    private String lastImprovedText = "";
    private int consistentImprovementCount;
    private double beforeConfidence;
    private double bestAfterConfidence;
    private long zoomedSinceNanos;

    public synchronized void setEnabled(boolean enabled) {
        featureEnabled = enabled;
        if (!enabled) {
            state = State.DISABLED;
            clearTarget();
        } else if (state == State.DISABLED) {
            state = State.READY;
        }
    }

    public synchronized boolean enabled() {
        return featureEnabled;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized long targetTrackId() {
        return targetTrackId;
    }

    /** Tekst celu zapamiętany jeszcze przed rozpoczęciem zoomu. */
    public synchronized String targetText() {
        return targetText;
    }

    public synchronized void resetSession() {
        attemptedTrackIds.clear();
        attemptedPlateTexts.clear();
        attemptedPlateRegions.clear();
        clearTarget();
        state = featureEnabled ? State.READY : State.DISABLED;
    }

    public synchronized Decision evaluate(List<Sample> samples, long nowNanos) {
        if (state == State.READY) {
            Sample candidate = chooseCandidate(samples);
            if (candidate == null) return Decision.none();

            attemptedTrackIds.add(candidate.trackId);
            if (!candidate.text.isEmpty()) attemptedPlateTexts.add(candidate.text);
            attemptedPlateRegions.add(regionKey(candidate));
            targetTrackId = candidate.trackId;
            targetCenterX = candidate.centerX;
            targetCenterY = candidate.centerY;
            targetText = candidate.text;
            beforeText = candidate.text;
            beforeConfidence = candidate.recognitionConfidence;
            bestAfterConfidence = beforeConfidence;
            state = State.ZOOM_SETTLING;
            String reason = candidate.recognitionExecuted
                    && !candidate.recognitionSuccessful
                    ? "mz_no_detection"
                    : candidate.normalizedWidth < SMALL_PLATE_WIDTH
                    ? "small_plate"
                    : "low_confidence";
            return new Decision(
                    Action.REQUEST_ZOOM,
                    targetTrackId,
                    targetCenterX,
                    targetCenterY,
                    beforeConfidence,
                    beforeConfidence,
                    reason
            );
        }

        if (state != State.ZOOMED_RETRY) return Decision.none();

        Sample target = findTarget(samples);
        if (target != null) {
            targetTrackId = target.trackId;
            targetCenterX = target.centerX;
            targetCenterY = target.centerY;
            if (!target.text.isEmpty()) targetText = target.text;
        }
        boolean minimumHoldElapsed =
                nowNanos - zoomedSinceNanos >= MINIMUM_ZOOMED_NANOS;
        boolean freshRecognition = target != null
                && target.recognitionExecuted
                && minimumHoldElapsed;
        boolean timedOut = nowNanos - zoomedSinceNanos >= MAXIMUM_ZOOMED_NANOS;
        boolean improved = freshRecognition
                && target.recognitionConfidence + 1e-9
                >= beforeConfidence + REQUIRED_CONFIDENCE_IMPROVEMENT;
        boolean confirmedImprovement = freshRecognition
                && target.confirmed
                && (!target.text.equals(beforeText)
                || target.recognitionConfidence + 1e-9 >= beforeConfidence + 0.05);

        if (freshRecognition) {
            bestAfterConfidence = Math.max(bestAfterConfidence, target.recognitionConfidence);
            if (improved && !target.text.isEmpty()) {
                if (target.text.equals(lastImprovedText)) {
                    consistentImprovementCount++;
                } else {
                    lastImprovedText = target.text;
                    consistentImprovementCount = 1;
                }
            }
        }

        boolean strongImprovement = improved
                && target.recognitionConfidence >= STRONG_RECOGNITION_CONFIDENCE
                && target.recognitionConfidence + 1e-9
                >= beforeConfidence + STRONG_CONFIDENCE_IMPROVEMENT;
        boolean consistentImprovement = improved
                && consistentImprovementCount >= REQUIRED_CONSISTENT_IMPROVEMENTS;

        if (!timedOut
                && !confirmedImprovement
                && !strongImprovement
                && !consistentImprovement) return Decision.none();

        double after = target == null
                ? bestAfterConfidence
                : Math.max(bestAfterConfidence, target.recognitionConfidence);
        state = State.RETURNING;
        return new Decision(
                Action.RETURN_NORMAL,
                targetTrackId,
                targetCenterX,
                targetCenterY,
                beforeConfidence,
                after,
                confirmedImprovement
                        ? "confirmed_improvement"
                        : strongImprovement
                        ? "strong_confidence_improvement"
                        : consistentImprovement
                        ? "consistent_confidence_improvement"
                        : "timeout"
        );
    }

    public synchronized void onZoomSettled(long nowNanos) {
        if (state != State.ZOOM_SETTLING) return;
        zoomedSinceNanos = nowNanos;
        state = State.ZOOMED_RETRY;
    }

    public synchronized void onZoomApplied(float centerX, float centerY) {
        targetCenterX = clamp(centerX);
        targetCenterY = clamp(centerY);
    }

    public synchronized boolean matchesTarget(Sample sample) {
        if (sample == null) return false;
        if (sample.trackId == targetTrackId) return true;
        if (!targetText.isEmpty() && targetText.equals(sample.text)) return true;
        double dx = sample.centerX - targetCenterX;
        double dy = sample.centerY - targetCenterY;
        return dx * dx + dy * dy <= 0.20 * 0.20;
    }

    /** Zwraca jedno najlepsze dopasowanie, z priorytetem trackId i tekstu. */
    public synchronized Sample targetSample(List<Sample> samples) {
        return findTarget(samples);
    }

    public synchronized void onRequestFailed() {
        if (state == State.ZOOM_SETTLING || state == State.RETURNING) {
            clearTarget();
            state = featureEnabled ? State.READY : State.DISABLED;
        }
    }

    public synchronized boolean requestReturn() {
        if (state != State.ZOOM_SETTLING && state != State.ZOOMED_RETRY) return false;
        state = State.RETURNING;
        return true;
    }

    public synchronized void onReturnSettled() {
        clearTarget();
        state = featureEnabled ? State.READY : State.DISABLED;
    }

    public synchronized String captureSource() {
        return state == State.ZOOMED_RETRY || state == State.RETURNING
                ? "auto_zoom_retry"
                : "normal";
    }

    private Sample chooseCandidate(List<Sample> samples) {
        Sample best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Sample sample : samples) {
            boolean failedFreshRecognition = sample.recognitionExecuted
                    && !sample.recognitionSuccessful;
            boolean lockedSingleObservationRescue = sample.stableTargetGeometry
                    && sample.recognitionExecuted
                    && sample.recognitionSuccessful
                    && sample.observations >= 1
                    && sample.normalizedWidth < SMALL_PLATE_WIDTH;
            if (sample.trackId <= 0L
                    || attemptedTrackIds.contains(sample.trackId)
                    || (!sample.text.isEmpty() && attemptedPlateTexts.contains(sample.text))
                    || attemptedPlateRegions.contains(regionKey(sample))
                    || (!failedFreshRecognition
                    && !lockedSingleObservationRescue
                    && sample.observations < MINIMUM_TRACK_OBSERVATIONS)
                    || !sample.validQuad) continue;

            boolean needsImprovement = failedFreshRecognition
                    || sample.normalizedWidth < SMALL_PLATE_WIDTH
                    || (!sample.confirmed
                    && sample.recognitionConfidence < LOW_RECOGNITION_CONFIDENCE);
            if (!needsImprovement) continue;

            double dx = sample.centerX - 0.5;
            double dy = sample.centerY - 0.5;
            double centerScore = 1.0 - Math.min(1.0, Math.sqrt(dx * dx + dy * dy) / 0.707);
            double sizeScore = Math.min(1.0, sample.normalizedWidth / SMALL_PLATE_WIDTH);
            double confidenceNeed = 1.0 - sample.recognitionConfidence;
            double score = 0.35 * centerScore + 0.35 * sizeScore + 0.30 * confidenceNeed;
            if (score > bestScore) {
                best = sample;
                bestScore = score;
            }
        }
        return best;
    }

    private Sample findTarget(List<Sample> samples) {
        for (Sample sample : samples) {
            if (sample.trackId == targetTrackId) return sample;
        }

        if (!targetText.isEmpty()) {
            Sample bestTextMatch = null;
            for (Sample sample : samples) {
                if (!targetText.equals(sample.text)) continue;
                if (bestTextMatch == null
                        || sample.recognitionConfidence
                        > bestTextMatch.recognitionConfidence) {
                    bestTextMatch = sample;
                }
            }
            if (bestTextMatch != null) return bestTextMatch;
        }

        Sample nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Sample sample : samples) {
            double dx = sample.centerX - targetCenterX;
            double dy = sample.centerY - targetCenterY;
            double distance = dx * dx + dy * dy;
            if (distance <= 0.20 * 0.20 && distance < nearestDistance) {
                nearest = sample;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void clearTarget() {
        targetTrackId = 0L;
        targetCenterX = 0.5f;
        targetCenterY = 0.5f;
        targetText = "";
        beforeText = "";
        lastImprovedText = "";
        consistentImprovementCount = 0;
        beforeConfidence = 0.0;
        bestAfterConfidence = 0.0;
        zoomedSinceNanos = 0L;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static double normalizeConfidence(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String normalizeText(String value) {
        if (value == null) return "";
        return value.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
    }

    private static String regionKey(Sample sample) {
        int xCell = Math.min(7, Math.max(0, (int) (sample.centerX * 8f)));
        int yCell = Math.min(7, Math.max(0, (int) (sample.centerY * 8f)));
        return xCell + ":" + yCell;
    }
}
