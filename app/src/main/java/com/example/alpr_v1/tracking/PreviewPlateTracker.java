package com.example.alpr_v1.tracking;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;

import com.example.alpr_v1.ui.OverlayItem;
import com.example.alpr_v1.ui.OverlayViewportTransform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Lekki tracker tablic działający na klatkach PreviewView.
 *
 * MT okresowo dostarcza dokładne położenie tablic,
 * a pomiędzy kolejnymi wynikami MT tracker szacuje
 * ich przesunięcie na obrazie.
 *
 * Wersja v2:
 * - obsługuje wiele tablic,
 * - każda tablica ma własne cztery punkty,
 * - wspólny obraz Preview służy jako źródło ruchu,
 * - na razie śledzona jest translacja całego quada.
 */
public final class PreviewPlateTracker {

    private static final int MAX_TRACK_WIDTH =
            240;

    private static final int PATCH_RADIUS =
            3;

    private static final int SEARCH_RADIUS =
            14;

    private static final int ANCHOR_SEARCH_RADIUS =
            22;

    private static final float MAX_MEAN_ERROR =
            34f;

    private static final int DISPLACEMENT_CONSISTENCY =
            3;

    /*
     * Po kilku kolejnych nieudanych dopasowaniach
     * przestajemy pokazywać dany track.
     */
    private static final int MAX_CONSECUTIVE_FAILURES =
            3;

    private static final int APPEARANCE_GRID_X = 20;
    private static final int APPEARANCE_GRID_Y = 8;


    private static final class TrackState {

        final OverlayItem basePlate;

        List<PointF> trackedPoints =
                Collections.emptyList();

        List<PointF> anchorPoints =
                Collections.emptyList();

        List<SparsePyramidalFlow.Point> flowAnchorPoints =
                Collections.emptyList();

        List<SparsePyramidalFlow.Point> currentFlowPoints =
                Collections.emptyList();

        RobustAffineTransform.Result affineTransform =
                RobustAffineTransform.Result.identity();

        final PlateBoxKalman kalman = new PlateBoxKalman();

        OverlayItem lastTrackedOverlay;

        int trackerInliers;

        int consecutiveFailures;
        int ageFrames;
        int framesSinceMtAnchor;
        float trackingQuality = 1f;
        float supportRatio = 1f;
        float meanMatchError;
        float[] anchorAppearance;
        float localAppearanceSimilarity;
        boolean localAppearanceValidated;


        TrackState(
                OverlayItem basePlate
        ) {

            this.basePlate =
                    basePlate;
        }
    }


    private List<TrackState> tracks =
            Collections.emptyList();


    private byte[] previousGray;
    private byte[] anchorGray;

    private int trackerWidth;
    private int trackerHeight;

    private int previousPreviewWidth;
    private int previousPreviewHeight;

    private int sourceWidth;
    private int sourceHeight;


    /**
     * Nowy wynik MT ponownie kotwiczy wszystkie
     * aktualnie wykryte tablice.
     */
    public synchronized boolean anchor(
            List<OverlayItem> items,
            int sourceWidth,
            int sourceHeight
    ) {

        if (items == null
                || sourceWidth <= 0
                || sourceHeight <= 0) {

            return false;
        }


        List<TrackState> newTracks =
                new ArrayList<>();


        for (OverlayItem item :
                items) {

            if (item.kind
                    != OverlayItem.Kind.PLATE) {

                continue;
            }


            /*
             * Do ponownego kotwiczenia używamy
             * rzeczywistej obserwacji MT, a nie
             * krótkiej predykcji starego trackera.
             */
            if (item.carriedPrediction) {

                continue;
            }


            if (item.normalizedKeypoints.size()
                    < 4) {

                continue;
            }


            newTracks.add(
                    new TrackState(
                            item
                    )
            );
        }


        /*
         * Pojedynczy miss MT nie kasuje od razu
         * istniejącego trackera.
         */
        if (newTracks.isEmpty()) {

            return false;
        }


        this.tracks =
                newTracks;


        this.sourceWidth =
                sourceWidth;

        this.sourceHeight =
                sourceHeight;


        /*
         * Pierwsza następna klatka Preview ustawi
         * pozycje punktów względem aktualnego obrazu.
         */
        previousGray =
                null;


        return true;
    }


    /**
     * Aktualizacja wszystkich aktywnych tablic
     * na podstawie jednej klatki PreviewView.
     */
    public synchronized List<OverlayItem> update(
            Bitmap preview
    ) {
        PreviewTrackingFrame frame = updateTracking(preview);
        return frame == null ? null : frame.overlayItems;
    }


    /**
     * Techniczna aktualizacja trackera. Oprócz geometrii overlay zwraca jakość,
     * błędy i wiek kotwicy potrzebne schedulerowi MT.
     */
    public synchronized PreviewTrackingFrame updateTracking(
            Bitmap preview
    ) {

        if (preview == null
                || preview.isRecycled()
                || tracks.isEmpty()) {

            return null;
        }


        GrayFrame current =
                grayFrame(
                        preview
                );


        if (current == null) {

            return null;
        }

        return updateGrayFrame(
                current,
                preview.getWidth(),
                preview.getHeight()
        );
    }


    private PreviewTrackingFrame updateGrayFrame(
            GrayFrame current,
            int previewWidth,
            int previewHeight
    ) {


        /*
         * Pierwsza klatka po nowym wyniku MT
         * tworzy obraz odniesienia.
         */
        if (previousGray == null
                || trackerWidth != current.width
                || trackerHeight != current.height
                || previousPreviewWidth
                != previewWidth
                || previousPreviewHeight
                != previewHeight) {

            trackerWidth =
                    current.width;

            trackerHeight =
                    current.height;

            previousPreviewWidth =
                    previewWidth;

            previousPreviewHeight =
                    previewHeight;


            for (TrackState track :
                    tracks) {

                List<PointF> initialPoints =
                        initialTrackerPoints(
                                track.basePlate,
                                previewWidth,
                                previewHeight,
                                trackerWidth,
                                trackerHeight
                        );

                track.anchorPoints =
                        initialPoints;

                track.trackedPoints =
                        initialPoints;

                track.flowAnchorPoints =
                        initialFlowPoints(initialPoints);

                track.currentFlowPoints =
                        track.flowAnchorPoints;

                track.affineTransform =
                        RobustAffineTransform.Result.identity();

                track.kalman.reset();

                track.lastTrackedOverlay = null;

                track.trackerInliers = 0;

                track.consecutiveFailures =
                        0;
                track.ageFrames = 0;
                track.framesSinceMtAnchor = 0;
                track.trackingQuality = 1f;
                track.supportRatio = 1f;
                track.meanMatchError = 0f;
                track.anchorAppearance = localAppearanceDescriptor(
                        current.gray,
                        current.width,
                        current.height,
                        initialPoints
                );
                track.localAppearanceSimilarity = 0f;
                track.localAppearanceValidated = false;
            }


            previousGray =
                    current.gray;

            anchorGray =
                    current.gray;


            return null;
        }


        List<TrackState> survivingTracks =
                new ArrayList<>();

        List<OverlayItem> visible =
                new ArrayList<>();

        List<TrackedPlate> technicalResults =
                new ArrayList<>();

        long updatedAtNanos = SystemClock.elapsedRealtimeNanos();


        for (TrackState track :
                tracks) {

            if (track.trackedPoints.size()
                    < 4) {

                continue;
            }

            AffineTrackUpdate affineUpdate = updateAffineTrack(
                    track,
                    previousGray,
                    anchorGray,
                    current.gray,
                    trackerWidth,
                    trackerHeight
            );

            if (affineUpdate.valid) {
                track.consecutiveFailures = 0;
                track.ageFrames++;
                track.framesSinceMtAnchor++;
                track.affineTransform = affineUpdate.transform;
                track.currentFlowPoints = transformedPoints(
                        track.flowAnchorPoints,
                        affineUpdate.transform
                );
                track.trackedPoints = firstFourAndroidPoints(track.currentFlowPoints);
                track.supportRatio = affineUpdate.supportRatio;
                track.meanMatchError = affineUpdate.meanError;
                track.trackerInliers = affineUpdate.inliers;
                track.trackingQuality = affineTrackingQuality(
                        affineUpdate,
                        track.framesSinceMtAnchor
                );

                OverlayItem affinePlate = buildAffineTrackedPlate(
                        track,
                        previewWidth,
                        previewHeight
                );
                if (affinePlate != null) {
                    track.lastTrackedOverlay = affinePlate;
                    visible.add(affinePlate);
                    technicalResults.add(
                            technicalResult(
                                    track, affinePlate, current.gray,
                                    trackerWidth, trackerHeight, updatedAtNanos
                            )
                    );
                    survivingTracks.add(track);
                    android.util.Log.d(
                            "ALPR_PREVIEW_TRACK",
                            "track=" + track.basePlate.trackId
                                    + " mode=KLT_AFFINE"
                                    + " inliers=" + affineUpdate.inliers
                                    + " support=" + affineUpdate.supportRatio
                                    + " quality=" + track.trackingQuality
                    );
                    continue;
                }
            }


            TrackUpdate incrementalUpdate =
                    updateTrack(
                            track.trackedPoints,
                            previousGray,
                            current.gray,
                            trackerWidth,
                            trackerHeight,
                            SEARCH_RADIUS
                    );

            TrackUpdate anchorUpdate =
                    updateTrack(
                            track.anchorPoints,
                            anchorGray,
                            current.gray,
                            trackerWidth,
                            trackerHeight,
                            ANCHOR_SEARCH_RADIUS
                    );

            int currentAbsoluteDx = Math.round(
                    track.trackedPoints.get(0).x
                            - track.anchorPoints.get(0).x
            );
            int currentAbsoluteDy = Math.round(
                    track.trackedPoints.get(0).y
                            - track.anchorPoints.get(0).y
            );

            PreviewTrackerDriftGuard.Decision update =
                    PreviewTrackerDriftGuard.reconcile(
                            currentAbsoluteDx,
                            currentAbsoluteDy,
                            driftMotion(incrementalUpdate),
                            driftMotion(anchorUpdate)
                    );


            if (!update.valid) {

                track.consecutiveFailures++;
                track.ageFrames++;
                track.framesSinceMtAnchor++;
                track.trackingQuality = Math.max(
                        0f,
                        track.trackingQuality * 0.62f
                );
                track.supportRatio = Math.max(
                        incrementalUpdate.support,
                        anchorUpdate.support
                ) / 4f;
                track.meanMatchError = Math.min(
                        finiteError(incrementalUpdate.meanError),
                        finiteError(anchorUpdate.meanError)
                );


                /*
                 * Krótki pojedynczy miss nie powoduje
                 * migotania ramki.
                 */
                if (track.consecutiveFailures
                        < MAX_CONSECUTIVE_FAILURES) {

                    OverlayItem unchanged =
                            buildKalmanPrediction(
                                    track,
                                    previewWidth,
                                    previewHeight
                            );

                    if (unchanged == null) {
                        unchanged = buildTrackedPlate(
                                track,
                                previewWidth,
                                previewHeight
                        );
                    }


                    if (unchanged != null) {

                        visible.add(
                                unchanged
                        );

                        technicalResults.add(
                                technicalResult(
                                        track, unchanged, current.gray,
                                        trackerWidth, trackerHeight, updatedAtNanos
                                )
                        );

                        survivingTracks.add(
                                track
                        );
                    }
                }


                continue;
            }


            track.consecutiveFailures =
                    0;
            track.ageFrames++;
            track.framesSinceMtAnchor++;
            track.supportRatio = update.support / 4f;
            track.trackerInliers = update.support;
            track.meanMatchError = update.anchored
                    ? anchorUpdate.meanError
                    : incrementalUpdate.meanError;
            track.trackingQuality = trackingQuality(
                    track.supportRatio,
                    track.meanMatchError,
                    update.anchored,
                    track.framesSinceMtAnchor
            );


            List<PointF> moved =
                    new ArrayList<>(
                            track.trackedPoints.size()
                    );


            for (PointF point :
                    track.anchorPoints) {

                moved.add(
                        new PointF(
                                point.x + update.absoluteDx,
                                point.y + update.absoluteDy
                        )
                );
            }


            track.trackedPoints =
                    Collections.unmodifiableList(
                            moved
                    );


            OverlayItem trackedPlate =
                    buildTrackedPlate(
                            track,
                            previewWidth,
                            previewHeight
                    );


            if (trackedPlate != null) {

                track.lastTrackedOverlay = trackedPlate;

                visible.add(
                        trackedPlate
                );

                technicalResults.add(
                        technicalResult(
                                track, trackedPlate, current.gray,
                                trackerWidth, trackerHeight, updatedAtNanos
                        )
                );

                survivingTracks.add(
                        track
                );
            }


            android.util.Log.d(
                    "ALPR_PREVIEW_TRACK",
                    "track="
                            + track.basePlate.trackId
                            + " absoluteDx="
                            + update.absoluteDx
                            + " absoluteDy="
                            + update.absoluteDy
                            + " anchored="
                            + update.anchored
                            + " support="
                            + update.support
            );
        }


        tracks =
                survivingTracks;


        previousGray =
                current.gray;


        /*
         * Ważne rozróżnienie:
         *
         * null:
         * tracker nie ma jeszcze nowej informacji,
         * więc UI powinno pozostawić bieżący overlay.
         *
         * emptyList:
         * tracker miał aktywne tablice, ale właśnie
         * stracił wszystkie tracki. UI musi wtedy
         * natychmiast usunąć stare ramki.
         */
        if (visible.isEmpty()) {

            return new PreviewTrackingFrame(
                    Collections.emptyList(),
                    updatedAtNanos
            );
        }


        return new PreviewTrackingFrame(
                technicalResults,
                updatedAtNanos
        );
    }


    /**
     * Aktualizacja bez odczytu GPU/PreviewView. Wejściem jest mała płaszczyzna
     * luminancji skopiowana bezpośrednio z ImageAnalysis CameraX.
     */
    public synchronized PreviewTrackingFrame updateLuma(
            byte[] gray,
            int width,
            int height
    ) {
        if (gray == null || width <= 0 || height <= 0
                || gray.length < width * height
                || tracks.isEmpty()) return null;
        return updateGrayFrame(
                new GrayFrame(
                        width,
                        height,
                        gray
                ),
                width,
                height
        );
    }


    private static TrackUpdate updateTrack(
            List<PointF> referencePoints,
            byte[] previous,
            byte[] current,
            int width,
            int height,
            int searchRadius
    ) {

        List<Match> matches =
                new ArrayList<>(
                        4
                );


        for (int index = 0;
             index < 4;
             index++) {

            matches.add(
                    matchPoint(
                            previous,
                            current,
                            width,
                            height,
                            referencePoints.get(index),
                            searchRadius
                    )
            );
        }


        List<Integer> dxValues =
                new ArrayList<>();

        List<Integer> dyValues =
                new ArrayList<>();


        for (Match match :
                matches) {

            if (!match.valid) {

                continue;
            }


            dxValues.add(
                    match.dx
            );

            dyValues.add(
                    match.dy
            );
        }


        if (dxValues.size() < 2) {

            return TrackUpdate.invalid();
        }


        int medianDx =
                median(
                        dxValues
                );

        int medianDy =
                median(
                        dyValues
                );


        int support =
                0;

        float errorSum =
                0f;


        for (Match match :
                matches) {

            if (!match.valid) {

                continue;
            }


            if (Math.abs(
                    match.dx - medianDx
            ) <= DISPLACEMENT_CONSISTENCY
                    && Math.abs(
                    match.dy - medianDy
            ) <= DISPLACEMENT_CONSISTENCY) {

                support++;
                errorSum += match.error;
            }
        }


        if (support < 2) {

            return TrackUpdate.invalid();
        }


        return new TrackUpdate(
                true,
                medianDx,
                medianDy,
                support,
                errorSum / Math.max(1, support)
        );
    }


    private static AffineTrackUpdate updateAffineTrack(
            TrackState track,
            byte[] previous,
            byte[] anchor,
            byte[] current,
            int width,
            int height
    ) {
        if (track.flowAnchorPoints.size() < 4
                || track.currentFlowPoints.size() < 4) {
            return AffineTrackUpdate.invalid();
        }

        SparsePyramidalFlow.Result anchoredFlow = SparsePyramidalFlow.track(
                anchor,
                current,
                width,
                height,
                track.flowAnchorPoints,
                track.ageFrames % 3 == 0
        );
        RobustAffineTransform.Result anchoredTransform =
                RobustAffineTransform.estimate(anchoredFlow.matches);
        if (anchoredTransform.valid
                && RobustAffineTransform.reasonableQuad(
                anchoredTransform,
                track.flowAnchorPoints,
                width,
                height
        )) {
            return new AffineTrackUpdate(
                    true,
                    anchoredTransform,
                    anchoredFlow.supportRatio,
                    anchoredFlow.meanError,
                    anchoredTransform.inlierCount,
                    true
            );
        }

        SparsePyramidalFlow.Result incrementalFlow = SparsePyramidalFlow.track(
                previous,
                current,
                width,
                height,
                track.currentFlowPoints,
                track.ageFrames % 3 == 0
        );
        RobustAffineTransform.Result incrementalTransform =
                RobustAffineTransform.estimate(incrementalFlow.matches);
        RobustAffineTransform.Result composed = incrementalTransform.valid
                ? incrementalTransform.compose(track.affineTransform)
                : RobustAffineTransform.Result.invalid();
        if (composed.valid
                && RobustAffineTransform.reasonableQuad(
                composed,
                track.flowAnchorPoints,
                width,
                height
        )) {
            return new AffineTrackUpdate(
                    true,
                    composed,
                    incrementalFlow.supportRatio,
                    incrementalFlow.meanError,
                    incrementalTransform.inlierCount,
                    false
            );
        }
        return AffineTrackUpdate.invalid();
    }


    private static List<SparsePyramidalFlow.Point> initialFlowPoints(
            List<PointF> quad
    ) {
        if (quad == null || quad.size() < 4) return Collections.emptyList();
        List<SparsePyramidalFlow.Point> points = new ArrayList<>(8);
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < 4; index++) {
            PointF point = quad.get(index);
            points.add(new SparsePyramidalFlow.Point(point.x, point.y));
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        float[] xFractions = {0.33f, 0.67f};
        float[] yFractions = {0.33f, 0.67f};
        for (float yFraction : yFractions) {
            for (float xFraction : xFractions) {
                points.add(new SparsePyramidalFlow.Point(
                        left + (right - left) * xFraction,
                        top + (bottom - top) * yFraction
                ));
            }
        }
        return Collections.unmodifiableList(points);
    }


    private static List<SparsePyramidalFlow.Point> transformedPoints(
            List<SparsePyramidalFlow.Point> source,
            RobustAffineTransform.Result transform
    ) {
        List<SparsePyramidalFlow.Point> result = new ArrayList<>(source.size());
        for (SparsePyramidalFlow.Point point : source) result.add(transform.apply(point));
        return Collections.unmodifiableList(result);
    }


    private static List<PointF> firstFourAndroidPoints(
            List<SparsePyramidalFlow.Point> source
    ) {
        if (source == null || source.size() < 4) return Collections.emptyList();
        List<PointF> result = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            SparsePyramidalFlow.Point point = source.get(index);
            result.add(new PointF(point.x, point.y));
        }
        return Collections.unmodifiableList(result);
    }


    private OverlayItem buildAffineTrackedPlate(
            TrackState track,
            int previewWidth,
            int previewHeight
    ) {
        if (track.currentFlowPoints.size() < 4) return null;
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < 4; index++) {
            SparsePyramidalFlow.Point point = track.currentFlowPoints.get(index);
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        if (right - left < 2f || bottom - top < 2f) return null;
        PlateBoxKalman.Box filtered = track.kalman.update(new PlateBoxKalman.Box(
                (left + right) * 0.5f,
                (top + bottom) * 0.5f,
                right - left,
                bottom - top
        ));
        List<PointF> normalizedPoints = normalizedFilteredQuad(
                track.currentFlowPoints,
                left,
                top,
                right,
                bottom,
                filtered,
                previewWidth,
                previewHeight
        );
        return overlayFromPoints(track.basePlate, normalizedPoints, false);
    }


    private OverlayItem buildKalmanPrediction(
            TrackState track,
            int previewWidth,
            int previewHeight
    ) {
        if (track.lastTrackedOverlay == null) return null;
        PlateBoxKalman.Box predicted = track.kalman.predict();
        if (predicted == null) return null;
        SparsePyramidalFlow.Point topLeft = trackerToNormalizedPoint(
                predicted.centerX - predicted.width * 0.5f,
                predicted.centerY - predicted.height * 0.5f,
                previewWidth,
                previewHeight
        );
        SparsePyramidalFlow.Point bottomRight = trackerToNormalizedPoint(
                predicted.centerX + predicted.width * 0.5f,
                predicted.centerY + predicted.height * 0.5f,
                previewWidth,
                previewHeight
        );
        RectF predictedBounds = new RectF(
                clamp(topLeft.x, 0f, 1f),
                clamp(topLeft.y, 0f, 1f),
                clamp(bottomRight.x, 0f, 1f),
                clamp(bottomRight.y, 0f, 1f)
        );
        RectF previousBounds = track.lastTrackedOverlay.normalizedBounds;
        if (predictedBounds.width() <= 0f || predictedBounds.height() <= 0f
                || previousBounds.width() <= 0f || previousBounds.height() <= 0f) {
            return null;
        }
        List<PointF> points = new ArrayList<>();
        for (PointF point : track.lastTrackedOverlay.normalizedKeypoints) {
            float relativeX = (point.x - previousBounds.left) / previousBounds.width();
            float relativeY = (point.y - previousBounds.top) / previousBounds.height();
            points.add(new PointF(
                    predictedBounds.left + relativeX * predictedBounds.width(),
                    predictedBounds.top + relativeY * predictedBounds.height()
            ));
        }
        OverlayItem prediction = new OverlayItem(
                OverlayItem.Kind.PLATE,
                predictedBounds,
                points,
                track.lastTrackedOverlay.label,
                track.basePlate.trackId,
                true
        );
        track.lastTrackedOverlay = prediction;
        return prediction;
    }


    private List<PointF> normalizedFilteredQuad(
            List<SparsePyramidalFlow.Point> currentPoints,
            float rawLeft,
            float rawTop,
            float rawRight,
            float rawBottom,
            PlateBoxKalman.Box filtered,
            int previewWidth,
            int previewHeight
    ) {
        float filteredLeft = filtered.centerX - filtered.width * 0.5f;
        float filteredTop = filtered.centerY - filtered.height * 0.5f;
        List<PointF> result = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            SparsePyramidalFlow.Point point = currentPoints.get(index);
            float relativeX = (point.x - rawLeft) / Math.max(1f, rawRight - rawLeft);
            float relativeY = (point.y - rawTop) / Math.max(1f, rawBottom - rawTop);
            SparsePyramidalFlow.Point normalized = trackerToNormalizedPoint(
                    filteredLeft + relativeX * filtered.width,
                    filteredTop + relativeY * filtered.height,
                    previewWidth,
                    previewHeight
            );
            result.add(new PointF(
                    clamp(normalized.x, 0f, 1f),
                    clamp(normalized.y, 0f, 1f)
            ));
        }
        return Collections.unmodifiableList(result);
    }


    private SparsePyramidalFlow.Point trackerToNormalizedPoint(
            float x,
            float y,
            int previewWidth,
            int previewHeight
    ) {
        PointF normalized = trackerToNormalized(
                new PointF(x, y),
                sourceWidth,
                sourceHeight,
                previewWidth,
                previewHeight,
                trackerWidth,
                trackerHeight
        );
        return new SparsePyramidalFlow.Point(normalized.x, normalized.y);
    }


    private static OverlayItem overlayFromPoints(
            OverlayItem base,
            List<PointF> points,
            boolean carried
    ) {
        if (points == null || points.size() < 4) return null;
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (PointF point : points) {
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        if (right - left < 0.002f || bottom - top < 0.002f) return null;
        return new OverlayItem(
                OverlayItem.Kind.PLATE,
                new RectF(left, top, right, bottom),
                points,
                base.label,
                base.trackId,
                carried
        );
    }


    private static float affineTrackingQuality(
            AffineTrackUpdate update,
            int framesSinceMtAnchor
    ) {
        float errorScore = 1f - clamp(update.meanError / 32f, 0f, 1f);
        float inlierScore = Math.min(1f, update.inliers / 10f);
        float anchorScore = update.anchored ? 1f : 0.78f;
        float ageScore = Math.max(0.65f, 1f - framesSinceMtAnchor * 0.01f);
        return clamp(
                0.32f * update.supportRatio
                        + 0.27f * inlierScore
                        + 0.23f * errorScore
                        + 0.10f * anchorScore
                        + 0.08f * ageScore,
                0f,
                1f
        );
    }


    private static TrackedPlate technicalResult(
            TrackState track,
            OverlayItem overlay,
            byte[] currentGray,
            int width,
            int height,
            long updatedAtNanos
    ) {
        float[] currentAppearance = localAppearanceDescriptor(
                currentGray,
                width,
                height,
                track.trackedPoints
        );
        track.localAppearanceValidated = track.anchorAppearance != null
                && currentAppearance != null;
        track.localAppearanceSimilarity = track.localAppearanceValidated
                ? localAppearanceSimilarity(track.anchorAppearance, currentAppearance)
                : 0f;
        return new TrackedPlate(
                overlay,
                track.trackingQuality,
                track.supportRatio,
                track.meanMatchError,
                track.trackerInliers,
                track.consecutiveFailures,
                track.ageFrames,
                track.framesSinceMtAnchor,
                track.localAppearanceSimilarity,
                track.localAppearanceValidated,
                currentAppearance,
                updatedAtNanos
        );
    }


    private static float[] localAppearanceDescriptor(
            byte[] gray,
            int width,
            int height,
            List<PointF> points
    ) {
        if (gray == null || width <= 0 || height <= 0
                || gray.length < width * height || points == null || points.size() < 4) {
            return null;
        }
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (PointF point : points) {
            if (point == null) continue;
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        int cropLeft = clamp(Math.round(left), 0, width - 1);
        int cropTop = clamp(Math.round(top), 0, height - 1);
        int cropRight = clamp(Math.round(right), cropLeft + 1, width);
        int cropBottom = clamp(Math.round(bottom), cropTop + 1, height);
        int cropWidth = cropRight - cropLeft;
        int cropHeight = cropBottom - cropTop;
        if (cropWidth < 4 || cropHeight < 2) return null;

        float[] descriptor = new float[APPEARANCE_GRID_X * APPEARANCE_GRID_Y];
        float sum = 0f;
        int index = 0;
        for (int gy = 0; gy < APPEARANCE_GRID_Y; gy++) {
            int y = clamp(
                    cropTop + Math.round((gy + 0.5f) * cropHeight / APPEARANCE_GRID_Y),
                    cropTop,
                    cropBottom - 1
            );
            for (int gx = 0; gx < APPEARANCE_GRID_X; gx++) {
                int x = clamp(
                        cropLeft + Math.round((gx + 0.5f) * cropWidth / APPEARANCE_GRID_X),
                        cropLeft,
                        cropRight - 1
                );
                float value = gray[y * width + x] & 0xff;
                descriptor[index++] = value;
                sum += value;
            }
        }

        float mean = sum / descriptor.length;
        float energy = 0f;
        for (int i = 0; i < descriptor.length; i++) {
            descriptor[i] -= mean;
            energy += descriptor[i] * descriptor[i];
        }
        if (energy < 1e-3f) return null;
        float norm = (float) Math.sqrt(energy);
        for (int i = 0; i < descriptor.length; i++) descriptor[i] /= norm;
        return descriptor;
    }


    static float localAppearanceSimilarity(float[] anchor, float[] current) {
        if (anchor == null || current == null || anchor.length != current.length) return 0f;
        float dot = 0f;
        for (int i = 0; i < anchor.length; i++) dot += anchor[i] * current[i];
        return clamp(dot, 0f, 1f);
    }


    private static float trackingQuality(
            float supportRatio,
            float meanError,
            boolean anchored,
            int framesSinceMtAnchor
    ) {
        float errorScore = 1f - clamp(
                finiteError(meanError) / MAX_MEAN_ERROR,
                0f,
                1f
        );
        float anchorConsistency = anchored ? 1f : 0.70f;
        float ageScore = Math.max(0.65f, 1f - framesSinceMtAnchor * 0.01f);
        return clamp(
                0.40f * clamp(supportRatio, 0f, 1f)
                        + 0.35f * errorScore
                        + 0.15f * anchorConsistency
                        + 0.10f * ageScore,
                0f,
                1f
        );
    }


    private static float finiteError(float error) {
        return Float.isNaN(error) || Float.isInfinite(error)
                ? MAX_MEAN_ERROR
                : Math.max(0f, error);
    }

    private static PreviewTrackerDriftGuard.Motion driftMotion(
            TrackUpdate update
    ) {
        return new PreviewTrackerDriftGuard.Motion(
                update.valid,
                update.dx,
                update.dy,
                update.support
        );
    }


    private List<PointF> initialTrackerPoints(
            OverlayItem plate,
            int previewWidth,
            int previewHeight,
            int trackerWidth,
            int trackerHeight
    ) {

        List<PointF> result =
                new ArrayList<>(
                        4
                );


        for (int index = 0;
             index < 4;
             index++) {

            PointF normalized =
                    plate.normalizedKeypoints.get(
                            index
                    );


            result.add(
                    normalizedToTracker(
                            normalized,
                            sourceWidth,
                            sourceHeight,
                            previewWidth,
                            previewHeight,
                            trackerWidth,
                            trackerHeight
                    )
            );
        }


        return Collections.unmodifiableList(
                result
        );
    }


    private OverlayItem buildTrackedPlate(
            TrackState track,
            int previewWidth,
            int previewHeight
    ) {

        if (track.trackedPoints.size()
                < 4) {

            return null;
        }


        OverlayItem basePlate =
                track.basePlate;


        PointF trackedFirst =
                trackerToNormalized(
                        track.trackedPoints.get(0),
                        sourceWidth,
                        sourceHeight,
                        previewWidth,
                        previewHeight,
                        trackerWidth,
                        trackerHeight
                );


        PointF originalFirst =
                basePlate.normalizedKeypoints.get(
                        0
                );


        float dx =
                trackedFirst.x
                        - originalFirst.x;

        float dy =
                trackedFirst.y
                        - originalFirst.y;


        dx =
                clamp(
                        dx,
                        -basePlate.normalizedBounds.left,
                        1f - basePlate.normalizedBounds.right
                );


        dy =
                clamp(
                        dy,
                        -basePlate.normalizedBounds.top,
                        1f - basePlate.normalizedBounds.bottom
                );


        RectF originalBounds =
                basePlate.normalizedBounds;


        RectF movedBounds =
                new RectF(
                        originalBounds.left + dx,
                        originalBounds.top + dy,
                        originalBounds.right + dx,
                        originalBounds.bottom + dy
                );


        List<PointF> movedPoints =
                new ArrayList<>(
                        basePlate
                                .normalizedKeypoints
                                .size()
                );


        for (PointF point :
                basePlate.normalizedKeypoints) {

            movedPoints.add(
                    new PointF(
                            clamp(
                                    point.x + dx,
                                    0f,
                                    1f
                            ),
                            clamp(
                                    point.y + dy,
                                    0f,
                                    1f
                            )
                    )
            );
        }


        return new OverlayItem(
                OverlayItem.Kind.PLATE,
                movedBounds,
                movedPoints,
                basePlate.label,
                basePlate.trackId,
                false
        );
    }


    public synchronized int sourceWidth() {

        return sourceWidth;
    }


    public synchronized int sourceHeight() {

        return sourceHeight;
    }


    public synchronized void reset() {

        tracks =
                Collections.emptyList();

        previousGray =
                null;

        anchorGray =
                null;

        trackerWidth =
                0;

        trackerHeight =
                0;

        previousPreviewWidth =
                0;

        previousPreviewHeight =
                0;

        sourceWidth =
                0;

        sourceHeight =
                0;
    }


    private static Match matchPoint(
            byte[] previous,
            byte[] current,
            int width,
            int height,
            PointF point,
            int searchRadius
    ) {

        int sourceX =
                Math.round(
                        point.x
                );

        int sourceY =
                Math.round(
                        point.y
                );


        if (!patchInside(
                sourceX,
                sourceY,
                width,
                height
        )) {

            return Match.invalid();
        }


        int minimumX =
                Math.max(
                        PATCH_RADIUS,
                        sourceX - searchRadius
                );

        int maximumX =
                Math.min(
                        width - PATCH_RADIUS - 1,
                        sourceX + searchRadius
                );

        int minimumY =
                Math.max(
                        PATCH_RADIUS,
                        sourceY - searchRadius
                );

        int maximumY =
                Math.min(
                        height - PATCH_RADIUS - 1,
                        sourceY + searchRadius
                );


        float bestError =
                Float.MAX_VALUE;

        int bestX =
                sourceX;

        int bestY =
                sourceY;


        int previousCenter =
                previous[
                        sourceY * width
                                + sourceX
                        ] & 0xff;


        int sampleCount =
                (PATCH_RADIUS * 2 + 1)
                        * (PATCH_RADIUS * 2 + 1);


        /*
         * Wyszukiwanie grubo-dokładne ogranicza liczbę porównań patchy około
         * 5–7 razy, zachowując ten sam maksymalny promień ruchu. To pozwala
         * wykonywać tracker poza cyklem ciężkiej inferencji z częstotliwością
         * zbliżoną do PreviewView.
         */
        int coarseStep = searchRadius >= 8 ? 3 : 2;
        for (int candidateY = minimumY;
             candidateY <= maximumY;
             candidateY += coarseStep) {
            for (int candidateX = minimumX;
                 candidateX <= maximumX;
                 candidateX += coarseStep) {
                float meanError = patchMeanError(
                        previous,
                        current,
                        width,
                        sourceX,
                        sourceY,
                        candidateX,
                        candidateY,
                        previousCenter,
                        sampleCount
                );
                if (meanError < bestError) {
                    bestError = meanError;
                    bestX = candidateX;
                    bestY = candidateY;
                }
            }
        }

        int fineMinimumX = Math.max(minimumX, bestX - coarseStep);
        int fineMaximumX = Math.min(maximumX, bestX + coarseStep);
        int fineMinimumY = Math.max(minimumY, bestY - coarseStep);
        int fineMaximumY = Math.min(maximumY, bestY + coarseStep);
        for (int candidateY = fineMinimumY;
             candidateY <= fineMaximumY;
             candidateY++) {
            for (int candidateX = fineMinimumX;
                 candidateX <= fineMaximumX;
                 candidateX++) {
                float meanError = patchMeanError(
                        previous,
                        current,
                        width,
                        sourceX,
                        sourceY,
                        candidateX,
                        candidateY,
                        previousCenter,
                        sampleCount
                );
                if (meanError < bestError) {
                    bestError = meanError;
                    bestX = candidateX;
                    bestY = candidateY;
                }
            }
        }


        if (bestError
                > MAX_MEAN_ERROR) {

            return Match.invalid();
        }


        return new Match(
                true,
                bestX - sourceX,
                bestY - sourceY,
                bestError
        );
    }


    private static float patchMeanError(
            byte[] previous,
            byte[] current,
            int width,
            int sourceX,
            int sourceY,
            int candidateX,
            int candidateY,
            int previousCenter,
            int sampleCount
    ) {
        int currentCenter = current[candidateY * width + candidateX] & 0xff;
        int brightnessShift = currentCenter - previousCenter;
        int error = 0;
        for (int patchY = -PATCH_RADIUS; patchY <= PATCH_RADIUS; patchY++) {
            int previousRow = (sourceY + patchY) * width;
            int currentRow = (candidateY + patchY) * width;
            for (int patchX = -PATCH_RADIUS; patchX <= PATCH_RADIUS; patchX++) {
                int oldValue = previous[previousRow + sourceX + patchX] & 0xff;
                int newValue = current[currentRow + candidateX + patchX] & 0xff;
                error += Math.abs(oldValue - (newValue - brightnessShift));
            }
        }
        return error / (float) sampleCount;
    }


    private static boolean patchInside(
            int x,
            int y,
            int width,
            int height
    ) {

        return x - PATCH_RADIUS >= 0
                && y - PATCH_RADIUS >= 0
                && x + PATCH_RADIUS < width
                && y + PATCH_RADIUS < height;
    }


    private static GrayFrame grayFrame(
            Bitmap source
    ) {

        int sourceWidth =
                source.getWidth();

        int sourceHeight =
                source.getHeight();


        if (sourceWidth <= 0
                || sourceHeight <= 0) {

            return null;
        }


        int width =
                Math.min(
                        MAX_TRACK_WIDTH,
                        sourceWidth
                );


        int height =
                Math.max(
                        1,
                        Math.round(
                                sourceHeight
                                        * (
                                        width
                                                / (float) sourceWidth
                                )
                        )
                );


        Bitmap scaled =
                sourceWidth == width
                        && sourceHeight == height

                        ? source

                        : Bitmap.createScaledBitmap(
                        source,
                        width,
                        height,
                        false
                );


        int[] pixels =
                new int[
                        width * height
                        ];


        scaled.getPixels(
                pixels,
                0,
                width,
                0,
                0,
                width,
                height
        );


        byte[] gray =
                new byte[
                        pixels.length
                        ];


        for (int index = 0;
             index < pixels.length;
             index++) {

            int pixel =
                    pixels[index];

            int red =
                    (pixel >> 16)
                            & 0xff;

            int green =
                    (pixel >> 8)
                            & 0xff;

            int blue =
                    pixel
                            & 0xff;


            int luminance =
                    (
                            77 * red
                                    + 150 * green
                                    + 29 * blue
                    ) >> 8;


            gray[index] =
                    (byte) luminance;
        }


        if (scaled != source) {

            scaled.recycle();
        }


        return new GrayFrame(
                width,
                height,
                gray
        );
    }


    private static PointF normalizedToTracker(
            PointF point,
            int sourceWidth,
            int sourceHeight,
            int previewWidth,
            int previewHeight,
            int trackerWidth,
            int trackerHeight
    ) {

        PointF preview = OverlayViewportTransform.mapNormalizedToView(
                point,
                sourceWidth,
                sourceHeight,
                previewWidth,
                previewHeight
        );


        return new PointF(
                preview.x
                        * trackerWidth
                        / previewWidth,

                preview.y
                        * trackerHeight
                        / previewHeight
        );
    }


    private static PointF trackerToNormalized(
            PointF point,
            int sourceWidth,
            int sourceHeight,
            int previewWidth,
            int previewHeight,
            int trackerWidth,
            int trackerHeight
    ) {

        float previewX =
                point.x
                        * previewWidth
                        / trackerWidth;

        float previewY =
                point.y
                        * previewHeight
                        / trackerHeight;


        return OverlayViewportTransform.mapViewToNormalized(
                new PointF(previewX, previewY),
                sourceWidth,
                sourceHeight,
                previewWidth,
                previewHeight
        );
    }


    private static int median(
            List<Integer> values
    ) {

        int[] sorted =
                new int[
                        values.size()
                        ];


        for (int index = 0;
             index < values.size();
             index++) {

            sorted[index] =
                    values.get(index);
        }


        Arrays.sort(
                sorted
        );


        int middle =
                sorted.length / 2;


        if ((sorted.length & 1)
                == 1) {

            return sorted[middle];
        }


        return Math.round(
                (
                        sorted[middle - 1]
                                + sorted[middle]
                ) * 0.5f
        );
    }


    private static float clamp(
            float value,
            float minimum,
            float maximum
    ) {

        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }


    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }


    private static final class GrayFrame {

        final int width;
        final int height;
        final byte[] gray;


        GrayFrame(
                int width,
                int height,
                byte[] gray
        ) {

            this.width =
                    width;

            this.height =
                    height;

            this.gray =
                    gray;
        }
    }


    private static final class Match {

        final boolean valid;
        final int dx;
        final int dy;
        final float error;


        Match(
                boolean valid,
                int dx,
                int dy,
                float error
        ) {

            this.valid =
                    valid;

            this.dx =
                    dx;

            this.dy =
                    dy;

            this.error =
                    error;
        }


        static Match invalid() {

            return new Match(
                    false,
                    0,
                    0,
                    Float.POSITIVE_INFINITY
            );
        }
    }


    private static final class AffineTrackUpdate {
        final boolean valid;
        final RobustAffineTransform.Result transform;
        final float supportRatio;
        final float meanError;
        final int inliers;
        final boolean anchored;

        AffineTrackUpdate(
                boolean valid,
                RobustAffineTransform.Result transform,
                float supportRatio,
                float meanError,
                int inliers,
                boolean anchored
        ) {
            this.valid = valid;
            this.transform = transform;
            this.supportRatio = supportRatio;
            this.meanError = meanError;
            this.inliers = inliers;
            this.anchored = anchored;
        }

        static AffineTrackUpdate invalid() {
            return new AffineTrackUpdate(
                    false,
                    RobustAffineTransform.Result.invalid(),
                    0f,
                    Float.POSITIVE_INFINITY,
                    0,
                    false
            );
        }
    }


    private static final class TrackUpdate {

        final boolean valid;
        final int dx;
        final int dy;
        final int support;
        final float meanError;


        TrackUpdate(
                boolean valid,
                int dx,
                int dy,
                int support,
                float meanError
        ) {

            this.valid =
                    valid;

            this.dx =
                    dx;

            this.dy =
                    dy;

            this.support =
                    support;

            this.meanError =
                    meanError;
        }


        static TrackUpdate invalid() {

            return new TrackUpdate(
                    false,
                    0,
                    0,
                    0,
                    Float.POSITIVE_INFINITY
            );
        }
    }
}
