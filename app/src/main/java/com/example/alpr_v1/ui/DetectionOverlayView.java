package com.example.alpr_v1.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DetectionOverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF scaledBounds = new RectF();
    private List<OverlayItem> items = Collections.emptyList();
    private int sourceWidth;
    private int sourceHeight;

    public DetectionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setColor(Color.rgb(125, 211, 252));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(2));
        pointPaint.setColor(Color.rgb(134, 239, 172));
        pointPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(14));
        textPaint.setShadowLayer(dp(2), 0, 0, Color.BLACK);
        setWillNotDraw(false);
    }

    public void setItems(List<OverlayItem> newItems) {
        setItems(newItems, 0, 0);
    }

    public void setItems(List<OverlayItem> newItems, int sourceWidth, int sourceHeight) {
        this.items = Collections.unmodifiableList(new ArrayList<>(newItems));
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float imageWidth = sourceWidth > 0 ? sourceWidth : width;
        float imageHeight = sourceHeight > 0 ? sourceHeight : height;
        float scale = Math.max(width / imageWidth, height / imageHeight);
        float offsetX = (width - imageWidth * scale) * 0.5f;
        float offsetY = (height - imageHeight * scale) * 0.5f;
        for (OverlayItem item : items) {
            RectF bounds = item.normalizedBounds;
            scaledBounds.set(
                    offsetX + bounds.left * imageWidth * scale,
                    offsetY + bounds.top * imageHeight * scale,
                    offsetX + bounds.right * imageWidth * scale,
                    offsetY + bounds.bottom * imageHeight * scale
            );
            canvas.drawRect(scaledBounds, boxPaint);
            for (PointF point : item.normalizedKeypoints) {
                canvas.drawCircle(
                        offsetX + point.x * imageWidth * scale,
                        offsetY + point.y * imageHeight * scale,
                        dp(4),
                        pointPaint
                );
            }
            if (!item.label.isEmpty()) {
                canvas.drawText(item.label, scaledBounds.left, Math.max(dp(16), scaledBounds.top - dp(4)), textPaint);
            }
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
