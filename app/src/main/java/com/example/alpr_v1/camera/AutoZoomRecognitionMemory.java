package com.example.alpr_v1.camera;

import java.util.Locale;

/**
 * Konserwatywnie wybiera tekst pokazywany podczas całego cyklu auto zoomu.
 * Brak odczytu MT/MZ nigdy nie kasuje ostatniego wiarygodnego numeru.
 */
public final class AutoZoomRecognitionMemory {
    private static final int MINIMUM_TEXT_LENGTH = 4;
    private static final int MAXIMUM_TEXT_LENGTH = 10;
    private static final double MINIMUM_NEW_CONFIDENCE = 0.45;
    private static final double STRONG_REPLACEMENT_CONFIDENCE = 0.72;
    private static final double REQUIRED_REPLACEMENT_GAIN = 0.10;
    private static final double CONFIRMED_REPLACEMENT_TOLERANCE = 0.08;

    public static final class Result {
        public final String text;
        public final double confidence;
        public final boolean acceptedFreshText;

        private Result(
                String text,
                double confidence,
                boolean acceptedFreshText
        ) {
            this.text = text;
            this.confidence = confidence;
            this.acceptedFreshText = acceptedFreshText;
        }
    }

    private AutoZoomRecognitionMemory() {
    }

    public static Result choose(
            String rememberedText,
            double rememberedConfidence,
            String freshText,
            double freshConfidence,
            boolean freshConfirmed
    ) {
        String remembered = normalizeText(rememberedText);
        String fresh = normalizeText(freshText);
        double rememberedScore = normalizeConfidence(rememberedConfidence);
        double freshScore = normalizeConfidence(freshConfidence);

        if (!isPlausible(fresh)
                || (!freshConfirmed && freshScore < MINIMUM_NEW_CONFIDENCE)) {
            return new Result(remembered, rememberedScore, false);
        }

        if (remembered.isEmpty()) {
            return new Result(fresh, freshScore, true);
        }

        if (remembered.equals(fresh)) {
            return new Result(
                    remembered,
                    Math.max(rememberedScore, freshScore),
                    true
            );
        }

        boolean confirmedReplacement = freshConfirmed
                && freshScore + CONFIRMED_REPLACEMENT_TOLERANCE
                >= rememberedScore;
        boolean clearlyStrongerReplacement = freshScore
                >= Math.max(
                STRONG_REPLACEMENT_CONFIDENCE,
                rememberedScore + REQUIRED_REPLACEMENT_GAIN
        );
        if (confirmedReplacement || clearlyStrongerReplacement) {
            return new Result(fresh, freshScore, true);
        }

        return new Result(remembered, rememberedScore, false);
    }

    private static boolean isPlausible(String text) {
        return text.length() >= MINIMUM_TEXT_LENGTH
                && text.length() <= MAXIMUM_TEXT_LENGTH;
    }

    private static double normalizeConfidence(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String normalizeText(String value) {
        if (value == null) return "";
        return value.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
    }
}
