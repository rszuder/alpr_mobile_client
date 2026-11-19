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
        final long entityId;

        Observation(
                int sourceIndex,
                MotionBoxTracker.Box box,
                float quality,
                boolean validGeometry
        ) {
            this(sourceIndex, box, quality, validGeometry, 0L);
        }

        Observation(
                int sourceIndex,
                MotionBoxTracker.Box box,
                float quality,
                boolean validGeometry,
                long entityId
        ) {
            this.sourceIndex = sourceIndex;
            this.box = box;
            this.quality = quality;
            this.validGeometry = validGeometry;
            this.entityId = Math.max(0L, entityId);
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
        final String schedulingReason;


        private Decision(
                long trackId,
                int sourceIndex,
                boolean recognize,
                int expectedCharacterCount,
                List<Integer> expectedRowCounts,
                String expectedLayout,
                TemporalCharacterAggregator.Result currentResult,
                int mzAttemptIndex,String schedulingReason
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
            this.schedulingReason=schedulingReason;
        }
    }

    private static final class State {
        final TemporalCharacterAggregator aggregator = new TemporalCharacterAggregator();
        final DynamicCharacterEvidence dynamicEvidence = new DynamicCharacterEvidence();
        final DynamicInferenceScheduler.Cadence cadence = new DynamicInferenceScheduler.Cadence();
        long lastAttemptSourceNanos = Long.MIN_VALUE;
        int emptyAttempts;
        String schedulingReason="LEGACY";
        int attempts;
        float bestAttemptQuality;
        long lastAttemptFrame = Long.MIN_VALUE;
        boolean zoomRetryPending;
        long lastSeenNanos;
        long missingSinceNanos;
    }

    private final MotionBoxTracker tracker = new MotionBoxTracker();
    private final Map<Long, State> states = new HashMap<>();
    /*
     * Scan przełącza MT pomiędzy pojazdami. Sam track tablicy jest wtedy
     * krótkotrwały, ale encja pojazdu pozostaje ta sama. Konsensus OCR i budżet
     * prób muszą więc należeć do encji, inaczej każda rotacja kolejki zaczyna
     * odczyt od zera.
     */
    private final Map<Long, State> entityStates = new HashMap<>();
    private final Map<Long, Long> identityEpochs = new HashMap<>();
    private final Map<Long, Long> unassignedIdentityEpochs = new HashMap<>();
    private final Map<Long, State> activeStateByTrack = new HashMap<>();
    private RecognitionProfile profile = RecognitionProfile.BALANCED;
    private boolean zoomRetryPendingForNewTracks;
    private boolean normalDynamicPolicy;
    private com.example.alpr_v1.continuity.ContinuityStamp dynamicSource;
    private final DynamicInferenceScheduler dynamicScheduler=new DynamicInferenceScheduler();
    DynamicInferenceScheduler dynamicScheduler(){return dynamicScheduler;}
    static final long DYNAMIC_RETRY_NANOS = 750_000_000L;
    static final long DYNAMIC_PERIODIC_NANOS = 3_000_000_000L;

    synchronized void beginSource(com.example.alpr_v1.continuity.ContinuityStamp source, boolean ordinaryDynamic) {
        if (normalDynamicPolicy != ordinaryDynamic) reset();
        normalDynamicPolicy = ordinaryDynamic;
        dynamicSource = source;
    }

    private TemporalCharacterAggregator.Result current(State state) {
        return normalDynamicPolicy ? state.dynamicEvidence.current() : state.aggregator.current();
    }

    PlateTrackCoordinator() {}

    PlateTrackCoordinator(RecognitionProfile profile) {
        this.profile = profile == null ? RecognitionProfile.BALANCED : profile;
    }

    synchronized List<Decision> update(
            List<Observation> observations,
            long frameId,
            long nowNanos
    ) {
        if(normalDynamicPolicy)activeStateByTrack.clear();
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
            State state = observation.entityId > 0L
                    ? entityStates.get(observation.entityId)
                    : states.get(track.trackId);
            if (state == null) {
                state = new State();
                state.zoomRetryPending = zoomRetryPendingForNewTracks;
                if (observation.entityId > 0L) {
                    entityStates.put(observation.entityId, state);
                } else {
                    states.put(track.trackId, state);
                }
            }
            activeStateByTrack.put(track.trackId, state);
            state.lastSeenNanos = nowNanos;
            state.missingSinceNanos = 0L;
            TemporalCharacterAggregator.Result current =
                    current(state);

            boolean recognize = shouldRecognize(state, observation, frameId);
            decisions.add(
                    new Decision(
                            track.trackId,
                            track.sourceIndex,
                            recognize,
                            normalDynamicPolicy ? 0 : state.aggregator.expectedCount(),
                            normalDynamicPolicy ? java.util.Collections.emptyList() : state.aggregator.expectedRowCounts(),
                            normalDynamicPolicy ? TemporalCharacterAggregator.LAYOUT_UNKNOWN : state.aggregator.expectedLayout(),
                            current,
                            recognize ? state.attempts + 1 : state.attempts,state.schedulingReason
                    )
            );
        }
        if(normalDynamicPolicy) {
            java.util.Set<Long> seen=new java.util.HashSet<>();
            for(Decision decision:decisions)seen.add(decision.trackId);
            for(Map.Entry<Long,State> entry:states.entrySet())if(!seen.contains(entry.getKey())&&entry.getValue().missingSinceNanos<=0L)
                entry.getValue().missingSinceNanos=nowNanos;
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
        return states.size() + entityStates.size();
    }

    synchronized TemporalCharacterAggregator.Result recordRecognition(
            long trackId,
            float quality,
            long frameId,
            List<Detection> characters,
            List<String> labels
    ) {
        State state = activeStateByTrack.get(trackId);
        if (state == null) state = states.computeIfAbsent(trackId, ignored -> new State());
        if (normalDynamicPolicy && !state.dynamicEvidence.isFresh(dynamicSource, frameId)) return current(state);
        state.zoomRetryPending = false;
        state.attempts++;
        state.bestAttemptQuality = Math.max(state.bestAttemptQuality, quality);
        state.lastAttemptFrame = frameId;
        if (normalDynamicPolicy) {
            state.cadence.attempted();
            state.lastAttemptSourceNanos = dynamicSource.sourceTimestampNanos;
            state.emptyAttempts = characters.isEmpty() ? state.emptyAttempts + 1 : 0;
            return state.dynamicEvidence.accept(dynamicSource, frameId, quality, characters, labels);
        }
        return state.aggregator.accept(characters, labels);
    }

    /**
     * Po walidacji skojarzenia tablicy z pojazdem przełącza zapis OCR na
     * pamięć tej encji. Jest to konieczne dla pełnoklatkowego fallbacku, gdzie
     * jedna inferencja MT może zwrócić tablice kilku różnych pojazdów.
     */
    synchronized void bindEntityState(long trackId, long entityId) {
        if (trackId <= 0L || entityId <= 0L) return;
        State entityState = entityStates.computeIfAbsent(
                entityId,
                ignored -> new State()
        );
        activeStateByTrack.put(trackId, entityState);
    }

    synchronized void bindEntityState(long trackId,long entityId,long epoch) {
        unassignedIdentityEpochs.remove(trackId);
        Long previous=identityEpochs.put(entityId,epoch);
        if(previous==null||previous!=epoch&&!normalDynamicPolicy)entityStates.put(entityId,new State());
        bindEntityState(trackId,entityId);
    }

    synchronized void bindUnassignedIdentity(long trackId,long epoch) {
        Long previous=unassignedIdentityEpochs.put(trackId,epoch);
        if(previous==null||previous!=epoch)states.put(trackId,new State());
        activeStateByTrack.put(trackId,states.get(trackId));
    }

    synchronized Decision identityDecision(Decision original,Observation observation,long frameId) {
        State state=activeStateByTrack.get(original.trackId);
        if(state==null)return original;
        boolean recognize=shouldRecognize(state,observation,frameId);
        return new Decision(original.trackId,original.sourceIndex,recognize,normalDynamicPolicy?0:state.aggregator.expectedCount(),
                normalDynamicPolicy?java.util.Collections.emptyList():state.aggregator.expectedRowCounts(),
                normalDynamicPolicy?TemporalCharacterAggregator.LAYOUT_UNKNOWN:state.aggregator.expectedLayout(),current(state),
                recognize?state.attempts+1:state.attempts,state.schedulingReason);
    }
    static Decision deferred(Decision original) {
        return new Decision(original.trackId,original.sourceIndex,false,original.expectedCharacterCount,
                original.expectedRowCounts,original.expectedLayout,original.currentResult,
                original.mzAttemptIndex,original.schedulingReason);
    }

    synchronized void promoteVerifiedState(long candidate,long original,long plateTrackId,long epoch) {
        State fresh=entityStates.remove(candidate);
        identityEpochs.remove(candidate);identityEpochs.put(original,epoch);
        if(fresh!=null){entityStates.put(original,fresh);activeStateByTrack.put(plateTrackId,fresh);}
    }

    synchronized void recordFailedAttempt(long trackId, float quality, long frameId) {
        State state = activeStateByTrack.get(trackId);
        if (state == null) state = states.computeIfAbsent(trackId, ignored -> new State());
        state.zoomRetryPending = false;
        state.attempts++;
        state.bestAttemptQuality = Math.max(state.bestAttemptQuality, quality);
        state.lastAttemptFrame = frameId;
    }

    synchronized void reset() {
        dynamicScheduler.reset();
        tracker.reset();
        states.clear();
        entityStates.clear();
        identityEpochs.clear();
        unassignedIdentityEpochs.clear();
        activeStateByTrack.clear();
        zoomRetryPendingForNewTracks = false;
    }
    synchronized void resetTechnicalTracks() {
        tracker.reset();states.clear();activeStateByTrack.clear();unassignedIdentityEpochs.clear();
        zoomRetryPendingForNewTracks=false;
    }
    synchronized void retainEntityStates(java.util.Set<Long> ids) {
        entityStates.keySet().retainAll(ids);identityEpochs.keySet().retainAll(ids);
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
        if(normalDynamicPolicy)unassignedIdentityEpochs.keySet().retainAll(states.keySet());
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
        for (State state : entityStates.values()) {
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
            state.schedulingReason="INVALID_GEOMETRY";
            return false;
        }

        if (normalDynamicPolicy) {
            if (dynamicSource == null || !state.dynamicEvidence.isFresh(dynamicSource, frameId)) {
                state.schedulingReason="SOURCE_DUPLICATE";return false;
            }
            if (!Float.isFinite(observation.quality)||observation.quality < profile.minimumQuality) {
                state.schedulingReason="IMAGE_QUALITY";return false;
            }
            TemporalCharacterAggregator.Result evidence = current(state);
            boolean better=state.zoomRetryPending||observation.quality>=state.bestAttemptQuality+profile.qualityImprovement;
            boolean due=dynamicScheduler.due(state.cadence,frameId,state.attempts,
                    evidence!=null&&evidence.stable,state.emptyAttempts,better);
            state.schedulingReason=state.attempts==0?"FIRST_OBSERVATION":!due?"ADAPTIVE_INTERVAL":better?"BETTER_CROP":
                    state.emptyAttempts>=2?"EMPTY_BACKOFF":evidence!=null&&evidence.stable?"STABLE_CHECK":"UNCERTAIN_RETRY";
            return due;
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
