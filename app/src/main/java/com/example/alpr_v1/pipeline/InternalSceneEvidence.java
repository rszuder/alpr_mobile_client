package com.example.alpr_v1.pipeline;

/** One-shot raw evidence emitted by the pipeline's pre-inference bitmap detector. */
final class InternalSceneEvidence {
    final boolean detected;
    final float score;
    final float changedFraction;
    final float brightnessDelta;

    private InternalSceneEvidence(
            boolean detected,
            float score,
            float changedFraction,
            float brightnessDelta
    ) {
        this.detected = detected;
        this.score = clamp01(score);
        this.changedFraction = clamp01(changedFraction);
        this.brightnessDelta = clamp01(brightnessDelta);
    }

    static InternalSceneEvidence none() {
        return new InternalSceneEvidence(false, 0f, 0f, 0f);
    }

    static InternalSceneEvidence detected(
            float score,
            float changedFraction,
            float brightnessDelta
    ) {
        return new InternalSceneEvidence(
                true, score, changedFraction, brightnessDelta
        );
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
