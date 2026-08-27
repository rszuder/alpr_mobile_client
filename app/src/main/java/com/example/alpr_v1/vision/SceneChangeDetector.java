package com.example.alpr_v1.vision;

import android.graphics.Bitmap;

/**
 * Lekki detektor nagłej zmiany sceny.
 *
 * Detektor porównuje regularną siatkę próbek luminancji.
 * Globalna zmiana jasności jest kompensowana.
 *
 * Detektor zostaje uzbrojony dopiero po zaobserwowaniu
 * stabilnej klatki. Po wykryciu zmiany sceny ponownie
 * czeka na stabilizację przed kolejnym zgłoszeniem.
 */
public final class SceneChangeDetector {

    private static final int GRID_X = 20;
    private static final int GRID_Y = 20;

    private static final float SAMPLE_DIFFERENCE_THRESHOLD = 32f;

    private static final float SCORE_THRESHOLD = 0.14f;
    private static final float CHANGED_FRACTION_THRESHOLD = 0.38f;

    public static final class Result {

        public final boolean initialized;
        public final boolean sceneChanged;
        public final boolean rawCandidate;
        public final boolean armed;

        public final float score;
        public final float changedFraction;
        public final float brightnessDelta;

        private Result(
                boolean initialized,
                boolean sceneChanged,
                boolean rawCandidate,
                boolean armed,
                float score,
                float changedFraction,
                float brightnessDelta
        ) {
            this.initialized = initialized;
            this.sceneChanged = sceneChanged;
            this.rawCandidate = rawCandidate;
            this.armed = armed;
            this.score = score;
            this.changedFraction = changedFraction;
            this.brightnessDelta = brightnessDelta;
        }
    }

    private float[] previousSamples;

    private int previousWidth;
    private int previousHeight;

    /*
     * false:
     * detektor czeka na pierwszą stabilną klatkę.
     *
     * true:
     * kolejna duża zmiana może zostać uznana za zmianę sceny.
     */
    private boolean armed;

    public synchronized Result update(Bitmap frame) {

        if (frame == null || frame.isRecycled()) {
            return new Result(
                    false,
                    false,
                    false,
                    armed,
                    0f,
                    0f,
                    0f
            );
        }

        int width = frame.getWidth();
        int height = frame.getHeight();

        float[] currentSamples = sampleLuminance(frame);

        return updateSamples(currentSamples, width, height);
    }

    /**
     * Wariant bez Bitmap używany bezpośrednio na lekkich próbkach płaszczyzny Y.
     * Pozwala wykryć zmianę sceny przed kosztowną konwersją i inferencją.
     */
    public synchronized Result updateSamples(
            float[] currentSamples,
            int width,
            int height
    ) {
        if (currentSamples == null || currentSamples.length == 0
                || width <= 0 || height <= 0) {
            return new Result(false, false, false, armed, 0f, 0f, 0f);
        }

        /*
         * Pierwsza klatka tworzy tylko punkt odniesienia.
         * Nie wolno jeszcze zgłaszać zmiany sceny.
         */
        if (previousSamples == null) {

            previousSamples = currentSamples;
            previousWidth = width;
            previousHeight = height;
            armed = false;

            return new Result(
                    false,
                    false,
                    false,
                    false,
                    0f,
                    0f,
                    0f
            );
        }

        /*
         * PreviewView może zmienić rozmiar po przebudowie layoutu, np. gdy
         * komunikat pod kamerą zajmie inną liczbę wierszy. Sama zmiana wymiarów
         * bitmapy nie jest dowodem zmiany fizycznej sceny. Ustawiamy więc nową
         * referencję i ponownie uzbrajamy detektor na kolejnej stabilnej klatce.
         * Zmiany orientacji i lifecycle są niezależnie obsługiwane przez Activity.
         */
        if (width != previousWidth || height != previousHeight) {
            previousSamples = currentSamples;
            previousWidth = width;
            previousHeight = height;
            armed = false;

            return new Result(
                    true,
                    false,
                    false,
                    false,
                    0f,
                    0f,
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

            float difference =
                    Math.abs(currentCentered - previousCentered);

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

        boolean rawCandidate =
                score >= SCORE_THRESHOLD
                        && changedFraction >= CHANGED_FRACTION_THRESHOLD;

        boolean sceneChanged = false;

        if (!armed) {

            /*
             * Dopóki obraz nadal mocno się zmienia
             * (np. start kamery albo animacja galerii),
             * detektor pozostaje rozbrojony.
             *
             * Pierwsza stabilna klatka go uzbraja.
             */
            if (!rawCandidate) {
                armed = true;
            }

        } else if (rawCandidate) {

            /*
             * Byliśmy w stabilnej scenie i pojawiła się
             * duża zmiana obrazu.
             */
            sceneChanged = true;

            /*
             * Po zmianie czekamy na stabilizację nowej sceny.
             */
            armed = false;
        }

        previousSamples = currentSamples;
        previousWidth = width;
        previousHeight = height;

        return new Result(
                true,
                sceneChanged,
                rawCandidate,
                armed,
                score,
                changedFraction,
                brightnessDelta
        );
    }

    public synchronized void reset() {
        previousSamples = null;
        previousWidth = 0;
        previousHeight = 0;
        armed = false;
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

                int red = (pixel >> 16) & 0xff;
                int green = (pixel >> 8) & 0xff;
                int blue = pixel & 0xff;

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
