package com.example.alpr_v1.vision;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.util.List;

public final class PlateRectifier {
    public static final int STANDARD_WIDTH = 256;
    public static final int STANDARD_HEIGHT_LONG = 64;
    public static final int STANDARD_HEIGHT_SQUARE = 128;
    public static final float SQUARE_ASPECT_THRESHOLD = 2.5f;

    private PlateRectifier() {}

    public static Bitmap rectify(Bitmap source, List<Point2> corners) {
        List<Point2> ordered = GeometryUtils.orderQuad(corners);
        float height = Math.max(1f, GeometryUtils.estimatedHeight(ordered));
        float aspect = GeometryUtils.estimatedWidth(ordered) / height;
        int outputHeight = aspect < SQUARE_ASPECT_THRESHOLD
                ? STANDARD_HEIGHT_SQUARE
                : STANDARD_HEIGHT_LONG;
        return rectify(source, ordered, STANDARD_WIDTH, outputHeight);
    }

    public static Bitmap rectify(Bitmap source, List<Point2> ordered, int outputWidth, int outputHeight) {
        if (ordered.size() != 4) throw new IllegalArgumentException("Wymagane są cztery narożniki");
        float[] sourcePoints = new float[8];
        for (int i = 0; i < 4; i++) {
            sourcePoints[i * 2] = ordered.get(i).x;
            sourcePoints[i * 2 + 1] = ordered.get(i).y;
        }
        float[] destination = new float[]{
                0, 0,
                outputWidth - 1f, 0,
                outputWidth - 1f, outputHeight - 1f,
                0, outputHeight - 1f
        };
        Matrix transform = new Matrix();
        if (!transform.setPolyToPoly(sourcePoints, 0, destination, 0, 4)) {
            throw new IllegalArgumentException("Nie udało się wyznaczyć homografii tablicy");
        }
        Bitmap output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.BLACK);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, transform, paint);
        return output;
    }
}
