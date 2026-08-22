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
    private List<OverlayItem> items = Collections.emptyList();
    private List<RenderItem> renderItems = Collections.emptyList();
    private int sourceWidth;
    private int sourceHeight;

    public DetectionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setColor(Color.argb(190, 56, 189, 248));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(1.35f));

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

        pointPaint.setColor(Color.argb(185, 94, 234, 212));
        pointPaint.setStyle(Paint.Style.FILL);

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

    public void setItems(List<OverlayItem> newItems, int sourceWidth, int sourceHeight) {
        this.items = Collections.unmodifiableList(new ArrayList<>(newItems));
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        rebuildRenderItems();
        postInvalidateOnAnimation();
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
            OverlayItem item = renderItem.item;
            RectF bounds = renderItem.bounds;
            canvas.drawRoundRect(
                    bounds,
                    dp(5),
                    dp(5),
                    paintFor(item)
            );
            if (item.carriedPrediction) continue;

            /*
             * Keypointy należą do modelu tablicy MT.
             * VEHICLE oraz VEHICLE_ROI są tylko prostokątami.
             */
            if (item.kind == OverlayItem.Kind.PLATE) {
                for (PointF point : renderItem.points) {
                    canvas.drawCircle(
                            point.x,
                            point.y,
                            dp(2.2f),
                            pointPaint
                    );
                }
            }
        }

        for (RenderItem renderItem : renderItems) {
            if (renderItem.badge != null) drawLabel(canvas, renderItem);
        }
    }
    private Paint paintFor(OverlayItem item) {
        if (item.kind == OverlayItem.Kind.VEHICLE) {
            return vehiclePaint;
        }

        if (item.kind == OverlayItem.Kind.VEHICLE_ROI) {
            return vehicleRoiPaint;
        }

        if (item.carriedPrediction) {
            return predictionPaint;
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
        float scale = Math.max(viewWidth / imageWidth, viewHeight / imageHeight);
        float offsetX = (viewWidth - imageWidth * scale) * 0.5f;
        float offsetY = (viewHeight - imageHeight * scale) * 0.5f;
        List<RenderItem> prepared = new ArrayList<>(items.size());
        List<RectF> frameBounds = new ArrayList<>(items.size());
        for (OverlayItem item : items) {
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
            frameBounds.add(bounds);
        }

        List<RectF> occupiedLabels = new ArrayList<>();
        float horizontalPadding = dp(6);
        float verticalPadding = dp(3.5f);
        float textHeight = Math.max(
                fontHeight(detectionTextPaint),
                fontHeight(confidenceTextPaint)
        );
        for (RenderItem renderItem : prepared) {
            if (renderItem.item.label.isEmpty() || renderItem.item.carriedPrediction) continue;
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
                    frameBounds,
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
