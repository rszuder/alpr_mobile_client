package com.example.alpr_v1.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stabilizuje niezależnie wszystkie numery obecne w kolejnych klatkach. */
public final class MultiRecognitionStabilizer {
    private static final class Candidate {
        int confirmations;
        double confidenceSum;
        int missedFrames;
    }

    private final int requiredConfirmations;
    private final int allowedMissedFrames;
    private final Map<String, Candidate> candidates = new LinkedHashMap<>();

    public MultiRecognitionStabilizer(int requiredConfirmations) {
        this(requiredConfirmations, 0);
    }

    public MultiRecognitionStabilizer(int requiredConfirmations, int allowedMissedFrames) {
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
        this.allowedMissedFrames = Math.max(0, allowedMissedFrames);
    }

    public synchronized List<RecognitionStabilizer.StableResult> accept(
            List<PlateRecognition> recognitions
    ) {
        Map<String, Double> current = new LinkedHashMap<>();
        for (PlateRecognition recognition : recognitions) {
            String text = normalize(recognition.text);
            if (text.isEmpty()) continue;
            Double previous = current.get(text);
            if (previous == null || recognition.confidence > previous) {
                current.put(text, recognition.confidence);
            }
        }

        for (Map.Entry<String, Double> entry : current.entrySet()) {
            Candidate candidate = candidates.get(entry.getKey());
            if (candidate == null) {
                candidate = new Candidate();
                candidates.put(entry.getKey(), candidate);
            }
            candidate.confirmations++;
            candidate.confidenceSum += entry.getValue();
            candidate.missedFrames = 0;
        }

        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Candidate> entry : candidates.entrySet()) {
            if (current.containsKey(entry.getKey())) continue;
            Candidate candidate = entry.getValue();
            candidate.missedFrames++;
            if (candidate.missedFrames > allowedMissedFrames) expired.add(entry.getKey());
        }
        for (String key : expired) candidates.remove(key);

        List<RecognitionStabilizer.StableResult> stable = new ArrayList<>();
        for (Map.Entry<String, Candidate> entry : candidates.entrySet()) {
            Candidate candidate = entry.getValue();
            // Podtrzymanie służy wyłącznie do zamaskowania pustego wyniku MZ.
            // Gdy pojawił się inny odczyt, nie pokazujemy obu numerów naraz.
            boolean visible = current.containsKey(entry.getKey()) || current.isEmpty();
            if (visible && candidate.confirmations >= requiredConfirmations) {
                stable.add(new RecognitionStabilizer.StableResult(
                        entry.getKey(),
                        candidate.confidenceSum / candidate.confirmations,
                        candidate.confirmations
                ));
            }
        }
        return stable;
    }

    public synchronized void reset() {
        candidates.clear();
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }
}
