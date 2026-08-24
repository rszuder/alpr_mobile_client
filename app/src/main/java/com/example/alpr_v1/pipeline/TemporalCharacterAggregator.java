package com.example.alpr_v1.pipeline;

import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.ReadingOrderResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ważony konsensus klas znaków dla kolejnych wyników MZ
 * tego samego tracku.
 *
 * Stan konsensusu jest rozdzielany nie tylko według
 * całkowitej liczby znaków, ale również według struktury
 * wierszy tablicy.
 */
public final class TemporalCharacterAggregator {

    public static final String LAYOUT_UNKNOWN =
            "unknown";

    public static final String LAYOUT_SINGLE_ROW =
            "single_row";

    public static final String LAYOUT_TWO_ROW =
            "two_row";

    public static final String LAYOUT_MULTI_ROW =
            "multi_row";


    public static final class Result {

        public final String text;

        public final double confidence;

        public final int observations;

        public final boolean stable;

        /*
         * Struktura przestrzenna sekwencji.
         *
         * Przykłady:
         *
         * single_row -> [7]
         * two_row    -> [3, 5]
         */
        public final String layout;

        public final int rowCount;

        public final List<Integer> rowCounts;


        private Result(
                String text,
                double confidence,
                int observations,
                boolean stable,
                String layout,
                List<Integer> rowCounts
        ) {
            this.text =
                    text;

            this.confidence =
                    confidence;

            this.observations =
                    observations;

            this.stable =
                    stable;

            this.layout =
                    layout == null
                            ? LAYOUT_UNKNOWN
                            : layout;

            this.rowCounts =
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    rowCounts
                            )
                    );

            this.rowCount =
                    this.rowCounts.size();
        }
    }


    private static final class Vote {

        int count;

        double confidenceSum;
    }


    private static final class StructureState {

        int observations;

        double sequenceConfidenceSum;

        final List<Map<String, Vote>> positions =
                new ArrayList<>();

        final List<Integer> rowCounts;

        final String layout;


        StructureState(
                List<Integer> rowCounts
        ) {
            this.rowCounts =
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    rowCounts
                            )
                    );

            this.layout =
                    layoutName(
                            rowCounts
                    );
        }


        int totalLength() {
            int total = 0;

            for (Integer count :
                    rowCounts) {

                if (count != null) {
                    total += Math.max(
                            0,
                            count
                    );
                }
            }

            return total;
        }
    }


    /*
     * Kluczem nie jest już wyłącznie długość sekwencji.
     *
     * Przykłady kluczy:
     *
     * 1:7
     * 2:3,5
     * 2:4,4
     */
    private final Map<String, StructureState> states =
            new LinkedHashMap<>();


    public synchronized Result accept(
            List<Detection> characters,
            List<String> labels
    ) {
        /*
         * Najpierw odrzucamy detekcje, których nie możemy
         * zamienić na poprawny symbol.
         *
         * Dzięki temu struktura rowCounts opisuje dokładnie
         * znaki uczestniczące w konsensusie.
         */
        List<Detection> validCharacters =
                new ArrayList<>();


        for (Detection detection :
                characters) {

            if (detection.classId < 0
                    || detection.classId >= labels.size()) {

                continue;
            }


            String symbol =
                    labels.get(
                            detection.classId
                    );


            if (symbol == null
                    || symbol.trim().isEmpty()) {

                continue;
            }


            validCharacters.add(
                    detection
            );
        }


        if (validCharacters.isEmpty()) {
            return current();
        }


        /*
         * Rekonstruujemy strukturę przestrzenną.
         */
        List<List<Detection>> rows =
                ReadingOrderResolver.rows(
                        validCharacters
                );


        if (rows.isEmpty()) {
            return current();
        }


        List<Integer> rowCounts =
                new ArrayList<>();

        List<String> symbols =
                new ArrayList<>();

        List<Double> confidences =
                new ArrayList<>();


        /*
         * Flatten odbywa się w naturalnej kolejności:
         *
         * wiersze: góra -> dół
         * znaki:   lewo -> prawo
         */
        for (List<Detection> row :
                rows) {

            if (row.isEmpty()) {
                continue;
            }


            rowCounts.add(
                    row.size()
            );


            for (Detection detection :
                    row) {

                String symbol =
                        labels.get(
                                detection.classId
                        );


                symbols.add(
                        symbol
                                .trim()
                                .toUpperCase(
                                        java.util.Locale.ROOT
                                )
                );


                confidences.add(
                        (double) Math.max(
                                0f,
                                Math.min(
                                        1f,
                                        detection.confidence
                                )
                        )
                );
            }
        }


        if (symbols.isEmpty()
                || rowCounts.isEmpty()) {

            return current();
        }


        String structureKey =
                structureKey(
                        rowCounts
                );


        StructureState state =
                states.computeIfAbsent(
                        structureKey,
                        ignored ->
                                new StructureState(
                                        rowCounts
                                )
                );


        int length =
                symbols.size();


        while (state.positions.size()
                < length) {

            state.positions.add(
                    new HashMap<>()
            );
        }


        state.observations++;


        double sequenceConfidence =
                0.0;


        for (int i = 0;
             i < length;
             i++) {

            String symbol =
                    symbols.get(i);

            double confidence =
                    confidences.get(i);


            Vote vote =
                    state.positions
                            .get(i)
                            .computeIfAbsent(
                                    symbol,
                                    ignored ->
                                            new Vote()
                            );


            vote.count++;

            vote.confidenceSum +=
                    confidence;

            sequenceConfidence +=
                    confidence;
        }


        state.sequenceConfidenceSum +=
                sequenceConfidence
                        / length;


        return current();
    }


    public synchronized Result current() {

        Map.Entry<String, StructureState> dominant =
                dominantState();


        if (dominant == null) {
            return null;
        }


        return buildResult(
                dominant.getValue()
        );
    }


    /**
     * Oczekiwana całkowita liczba znaków.
     *
     * Zachowana dla kompatybilności z aktualnym
     * CharacterSequencePostProcessor.
     */
    public synchronized int expectedCount() {

        Map.Entry<String, StructureState> dominant =
                dominantState();


        if (dominant == null
                || dominant.getValue().observations < 2) {

            return 0;
        }


        return dominant
                .getValue()
                .totalLength();
    }


    /**
     * Oczekiwana struktura liczby znaków w wierszach.
     *
     * Przykład:
     *
     * [3, 5]
     */
    public synchronized List<Integer> expectedRowCounts() {

        Map.Entry<String, StructureState> dominant =
                dominantState();


        if (dominant == null
                || dominant.getValue().observations < 2) {

            return Collections.emptyList();
        }


        return Collections.unmodifiableList(
                new ArrayList<>(
                        dominant
                                .getValue()
                                .rowCounts
                )
        );
    }


    public synchronized String expectedLayout() {

        Map.Entry<String, StructureState> dominant =
                dominantState();


        if (dominant == null
                || dominant.getValue().observations < 2) {

            return LAYOUT_UNKNOWN;
        }


        return dominant
                .getValue()
                .layout;
    }


    public synchronized void reset() {
        states.clear();
    }


    private Map.Entry<String, StructureState>
    dominantState() {

        Map.Entry<String, StructureState> best =
                null;


        for (Map.Entry<String, StructureState> entry :
                states.entrySet()) {

            if (best == null
                    || entry.getValue().observations
                    > best.getValue().observations
                    || (
                    entry.getValue().observations
                            == best.getValue().observations
                            && entry.getValue().sequenceConfidenceSum
                            > best.getValue().sequenceConfidenceSum
            )) {

                best =
                        entry;
            }
        }


        return best;
    }


    private static Result buildResult(
            StructureState state
    ) {
        int length =
                state.totalLength();


        if (length <= 0
                || state.positions.size() < length) {

            return null;
        }


        StringBuilder text =
                new StringBuilder();


        double minimumConfidence =
                1.0;


        boolean stable =
                state.observations >= 2;


        for (int i = 0;
             i < length;
             i++) {

            String bestSymbol =
                    "";

            Vote bestVote =
                    null;


            for (Map.Entry<String, Vote> entry :
                    state.positions
                            .get(i)
                            .entrySet()) {

                Vote vote =
                        entry.getValue();


                if (bestVote == null
                        || vote.count
                        > bestVote.count
                        || (
                        vote.count
                                == bestVote.count
                                && vote.confidenceSum
                                > bestVote.confidenceSum
                )) {

                    bestSymbol =
                            entry.getKey();

                    bestVote =
                            vote;
                }
            }


            if (bestVote == null) {

                stable =
                        false;

                continue;
            }


            text.append(
                    bestSymbol
            );


            minimumConfidence =
                    Math.min(
                            minimumConfidence,
                            bestVote.confidenceSum
                                    / Math.max(
                                    1,
                                    bestVote.count
                            )
                    );


            if (bestVote.count < 2) {
                stable = false;
            }
        }


        if (text.length() == 0) {
            return null;
        }


        return new Result(
                text.toString(),
                minimumConfidence,
                state.observations,
                stable,
                state.layout,
                state.rowCounts
        );
    }


    private static String structureKey(
            List<Integer> rowCounts
    ) {
        StringBuilder key =
                new StringBuilder();


        key.append(
                rowCounts.size()
        );

        key.append(':');


        for (int i = 0;
             i < rowCounts.size();
             i++) {

            if (i > 0) {
                key.append(',');
            }


            key.append(
                    Math.max(
                            0,
                            rowCounts.get(i)
                    )
            );
        }


        return key.toString();
    }


    private static String layoutName(
            List<Integer> rowCounts
    ) {
        if (rowCounts == null
                || rowCounts.isEmpty()) {

            return LAYOUT_UNKNOWN;
        }


        if (rowCounts.size() == 1) {
            return LAYOUT_SINGLE_ROW;
        }


        if (rowCounts.size() == 2) {
            return LAYOUT_TWO_ROW;
        }


        return LAYOUT_MULTI_ROW;
    }
}