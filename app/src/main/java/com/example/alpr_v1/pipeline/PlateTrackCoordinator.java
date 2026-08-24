package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.tracking.MotionBoxTracker;
import com.example.alpr_v1.vision.Detection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Łączy tracki tablic, budżet wywołań MZ i temporalny konsensus znaków. */
final class PlateTrackCoordinator {
    static final class Observation {
        final int sourceIndex;
        final MotionBoxTracker.Box box;
        final float quality;
        final boolean validGeometry;

        Observation(
                int sourceIndex,
                MotionBoxTracker.Box box,
                float quality,
                boolean validGeometry
        ) {
            this.sourceIndex = sourceIndex;
            this.box = box;
            this.quality = quality;
            this.validGeometry = validGeometry;
        }
    }

    static final class Decision {

        final long trackId;

        final int sourceIndex;

        final boolean recognize;

        /*
         * Całkowita liczba znaków oczekiwana na podstawie
         * dominującego konsensusu czasowego.
         */
        final int expectedCharacterCount;

        /*
         * Struktura wierszy oczekiwana dla tracku.
         *
         * Przykłady:
         *
         * [7]
         * [3, 5]
         * [2, 3, 3]
         */
        final List<Integer> expectedRowCounts;

        /*
         * single_row / two_row / multi_row / unknown
         */
        final String expectedLayout;

        final TemporalCharacterAggregator.Result currentResult;


        private Decision(
                long trackId,
                int sourceIndex,
                boolean recognize,
                int expectedCharacterCount,
                List<Integer> expectedRowCounts,
                String expectedLayout,
                TemporalCharacterAggregator.Result currentResult
        ) {
            this.trackId =
                    trackId;

            this.sourceIndex =
                    sourceIndex;

            this.recognize =
                    recognize;

            this.expectedCharacterCount =
                    expectedCharacterCount;

            this.expectedRowCounts =
                    java.util.Collections.unmodifiableList(
                            new ArrayList<>(
                                    expectedRowCounts == null
                                            ? java.util.Collections.emptyList()
                                            : expectedRowCounts
                            )
                    );

            this.expectedLayout =
                    expectedLayout == null
                            ? TemporalCharacterAggregator.LAYOUT_UNKNOWN
                            : expectedLayout;

            this.currentResult =
                    currentResult;
        }
    }

    private static final class State {
        final TemporalCharacterAggregator aggregator = new TemporalCharacterAggregator();
        int attempts;
        float bestAttemptQuality;
        long lastAttemptFrame = Long.MIN_VALUE;
    }

    private final MotionBoxTracker tracker = new MotionBoxTracker();
    private final Map<Long, State> states = new HashMap<>();
    private RecognitionProfile profile = RecognitionProfile.BALANCED;

    PlateTrackCoordinator() {}

    PlateTrackCoordinator(RecognitionProfile profile) {
        this.profile = profile == null ? RecognitionProfile.BALANCED : profile;
    }

    synchronized List<Decision> update(
            List<Observation> observations,
            long frameId,
            long nowNanos
    ) {
        List<MotionBoxTracker.Observation> boxes = new ArrayList<>();
        Map<Integer, Observation> byIndex = new HashMap<>();
        for (Observation observation : observations) {
            boxes.add(new MotionBoxTracker.Observation(
                    observation.box, "", observation.sourceIndex
            ));
            byIndex.put(observation.sourceIndex, observation);
        }
        List<MotionBoxTracker.Result> tracked = tracker.update(boxes, nowNanos, nowNanos);
        Set<Long> activeIds = new HashSet<>();
        List<Decision> decisions = new ArrayList<>();
        for (MotionBoxTracker.Result track : tracked) {
            activeIds.add(track.trackId);
            if (track.sourceIndex < 0) continue;
            Observation observation = byIndex.get(track.sourceIndex);
            if (observation == null) continue;
            State state = states.computeIfAbsent(track.trackId, ignored -> new State());
            TemporalCharacterAggregator.Result current = state.aggregator.current();
            boolean stable = current != null && current.stable;
            boolean recognize = shouldRecognize(state, observation, frameId, stable);
            decisions.add(
                    new Decision(
                            track.trackId,
                            track.sourceIndex,
                            recognize,
                            state.aggregator.expectedCount(),
                            state.aggregator.expectedRowCounts(),
                            state.aggregator.expectedLayout(),
                            current
                    )
            );
        }
        Iterator<Map.Entry<Long, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!activeIds.contains(iterator.next().getKey())) iterator.remove();
        }
        return decisions;
    }

    synchronized TemporalCharacterAggregator.Result recordRecognition(
            long trackId,
            float quality,
            long frameId,
            List<Detection> characters,
            List<String> labels
    ) {
        State state = states.computeIfAbsent(trackId, ignored -> new State());
        state.attempts++;
        state.bestAttemptQuality = Math.max(state.bestAttemptQuality, quality);
        state.lastAttemptFrame = frameId;
        return state.aggregator.accept(characters, labels);
    }

    synchronized void recordFailedAttempt(long trackId, float quality, long frameId) {
        State state = states.computeIfAbsent(trackId, ignored -> new State());
        state.attempts++;
        state.bestAttemptQuality = Math.max(state.bestAttemptQuality, quality);
        state.lastAttemptFrame = frameId;
    }

    synchronized void reset() {
        tracker.reset();
        states.clear();
    }

    synchronized void setProfile(RecognitionProfile profile) {
        RecognitionProfile resolved = profile == null ? RecognitionProfile.BALANCED : profile;
        if (this.profile == resolved) return;
        this.profile = resolved;
        reset();
    }

    private boolean shouldRecognize(
            State state,
            Observation observation,
            long frameId,
            boolean stable
    ) {
        if (stable || !observation.validGeometry || observation.quality < profile.minimumQuality) {
            return false;
        }
        if (state.attempts >= profile.burstAttempts) {
            return frameId - state.lastAttemptFrame >= profile.periodicRetryGapFrames;
        }
        if (state.attempts == 0) return true;
        long framesSinceAttempt = frameId - state.lastAttemptFrame;
        if (state.attempts == 1) {
            return observation.quality >= state.bestAttemptQuality - 0.10f
                    || framesSinceAttempt >= profile.retryGapFrames;
        }
        return observation.quality >= state.bestAttemptQuality + profile.qualityImprovement
                || framesSinceAttempt >= profile.retryGapFrames;
    }
}
