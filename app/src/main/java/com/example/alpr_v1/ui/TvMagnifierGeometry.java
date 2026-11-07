package com.example.alpr_v1.ui;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import com.example.alpr_v1.setup.CameraPreviewGeometry;

/** Geometry of a camera-surface copy, independent of the position of the loupe window. */
final class TvMagnifierGeometry {
    final CameraPreviewGeometry camera;
    final int rawWidth, rawHeight;
    final Matrix rawToView, viewToRaw;
    private final float[] values = new float[9];

    TvMagnifierGeometry(CameraPreviewGeometry camera, int width, int height, Matrix rawToView) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Empty camera surface");
        this.camera = camera;
        rawWidth = width;
        rawHeight = height;
        this.rawToView = new Matrix(rawToView);
        viewToRaw = new Matrix();
        if (!rawToView.invert(viewToRaw)) throw new IllegalArgumentException("Singular surface transform");
        rawToView.getValues(values);
        for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Invalid transform");
    }

    boolean same(TvMagnifierGeometry other) {
        if (other == null || !camera.sameGeometry(other.camera)
                || camera.viewportRevision != other.camera.viewportRevision
                || rawWidth != other.rawWidth || rawHeight != other.rawHeight) return false;
        float[] b = new float[9];
        other.rawToView.getValues(b);
        for (int i = 0; i < 9; i++) if (Math.abs(values[i] - b[i]) > .0001f) return false;
        return true;
    }

    Matrix bitmapToLoupe(Rect copied, float x, float y, int width, int height, float zoom) {
        Matrix matrix = new Matrix();
        matrix.setTranslate(copied.left, copied.top);
        matrix.postConcat(rawToView);
        matrix.postTranslate(-x, -y);
        matrix.postScale(zoom, zoom);
        matrix.postTranslate(width / 2f, height / 2f);
        return matrix;
    }

    Path visibleImageInLoupe(float x, float y, int width, int height, float zoom) {
        Path image = new Path();
        for (int i = 0; i < 4; i++) {
            double[] point = camera.normalizedToView(i == 1 || i == 2 ? 1 : 0, i >= 2 ? 1 : 0);
            float px = (float)((point[0] - x) * zoom + width / 2f);
            float py = (float)((point[1] - y) * zoom + height / 2f);
            if (i == 0) image.moveTo(px, py); else image.lineTo(px, py);
        }
        image.close();
        return image;
    }

    RectF usableInLoupe(float x, float y, int width, int height, float zoom) {
        double[] r = camera.usableViewRect();
        return new RectF((float)((r[0] - x) * zoom + width / 2f), (float)((r[1] - y) * zoom + height / 2f),
                (float)((r[2] - x) * zoom + width / 2f), (float)((r[3] - y) * zoom + height / 2f));
    }

    // CameraX 1.4.0 PreviewTransformation.createTransformedBitmap applies this correction to
    // raw SurfaceView copies. TextureView exposes the actual correction via getTransform().
    static Matrix surfaceCorrection(int width, int height, int displayRotation) {
        Matrix result = new Matrix();
        RectF raw = new RectF(0, 0, width, height), normalized = new RectF(-1, -1, 1, 1);
        result.setRectToRect(raw, normalized, Matrix.ScaleToFit.FILL);
        result.postRotate(-displayRotation);
        Matrix restore = new Matrix();
        restore.setRectToRect(normalized, raw, Matrix.ScaleToFit.FILL);
        result.postConcat(restore);
        return result;
    }
}
