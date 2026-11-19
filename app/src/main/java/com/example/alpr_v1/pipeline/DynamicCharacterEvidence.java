package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.continuity.ContinuityStamp;
import com.example.alpr_v1.domain.RegistrationTextPolicy;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.ReadingOrderResolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded character evidence for one ordinary-dynamic physical owner. Not a calibrated probability. */
final class DynamicCharacterEvidence {
    static final int WINDOW = 16;
    private static final double MIN_SUPPORT = .75;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private long lastSequence, lastFrame, lastTime = Long.MIN_VALUE;

    private static final class Sample {
        final List<Integer> rows;
        final List<String> symbols;
        final List<Double> confidence;
        final double quality;
        Sample(List<Integer> rows, List<String> symbols, List<Double> confidence, double quality) {
            this.rows = rows; this.symbols = symbols; this.confidence = confidence; this.quality = quality;
        }
        double weight() {
            double sum = 0;
            for (double value : confidence) sum += value;
            return quality * sum / confidence.size();
        }
    }

    boolean isFresh(ContinuityStamp source, long frameId) {
        return source != null && source.sourceTimestampNanos > lastTime
                && (source.sourceSequence > 0 ? source.sourceSequence > lastSequence
                : frameId > lastFrame);
    }

    TemporalCharacterAggregator.Result accept(ContinuityStamp source, long frameId, float quality,
            List<Detection> characters, List<String> labels) {
        if (!isFresh(source, frameId)) return current();
        lastTime = source.sourceTimestampNanos; lastFrame = frameId;
        lastSequence = source.sourceSequence;
        List<Detection> valid = new ArrayList<>();
        for (Detection character : characters) {
            if (character != null && character.classId >= 0 && character.classId < labels.size()
                    && labels.get(character.classId) != null && !labels.get(character.classId).trim().isEmpty())
                valid.add(character);
        }
        List<Integer> rows = new ArrayList<>();
        List<String> symbols = new ArrayList<>();
        List<Double> confidence = new ArrayList<>();
        for (List<Detection> row : ReadingOrderResolver.rows(valid)) {
            if (row.isEmpty()) continue;
            rows.add(row.size());
            for (Detection character : row) {
                symbols.add(labels.get(character.classId).trim().toUpperCase(Locale.ROOT));
                confidence.add(Float.isFinite(character.confidence)?(double)Math.max(0f,Math.min(1f,character.confidence)):0.0);
            }
        }
        // An empty execution is an attempted observation, not an invented vote or a new identity.
        if (!symbols.isEmpty()) {
            samples.addLast(new Sample(rows,symbols,confidence,Float.isFinite(quality)?Math.max(0,Math.min(1,quality)):0));
            while (samples.size() > WINDOW) samples.removeFirst();
        }
        return current();
    }

    TemporalCharacterAggregator.Result current() {
        if (samples.isEmpty()) return null;
        Map<List<Integer>, List<Sample>> structures = new LinkedHashMap<>();
        double totalWeight = 0;
        for (Sample sample : samples) {
            structures.computeIfAbsent(sample.rows, ignored -> new ArrayList<>()).add(sample);
            totalWeight += sample.weight();
        }
        List<Sample> dominant = null;
        double dominantWeight = -1;
        boolean dominantPlausible = false;
        for (List<Sample> group : structures.values()) {
            boolean plausible = RegistrationTextPolicy.plausibleLength(group.get(0).symbols.size());
            double weight = 0;
            for (Sample sample : group) weight += sample.weight();
            if (dominant == null || plausible && !dominantPlausible
                    || plausible == dominantPlausible && weight > dominantWeight) {
                dominant = group; dominantWeight = weight; dominantPlausible = plausible;
            }
        }
        double structureSupport = totalWeight > 0 ? dominantWeight / totalWeight : 0;
        boolean stable = dominantPlausible && dominant.size() >= 2 && structureSupport >= MIN_SUPPORT;
        double minimum = 1;
        StringBuilder text = new StringBuilder();
        for (int position = 0; position < dominant.get(0).symbols.size(); position++) {
            Map<String, double[]> votes = new LinkedHashMap<>();
            double total = 0;
            for (Sample sample : dominant) {
                double weight = sample.quality * sample.confidence.get(position);
                double[] vote = votes.computeIfAbsent(sample.symbols.get(position), ignored -> new double[3]);
                vote[0] += weight; vote[1] += sample.quality; vote[2]++;
                total += weight;
            }
            String winner = ""; double[] best = null;
            for (Map.Entry<String, double[]> vote : votes.entrySet()) {
                if (best == null || vote.getValue()[0] > best[0]) { winner = vote.getKey(); best = vote.getValue(); }
            }
            double support = total > 0 ? best[0] / total : 0;
            double meanConfidence = best[1] > 0 ? best[0] / best[1] : 0;
            text.append(winner);
            minimum = Math.min(minimum, support * meanConfidence);
            stable &= best[2] >= 2 && support >= MIN_SUPPORT;
        }
        List<Integer> rows = dominant.get(0).rows;
        String layout = rows.size() == 1 ? TemporalCharacterAggregator.LAYOUT_SINGLE_ROW
                : rows.size() == 2 ? TemporalCharacterAggregator.LAYOUT_TWO_ROW : TemporalCharacterAggregator.LAYOUT_MULTI_ROW;
        double evidenceCount = dominant.size() / (dominant.size() + 1.0);
        return new TemporalCharacterAggregator.Result(text.toString(), structureSupport * minimum * evidenceCount,
                dominant.size(), stable, layout, Collections.unmodifiableList(rows));
    }
}
