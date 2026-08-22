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
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint characterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint confidencePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint separatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageBounds = new RectF();
    private final RectF badgeBand = new RectF();
    private final RectF characterBox = new RectF();
    private Bitmap bitmap;
    private List<PlateCharacter> characters = Collections.emptyList();

    public PlateCropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setColor(Color.rgb(255, 152, 0));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(1.5f));
        bandPaint.setColor(Color.rgb(8, 13, 21));
        bandPaint.setStyle(Paint.Style.FILL);
        characterPaint.setColor(Color.rgb(125, 211, 252));
        characterPaint.setTextSize(dp(8.5f));
        characterPaint.setFakeBoldText(true);
        confidencePaint.setColor(Color.rgb(94, 230, 168));
        confidencePaint.setTextSize(dp(8f));
        confidencePaint.setFakeBoldText(true);
        separatorPaint.setColor(Color.rgb(100, 116, 139));
        separatorPaint.setTextSize(dp(8f));
        setWillNotDraw(false);
    }

    public void setPlate(Bitmap bitmap, List<PlateCharacter> characters) {
        this.bitmap = bitmap;
        this.characters = characters == null ? Collections.emptyList() : characters;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || bitmap.isRecycled()) return;

        float bandHeight = characters.isEmpty() ? 0f : dp(19f);
        float bandGap = characters.isEmpty() ? 0f : dp(3f);
        float imageAreaHeight = Math.max(1f, getHeight() - bandHeight - bandGap);
        float scale = Math.min(
                getWidth() / (float) bitmap.getWidth(),
                imageAreaHeight / bitmap.getHeight()
        );
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float left = (getWidth() - width) * 0.5f;
        float top = (imageAreaHeight - height) * 0.5f;
        imageBounds.set(left, top, left + width, top + height);
        canvas.drawBitmap(bitmap, null, imageBounds, imagePaint);

        for (PlateCharacter character : characters) {
            characterBox.set(
                    imageBounds.left + character.left * imageBounds.width(),
                    imageBounds.top + character.top * imageBounds.height(),
                    imageBounds.left + character.right * imageBounds.width(),
                    imageBounds.top + character.bottom * imageBounds.height()
            );
            canvas.drawRoundRect(characterBox, dp(1.5f), dp(1.5f), boxPaint);
        }
        if (!characters.isEmpty()) drawLegend(canvas, imageAreaHeight + bandGap);
    }

    private void drawLegend(Canvas canvas, float top) {
        badgeBand.set(0f, top, getWidth(), getHeight());
        canvas.drawRoundRect(badgeBand, dp(3), dp(3), bandPaint);

        float gap = dp(3f);
        float separatorGap = dp(4f);
        float totalWidth = 0f;
        for (int index = 0; index < characters.size(); index++) {
            PlateCharacter character = characters.get(index);
            String confidence = Math.round(character.confidence * 100) + "%";
            totalWidth += characterPaint.measureText(character.label)
                    + gap + confidencePaint.measureText(confidence);
            if (index + 1 < characters.size()) {
                totalWidth += separatorGap * 2f + separatorPaint.measureText("·");
            }
        }
        float x = Math.max(dp(4), (getWidth() - totalWidth) * 0.5f);
        float baseline = badgeBand.centerY() - (
                characterPaint.ascent() + characterPaint.descent()
        ) * 0.5f;
        canvas.save();
        canvas.clipRect(badgeBand);
        for (int index = 0; index < characters.size(); index++) {
            PlateCharacter character = characters.get(index);
            String confidence = Math.round(character.confidence * 100) + "%";
            canvas.drawText(character.label, x, baseline, characterPaint);
            x += characterPaint.measureText(character.label) + gap;
            canvas.drawText(confidence, x, baseline, confidencePaint);
            x += confidencePaint.measureText(confidence);
            if (index + 1 < characters.size()) {
                x += separatorGap;
                canvas.drawText("·", x, baseline, separatorPaint);
                x += separatorPaint.measureText("·") + separatorGap;
            }
        }
        canvas.restore();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
