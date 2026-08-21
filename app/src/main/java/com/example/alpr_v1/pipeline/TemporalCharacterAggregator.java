package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.vision.Detection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ważony konsensus klas znaków dla kolejnych wyników MZ tego samego tracku. */
public final class TemporalCharacterAggregator {
    public static final class Result {
        public final String text;
        public final double confidence;
        public final int observations;
        public final boolean stable;

        private Result(String text, double confidence, int observations, boolean stable) {
            this.text = text;
            this.confidence = confidence;
            this.observations = observations;
            this.stable = stable;
        }
    }

    private static final class Vote {
        int count;
        double confidenceSum;
    }

    private static final class LengthState {
        int observations;
        double sequenceConfidenceSum;
        final List<Map<String, Vote>> positions = new ArrayList<>();
    }

    private final Map<Integer, LengthState> states = new LinkedHashMap<>();

    public synchronized Result accept(List<Detection> characters, List<String> labels) {
        List<String> symbols = new ArrayList<>();
        List<Double> confidences = new ArrayList<>();
        for (Detection detection : characters) {
            if (detection.classId < 0 || detection.classId >= labels.size()) continue;
            String symbol = labels.get(detection.classId);
            if (symbol == null || symbol.trim().isEmpty()) continue;
            symbols.add(symbol.trim().toUpperCase(java.util.Locale.ROOT));
            confidences.add((double) Math.max(0f, Math.min(1f, detection.confidence)));
        }
        if (symbols.isEmpty()) return current();

        int length = symbols.size();
        LengthState state = states.computeIfAbsent(length, ignored -> new LengthState());
        while (state.positions.size() < length) state.positions.add(new HashMap<>());
        state.observations++;
        double sequenceConfidence = 0.0;
        for (int i = 0; i < length; i++) {
            String symbol = symbols.get(i);
            double confidence = confidences.get(i);
            Vote vote = state.positions.get(i).computeIfAbsent(symbol, ignored -> new Vote());
            vote.count++;
            vote.confidenceSum += confidence;
            sequenceConfidence += confidence;
        }
        state.sequenceConfidenceSum += sequenceConfidence / length;
        return current();
    }

    public synchronized Result current() {
        Map.Entry<Integer, LengthState> dominant = dominantState();
        if (dominant == null) return null;
        return buildResult(dominant.getKey(), dominant.getValue());
    }

    public synchronized int expectedCount() {
        Map.Entry<Integer, LengthState> dominant = dominantState();
        return dominant == null || dominant.getValue().observations < 2 ? 0 : dominant.getKey();
    }

    public synchronized void reset() {
        states.clear();
    }

    private Map.Entry<Integer, LengthState> dominantState() {
        Map.Entry<Integer, LengthState> best = null;
        for (Map.Entry<Integer, LengthState> entry : states.entrySet()) {
            if (best == null
                    || entry.getValue().observations > best.getValue().observations
                    || (entry.getValue().observations == best.getValue().observations
                    && entry.getValue().sequenceConfidenceSum
                    > best.getValue().sequenceConfidenceSum)) {
                best = entry;
            }
        }
        return best;
    }

    private static Result buildResult(int length, LengthState state) {
        StringBuilder text = new StringBuilder();
        double minimumConfidence = 1.0;
        boolean stable = state.observations >= 2;
        for (int i = 0; i < length; i++) {
            String bestSymbol = "";
            Vote bestVote = null;
            for (Map.Entry<String, Vote> entry : state.positions.get(i).entrySet()) {
                Vote vote = entry.getValue();
                if (bestVote == null
                        || vote.count > bestVote.count
                        || (vote.count == bestVote.count
                        && vote.confidenceSum > bestVote.confidenceSum)) {
                    bestSymbol = entry.getKey();
                    bestVote = vote;
                }
            }
            if (bestVote == null) {
                stable = false;
                continue;
            }
            text.append(bestSymbol);
            minimumConfidence = Math.min(
                    minimumConfidence,
                    bestVote.confidenceSum / Math.max(1, bestVote.count)
            );
            if (bestVote.count < 2) stable = false;
        }
        if (text.length() == 0) return null;
        return new Result(
                text.toString(),
                minimumConfidence,
                state.observations,
                stable
        );
    }
}
