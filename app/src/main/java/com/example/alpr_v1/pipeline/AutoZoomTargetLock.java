package com.example.alpr_v1.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Utrzymuje tożsamość jednej tablicy podczas sesji auto-zoom. */
final class AutoZoomTargetLock {
    enum State { DISABLED, ACQUIRING, LOCKED, UNCERTAIN, LOST }

    static final class Box {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Box(float left, float top, float right, float bottom) {
            this.left = clamp01(Math.min(left, right));
            this.top = clamp01(Math.min(top, bottom));
            this.right = clamp01(Math.max(left, right));
            this.bottom = clamp01(Math.max(top, bottom));
        }

        float width() { return Math.max(0.001f, right - left); }
        float height() { return Math.max(0.001f, bottom - top); }
        float centerX() { return (left + right) * 0.5f; }
        float centerY() { return (top + bottom) * 0.5f; }
        float area() { return width() * height(); }
    }

    static final class Candidate {
        final int sourceIndex;
        final Box box;
        final float confidence;
        final boolean validGeometry;
        final float[] appearance;

        Candidate(
                int sourceIndex,
                Box box,
                float confidence,
                boolean validGeometry,
                float[] appearance
        ) {
            this.sourceIndex = sourceIndex;
            this.box = box;
            this.confidence = confidence;
            this.validGeometry = validGeometry;
            this.appearance = appearance;
        }
    }

    static final class Selection {
        final Candidate candidate;
        final State state;
        final float score;
        final float secondScore;
        final int candidateCount;

        Selection(
                Candidate candidate,
                State state,
                float score,
                float secondScore,
                int candidateCount
        ) {
            this.candidate = candidate;
            this.state = state;
            this.score = score;
            this.secondScore = secondScore;
            this.candidateCount = candidateCount;
        }
    }

    private static final int MAXIMUM_MISSES = 2;
    private static final float MINIMUM_SCORE = 0.43f;
    private static final float MINIMUM_AMBIGUITY_MARGIN = 0.10f;

    private State state = State.DISABLED;
    private Box predicted;
    private Box accepted;
    private float[] initialAppearance;
    private float[] adaptiveAppearance;
    private int misses;
    private float confidence;

    synchronized void begin(Box expected, float[] appearance) {
        predicted = expected;
        accepted = expected;
        initialAppearance = appearance == null ? null : appearance.clone();
        adaptiveAppearance = appearance == null ? null : appearance.clone();
        misses = 0;
        confidence = 0.5f;
        state = State.ACQUIRING;
    }

    synchronized void clear() {
        state = State.DISABLED;
        predicted = null;
        accepted = null;
        initialAppearance = null;
        adaptiveAppearance = null;
        misses = 0;
        confidence = 0f;
    }

    synchronized boolean active() {
        return state != State.DISABLED && predicted != null;
    }

    synchronized State state() { return state; }
    synchronized float confidence() { return confidence; }
    synchronized int misses() { return misses; }

    synchronized void updatePrediction(Box externalPrediction) {
        if (!active() || externalPrediction == null) return;
        float dx = externalPrediction.centerX() - predicted.centerX();
        float dy = externalPrediction.centerY() - predicted.centerY();
        if (dx * dx + dy * dy > 0.18f * 0.18f) {
            state = State.UNCERTAIN;
            confidence *= 0.75f;
            return;
        }
        predicted = blend(predicted, externalPrediction, 0.70f);
    }

    synchronized Box searchBox() {
        if (!active()) return new Box(0f, 0f, 1f, 1f);
        float uncertainty = state == State.LOCKED ? 1f : 1.35f + 0.30f * misses;
        float halfWidth = Math.min(
                0.30f,
                Math.max(0.12f, predicted.width() * 1.35f) * uncertainty
        );
        float halfHeight = Math.min(
                0.25f,
                Math.max(0.09f, predicted.height() * 2.6f) * uncertainty
        );
        return around(predicted.centerX(), predicted.centerY(), halfWidth, halfHeight);
    }

    synchronized Selection select(List<Candidate> candidates) {
        if (!active()) return new Selection(null, State.DISABLED, 0f, 0f, 0);
        Box gate = searchBox();
        List<ScoredCandidate> scored = new ArrayList<>();
        for (Candidate candidate : candidates == null
                ? Collections.<Candidate>emptyList() : candidates) {
            float score = score(candidate, gate);
            if (score >= 0f) scored.add(new ScoredCandidate(candidate, score));
        }
        scored.sort(Comparator.comparingDouble((ScoredCandidate item) -> item.score).reversed());

        float best = scored.isEmpty() ? 0f : scored.get(0).score;
        float second = scored.size() < 2 ? 0f : scored.get(1).score;
        boolean ambiguous = scored.size() > 1
                && best - second < MINIMUM_AMBIGUITY_MARGIN;
        if (scored.isEmpty() || best < MINIMUM_SCORE || ambiguous) {
            misses++;
            confidence *= ambiguous ? 0.70f : 0.62f;
            state = misses > MAXIMUM_MISSES ? State.LOST : State.UNCERTAIN;
            return new Selection(null, state, best, second, scored.size());
        }

        Candidate selected = scored.get(0).candidate;
        misses = 0;
        confidence = best;
        state = State.LOCKED;
        accepted = selected.box;
        predicted = blend(predicted, selected.box, 0.82f);
        if (selected.appearance != null && best >= 0.72f) {
            adaptiveAppearance = PlateAppearanceDescriptor.blend(
                    adaptiveAppearance,
                    selected.appearance,
                    0.12f
            );
        }
        return new Selection(selected, state, best, second, scored.size());
    }

    private float score(Candidate candidate, Box gate) {
        if (candidate == null || candidate.box == null) return -1f;
        Box box = candidate.box;
        if (box.centerX() < gate.left || box.centerX() > gate.right
                || box.centerY() < gate.top || box.centerY() > gate.bottom) return -1f;

        float widthRatio = box.width() / predicted.width();
        float heightRatio = box.height() / predicted.height();
        if (widthRatio < 0.40f || widthRatio > 2.50f
                || heightRatio < 0.35f || heightRatio > 3.0f) return -1f;

        float gateHalfWidth = Math.max(0.01f, gate.width() * 0.5f);
        float gateHalfHeight = Math.max(0.01f, gate.height() * 0.5f);
        float nx = (box.centerX() - predicted.centerX()) / gateHalfWidth;
        float ny = (box.centerY() - predicted.centerY()) / gateHalfHeight;
        float motion = clamp01(1f - (float) Math.sqrt(nx * nx + ny * ny));
        float overlap = iou(predicted, box);
        float scale = (float) Math.exp(-Math.abs(Math.log(box.area() / predicted.area())));
        float aspect = (float) Math.exp(-Math.abs(Math.log(
                (box.width() / box.height()) / (predicted.width() / predicted.height())
        )));
        float appearance = appearanceScore(candidate.appearance);

        float score;
        if (initialAppearance == null && adaptiveAppearance == null) {
            score = 0.38f * motion
                    + 0.27f * overlap
                    + 0.15f * scale
                    + 0.08f * aspect
                    + 0.12f * clamp01(candidate.confidence);
        } else {
            score = 0.30f * motion
                    + 0.20f * overlap
                    + 0.12f * scale
                    + 0.06f * aspect
                    + 0.22f * appearance
                    + 0.10f * clamp01(candidate.confidence);
        }
        return candidate.validGeometry ? score : score * 0.82f;
    }

    private float appearanceScore(float[] candidate) {
        if (candidate == null) return 0.5f;
        float initial = PlateAppearanceDescriptor.similarity(initialAppearance, candidate);
        float adaptive = PlateAppearanceDescriptor.similarity(adaptiveAppearance, candidate);
        return clamp01((Math.max(initial, adaptive) + 1f) * 0.5f);
    }

    private static Box blend(Box first, Box second, float secondWeight) {
        float weight = clamp01(secondWeight);
        return new Box(
                lerp(first.left, second.left, weight),
                lerp(first.top, second.top, weight),
                lerp(first.right, second.right, weight),
                lerp(first.bottom, second.bottom, weight)
        );
    }

    private static Box around(float centerX, float centerY, float halfWidth, float halfHeight) {
        return new Box(
                centerX - halfWidth,
                centerY - halfHeight,
                centerX + halfWidth,
                centerY + halfHeight
        );
    }

    private static float iou(Box first, Box second) {
        float left = Math.max(first.left, second.left);
        float top = Math.max(first.top, second.top);
        float right = Math.min(first.right, second.right);
        float bottom = Math.min(first.bottom, second.bottom);
        float intersection = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float union = first.area() + second.area() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    private static float lerp(float first, float second, float amount) {
        return first + (second - first) * amount;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class ScoredCandidate {
        final Candidate candidate;
        final float score;

        ScoredCandidate(Candidate candidate, float score) {
            this.candidate = candidate;
            this.score = score;
        }
    }
}
