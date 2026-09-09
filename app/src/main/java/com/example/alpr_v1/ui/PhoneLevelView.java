package com.example.alpr_v1.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import com.example.alpr_v1.R;
import com.example.alpr_v1.camera.PhoneOrientationEstimator;

/** Small two-axis level drawn over the preview; updates never resize the viewport. */
public final class PhoneLevelView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float ballX, ballY;
    private boolean available, warning;
    private ValueAnimator animator;

    public PhoneLevelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setClickable(false);
        setContentDescription(context.getString(R.string.phone_orientation_unavailable));
    }

    public void render(PhoneOrientationEstimator.Snapshot snapshot) {
        if (animator != null) animator.cancel();
        boolean wasAvailable = available;
        available = snapshot.available;
        warning = snapshot.warning;
        setContentDescription(getContext().getString(!available ? R.string.phone_orientation_unavailable
                : warning ? R.string.phone_orientation_tilted : R.string.phone_orientation_level,
                Math.round(snapshot.deviationDegrees)));
        if (!available) {
            ballX = ballY = 0f;
            invalidate();
            return;
        }
        float x = Float.isFinite(snapshot.sidewaysDegrees)
                ? snapshot.sidewaysDegrees / PhoneOrientationEstimator.WARNING_DEGREES : 0f;
        float y = -snapshot.forwardDegrees / PhoneOrientationEstimator.WARNING_DEGREES;
        float magnitude = (float) Math.hypot(x, y);
        if (magnitude > 1f) { x /= magnitude; y /= magnitude; }
        final float toX = x, toY = y;
        if (!wasAvailable || !isShown()) {
            ballX = toX; ballY = toY;
            invalidate();
            return;
        }
        final float fromX = ballX, fromY = ballY;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(220L);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            ballX = fromX + (toX - fromX) * fraction;
            ballY = fromY + (toY - fromY) * fraction;
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.max(0f, Math.min(cx, cy) - 3f * density);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99101924);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(0x99D3DFEA);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);
        paint.setStrokeWidth(2f * density);
        paint.setColor(!available ? 0xFF8C9AAA : warning ? 0xFFFF5252 : 0xFF50E3A4);
        canvas.drawCircle(cx, cy, radius, paint);
        if (available) {
            float ballRadius = 4f * density;
            float travel = Math.max(0f, radius - ballRadius - 2f * density);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFF5252);
            canvas.drawCircle(cx + ballX * travel, cy + ballY * travel, ballRadius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx + ballX * travel, cy + ballY * travel, ballRadius, paint);
        }
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE && animator != null) animator.cancel();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
