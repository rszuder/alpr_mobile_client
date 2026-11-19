package com.example.alpr_v1.ui;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.alpr_v1.R;
import com.example.alpr_v1.experiment.ResearchSessionSummary;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public final class ResearchSessionPicker {
    private ResearchSessionPicker() { }
    public static AlertDialog showHistory(Context context,List<ResearchSessionSummary> sessions,java.util.function.Supplier<String> operation,Consumer<File> selected){
        return showHistory(context,sessions,operation,selected,java.util.concurrent.ForkJoinPool.commonPool());
    }
    public static AlertDialog showHistory(Context context,List<ResearchSessionSummary> sessions,java.util.function.Supplier<String> operation,Consumer<File> selected,java.util.concurrent.Executor io){
        MaterialAlertDialogBuilder builder=new com.example.alpr_v1.ui.ImmersiveDialogBuilder(context).setTitle(R.string.research_sessions_export)
                .setNegativeButton(R.string.menu_close,null)
                .setNeutralButton("Szczegóły operacji",(d,w)->new com.example.alpr_v1.ui.ImmersiveDialogBuilder(context).setTitle("Operacja zapisu / eksportu")
                        .setMessage(operation.get()).setPositiveButton("OK",null).show());
        if(sessions.isEmpty())return builder.setMessage(R.string.research_sessions_empty).show();
        ResearchSessionExportAdapter adapter=new ResearchSessionExportAdapter(sessions);
        return builder.setAdapter(adapter,(dialog,index)->{
            ResearchSessionSummary item=adapter.getItem(index);
            String details="Sesja: "+item.session.optString("session_id")+"\nStan: "+item.session.optString("state")
                    +"\n"+ResearchConfigurationText.details(item.execution)+"\n"+item.partialReason();
            AlertDialog detail=new com.example.alpr_v1.ui.ImmersiveDialogBuilder(context).setTitle(ResearchConfigurationText.title(item))
                    .setMessage(details+(item.hasF6Evidence()?"\n\nOdczytywanie rozliczenia F6…":""))
                    .setNegativeButton("Wróć",(d,w)->showHistory(context,sessions,operation,selected,io))
                    .setPositiveButton(item.hasArchive()?"Eksportuj kopię":"Sprawdź i odzyskaj kopię PARTIAL",(d,w)->selected.accept(item.archive)).create();
            java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true);
            detail.setOnDismissListener(d->active.set(false));detail.show();
            if(item.hasF6Evidence()){
                android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
                java.util.function.Consumer<String> publish=text->main.post(()->{
                    if(active.get()&&detail.isShowing()&&(!(context instanceof android.app.Activity)
                            ||!((android.app.Activity)context).isFinishing()&&!((android.app.Activity)context).isDestroyed()))detail.setMessage(details+"\n\n"+text);
                });
                try{io.execute(()->publish.accept(item.evidenceBreakdown()));}
                catch(java.util.concurrent.RejectedExecutionException unavailable){publish.accept("Rozliczenie F6: nieustalone — odczyt historii jest niedostępny.");}
            }
        }).show();
    }
    public static AlertDialog show(Context context, List<ResearchSessionSummary> sessions, Consumer<File> selected) {
        ResearchSessionExportAdapter adapter = new ResearchSessionExportAdapter(sessions);
        return new com.example.alpr_v1.ui.ImmersiveDialogBuilder(context).setTitle(R.string.session_export_title)
                .setAdapter(adapter,(dialog,index) -> selected.accept(adapter.getItem(index).archive))
                .setNegativeButton(R.string.menu_close,null).show();
    }
}
