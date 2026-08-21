package com.example.alpr_v1.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lekki tracker ramek w znormalizowanych współrzędnych obrazu.
 *
 * <p>Łączy kolejne detekcje na podstawie IoU i ruchu środka, estymuje prędkość
 * ramki oraz kompensuje opóźnienie inferencji. Nie podtrzymuje starych śladów,
 * gdy w tej samej klatce pojawiły się nowe, niedopasowane obiekty — dzięki temu
 * szybki obrót telefonu nie pozostawia „duchów” obok aktualnej tablicy.</p>
 */
public final class MotionBoxTracker {
    public static final class Box {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Box(float left, float top, float right, float bottom) {
            this.left = clamp(Math.min(left, right));
            this.top = clamp(Math.min(top, bottom));
            this.right = clamp(Math.max(left, right));
            this.bottom = clamp(Math.max(top, bottom));
        }

        public float width() { return Math.max(0f, right - left); }
        public float height() { return Math.max(0f, bottom - top); }
        public float centerX() { return (left + right) * 0.5f; }
        public float centerY() { return (top + bottom) * 0.5f; }
    }

    public static final class Observation {
        public final Box box;
        public final String label;
        public final int sourceIndex;

        public Observation(Box box, String label, int sourceIndex) {
            this.box = box;
            this.label = label == null ? "" : label;
            this.sourceIndex = sourceIndex;
        }
    }

    public static final class Result {
        public final long trackId;
        public final Box box;
        public final String label;
        public final int sourceIndex;
        public final boolean predicted;

        private Result(long trackId, Box box, String label, int sourceIndex, boolean predicted) {
            this.trackId = trackId;
            this.box = box;
            this.label = label;
            this.sourceIndex = sourceIndex;
            this.predicted = predicted;
        }
    }

    private static final class Track {
        long id;
        Box box;
        String label;
        long observationNanos;
        float velocityX;
        float velocityY;
        float velocityWidth;
        float velocityHeight;
        int missedFrames;
    }

    private static final float DUPLICATE_IOU = 0.45f;
    private static final float DUPLICATE_CONTAINMENT = 0.72f;
    private static final long MAX_PREDICTION_NANOS = 140_000_000L;
    private final List<Track> tracks = new ArrayList<>();
    private long nextTrackId = 1L;

    public synchronized List<Result> update(
            List<Observation> rawObservations,
            long observationNanos,
            long presentationNanos
    ) {
        List<Observation> observations = deduplicate(rawObservations);
        boolean[] matchedTracks = new boolean[tracks.size()];
        List<Track> next = new ArrayList<>();
        List<Integer> sourceIndices = new ArrayList<>();

        for (Observation observation : observations) {
            int match = bestMatch(observation.box, observationNanos, matchedTracks);
            Track track;
            if (match >= 0) {
                matchedTracks[match] = true;
                track = tracks.get(match);
                updateTrack(track, observation, observationNanos);
            } else {
                track = new Track();
                track.id = nextTrackId++;
                track.box = observation.box;
                track.label = observation.label;
                track.observationNanos = observationNanos;
            }
            next.add(track);
            sourceIndices.add(observation.sourceIndex);
        }

        // Jedną pominiętą klatkę podtrzymujemy tylko wtedy, gdy detektor nie zwrócił
        // niczego. Gdy istnieją nowe detekcje, stare ślady nie mogą tworzyć duchów.
        if (observations.isEmpty()) {
            for (Track track : tracks) {
                if (track.missedFrames >= 1) continue;
                track.missedFrames++;
                next.add(track);
                sourceIndices.add(-1);
            }
        }

        tracks.clear();
        tracks.addAll(next);
        List<Result> results = new ArrayList<>(tracks.size());
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            long ahead = Math.max(0L, Math.min(
                    MAX_PREDICTION_NANOS,
                    presentationNanos - track.observationNanos
            ));
            Box visible = predict(track, ahead / 1_000_000_000f);
            int sourceIndex = sourceIndices.get(i);
            results.add(new Result(
                    track.id, visible, track.label, sourceIndex,
                    sourceIndex < 0 || ahead > 0L
            ));
        }
        return Collections.unmodifiableList(results);
    }

    public synchronized void reset() {
        tracks.clear();
        nextTrackId = 1L;
    }

    private int bestMatch(Box observation, long observationNanos, boolean[] matched) {
        int bestIndex = -1;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < tracks.size(); i++) {
            if (matched[i]) continue;
            Track track = tracks.get(i);
            float dt = secondsBetween(track.observationNanos, observationNanos);
            Box predicted = predict(track, dt);
            float overlap = iou(predicted, observation);
            float distance = centerDistance(predicted, observation);
            float scale = Math.max(0.05f, (diagonal(predicted) + diagonal(observation)) * 0.45f);
            float widthRatio = ratio(predicted.width(), observation.width());
            float heightRatio = ratio(predicted.height(), observation.height());
            if (widthRatio < 0.38f || heightRatio < 0.38f) continue;
            if (overlap < 0.08f && distance > scale) continue;
            float proximity = Math.max(0f, 1f - distance / Math.max(scale, 0.001f));
            float score = overlap * 0.75f + proximity * 0.25f;
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static void updateTrack(Track track, Observation observation, long observationNanos) {
        float dt = secondsBetween(track.observationNanos, observationNanos);
        Box previous = track.box;
        float measuredVx = (observation.box.centerX() - previous.centerX()) / dt;
        float measuredVy = (observation.box.centerY() - previous.centerY()) / dt;
        float measuredVw = (observation.box.width() - previous.width()) / dt;
        float measuredVh = (observation.box.height() - previous.height()) / dt;
        track.velocityX = clampVelocity(blend(track.velocityX, measuredVx, 0.30f));
        track.velocityY = clampVelocity(blend(track.velocityY, measuredVy, 0.30f));
        track.velocityWidth = clampVelocity(blend(track.velocityWidth, measuredVw, 0.22f));
        track.velocityHeight = clampVelocity(blend(track.velocityHeight, measuredVh, 0.22f));

        float displacement = centerDistance(previous, observation.box);
        float alpha = displacement > 0.03f
                ? 0.78f
                : displacement > 0.012f ? 0.58f : 0.38f;
        Box predicted = predict(track, dt);
        track.box = lerp(predicted, observation.box, alpha);
        if (!observation.label.isEmpty()) track.label = observation.label;
        track.observationNanos = observationNanos;
        track.missedFrames = 0;
    }

    private static List<Observation> deduplicate(List<Observation> observations) {
        if (observations == null || observations.isEmpty()) return Collections.emptyList();
        List<Observation> unique = new ArrayList<>();
        for (Observation candidate : observations) {
            boolean duplicate = false;
            for (Observation selected : unique) {
                if (iou(selected.box, candidate.box) >= DUPLICATE_IOU
                        || containment(selected.box, candidate.box) >= DUPLICATE_CONTAINMENT) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) unique.add(candidate);
        }
        return unique;
    }

    private static Box predict(Track track, float seconds) {
        float cx = track.box.centerX() + track.velocityX * seconds;
        float cy = track.box.centerY() + track.velocityY * seconds;
        float width = Math.max(0.001f, track.box.width() + track.velocityWidth * seconds);
        float height = Math.max(0.001f, track.box.height() + track.velocityHeight * seconds);
        return new Box(
                cx - width * 0.5f, cy - height * 0.5f,
                cx + width * 0.5f, cy + height * 0.5f
        );
    }

    private static Box lerp(Box from, Box to, float amount) {
        return new Box(
                blend(from.left, to.left, amount),
                blend(from.top, to.top, amount),
                blend(from.right, to.right, amount),
                blend(from.bottom, to.bottom, amount)
        );
    }

    private static float iou(Box a, Box b) {
        float intersection = intersection(a, b);
        float union = a.width() * a.height() + b.width() * b.height() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    private static float containment(Box a, Box b) {
        float smaller = Math.min(a.width() * a.height(), b.width() * b.height());
        return smaller <= 0f ? 0f : intersection(a, b) / smaller;
    }

    private static float intersection(Box a, Box b) {
        return Math.max(0f, Math.min(a.right, b.right) - Math.max(a.left, b.left))
                * Math.max(0f, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
    }

    private static float centerDistance(Box a, Box b) {
        return (float) Math.hypot(a.centerX() - b.centerX(), a.centerY() - b.centerY());
    }

    private static float diagonal(Box box) {
        return (float) Math.hypot(box.width(), box.height());
    }

    private static float ratio(float first, float second) {
        float maximum = Math.max(first, second);
        return maximum <= 0f ? 0f : Math.min(first, second) / maximum;
    }

    private static float secondsBetween(long from, long to) {
        if (from <= 0L || to <= from) return 1f / 30f;
        return Math.max(1f / 60f, Math.min(0.5f, (to - from) / 1_000_000_000f));
    }

    private static float blend(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float clampVelocity(float value) {
        return Math.max(-1.25f, Math.min(1.25f, value));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
