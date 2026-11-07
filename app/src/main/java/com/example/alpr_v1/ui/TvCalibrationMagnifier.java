package com.example.alpr_v1.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Magnifier;
import android.widget.PopupWindow;
import androidx.camera.view.PreviewView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Camera-only, asynchronous loupe. Never reads the UI window or the inference pipeline. */
final class TvCalibrationMagnifier {
    static final long INTERVAL_MS = 100, MAX_AGE_MS = 250;
    static final long MAX_BITMAP_BYTES = 32L * 1024 * 1024;
    interface Resolver { Source resolve(CameraPreviewGeometry camera); default void close() { } }
    interface Copy { void request(Source source, Bitmap target, java.util.function.IntConsumer done); }
    interface Clock { long now(); }

    static final class Source {
        final TvMagnifierGeometry geometry;
        final Object identity;
        final Surface surface;
        final boolean ownsSurface;
        Source(TvMagnifierGeometry geometry, Object identity, Surface surface, boolean ownsSurface) {
            this.geometry = geometry; this.identity = identity; this.surface = surface; this.ownsSurface = ownsSurface;
        }
        boolean same(Source other) {
            return other != null && identity == other.identity && geometry.same(other.geometry);
        }
        void release() { if (ownsSurface && surface != null) surface.release(); }
    }

    private final View anchor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Resolver resolver;
    private final Copy copy;
    private final Clock clock;
    private final int width, height;
    private final float zoom, radius;
    private final Content content;
    private PopupWindow popup;
    private ExecutorService worker;
    private CameraPreviewGeometry camera;
    private Source current;
    private Bitmap cached, spare;
    private Rect cachedRect;
    private Source cachedSource;
    private long cachedAt, lastRequest = -INTERVAL_MS, epoch;
    private boolean showing, inFlight, readbackUnavailable;
    private float x, y;
    private final Runnable update = this::update;

    TvCalibrationMagnifier(View anchor, PreviewView preview) {
        this(anchor, new LiveResolver(preview, anchor), null, SystemClock::uptimeMillis, defaults(anchor));
        ((LiveResolver)resolver).onChanged = () -> main.post(update);
    }
    TvCalibrationMagnifier(View anchor, Resolver resolver, Copy copy, Clock clock, float[] style) {
        this.anchor = anchor; this.resolver = resolver; this.clock = clock;
        width = Math.max(1, Math.round(style[0])); height = Math.max(1, Math.round(style[1]));
        zoom = style[2]; radius = style[3];
        if (!Float.isFinite(zoom) || zoom <= 0) throw new IllegalArgumentException("Invalid loupe zoom");
        content = new Content();
        this.copy = copy == null ? this::pixelCopy : copy;
    }

    private static float[] defaults(View anchor) {
        Magnifier defaults = new Magnifier.Builder(anchor).build();
        return new float[]{defaults.getWidth(), defaults.getHeight(), defaults.getZoom(), defaults.getCornerRadius()};
    }

    void show(CameraPreviewGeometry camera, float x, float y) {
        this.camera = camera; this.x = x; this.y = y; showing = true;
        if (anchor.isAttachedToWindow() && anchor.getWindowVisibility() == View.VISIBLE) {
            if (popup == null) {
                popup = new PopupWindow(content, width, height, false);
                popup.setTouchable(false); popup.setFocusable(false);
                popup.setIsLaidOutInScreen(true); popup.setIsClippedToScreen(true);
                popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
                popup.setElevation(6 * anchor.getResources().getDisplayMetrics().density);
            }
            position();
        }
        update();
    }

    private void position() {
        int[] origin = new int[2]; anchor.getLocationOnScreen(origin);
        Rect bounds = new Rect(); anchor.getGlobalVisibleRect(bounds);
        int[] rootOrigin=new int[2];anchor.getRootView().getLocationOnScreen(rootOrigin);bounds.offset(rootOrigin[0],rootOrigin[1]);
        Rect position = popupBounds(bounds, origin[0]+x, origin[1]+y, width, height,
                Math.round(8 * anchor.getResources().getDisplayMetrics().density));
        if(position==null){if(popup.isShowing())popup.dismiss();return;}
        int left=position.left,top=position.top;
        if (popup.isShowing()) popup.update(left, top, -1, -1);
        else popup.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT, left, top);
    }

    static Rect popupBounds(Rect bounds,float pointX,float pointY,int width,int height,int inset){
        if(bounds.width()<width||bounds.height()<height)return null;
        int left=bounds.left+Math.min(inset,(bounds.width()-width)/2),right=bounds.right-width-Math.min(inset,(bounds.width()-width)/2);
        int top=bounds.top+Math.min(inset,(bounds.height()-height)/2),bottom=bounds.bottom-height-Math.min(inset,(bounds.height()-height)/2);
        Rect best=null;double distance=-1;
        for(int x:new int[]{left,right})for(int y:new int[]{top,bottom}){
            double d=Math.hypot(x+width/2f-pointX,y+height/2f-pointY);
            if(d>distance){distance=d;best=new Rect(x,y,x+width,y+height);}
        }
        return best;
    }

    private void update() {
        main.removeCallbacks(update);
        if (!showing) return;
        Source source;
        try { source = resolver.resolve(camera); } catch (RuntimeException unavailable) { source = null; }
        if (source == null) {
            epoch++; releaseCurrent(); clearCache(); content.invalidate(); schedule(INTERVAL_MS); return;
        }
        if (!source.same(current)) {
            epoch++; releaseCurrent(); current = source; clearCache();readbackUnavailable=false;
        } else { source.release(); }
        content.invalidate();
        long now = clock.now();
        if (inFlight) { schedule(INTERVAL_MS); return; }
        if (now - lastRequest < INTERVAL_MS) { schedule(INTERVAL_MS - (now - lastRequest)); return; }
        // PixelCopy crops in the producer's logical (already crop/rotated) space, NOT in
        // SurfaceHolder's buffer space. A full copy to the inner-view dimensions is the
        // CameraX snapshot convention; apply the loupe crop only after this normalization.
        Rect rect = new Rect(0,0,current.geometry.rawWidth,current.geometry.rawHeight);
        if(2L*rect.width()*rect.height()*4>MAX_BITMAP_BYTES){readbackUnavailable=true;clearCache();content.invalidate();schedule(INTERVAL_MS);return;}
        Source request;
        try { request = resolver.resolve(camera); } catch (RuntimeException unavailable) { request = null; }
        if (request == null || !current.same(request)) {
            if (request != null) request.release();
            clearCache(); schedule(INTERVAL_MS); return;
        }
        Bitmap target;
        try{
            target=spare;spare=null;
            if(target!=null&&(target.getWidth()!=rect.width()||target.getHeight()!=rect.height())){target.recycle();target=null;}
            if(target==null)target=Bitmap.createBitmap(rect.width(),rect.height(),Bitmap.Config.ARGB_8888);
        }catch(OutOfMemoryError unavailable){readbackUnavailable=true;request.release();clearCache();schedule(INTERVAL_MS);return;}
        final Bitmap destination=target;
        Source copying = request;
        long token = epoch, requestedAt = now;
        inFlight = true; lastRequest = now;
        try {
            copy.request(copying, destination, result -> {
                // Copy ports deliver on main, including PixelCopy errors and late completions.
                inFlight = false;
                Source latest = null;
                try { if (showing) latest = resolver.resolve(this.camera); } catch (RuntimeException ignored) { }
                boolean valid = showing && token == epoch && copying.same(current) && copying.same(latest)
                        && result == PixelCopy.SUCCESS && clock.now() - requestedAt <= MAX_AGE_MS;
                if (latest != null) latest.release();
                if (valid) {
                    readbackUnavailable=false;
                    if(spare!=null)spare.recycle();spare=cached;
                    cached = destination; cachedRect = new Rect(rect); cachedSource = copying; cachedAt = requestedAt;
                } else { readbackUnavailable=result!=PixelCopy.SUCCESS;destination.recycle(); clearCache(); }
                copying.release();
                content.invalidate();
                if (showing) schedule(Math.max(1, INTERVAL_MS - (clock.now() - lastRequest)));
            });
        } catch (RuntimeException unavailable) {
            readbackUnavailable=true;inFlight = false; copying.release(); destination.recycle(); clearCache(); schedule(INTERVAL_MS);
        }
    }

    private void pixelCopy(Source source, Bitmap target, java.util.function.IntConsumer done) {
        if (worker == null || worker.isShutdown()) worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "tv-loupe-copy"));
        // On older Android versions request() itself can block while reading the GPU surface.
        worker.execute(() -> {
            try { PixelCopy.request(source.surface, target, done::accept, main); }
            catch (RuntimeException unavailable) { main.post(() -> done.accept(PixelCopy.ERROR_SOURCE_INVALID)); }
        });
    }

    private void schedule(long delay) { main.removeCallbacks(update); main.postDelayed(update, Math.max(1, delay)); }
    private void releaseCurrent() { if (current != null) current.release(); current = null; }
    private void clearCache() { if (cached != null) cached.recycle(); if(spare!=null)spare.recycle();spare=null;cached = null; cachedSource = null; cachedRect = null; }
    void dismiss() {
        showing = false; readbackUnavailable=false;epoch++; main.removeCallbacks(update);
        if (popup != null) { popup.dismiss(); popup = null; }
        releaseCurrent(); clearCache();
        resolver.close();
        if (worker != null) { worker.shutdown(); worker = null; }
    }

    boolean hasCurrentPixels() {
        if (!showing || cached == null || current == null || !cachedSource.same(current)
                || clock.now() - cachedAt > MAX_AGE_MS) return false;
        return true; // A current full snapshot covers every corner; the renderer supplies padding.
    }

    /** Also used by pixel tests before the reticle is painted. */
    void drawCamera(Canvas canvas) {
        canvas.drawColor(0xff17212b);
        if (!hasCurrentPixels()) return;
        TvMagnifierGeometry geometry = current.geometry;
        int saved = canvas.save();
        canvas.clipPath(geometry.visibleImageInLoupe(x, y, width, height, zoom));
        canvas.clipRect(geometry.usableInLoupe(x, y, width, height, zoom));
        canvas.drawBitmap(cached, geometry.bitmapToLoupe(cachedRect, x, y, width, height, zoom), content.imagePaint);
        canvas.restoreToCount(saved);
    }

    void drawWindow(Canvas canvas) { content.layout(0, 0, width, height); content.draw(canvas); }

    private final class Content extends View {
        final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        Content() { super(anchor.getContext()); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Path round = new Path(); round.addRoundRect(0, 0, width, height, radius, radius, Path.Direction.CW);
            int saved = canvas.save(); canvas.clipPath(round); drawCamera(canvas);
            float density = getResources().getDisplayMetrics().density;
            if (!hasCurrentPixels()) {
                pen.setColor(Color.WHITE); pen.setTextSize(10 * density); pen.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(getContext().getString(current == null||readbackUnavailable ? R.string.tv_loupe_unavailable : R.string.tv_loupe_updating),
                        width / 2f, height - 7 * density, pen);
            }
            float cx = width / 2f, cy = height / 2f;
            pen.setColor(Color.BLACK); pen.setStrokeWidth(3 * density); cross(canvas, cx, cy, 10 * density);
            pen.setColor(Color.WHITE); pen.setStrokeWidth(density); cross(canvas, cx, cy, 10 * density);
            canvas.restoreToCount(saved);
        }
        private void cross(Canvas canvas, float cx, float cy, float arm) {
            canvas.drawLine(cx - arm, cy, cx + arm, cy, pen); canvas.drawLine(cx, cy - arm, cx, cy + arm, pen);
        }
    }

    private static final class LiveResolver implements Resolver, SurfaceHolder.Callback {
        final PreviewView preview;
        final View anchor;
        SurfaceHolder observed;
        Object identity;
        Runnable onChanged = () -> { };
        LiveResolver(PreviewView preview, View anchor) { this.preview = preview; this.anchor = anchor; }
        @Override public Source resolve(CameraPreviewGeometry camera) {
            View view = preview == null ? null : findSurface(preview);
            SurfaceHolder holder = view instanceof SurfaceView ? ((SurfaceView)view).getHolder() : null;
            if (holder != observed) {
                close(); observed = holder; identity = new Object();
                if (observed != null) observed.addCallback(this);
            }
            return TvCalibrationMagnifier.resolve(preview, anchor, camera, identity);
        }
        @Override public void close() { if (observed != null) observed.removeCallback(this); observed = null; identity = null; }
        private void changed() { identity = new Object(); onChanged.run(); }
        @Override public void surfaceCreated(SurfaceHolder holder) { changed(); }
        @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { changed(); }
        @Override public void surfaceDestroyed(SurfaceHolder holder) { changed(); }
    }

    static Source resolve(PreviewView preview, View anchor, CameraPreviewGeometry camera, Object surfaceIdentity) {
        if (preview == null || camera == null || !preview.isAttachedToWindow() || !preview.isShown()) return null;
        View view = findSurface(preview);
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        Matrix correction; Surface surface; Object identity; boolean owns; int width, height;
        if (view instanceof SurfaceView) {
            SurfaceView sv = (SurfaceView)view;
            surface = sv.getHolder().getSurface(); if (surface == null || !surface.isValid()) return null;
            // Do not use getSurfaceFrame() as the coordinate system of PixelCopy's srcRect.
            width = view.getWidth(); height = view.getHeight();
            correction = TvMagnifierGeometry.surfaceCorrection(view.getWidth(), view.getHeight(), camera.displayRotationDegrees);
            identity = surfaceIdentity; owns = false;
        } else {
            TextureView tv = (TextureView)view;
            SurfaceTexture texture = tv.getSurfaceTexture(); if (!tv.isAvailable() || texture == null) return null;
            width = view.getWidth(); height = view.getHeight();
            correction = tv.getTransform(new Matrix());
            surface = new Surface(texture); identity = texture; owns = true;
        }
        try {
            Matrix mapping = rawToOverlay(view, anchor, width, height, correction);
            return new Source(new TvMagnifierGeometry(camera, width, height, mapping), identity, surface, owns);
        } catch (RuntimeException unavailable) { if (owns) surface.release(); return null; }
    }

    static Matrix rawToOverlay(View view, View anchor, int width, int height, Matrix correction) {
        Matrix mapping = new Matrix(); mapping.setScale((float)view.getWidth() / width, (float)view.getHeight() / height);
        mapping.postConcat(correction);
        Matrix localToGlobal = new Matrix(); view.transformMatrixToGlobal(localToGlobal);
        mapping.postConcat(localToGlobal);
        Matrix globalToAnchor = new Matrix(); anchor.transformMatrixToLocal(globalToAnchor);
        mapping.postConcat(globalToAnchor);
        return mapping;
    }

    static View findSurface(View view) {
        if (!view.isShown()) return null;
        if (view instanceof SurfaceView || view instanceof TextureView) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View candidate = findSurface(group.getChildAt(i)); if (candidate != null) return candidate;
            }
        }
        return null;
    }
}
