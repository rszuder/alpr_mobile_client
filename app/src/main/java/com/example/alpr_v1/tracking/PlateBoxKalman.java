package com.example.alpr_v1.tracking;

/** Cztery niezależne filtry położenie/prędkość dla [cx, cy, w, h]. */
final class PlateBoxKalman {
    static final class Box {
        final float centerX;
        final float centerY;
        final float width;
        final float height;

        Box(float centerX, float centerY, float width, float height) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = Math.max(1f, width);
            this.height = Math.max(1f, height);
        }
    }

    private final Axis centerX = new Axis(0.10f, 2.6f);
    private final Axis centerY = new Axis(0.10f, 2.6f);
    private final Axis width = new Axis(0.055f, 3.4f);
    private final Axis height = new Axis(0.055f, 3.4f);
    private boolean initialized;

    synchronized Box update(Box measurement) {
        if (measurement == null) return predict();
        if (!initialized) {
            centerX.initialize(measurement.centerX);
            centerY.initialize(measurement.centerY);
            width.initialize(measurement.width);
            height.initialize(measurement.height);
            initialized = true;
            return measurement;
        }
        centerX.predict();
        centerY.predict();
        width.predict();
        height.predict();
        centerX.correct(measurement.centerX);
        centerY.correct(measurement.centerY);
        width.correct(measurement.width);
        height.correct(measurement.height);
        return current();
    }

    synchronized Box predict() {
        if (!initialized) return null;
        centerX.predict();
        centerY.predict();
        width.predict();
        height.predict();
        return current();
    }

    synchronized void reset() {
        initialized = false;
        centerX.reset();
        centerY.reset();
        width.reset();
        height.reset();
    }

    private Box current() {
        return new Box(
                centerX.position,
                centerY.position,
                Math.max(1f, width.position),
                Math.max(1f, height.position)
        );
    }

    private static final class Axis {
        final float processNoise;
        final float measurementNoise;
        float position;
        float velocity;
        float p00;
        float p01;
        float p10;
        float p11;

        Axis(float processNoise, float measurementNoise) {
            this.processNoise = processNoise;
            this.measurementNoise = measurementNoise;
        }

        void initialize(float value) {
            position = value;
            velocity = 0f;
            p00 = 8f;
            p01 = 0f;
            p10 = 0f;
            p11 = 3f;
        }

        void predict() {
            position += velocity;
            float nextP00 = p00 + p01 + p10 + p11 + processNoise;
            float nextP01 = p01 + p11;
            float nextP10 = p10 + p11;
            float nextP11 = p11 + processNoise * 0.35f;
            p00 = nextP00;
            p01 = nextP01;
            p10 = nextP10;
            p11 = nextP11;
        }

        void correct(float measurement) {
            float innovation = measurement - position;
            float denominator = p00 + measurementNoise;
            float gainPosition = p00 / denominator;
            float gainVelocity = p10 / denominator;
            position += gainPosition * innovation;
            velocity += gainVelocity * innovation;
            float oldP00 = p00;
            float oldP01 = p01;
            p00 -= gainPosition * oldP00;
            p01 -= gainPosition * oldP01;
            p10 -= gainVelocity * oldP00;
            p11 -= gainVelocity * oldP01;
        }

        void reset() {
            position = 0f;
            velocity = 0f;
            p00 = p01 = p10 = p11 = 0f;
        }
    }
}
