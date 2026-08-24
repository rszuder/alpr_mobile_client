package com.example.alpr_v1.tracking;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;

import com.example.alpr_v1.ui.OverlayItem;

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
            320;

    private static final int PATCH_RADIUS =
            3;

    private static final int SEARCH_RADIUS =
            14;

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


    private static final class TrackState {

        final OverlayItem basePlate;

        List<PointF> trackedPoints =
                Collections.emptyList();

        int consecutiveFailures;


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


        /*
         * Pierwsza klatka po nowym wyniku MT
         * tworzy obraz odniesienia.
         */
        if (previousGray == null
                || trackerWidth != current.width
                || trackerHeight != current.height
                || previousPreviewWidth
                != preview.getWidth()
                || previousPreviewHeight
                != preview.getHeight()) {

            trackerWidth =
                    current.width;

            trackerHeight =
                    current.height;

            previousPreviewWidth =
                    preview.getWidth();

            previousPreviewHeight =
                    preview.getHeight();


            for (TrackState track :
                    tracks) {

                track.trackedPoints =
                        initialTrackerPoints(
                                track.basePlate,
                                preview.getWidth(),
                                preview.getHeight(),
                                trackerWidth,
                                trackerHeight
                        );

                track.consecutiveFailures =
                        0;
            }


            previousGray =
                    current.gray;


            return null;
        }


        List<TrackState> survivingTracks =
                new ArrayList<>();

        List<OverlayItem> visible =
                new ArrayList<>();


        for (TrackState track :
                tracks) {

            if (track.trackedPoints.size()
                    < 4) {

                continue;
            }


            TrackUpdate update =
                    updateTrack(
                            track,
                            previousGray,
                            current.gray,
                            trackerWidth,
                            trackerHeight
                    );


            if (!update.valid) {

                track.consecutiveFailures++;


                /*
                 * Krótki pojedynczy miss nie powoduje
                 * migotania ramki.
                 */
                if (track.consecutiveFailures
                        < MAX_CONSECUTIVE_FAILURES) {

                    OverlayItem unchanged =
                            buildTrackedPlate(
                                    track,
                                    preview.getWidth(),
                                    preview.getHeight()
                            );


                    if (unchanged != null) {

                        visible.add(
                                unchanged
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


            List<PointF> moved =
                    new ArrayList<>(
                            track.trackedPoints.size()
                    );


            for (PointF point :
                    track.trackedPoints) {

                moved.add(
                        new PointF(
                                point.x + update.dx,
                                point.y + update.dy
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
                            preview.getWidth(),
                            preview.getHeight()
                    );


            if (trackedPlate != null) {

                visible.add(
                        trackedPlate
                );

                survivingTracks.add(
                        track
                );
            }


            android.util.Log.d(
                    "ALPR_PREVIEW_TRACK",
                    "track="
                            + track.basePlate.trackId
                            + " dx="
                            + update.dx
                            + " dy="
                            + update.dy
                            + " support="
                            + update.support
            );
        }


        tracks =
                survivingTracks;


        previousGray =
                current.gray;


        if (visible.isEmpty()) {

            return null;
        }


        return Collections.unmodifiableList(
                visible
        );
    }


    private static TrackUpdate updateTrack(
            TrackState track,
            byte[] previous,
            byte[] current,
            int width,
            int height
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
                            track.trackedPoints.get(
                                    index
                            )
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
            }
        }


        if (support < 2) {

            return TrackUpdate.invalid();
        }


        return new TrackUpdate(
                true,
                medianDx,
                medianDy,
                support
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
                basePlate.carriedPrediction
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
            PointF point
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
                        sourceX - SEARCH_RADIUS
                );

        int maximumX =
                Math.min(
                        width - PATCH_RADIUS - 1,
                        sourceX + SEARCH_RADIUS
                );

        int minimumY =
                Math.max(
                        PATCH_RADIUS,
                        sourceY - SEARCH_RADIUS
                );

        int maximumY =
                Math.min(
                        height - PATCH_RADIUS - 1,
                        sourceY + SEARCH_RADIUS
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


        for (int candidateY = minimumY;
             candidateY <= maximumY;
             candidateY++) {

            for (int candidateX = minimumX;
                 candidateX <= maximumX;
                 candidateX++) {

                int currentCenter =
                        current[
                                candidateY * width
                                        + candidateX
                                ] & 0xff;


                int brightnessShift =
                        currentCenter
                                - previousCenter;


                int error =
                        0;


                for (int patchY = -PATCH_RADIUS;
                     patchY <= PATCH_RADIUS;
                     patchY++) {

                    int previousRow =
                            (sourceY + patchY)
                                    * width;

                    int currentRow =
                            (candidateY + patchY)
                                    * width;


                    for (int patchX = -PATCH_RADIUS;
                         patchX <= PATCH_RADIUS;
                         patchX++) {

                        int oldValue =
                                previous[
                                        previousRow
                                                + sourceX
                                                + patchX
                                        ] & 0xff;

                        int newValue =
                                current[
                                        currentRow
                                                + candidateX
                                                + patchX
                                        ] & 0xff;


                        error +=
                                Math.abs(
                                        oldValue
                                                - (
                                                newValue
                                                        - brightnessShift
                                        )
                                );
                    }
                }


                float meanError =
                        error
                                / (float) sampleCount;


                if (meanError < bestError) {

                    bestError =
                            meanError;

                    bestX =
                            candidateX;

                    bestY =
                            candidateY;
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

        float scale =
                Math.max(
                        previewWidth
                                / (float) sourceWidth,
                        previewHeight
                                / (float) sourceHeight
                );


        float offsetX =
                (
                        previewWidth
                                - sourceWidth
                                * scale
                ) * 0.5f;

        float offsetY =
                (
                        previewHeight
                                - sourceHeight
                                * scale
                ) * 0.5f;


        float previewX =
                offsetX
                        + point.x
                        * sourceWidth
                        * scale;

        float previewY =
                offsetY
                        + point.y
                        * sourceHeight
                        * scale;


        return new PointF(
                previewX
                        * trackerWidth
                        / previewWidth,

                previewY
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


        float scale =
                Math.max(
                        previewWidth
                                / (float) sourceWidth,
                        previewHeight
                                / (float) sourceHeight
                );


        float offsetX =
                (
                        previewWidth
                                - sourceWidth
                                * scale
                ) * 0.5f;

        float offsetY =
                (
                        previewHeight
                                - sourceHeight
                                * scale
                ) * 0.5f;


        return new PointF(
                clamp(
                        (
                                previewX
                                        - offsetX
                        )
                                / (
                                sourceWidth
                                        * scale
                        ),
                        0f,
                        1f
                ),

                clamp(
                        (
                                previewY
                                        - offsetY
                        )
                                / (
                                sourceHeight
                                        * scale
                        ),
                        0f,
                        1f
                )
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


    private static final class TrackUpdate {

        final boolean valid;
        final int dx;
        final int dy;
        final int support;


        TrackUpdate(
                boolean valid,
                int dx,
                int dy,
                int support
        ) {

            this.valid =
                    valid;

            this.dx =
                    dx;

            this.dy =
                    dy;

            this.support =
                    support;
        }


        static TrackUpdate invalid() {

            return new TrackUpdate(
                    false,
                    0,
                    0,
                    0
            );
        }
    }
}