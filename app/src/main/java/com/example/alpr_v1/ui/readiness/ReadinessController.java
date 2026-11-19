package com.example.alpr_v1.ui.readiness;

import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Cancels obsolete results independently of settings_revision, including file/lifecycle changes. */
public final class ReadinessController implements AutoCloseable {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ReadinessEvaluator evaluator=new ReadinessEvaluator();
    private final LongFunction<ReadinessSnapshot> capture;
    private final Consumer<ReadinessResult> display;
    private final List<FileObserver> observers=new ArrayList<>();
    private Set<File> watched=Collections.emptySet();
    private volatile long generation;
    private boolean closed,suspended;
    private Runnable pending;
    private ReadinessResult current;

    public ReadinessController(LongFunction<ReadinessSnapshot> capture,Consumer<ReadinessResult> display) {
        this.capture=capture;this.display=display;
    }
    public ReadinessResult current() { return current; }
    public void refresh() { refresh(false); }
    public void refresh(boolean filesChanged) {
        if(closed||suspended)return;
        if(filesChanged)evaluator.invalidateFiles();
        long ticket=++generation;current=null;display.accept(null);
        if(pending!=null)main.removeCallbacks(pending);
        pending=()->{
            pending=null;if(closed||suspended||ticket!=generation)return;
            ReadinessSnapshot snapshot=capture.apply(ticket);watch(snapshot.watchedDirectories());
            worker.execute(()->{
                if(ticket!=generation)return;
                ReadinessResult result;
                try { result=evaluator.evaluate(snapshot); }
                catch(RuntimeException unavailable) {
                    android.util.Log.w("ALPR_READINESS","Read-only assessment unavailable",unavailable);
                    result=new ReadinessResult(snapshot.identity,snapshot.goal(),Collections.singletonList(new ReadinessFinding(
                            "assessment_pending",ReadinessFinding.Domain.CONFIG,ReadinessFinding.Status.NOT_CHECKABLE_YET,
                            "Nie udało się jeszcze zakończyć sprawdzania","Ponów sprawdzenie po powrocie do Opcji.",
                            false,null,"ReadinessEvaluator",snapshot.identity)));
                }
                ReadinessResult answer=result;
                main.post(()->{
                    if(closed||suspended||ticket!=generation)return;
                    if(snapshot.runtimeGeneration!=com.example.alpr_v1.inference.RuntimeBackendFactory.availabilityGeneration()) { refresh();return; }
                    current=answer;display.accept(answer);
                });
            });
        };
        main.postDelayed(pending,100);
    }
    private void watch(Collection<File> dirs) {
        Set<File> next=new HashSet<>(dirs);if(next.equals(watched))return;
        stopWatching();watched=next;
        for(File directory:next)if(directory!=null&&directory.isDirectory())try {
            FileObserver observer=new FileObserver(directory.getAbsolutePath(),FileObserver.CLOSE_WRITE|FileObserver.CREATE
                    |FileObserver.DELETE|FileObserver.MOVED_FROM|FileObserver.MOVED_TO|FileObserver.DELETE_SELF|FileObserver.MOVE_SELF) {
                @Override public void onEvent(int event,String path) { main.post(()->refresh(true)); }
            };
            observer.startWatching();observers.add(observer);
        } catch(RuntimeException unsupported) { /* onResume also invalidates every file observation. */ }
    }
    private void stopWatching() { for(FileObserver o:observers)o.stopWatching();observers.clear();watched=Collections.emptySet(); }
    public void suspend() {
        suspended=true;++generation;current=null;
        if(pending!=null)main.removeCallbacks(pending);pending=null;stopWatching();
    }
    public void resume() { if(closed)return;suspended=false;refresh(true); }
    @Override public void close() { suspend();closed=true;worker.shutdown(); }
}
