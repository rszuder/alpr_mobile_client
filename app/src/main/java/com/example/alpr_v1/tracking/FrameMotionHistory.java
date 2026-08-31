package com.example.alpr_v1.tracking;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Krótka historia ruchu obrazu. Pozwala przenieść geometrię wyniku inferencji
 * z czasu wykonania zdjęcia do aktualnie widocznej klatki podglądu.
 */
public final class FrameMotionHistory {
    private static final int MAXIMUM_SAMPLES = 320;
    private static final long MAXIMUM_AGE_NANOS = 30_000_000_000L;

    private static final class Sample {
        final long destinationTimestampNanos;
        final FrameMotionTransform transform;

        Sample(long destinationTimestampNanos, FrameMotionTransform transform) {
            this.destinationTimestampNanos = destinationTimestampNanos;
            this.transform = transform;
        }
    }

    private final Deque<Sample> samples = new ArrayDeque<>();

    public synchronized void record(
            long destinationTimestampNanos,
            FrameMotionTransform transform
    ) {
        if (destinationTimestampNanos <= 0L
                || transform == null
                || !transform.valid) {
            return;
        }
        Sample newest = samples.peekLast();
        if (newest != null
                && destinationTimestampNanos <= newest.destinationTimestampNanos) {
            return;
        }
        samples.addLast(new Sample(destinationTimestampNanos, transform));
        long oldestAllowed = Math.max(
                0L,
                destinationTimestampNanos - MAXIMUM_AGE_NANOS
        );
        while (samples.size() > MAXIMUM_SAMPLES
                || (!samples.isEmpty()
                && samples.peekFirst().destinationTimestampNanos < oldestAllowed)) {
            samples.removeFirst();
        }
    }

    public synchronized FrameMotionTransform transformAfter(
            long sourceTimestampNanos
    ) {
        if (sourceTimestampNanos <= 0L || samples.isEmpty()) {
            return FrameMotionTransform.invalid();
        }
        FrameMotionTransform composed = FrameMotionTransform.identity();
        boolean found = false;
        for (Sample sample : samples) {
            if (sample.destinationTimestampNanos <= sourceTimestampNanos) continue;
            composed = FrameMotionTransform.compose(composed, sample.transform);
            found = true;
        }
        return found ? composed : FrameMotionTransform.identity();
    }

    public synchronized void reset() {
        samples.clear();
    }
}
