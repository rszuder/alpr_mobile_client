package com.example.alpr_v1.vision;

import android.graphics.Bitmap;

/**
 * Lekki detektor nagłej zmiany sceny.
 *
 * Porównuje regularną siatkę próbek luminancji pomiędzy
 * kolejnymi przetwarzanymi klatkami.
 *
 * Globalna zmiana jasności jest kompensowana, dzięki czemu
 * zmiana ekspozycji kamery nie powinna być traktowana tak samo
 * jak przełączenie na zupełnie inne zdjęcie.
 */
public final class SceneChangeDetector {

    private static final int GRID_X = 20;
    private static final int GRID_Y = 20;

    private static final float SAMPLE_DIFFERENCE_THRESHOLD = 32f;

    private static final float SCORE_THRESHOLD = 0.14f;
    private static final float CHANGED_FRACTION_THRESHOLD = 0.38f;

    private static final int COOLDOWN_UPDATES = 2;

    public static final class Result {

        public final boolean initialized;

        /**
         * Końcowa decyzja detektora, po uwzględnieniu cooldownu.
         */
        public final boolean sceneChanged;

        /**
         * Czy sama różnica obrazu przekroczyła progi,
         * niezależnie od cooldownu.
         */
        public final boolean rawCandidate;

        /**
         * Stan cooldownu przed obsłużeniem bieżącej klatki.
         */
        public final int cooldown;

        /**
         * Średnia zmiana struktury obrazu, około 0..1.
         */
        public final float score;

        /**
         * Udział próbek, które zmieniły się silnie.
         */
        public final float changedFraction;

        /**
         * Globalna różnica średniej jasności.
         */
        public final float brightnessDelta;

        private Result(
                boolean initialized,
                boolean sceneChanged,
                boolean rawCandidate,
                int cooldown,
                float score,
                float changedFraction,
                float brightnessDelta
        ) {
            this.initialized = initialized;
            this.sceneChanged = sceneChanged;
            this.rawCandidate = rawCandidate;
            this.cooldown = cooldown;
            this.score = score;
            this.changedFraction = changedFraction;
            this.brightnessDelta = brightnessDelta;
        }
    }

    private float[] previousSamples;

    private int previousWidth;
    private int previousHeight;

    private int cooldownUpdates;

    public synchronized Result update(Bitmap frame) {

        if (frame == null || frame.isRecycled()) {
            return new Result(
                    false,
                    false,
                    false,
                    0,
                    0f,
                    0f,
                    0f
            );
        }

        int width = frame.getWidth();
        int height = frame.getHeight();

        float[] currentSamples = sampleLuminance(frame);

        /*
         * Pierwsza klatka tworzy tylko punkt odniesienia.
         */
        if (previousSamples == null) {

            previousSamples = currentSamples;
            previousWidth = width;
            previousHeight = height;

            return new Result(
                    false,
                    false,
                    false,
                    0,
                    0f,
                    0f,
                    0f
            );
        }

        /*
         * Zmiana orientacji albo rozdzielczości oznacza
         * przerwanie ciągłości sceny.
         */
        if (width != previousWidth || height != previousHeight) {

            previousSamples = currentSamples;
            previousWidth = width;
            previousHeight = height;

            int cooldownBeforeUpdate = cooldownUpdates;

            cooldownUpdates = COOLDOWN_UPDATES;

            return new Result(
                    true,
                    true,
                    true,
                    cooldownBeforeUpdate,
                    1f,
                    1f,
                    0f
            );
        }

        float previousMean = mean(previousSamples);
        float currentMean = mean(currentSamples);

        float differenceSum = 0f;
        int stronglyChanged = 0;

        for (int i = 0; i < currentSamples.length; i++) {

            /*
             * Kompensacja globalnej zmiany jasności.
             */
            float previousCentered =
                    previousSamples[i] - previousMean;

            float currentCentered =
                    currentSamples[i] - currentMean;

            float difference = Math.abs(
                    currentCentered - previousCentered
            );

            differenceSum += difference;

            if (difference >= SAMPLE_DIFFERENCE_THRESHOLD) {
                stronglyChanged++;
            }
        }

        float score =
                differenceSum
                        / (currentSamples.length * 255f);

        float changedFraction =
                stronglyChanged
                        / (float) currentSamples.length;

        float brightnessDelta =
                Math.abs(currentMean - previousMean) / 255f;

        /*
         * Najpierw sprawdzamy sam obraz.
         *
         * To właśnie rawCandidate pozwoli nam odróżnić:
         *
         * candidate=true, changed=false
         *
         * czyli sytuację, gdy obraz mocno się zmienił,
         * ale detektor był jeszcze w cooldownie.
         */
        boolean rawCandidate =
                score >= SCORE_THRESHOLD
                        && changedFraction
                        >= CHANGED_FRACTION_THRESHOLD;

        int cooldownBeforeUpdate = cooldownUpdates;

        boolean changed =
                cooldownUpdates == 0
                        && rawCandidate;

        if (cooldownUpdates > 0) {
            cooldownUpdates--;
        }

        if (changed) {
            cooldownUpdates = COOLDOWN_UPDATES;
        }

        previousSamples = currentSamples;
        previousWidth = width;
        previousHeight = height;

        return new Result(
                true,
                changed,
                rawCandidate,
                cooldownBeforeUpdate,
                score,
                changedFraction,
                brightnessDelta
        );
    }

    public synchronized void reset() {
        previousSamples = null;
        previousWidth = 0;
        previousHeight = 0;
        cooldownUpdates = 0;
    }

    private static float[] sampleLuminance(Bitmap frame) {

        float[] samples =
                new float[GRID_X * GRID_Y];

        int width = frame.getWidth();
        int height = frame.getHeight();

        int index = 0;

        for (int gridY = 0; gridY < GRID_Y; gridY++) {

            int y = Math.min(
                    height - 1,
                    Math.max(
                            0,
                            Math.round(
                                    (gridY + 0.5f)
                                            * height
                                            / GRID_Y
                            )
                    )
            );

            for (int gridX = 0; gridX < GRID_X; gridX++) {

                int x = Math.min(
                        width - 1,
                        Math.max(
                                0,
                                Math.round(
                                        (gridX + 0.5f)
                                                * width
                                                / GRID_X
                                )
                        )
                );

                int pixel = frame.getPixel(x, y);

                int red =
                        (pixel >> 16) & 0xff;

                int green =
                        (pixel >> 8) & 0xff;

                int blue =
                        pixel & 0xff;

                samples[index++] =
                        0.2126f * red
                                + 0.7152f * green
                                + 0.0722f * blue;
            }
        }

        return samples;
    }

    private static float mean(float[] values) {

        if (values == null || values.length == 0) {
            return 0f;
        }

        float sum = 0f;

        for (float value : values) {
            sum += value;
        }

        return sum / values.length;
    }
}