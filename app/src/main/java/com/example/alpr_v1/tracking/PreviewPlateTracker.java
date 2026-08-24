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
 * Lekki tracker tablicy działający na klatkach PreviewView.
 *
 * Nie wykonuje inferencji.
 *
 * MT okresowo dostarcza dokładne położenie tablicy,
 * a pomiędzy kolejnymi wynikami MT tracker szacuje
 * przesunięcie tablicy na podstawie obrazu.
 *
 * Wersja v1 śledzi translację całego quada.
 */
public final class PreviewPlateTracker {

    /*
     * Preview jest zmniejszany przed śledzeniem.
     *
     * Dzięki temu tracker ma mały koszt nawet wtedy,
     * gdy podgląd telefonu ma rozdzielczość Full HD.
     */
    private static final int MAX_TRACK_WIDTH =
            320;


    /*
     * Fragment analizowany wokół każdego narożnika:
     *
     * radius = 3 -> patch 7x7.
     */
    private static final int PATCH_RADIUS =
            3;


    /*
     * Maksymalne przesunięcie narożnika między
     * kolejnymi klatkami trackera.
     */
    private static final int SEARCH_RADIUS =
            14;


    /*
     * Maksymalny średni błąd jasności patcha.
     *
     * Chroni przed śledzeniem przypadkowego fragmentu obrazu.
     */
    private static final float MAX_MEAN_ERROR =
            34f;


    /*
     * Cztery narożniki powinny zgadzać się mniej więcej
     * co do kierunku i wielkości ruchu.
     */
    private static final int DISPLACEMENT_CONSISTENCY =
            3;


    /*
     * Jeżeli MT przez bardzo długi czas nie potwierdzi
     * tablicy, przestajemy ufać trackerowi.
     */
    private static final long MAX_WITHOUT_REANCHOR_NANOS =
            8_000_000_000L;


    private List<OverlayItem> baseItems =
            Collections.emptyList();


    private OverlayItem basePlate;


    private int plateIndex =
            -1;


    private int sourceWidth;
    private int sourceHeight;


    /*
     * Pozycje narożników w zmniejszonym obrazie trackera.
     */
    private List<PointF> trackedPoints =
            Collections.emptyList();


    private byte[] previousGray;


    private int trackerWidth;
    private int trackerHeight;


    private int previousPreviewWidth;
    private int previousPreviewHeight;


    private long lastAnchorNanos =
            Long.MIN_VALUE;


    /**
     * Ponowne zakotwiczenie trackera wynikiem MT.
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


        int foundIndex =
                -1;

        OverlayItem foundPlate =
                null;


        for (int index = 0;
             index < items.size();
             index++) {

            OverlayItem candidate =
                    items.get(index);


            if (candidate.kind
                    != OverlayItem.Kind.PLATE) {

                continue;
            }


            if (candidate.normalizedKeypoints.size()
                    < 4) {

                continue;
            }


            foundIndex =
                    index;

            foundPlate =
                    candidate;

            break;
        }


        /*
         * Brak tablicy w pojedynczym wyniku MT nie zabija
         * natychmiast poprzedniego trackera.
         *
         * Może to być chwilowy miss detektora.
         */
        if (foundPlate == null) {

            return false;
        }


        this.baseItems =
                Collections.unmodifiableList(
                        new ArrayList<>(
                                items
                        )
                );


        this.basePlate =
                foundPlate;


        this.plateIndex =
                foundIndex;


        this.sourceWidth =
                sourceWidth;

        this.sourceHeight =
                sourceHeight;


        /*
         * Nowy MT jest nowym punktem odniesienia.
         *
         * Pierwsza kolejna klatka Preview utworzy
         * świeży obraz referencyjny.
         */
        this.previousGray =
                null;


        this.trackedPoints =
                Collections.emptyList();


        this.lastAnchorNanos =
                System.nanoTime();


        return true;
    }


    /**
     * Przetwarza kolejną klatkę PreviewView.
     *
     * null oznacza:
     * nie mamy jeszcze wiarygodnej aktualizacji.
     */
    public synchronized List<OverlayItem> update(
            Bitmap preview
    ) {

        if (preview == null
                || preview.isRecycled()
                || basePlate == null
                || plateIndex < 0) {

            return null;
        }


        long now =
                System.nanoTime();


        if (lastAnchorNanos == Long.MIN_VALUE
                || now - lastAnchorNanos
                > MAX_WITHOUT_REANCHOR_NANOS) {

            reset();

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
         * Pierwsza klatka po wyniku MT tworzy
         * punkt odniesienia trackera.
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


            trackedPoints =
                    initialTrackerPoints(
                            preview.getWidth(),
                            preview.getHeight(),
                            trackerWidth,
                            trackerHeight
                    );


            previousGray =
                    current.gray;


            return null;
        }


        if (trackedPoints.size() < 4) {

            previousGray =
                    current.gray;

            return null;
        }


        List<Match> matches =
                new ArrayList<>(
                        4
                );


        for (int index = 0;
             index < 4;
             index++) {

            matches.add(
                    matchPoint(
                            previousGray,
                            current.gray,
                            trackerWidth,
                            trackerHeight,
                            trackedPoints.get(
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

            previousGray =
                    current.gray;

            return null;
        }


        int medianDx =
                median(
                        dxValues
                );

        int medianDy =
                median(
                        dyValues
                );


        /*
         * Weryfikujemy, czy przynajmniej dwa narożniki
         * naprawdę opisują ten sam ruch.
         */
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

            previousGray =
                    current.gray;

            return null;
        }


        List<PointF> moved =
                new ArrayList<>(
                        trackedPoints.size()
                );


        for (PointF point :
                trackedPoints) {

            moved.add(
                    new PointF(
                            point.x + medianDx,
                            point.y + medianDy
                    )
            );
        }


        trackedPoints =
                Collections.unmodifiableList(
                        moved
                );


        previousGray =
                current.gray;


        List<OverlayItem> result =
                buildTrackedItems(
                        preview.getWidth(),
                        preview.getHeight()
                );


        android.util.Log.d(
                "ALPR_PREVIEW_TRACK",
                "dx=" + medianDx
                        + " dy=" + medianDy
                        + " support=" + support
        );


        return result;
    }


    public synchronized int sourceWidth() {

        return sourceWidth;
    }


    public synchronized int sourceHeight() {

        return sourceHeight;
    }


    public synchronized void reset() {

        baseItems =
                Collections.emptyList();

        basePlate =
                null;

        plateIndex =
                -1;

        sourceWidth =
                0;

        sourceHeight =
                0;

        trackedPoints =
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

        lastAnchorNanos =
                Long.MIN_VALUE;
    }


    private List<PointF> initialTrackerPoints(
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
                    basePlate
                            .normalizedKeypoints
                            .get(index);


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


    private List<OverlayItem> buildTrackedItems(
            int previewWidth,
            int previewHeight
    ) {

        if (trackedPoints.size() < 4
                || basePlate == null) {

            return null;
        }


        PointF trackedFirst =
                trackerToNormalized(
                        trackedPoints.get(0),
                        sourceWidth,
                        sourceHeight,
                        previewWidth,
                        previewHeight,
                        trackerWidth,
                        trackerHeight
                );


        PointF originalFirst =
                basePlate
                        .normalizedKeypoints
                        .get(0);


        float dx =
                trackedFirst.x
                        - originalFirst.x;

        float dy =
                trackedFirst.y
                        - originalFirst.y;


        /*
         * Nie pozwalamy bboxowi wyjść poza obraz.
         */
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


        /*
         * W wersji v1 zachowujemy kształt quada
         * i przesuwamy go jako całość.
         */
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


        OverlayItem trackedPlate =
                new OverlayItem(
                        OverlayItem.Kind.PLATE,
                        movedBounds,
                        movedPoints,
                        basePlate.label,
                        basePlate.trackId,
                        basePlate.carriedPrediction
                );


        List<OverlayItem> result =
                new ArrayList<>(
                        baseItems
                );


        result.set(
                plateIndex,
                trackedPlate
        );


        return Collections.unmodifiableList(
                result
        );
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


                /*
                 * Prosta kompensacja lokalnej zmiany jasności.
                 */
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
}