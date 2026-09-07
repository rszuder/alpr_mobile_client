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
    private volatile ResearchSessionStore store;
    private volatile boolean recovering = true, finalizing;
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
    public boolean ready() { return !recovering && !finalizing && !experiment.isRunning(); }
    public ResearchSessionStore prepare(ExperimentSession.Prepared prepared) throws Exception {
        if (!ready()) throw new IllegalStateException("Trwa przygotowanie lub finalizacja poprzedniej sesji");
        store = ResearchSessionStore.prepare(getApplication(),prepared); return store;
    }
    public void beginFinalizing() { finalizing=true; }
    public void finish(ResearchSessionStore target,Callable<ResearchSessionStore.Telemetry> telemetry,
                       ResearchExecutionConfig frozen) {
        finalizer.execute(() -> {
            try {
                File archive = target.finish(telemetry.call(),frozen);
                messages.postValue((target.state() == ResearchSessionStore.State.COMPLETED
                        ? "Zapisano pełną sesję: " : "Sesja niekompletna: ")+archive.getName());
            } catch (Exception error) {
                target.abortPreparation("finalization_failed: "+error.getMessage());
                messages.postValue("Błąd finalizacji sesji: "+error.getMessage());
            }
            finally { finalizing=false; if (cleared) finalizer.shutdown(); }
        });
    }
    @Override protected void onCleared() {
        cleared=true;
        if (!recovering && !experiment.isRunning() && !finalizing) finalizer.shutdown();
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
