package com.example.alpr_v1.capture;

import androidx.lifecycle.ViewModel;

import com.example.alpr_v1.metrics.MetricsCollector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Przechowuje ciężki stan galerii podczas odtwarzania aktywności, np. po obrocie ekranu.
 * Bitmap nie zapisujemy w Bundle, ponieważ łatwo przekroczyłyby limit transakcji Androida.
 */
public final class CaptureGalleryViewModel extends ViewModel {
    private final List<CapturedPlateItem> capturedCrops = new ArrayList<>();
    private final Map<Long, CropSamplingPolicy.Previous> lastCaptureByTrack = new HashMap<>();
    private final MetricsCollector metricsCollector = new MetricsCollector();
    private final RecognitionHistoryStore recognitionHistory = new RecognitionHistoryStore();
    public final RecentReadCache recentReads = new RecentReadCache(40);
    private CropSessionStore cropSessions;
    public String pendingCropSessionExport;
    public volatile boolean cropSessionExportBusy;
    public final androidx.lifecycle.MutableLiveData<String> cropSessionMessages = new androidx.lifecycle.MutableLiveData<>();
    public synchronized CropSessionStore cropSessions(android.content.Context context) {
        if (cropSessions == null) cropSessions = new CropSessionStore(context.getApplicationContext());
        return cropSessions;
    }

    private final java.util.concurrent.ExecutorService cropPreparer =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "crop-preview-preparer"));
    private final java.util.ArrayDeque<CapturedPlateItem> preparedCrops = new java.util.ArrayDeque<>();
    private final Map<String, Integer> pendingNumberKeys = new HashMap<>();
    private final Map<CapturedPlateItem, String> preparedNumberKeys = new java.util.IdentityHashMap<>();
    private int previewGeneration, pendingPreviews;
    private boolean cleared;
    public final androidx.lifecycle.MutableLiveData<Integer> cropPreparationReady = new androidx.lifecycle.MutableLiveData<>();

    /** Only RAM gallery previews use this bounded queue; research evidence has its own durable writer. */
    public boolean prepareCrop(java.util.concurrent.Callable<CapturedPlateItem> prepare,
            Runnable discardOwnedBitmap, int capacity) {
        return prepareCrop(prepare, discardOwnedBitmap, capacity, "");
    }

    public synchronized boolean previewNumberPending(String key) { return pendingNumberKeys.containsKey(key); }

    public synchronized boolean prepareCrop(java.util.concurrent.Callable<CapturedPlateItem> prepare,
            Runnable discardOwnedBitmap, int capacity, String numberKey) {
        if (cleared || pendingPreviews >= Math.max(1, capacity)
                || !numberKey.isEmpty() && pendingNumberKeys.containsKey(numberKey)) {
            discardOwnedBitmap.run();
            return false;
        }
        int generation = previewGeneration;
        pendingPreviews++;
        if (!numberKey.isEmpty()) pendingNumberKeys.put(numberKey, generation);
        cropPreparer.execute(() -> {
            CapturedPlateItem result = null;
            boolean delivered = false;
            try {
                synchronized (this) {
                    if (cleared || generation != previewGeneration) return;
                }
                result = prepare.call();
                synchronized (this) {
                    if (!cleared && generation == previewGeneration) {
                        metricsCollector.recordCapturedCrop(result);
                        preparedCrops.add(result);
                        if (!numberKey.isEmpty()) preparedNumberKeys.put(result, numberKey);
                        result = null; // Ownership moves to the retained delivery queue.
                        delivered = true;
                        cropPreparationReady.postValue(generation);
                    }
                }
            } catch (Exception error) {
                cropSessionMessages.postValue("Nie uda?o si? przygotowa? podgl?du cropu: " + error.getMessage());
            } finally {
                if (result != null) result.recycle();
                // A delivered result owns its bitmap; all other exits must release the owned copy.
                synchronized (this) {
                    if (!delivered) {
                        pendingPreviews--;
                        pendingNumberKeys.remove(numberKey, generation);
                        discardOwnedBitmap.run();
                    }
                }
            }
        });
        return true;
    }

    public synchronized List<CapturedPlateItem> takePreparedCrops() {
        List<CapturedPlateItem> result = new ArrayList<>(preparedCrops);
        pendingPreviews -= result.size();
        for (CapturedPlateItem item : result) {
            String key = preparedNumberKeys.remove(item);
            if (key != null) pendingNumberKeys.remove(key);
        }
        preparedCrops.clear();
        return result;
    }

    public synchronized void invalidatePendingCropPreviews() {
        previewGeneration++;
        pendingPreviews -= preparedCrops.size();
        pendingNumberKeys.clear(); preparedNumberKeys.clear();
        for (CapturedPlateItem item : preparedCrops) item.recycle();
        preparedCrops.clear();
    }

    public java.util.concurrent.CompletableFuture<Void> cropPreparationBarrier() {
        java.util.concurrent.CompletableFuture<Void> done = new java.util.concurrent.CompletableFuture<>();
        cropPreparer.execute(() -> done.complete(null));
        return done;
    }

    private boolean collectionActive;
    private String collectionSessionId = "";
    private long collectionSessionStartedElapsedNanos;
    private int collectionSequence;


    public List<CapturedPlateItem> capturedCrops() { return capturedCrops; }

    public Map<Long, CropSamplingPolicy.Previous> lastCaptureByTrack() {
        return lastCaptureByTrack;
    }

    public MetricsCollector metricsCollector() { return metricsCollector; }

    public RecognitionHistoryStore recognitionHistory() { return recognitionHistory; }

    public boolean collectionActive() { return collectionActive; }
    public String collectionSessionId() { return collectionSessionId; }
    public long collectionSessionStartedElapsedNanos() {
        return collectionSessionStartedElapsedNanos;
    }
    public int collectionSequence() { return collectionSequence; }

    public void beginCollectionWindow() {
        lastCaptureByTrack.clear();
    }

    public void retainSession(
            boolean active,
            String sessionId,
            long startedElapsedNanos,
            int sequence
    ) {
        collectionActive = active;
        collectionSessionId = sessionId == null ? "" : sessionId;
        collectionSessionStartedElapsedNanos = startedElapsedNanos;
        collectionSequence = Math.max(0, sequence);
    }



    @Override
    protected void onCleared() {
        synchronized (this) { cleared = true; invalidatePendingCropPreviews(); }
        cropPreparer.shutdown(); // Queued owners still run their release path.
        recentReads.clear();
        if (cropSessions != null) cropSessions.close();
        for (CapturedPlateItem item : capturedCrops) item.recycle();
        capturedCrops.clear();
        lastCaptureByTrack.clear();
        recognitionHistory.clear();
        metricsCollector.clearCropSession();
    }
}
