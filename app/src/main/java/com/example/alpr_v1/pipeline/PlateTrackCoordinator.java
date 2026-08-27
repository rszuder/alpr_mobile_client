package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.tracking.MotionBoxTracker;
import com.example.alpr_v1.vision.Detection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Łączy tracki tablic, budżet wywołań MZ i temporalny konsensus znaków. */
final class PlateTrackCoordinator {
    static final long MZ_STATE_TTL_NANOS = 2_500_000_000L;

    enum MtStateEvent {
        NO_MT_RUN,
        MT_RUN_WITH_DETECTIONS,
        MT_RUN_WITHOUT_DETECTIONS,
        TARGET_LOST,
        SCENE_RESET
    }
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
        final int mzAttemptIndex;


        private Decision(
                long trackId,
                int sourceIndex,
                boolean recognize,
                int expectedCharacterCount,
                List<Integer> expectedRowCounts,
                String expectedLayout,
                TemporalCharacterAggregator.Result currentResult,
                int mzAttemptIndex
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
            this.mzAttemptIndex = Math.max(0, mzAttemptIndex);
        }
    }

    private static final class State {
        final TemporalCharacterAggregator aggregator = new TemporalCharacterAggregator();
        int attempts;
        float bestAttemptQuality;
        long lastAttemptFrame = Long.MIN_VALUE;
        boolean zoomRetryPending;
        long lastSeenNanos;
        long missingSinceNanos;
    }

    private final MotionBoxTracker tracker = new MotionBoxTracker();
    private final Map<Long, State> states = new HashMap<>();
    private RecognitionProfile profile = RecognitionProfile.BALANCED;
    private boolean zoomRetryPendingForNewTracks;

    PlateTrackCoordinator() {}

    PlateTrackCoordinator(RecognitionProfile profile) {
        this.profile = profile == null ? RecognitionProfile.BALANCED : profile;
    }

    synchronized List<Decision> update(
            List<Observation> observations,
            long frameId,
            long nowNanos
    ) {
        onMtEvent(
                observations == null || observations.isEmpty()
                        ? MtStateEvent.MT_RUN_WITHOUT_DETECTIONS
                        : MtStateEvent.MT_RUN_WITH_DETECTIONS,
                nowNanos
        );
        List<MotionBoxTracker.Observation> boxes = new ArrayList<>();
        Map<Integer, Observation> byIndex = new HashMap<>();
        for (Observation observation : observations) {
            boxes.add(new MotionBoxTracker.Observation(
                    observation.box, "", observation.sourceIndex
            ));
            byIndex.put(observation.sourceIndex, observation);
        }
        List<MotionBoxTracker.Result> tracked = tracker.update(boxes, nowNanos, nowNanos);
        List<Decision> decisions = new ArrayList<>();
        for (MotionBoxTracker.Result track : tracked) {
            if (track.sourceIndex < 0) continue;
            Observation observation = byIndex.get(track.sourceIndex);
            if (observation == null) continue;
            State state = states.get(track.trackId);
            if (state == null) {
                state = new State();
                state.zoomRetryPending = zoomRetryPendingForNewTracks;
                states.put(track.trackId, state);
            }
            state.lastSeenNanos = nowNanos;
            state.missingSinceNanos = 0L;
            TemporalCharacterAggregator.Result current =
                    state.aggregator.current();

            boolean recognize = shouldRecognize(state, observation, frameId);
            decisions.add(
                    new Decision(
                            track.trackId,
                            track.sourceIndex,
                            recognize,
                            state.aggregator.expectedCount(),
                            state.aggregator.expectedRowCounts(),
                            state.aggregator.expectedLayout(),
                            current,
                            recognize ? state.attempts + 1 : state.attempts
                    )
            );
        }
        expireMissingStates(nowNanos);
        if (!decisions.isEmpty()) {
            zoomRetryPendingForNewTracks = false;
        }
        return decisions;
    }

    synchronized void onMtEvent(MtStateEvent event, long nowNanos) {
        MtStateEvent safeEvent = event == null ? MtStateEvent.NO_MT_RUN : event;
        if (safeEvent == MtStateEvent.SCENE_RESET) {
            reset();
            return;
        }
        if (safeEvent == MtStateEvent.NO_MT_RUN
                || safeEvent == MtStateEvent.MT_RUN_WITH_DETECTIONS) {
            expireMissingStates(nowNanos);
            return;
        }
        for (State state : states.values()) {
            if (state.missingSinceNanos <= 0L) {
                state.missingSinceNanos = nowNanos;
            }
        }
        expireMissingStates(nowNanos);
    }

    synchronized int retainedStateCount() {
        return states.size();
    }

    synchronized TemporalCharacterAggregator.Result recordRecognition(
            long trackId,
            float quality,
            long frameId,
            List<Detection> characters,
            List<String> labels
    ) {
        State state = states.computeIfAbsent(trackId, ignored -> new State());
        state.zoomRetryPending = false;
        state.attempts++;
        state.bestAttemptQuality = Math.max(state.bestAttemptQuality, quality);
        state.lastAttemptFrame = frameId;
        return state.aggregator.accept(characters, labels);
    }

    synchronized void recordFailedAttempt(long trackId, float quality, long frameId) {
        State state = states.computeIfAbsent(trackId, ignored -> new State());
        state.zoomRetryPending = false;
        state.attempts++;
        state.bestAttemptQuality = Math.max(state.bestAttemptQuality, quality);
        state.lastAttemptFrame = frameId;
    }

    synchronized void reset() {
        tracker.reset();
        states.clear();
        zoomRetryPendingForNewTracks = false;
    }

    private void expireMissingStates(long nowNanos) {
        Iterator<Map.Entry<Long, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next().getValue();
            if (state.missingSinceNanos > 0L
                    && nowNanos - state.missingSinceNanos > MZ_STATE_TTL_NANOS) {
                iterator.remove();
            }
        }
    }

    synchronized void applyCameraZoomTransform(float relativeRatio) {
        tracker.applyCenteredZoom(relativeRatio);
    }

    /**
     * Zoom-in został wykonany właśnie po to, aby uzyskać nowy crop dla MZ.
     * Zachowujemy track i konsensus znaków, ale każdemu aktywnemu trackowi
     * przyznajemy jedną świeżą próbę przy pierwszej poprawnej geometrii MT.
     */
    synchronized void requestFreshRecognitionAfterZoom() {
        zoomRetryPendingForNewTracks = true;
        for (State state : states.values()) {
            state.zoomRetryPending = true;
        }
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
            long frameId
    ) {

        /*
         * Nie uruchamiamy MZ dla geometrii,
         * której nie można bezpiecznie zrektyfikować,
         * ani dla bardzo słabego obrazu.
         */
        if (!observation.validGeometry) {

            return false;
        }

        /*
         * Jednorazowa próba po zoomie omija zwykły próg jakości i odstęp
         * schedulera. Nadal wymagamy czterech bezpiecznych narożników, ponieważ
         * bez nich nie można poprawnie zrektyfikować cropa dla MZ.
         */
        if (state.zoomRetryPending) {
            return true;
        }

        if (observation.quality < profile.minimumQuality) {
            return false;
        }


        /*
         * Pierwsza poprawna obserwacja tracku
         * zawsze dostaje próbę MZ.
         */
        if (state.attempts == 0) {

            return true;
        }


        long framesSinceAttempt =
                frameId - state.lastAttemptFrame;


        /*
         * Jeżeli pojawiła się wyraźnie lepsza obserwacja
         * tablicy, warto wykorzystać ją od razu.
         *
         * Dotyczy to również wyniku, który temporalnie
         * został już uznany za stabilny.
         */
        boolean qualityImproved =
                observation.quality
                        >= state.bestAttemptQuality
                        + profile.qualityImprovement;


        if (qualityImproved) {

            return true;
        }


        /*
         * Początkowy burst.
         *
         * Stable NIE kończy już działania MZ.
         *
         * Dzięki temu dwa zgodne, ale błędne odczyty
         * nie zamrażają wyniku na resztę życia tracku.
         */
        if (state.attempts < profile.burstAttempts) {

            /*
             * Po pierwszej próbie jesteśmy nieco bardziej
             * liberalni, aby szybko zebrać materiał
             * do konsensusu.
             */
            if (state.attempts == 1) {

                return observation.quality
                        >= state.bestAttemptQuality - 0.10f
                        || framesSinceAttempt
                        >= profile.retryGapFrames;
            }


            return framesSinceAttempt
                    >= profile.retryGapFrames;
        }


        /*
         * Po wykorzystaniu burstu nadal okresowo
         * sprawdzamy nieruchomą tablicę.
         *
         * Nowa, ostrzejsza klatka albo nieco lepsze
         * położenie keypointów MT może dzięki temu
         * poprawić wcześniejszy konsensus.
         */
        return framesSinceAttempt
                >= profile.periodicRetryGapFrames;
    }}
