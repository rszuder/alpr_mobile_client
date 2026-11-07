package com.example.alpr_v1.experiment;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Keeps collection/finalization alive across recreation; owns no Activity or gallery bitmaps. */
public final class ResearchSessionViewModel extends AndroidViewModel {
    public final ExperimentSession experiment = new ExperimentSession();
    public final MutableLiveData<String> messages = new MutableLiveData<>();
    private final ExecutorService finalizer = Executors.newSingleThreadExecutor(r -> new Thread(r,"research-finalizer"));
    private static ScheduledThreadPoolExecutor timerExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "experiment-deadline"); thread.setDaemon(true); return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }
    private final ScheduledThreadPoolExecutor timerExecutor = timerExecutor();
    private final ExperimentDeadline timer = new ExperimentDeadline(android.os.SystemClock::elapsedRealtimeNanos,
            (task, delay) -> {
                ScheduledFuture<?> scheduled = timerExecutor.schedule(task, delay, TimeUnit.NANOSECONDS);
                return () -> scheduled.cancel(false);
            });
    public final MutableLiveData<String> timerExpired = new MutableLiveData<>();
    private volatile ResearchSessionStore store;
    private final Map<String,com.example.alpr_v1.capture.GallerySessionScope> galleryCatalog = new ConcurrentHashMap<>();
    public final MutableLiveData<Long> galleryChanged = new MutableLiveData<>();
    private boolean galleryLoading;
    private void rememberGallerySession(ResearchSessionStore value) {
        if (value != null) galleryCatalog.put(value.sessionId(), com.example.alpr_v1.capture.GallerySessionScope.known(
                value.sessionId(), value.state().name(), value.writtenCropCount(), "LIVE_SESSION_WRITER"));
    }
    public com.example.alpr_v1.capture.GallerySessionScope gallerySession(String id) {
        ResearchSessionStore current=store;
        if(current!=null && current.sessionId().equals(id)) return com.example.alpr_v1.capture.GallerySessionScope.known(
                id,current.state().name(),current.writtenCropCount(),"LIVE_SESSION_WRITER");
        com.example.alpr_v1.capture.GallerySessionScope found=galleryCatalog.get(id);
        return found==null ? com.example.alpr_v1.capture.GallerySessionScope.unavailable(id,"UNKNOWN","Brak potwierdzonego źródła liczby") : found;
    }
    public List<com.example.alpr_v1.capture.GallerySessionScope> gallerySessions() {
        rememberGallerySession(store);
        List<com.example.alpr_v1.capture.GallerySessionScope> result=new ArrayList<>(galleryCatalog.values());
        result.sort((a,b)->b.sessionId.compareTo(a.sessionId));return result;
    }
    public synchronized void refreshGallerySessions() {
        if(galleryLoading || cleared || finalizer.isShutdown())return;
        galleryLoading=true;
        finalizer.execute(()->{
            try {
                File[] dirs=ResearchSessionStore.sessionsRoot(getApplication()).listFiles(File::isDirectory);
                if(dirs!=null)for(File dir:dirs){
                    ResearchSessionStore current=store;
                    if(current!=null && current.sessionId().equals(dir.getName()))continue;
                    galleryCatalog.put(dir.getName(),com.example.alpr_v1.capture.GallerySessionScope.readStored(dir));
                }
                rememberGallerySession(store);
            } finally {
                synchronized(this){galleryLoading=false;}
                galleryChanged.postValue(System.nanoTime());
            }
        });
    }
    private volatile boolean recovering = true, finalizing, preparing;
    private volatile boolean cleared;
    public long previousSceneGeneration;
    public ResearchSessionViewModel(Application application) {
        super(application);
        finalizer.execute(() -> {
            try {
                List<File> recovered = ResearchSessionStore.recoverInterrupted(application);
                if (!recovered.isEmpty()) messages.postValue("Odzyskano niepełne sesje badawcze: "+recovered.size());
            } catch (Exception error) { messages.postValue("Błąd odzyskiwania sesji: "+error.getMessage()); }
            finally { recovering=false; if (cleared && !experiment.isRunning() && !finalizing) finalizer.shutdown(); }
        });
    }
    public ResearchSessionStore store() { return store; }
    public boolean finalizing() { return finalizing; }
    public boolean ready() {
        ResearchSessionStore current = store;
        return !recovering && !finalizing && !preparing && !experiment.isRunning()
                && (current == null || current.state() != ResearchSessionStore.State.PREPARED
                    && current.state() != ResearchSessionStore.State.RUNNING
                    && current.state() != ResearchSessionStore.State.FINALIZING);
    }
    public ResearchSessionStore prepare(ExperimentSession.Prepared prepared) throws Exception {
        synchronized (this) {
            if (!ready()) throw new IllegalStateException("Trwa przygotowanie lub finalizacja poprzedniej sesji");
            preparing = true;
        }
        try {
            rememberGallerySession(store);
            store = ResearchSessionStore.prepare(getApplication(),prepared);
            rememberGallerySession(store);
            galleryChanged.postValue(System.nanoTime());
            return store;
        } finally {
            preparing = false;
            if (cleared && !experiment.isRunning() && !finalizing) finalizer.shutdown();
        }
    }
    /** Captures only retained session state, never an Activity or its views. */
    public synchronized void armTimer(com.example.alpr_v1.metrics.MetricsCollector metrics) {
        if (!experiment.isRunning()) return;
        String id = experiment.sessionId();
        long deadline = experiment.timerDeadlineElapsedNanos();
        metrics.setMeasurementDeadlineElapsedNanos(deadline);
        timer.arm(id, deadline, () -> {
            synchronized (ResearchSessionViewModel.this) {
                if (!experiment.isRunning() || !id.equals(experiment.sessionId())) return;
                closeMeasurement(ExperimentSession.CompletionReason.TIMER, metrics);
            }
            timerExpired.postValue(id);
        });
    }

    /** Shared, short end transition for scheduler and operator STOP. */
    public synchronized void closeMeasurement(ExperimentSession.CompletionReason reason,
            com.example.alpr_v1.metrics.MetricsCollector metrics) {
        if (!experiment.isRunning()) return;
        long wall = System.currentTimeMillis(), elapsed = android.os.SystemClock.elapsedRealtimeNanos();
        long deadline = experiment.timerDeadlineElapsedNanos();
        if (deadline >= 0L && elapsed >= deadline) {
            if (com.example.alpr_v1.BuildConfig.DEBUG)
                android.util.Log.d("ALPR_TIMER", "deadline_lateness_ms=" + (elapsed - deadline) / 1_000_000L);
            reason = ExperimentSession.CompletionReason.TIMER;
            wall = experiment.timerDeadlineWallMillis(); elapsed = deadline;
        }
        if (store != null) store.closeAdmission(reason.name().toLowerCase(Locale.ROOT), wall, elapsed);
        experiment.finishAt(reason, wall, elapsed);
        metrics.finishMeasurementSession(wall);
        metrics.setCropCollectionActive(false);
        timer.cancel();
    }

    public void beginFinalizing() { finalizing=true; }
    public void finish(ResearchSessionStore target,Callable<ResearchSessionStore.Telemetry> telemetry,
                       ResearchExecutionConfig frozen) {
        finalizer.execute(() -> {
            try {
                File archive = target.finish(telemetry.call(),frozen);
                messages.postValue((target.state() == ResearchSessionStore.State.COMPLETED && target.evidenceExtensionsComplete()
                        ? "Zapisano pełną sesję: " : "Sesja niekompletna: ")+archive.getName());
            } catch (Exception error) {
                target.abortPreparation("finalization_failed: "+error.getMessage());
                messages.postValue("Błąd finalizacji sesji: "+error.getMessage());
            }
            finally { rememberGallerySession(target);galleryChanged.postValue(System.nanoTime());
                finalizing=false; if (cleared) finalizer.shutdown(); }
        });
    }
    @Override protected void onCleared() {
        cleared=true;
        timer.cancel(); timerExecutor.shutdown();
        if (!recovering && !preparing && !experiment.isRunning() && !finalizing) finalizer.shutdown();
    }
    public List<File> archives() {
        List<File> result = new ArrayList<>();
        File[] sessions = ResearchSessionStore.sessionsRoot(getApplication()).listFiles(File::isDirectory);
        if (sessions != null) for (File session : sessions) {
            File[] files = new File(session,"final").listFiles((dir,name)->name.endsWith(".alprsession"));
            if (files != null) result.addAll(Arrays.asList(files));
        }
        result.sort((a,b)->Long.compare(b.lastModified(),a.lastModified())); return result;
    }
}
