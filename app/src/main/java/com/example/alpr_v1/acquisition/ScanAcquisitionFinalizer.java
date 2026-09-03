package com.example.alpr_v1.acquisition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Finalizuje wyniki Scan i tłumi duplikaty tekstu w granicach jednego runu. */
final class ScanAcquisitionFinalizer {
    private final List<AcquisitionRecord> records = new ArrayList<>();
    private final Map<String, AcquisitionRecord> firstUniqueByText =
            new LinkedHashMap<>();
    private long sequence;

    AcquisitionRecord finalizeAcquisition(
            long scanRunId,
            long sessionId,
            long entityId,
            long plateTrackId,
            String text,
            double confidence,
            int observations,
            long firstObservationRuntimeNanos,
            long sessionStartedRuntimeNanos,
            long finalizedRuntimeNanos,
            String bestCropId
    ) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Brak tekstu do finalizacji Scan");
        }
        AcquisitionRecord original = firstUniqueByText.get(normalized);
        boolean unique = original == null;
        String recordId = String.format(
                Locale.ROOT,
                "scan-%d-a%04d",
                Math.max(0L, scanRunId),
                ++sequence
        );
        AcquisitionRecord record = new AcquisitionRecord(
                recordId,
                scanRunId,
                sessionId,
                entityId,
                plateTrackId,
                text,
                normalized,
                confidence,
                observations,
                firstObservationRuntimeNanos,
                finalizedRuntimeNanos,
                Math.max(0L, finalizedRuntimeNanos - sessionStartedRuntimeNanos),
                bestCropId,
                unique,
                unique ? "" : original.recordId
        );
        records.add(record);
        if (unique) firstUniqueByText.put(normalized, record);
        return record;
    }

    List<AcquisitionRecord> records() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    int finalizedCount() {
        return records.size();
    }

    int uniqueSavedCount() {
        return firstUniqueByText.size();
    }

    int duplicateSuppressedCount() {
        return Math.max(0, finalizedCount() - uniqueSavedCount());
    }

    void reset() {
        records.clear();
        firstUniqueByText.clear();
        sequence = 0L;
    }

    static String normalize(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
    }
}
