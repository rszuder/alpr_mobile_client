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
    private String ordinaryRunId = "";
    private TimerConfig ordinaryTimer = TimerConfig.disabled();
    private boolean ordinaryRunning;
    private long ordinaryDeadline = -1L, ordinaryDeadlineWall = -1L;
    private ExperimentSession.CompletionReason ordinaryCompletion;

    /** Presentation of the one retained deadline, including ordinary analysis (no research session). */
    public static final class TimerState {
        public final String id;
        public final boolean running, enabled, expired;
        public final long durationSeconds, deadlineElapsedNanos;
        private TimerState(String id, boolean running, boolean enabled, long seconds, long deadline, boolean expired) {
            this.id=id; this.running=running; this.enabled=enabled;
            durationSeconds=seconds; deadlineElapsedNanos=deadline; this.expired=expired;
        }
    }
    public synchronized TimerState timerState() {
        if (experiment.state() != ExperimentSession.State.IDLE) {
            ExperimentSession.Snapshot snapshot=experiment.snapshot();
            return new TimerState(snapshot.sessionId, experiment.isRunning(), snapshot.timerEnabled,
                    snapshot.timerDurationMillis/1000L, experiment.timerDeadlineElapsedNanos(),
                    experiment.completionReason()==ExperimentSession.CompletionReason.TIMER);
        }
        return new TimerState(ordinaryRunId, ordinaryRunning, ordinaryTimer.enabled(), ordinaryTimer.durationSeconds(),
                ordinaryDeadline, ordinaryCompletion==ExperimentSession.CompletionReason.TIMER);
    }
    public synchronized void startOrdinaryTimer(TimerConfig config, long startWall, long startElapsed,
            com.example.alpr_v1.metrics.MetricsCollector metrics) {
        if (experiment.isRunning() || ordinaryRunning) throw new IllegalStateException("Analiza już trwa");
        ordinaryRunId="analysis-"+UUID.randomUUID();
        ordinaryTimer=config; ordinaryRunning=true; ordinaryCompletion=null;
        ordinaryDeadline=config.enabled()?startElapsed+config.durationMillis()*1_000_000L:-1L;
        ordinaryDeadlineWall=config.enabled()?startWall+config.durationMillis():-1L;
        armTimer(metrics);
    }
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
    public final MutableLiveData<ResearchOperationState.Snapshot> operationChanged=new MutableLiveData<>();
    public final ResearchOperationState operations;
    private final ExecutorService historyExecutor=Executors.newSingleThreadExecutor(r->new Thread(r,"research-history"));
    private String finalizationOperation="",documentOperation="";
    private ResearchSessionStore finalizationOwner;
    private volatile boolean documentBusy;
    private volatile boolean recovering, finalizing, preparing;
    private volatile boolean cleared;
    public long previousSceneGeneration;
    public ResearchSessionViewModel(Application application) {
        super(application);
        operations=new ResearchOperationState(new File(application.getFilesDir(),"research/operations"),operationChanged::postValue);
        historyExecutor.execute(() -> {
            String ticket="";
            try {
                operations.restore();ticket=operations.begin("","INVENTORY","Odczyt trwałego rejestru; bez automatycznej przebudowy sesji");
                List<ResearchSessionSummary> sessions=ResearchSessionSummary.catalog(ResearchSessionStore.sessionsRoot(application));
                operations.end(ticket,"SUCCEEDED","Sesje w historii: "+sessions.size(),null);
            } catch (Exception error) { operations.end(ticket,"FAILED",null,error);messages.postValue("Błąd odczytu historii: "+error); }
            finally { galleryChanged.postValue(System.nanoTime());if(cleared)historyExecutor.shutdown(); }
        });
    }
    public ResearchSessionStore store() { return store; }
    public boolean finalizing() { return finalizing; }
    public boolean ready() {
        ResearchSessionStore current = store;
        return !recovering && !finalizing && !preparing && !operations.hasBlockingWork() && !documentBusy() && !experiment.isRunning()
                && (current == null || current.state() != ResearchSessionStore.State.PREPARED
                    && current.state() != ResearchSessionStore.State.RUNNING
                    && current.state() != ResearchSessionStore.State.FINALIZING);
    }
    public ResearchSessionStore prepare(ExperimentSession.Prepared prepared) throws Exception {
        synchronized (this) {
            if (!ready()) throw new IllegalStateException("Trwa przygotowanie lub finalizacja poprzedniej sesji");
            preparing = true;
        }
        String ticket=operations.begin(prepared.sessionId,"PREPARING","");
        try {
            rememberGallerySession(store);
            store = ResearchSessionStore.prepare(getApplication(),prepared);
            rememberGallerySession(store);
            galleryChanged.postValue(System.nanoTime());
            operations.end(ticket,"SUCCEEDED","Przygotowano; oczekiwanie na START",null);
            return store;
        } catch(Exception|Error error){operations.end(ticket,"FAILED",null,error);throw error;}
        finally {
            preparing = false;
            if (cleared && !experiment.isRunning() && !finalizing) finalizer.shutdown();
        }
    }
    /** Captures only retained session state, never an Activity or its views. */
    public synchronized void armTimer(com.example.alpr_v1.metrics.MetricsCollector metrics) {
        if (experiment.isRunning()) {
            ordinaryRunId=""; ordinaryRunning=false; ordinaryCompletion=null;
        } else if (!ordinaryRunning) return;
        TimerState state=timerState();
        String id = state.id;
        long deadline = state.deadlineElapsedNanos;
        metrics.setMeasurementDeadlineElapsedNanos(deadline);
        timer.arm(id, deadline, () -> {
            synchronized (ResearchSessionViewModel.this) {
                TimerState current=timerState();
                if (!current.running || !id.equals(current.id)) return;
                closeMeasurement(ExperimentSession.CompletionReason.TIMER, metrics);
            }
            timerExpired.postValue(id);
        });
    }

    /** Shared, short end transition for scheduler and operator STOP. */
    public synchronized void closeMeasurement(ExperimentSession.CompletionReason reason,
            com.example.alpr_v1.metrics.MetricsCollector metrics) {
        if (!experiment.isRunning() && !ordinaryRunning) return;
        long wall = System.currentTimeMillis(), elapsed = android.os.SystemClock.elapsedRealtimeNanos();
        boolean research=experiment.isRunning();
        long deadline = research?experiment.timerDeadlineElapsedNanos():ordinaryDeadline;
        if (deadline >= 0L && elapsed >= deadline) {
            if (com.example.alpr_v1.BuildConfig.DEBUG)
                android.util.Log.d("ALPR_TIMER", "deadline_lateness_ms=" + (elapsed - deadline) / 1_000_000L);
            reason = ExperimentSession.CompletionReason.TIMER;
            wall = research?experiment.timerDeadlineWallMillis():ordinaryDeadlineWall; elapsed = deadline;
        }
        if (research) {
            if (store != null) store.closeAdmission(reason.name().toLowerCase(Locale.ROOT), wall, elapsed);
            experiment.finishAt(reason, wall, elapsed);
        } else {
            ordinaryRunning=false; ordinaryCompletion=reason;
        }
        metrics.finishMeasurementSession(wall);
        metrics.setCropCollectionActive(false);
        timer.cancel();
    }

    public synchronized void beginFinalizing() {
        if(finalizing)return;
        finalizing=true;finalizationOwner=store;finalizationOperation=operations.begin(store==null?"":store.sessionId(),"QUEUE_DRAIN","Domykanie bieżących zadań; pliki sesji pozostają zachowane");
    }
    public synchronized String operationDescription(){ResearchOperationState.Snapshot s=operations.latest();return s==null?"Brak aktywnej operacji":s.description();}
    public boolean documentBusy(){return documentBusy||operations.hasDocumentWork();}
    public String operationHistoryDescription(){return operations.historyDescription();}
    /** Also covers failures before the finalizer is enqueued (e.g. a failed crop barrier). */
    public void failFinalization(ResearchSessionStore target,Throwable cause){
        Exception error=cause instanceof Exception?(Exception)cause:new RuntimeException("FINALIZATION_FATAL",cause);
        final String ticket;
        synchronized(this){if(!finalizing||finalizationOwner!=target)return;ticket=finalizationOperation;}
        target.recordFinalizationFailure(error);
        com.example.alpr_v1.logging.AppLog.error(getApplication(),"F6_SESSION_FINALIZATION",
                "Nie utworzono zatwierdzonego eksportu sesji "+target.sessionId()+". Pliki zachowano: "+target.directory()+". "+ResearchOperationState.chain(error),error);
        operations.end(ticket,"FAILED","Pliki sesji zachowano: "+target.directory(),error);
        messages.postValue("Nie zakończono zapisu sesji "+target.sessionId()+". Pliki zachowano; Sesje / Eksport → szczegóły. "+ResearchOperationState.chain(error));
        synchronized(this){if(ticket.equals(finalizationOperation)){finalizing=false;finalizationOwner=null;}}
        rememberGallerySession(target);galleryChanged.postValue(System.nanoTime());
    }
    public void finish(ResearchSessionStore target,Callable<ResearchSessionStore.Telemetry> telemetry,ResearchExecutionConfig frozen) {
        final String ticket;
        synchronized(this){
            if(target.state()!=ResearchSessionStore.State.FINALIZING||store!=null&&store!=target||finalizationOwner!=null&&finalizationOwner!=target)return;
            if(!finalizing)beginFinalizing();
            if(finalizationOwner==null){finalizationOwner=target;operations.bindSession(finalizationOperation,target.sessionId());}
            ticket=finalizationOperation;
        }
        try{finalizer.execute(() -> {
            try {
                File archive=target.finish(telemetry.call(),frozen,stage->operations.update(ticket,stage,0,0,null));
                operations.end(ticket,"SUCCEEDED","Archiwum: "+archive+" · "+target.state(),null);
                messages.postValue((target.state()==ResearchSessionStore.State.COMPLETED&&target.evidenceExtensionsComplete()?"Zapisano pełną sesję: ":"Sesja niekompletna: ")+archive.getName());
            }catch(Exception|Error error){failFinalization(target,error);}
            finally{rememberGallerySession(target);galleryChanged.postValue(System.nanoTime());
                synchronized(this){if(ticket.equals(finalizationOperation)){finalizing=false;finalizationOwner=null;}}
                if(cleared)finalizer.shutdown();}
        });}catch(RuntimeException|Error error){failFinalization(target,error);}
    }
    public CompletableFuture<File> recoverArchive(File archive){
        CompletableFuture<File> result=new CompletableFuture<>();
        synchronized(this){
            if(recovering||finalizing||preparing||documentBusy()||operations.hasBlockingWork()||experiment.isRunning()){result.completeExceptionally(new IllegalStateException(operationDescription()));return result;}
            recovering=true;
        }
        File source=archive.getParentFile().getParentFile();String ticket=operations.begin(source.getName(),"RECOVERING",source.toString());
        try{historyExecutor.execute(()->{
            try{File recovered=ResearchSessionStore.recoverCopy(source,getApplication());operations.end(ticket,"SUCCEEDED","Kopia PARTIAL; oryginał zachowano: "+recovered,null);result.complete(recovered);}
            catch(Exception|Error error){operations.end(ticket,"FAILED","Oryginał zachowano: "+source,error);result.completeExceptionally(error);}
            finally{recovering=false;galleryChanged.postValue(System.nanoTime());}
        });}catch(RuntimeException|Error error){recovering=false;operations.end(ticket,"FAILED",null,error);result.completeExceptionally(error);}
        return result;
    }
    public synchronized void exportArchive(File source,android.net.Uri uri){
        if(documentBusy()){messages.postValue(operationDescription());return;}
        if(experiment.isRunning()||finalizing||preparing||recovering||operations.hasWriteWork()){
            String blocked=operations.begin(experiment.snapshot().sessionId,"VALIDATING_ARCHIVE",uri.toString());
            operations.end(blocked,"CANCELLED","Nie rozpoczęto kopiowania: trwa pomiar lub operacja zapisu/przygotowania. Dokument może być pusty: "+uri+". Źródłową sesję zachowano.",null);
            messages.postValue(operationHistoryDescription());return;
        }
        documentBusy=true;
        ResearchSessionSummary summary=ResearchSessionSummary.load(Collections.singletonList(source)).get(0);
        final String ticket=operations.begin(summary.session.optString("session_id"),"VALIDATING_ARCHIVE",uri.toString());documentOperation=ticket;
        try{historyExecutor.execute(()->{
            try{
                if(!source.getCanonicalFile().toPath().startsWith(ResearchSessionStore.sessionsRoot(getApplication()).getCanonicalFile().toPath()))throw new java.io.IOException("EXPORT_OUTSIDE_SESSION_REGISTRY");
                com.example.alpr_v1.metrics.ResearchArchive.verifyEntryHashes(source);
                if("TV_BBOX_CROP".equals(summary.value("input_mode")))TvBboxInputValidator.validate(source);
                ResearchDocumentExport.Receipt receipt=ResearchDocumentExport.copy(getApplication().getContentResolver(),uri,source,
                        (stage,done,total)->operations.update(ticket,stage,done,total,uri.toString()));
                String detail="Dokument: "+ResearchDocumentExport.displayName(getApplication().getContentResolver(),uri)+"\nURI: "+uri+"\nBajty: "+receipt.bytes+"\nSHA-256: "+receipt.sha256+"\n"+receipt.verification;
                operations.end(ticket,receipt.verification.equals("VERIFIED_SHA256")?"SUCCEEDED":"SAVED_UNVERIFIED",detail,null);
                com.example.alpr_v1.logging.AppLog.info(getApplication(),"RESEARCH_EXPORT_RECEIPT",detail);messages.postValue("Eksport: "+detail);
            }catch(Exception|Error error){operations.end(ticket,"FAILED","Dokument może być częściowy: "+uri+". Źródłowa sesja zachowana.",error);
                com.example.alpr_v1.logging.AppLog.error(getApplication(),"RESEARCH_EXPORT_FAILED",uri.toString(),error);messages.postValue("Błąd eksportu: "+ResearchOperationState.chain(error));}
            finally{synchronized(this){if(ticket.equals(documentOperation))documentBusy=false;}galleryChanged.postValue(System.nanoTime());if(cleared)historyExecutor.shutdown();}
        });}catch(RuntimeException|Error error){documentBusy=false;operations.end(ticket,"FAILED",uri.toString(),error);}
    }
    @Override protected void onCleared() {
        cleared=true;
        timer.cancel(); timerExecutor.shutdown();
        if(!documentBusy&&!recovering)historyExecutor.shutdown();
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
