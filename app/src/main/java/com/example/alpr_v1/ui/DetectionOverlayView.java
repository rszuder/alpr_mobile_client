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
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.graphics.Path;

import androidx.annotation.Nullable;

import com.example.alpr_v1.domain.ScanAcquisitionViewport;
import com.example.alpr_v1.acquisition.EntityRecognitionSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Lekki overlay ramek z badge'ami układanymi poza obszarem detekcji. */
public final class DetectionOverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vehiclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint recognizedVehiclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vehicleRoiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint predictionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detectionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint confidenceTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint calibrationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeVehicleMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint analysisViewportShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint analysisViewportCornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final long OVERLAY_TRANSITION_MS =
            120L;
    private static final long ACTIVE_VEHICLE_MARKER_HALF_CYCLE_MS =
            620L;
    private static final long PLATE_FADE_OUT_MS =
            2_400L;
    private static final long PLATE_ABSORB_MS = 620L;


    /*
     * items reprezentuje aktualnie rysowaną,
     * również pośrednią klatkę animacji.
     */
    private List<OverlayItem> items =
            Collections.emptyList();

    private List<RenderItem> renderItems =
            Collections.emptyList();


    private ValueAnimator overlayAnimator;
    private ValueAnimator activeVehicleMarkerAnimator;
    private ValueAnimator plateFadeAnimator;
    private ValueAnimator plateAbsorbAnimator;
    private List<RenderItem> fadingPlateRenderItems = Collections.emptyList();
    private float fadingPlateAlpha = 1f;
    private AbsorbingPlate absorbingPlate;
    private float plateAbsorbProgress;


    private final DecelerateInterpolator overlayInterpolator =
            new DecelerateInterpolator();


    private int sourceWidth;
    private int sourceHeight;
    private boolean geometryCalibrationEnabled;
    private boolean diagnosticMode;
    private Set<Long> identifiedVehicleEntityIds = Collections.emptySet();
    private Set<Long> completedVehicleEntityIds = Collections.emptySet();
    private Map<Long, EntityRecognitionSnapshot> vehicleRecognitions =
            Collections.emptyMap();
    private Map<Long, EntityRecognitionSnapshot> requestedVehicleRecognitions =
            Collections.emptyMap();
    private final Set<Long> absorbedPlateTrackIds = new HashSet<>();
    private long focusedTrackId;
    private long activeVehicleEntityId;
    private RectF activeVehicleNormalizedBounds;
    private long activeVehicleBoundsFreshAtNanos;
    private long activeVehicleGeometryMaximumAgeNanos = 500_000_000L;
    private float activeVehicleMarkerProgress;
    private boolean analysisViewportEnabled;

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

        recognizedVehiclePaint.setColor(Color.argb(245, 52, 211, 153));
        recognizedVehiclePaint.setStyle(Paint.Style.STROKE);
        recognizedVehiclePaint.setStrokeWidth(dp(2.2f));

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
        calibrationPaint.setColor(Color.rgb(250, 204, 21));
        calibrationPaint.setStyle(Paint.Style.STROKE);
        calibrationPaint.setStrokeWidth(dp(1.5f));
        activeVehicleMarkerPaint.setColor(Color.rgb(250, 204, 21));
        activeVehicleMarkerPaint.setStyle(Paint.Style.FILL);
        analysisViewportShadePaint.setColor(Color.argb(54, 0, 0, 0));
        analysisViewportShadePaint.setStyle(Paint.Style.FILL);
        analysisViewportCornerPaint.setColor(Color.argb(185, 125, 211, 252));
        analysisViewportCornerPaint.setStyle(Paint.Style.STROKE);
        analysisViewportCornerPaint.setStrokeWidth(dp(1.4f));
        setWillNotDraw(false);
    }

    public void setAnalysisViewportEnabled(boolean enabled) {
        if (analysisViewportEnabled == enabled) return;
        analysisViewportEnabled = enabled;
        postInvalidateOnAnimation();
    }

    public void setGeometryCalibrationEnabled(boolean enabled) {
        geometryCalibrationEnabled = enabled;
        invalidate();
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
        trackedPlates = withoutAbsorbedPlates(trackedPlates);
        if (trackedPlates == null || trackedPlates.isEmpty()) return;

        removeReappearedFadingPlates(trackedPlates);

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

    /** Natychmiast usuwa PLATE, np. podczas STOP lub pełnego resetu sceny. */
    public void clearPlateItems() {
        cancelPlateFade();
        if (overlayAnimator != null) {
            overlayAnimator.cancel();
            overlayAnimator = null;
        }
        List<OverlayItem> updated = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            if (item.kind != OverlayItem.Kind.PLATE) updated.add(item);
        }
        if (updated.size() == items.size()) return;
        items = Collections.unmodifiableList(updated);
        rebuildRenderItems();
        postInvalidateOnAnimation();
    }

    /** Łagodnie wygasza nieświeżą warstwę PLATE bez zamrażania geometrii MP. */
    public void fadeOutPlateItems() {
        fadeOutPlateItems(Collections.emptyList());
    }

    /** Wygasza wyłącznie tablice nieobecne już w zbiorze nadal świeżych ramek. */
    public void fadeOutPlateItems(List<OverlayItem> retainedPlates) {
        List<OverlayItem> safeRetained = new ArrayList<>();
        if (retainedPlates != null) {
            for (OverlayItem item : retainedPlates) {
                if (item != null && item.kind == OverlayItem.Kind.PLATE) {
                    safeRetained.add(item);
                }
            }
        }

        float startAlpha = 1f;
        List<RenderItem> fading = new ArrayList<>();
        if (plateFadeAnimator != null) {
            startAlpha = fadingPlateAlpha;
            for (RenderItem renderItem : fadingPlateRenderItems) {
                if (!containsPlateTrack(safeRetained, renderItem.item.trackId)) {
                    fading.add(renderItem);
                }
            }
            ValueAnimator previousAnimator = plateFadeAnimator;
            plateFadeAnimator = null;
            previousAnimator.cancel();
        }
        for (RenderItem renderItem : renderItems) {
            if (renderItem.item.kind == OverlayItem.Kind.PLATE
                    && !containsPlateTrack(safeRetained, renderItem.item.trackId)
                    && !containsRenderTrack(fading, renderItem.item.trackId)) {
                fading.add(renderItem);
            }
        }
        if (fading.isEmpty()) {
            if (safeRetained.isEmpty()) {
                clearPlateItems();
            } else {
                setTrackedPlateItems(safeRetained);
            }
            return;
        }

        if (overlayAnimator != null) {
            overlayAnimator.cancel();
            overlayAnimator = null;
        }
        fadingPlateRenderItems = Collections.unmodifiableList(fading);
        List<OverlayItem> retained = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
            if (item.kind != OverlayItem.Kind.PLATE) retained.add(item);
        }
        retained.addAll(safeRetained);
        items = Collections.unmodifiableList(retained);
        rebuildRenderItems();

        fadingPlateAlpha = startAlpha;
        final ValueAnimator animator = ValueAnimator.ofFloat(startAlpha, 0f);
        plateFadeAnimator = animator;
        animator.setDuration(Math.max(
                1L,
                Math.round(PLATE_FADE_OUT_MS * startAlpha)
        ));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            fadingPlateAlpha = (float) valueAnimator.getAnimatedValue();
            postInvalidateOnAnimation();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (plateFadeAnimator != animator) return;
                plateFadeAnimator = null;
                fadingPlateRenderItems = Collections.emptyList();
                fadingPlateAlpha = 1f;
                postInvalidateOnAnimation();
            }
        });
        animator.start();
    }

    private void cancelPlateFade() {
        ValueAnimator animator = plateFadeAnimator;
        plateFadeAnimator = null;
        if (animator != null) animator.cancel();
        fadingPlateRenderItems = Collections.emptyList();
        fadingPlateAlpha = 1f;
    }

    private void removeReappearedFadingPlates(List<OverlayItem> freshItems) {
        if (fadingPlateRenderItems.isEmpty()
                || !containsFreshPlateItem(freshItems)) return;
        List<RenderItem> retained = new ArrayList<>();
        for (RenderItem fading : fadingPlateRenderItems) {
            if (!containsFreshPlateTrack(freshItems, fading.item.trackId)) {
                retained.add(fading);
            }
        }
        if (retained.size() == fadingPlateRenderItems.size()) return;
        if (retained.isEmpty()) {
            cancelPlateFade();
        } else {
            fadingPlateRenderItems = Collections.unmodifiableList(retained);
            postInvalidateOnAnimation();
        }
    }

    /** Bezanimacyjna klatka Preview zawierajaca juz komplet warstw. */
    public void setPreviewItems(List<OverlayItem> previewItems) {
        previewItems = withoutAbsorbedPlates(previewItems);
        removeReappearedFadingPlates(previewItems);
        if (overlayAnimator != null) {
            overlayAnimator.cancel();
            overlayAnimator = null;
        }
        items = Collections.unmodifiableList(
                withoutCarriedFadingPlates(previewItems)
        );
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

        List<OverlayItem> incomingItems = new ArrayList<>(
                newItems == null ? Collections.emptyList() : newItems
        );
        incomingItems = withoutAbsorbedPlates(incomingItems);
        removeReappearedFadingPlates(incomingItems);
        List<OverlayItem> targetItems = Collections.unmodifiableList(
                withoutCarriedFadingPlates(incomingItems)
        );

        if (targetItems.isEmpty() && !analysisViewportEnabled) {
            cancelPlateFade();
        }


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

    public void setVehicleEntityStates(
            Set<Long> identifiedEntityIds,
            Set<Long> completedEntityIds
    ) {
        setVehicleEntityStates(
                identifiedEntityIds,
                completedEntityIds,
                Collections.emptyMap()
        );
    }

    /** Aktualizuje wyłącznie spokojny status kolejki, bez konsumowania wyniku OCR. */
    public void setVehicleEntityProgress(
            Set<Long> identifiedEntityIds,
            Set<Long> completedEntityIds
    ) {
        Set<Long> safeIdentified = positiveEntityIds(identifiedEntityIds);
        Set<Long> safeCompleted = positiveEntityIds(completedEntityIds);
        if (identifiedVehicleEntityIds.equals(safeIdentified)
                && completedVehicleEntityIds.equals(safeCompleted)) return;
        identifiedVehicleEntityIds = Collections.unmodifiableSet(safeIdentified);
        completedVehicleEntityIds = Collections.unmodifiableSet(safeCompleted);
        rebuildRenderItems();
        postInvalidateOnAnimation();
    }

    public void setVehicleEntityStates(
            Set<Long> identifiedEntityIds,
            Set<Long> completedEntityIds,
            Map<Long, EntityRecognitionSnapshot> recognitions
    ) {
        Set<Long> safeIdentified = positiveEntityIds(identifiedEntityIds);
        Set<Long> safeCompleted = positiveEntityIds(completedEntityIds);
        Map<Long, EntityRecognitionSnapshot> safeRecognitions =
                positiveRecognitions(recognitions);
        if (identifiedVehicleEntityIds.equals(safeIdentified)
                && completedVehicleEntityIds.equals(safeCompleted)
                && sameRecognitions(requestedVehicleRecognitions, safeRecognitions)) return;

        EntityRecognitionSnapshot incomingRecognition = firstTransferRecognition(
                requestedVehicleRecognitions,
                safeRecognitions
        );
        RenderItem sourcePlate = incomingRecognition == null
                ? null : findPlateRenderItem(incomingRecognition.plateTrackId);
        identifiedVehicleEntityIds = Collections.unmodifiableSet(safeIdentified);
        completedVehicleEntityIds = Collections.unmodifiableSet(safeCompleted);
        requestedVehicleRecognitions = Collections.unmodifiableMap(
                new HashMap<>(safeRecognitions)
        );
        Map<Long, EntityRecognitionSnapshot> displayedRecognitions =
                new HashMap<>(safeRecognitions);
        if (incomingRecognition != null && sourcePlate != null) {
            // Numer pojawi się w polu pojazdu dopiero po dotarciu animacji.
            displayedRecognitions.remove(incomingRecognition.entityId);
        }
        vehicleRecognitions = Collections.unmodifiableMap(displayedRecognitions);
        if (safeRecognitions.isEmpty()) {
            absorbedPlateTrackIds.clear();
            cancelPlateAbsorption();
        } else {
            for (EntityRecognitionSnapshot recognition : safeRecognitions.values()) {
                if (recognition.plateTrackId <= 0L) continue;
                absorbedPlateTrackIds.add(recognition.plateTrackId);
                removeAbsorbedPlateLayers(recognition.plateTrackId);
            }
        }
        rebuildRenderItems();
        if (incomingRecognition != null && sourcePlate != null) {
            startPlateAbsorption(incomingRecognition, sourcePlate.bounds);
        }
        postInvalidateOnAnimation();
    }

    private static Map<Long, EntityRecognitionSnapshot> positiveRecognitions(
            Map<Long, EntityRecognitionSnapshot> recognitions
    ) {
        Map<Long, EntityRecognitionSnapshot> safe = new HashMap<>();
        if (recognitions == null) return safe;
        for (Map.Entry<Long, EntityRecognitionSnapshot> entry : recognitions.entrySet()) {
            EntityRecognitionSnapshot value = entry.getValue();
            if (entry.getKey() != null && entry.getKey() > 0L
                    && value != null && !value.text.isEmpty()) {
                safe.put(entry.getKey(), value);
            }
        }
        return safe;
    }

    private static boolean sameRecognitions(
            Map<Long, EntityRecognitionSnapshot> first,
            Map<Long, EntityRecognitionSnapshot> second
    ) {
        if (first.size() != second.size()) return false;
        for (Map.Entry<Long, EntityRecognitionSnapshot> entry : first.entrySet()) {
            EntityRecognitionSnapshot other = second.get(entry.getKey());
            EntityRecognitionSnapshot value = entry.getValue();
            if (other == null
                    || !value.text.equals(other.text)
                    || value.plateTrackId != other.plateTrackId
                    || value.confirmed != other.confirmed
                    || Math.abs(value.confidence - other.confidence) > 0.0001) {
                return false;
            }
        }
        return true;
    }

    private static EntityRecognitionSnapshot firstTransferRecognition(
            Map<Long, EntityRecognitionSnapshot> previous,
            Map<Long, EntityRecognitionSnapshot> current
    ) {
        for (Map.Entry<Long, EntityRecognitionSnapshot> entry : current.entrySet()) {
            EntityRecognitionSnapshot old = previous.get(entry.getKey());
            if (old == null) {
                return entry.getValue();
            }
        }
        return null;
    }

    private RenderItem findPlateRenderItem(long plateTrackId) {
        if (plateTrackId <= 0L) return null;
        for (RenderItem item : renderItems) {
            if (item.item.kind == OverlayItem.Kind.PLATE
                    && item.item.trackId == plateTrackId) return item;
        }
        for (RenderItem item : fadingPlateRenderItems) {
            if (item.item.trackId == plateTrackId) return item;
        }
        return null;
    }

    private void removeAbsorbedPlateLayers(long plateTrackId) {
        List<OverlayItem> retained = new ArrayList<>();
        for (OverlayItem item : items) {
            if (item.kind != OverlayItem.Kind.PLATE
                    || item.trackId != plateTrackId) retained.add(item);
        }
        items = Collections.unmodifiableList(retained);
        List<RenderItem> fading = new ArrayList<>();
        for (RenderItem item : fadingPlateRenderItems) {
            if (item.item.trackId != plateTrackId) fading.add(item);
        }
        fadingPlateRenderItems = Collections.unmodifiableList(fading);
        if (fading.isEmpty() && plateFadeAnimator != null) cancelPlateFade();
    }

    private List<OverlayItem> withoutAbsorbedPlates(List<OverlayItem> source) {
        if (source == null || source.isEmpty() || absorbedPlateTrackIds.isEmpty()) {
            return source == null ? Collections.emptyList() : new ArrayList<>(source);
        }
        List<OverlayItem> filtered = new ArrayList<>(source.size());
        for (OverlayItem item : source) {
            if (item == null) continue;
            if (item.kind != OverlayItem.Kind.PLATE
                    || !absorbedPlateTrackIds.contains(item.trackId)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private void startPlateAbsorption(
            EntityRecognitionSnapshot recognition,
            RectF sourceBounds
    ) {
        cancelPlateAbsorption();
        absorbingPlate = new AbsorbingPlate(recognition, new RectF(sourceBounds));
        plateAbsorbProgress = 0f;
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        plateAbsorbAnimator = animator;
        animator.setDuration(PLATE_ABSORB_MS);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            plateAbsorbProgress = (float) valueAnimator.getAnimatedValue();
            postInvalidateOnAnimation();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (plateAbsorbAnimator != animator) return;
                plateAbsorbAnimator = null;
                EntityRecognitionSnapshot completed = absorbingPlate == null
                        ? null : absorbingPlate.recognition;
                absorbingPlate = null;
                plateAbsorbProgress = 0f;
                if (completed != null) {
                    EntityRecognitionSnapshot requested =
                            requestedVehicleRecognitions.get(completed.entityId);
                    if (requested != null
                            && requested.plateTrackId == completed.plateTrackId) {
                        Map<Long, EntityRecognitionSnapshot> committed =
                                new HashMap<>(vehicleRecognitions);
                        committed.put(completed.entityId, requested);
                        vehicleRecognitions = Collections.unmodifiableMap(committed);
                        rebuildRenderItems();
                    }
                }
                postInvalidateOnAnimation();
            }
        });
        animator.start();
    }

    private void cancelPlateAbsorption() {
        ValueAnimator animator = plateAbsorbAnimator;
        plateAbsorbAnimator = null;
        if (animator != null) animator.cancel();
        absorbingPlate = null;
        plateAbsorbProgress = 0f;
    }

    private static Set<Long> positiveEntityIds(Set<Long> entityIds) {
        Set<Long> safe = new HashSet<>();
        if (entityIds != null) {
            for (Long entityId : entityIds) {
                if (entityId != null && entityId > 0L) safe.add(entityId);
            }
        }
        return safe;
    }

    public void setFocusedTrackId(long trackId) {
        long safeTrackId = Math.max(0L, trackId);
        if (focusedTrackId == safeTrackId) return;
        focusedTrackId = safeTrackId;
        rebuildRenderItems();
        postInvalidateOnAnimation();
    }

    /**
     * Wskazuje pojazd aktualnie analizowany przez Scan. Znacznik korzysta z
     * ostatniej znanej geometrii MP, dlatego pozostaje widoczny również wtedy,
     * gdy diagnostyczna ramka VEHICLE jest ukryta.
     */
    public void setActiveVehicleEntityId(long entityId) {
        long safeEntityId = Math.max(0L, entityId);
        if (activeVehicleEntityId != safeEntityId) {
            activeVehicleEntityId = safeEntityId;
            activeVehicleNormalizedBounds = null;
            activeVehicleBoundsFreshAtNanos = 0L;
            refreshActiveVehicleBounds();
            rebuildRenderItems();
        }
        if (activeVehicleEntityId > 0L) {
            ensureActiveVehicleMarkerAnimator();
        } else {
            stopActiveVehicleMarkerAnimator();
        }
        postInvalidateOnAnimation();
    }

    public void setActiveVehicleGeometryMaximumAgeNanos(long maximumAgeNanos) {
        activeVehicleGeometryMaximumAgeNanos = Math.max(0L, maximumAgeNanos);
        postInvalidateOnAnimation();
    }

    private void refreshActiveVehicleBounds() {
        if (activeVehicleEntityId <= 0L) {
            activeVehicleNormalizedBounds = null;
            activeVehicleBoundsFreshAtNanos = 0L;
            return;
        }
        OverlayItem roiFallback = null;
        for (OverlayItem item : items) {
            if (item == null || item.trackId != activeVehicleEntityId) continue;
            if (item.kind == OverlayItem.Kind.VEHICLE) {
                activeVehicleNormalizedBounds = new RectF(item.normalizedBounds);
                activeVehicleBoundsFreshAtNanos =
                        android.os.SystemClock.elapsedRealtimeNanos();
                return;
            }
            if (item.kind == OverlayItem.Kind.VEHICLE_ROI) roiFallback = item;
        }
        if (roiFallback != null) {
            activeVehicleNormalizedBounds = new RectF(roiFallback.normalizedBounds);
            activeVehicleBoundsFreshAtNanos =
                    android.os.SystemClock.elapsedRealtimeNanos();
        }
        // Brak geometrii w bieżącej klatce nie usuwa ostatniej pozycji celu.
    }

    private void ensureActiveVehicleMarkerAnimator() {
        if (!isAttachedToWindow() || activeVehicleMarkerAnimator != null) return;
        activeVehicleMarkerAnimator = ValueAnimator.ofFloat(0f, 1f);
        activeVehicleMarkerAnimator.setDuration(ACTIVE_VEHICLE_MARKER_HALF_CYCLE_MS);
        activeVehicleMarkerAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        activeVehicleMarkerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        activeVehicleMarkerAnimator.setRepeatMode(ValueAnimator.REVERSE);
        activeVehicleMarkerAnimator.addUpdateListener(animator -> {
            activeVehicleMarkerProgress = (float) animator.getAnimatedValue();
            postInvalidateOnAnimation();
        });
        activeVehicleMarkerAnimator.start();
    }

    private void stopActiveVehicleMarkerAnimator() {
        if (activeVehicleMarkerAnimator != null) {
            activeVehicleMarkerAnimator.cancel();
            activeVehicleMarkerAnimator = null;
        }
        activeVehicleMarkerProgress = 0f;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (activeVehicleEntityId > 0L) ensureActiveVehicleMarkerAnimator();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopActiveVehicleMarkerAnimator();
        cancelPlateFade();
        cancelPlateAbsorption();
        if (overlayAnimator != null) {
            overlayAnimator.cancel();
            overlayAnimator = null;
        }
        super.onDetachedFromWindow();
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

        int imageWidth = sourceWidth > 0 ? sourceWidth : Math.round(viewWidth);
        int imageHeight = sourceHeight > 0 ? sourceHeight : Math.round(viewHeight);
        return OverlayViewportTransform.mapNormalizedToView(
                normalizedX,
                normalizedY,
                imageWidth,
                imageHeight,
                Math.round(viewWidth),
                Math.round(viewHeight)
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

        int imageWidth = sourceWidth > 0 ? sourceWidth : Math.round(viewWidth);
        int imageHeight = sourceHeight > 0 ? sourceHeight : Math.round(viewHeight);
        PointF topLeft = OverlayViewportTransform.mapViewToNormalized(
                new PointF(0f, 0f),
                imageWidth,
                imageHeight,
                Math.round(viewWidth),
                Math.round(viewHeight)
        );
        PointF bottomRight = OverlayViewportTransform.mapViewToNormalized(
                new PointF(viewWidth, viewHeight),
                imageWidth,
                imageHeight,
                Math.round(viewWidth),
                Math.round(viewHeight)
        );
        return new RectF(
                topLeft.x,
                topLeft.y,
                bottomRight.x,
                bottomRight.y
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
        drawAnalysisViewport(canvas);
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

        drawFadingPlateLayer(canvas);

        drawPlateAbsorption(canvas);

        drawActiveVehicleMarker(canvas);

        for (RenderItem renderItem : renderItems) {
            if (renderItem.badge != null) drawLabel(canvas, renderItem);
        }
        if (geometryCalibrationEnabled) drawGeometryCalibration(canvas);
    }

    private void drawAnalysisViewport(Canvas canvas) {
        RectF bounds = analysisViewportViewBounds();
        if (!analysisViewportEnabled || bounds == null) return;

        canvas.drawRect(0f, 0f, getWidth(), bounds.top, analysisViewportShadePaint);
        canvas.drawRect(0f, bounds.bottom, getWidth(), getHeight(), analysisViewportShadePaint);
        canvas.drawRect(0f, bounds.top, bounds.left, bounds.bottom,
                analysisViewportShadePaint);
        canvas.drawRect(bounds.right, bounds.top, getWidth(), bounds.bottom,
                analysisViewportShadePaint);

        float corner = Math.min(dp(18f), Math.min(bounds.width(), bounds.height()) * 0.10f);
        canvas.drawLine(bounds.left, bounds.top, bounds.left + corner, bounds.top,
                analysisViewportCornerPaint);
        canvas.drawLine(bounds.left, bounds.top, bounds.left, bounds.top + corner,
                analysisViewportCornerPaint);
        canvas.drawLine(bounds.right, bounds.top, bounds.right - corner, bounds.top,
                analysisViewportCornerPaint);
        canvas.drawLine(bounds.right, bounds.top, bounds.right, bounds.top + corner,
                analysisViewportCornerPaint);
        canvas.drawLine(bounds.left, bounds.bottom, bounds.left + corner, bounds.bottom,
                analysisViewportCornerPaint);
        canvas.drawLine(bounds.left, bounds.bottom, bounds.left, bounds.bottom - corner,
                analysisViewportCornerPaint);
        canvas.drawLine(bounds.right, bounds.bottom, bounds.right - corner, bounds.bottom,
                analysisViewportCornerPaint);
        canvas.drawLine(bounds.right, bounds.bottom, bounds.right, bounds.bottom - corner,
                analysisViewportCornerPaint);
    }

    private RectF analysisViewportViewBounds() {
        if (getWidth() <= 0 || getHeight() <= 0
                || sourceWidth <= 0 || sourceHeight <= 0) return null;
        return OverlayViewportTransform.mapNormalizedToView(
                new RectF(
                        ScanAcquisitionViewport.BOUNDS.left,
                        ScanAcquisitionViewport.BOUNDS.top,
                        ScanAcquisitionViewport.BOUNDS.right,
                        ScanAcquisitionViewport.BOUNDS.bottom
                ),
                sourceWidth,
                sourceHeight,
                getWidth(),
                getHeight()
        );
    }

    private void drawFadingPlateLayer(Canvas canvas) {
        if (fadingPlateRenderItems.isEmpty() || fadingPlateAlpha <= 0f) return;
        int saveCount = canvas.saveLayerAlpha(
                0f,
                0f,
                getWidth(),
                getHeight(),
                Math.round(255f * fadingPlateAlpha)
        );
        for (RenderItem renderItem : fadingPlateRenderItems) {
            if (renderItem.points.size() >= 4) {
                drawPlateQuad(canvas, renderItem);
            } else {
                canvas.drawRoundRect(
                        renderItem.bounds,
                        dp(5),
                        dp(5),
                        paintFor(renderItem.item)
                );
            }
        }
        for (RenderItem renderItem : fadingPlateRenderItems) {
            if (renderItem.badge != null) drawLabel(canvas, renderItem);
        }
        canvas.restoreToCount(saveCount);
    }

    private void drawPlateAbsorption(Canvas canvas) {
        if (absorbingPlate == null) return;
        RectF target = recognitionTargetBounds(absorbingPlate.recognition.entityId);
        if (target == null) return;
        float progress = Math.max(0f, Math.min(1f, plateAbsorbProgress));
        RectF moving = interpolateRect(absorbingPlate.sourceBounds, target, progress);
        int saveCount = canvas.saveLayerAlpha(
                0f, 0f, getWidth(), getHeight(),
                Math.round(255f * (1f - progress * 0.25f))
        );
        canvas.drawRoundRect(moving, dp(5), dp(5), boxPaint);
        if (progress < 0.72f) {
            String text = absorbingPlate.recognition.text + " · "
                    + Math.round(absorbingPlate.recognition.confidence * 100.0) + "%";
            float labelHeight = fontHeight(detectionTextPaint) + dp(7f);
            float labelWidth = Math.min(
                    getWidth(),
                    detectionTextPaint.measureText(text) + dp(12f)
            );
            RectF badge = new RectF(
                    Math.max(0f, Math.min(getWidth() - labelWidth, moving.left)),
                    Math.max(0f, moving.top - labelHeight - dp(3f)),
                    0f,
                    0f
            );
            badge.right = badge.left + labelWidth;
            badge.bottom = badge.top + labelHeight;
            canvas.drawRoundRect(badge, dp(5), dp(5), labelPaint);
            float baseline = badge.centerY()
                    - (detectionTextPaint.ascent() + detectionTextPaint.descent()) * 0.5f;
            canvas.drawText(text, badge.left + dp(6f), baseline, detectionTextPaint);
        }
        canvas.restoreToCount(saveCount);
    }

    private RectF recognitionTargetBounds(long entityId) {
        for (RenderItem item : renderItems) {
            if (item.item.kind == OverlayItem.Kind.VEHICLE
                    && item.item.trackId == entityId) {
                if (item.badge != null) return new RectF(item.badge);
                float width = Math.min(dp(110f), item.bounds.width());
                return new RectF(
                        item.bounds.left,
                        item.bounds.top,
                        item.bounds.left + width,
                        item.bounds.top + dp(24f)
                );
            }
        }
        return null;
    }

    private static RectF interpolateRect(RectF start, RectF end, float progress) {
        return new RectF(
                start.left + (end.left - start.left) * progress,
                start.top + (end.top - start.top) * progress,
                start.right + (end.right - start.right) * progress,
                start.bottom + (end.bottom - start.bottom) * progress
        );
    }

    private void drawActiveVehicleMarker(Canvas canvas) {
        PointF tip = activeVehicleMarkerTip(activeVehicleMarkerProgress);
        if (tip == null) return;

        float halfWidth = dp(8f);
        float height = dp(10f);
        Path triangle = new Path();
        triangle.moveTo(tip.x, tip.y);
        triangle.lineTo(tip.x + halfWidth, tip.y - height);
        triangle.lineTo(tip.x - halfWidth, tip.y - height);
        triangle.close();
        canvas.drawPath(triangle, activeVehicleMarkerPaint);
    }

    private PointF activeVehicleMarkerTip(float progress) {
        if (activeVehicleEntityId <= 0L
                || activeVehicleNormalizedBounds == null
                || !activeVehicleGeometryFresh()
                || getWidth() <= 0
                || getHeight() <= 0) {
            return null;
        }
        int imageWidth = sourceWidth > 0 ? sourceWidth : getWidth();
        int imageHeight = sourceHeight > 0 ? sourceHeight : getHeight();
        RectF bounds = OverlayViewportTransform.mapNormalizedToView(
                activeVehicleNormalizedBounds,
                imageWidth,
                imageHeight,
                getWidth(),
                getHeight()
        );
        float halfWidth = dp(8f);
        float height = dp(10f);
        float awayDistance = dp(10f);
        float centerX = Math.max(
                halfWidth,
                Math.min(getWidth() - halfWidth, bounds.centerX())
        );
        float safeProgress = Math.max(0f, Math.min(1f, progress));
        float tipY = bounds.top - awayDistance * (1f - safeProgress);
        tipY = Math.max(height, Math.min(getHeight(), tipY));
        return new PointF(centerX, tipY);
    }

    private boolean activeVehicleGeometryFresh() {
        long freshAt = activeVehicleBoundsFreshAtNanos;
        if (freshAt <= 0L) return false;
        long age = Math.max(
                0L,
                android.os.SystemClock.elapsedRealtimeNanos() - freshAt
        );
        return age <= activeVehicleGeometryMaximumAgeNanos;
    }

    private void drawGeometryCalibration(Canvas canvas) {
        float[][] markers = new float[][]{
                {0.50f, 0.50f},
                {0.05f, 0.05f},
                {0.95f, 0.05f},
                {0.05f, 0.95f},
                {0.95f, 0.95f}
        };
        for (float[] marker : markers) {
            PointF point = normalizedToViewPoint(marker[0], marker[1]);
            float radius = dp(7f);
            canvas.drawCircle(point.x, point.y, radius, calibrationPaint);
            canvas.drawLine(
                    point.x - radius * 1.5f,
                    point.y,
                    point.x + radius * 1.5f,
                    point.y,
                    calibrationPaint
            );
            canvas.drawLine(
                    point.x,
                    point.y - radius * 1.5f,
                    point.x,
                    point.y + radius * 1.5f,
                    calibrationPaint
            );
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
        if (item.kind == OverlayItem.Kind.VEHICLE
                && vehicleRecognitions.containsKey(item.trackId)) {
            return recognizedVehiclePaint;
        }
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
        refreshActiveVehicleBounds();
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        if (viewWidth <= 0f || viewHeight <= 0f || items.isEmpty()) {
            renderItems = Collections.emptyList();
            return;
        }
        int imageWidth = sourceWidth > 0 ? sourceWidth : Math.round(viewWidth);
        int imageHeight = sourceHeight > 0 ? sourceHeight : Math.round(viewHeight);
        if (geometryCalibrationEnabled) {
            PointF center = OverlayViewportTransform.mapNormalizedToView(
                    0.5f,
                    0.5f,
                    imageWidth,
                    imageHeight,
                    Math.round(viewWidth),
                    Math.round(viewHeight)
            );
            android.util.Log.d(
                    "ALPR_GEOMETRY",
                    "overlay source=" + imageWidth + "x" + imageHeight
                            + " view=" + Math.round(viewWidth) + "x"
                            + Math.round(viewHeight)
                            + " mapped_center=" + center.x + "," + center.y
            );
        }
        List<RenderItem> prepared = new ArrayList<>(items.size());

        /*
         * Przy rozmieszczaniu badge'y tablic unikamy tylko
         * innych ramek tablic.
         *
         * VEHICLE i VEHICLE_ROI są ramkami diagnostycznymi
         * obejmującymi tablicę, więc nie mogą blokować jej napisu.
         */
        List<RectF> plateFrameBounds = new ArrayList<>();

        for (OverlayItem item : orderedForRendering(items)) {
            if (!diagnosticMode && item.kind == OverlayItem.Kind.VEHICLE_ROI) continue;
            RectF bounds = OverlayViewportTransform.mapNormalizedToView(
                    item.normalizedBounds,
                    imageWidth,
                    imageHeight,
                    Math.round(viewWidth),
                    Math.round(viewHeight)
            );
            List<PointF> points = new ArrayList<>(item.normalizedKeypoints.size());
            for (PointF point : item.normalizedKeypoints) {
                points.add(OverlayViewportTransform.mapNormalizedToView(
                        point,
                        imageWidth,
                        imageHeight,
                        Math.round(viewWidth),
                        Math.round(viewHeight)
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
            boolean vehicleLabel = renderItem.item.kind == OverlayItem.Kind.VEHICLE
                    && renderItem.item.trackId > 0L;
            boolean plateLabel = renderItem.item.kind == OverlayItem.Kind.PLATE
                    && !renderItem.item.carriedPrediction;
            if (!vehicleLabel && !plateLabel) continue;
            if (plateLabel && renderItem.item.label.isEmpty()) continue;
            if (plateLabel && !diagnosticMode && focusedTrackId > 0L
                    && renderItem.item.trackId != focusedTrackId) continue;
            float confidenceWidth = confidenceTextPaint.measureText(renderItem.parts.confidence);
            float textGap = renderItem.parts.confidence.isEmpty() ? 0f : dp(5);
            float labelWidth = Math.min(
                    viewWidth,
                    renderItem.detectionWidth + confidenceWidth + textGap + horizontalPadding * 2f
            );
            float labelHeight = textHeight + verticalPadding * 2f;
            renderItem.badge = vehicleLabel
                    ? findVehicleBadge(
                            renderItem.bounds,
                            labelWidth,
                            labelHeight,
                            occupiedLabels,
                            viewWidth,
                            viewHeight
                    )
                    : findFreeBadge(
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

    List<RectF> snapshotRenderBoundsForTesting() {
        List<RectF> bounds = new ArrayList<>(renderItems.size());
        for (RenderItem item : renderItems) bounds.add(new RectF(item.bounds));
        return Collections.unmodifiableList(bounds);
    }

    RectF snapshotActiveVehicleBoundsForTesting() {
        return activeVehicleNormalizedBounds == null
                || !activeVehicleGeometryFresh()
                ? null
                : new RectF(activeVehicleNormalizedBounds);
    }

    PointF activeVehicleMarkerTipForTesting(float progress) {
        PointF tip = activeVehicleMarkerTip(progress);
        return tip == null ? null : new PointF(tip.x, tip.y);
    }

    int fadingPlateCountForTesting() {
        return fadingPlateRenderItems.size();
    }

    int renderedKindCountForTesting(OverlayItem.Kind kind) {
        int count = 0;
        for (RenderItem item : renderItems) {
            if (item.item.kind == kind) count++;
        }
        return count;
    }

    String vehicleLabelForTesting(long entityId) {
        for (RenderItem item : renderItems) {
            if (item.item.kind == OverlayItem.Kind.VEHICLE
                    && item.item.trackId == entityId) {
                return item.parts.detection
                        + (item.parts.confidence.isEmpty()
                        ? "" : " · " + item.parts.confidence);
            }
        }
        return "";
    }

    RectF vehicleBadgeForTesting(long entityId) {
        for (RenderItem item : renderItems) {
            if (item.item.kind == OverlayItem.Kind.VEHICLE
                    && item.item.trackId == entityId
                    && item.badge != null) return new RectF(item.badge);
        }
        return null;
    }

    RectF vehicleBoundsForTesting(long entityId) {
        for (RenderItem item : renderItems) {
            if (item.item.kind == OverlayItem.Kind.VEHICLE
                    && item.item.trackId == entityId) return new RectF(item.bounds);
        }
        return null;
    }

    boolean recognizedVehicleForTesting(long entityId) {
        return vehicleRecognitions.containsKey(entityId);
    }

    boolean confirmedVehicleForTesting(long entityId) {
        EntityRecognitionSnapshot recognition = vehicleRecognitions.get(entityId);
        return recognition != null && recognition.confirmed;
    }

    long plateAbsorptionEntityForTesting() {
        return absorbingPlate == null ? 0L : absorbingPlate.recognition.entityId;
    }

    boolean absorbedPlateTrackForTesting(long trackId) {
        return absorbedPlateTrackIds.contains(trackId);
    }

    void finishPlateAbsorptionForTesting() {
        if (plateAbsorbAnimator != null) plateAbsorbAnimator.end();
    }

    float fadingPlateAlphaForTesting() {
        return fadingPlateAlpha;
    }

    RectF analysisViewportBoundsForTesting() {
        RectF bounds = analysisViewportViewBounds();
        return bounds == null ? null : new RectF(bounds);
    }

    private List<OverlayItem> withoutCarriedFadingPlates(
            List<OverlayItem> source
    ) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        List<OverlayItem> visible = new ArrayList<>(source.size());
        for (OverlayItem item : source) {
            if (item != null
                    && item.kind == OverlayItem.Kind.PLATE
                    && item.carriedPrediction
                    && containsRenderTrack(
                            fadingPlateRenderItems,
                            item.trackId
                    )) {
                continue;
            }
            if (item != null) visible.add(item);
        }
        return visible;
    }

    private static boolean containsFreshPlateItem(List<OverlayItem> source) {
        if (source == null) return false;
        for (OverlayItem item : source) {
            if (item != null
                    && item.kind == OverlayItem.Kind.PLATE
                    && !item.carriedPrediction) return true;
        }
        return false;
    }

    private static boolean containsFreshPlateTrack(
            List<OverlayItem> source,
            long trackId
    ) {
        if (source == null || trackId <= 0L) return false;
        for (OverlayItem item : source) {
            if (item != null
                    && item.kind == OverlayItem.Kind.PLATE
                    && !item.carriedPrediction
                    && item.trackId == trackId) return true;
        }
        return false;
    }

    private static boolean containsPlateTrack(List<OverlayItem> source, long trackId) {
        if (trackId <= 0L) return false;
        for (OverlayItem item : source) {
            if (item.trackId == trackId) return true;
        }
        return false;
    }

    private static boolean containsRenderTrack(List<RenderItem> source, long trackId) {
        if (trackId <= 0L) return false;
        for (RenderItem item : source) {
            if (item.item.trackId == trackId) return true;
        }
        return false;
    }

    static List<OverlayItem> orderedForRendering(List<OverlayItem> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<OverlayItem> ordered = new ArrayList<>(source);
        ordered.sort(
                Comparator.comparingInt(
                                (OverlayItem item) -> zOrder(item.kind)
                        )
                        .thenComparingLong(item -> item.trackId)
        );
        return Collections.unmodifiableList(ordered);
    }

    private static int zOrder(OverlayItem.Kind kind) {
        if (kind == OverlayItem.Kind.VEHICLE) return 0;
        if (kind == OverlayItem.Kind.VEHICLE_ROI) return 1;
        if (kind == OverlayItem.Kind.PLATE) return 2;
        return 0;
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

    /** Etykieta pojazdu pozostaje nad ramką albo przy jej górnej krawędzi. */
    private RectF findVehicleBadge(
            RectF owner,
            float width,
            float height,
            List<RectF> labels,
            float viewWidth,
            float viewHeight
    ) {
        float gap = dp(5f);
        float left = clamp(owner.left, 0f, Math.max(0f, viewWidth - width));
        float right = clamp(
                owner.right - width,
                0f,
                Math.max(0f, viewWidth - width)
        );
        RectF[] candidates = new RectF[]{
                new RectF(left, owner.top - gap - height, left + width, owner.top - gap),
                new RectF(right, owner.top - gap - height, right + width, owner.top - gap),
                new RectF(left, owner.top + gap, left + width, owner.top + gap + height),
                new RectF(right, owner.top + gap, right + width, owner.top + gap + height)
        };
        for (RectF candidate : candidates) {
            if (!inside(candidate, viewWidth, viewHeight)) continue;
            if (!intersectsAny(candidate, labels, gap * 0.45f)) return candidate;
        }
        return inside(candidates[2], viewWidth, viewHeight) ? candidates[2] : null;
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
            if (item.kind == OverlayItem.Kind.VEHICLE && item.trackId > 0L) {
                EntityRecognitionSnapshot recognition = vehicleRecognitions.get(
                        item.trackId
                );
                EntityRecognitionSnapshot requestedRecognition =
                        requestedVehicleRecognitions.get(item.trackId);
                EntityRecognitionSnapshot labelRecognition = recognition != null
                        ? recognition : requestedRecognition;
                this.parts = new LabelParts(
                        labelRecognition == null
                                ? "Pojazd " + item.trackId
                                : "Pojazd " + item.trackId + ": "
                                + labelRecognition.text,
                        labelRecognition != null
                                ? Math.round(labelRecognition.confidence * 100.0) + "%"
                                : completedVehicleEntityIds.contains(item.trackId)
                                ? "odczytany"
                                : identifiedVehicleEntityIds.contains(item.trackId)
                                ? activeVehicleEntityId == item.trackId
                                ? "odczytuję"
                                : "czeka na odczyt"
                                : ""
                );
            } else {
                this.parts = LabelParts.parse(item.label);
            }
            this.detectionWidth = detectionTextPaint.measureText(parts.detection);
        }
    }

    private static final class AbsorbingPlate {
        final EntityRecognitionSnapshot recognition;
        final RectF sourceBounds;

        AbsorbingPlate(EntityRecognitionSnapshot recognition, RectF sourceBounds) {
            this.recognition = recognition;
            this.sourceBounds = sourceBounds;
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
