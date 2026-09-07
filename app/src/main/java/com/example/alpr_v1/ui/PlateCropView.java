package com.example.alpr_v1.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.alpr_v1.pipeline.PlateCharacter;

import java.util.Collections;
import java.util.List;

/** Rysuje crop, lekkie ramki znaków i oddzielony od obrazu pas confidence. */
public final class PlateCropView extends View {
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint characterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint confidencePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageBounds = new RectF();
    private final RectF characterBox = new RectF();
    private final RectF characterBadge = new RectF();
    private Bitmap bitmap;
    private List<PlateCharacter> characters = Collections.emptyList();
    private boolean boxesVisible = true;

    public PlateCropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setColor(Color.rgb(255, 152, 0));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(2f));
        badgePaint.setColor(Color.argb(235, 8, 13, 21));
        badgePaint.setStyle(Paint.Style.FILL);
        characterPaint.setColor(Color.rgb(125, 211, 252));
        characterPaint.setTextSize(sp(9f));
        characterPaint.setFakeBoldText(true);
        confidencePaint.setColor(Color.rgb(94, 230, 168));
        confidencePaint.setTextSize(sp(9f));
        confidencePaint.setFakeBoldText(true);
        setWillNotDraw(false);
    }

    public void setPlate(Bitmap bitmap, List<PlateCharacter> characters) {
        this.bitmap = bitmap;
        this.characters = characters == null ? Collections.emptyList() : characters;
        invalidate();
    }

    public void setBoxesVisible(boolean visible) {
        if (boxesVisible == visible) return;
        boxesVisible = visible;
        invalidate();
    }

    public boolean boxesVisible() {
        return boxesVisible;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || bitmap.isRecycled()) return;

        boolean drawBoxes = boxesVisible && !characters.isEmpty();
        float contentLeft = getPaddingLeft();
        float contentTop = getPaddingTop();
        float contentWidth = Math.max(
                1f,
                getWidth() - getPaddingLeft() - getPaddingRight()
        );
        float contentHeight = Math.max(
                1f,
                getHeight() - getPaddingTop() - getPaddingBottom()
        );
        // Reserve room even when a character starts at the very top of the crop.
        float badgeSpace = drawBoxes ? badgeHeight() + dp(4f) : 0f;
        float imageHeight = Math.max(1f, contentHeight - badgeSpace);
        float scale = Math.min(
                contentWidth / bitmap.getWidth(),
                imageHeight / bitmap.getHeight()
        );
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float left = contentLeft + (contentWidth - width) * 0.5f;
        float top = contentTop + badgeSpace + (imageHeight - height) * 0.5f;
        imageBounds.set(left, top, left + width, top + height);
        canvas.drawBitmap(bitmap, null, imageBounds, imagePaint);

        if (drawBoxes) {
            for (PlateCharacter character : characters) {
                float boxLeft = imageBounds.left + character.left * imageBounds.width();
                float boxTop = imageBounds.top + character.top * imageBounds.height();
                float boxRight = imageBounds.left + character.right * imageBounds.width();
                float boxBottom = imageBounds.top + character.bottom * imageBounds.height();
                characterBox.set(
                        Math.min(boxLeft, boxRight),
                        Math.min(boxTop, boxBottom),
                        Math.max(boxLeft, boxRight),
                        Math.max(boxTop, boxBottom)
                );
                canvas.drawRoundRect(characterBox, dp(1.5f), dp(1.5f), boxPaint);
                drawCharacterBadge(canvas, character);
            }
        }
    }

    private void drawCharacterBadge(Canvas canvas, PlateCharacter character) {
        String label = character.label == null || character.label.isEmpty()
                ? "?" : character.label;
        String confidence = Math.round(character.confidence * 100) + "%";
        float horizontalPadding = dp(3f);
        float textGap = dp(2f);
        float badgeWidth = horizontalPadding * 2f
                + characterPaint.measureText(label)
                + textGap
                + confidencePaint.measureText(confidence);
        float badgeHeight = badgeHeight();
        float left = characterBox.centerX() - badgeWidth * 0.5f;
        left = Math.max(imageBounds.left, Math.min(left, imageBounds.right - badgeWidth));
        float top = characterBox.top - badgeHeight - dp(2f);
        characterBadge.set(left, top, left + badgeWidth, top + badgeHeight);
        canvas.drawRoundRect(characterBadge, dp(3f), dp(3f), badgePaint);

        float baseline = characterBadge.centerY() - (
                confidencePaint.ascent() + confidencePaint.descent()
        ) * 0.5f;
        float textLeft = characterBadge.left + horizontalPadding;
        canvas.drawText(label, textLeft, baseline, characterPaint);
        textLeft += characterPaint.measureText(label) + textGap;
        canvas.drawText(confidence, textLeft, baseline, confidencePaint);
    }

    private float badgeHeight() {
        Paint.FontMetrics metrics = confidencePaint.getFontMetrics();
        return Math.max(dp(15f), metrics.descent - metrics.ascent + dp(4f));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
