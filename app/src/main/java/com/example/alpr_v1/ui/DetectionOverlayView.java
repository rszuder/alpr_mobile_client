package com.example.alpr_v1.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.graphics.Path;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lekki overlay ramek z badge'ami układanymi poza obszarem detekcji. */
public final class DetectionOverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vehiclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vehicleRoiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint predictionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detectionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint confidenceTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final long OVERLAY_TRANSITION_MS =
            120L;


    /*
     * items reprezentuje aktualnie rysowaną,
     * również pośrednią klatkę animacji.
     */
    private List<OverlayItem> items =
            Collections.emptyList();

    private List<RenderItem> renderItems =
            Collections.emptyList();


    private ValueAnimator overlayAnimator;


    private final DecelerateInterpolator overlayInterpolator =
            new DecelerateInterpolator();


    private int sourceWidth;
    private int sourceHeight;
    private boolean diagnosticMode;
    private long focusedTrackId;

    public DetectionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        /*
         * PLATE:
         * jaskrawa magenta odróżnia tablicę zarówno
         * od pomarańczowego VEHICLE, jak i żółtego ROI.
         */
        boxPaint.setColor(
                Color.argb(
                        245,
                        255,
                        55,
                        190
                )
        );

        boxPaint.setStyle(
                Paint.Style.STROKE
        );

        boxPaint.setStrokeWidth(
                dp(2.3f)
        );

        vehiclePaint.setColor(Color.argb(225, 255, 152, 0));
        vehiclePaint.setStyle(Paint.Style.STROKE);
        vehiclePaint.setStrokeWidth(dp(1.8f));

        vehicleRoiPaint.setColor(Color.argb(190, 255, 183, 77));
        vehicleRoiPaint.setStyle(Paint.Style.STROKE);
        vehicleRoiPaint.setStrokeWidth(dp(1.4f));
        vehicleRoiPaint.setPathEffect(
                new DashPathEffect(
                        new float[]{dp(7), dp(5)},
                        0f
                )
        );

        predictionPaint.setColor(Color.argb(95, 125, 211, 252));
        predictionPaint.setStyle(Paint.Style.STROKE);
        predictionPaint.setStrokeWidth(dp(1f));
        predictionPaint.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0f));

        pointPaint.setColor(
                Color.argb(
                        245,
                        255,
                        255,
                        255
                )
        );

        pointPaint.setStyle(
                Paint.Style.FILL
        );

        detectionTextPaint.setColor(Color.rgb(125, 211, 252));
        detectionTextPaint.setTextSize(dp(11.5f));
        detectionTextPaint.setFakeBoldText(true);
        confidenceTextPaint.setColor(Color.rgb(94, 230, 168));
        confidenceTextPaint.setTextSize(dp(10.5f));
        confidenceTextPaint.setFakeBoldText(true);

        labelPaint.setColor(Color.argb(188, 8, 13, 21));
        labelPaint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
    }

    public void setItems(List<OverlayItem> newItems) {
        setItems(newItems, 0, 0);
    }

    /**
     * Szybki tracker Preview jest wlascicielem wyłącznie geometrii PLATE.
     * Diagnostyczne VEHICLE i VEHICLE_ROI pochodza z MP i nie moga byc
     * ponownie skladane ani animowane przy kazdej lekkiej klatce kamery.
     */
    public void setTrackedPlateItems(List<OverlayItem> trackedPlates) {
        if (trackedPlates == null || trackedPlates.isEmpty()) return;

        if (overlayAnimator != null) {
            overlayAnimator.cancel();
            overlayAnimator = null;
        }

        List<OverlayItem> updated = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            if (item.kind != OverlayItem.Kind.PLATE) updated.add(item);
        }
        if (trackedPlates != null) {
            for (OverlayItem item : trackedPlates) {
                if (item != null && item.kind == OverlayItem.Kind.PLATE) {
                    updated.add(item);
                }
            }
        }
        items = Collections.unmodifiableList(updated);
        rebuildRenderItems();
        postInvalidateOnAnimation();
    }

    /** Bezanimacyjna klatka Preview zawierajaca juz komplet warstw. */
    public void setPreviewItems(List<OverlayItem> previewItems) {
        if (overlayAnimator != null) {
            overlayAnimator.cancel();
            overlayAnimator = null;
        }
        items = Collections.unmodifiableList(new ArrayList<>(
                previewItems == null ? Collections.emptyList() : previewItems
        ));
        rebuildRenderItems();
        postInvalidateOnAnimation();
    }

    List<OverlayItem> snapshotItemsForTesting() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public void setItems(
            List<OverlayItem> newItems,
            int sourceWidth,
            int sourceHeight
    ) {

        List<OverlayItem> targetItems =
                Collections.unmodifiableList(
                        new ArrayList<>(
                                newItems == null
                                        ? Collections.emptyList()
                                        : newItems
                        )
                );


        this.sourceWidth =
                sourceWidth;

        this.sourceHeight =
                sourceHeight;


        /*
         * Jeżeli poprzednia animacja jeszcze trwa,
         * zaczynamy nową od aktualnego położenia,
         * a nie od jej starego punktu początkowego.
         */
        if (overlayAnimator != null) {

            overlayAnimator.cancel();

            overlayAnimator =
                    null;
        }


        /*
         * Pusta lista oznacza jawne unieważnienie overlayu,
         * np. zmianę sceny albo STOP.
         *
         * Tego nie morphujemy do przypadkowego miejsca.
         */
        if (targetItems.isEmpty()) {

            items =
                    Collections.emptyList();

            rebuildRenderItems();

            postInvalidateOnAnimation();

            return;
        }


        /*
         * Pierwszy wynik nie ma poprzedniej geometrii,
         * więc pokazujemy go od razu.
         */
        if (items.isEmpty()) {

            items =
                    targetItems;

            rebuildRenderItems();

            postInvalidateOnAnimation();

            return;
        }

        if (sameTrackedTargets(items, targetItems)) {
            items = targetItems;
            rebuildRenderItems();
            postInvalidateOnAnimation();
            return;
        }


        /*
         * Zachowujemy aktualną pozycję jako początek
         * nowej krótkiej korekty.
         */
        final List<OverlayItem> startItems =
                Collections.unmodifiableList(
                        new ArrayList<>(
                                items
                        )
                );


        final List<OverlayItem> finalTargetItems =
                targetItems;


        overlayAnimator =
                ValueAnimator.ofFloat(
                        0f,
                        1f
                );


        overlayAnimator.setDuration(
                OVERLAY_TRANSITION_MS
        );


        overlayAnimator.setInterpolator(
                overlayInterpolator
        );


        overlayAnimator.addUpdateListener(
                animator -> {

                    float progress =
                            (float) animator.getAnimatedValue();


                    items =
                            interpolateItems(
                                    startItems,
                                    finalTargetItems,
                                    progress
                            );


                    rebuildRenderItems();

                    postInvalidateOnAnimation();
                }
        );


        overlayAnimator.start();
    }

    public void setDiagnosticMode(boolean enabled) {
        if (diagnosticMode == enabled) return;
        diagnosticMode = enabled;
        rebuildRenderItems();
        postInvalidateOnAnimation();
    }

    public void setFocusedTrackId(long trackId) {
        long safeTrackId = Math.max(0L, trackId);
        if (focusedTrackId == safeTrackId) return;
        focusedTrackId = safeTrackId;
        rebuildRenderItems();
        postInvalidateOnAnimation();
    }

    private static boolean sameTrackedTargets(
            List<OverlayItem> previous,
            List<OverlayItem> next
    ) {
        if (previous == null || next == null || previous.isEmpty()) return false;
        int previousPlateCount = 0;
        int nextPlateCount = 0;
        for (OverlayItem item : previous) {
            if (item.kind == OverlayItem.Kind.PLATE && item.trackId > 0L) {
                previousPlateCount++;
            }
        }
        for (OverlayItem target : next) {
            if (target.kind != OverlayItem.Kind.PLATE) continue;
            if (target.trackId <= 0L) return false;
            nextPlateCount++;
            boolean found = false;
            for (OverlayItem old : previous) {
                if (old.kind == OverlayItem.Kind.PLATE
                        && old.trackId == target.trackId) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return nextPlateCount > 0 && nextPlateCount == previousPlateCount;
    }

    /**
     * Mapuje punkt ze znormalizowanych współrzędnych obrazu źródłowego do
     * lokalnych współrzędnych widoku dokładnie tą samą transformacją fitCenter,
     * której używają ramki. Celownik i overlay nie mogą stosować dwóch różnych
     * układów współrzędnych.
     */
    public PointF normalizedToViewPoint(float normalizedX, float normalizedY) {
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        if (viewWidth <= 0f || viewHeight <= 0f) {
            return new PointF(0f, 0f);
        }

        float imageWidth = sourceWidth > 0 ? sourceWidth : viewWidth;
        float imageHeight = sourceHeight > 0 ? sourceHeight : viewHeight;
        float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);
        float offsetX = (viewWidth - imageWidth * scale) * 0.5f;
        float offsetY = (viewHeight - imageHeight * scale) * 0.5f;

        return new PointF(
                offsetX + normalizedX * imageWidth * scale,
                offsetY + normalizedY * imageHeight * scale
        );
    }

    /**
     * Fragment obrazu źródłowego faktycznie widoczny po zastosowaniu fitCenter.
     * Cały kadr analizy pozostaje widoczny; metoda nadal jest wspólnym
     * kontraktem dla bezpiecznego zoomu i punktu ostrości.
     */
    public RectF normalizedVisibleBounds() {
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        if (viewWidth <= 0f || viewHeight <= 0f) {
            return new RectF(0f, 0f, 1f, 1f);
        }

        float imageWidth = sourceWidth > 0 ? sourceWidth : viewWidth;
        float imageHeight = sourceHeight > 0 ? sourceHeight : viewHeight;
        float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);
        float scaledWidth = imageWidth * scale;
        float scaledHeight = imageHeight * scale;
        float offsetX = (viewWidth - scaledWidth) * 0.5f;
        float offsetY = (viewHeight - scaledHeight) * 0.5f;

        return new RectF(
                clamp(-offsetX / scaledWidth, 0f, 1f),
                clamp(-offsetY / scaledHeight, 0f, 1f),
                clamp((viewWidth - offsetX) / scaledWidth, 0f, 1f),
                clamp((viewHeight - offsetY) / scaledHeight, 0f, 1f)
        );
    }

    private static List<OverlayItem> interpolateItems(
            List<OverlayItem> previousItems,
            List<OverlayItem> targetItems,
            float progress
    ) {

        float amount =
                clamp(
                        progress,
                        0f,
                        1f
                );


        boolean[] previousUsed =
                new boolean[
                        previousItems.size()
                        ];


        List<OverlayItem> result =
                new ArrayList<>(
                        targetItems.size()
                );


        for (OverlayItem target :
                targetItems) {

            /*
             * Animacja sluzy wyłącznie lagodnej korekcie ramki tablicy.
             * VEHICLE i VEHICLE_ROI pochodza z innej fazy inferencji; ich
             * morphowanie przy pojawieniu sie PLATE dawalo falszywy impuls
             * zmiany wysokosci wszystkich ramek pojazdow.
             */
            if (!shouldInterpolateGeometry(target.kind)) {
                result.add(target);
                continue;
            }

            int previousIndex =
                    findPreviousMatch(
                            target,
                            previousItems,
                            previousUsed
                    );


            /*
             * Nowa detekcja bez odpowiednika.
             *
             * Nie próbujemy przeciągać do niej
             * przypadkowej starej ramki.
             */
            if (previousIndex < 0) {

                result.add(
                        target
                );

                continue;
            }


            previousUsed[previousIndex] =
                    true;


            OverlayItem previous =
                    previousItems.get(
                            previousIndex
                    );


            result.add(
                    interpolateItem(
                            previous,
                            target,
                            amount
                    )
            );
        }


        return Collections.unmodifiableList(
                result
        );
    }

    static boolean shouldInterpolateGeometry(OverlayItem.Kind kind) {
        return kind == OverlayItem.Kind.PLATE;
    }

    private static int findPreviousMatch(
            OverlayItem target,
            List<OverlayItem> previousItems,
            boolean[] previousUsed
    ) {

        /*
         * PLATE oraz exact-entity VEHICLE_ROI mają stabilny identyfikator.
         * To jest najlepszy możliwy klucz i zapobiega przeskakiwaniu ROI
         * pomiędzy pojazdami przy zmianie kolejności rankingu.
         */
        if (target.trackId > 0L) {

            for (int index = 0;
                 index < previousItems.size();
                 index++) {

                if (previousUsed[index]) {
                    continue;
                }


                OverlayItem previous =
                        previousItems.get(
                                index
                        );


                if (previous.kind == target.kind
                        && previous.trackId
                        == target.trackId) {

                    return index;
                }
            }
        }


        /*
         * Surowe VEHICLE i starsze ROI mogą nie mieć stabilnego trackId.
         *
         * Dopasowujemy więc geometrycznie:
         * ten sam rodzaj + możliwie największe IoU.
         */
        int bestIndex =
                -1;

        float bestScore =
                Float.NEGATIVE_INFINITY;


        for (int index = 0;
             index < previousItems.size();
             index++) {

            if (previousUsed[index]) {
                continue;
            }


            OverlayItem previous =
                    previousItems.get(
                            index
                    );


            if (previous.kind
                    != target.kind) {

                continue;
            }


            float overlap =
                    iou(
                            previous.normalizedBounds,
                            target.normalizedBounds
                    );


            float distance =
                    centerDistance(
                            previous.normalizedBounds,
                            target.normalizedBounds
                    );


            /*
             * Duże przesunięcie bez żadnego overlapu
             * jest raczej nowym obiektem niż korektą
             * starej ramki.
             */
            if (overlap < 0.02f
                    && distance > 0.30f) {

                continue;
            }


            float score =
                    overlap
                            - distance * 0.25f;


            if (score > bestScore) {

                bestScore =
                        score;

                bestIndex =
                        index;
            }
        }


        return bestIndex;
    }
    private static OverlayItem interpolateItem(
            OverlayItem previous,
            OverlayItem target,
            float amount
    ) {

        RectF previousBounds =
                previous.normalizedBounds;

        RectF targetBounds =
                target.normalizedBounds;


        RectF bounds =
                new RectF(
                        lerp(
                                previousBounds.left,
                                targetBounds.left,
                                amount
                        ),
                        lerp(
                                previousBounds.top,
                                targetBounds.top,
                                amount
                        ),
                        lerp(
                                previousBounds.right,
                                targetBounds.right,
                                amount
                        ),
                        lerp(
                                previousBounds.bottom,
                                targetBounds.bottom,
                                amount
                        )
                );


        List<PointF> points =
                interpolatePoints(
                        previous.normalizedKeypoints,
                        target.normalizedKeypoints,
                        amount
                );


        /*
         * Geometria jest interpolowana.
         *
         * Tekst, confidence, trackId i stan predykcji
         * pochodzą już z najnowszego wyniku.
         */
        return new OverlayItem(
                target.kind,
                bounds,
                points,
                target.label,
                target.trackId,
                target.carriedPrediction
        );
    }

    private static List<PointF> interpolatePoints(
            List<PointF> previous,
            List<PointF> target,
            float amount
    ) {

        /*
         * Quad MT możemy płynnie morphować tylko wtedy,
         * gdy obie obserwacje mają tę samą liczbę punktów.
         */
        if (previous == null
                || target == null
                || previous.size()
                != target.size()
                || target.isEmpty()) {

            return target == null
                    ? Collections.emptyList()
                    : new ArrayList<>(
                    target
            );
        }


        List<PointF> result =
                new ArrayList<>(
                        target.size()
                );


        for (int index = 0;
             index < target.size();
             index++) {

            PointF from =
                    previous.get(
                            index
                    );

            PointF to =
                    target.get(
                            index
                    );


            result.add(
                    new PointF(
                            lerp(
                                    from.x,
                                    to.x,
                                    amount
                            ),
                            lerp(
                                    from.y,
                                    to.y,
                                    amount
                            )
                    )
            );
        }


        return result;
    }

    private static float lerp(
            float from,
            float to,
            float amount
    ) {

        return from
                + (to - from)
                * amount;
    }


    private static float iou(
            RectF first,
            RectF second
    ) {

        float intersectionLeft =
                Math.max(
                        first.left,
                        second.left
                );

        float intersectionTop =
                Math.max(
                        first.top,
                        second.top
                );

        float intersectionRight =
                Math.min(
                        first.right,
                        second.right
                );

        float intersectionBottom =
                Math.min(
                        first.bottom,
                        second.bottom
                );


        float intersectionWidth =
                Math.max(
                        0f,
                        intersectionRight
                                - intersectionLeft
                );

        float intersectionHeight =
                Math.max(
                        0f,
                        intersectionBottom
                                - intersectionTop
                );


        float intersection =
                intersectionWidth
                        * intersectionHeight;


        float firstArea =
                Math.max(
                        0f,
                        first.width()
                )
                        * Math.max(
                        0f,
                        first.height()
                );


        float secondArea =
                Math.max(
                        0f,
                        second.width()
                )
                        * Math.max(
                        0f,
                        second.height()
                );


        float union =
                firstArea
                        + secondArea
                        - intersection;


        return union <= 0f
                ? 0f
                : intersection / union;
    }


    private static float centerDistance(
            RectF first,
            RectF second
    ) {

        float dx =
                first.centerX()
                        - second.centerX();

        float dy =
                first.centerY()
                        - second.centerY();


        return (float) Math.hypot(
                dx,
                dy
        );
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        rebuildRenderItems();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Najpierw lekkie ramki i punkty. Badge'e są układane osobno, aby nie
        // przykrywały żadnej z ramek detekcyjnych.
        for (RenderItem renderItem : renderItems) {

            OverlayItem item =
                    renderItem.item;


            /*
             * PLATE:
             * jeżeli mamy komplet czterech keypointów MT,
             * rysujemy rzeczywisty quad.
             *
             * VEHICLE / VEHICLE_ROI:
             * nadal pozostają bboxami.
             */
            if (item.kind == OverlayItem.Kind.PLATE
                    && renderItem.points.size() >= 4) {

                drawPlateQuad(
                        canvas,
                        renderItem
                );

            } else {

                canvas.drawRoundRect(
                        renderItem.bounds,
                        dp(5),
                        dp(5),
                        paintFor(item)
                );
            }
        }

        for (RenderItem renderItem : renderItems) {
            if (renderItem.badge != null) drawLabel(canvas, renderItem);
        }
    }

    private void drawPlateQuad(
            Canvas canvas,
            RenderItem renderItem
    ) {

        OverlayItem item =
                renderItem.item;

        List<PointF> points =
                renderItem.points;


        PointF first =
                points.get(0);

        PointF second =
                points.get(1);

        PointF third =
                points.get(2);

        PointF fourth =
                points.get(3);


        Path quad =
                new Path();


        quad.moveTo(
                first.x,
                first.y
        );

        quad.lineTo(
                second.x,
                second.y
        );

        quad.lineTo(
                third.x,
                third.y
        );

        quad.lineTo(
                fourth.x,
                fourth.y
        );

        quad.close();


        canvas.drawPath(
                quad,
                paintFor(item)
        );


        /*
         * Punkty narożników pokazujemy tylko
         * dla normalnej aktywnej obserwacji.
         */
        if (!item.carriedPrediction) {

            for (int index = 0;
                 index < 4;
                 index++) {

                PointF point =
                        points.get(index);


                canvas.drawCircle(
                        point.x,
                        point.y,
                        dp(2.7f),
                        pointPaint
                );
            }
        }
    }
    private Paint paintFor(OverlayItem item) {
        if (item.carriedPrediction) {
            return predictionPaint;
        }

        if (item.kind == OverlayItem.Kind.VEHICLE) {
            return vehiclePaint;
        }

        if (item.kind == OverlayItem.Kind.VEHICLE_ROI) {
            return vehicleRoiPaint;
        }

        return boxPaint;
    }
    private void drawLabel(Canvas canvas, RenderItem renderItem) {
        RectF badge = renderItem.badge;
        canvas.drawRoundRect(badge, dp(5), dp(5), labelPaint);
        float baseline = badge.centerY() - (
                detectionTextPaint.ascent() + detectionTextPaint.descent()
        ) * 0.5f;
        float textX = badge.left + dp(6);
        canvas.save();
        canvas.clipRect(badge);
        canvas.drawText(
                renderItem.parts.detection,
                textX,
                baseline,
                detectionTextPaint
        );
        if (!renderItem.parts.confidence.isEmpty()) {
            canvas.drawText(
                    renderItem.parts.confidence,
                    textX + renderItem.detectionWidth + dp(5),
                    baseline,
                    confidenceTextPaint
            );
        }
        canvas.restore();
    }

    private void rebuildRenderItems() {
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        if (viewWidth <= 0f || viewHeight <= 0f || items.isEmpty()) {
            renderItems = Collections.emptyList();
            return;
        }
        float imageWidth = sourceWidth > 0 ? sourceWidth : viewWidth;
        float imageHeight = sourceHeight > 0 ? sourceHeight : viewHeight;
        float scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);
        float offsetX = (viewWidth - imageWidth * scale) * 0.5f;
        float offsetY = (viewHeight - imageHeight * scale) * 0.5f;
        List<RenderItem> prepared = new ArrayList<>(items.size());

        /*
         * Przy rozmieszczaniu badge'y tablic unikamy tylko
         * innych ramek tablic.
         *
         * VEHICLE i VEHICLE_ROI są ramkami diagnostycznymi
         * obejmującymi tablicę, więc nie mogą blokować jej napisu.
         */
        List<RectF> plateFrameBounds = new ArrayList<>();

        for (OverlayItem item : items) {
            if (!diagnosticMode && item.kind != OverlayItem.Kind.PLATE) continue;
            RectF source = item.normalizedBounds;
            RectF bounds = new RectF(
                    offsetX + source.left * imageWidth * scale,
                    offsetY + source.top * imageHeight * scale,
                    offsetX + source.right * imageWidth * scale,
                    offsetY + source.bottom * imageHeight * scale
            );
            List<PointF> points = new ArrayList<>(item.normalizedKeypoints.size());
            for (PointF point : item.normalizedKeypoints) {
                points.add(new PointF(
                        offsetX + point.x * imageWidth * scale,
                        offsetY + point.y * imageHeight * scale
                ));
            }
            RenderItem renderItem = new RenderItem(item, bounds, points);
            prepared.add(renderItem);

            if (item.kind == OverlayItem.Kind.PLATE) {
                plateFrameBounds.add(bounds);
            }
        }

        List<RectF> occupiedLabels = new ArrayList<>();
        float horizontalPadding = dp(6);
        float verticalPadding = dp(3.5f);
        float textHeight = Math.max(
                fontHeight(detectionTextPaint),
                fontHeight(confidenceTextPaint)
        );
        for (RenderItem renderItem : prepared) {
            if (renderItem.item.label.isEmpty()) continue;
            if (renderItem.item.kind != OverlayItem.Kind.PLATE
                    || renderItem.item.carriedPrediction) continue;
            if (!diagnosticMode && focusedTrackId > 0L
                    && renderItem.item.trackId != focusedTrackId) continue;
            float confidenceWidth = confidenceTextPaint.measureText(renderItem.parts.confidence);
            float textGap = renderItem.parts.confidence.isEmpty() ? 0f : dp(5);
            float labelWidth = Math.min(
                    viewWidth,
                    renderItem.detectionWidth + confidenceWidth + textGap + horizontalPadding * 2f
            );
            float labelHeight = textHeight + verticalPadding * 2f;
            renderItem.badge = findFreeBadge(
                    renderItem.bounds,
                    labelWidth,
                    labelHeight,
                    plateFrameBounds,
                    occupiedLabels,
                    viewWidth,
                    viewHeight
            );
            if (renderItem.badge != null) occupiedLabels.add(renderItem.badge);
        }
        renderItems = Collections.unmodifiableList(prepared);
    }

    private RectF findFreeBadge(
            RectF owner,
            float width,
            float height,
            List<RectF> frames,
            List<RectF> labels,
            float viewWidth,
            float viewHeight
    ) {
        float gap = dp(5);
        float alignedLeft = clamp(owner.left, 0f, Math.max(0f, viewWidth - width));
        float alignedRight = clamp(owner.right - width, 0f, Math.max(0f, viewWidth - width));
        float alignedTop = clamp(owner.top, 0f, Math.max(0f, viewHeight - height));
        RectF[] candidates = new RectF[]{
                new RectF(alignedLeft, owner.top - gap - height, alignedLeft + width, owner.top - gap),
                new RectF(alignedLeft, owner.bottom + gap, alignedLeft + width, owner.bottom + gap + height),
                new RectF(owner.right + gap, alignedTop, owner.right + gap + width, alignedTop + height),
                new RectF(owner.left - gap - width, alignedTop, owner.left - gap, alignedTop + height),
                new RectF(alignedRight, owner.top - gap - height, alignedRight + width, owner.top - gap),
                new RectF(alignedRight, owner.bottom + gap, alignedRight + width, owner.bottom + gap + height)
        };
        for (RectF candidate : candidates) {
            if (!inside(candidate, viewWidth, viewHeight)) continue;
            if (intersectsAny(candidate, frames, gap * 0.45f)) continue;
            if (intersectsAny(candidate, labels, gap * 0.45f)) continue;
            return candidate;
        }
        return null;
    }

    private static boolean inside(RectF bounds, float width, float height) {
        return bounds.left >= 0f && bounds.top >= 0f
                && bounds.right <= width && bounds.bottom <= height;
    }

    private static boolean intersectsAny(RectF candidate, List<RectF> bounds, float margin) {
        for (RectF value : bounds) {
            RectF expanded = new RectF(value);
            expanded.inset(-margin, -margin);
            if (RectF.intersects(candidate, expanded)) return true;
        }
        return false;
    }

    private static float fontHeight(Paint paint) {
        return paint.descent() - paint.ascent();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private final class RenderItem {
        final OverlayItem item;
        final RectF bounds;
        final List<PointF> points;
        final LabelParts parts;
        final float detectionWidth;
        RectF badge;

        RenderItem(OverlayItem item, RectF bounds, List<PointF> points) {
            this.item = item;
            this.bounds = bounds;
            this.points = points;
            this.parts = LabelParts.parse(item.label);
            this.detectionWidth = detectionTextPaint.measureText(parts.detection);
        }
    }

    static final class LabelParts {
        final String detection;
        final String confidence;

        private LabelParts(String detection, String confidence) {
            this.detection = detection;
            this.confidence = confidence;
        }

        static LabelParts parse(String label) {
            String trimmed = label == null ? "" : label.trim();
            int separator = trimmed.lastIndexOf(' ');
            if (separator > 0) {
                String suffix = trimmed.substring(separator + 1);
                if (suffix.matches("\\d{1,3}%")) {
                    return new LabelParts(trimmed.substring(0, separator), suffix);
                }
            }
            return new LabelParts(trimmed, "");
        }
    }
}
